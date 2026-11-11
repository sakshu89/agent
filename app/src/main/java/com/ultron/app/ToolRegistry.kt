package com.ultron.app

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.ultron.app.tools.Command

object ToolRegistry {
    const val SEND_MESSAGE_TOOL = "send_message_tool"
    const val OPEN_APP_TOOL = "open_app_tool"
    const val PLAY_MEDIA_TOOL = "play_media_tool"
    const val SEARCH_TOOL = "search_tool"
    const val SCREEN_ACTION_TOOL = "screen_action_tool"
    const val OPEN_URL_TOOL = "open_url_tool"
    const val READ_SCREEN_TOOL = "read_screen_tool"
    const val SYSTEM_CONTROL_TOOL = "system_control_tool"

    private val allTools = setOf(SEND_MESSAGE_TOOL, OPEN_APP_TOOL, PLAY_MEDIA_TOOL, SEARCH_TOOL, SCREEN_ACTION_TOOL, OPEN_URL_TOOL, READ_SCREEN_TOOL, SYSTEM_CONTROL_TOOL)

    private fun stringProp(description: String) = JsonObject().apply {
        addProperty("type", "string")
        addProperty("description", description)
    }

    private fun tool(name: String, description: String, props: Map<String, JsonObject>, required: List<String>): JsonObject {
        val function = JsonObject()
        function.addProperty("name", name)
        function.addProperty("description", description)
        val params = JsonObject()
        params.addProperty("type", "object")
        val properties = JsonObject()
        props.forEach { (k, v) -> properties.add(k, v) }
        params.add("properties", properties)
        params.add("required", JsonArray().apply { required.forEach { add(it) } })
        function.add("parameters", params)
        return JsonObject().apply {
            addProperty("type", "function")
            add("function", function)
        }
    }

    fun getOpenAiTools(): JsonArray = JsonArray().apply {
        add(
            tool(
                SEND_MESSAGE_TOOL,
                "Send one personal message to a contact in any messaging app (WhatsApp, Telegram, Instagram, Messenger, Signal, SMS, ...). Preserve the user's exact message.",
                mapOf(
                    "app_name" to stringProp("Messaging app name as the user said it, e.g. WhatsApp."),
                    "contact_name" to stringProp("Exact contact name."),
                    "message" to stringProp("Exact message to send.")
                ),
                listOf("app_name", "contact_name", "message")
            )
        )
        add(
            tool(
                OPEN_APP_TOOL,
                "Open any app installed on the phone.",
                mapOf("app_name" to stringProp("Name of the app to open.")),
                listOf("app_name")
            )
        )
        add(
            tool(
                PLAY_MEDIA_TOOL,
                "Play a song, video or artist. Defaults to YouTube when no app is named.",
                mapOf(
                    "query" to stringProp("What to play, e.g. 'Lana Del Rey songs'."),
                    "app_name" to stringProp("youtube, youtube music or spotify. Optional.")
                ),
                listOf("query")
            )
        )
        add(
            tool(
                SEARCH_TOOL,
                "Search Google, YouTube, Maps, Play Store or another site and show the results on screen.",
                mapOf(
                    "query" to stringProp("What to search for."),
                    "app_name" to stringProp("google, youtube, maps, play store, amazon... Optional.")
                ),
                listOf("query")
            )
        )
        add(
            tool(
                OPEN_URL_TOOL,
                "Open a user-provided public website URL in the browser. Only http and https URLs are allowed; do not invent a URL if the user did not provide or request a known destination.",
                mapOf("url" to stringProp("Full http or https website URL.")),
                listOf("url")
            )
        )
        add(
            tool(
                READ_SCREEN_TOOL,
                "Read visible text from the current screen using on-device OCR. Use only when the user asks what is on screen or when screen text is needed to complete their request. This captures the screen only for this request and does not save the screenshot.",
                emptyMap(),
                emptyList()
            )
        )
        add(
            tool(
                SYSTEM_CONTROL_TOOL,
                "Change supported phone settings: ringer mode, media volume, or brightness. Android permissions still apply.",
                mapOf("action" to stringProp("silent, vibrate, volume, or brightness"), "query" to stringProp("0-100 for volume/brightness; omitted for silent/vibrate")),
                listOf("action")
            )
        )
        add(
            tool(
                SCREEN_ACTION_TOOL,
                "Perform a user-requested, non-sensitive action on the currently visible Android screen. Allowed actions: back, home, scroll_up, scroll_down, skip_ad, tap. Never use tap for payments, purchases, sending, deleting, posting, account changes, or other consequential actions; ask the user to do those manually. Use tap only when the user explicitly names a visible control.",
                mapOf(
                    "action" to stringProp("One of: back, home, scroll_up, scroll_down, skip_ad, tap."),
                    "target" to stringProp("Visible control label. Required only for tap.")
                ),
                listOf("action")
            )
        )
    }

    fun isKnownTool(name: String): Boolean = name in allTools
    fun isSendMessageTool(name: String): Boolean = name == SEND_MESSAGE_TOOL

    fun toCommand(call: LlmClient.ToolCall): Command? = when (call.name) {
        SEND_MESSAGE_TOOL -> Command.SendMessage(call.appName.ifBlank { null }, call.contactName, call.message)
        OPEN_APP_TOOL -> Command.OpenApp(call.appName)
        PLAY_MEDIA_TOOL -> Command.PlayMedia(call.query, call.appName.ifBlank { null })
        SEARCH_TOOL -> Command.Search(call.query, call.appName.ifBlank { null })
        OPEN_URL_TOOL -> Command.OpenUrl(call.query)
        READ_SCREEN_TOOL -> Command.ReadScreen
        SYSTEM_CONTROL_TOOL -> when (call.action.lowercase()) {
            "silent", "vibrate" -> Command.SetRingerMode(call.action.lowercase())
            "volume" -> call.query.toIntOrNull()?.coerceIn(0, 100)?.let { Command.SetVolume(it) }
            "brightness" -> call.query.toIntOrNull()?.coerceIn(0, 100)?.let { Command.SetBrightness(it) }
            else -> null
        }
        SCREEN_ACTION_TOOL -> when (call.action.lowercase()) {
            "back", "home", "scroll_up", "scroll_down", "skip_ad" -> Command.ScreenAction(call.action.lowercase())
            "tap" -> call.target.takeIf { it.isNotBlank() }?.let { Command.ScreenAction("tap", it) }
            else -> null
        }
        else -> null
    }
}
