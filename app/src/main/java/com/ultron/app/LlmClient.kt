package com.ultron.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ultron.app.ai.CloudModelConfig
import com.ultron.core.ai.HybridModelRouter
import com.ultron.core.ai.ModelMode
import com.ultron.core.ai.ModelRequest
import com.ultron.core.ai.ModelRoute
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit

class LlmClient(context: android.content.Context) {
    private val appContext = context.applicationContext
    private val baseUrl: String get() = LocalServerConfig.getBaseUrl(appContext)

    companion object {
        private const val TAG = "ITRA_LLM"
        private const val ENDPOINT = "/v1/chat/completions"

        const val SYSTEM_PROMPT = """
You are ITRA, a voice assistant running on the user's Android phone.

You can act on the phone by calling tools:
- send_message_tool: send one message to a contact in any messaging app.
- open_app_tool: open any installed app.
- play_media_tool: play a song, artist or video (YouTube by default).
- search_tool: search Google, YouTube, Maps or the Play Store.
- screen_action_tool: perform safe, explicitly requested visible-screen actions (back, home, scroll, skip ad, or tap a named control).
- read_screen_tool: use on-device OCR to read the current screen when the user asks about visible content.
- system_control_tool: change supported ringer mode, media volume or brightness using official Android APIs.
- open_url_tool: open a public http/https URL explicitly supplied or clearly requested by the user.

You cannot power off, restart, lock, or otherwise control Android system power. Never claim that you performed such an action. Explain the limitation and direct the user to the phone's system power menu.

When the user asks for one of these actions, call the matching tool immediately instead of describing it.
You are multilingual: reply in the language used by the user, including code-switching when natural. Do not claim perfect understanding; if a phrase is ambiguous, ask one short clarifying question in that language.
You have an ITRA 8.0 agent layer above the tools. For complex goals, reason as Goal -> Plan -> Execute -> Verify -> Recover -> Re-plan. Use the supplied AGENT 8.0 PLAN and TASK STATE as hints, not as proof that an action happened. Break work into a short ordered plan, perform only supported actions, verify each result from tool feedback, and continue only when the next step is safe. Never repeat an action merely because the previous call was not visibly acknowledged; re-observe or choose a bounded recovery path first. Never claim an action succeeded unless the tool confirms it. Ask before irreversible, financial, privacy-sensitive, or externally visible actions (sending, posting, purchasing, deleting, sharing, changing account/security settings). Never bypass Android permission prompts or app safeguards.
Do not invent tools, screen contents, app capabilities, contacts, locations, or results. If a task is not supported on Android, explain the limitation and offer the closest safe supported action.
For messages, preserve the user's intended message exactly. Do not rewrite, shorten, translate, add punctuation, or add a signature.
Never invent a contact name or message. If required information is missing, ask the user instead of calling the tool.
For educational and knowledge questions, use the supplied Google search evidence when available and explain the answer in simple language.
For spoken answers, lead with the direct answer, use short sentences, avoid reading search-result lists or website navigation text, and keep the response concise unless the user asks for detail.
DEFAULT RESPONSE STYLE: Give only the answer the user asked for. Do not narrate your thinking, plans, tool calls, searches, URLs, sources, internal status, execution traces, or implementation details. Never say "here is what I found" or describe browsing. Do not include raw links unless the user asks for a link. For a normal question, answer in 1-4 natural sentences. For a task, briefly confirm only what was actually completed. Ask one concise question only when required information is missing.
For everything else, answer briefly and conversationally.
"""
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val googleSearchClient = GoogleSearchClient()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun processUserMessage(
        userMessage: String,
        conversationContext: String = "",
        onText: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onError: (Throwable) -> Unit,
        onStatus: (String) -> Unit = {},
        onComplete: (Boolean) -> Unit = {},
        allowWebSearch: Boolean = true
    ) {
        if (userMessage.isBlank()) {
            postError(onError, IllegalArgumentException("User message is empty."))
            return
        }

        // Explicit tool requests, internal tool-result follow-ups and stable questions use the
        // local backend directly. Current-information questions require web evidence.
        val needsFresh = allowWebSearch && mentionsFreshTopic(userMessage)
        val educational = allowWebSearch && !needsFresh && isEducationalQuestion(userMessage)
        val knowledgeRequest = allowWebSearch && isKnowledgeRequest(userMessage)
        if (!allowWebSearch || isMessageAutomationRequest(userMessage) || (!needsFresh && !educational && !knowledgeRequest)) {
            requestLlm(userMessage, conversationContext, onText, onToolCall, onError, onStatus, onComplete)
            return
        }

        postStatus(onStatus, "SEARCHING WEB")
        googleSearchClient.search(
            userMessage,
            onSuccess = { evidence ->
                val groundedPrompt = buildWebGroundedPrompt(userMessage, evidence)
                postStatus(onStatus, "READING RESULTS")
                requestLlm(groundedPrompt, conversationContext, onText, onToolCall, onError, onStatus, onComplete)
            },
            onError = { error ->
                postStatus(onStatus, "WEB SEARCH UNAVAILABLE")
                if (educational || knowledgeRequest) {
                    // Stable "what is / how does" questions can still be answered from the model's own
                    // knowledge. Only genuinely current information (news, prices, weather...) must fail.
                    LogStore.w("LLM", "Web search failed (${error.message}); answering from the local model.")
                    requestLlm(userMessage, conversationContext, onText, onToolCall, onError, onStatus, onComplete)
                } else {
                    postError(onError, IllegalStateException(
                        "I couldn't verify current information because web search failed. ${error.message}", error
                    ))
                }
            }
        )
    }

    private fun requestLlm(
        userMessage: String,
        conversationContext: String = "",
        onText: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onError: (Throwable) -> Unit,
        onStatus: (String) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        val mode = CloudModelConfig.mode(appContext)
        val privateContent = UltronSettings.privacyMode || looksSensitive(userMessage) || looksSensitive(conversationContext)
        val route = HybridModelRouter.choose(
            mode,
            ModelRequest("chat", privateContent, mode != ModelMode.LOCAL, true, emptyMap()),
            CloudModelConfig.hasKey(appContext)
        )
        if (mode == ModelMode.CLOUD && route.backend() != ModelRoute.Backend.CLOUD) {
            val reason = if (privateContent) "Cloud blocked by Privacy Mode or sensitive-content protection." else "Cloud mode needs a valid HTTPS endpoint, model and encrypted API key."
            postError(onError, IllegalStateException(reason))
            return
        }
        if (route.backend() == ModelRoute.Backend.CLOUD) {
            executeRequest(userMessage, conversationContext, true, onText, onToolCall, onError, onStatus, onComplete, null)
        } else {
            executeRequest(userMessage, conversationContext, false, onText, onToolCall, onError, onStatus, onComplete, route, skipHermes = requiresNativeAndroidTools(userMessage))
        }
    }

    private fun executeRequest(
        userMessage: String, conversationContext: String, cloud: Boolean,
        onText: (String) -> Unit, onToolCall: (ToolCall) -> Unit,
        onError: (Throwable) -> Unit, onStatus: (String) -> Unit,
        onComplete: (Boolean) -> Unit, fallbackRoute: ModelRoute?, skipHermes: Boolean = false
    ) {
        val useHermes = !cloud && !skipHermes && HermesBridgeConfig.isEnabled(appContext)
        val endpoint = when {
            cloud -> CloudModelConfig.endpoint(appContext).trimEnd('/') + ENDPOINT
            useHermes -> HermesBridgeConfig.getBaseUrl(appContext).trimEnd('/') + ENDPOINT
            else -> baseUrl.trimEnd('/') + ENDPOINT
        }
        val model = when {
            cloud -> CloudModelConfig.model(appContext)
            useHermes -> "hermes-agent"
            else -> LocalServerConfig.getModel(appContext)
        }
        val request = try {
            val builder = Request.Builder().url(endpoint)
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
            when {
                cloud -> builder.header("Authorization", "Bearer ${CloudModelConfig.apiKey(appContext)}")
                useHermes -> builder.header("Authorization", "Bearer ${HermesBridgeConfig.getToken(appContext)}")
            }
            builder.post(createRequestBody(userMessage, conversationContext, model)).build()
        } catch (t: Throwable) { postError(onError, t); return }

        postStatus(onStatus, when { cloud -> "THINKING · CLOUD"; useHermes -> "THINKING · HERMES"; else -> "THINKING · LOCAL" })
        LogStore.i("LLM", "POST ${when { cloud -> "configured cloud provider"; useHermes -> "local Hermes bridge"; else -> baseUrl.trimEnd('/') }}$ENDPOINT")
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (useHermes) {
                    LogStore.w("LLM", "Hermes bridge unavailable; falling back to ITRA local model.")
                    executeRequest(userMessage, conversationContext, false, onText, onToolCall, onError, onStatus, onComplete, null, skipHermes = true)
                } else if (!cloud && fallbackRoute != null && HybridModelRouter.shouldFallback(fallbackRoute, 0, true)) {
                    LogStore.w("LLM", "Local transport failed; attempting permitted cloud fallback.")
                    executeRequest(userMessage, conversationContext, true, onText, onToolCall, onError, onStatus, onComplete, null)
                } else {
                    LogStore.e("LLM", "Cannot reach ${if (cloud) "configured cloud provider" else "local model at $baseUrl"}", e)
                    postError(onError, e)
                }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) {
                        val detail = response.body?.string().orEmpty().take(1200)
                        LogStore.e("LLM", "${if (cloud) "Cloud" else "Local"} model returned HTTP ${response.code}: ${detail.take(500)}")
                        if (useHermes) {
                            LogStore.w("LLM", "Hermes bridge returned HTTP ${response.code}; falling back to ITRA local model.")
                            executeRequest(userMessage, conversationContext, false, onText, onToolCall, onError, onStatus, onComplete, null, skipHermes = true)
                        } else if (!cloud && fallbackRoute != null && HybridModelRouter.shouldFallback(fallbackRoute, response.code, false)) {
                            LogStore.w("LLM", "Local server unavailable (HTTP ${response.code}); attempting permitted cloud fallback.")
                            executeRequest(userMessage, conversationContext, true, onText, onToolCall, onError, onStatus, onComplete, null)
                        } else {
                            postError(onError, IOException("${if (cloud) "Cloud" else "Local"} LLM HTTP ${response.code}: ${detail.ifBlank { response.message }}"))
                        }
                        return
                    }
                    val responseBody = response.body ?: run { postError(onError, IOException("LLM returned an empty body.")); return }
                    try {
                        val hasToolCall = readSse(responseBody.byteStream().bufferedReader(), onText, onToolCall, onError)
                        postComplete(onComplete, hasToolCall)
                    } catch (t: Throwable) {
                        LogStore.e("LLM", "Failed to parse model response", t)
                        Log.e(TAG, "Failed to parse LLM response", t)
                        postError(onError, t)
                    }
                }
            }
        })
    }

    /** Native phone actions must stay on ITRA's Android tool path; Hermes cannot dispatch APK tools. */
    private fun requiresNativeAndroidTools(text: String): Boolean {
        val action = Regex("\\b(open|launch|play|send|message|text|call|tap|click|scroll|swipe|press|back|home|ringer|brightness|volume|notification|alarm|read screen|screenshot|skip ad)\\b", RegexOption.IGNORE_CASE)
        return action.containsMatchIn(text) || isMessageAutomationRequest(text)
    }

    private fun looksSensitive(value: String): Boolean {
        if (value.isBlank()) return false
        val lower = value.lowercase()
        return listOf("password", "passcode", "otp", "one-time code", "api key", "secret", "bank account", "card number")
            .any { lower.contains(it) } || Regex("\\b[0-9]{10,}\\b").containsMatchIn(value) ||
            Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE).containsMatchIn(value)
    }



    private fun normalizeQuestion(text: String): String = text.lowercase()
        .replace("how's ", "how does ")
        .replace("what's ", "what is ")
        .replace("who's ", "who is ")

    /** Current, changing information (news, prices, weather...) that must come from the web. */
    private fun mentionsFreshTopic(text: String): Boolean {
        val lower = normalizeQuestion(text)
        val indicators = listOf(
            "weather", "latest", "current", "today", "tonight", "now",
            "news", "recent", "price", "stock", "score", "live",
            "this week", "tomorrow", "forecast", "trending"
        )
        // Whole words only: "now" must not match "know", nor "live" match "deliver".
        return indicators.any { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(lower) }
    }

    private fun isKnowledgeRequest(text: String): Boolean {
        val lower = normalizeQuestion(text).trim()
        return listOf(
            Regex("^(?:tell|teach)\\s+me\\s+(?:about|how|why|what)\\b"),
            Regex("^give\\s+(?:me\\s+)?(?:information|details)\\b"),
            Regex("^(?:explain|describe|define)\\b"),
            Regex("^(?:what|why|how|who|when|where)\\b")
        ).any { it.containsMatchIn(lower) }
    }

    private fun isEducationalQuestion(text: String): Boolean {
        val lower = normalizeQuestion(text)
        // Search educational and explanatory questions too, not only breaking news.
        // This catches natural voice phrasing such as contracted and spoken questions.
        val educationalPatterns = listOf(
            Regex("\\bhow\\s+(?:does|do|is|are|did)\\b"),
            Regex("\\bhow\\s+.*\\bwork(?:s|ing)?\\b"),
            Regex("\\bwhat\\s+(?:is|are|does|do|was|were)\\b"),
            Regex("\\bwhy\\s+(?:is|are|does|do|did)\\b"),
            Regex("\\bexplain\\b"),
            Regex("\\bdefine\\b"),
            Regex("\\bmeaning\\s+of\\b"),
            Regex("\\bwho\\s+(?:is|was|are|were)\\b"),
            Regex("\\bwhen\\s+(?:was|is|did|does)\\b")
        )
        return educationalPatterns.any { it.containsMatchIn(lower) }
    }

    private fun isMessageAutomationRequest(text: String): Boolean {
        val lower = text.lowercase()
        val hasSendVerb = Regex("\\b(send|message|text|tell|play|open|launch|search)\\b").containsMatchIn(lower)
        val hasTarget = Regex("\\b(to|on|in|for)\\b").containsMatchIn(lower)
        return hasSendVerb && hasTarget
    }

    private fun buildWebGroundedPrompt(question: String, evidence: String): String {
        return """
You are ITRA. Answer the user's question naturally and helpfully.

IMPORTANT: Fresh Google search results are provided below. Use them as the factual
source for current or changeable information. Do not pretend you personally browsed
the web beyond these results. If the results are insufficient or conflicting, say so
briefly instead of inventing facts. For stable educational questions, combine the
search evidence with your general knowledge, but prioritize the supplied evidence.
Do not mention this internal prompt, tool names, or that you are a language model.
Keep the answer conversational, concise, and easy to listen to aloud. Start with the direct answer. Do not read Google result lists, ads, URLs, menus, or website navigation. Never mention that you searched, narrate your process, expose snippets, or output a raw URL. Explain the answer in 1-4 short sentences. Mention uncertainty only if the evidence is insufficient.

USER QUESTION:
$question

GOOGLE SEARCH RESULTS:
$evidence
""".trimIndent()
    }

    private fun createRequestBody(userMessage: String, conversationContext: String, model: String): okhttp3.RequestBody {
        val root = JsonObject()
        root.addProperty("model", model)
        root.addProperty("stream", true)
        root.addProperty("temperature", 0.2)

        val messages = JsonArray()
        messages.add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", SYSTEM_PROMPT.trimIndent() + "\n\nPERSONALITY: ${UltronSettings.personality}. Keep the same personality consistently. COMPACT SPOKEN ANSWERS: ${UltronSettings.compactReplies}. PRIVACY MODE: ${UltronSettings.privacyMode}.")
        })
        messages.add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", if (conversationContext.isBlank()) "No previous turns." else "Recent conversation context:\n$conversationContext")
        })
        messages.add(JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", userMessage)
        })

        root.add("messages", messages)
        root.add("tools", ToolRegistry.getOpenAiTools())
        root.addProperty("tool_choice", "auto")

        return root.toString().toRequestBody("application/json".toMediaType())
    }

    private fun readSse(
        reader: BufferedReader,
        onText: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onError: (Throwable) -> Unit
    ): Boolean {
        val calls = mutableMapOf<Int, MutableToolCall>()

        while (true) {
            val line = reader.readLine() ?: break
            if (!line.trimStart().startsWith("data:")) continue

            val data = line.trim().removePrefix("data:").trim()
            if (data.isBlank()) continue
            if (data == "[DONE]") break

            val root = try {
                JsonParser.parseString(data).asJsonObject
            } catch (_: Exception) {
                // Some servers may emit comments/keep-alives. Ignore invalid SSE data.
                continue
            }

            val choices = root.getAsJsonArray("choices") ?: continue
            if (choices.size() == 0) continue

            val choice = choices[0].asJsonObject
            val delta = choice.getAsJsonObject("delta")

            delta?.get("content")
                ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                ?.asString
                ?.let { if (it.isNotEmpty()) postText(onText, it) }

            delta?.getAsJsonArray("tool_calls")?.let { toolArray ->
                for (element in toolArray) {
                    val tool = element.asJsonObject
                    val index = tool.get("index")?.asInt ?: 0
                    val acc = calls.getOrPut(index) { MutableToolCall() }

                    tool.get("id")
                        ?.takeIf { !it.isJsonNull }
                        ?.asString
                        ?.let { acc.id = it }

                    tool.getAsJsonObject("function")?.let { function ->
                        function.get("name")
                            ?.takeIf { !it.isJsonNull }
                            ?.asString
                            ?.let { acc.name = it }

                        function.get("arguments")
                            ?.takeIf { !it.isJsonNull }
                            ?.asString
                            ?.let { acc.arguments.append(it) }
                    }
                }
            }
        }

        var dispatchedToolCall = false
        for (call in calls.values) {
            val name = call.name ?: continue
            if (!ToolRegistry.isKnownTool(name)) continue

            val rawArguments = call.arguments.toString().trim()
            if (rawArguments.isBlank()) {
                postError(onError, IllegalArgumentException("Tool call contained empty arguments."))
                continue
            }

            val args = try {
                JsonParser.parseString(rawArguments).asJsonObject
            } catch (t: Throwable) {
                postError(onError, IllegalArgumentException("Invalid tool arguments from LLM.", t))
                continue
            }

            fun arg(key: String) = args.get(key)?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
            val appName = arg("app_name")
            val contactName = arg("contact_name")
            val message = args.get("message")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            val query = arg("query")
            val action = arg("action")
            val target = arg("target")
            val url = arg("url")

            val complete = when (name) {
                ToolRegistry.SEND_MESSAGE_TOOL -> contactName.isNotBlank() && message.isNotBlank()
                ToolRegistry.OPEN_APP_TOOL -> appName.isNotBlank()
                ToolRegistry.SCREEN_ACTION_TOOL -> action in setOf("back", "home", "scroll_up", "scroll_down", "skip_ad", "tap") && (action != "tap" || target.isNotBlank())
                ToolRegistry.OPEN_URL_TOOL -> url.startsWith("https://", true) || url.startsWith("http://", true)
                ToolRegistry.READ_SCREEN_TOOL -> true
                ToolRegistry.SYSTEM_CONTROL_TOOL -> action in setOf("silent", "vibrate") || (action in setOf("volume", "brightness") && query.toIntOrNull() != null)
                else -> query.isNotBlank()
            }
            if (!complete) {
                postError(onError, IllegalArgumentException("$name has incomplete arguments."))
                continue
            }

            dispatchedToolCall = true
            postToolCall(
                onToolCall,
                ToolCall(
                    id = call.id.orEmpty(),
                    name = name,
                    appName = appName,
                    contactName = contactName,
                    message = message,
                    query = if (name == ToolRegistry.OPEN_URL_TOOL) url else query,
                    action = action,
                    target = target
                )
            )
            // Execute one tool per model turn. The controller feeds the verified result
            // back to the local model before another action can be selected.
            break
        }
        return dispatchedToolCall
    }

    /** Runs a tool call requested by the LLM (or confirmed by the user). */
    fun dispatchToolCall(
        context: android.content.Context,
        toolCall: ToolCall,
        onStatus: (String) -> Unit = {},
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val command = ToolRegistry.toCommand(toolCall)
        if (command == null) {
            onDone(false, "Unknown action: ${toolCall.name}")
            return
        }
        com.ultron.app.tools.CommandExecutor.run(context, command, onStatus, onDone)
    }

    fun shutdown() {
        client.dispatcher.cancelAll()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        googleSearchClient.shutdown()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun postText(callback: (String) -> Unit, text: String) {
        mainHandler.post { callback(text) }
    }

    private fun postToolCall(callback: (ToolCall) -> Unit, toolCall: ToolCall) {
        mainHandler.post { callback(toolCall) }
    }

    private fun postError(callback: (Throwable) -> Unit, error: Throwable) {
        mainHandler.post { callback(error) }
    }

    private fun postStatus(callback: (String) -> Unit, status: String) {
        mainHandler.post { callback(status) }
    }

    private fun postComplete(callback: (Boolean) -> Unit, hasToolCall: Boolean) {
        mainHandler.post { callback(hasToolCall) }
    }

    data class ToolCall(
        val id: String,
        val name: String,
        val appName: String,
        val contactName: String,
        val message: String,
        val query: String = "",
        val action: String = "",
        val target: String = ""
    )

    private class MutableToolCall {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }
}
