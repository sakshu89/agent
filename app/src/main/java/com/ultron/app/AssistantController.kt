package com.ultron.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.ultron.app.tools.AppResolver
import com.ultron.app.tools.Command
import com.ultron.app.tools.CommandExecutor
import com.ultron.app.tools.CommandParser
import com.ultron.app.tools.PartialMessage

/**
 * The assistant's brain. Owns voice, the LLM client and the follow-up conversation,
 * so tap-to-talk, typed commands and always-on hands-free mode all share one pipeline.
 * Everything runs on the main thread.
 */
object AssistantController {
    private const val PREFS = "ultron_preferences"
    private const val KEY_HANDS_FREE = "hands_free"
    private const val KEY_WAKE_WORD = "wake_word_required"
    private const val KEY_CONFIRM = "confirm_send"
    private const val FOLLOW_UP_WINDOW_MS = 200_000L
    private const val CONVERSATION_TIMEOUT_MS = 200_000L
    // In strict mode ITRA only accepts a reply without its name for this long after it asked a question.
    private const val ANSWER_WINDOW_MS = 15_000L
    private const val KEY_STRICT_WAKE = "strict_wake"

    // State observed by the UI.
    var status by mutableStateOf("SYSTEM READY")
    var listening by mutableStateOf(false)
    var processing by mutableStateOf(false)
    var userText by mutableStateOf("")
    var assistantText by mutableStateOf("")
    var pendingTool by mutableStateOf<LlmClient.ToolCall?>(null)
    var handsFree by mutableStateOf(false)
        private set

    private var appContext: Context? = null
    private var voice: VoiceManager? = null
    private var llm: LlmClient? = null
    private var partial: PartialMessage? = null
    private var lastActiveAt = 0L
    private var sessionId: String? = null
    private var timeoutHandler: android.os.Handler? = null
    private var timeoutRunnable: Runnable? = null
    private val responseForSpeech = StringBuilder()
    private val conversation = ArrayDeque<Pair<String, String>>()
    private var weather: WeatherClient? = null
    private var locationPermissionRequest: (() -> Unit)? = null
    private var activeAgentGoal: String? = null
    private var agentToolSteps = 0
    private var taskContext: com.ultron.app.agent.TaskContextStore? = null
    private val maxAgentToolSteps = 4

    private const val KEY_WAKE_ALIASES = "wake_aliases"
    private const val OLD_DEFAULT_WAKE_ALIASES =
        "ultron, altron, ultran, oltron, ultra on, alton, all tron, alter on, old tron, ultra"
    const val DEFAULT_WAKE_ALIASES = "itra, eetra, eye tra, it rah"

    private var wakeWord = buildWakeWord(DEFAULT_WAKE_ALIASES)
    private var wakeStrip = buildWakeStrip(DEFAULT_WAKE_ALIASES)
    private var wakeStart = buildWakeStart(DEFAULT_WAKE_ALIASES)

