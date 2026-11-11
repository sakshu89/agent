package com.ultron.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import com.ultron.app.tools.AppResolver
import com.ultron.app.agent.AutomationStateMachine
import com.ultron.app.agent.UniversalAgentLoop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drives other apps through the accessibility service. Works with any messaging
 * app that has a search box and a text field (WhatsApp, Telegram, Instagram,
 * Messenger, Signal, SMS apps, ...), and can start the first YouTube result.
 *
 * The screen is re-read from rootInActiveWindow on every poll, because apps
 * replace their views constantly and cached nodes go stale.
 */
class AutomationManager(
    private val context: Context,
    private val service: AccessibilityService
) {
    companion object {
        private const val TAG = "AUTOMATION"
        private const val POLL_MS = 350L
        private const val SETTLE_MS = 900L
        private const val CLICK_COOLDOWN_MS = 850L
        private const val CLICK_SETTLE_MS = 120L
        private const val CLICK_VERIFY_MS = 750L
        private const val MAX_ACTIONS_PER_SECOND = 5

        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val TELEGRAM_PACKAGE = "org.telegram.messenger"
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
    }

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var activeJob: Job? = null
    private val stateMachine = AutomationStateMachine()
    private val universalAgentLoop = UniversalAgentLoop(
        rootProvider = { service.rootInActiveWindow },
        nodesProvider = { root -> allNodes(root) },
        action = { node -> clickNode(node) },
        fingerprint = { screenFingerprint() }
    )

    // Reliability guard: Accessibility callbacks and UI polling can expose the same
    // node multiple times before the previous action has settled. Keep click execution
    // serialized and make identical clicks idempotent for a short, target-aware window.
    private val clickGuard = Any()
    private var clickInFlight = false
    private var lastClickSignature: String? = null
    private var lastClickAt = 0L
    private val recentActionTimes = ArrayDeque<Long>()

    private class AutomationException(message: String) : Exception(message)

    fun cancelCurrentTask(): Boolean {
        val job = activeJob ?: return false
        if (!job.isActive) return false
        job.cancel()
        activeJob = null
        LogStore.w(TAG, "Emergency stop cancelled the active automation task.")
        return true
    }

    // ------------------------------------------------------------------ messaging

    fun sendAppMessage(
        appPackage: String,
        contactName: String,
        message: String,
        onStatus: (String) -> Unit = {},
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        var finished = false
        fun finish(ok: Boolean, text: String) {
            if (finished) return
            finished = true
            if (ok) LogStore.a(TAG, text) else LogStore.e(TAG, text)
            onStatus(text)
            onDone(ok, text)
        }
        fun step(text: String) {
            LogStore.a(TAG, text)
            onStatus(text)
        }

        if (contactName.isBlank() || message.isBlank()) {
            finish(false, "Contact and message are required.")
            return
        }

        if (activeJob?.isActive == true) {
            onDone(false, "Another automation task is already running.")
            return
        }
        val execution = stateMachine.start("send_message:$appPackage:$contactName:$message")
        if (execution == null) { onDone(false, "Another automation task is already running."); return }
        stateMachine.transition(execution.id, AutomationStateMachine.State.EXECUTING)
        activeJob = scope.launch {
            val label = AppResolver.labelOf(context, appPackage) ?: appPackage
            try {
                step("Opening $label")
                launchApp(appPackage)
                if (!waitForPackage(appPackage, 15000)) {
                    throw AutomationException("$label did not open.")
                }
                delay(SETTLE_MS + 500)

                if (appPackage == INSTAGRAM_PACKAGE) {
                    step("Opening Instagram messages")
                    val inbox = waitFor(appPackage, 4000) { findByLabel(it, listOf("messenger", "direct", "messages", "inbox")) }
                    if (inbox != null) {
                        clickNode(inbox)
                        delay(SETTLE_MS)
                    } else {
                        LogStore.w(TAG, "Instagram inbox button not found; continuing from the current screen.")
                    }
                }

                step("Searching for $contactName")
                openSearchAndType(appPackage, contactName)
                delay(SETTLE_MS + 400)

                step("Selecting $contactName")
                val contact = waitFor(appPackage, 10000) { findContactResult(it, contactName) }
                    ?: throw AutomationException("Contact \"$contactName\" was not found in $label.")
                if (!clickNode(contact)) throw AutomationException("Could not open the chat with $contactName.")
                delay(SETTLE_MS + 300)

                step("Typing message")
                val field = waitFor(appPackage, 10000) { findMessageField(it) }
                    ?: throw AutomationException("Message box not found in $label.")
                if (!enterText(field, message)) throw AutomationException("Could not type the message.")
                delay(600)

                step("Sending")
                val send = waitFor(appPackage, 7000) { findSendButton(it) }
                    ?: throw AutomationException("Send button not found in $label.")
                if (!clickNode(send, requireStateChange = false)) throw AutomationException("Could not press Send.")
                delay(400)

                stateMachine.transition(execution.id, AutomationStateMachine.State.VERIFYING)
                val verified = waitFor(appPackage, 2500) { root ->
                    findMessageEvidence(root, message)
                }
                if (verified == null) throw AutomationException("Send action completed, but I could not verify the message in the conversation.")
                stateMachine.transition(execution.id, AutomationStateMachine.State.COMPLETED)
                finish(true, "Message sent to $contactName on $label.")
            } catch (c: CancellationException) {
                stateMachine.cancel(execution.id)
                finish(false, "Action cancelled.")
                throw c
            } catch (t: Throwable) {
                stateMachine.transition(execution.id, AutomationStateMachine.State.FAILED)
                finish(false, "Automation failed: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                stateMachine.clearIfTerminal(execution.id)
            }
        }
    }

    // ------------------------------------------------------------------ Instagram inbox

    fun openInstagramInbox(
        onStatus: (String) -> Unit = {},
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                onStatus("Opening Instagram")
                launchApp(INSTAGRAM_PACKAGE)
                if (!waitForPackage(INSTAGRAM_PACKAGE, 15000)) {
                    throw AutomationException("Instagram did not open.")
                }
                delay(SETTLE_MS + 500)
                onStatus("Opening Instagram messages")
                val inbox = waitFor(INSTAGRAM_PACKAGE, 7000) {
                    findByLabel(it, listOf("messenger", "direct", "messages", "inbox"))
                } ?: throw AutomationException("I couldn't find the Instagram messages button on this screen.")
                if (!clickNode(inbox)) throw AutomationException("I couldn't open Instagram messages.")
                delay(SETTLE_MS)
                onDone(true, "Opened Instagram messages.")
            } catch (c: CancellationException) {
                onDone(false, "Action cancelled.")
                throw c
            } catch (t: Throwable) {
                onDone(false, "Couldn't open Instagram messages: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    // ------------------------------------------------------------------ YouTube

    /** Call after the YouTube results page was opened. Taps the first video result. */
    fun playFirstYouTubeResult(
        onStatus: (String) -> Unit = {},
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        var finished = false
        fun finish(ok: Boolean, text: String) {
            if (finished) return
            finished = true
            if (ok) LogStore.a(TAG, text) else LogStore.e(TAG, text)
            onStatus(text)
            onDone(ok, text)
        }

        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                LogStore.a(TAG, "Waiting for YouTube results")
                onStatus("Waiting for YouTube")
                if (!waitForPackage(AppResolver.YOUTUBE, 15000)) {
                    throw AutomationException("YouTube did not open.")
                }
                delay(1800)
                val video = waitFor(AppResolver.YOUTUBE, 15000) { findFirstVideo(it) }
                    ?: throw AutomationException("No video result found to play.")
                LogStore.a(TAG, "Tapping first video result")
                onStatus("Starting video")
                if (!clickNode(video)) throw AutomationException("Could not tap the video.")
                delay(600)
                finish(true, "Playing it on YouTube.")
            } catch (c: CancellationException) {
                finish(false, "Action cancelled.")
                throw c
            } catch (t: Throwable) {
                finish(false, "Could not start the video: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    // ------------------------------------------------------------------ universal research

    /**
     * Opens a normal web search, submits it through the current browser/search UI,
     * then re-observes the screen and reads the result using accessibility/OCR.
     * The flow is intentionally topic- and app-agnostic.
     */
    fun researchWeb(
        query: String,
        onStatus: (String) -> Unit = {},
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            onDone(false, "Tell me what you want me to research.")
            return
        }
        if (activeJob?.isActive == true) {
            onDone(false, "Another automation task is already running.")
            return
        }
        activeJob = scope.launch {
            try {
                onStatus("Opening web search")
                val uri = Uri.parse("https://www.google.com/search").buildUpon()
                    .appendQueryParameter("q", cleanQuery)
                    .build()
                val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                service.startActivity(intent)
                delay(SETTLE_MS + 700L)

                onStatus("Reading answer")
                val chunks = ArrayList<String>()
                repeat(3) { page ->
                    val root = service.rootInActiveWindow
                    if (root != null) {
                        val text = accessibleScreenText(root)
                        if (text.isNotBlank()) chunks.add(text)
                    }
                    if (page < 2) {
                        delay(500L)
                        scrollDownOnce()
                        delay(SETTLE_MS)
                    }
                }

                onStatus("Checking visual text")
                ScreenUnderstanding.captureAndRead(service) { result ->
                    val visual = result.getOrNull().orEmpty()
                    if (visual.isNotBlank()) chunks.add(visual)
                    val answer = ResearchAnswerSynthesis.summarize(cleanQuery, chunks)
                    if (answer.isBlank()) {
                        onDone(false, "I opened the search for $cleanQuery, but I couldn't reliably read an answer from the current screen.")
                    } else {
                        onDone(true, answer)
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                LogStore.e(TAG, "Universal research failed", t)
                onDone(false, "I couldn't research that right now: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    private fun accessibleScreenText(root: AccessibilityNodeInfo): String =
        allNodes(root)
            .asSequence()
            .filter { it.isVisibleToUser }
            .map { labelOf(it).replace(Regex("\\s+"), " ").trim() }
            .filter { it.length >= 2 }
            .distinct()
            .take(180)
            .joinToString("\n")

    /** Universal submit fallback for an editable search/form field.
     *
     * Order is deliberately conservative: use the IME action first, then a visible
     * submit/search control found from the fresh accessibility tree. We never press
     * Enter repeatedly and never treat a generic field click as a submission.
     */
    private suspend fun submitEditableField(node: AccessibilityNodeInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val imeEnter = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
            if (node.performAction(imeEnter)) return true
        }

        val root = service.rootInActiveWindow ?: return false
        val submit = allNodes(root).firstOrNull { candidate ->
            if (!candidate.isVisibleToUser || candidate.isEditable) return@firstOrNull false
            val label = labelOf(candidate).trim().lowercase()
            val id = candidate.viewIdResourceName.orEmpty().lowercase()
            val looksLikeSubmit = label in setOf("search", "go", "submit", "done", "enter") ||
                id.contains("search") || id.contains("submit") || id.contains("go")
            looksLikeSubmit && (candidate.isClickable || candidate.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
        }
        return submit?.let { clickNode(it) } == true
    }

    private fun scrollDownOnce(): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val target = allNodes(root).firstOrNull { it.isScrollable && it.isVisibleToUser } ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    // ------------------------------------------------------------------ screen actions

    /** back, home, scroll_up, scroll_down, skip_ad, or tap (with the visible label in [arg]). */
    fun screenAction(kind: String, arg: String?, onDone: (Boolean, String) -> Unit) {
        synchronized(clickGuard) {
            if (kind == "tap" && clickInFlight) {
                onDone(false, "ITRA is still processing the previous tap.")
                return
            }
        }
        scope.launch {
            try {
                when {
                    kind == "back" -> {
                        val ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        onDone(ok, if (ok) "Going back." else "I couldn't go back.")
                    }
                    kind == "home" -> {
                        val ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        onDone(ok, if (ok) "Going home." else "I couldn't go home.")
                    }
                    kind.startsWith("scroll_") -> {
                        val down = kind.endsWith("down")
                        val root = service.rootInActiveWindow
                        val target = root?.let { r -> allNodes(r).firstOrNull { it.isScrollable && it.isVisibleToUser } }
                        val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                        val ok = target?.performAction(action) == true
                        onDone(ok, if (ok) "Scrolling ${if (down) "down" else "up"}." else "Nothing to scroll here.")
                    }
                    kind == "skip_ad" -> {
                        val node = waitFor(null, 3500) { root ->
                            allNodes(root).firstOrNull {
                                val l = labelOf(it).lowercase()
                                it.isVisibleToUser && !it.isEditable && l.contains("skip") && l.length < 30
                            }
                        }
                        val ok = node != null && clickNode(node)
                        onDone(ok, if (ok) "Skipped." else "I don't see a skip button right now.")
                    }
                    kind == "tap" && !arg.isNullOrBlank() -> {
                        val target = arg.trim()
                        val result = universalAgentLoop.findAndAct(
                            matcher = { node ->
                                !node.isEditable && labelOf(node).contains(target, ignoreCase = true)
                            },
                            maxRecoveryAttempts = 1
                        )
                        val ok = result.success
                        val detail = if (ok) {
                            if (result.recovered) "Tapped $target after re-observing the screen."
                            else "Tapped $target."
                        } else {
                            "I couldn't reliably tap $target: ${result.reason}."
                        }
                        onDone(ok, detail)
                    }
                    else -> onDone(false, "I don't know how to do that on screen.")
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                LogStore.e(TAG, "Screen action failed", t)
                onDone(false, "That didn't work: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    fun cancel() {
        activeJob?.cancel()
    }

    fun destroy() {
        activeJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
    }

    // ------------------------------------------------------------------ steps

    private fun launchApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw AutomationException("App is not installed: $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
    }

    private suspend fun openSearchAndType(pkg: String, contact: String) {
        var field = waitFor(pkg, 1500) { findEditableSearchField(it, requireSearchHint = true) }

        if (field != null) {
            // Some apps show a fake search bar that opens the real one when tapped.
            clickNode(field)
            delay(700)
            field = waitFor(pkg, 4000) { findEditableSearchField(it, requireSearchHint = false) } ?: field
        } else {
            val trigger = waitFor(pkg, 6000) { findSearchTrigger(it) }
                ?: throw AutomationException("Search button not found.")
            if (!clickNode(trigger)) throw AutomationException("Could not open search.")
            delay(SETTLE_MS)
            field = waitFor(pkg, 6000) { findEditableSearchField(it, requireSearchHint = false) }
                ?: throw AutomationException("Search box did not appear.")
        }

        if (!enterText(field, contact)) throw AutomationException("Could not type the contact name.")
        // Some apps search live; others require an explicit submission. Try one
        // universal submit path without treating failure as a reason to press again.
        submitEditableField(field)
    }

    // ------------------------------------------------------------------ waiting

    private suspend fun waitForPackage(pkg: String, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (service.rootInActiveWindow?.packageName?.toString() == pkg) return true
            delay(POLL_MS)
        }
        return false
    }

    private suspend fun waitFor(
        pkg: String?,
        timeoutMs: Long,
        finder: (AccessibilityNodeInfo) -> AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val root = service.rootInActiveWindow
            if (root != null && (pkg == null || root.packageName?.toString() == pkg)) {
                val node = try { finder(root) } catch (_: Exception) { null }
                if (node != null) return node
            }
            delay(POLL_MS)
        }
        return null
    }

    // ------------------------------------------------------------------ finders

    private fun allNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (out.size > 4000 || depth > 40) return
            out.add(node)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                visit(child, depth + 1)
            }
        }
        visit(root, 0)
        return out
    }

    private fun textOf(node: AccessibilityNodeInfo): String = node.text?.toString().orEmpty()
    private fun descOf(node: AccessibilityNodeInfo): String = node.contentDescription?.toString().orEmpty()
    private fun hintOf(node: AccessibilityNodeInfo): String =
        if (Build.VERSION.SDK_INT >= 26) node.hintText?.toString().orEmpty() else ""

    private fun labelOf(node: AccessibilityNodeInfo): String =
        listOf(textOf(node), descOf(node), hintOf(node)).filter { it.isNotBlank() }.joinToString(" ")

    private fun topOf(node: AccessibilityNodeInfo): Int {
        val r = Rect()
        node.getBoundsInScreen(r)
        return r.top
    }

    private fun findByLabel(root: AccessibilityNodeInfo, keys: List<String>): AccessibilityNodeInfo? =
        allNodes(root)
            .filter { it.isVisibleToUser && !it.isEditable }
            .filter { node ->
                val label = (descOf(node) + " " + textOf(node)).lowercase()
                keys.any { label.contains(it) } && label.length < 40
            }
            .minByOrNull { labelOf(it).length }

    private fun findSearchTrigger(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        allNodes(root)
            .filter { it.isVisibleToUser && !it.isEditable }
            .filter { labelOf(it).contains("search", ignoreCase = true) }
            .minByOrNull { labelOf(it).length }

    private fun findEditableSearchField(
        root: AccessibilityNodeInfo,
        requireSearchHint: Boolean
    ): AccessibilityNodeInfo? {
        val editable = allNodes(root).filter { it.isVisibleToUser && it.isEditable }
        val hinted = editable.firstOrNull { labelOf(it).contains("search", ignoreCase = true) }
        if (hinted != null) return hinted
        if (requireSearchHint) return null
        return editable.minByOrNull { topOf(it) }
    }

    /** Finds the just-sent message as a non-editable node in the conversation (proof the send worked). */
    private fun findMessageEvidence(root: AccessibilityNodeInfo, message: String): AccessibilityNodeInfo? {
        fun norm(s: String) = s.replace(Regex("\\s+"), " ").trim().lowercase()
        val target = norm(message)
        if (target.isEmpty()) return null
        val probe = target.take(40)
        return allNodes(root).firstOrNull { node ->
            if (!node.isVisibleToUser || node.isEditable) return@firstOrNull false
            val l = norm(labelOf(node))
            l.isNotEmpty() && (l.contains(target) || (probe.length >= 8 && l.contains(probe)))
        }
    }

    private fun findMessageField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val editable = allNodes(root).filter { it.isVisibleToUser && it.isEditable }
        if (editable.isEmpty()) return null
        val keys = listOf("message", "type", "write", "reply", "text")
        val labelled = editable.filter { node ->
            val l = labelOf(node).lowercase()
            !l.contains("search") && keys.any { l.contains(it) }
        }
        if (labelled.isNotEmpty()) return labelled.maxByOrNull { topOf(it) }
        val notSearch = editable.filter { !labelOf(it).contains("search", ignoreCase = true) }
        return (if (notSearch.isNotEmpty()) notSearch else editable).maxByOrNull { topOf(it) }
    }

    private fun findContactResult(root: AccessibilityNodeInfo, contact: String): AccessibilityNodeInfo? {
        val target = contact.trim().lowercase()
        val firstName = target.split(' ').firstOrNull { it.length >= 3 }
        val nodes = allNodes(root).filter { it.isVisibleToUser && !it.isEditable && textOf(it).isNotBlank() }

        fun ranked(list: List<AccessibilityNodeInfo>) =
            list.sortedWith(compareBy({ textOf(it).length }, { topOf(it) }))

        val exact = nodes.filter { textOf(it).trim().lowercase() == target }
        if (exact.isNotEmpty()) return ranked(exact).first()

        val contains = nodes.filter { textOf(it).lowercase().contains(target) }
        if (contains.isNotEmpty()) return ranked(contains).first()

        if (firstName != null) {
            val partial = nodes.filter { textOf(it).lowercase().contains(firstName) }
            // Only accept a first-name match when it is a single, unambiguous contact. Otherwise we could message the wrong person.
            if (partial.map { textOf(it).trim().lowercase() }.distinct().size == 1) return ranked(partial).first()
        }
        return null
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val nodes = allNodes(root).filter { it.isVisibleToUser && !it.isEditable }
        nodes.firstOrNull {
            val l = (descOf(it) + " " + textOf(it)).trim().lowercase()
            l == "send" || l == "send message"
        }?.let { return it }
        nodes.firstOrNull {
            (it.viewIdResourceName ?: "").lowercase().let { id -> id.contains("send") && !id.contains("sending") }
        }?.let { return it }
        return nodes.firstOrNull {
            val l = (descOf(it) + " " + textOf(it)).lowercase()
            l.contains("send") && l.length < 25 && !l.contains("sending")
        }
    }

    private fun findFirstVideo(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val nodes = allNodes(root).filter { it.isVisibleToUser && !it.isEditable }
        val byPlay = nodes.filter {
            val d = descOf(it).lowercase()
            d.contains("play video") && !d.contains("short")
        }
        val pool = byPlay.ifEmpty {
            nodes.filter {
                val d = descOf(it).lowercase()
                d.length > 25 && d.contains("views") && !d.contains("short") && !d.contains("sponsored")
            }
        }
        return pool.filter { topOf(it) > 0 }.minByOrNull { topOf(it) }
    }

    // ------------------------------------------------------------------ actions

    private fun enterText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private suspend fun clickNode(node: AccessibilityNodeInfo, requireStateChange: Boolean = true): Boolean {
        val signature = nodeSignature(node)
        val now = SystemClock.uptimeMillis()
        synchronized(clickGuard) {
            if (clickInFlight) {
                LogStore.w(TAG, "Ignoring click while another click is settling.")
                return false
            }
            if (signature == lastClickSignature && now - lastClickAt < CLICK_COOLDOWN_MS) {
                LogStore.w(TAG, "Suppressing duplicate click: $signature")
                return false
            }
            while (recentActionTimes.isNotEmpty() && now - recentActionTimes.first() > 1000L) {
                recentActionTimes.removeFirst()
            }
            if (recentActionTimes.size >= MAX_ACTIONS_PER_SECOND) {
                LogStore.w(TAG, "Action-rate guard blocked a click.")
                return false
            }
            clickInFlight = true
            lastClickSignature = signature
            lastClickAt = now
            recentActionTimes.addLast(now)
        }

        try {
            val before = screenFingerprint()
            var clicked = false
            var current: AccessibilityNodeInfo? = node
            var depth = 0
            while (current != null && depth++ < 8) {
                if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    clicked = true
                    break
                }
                current = current.parent
            }
            if (!clicked) {
                val r = Rect()
                node.getBoundsInScreen(r)
                if (!r.isEmpty) clicked = tapAt(r.exactCenterX(), r.exactCenterY())
            }
            if (!clicked) return false

            // Give the target app one frame to process the action, then observe again.
            // We intentionally do NOT retry here: retrying an unchanged UI is what causes
            // runaway repeated taps and phone lag.
            delay(CLICK_SETTLE_MS)
            val deadline = SystemClock.uptimeMillis() + CLICK_VERIFY_MS
            if (!requireStateChange) return true
            while (SystemClock.uptimeMillis() < deadline) {
                val after = screenFingerprint()
                if (after != before) return true
                delay(100L)
            }
            LogStore.w(TAG, "Click dispatched but screen did not change; no automatic re-click.")
            return false
        } finally {
            synchronized(clickGuard) { clickInFlight = false }
        }
    }

    private fun nodeSignature(node: AccessibilityNodeInfo): String {
        val r = Rect()
        node.getBoundsInScreen(r)
        return listOf(
            node.packageName?.toString().orEmpty(),
            node.viewIdResourceName.orEmpty(),
            textOf(node).trim().lowercase(),
            descOf(node).trim().lowercase(),
            node.className?.toString().orEmpty(),
            r.left, r.top, r.right, r.bottom
        ).joinToString("|")
    }

    /** Small deterministic fingerprint used only for immediate action verification. */
    private fun screenFingerprint(): String {
        val root = service.rootInActiveWindow ?: return "<no-root>"
        return allNodes(root)
            .asSequence()
            .filter { it.isVisibleToUser }
            .take(180)
            .map { nodeSignature(it) }
            .joinToString(";")
            .hashCode()
            .toString()
    }

    private fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return service.dispatchGesture(gesture, null, null)
    }
}