    private fun aliasPattern(aliases: String): String {
        val parts = aliases.split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("itra") }
            .sortedByDescending { it.length }
        return parts.joinToString("|") { Regex.escape(it) }
    }

    private fun buildWakeWord(aliases: String) =
        Regex("\\b(?:${aliasPattern(aliases)})\\b", RegexOption.IGNORE_CASE)

    /** The name must be at the start of the sentence ("ITRA, ...", "Hey ITRA, ..."), not just mentioned in passing. */
    private fun buildWakeStart(aliases: String) = Regex(
        "^\\W*(?:\\w+\\s+){0,2}?(?:${aliasPattern(aliases)})\\b",
        RegexOption.IGNORE_CASE
    )

    private val wakeFillers = setOf("hey", "hi", "hello", "ok", "okay", "yo")

    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(
                    prev[j] + 1,
                    cur[j - 1] + 1,
                    prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
            prev = cur
        }
        return prev[b.length]
    }

    /**
     * Offline speech models often do not know the word "ITRA" and write something that sounds like it
     * ("alter on", "old tron", "all tron", "ultra on"). If the first one or two words (after an optional
     * "hey / ok") sound close enough to "ITRA", count them as the name.
     * @return how many leading words (including "hey") make up the name, or 0 if the name is not there.
     */
    private fun soundAlikeWordCount(words: List<String>): Int {
        val t = words.map { w -> w.lowercase().filter { it.isLetter() } }
        val start = if (t.isNotEmpty() && t[0] in wakeFillers) 1 else 0
        for (n in 1..2) {
            if (start + n > t.size) break
            val candidate = t.subList(start, start + n).joinToString("")
            if (candidate.length >= 4 && editDistance(candidate, "itra") <= 1) return start + n
        }
        return 0
    }

    private fun isAddressed(text: String): Boolean {
        val byAlias = if (strictWake) wakeStart.containsMatchIn(text) else wakeWord.containsMatchIn(text)
        return byAlias || soundAlikeWordCount(text.trim().split(Regex("\\s+"))) > 0
    }

    /** Remove the spoken name (and a leading "hey") so only the command is left. */
    private fun stripName(raw: String): String {
        val byAlias = wakeStrip.replace(raw, "").trim()
        if (byAlias != raw.trim()) return byAlias
        val words = raw.trim().split(Regex("\\s+"))
        val count = soundAlikeWordCount(words)
        return if (count > 0) words.drop(count).joinToString(" ").trim(' ', ',', '.', ':', '!', '-') else raw.trim()
    }

    private fun buildWakeStrip(aliases: String) = Regex(
        "^(?:hey|hi|hello|ok|okay)?\\s*(?:${aliasPattern(aliases)})\\b[\\s,.:!-]*",
        RegexOption.IGNORE_CASE
    )

    /** The names that wake ITRA, comma separated. Add what your speech model hears for "ITRA". */
    val wakeAliases: String
        get() = appContext?.let { storedAliases() } ?: DEFAULT_WAKE_ALIASES

    private fun storedAliases(): String {
        val saved = prefs().getString(KEY_WAKE_ALIASES, DEFAULT_WAKE_ALIASES) ?: DEFAULT_WAKE_ALIASES
        // Move people still on the old, too-loose default list to the tighter one.
        return if (saved == OLD_DEFAULT_WAKE_ALIASES) DEFAULT_WAKE_ALIASES else saved
    }

    fun setWakeAliases(value: String) {
        val cleaned = value.trim().ifBlank { DEFAULT_WAKE_ALIASES }
        prefs().edit().putString(KEY_WAKE_ALIASES, cleaned).apply()
        wakeWord = buildWakeWord(cleaned)
        wakeStrip = buildWakeStrip(cleaned)
        wakeStart = buildWakeStart(cleaned)
        LogStore.i("SETTINGS", "Wake words: $cleaned")
    }

    /** Called by [VoskModelManager] after the model files change. */
    fun onVoskModelChanged(releaseOnly: Boolean = false) {
        voice?.reloadVosk(releaseOnly)
    }

    private val yes = Regex(
        "^(?:yes|yeah|yep|yup|confirm|confirmed|send|send it|go ahead|do it|ok|okay|sure|haan|ha|kar do)\\W*$",
        RegexOption.IGNORE_CASE
    )
    private val no = Regex(
        "^(?:no|nope|cancel|stop|don'?t|never ?mind|nahi|mat bhejo)\\W*$",
        RegexOption.IGNORE_CASE
    )
    private val sleep = Regex(
        "^(?:stop listening|hands[- ]?free off|turn off hands[- ]?free|go to sleep|sleep)\\W*$",
        RegexOption.IGNORE_CASE
    )

    // ------------------------------------------------------------------ setup

    fun setLocationPermissionRequest(callback: () -> Unit) { locationPermissionRequest = callback }
    fun applyVoiceSettings() { voice?.applySettings() }

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        LogStore.init(app)
        ModeManager.init(app)
        GhostModeManager.init(app)
        StorageManager.init(app)
        com.ultron.app.memory.SessionManager.init(app)
        com.ultron.app.memory.PersonalInstructionManager.init(app)
        com.ultron.app.memory.LongTermMemoryManager.init(app)
        voice = VoiceManager(app)
        llm = LlmClient(context.applicationContext)
        weather = WeatherClient(app)
        UltronSettings.load(app)
        taskContext = com.ultron.app.agent.TaskContextStore(app).takeIf { !UltronSettings.privacyMode }
        VoskModelManager.refresh(app)
        com.ultron.app.notifications.NotificationInsightManager.init()
        val aliases = storedAliases()
        wakeWord = buildWakeWord(aliases)
        wakeStrip = buildWakeStrip(aliases)
        wakeStart = buildWakeStart(aliases)
        requireWakeWord = prefs().getBoolean(KEY_WAKE_WORD, true)
        strictWake = prefs().getBoolean(KEY_STRICT_WAKE, false)
    }

    private fun prefs() = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Hands-free only reacts to speech that starts with "ITRA". On by default. */
    var requireWakeWord by mutableStateOf(true)
        private set

    /** Strict: no open follow-up chat. Every command needs "ITRA" (except short answers to a question ITRA just asked). */
    var strictWake by mutableStateOf(false)
        private set

    fun updateStrictWake(value: Boolean) {
        strictWake = value
        prefs().edit().putBoolean(KEY_STRICT_WAKE, value).apply()
        LogStore.i("SETTINGS", "Strict wake word: $value")
    }

    fun updateRequireWakeWord(value: Boolean) {
        requireWakeWord = value
        prefs().edit().putBoolean(KEY_WAKE_WORD, value).apply()
        LogStore.i("SETTINGS", "Wake word required in hands-free: $value")
    }

    val confirmBeforeSending: Boolean get() = prefs().getBoolean(KEY_CONFIRM, true)
    fun setConfirmBeforeSending(value: Boolean) {
        prefs().edit().putBoolean(KEY_CONFIRM, value).apply()
        LogStore.i("SETTINGS", "Confirm before sending: $value")
    }

    private fun hasMic(): Boolean =
        appContext!!.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun idleStatus() = if (handsFree) "HANDS-FREE" else "SYSTEM READY"

    // ------------------------------------------------------------------ hands-free

    /** Called when the app opens: continue hands-free if it was on last time. */
    fun resumeIfEnabled() {
        maybeGreet()
        if (!handsFree && prefs().getBoolean(KEY_HANDS_FREE, false) && hasMic()) setHandsFree(true)
    }


    private fun maybeGreet() {
        val c = appContext ?: return
        val now = java.util.Calendar.getInstance()
        val day = now.get(java.util.Calendar.DAY_OF_YEAR)
        val year = now.get(java.util.Calendar.YEAR)
        val key = "greet_${year}_${day}"
        val prefs = prefs()
        if (prefs.getBoolean(key, false)) return
        val h = now.get(java.util.Calendar.HOUR_OF_DAY)
        val text = when { h < 5 -> "You're up late, Sir. How can I help?"; h < 12 -> "Hello, Sir. Good morning! How are you today?"; h < 17 -> "Good afternoon, Sir."; h < 21 -> "Good evening, Sir."; else -> "You're up late, Sir. How can I help?" }
        prefs.edit().putBoolean(key, true).apply()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (!processing && !listening) voice?.speak(text) }, 650)
    }

    /** @return false if the microphone permission is missing (caller should request it). */
    fun setHandsFree(on: Boolean): Boolean {
        val app = appContext ?: return false
        if (on == handsFree) return true
        if (on) {
            if (!hasMic()) {
                LogStore.w("VOICE", "Microphone permission is needed for hands-free mode.")
                return false
            }
            handsFree = true
            lastActiveAt = System.currentTimeMillis()
            scheduleConversationTimeout()
            prefs().edit().putBoolean(KEY_HANDS_FREE, true).apply()
            runCatching {
                ContextCompat.startForegroundService(app, Intent(app, UltronListenService::class.java))
            }.onFailure {
                LogStore.w("VOICE", "Could not start the background listener (${it.message}). Hands-free works only while ITRA is open.")
            }
            voice?.stopListening()
            listening = false
            voice?.startContinuous(onHeard = { heard(it) })
            status = "HANDS-FREE"
            LogStore.a("VOICE", if (requireWakeWord) "Hands-free ON. Say \"ITRA ...\"." else "Hands-free ON. Listening to everything.")
        } else {
            handsFree = false
            cancelConversationTimeout()
            prefs().edit().putBoolean(KEY_HANDS_FREE, false).apply()
            voice?.stopContinuous()
            runCatching { app.stopService(Intent(app, UltronListenService::class.java)) }
            if (!processing) status = "SYSTEM READY"
            LogStore.a("VOICE", "Hands-free OFF.")
        }
        return true
    }

    /** Speech heard while hands-free. Applies the wake word rule. */
    private fun heard(text: String) {
        // Private mode phrases are evaluated before the normal wake-word gate.
        // They are intentionally not added to the public wake-word aliases or UI.
        if (GhostModeManager.isPrivatePhrase(text)) {
            handleUserInput(text)
            return
        }
        // A short interruption command is accepted even without the wake word.
        if (voice?.isSpeaking == true && isStopCommand(text)) {
            stopSpeakingByVoice()
            return
        }
        val now = System.currentTimeMillis()
        val awaitingAnswer = (partial != null || pendingTool != null) && now - lastActiveAt < ANSWER_WINDOW_MS
        val inFollowUp = if (strictWake) awaitingAnswer
            else partial != null || pendingTool != null || now - lastActiveAt < FOLLOW_UP_WINDOW_MS
        val addressed = isAddressed(text)
        if (requireWakeWord && !addressed && !inFollowUp) {
            // Not addressed to ITRA: drop it. Only the first 3 words are logged, to help tune the name.
            val first = text.trim().split(Regex("\\s+")).take(3).joinToString(" ")
            LogStore.i("HEARD", "(ignored) $first ...")
            return
        }
        if (processing && pendingTool == null) {
            LogStore.i("HEARD", "(busy, ignored) $text")
            return
        }
        handleUserInput(text)
    }

    // ------------------------------------------------------------------ tap to talk

    fun tapToTalk(onNeedMicPermission: () -> Unit) {
        if (!hasMic()) {
            LogStore.i("ITRA", "Requesting microphone permission...")
            onNeedMicPermission()
            return
        }
        if ((processing && pendingTool == null) || handsFree) return
        listening = true
        status = "LISTENING"
        LogStore.i("ITRA", "Listening...")
        voice?.startListening { spoken ->
            listening = false
            if (spoken.isBlank()) {
                status = idleStatus()
                LogStore.w("VOICE", "No speech result. Check recognition service and language settings.")
                return@startListening
            }
            handleUserInput(spoken)
        }
    }

    private fun isStopCommand(text: String): Boolean = VoiceInterruptCommand.matches(text)

    private fun stopSpeakingByVoice() {
        voice?.stopSpeaking()
        LogStore.a("VOICE", "Speech interrupted by voice command.")
        status = idleStatus()
    }

    fun emergencyStop(): Boolean {
        val cancelled = UltronAccessibilityService.getInstance()?.emergencyStop() == true
        pendingTool = null
        partial = null
        voice?.stop()
        setHandsFree(false)
        processing = false
        status = "EMERGENCY STOP"
        assistantText = if (cancelled) "Emergency stop engaged. Active automation was cancelled." else "Emergency stop engaged."
        LogStore.w("ITRA", assistantText)
        return cancelled
    }

    fun stopTapListening() {
        voice?.stopListening()
        listening = false
        status = idleStatus()
    }

    // ------------------------------------------------------------------ input pipeline

    fun handleUserInput(input: String) {
        val raw = input.trim()
        if (raw.isBlank()) return
        if (voice?.isSpeaking == true && isStopCommand(raw)) {
            stopSpeakingByVoice()
            return
        }

        // Private mode control is intercepted before normal wake-word, logging, or LLM routing.
        GhostModeManager.intercept(raw)?.let { response ->
            userText = ""
            assistantText = response
            status = if (GhostModeManager.active) "PRIVILEGED READY" else idleStatus()
            responseForSpeech.setLength(0)
            responseForSpeech.append(response)
            voice?.speak(response)
            return
        }

        val spoken = stripName(raw)

        // A pending message is waiting for yes / no.
        pendingTool?.let { tool ->
            LogStore.i("YOU", raw)
            when {
                yes.matches(spoken) -> confirmPending()
                no.matches(spoken) -> cancelPending()
                else -> LogStore.i("ITRA", "Waiting for confirmation. Say \"confirm\" or \"cancel\".")
            }
            return
        }

        if (processing) {
            LogStore.w("ITRA", "Busy, ignored: $raw")
            return
        }

        userText = raw
        assistantText = ""
        responseForSpeech.setLength(0)
        LogStore.i("YOU", raw)
        lastActiveAt = System.currentTimeMillis()
        if (handsFree) scheduleConversationTimeout()
        // Privacy mode: nothing from the conversation (not even a session title) is written to storage.
        if (!UltronSettings.privacyMode) {
            val sid = sessionId ?: com.ultron.app.memory.SessionManager.start("ITRA session").also { sessionId = it }
            com.ultron.app.memory.SessionManager.addMessage("user", raw, sid)
            if (com.ultron.app.memory.SessionManager.list(1).firstOrNull { it.id == sid }?.title == "ITRA session") {
                val title = raw.replace(Regex("\\s+"), " ").trim().take(60).ifBlank { "ITRA session" }
                com.ultron.app.memory.SessionManager.updateTitleAndSummary(sid, title, "Conversation session")
            }
        }

        when {
            Regex("^switch to light mode$", RegexOption.IGNORE_CASE).matches(spoken) -> { ModeManager.setMode(ModeManager.Mode.LIGHT); finish(true, "Light mode active, Sir."); return }
            Regex("^switch to medium mode$", RegexOption.IGNORE_CASE).matches(spoken) -> { ModeManager.setMode(ModeManager.Mode.MEDIUM); finish(true, "Medium mode active, Sir."); return }
            Regex("^(activate|switch to) extreme mode$", RegexOption.IGNORE_CASE).matches(spoken) -> { ModeManager.setMode(ModeManager.Mode.EXTREME); finish(true, "Extreme mode active, Sir. Android permissions still apply."); return }
            Regex("^itra pause$", RegexOption.IGNORE_CASE).matches(raw.trim()) -> { setHandsFree(false); finish(true, "Paused, Sir."); return }
            Regex("^itra shutdown$", RegexOption.IGNORE_CASE).matches(raw.trim()) -> { setHandsFree(false); voice?.stopListening(); voice?.stopContinuous(); finish(true, "ITRA listening has been shut down. Restart the app to resume."); return }
        }

        if (spoken.isBlank()) {
            finish(true, "Yes?")
            return
        }

        // Explicit long-term memory controls. Nothing is stored unless the user asks.
        val remember = Regex("(?i)^(?:remember|remember that|save this)\\s+(.+)$").find(spoken)
        if (remember != null && !UltronSettings.privacyMode) {
            val value = remember.groupValues[1].trim()
            val split = Regex("(?i)^(.+?)\\s+(?:is|=)\\s+(.+)$").find(value)
            val key = split?.groupValues?.getOrNull(1)?.trim() ?: "note"
            val memoryValue = split?.groupValues?.getOrNull(2)?.trim() ?: value
            com.ultron.app.memory.LongTermMemoryManager.remember("preference", key, memoryValue)
            finish(true, "Saved that to my local memory.")
            return
        }
        val forget = Regex("(?i)^(?:forget|delete from memory)\\s+(.+)$").find(spoken)
        if (forget != null) {
            val key = forget.groupValues[1].trim()
            val removed = com.ultron.app.memory.LongTermMemoryManager.search(key, 20).sumOf { if (com.ultron.app.memory.LongTermMemoryManager.delete(it.id)) 1 else 0 }
            finish(true, if (removed > 0) "Removed $removed local memory item${if (removed == 1) "" else "s"}." else "I couldn't find that in local memory.")
            return
        }
        if (sleep.matches(spoken)) {
            finish(true, "Okay, hands-free is off.")
            setHandsFree(false)
            return
        }

        if (GhostModeManager.active) {
            when {
                Regex("^(?:lock|lock the) (?:phone|device)$", RegexOption.IGNORE_CASE).matches(spoken) -> {
                    processing = true
                    val result = GhostModeManager.lockDevice()
                    processing = false
                    finish(result.isSuccess, result.fold({ "Device locked." }, { "I cannot lock the device: ${it.message ?: "permission not available"}." }))
                    return
                }
                Regex("^(?:reboot|restart) (?:the )?(?:phone|device)$", RegexOption.IGNORE_CASE).matches(spoken) -> {
                    pendingTool = LlmClient.ToolCall(id = "ghost_reboot", name = "ghost_reboot", appName = "", contactName = "", message = "")
                    finish(true, "Reboot is a privileged action. Say confirm to continue or cancel.")
                    return
                }
            }
        }

        // Gate the parsed command itself rather than all parsed commands. Research is
        // intentionally available in Light mode; only commands disallowed by the
        // operating-mode policy should be rejected here.
        val parsedCommand = CommandParser.parse(spoken)
        if (parsedCommand != null && !ModeManager.allowsCommand(parsedCommand)) {
            finish(true, "That action is not available in ${ModeManager.mode.name.lowercase()} mode, Sir. Switch to medium or extreme mode if you want to allow it.")
            return
        }

        if (spoken.lowercase().contains("weather") || spoken.lowercase().contains("forecast")) {
            processing = true
            status = "WEATHER"
            val w = weather
            if (w == null) {
                finish(false, "Weather is not available right now.")
                return
            }
            w.current(
                onResult = { result -> finish(true, result) },
                onError = { error ->
                    if (error.contains("Location permission", true)) locationPermissionRequest?.invoke()
                    finish(false, error)
                }
            )
            return
        }

        val waiting = partial
        if (waiting != null) {
            partial = null
            val next = CommandParser.continuePartial(waiting, spoken)
            if (next == null) {
                finish(true, "Okay, cancelled.")
            } else {
                route(next)
            }
            return
        }

        val command = parsedCommand
        if (command == null) {
            processing = true
            status = "THINKING"
            askLlm(spoken)
            return
        }
        route(command)
    }

    private fun route(command: Command) {
        when (command) {
            is Command.NeedMoreInfo -> {
                partial = command.partial
                finish(false, command.question)
            }
            is Command.SendMessage -> prepareSend(command)
            else -> execute(command)
        }
    }

    private fun prepareSend(command: Command.SendMessage) {
        val ctx = appContext ?: return
        // Check the operating mode before asking the user to confirm something that would be refused anyway.
        if (!ModeManager.allowsCommand(command)) {
            finish(false, "Sending messages is not available in ${ModeManager.mode.name.lowercase()} mode, Sir. Switch to extreme mode to allow it.")
            return
        }
        val app = command.app ?: CommandExecutor.defaultChatApp(ctx)
        if (app == null) {
            partial = PartialMessage(null, command.contact, command.message, "app")
            val installed = AppResolver.installedMessagingApps(ctx).joinToString(", ") { it.label }
            finish(false, "Which app should I use?" + if (installed.isNotBlank()) " You have $installed." else "")
            return
        }
        val tool = LlmClient.ToolCall(
            id = "",
            name = ToolRegistry.SEND_MESSAGE_TOOL,
            appName = app,
            contactName = command.contact,
            message = command.message
        )
        if (confirmBeforeSending) {
            pendingTool = tool
            processing = true
            status = "CONFIRM ACTION"
            val ask = "Send \"${tool.message}\" to ${tool.contactName} on ${tool.appName}? Say confirm or cancel."
            assistantText = ask
            LogStore.w("ITRA", ask)
            voice?.speak(ask)
            lastActiveAt = System.currentTimeMillis()
        } else {
            execute(Command.SendMessage(app, command.contact, command.message))
        }
    }

    fun confirmPending() {
        val tool = pendingTool ?: return
        pendingTool = null
        if (tool.name == "ghost_reboot") {
            val result = GhostModeManager.rebootDevice()
            finish(result.isSuccess, result.fold({ "Rebooting device." }, { "I cannot reboot the device: ${it.message ?: "permission not available"}." }))
            return
        }
        LogStore.a("ITRA", "Message confirmed.")
        execute(Command.SendMessage(tool.appName, tool.contactName, tool.message))
    }

    fun cancelPending() {
        if (pendingTool == null) return
        pendingTool = null
        LogStore.w("ITRA", "Message cancelled.")
        finish(true, "Okay, cancelled.")
    }

    private fun execute(command: Command, onResult: ((Boolean, String) -> Unit)? = null) {
        val ctx = appContext ?: return
        if (!ModeManager.allowsCommand(command)) {
            finish(false, "That action is not available in ${ModeManager.mode.name.lowercase()} mode. Switch to ${if (ModeManager.mode == ModeManager.Mode.MEDIUM) "extreme" else "medium"} mode if you want to allow it.")
            return
        }
        if (!com.ultron.app.agent.AgentExecutionGuard.allow(command)) {
            LogStore.w("AGENT", "Duplicate dispatch suppressed: $command")
            if (!UltronSettings.privacyMode) taskContext?.recordRecovery("Duplicate dispatch suppressed; re-observe before retry.")
            val message = "I suppressed a duplicate action and re-observed instead."
            onResult?.invoke(false, message) ?: finish(false, message)
            return
        }
        processing = true
        status = "EXECUTING"
        if (!UltronSettings.privacyMode) taskContext?.recordStep("dispatch ${command::class.simpleName}", "started", true)
        // The accessibility service may start activities even while ITRA is in the background.
        val launchContext: Context = UltronAccessibilityService.getInstance() ?: ctx
        CommandExecutor.run(
            launchContext,
            command,
            onStatus = { message -> status = message.uppercase().take(40) },
            onDone = { ok, text ->
                if (!UltronSettings.privacyMode) taskContext?.recordStep(command::class.simpleName.orEmpty(), text, ok)
                if (!ok && !UltronSettings.privacyMode) taskContext?.recordRecovery("Action failed; use a fresh observation/re-plan before retrying.")
                if (onResult != null) onResult(ok, text) else finish(ok, text)
            }
        )
    }

    private fun finish(ok: Boolean, text: String) {
        taskContext?.clear()
        activeAgentGoal = null
        agentToolSteps = 0
        assistantText = text
        if (!UltronSettings.privacyMode) sessionId?.let { com.ultron.app.memory.SessionManager.addMessage("assistant", text, it) }
        processing = false
        status = idleStatus()
        lastActiveAt = System.currentTimeMillis()
        if (!UltronSettings.privacyMode) {
            if (ok) LogStore.i("ITRA", text) else LogStore.w("ITRA", text)
        }
        voice?.speak(text)
    }

    private fun scheduleConversationTimeout() {
        if (!handsFree) return
        if (timeoutHandler == null) timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        timeoutRunnable?.let { timeoutHandler?.removeCallbacks(it) }
        val r = Runnable {
            if (handsFree) {
                val idleFor = System.currentTimeMillis() - lastActiveAt
                if (!processing && idleFor >= CONVERSATION_TIMEOUT_MS) {
                    LogStore.i("VOICE", "200-second inactivity timeout; returning to standby.")
                    setHandsFree(false)
                    status = "STANDBY"
                    voice?.speak("Returning to standby, Sir.")
                } else {
                    val remaining = (CONVERSATION_TIMEOUT_MS - idleFor).coerceAtLeast(5_000L)
                    timeoutRunnable?.let { timeoutHandler?.postDelayed(it, remaining) }
                }
            }
        }
        timeoutRunnable = r
        timeoutHandler?.postDelayed(r, CONVERSATION_TIMEOUT_MS)
    }

    private fun cancelConversationTimeout() {
        timeoutRunnable?.let { timeoutHandler?.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun askLlm(spoken: String, isToolFollowUp: Boolean = false) {
        val client = llm ?: return
        if (activeAgentGoal == null) {
            activeAgentGoal = spoken
            agentToolSteps = 0
            if (!UltronSettings.privacyMode) taskContext?.begin(spoken)
        }
        val plannerContext = com.ultron.app.agent.UniversalAgentPlanner.promptContext(activeAgentGoal ?: spoken)
        client.processUserMessage(
            userMessage = spoken,
            allowWebSearch = !isToolFollowUp,
            conversationContext = buildString {
                val recent = conversation.joinToString("\n") { "USER: ${it.first}\nITRA: ${it.second}" }
                if (recent.isNotBlank()) append(recent)
                append("\n\n").append(plannerContext)
                if (!UltronSettings.privacyMode) taskContext?.summary()?.takeIf { it.isNotBlank() }?.let { append("\n\nTASK STATE:\n").append(it) }
                val memories = if (!UltronSettings.privacyMode) com.ultron.app.memory.LongTermMemoryManager.contextFor(spoken, 8) else ""
                if (memories.isNotBlank()) append("\nLOCAL MEMORY:\n").append(memories)
            },
            onText = { text ->
                assistantText += text
                responseForSpeech.append(text)
                status = "ITRA"
            },
            onToolCall = { tool ->
                LogStore.a("LLM", "Tool call: ${tool.name}")
                val command = ToolRegistry.toCommand(tool)
                processing = false
                when {
                    command != null && !ModeManager.allowsCommand(command) ->
                        finish(true, "That action is not available in ${ModeManager.mode.name.lowercase()} mode, Sir. Switch to medium or extreme mode if you want to allow it.")
                    command is Command.SendMessage -> {
                        activeAgentGoal = null
                        prepareSend(command)
                    }
                    command != null -> execute(command) { ok, result ->
                        if (!ok) {
                            activeAgentGoal = null
                            finish(false, result)
                        } else if (agentToolSteps < maxAgentToolSteps) {
                            agentToolSteps++
                            val goal = activeAgentGoal.orEmpty()
                            responseForSpeech.setLength(0)
                            askLlm("Continue fulfilling this original request: $goal\n\nThe previous action completed successfully. Its verified result was: $result\n\nDecide whether another supported, safe action is needed. If the goal is complete, answer the user. Do not repeat completed actions. Ask before consequential actions.", isToolFollowUp = true)
                        } else {
                            activeAgentGoal = null
                            finish(true, "Completed the supported actions. I stopped the automatic action chain after $maxAgentToolSteps steps for safety. Result: $result")
                        }
                    }
                    else -> { activeAgentGoal = null; finish(false, "I don't know how to do ${tool.name}.") }
                }
            },
            onStatus = { message ->
                status = message.uppercase()
                LogStore.i("ITRA", message)
            },
            onComplete = { hasToolCall ->
                if (!hasToolCall) {
                    processing = false
                    val reply = responseForSpeech.toString().trim()
                    val completedGoal = activeAgentGoal ?: spoken
                    activeAgentGoal = null
                    agentToolSteps = 0
                    if (reply.isNotBlank()) {
                        if (!UltronSettings.privacyMode) {
                            sessionId?.let { com.ultron.app.memory.SessionManager.addMessage("assistant", reply, it) }
                        }
                        lastActiveAt = System.currentTimeMillis()
                        conversation.addLast(completedGoal to reply)
                        while (conversation.size > 8) conversation.removeFirst()
                        voice?.speak(reply)
                    }
                    status = idleStatus()
                }
            },
            onError = { error ->
                processing = false
                status = idleStatus()
                activeAgentGoal = null
                agentToolSteps = 0
                assistantText = "I couldn't get an answer: ${error.message ?: "unknown error"}"
                LogStore.e("LLM", "Request failed", error)
            }
        )
    }
}
