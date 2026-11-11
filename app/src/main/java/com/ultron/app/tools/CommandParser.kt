package com.ultron.app.tools

/** A user request ITRA can carry out without asking the LLM what it means. */
sealed class Command {
    data class OpenApp(val app: String) : Command()
    data class PlayMedia(val query: String, val app: String?) : Command()
    data object PlayFirstResult : Command()
    data object OpenInstagramInbox : Command()
    data class Search(val query: String, val app: String?) : Command()
    data class Research(val query: String) : Command()
    data class OpenUrl(val url: String) : Command()
    data class Navigate(val place: String) : Command()
    data class SendMessage(val app: String?, val contact: String, val message: String) : Command()

    /** Back, home, scroll, skip ad, or tap something visible on screen. */
    data class ScreenAction(val kind: String, val arg: String? = null) : Command()
    data object ReadScreen : Command()
    data class SetRingerMode(val mode: String) : Command()
    data class SetVolume(val percent: Int) : Command()
    data class SetBrightness(val percent: Int) : Command()

    /** The request was understood but something required is missing. */
    data class NeedMoreInfo(val question: String, val partial: PartialMessage? = null) : Command()
}

/** A half-finished "send message" request waiting for the user's next answer. */
data class PartialMessage(
    val app: String?,
    val contact: String?,
    val message: String?,
    /** Which slot the next answer fills: "app", "contact" or "message". */
    val awaiting: String
)

/**
 * Deterministic English/Hinglish command parser. Pure Kotlin (no Android classes)
 * so it can be unit-tested. Returns null when the text is not a recognised command,
 * in which case the caller falls back to the LLM.
 */
object CommandParser {
    private const val APP = "[a-z0-9][a-z0-9 .+&']*?"
    private const val SEP =
        "(?:,|:|-|\\bsaying\\b|\\bsay\\b|\\bthat says\\b|\\bthat\\b|\\bto say\\b|\\bwith message\\b|\\bwith text\\b)"
    private const val PREP = "(?:on|in|via|using|through)"
    private const val HIN_PREP = "(?:par|pe|me|mein)"

    private const val SEARCH_APPS =
        "youtube music|youtube|google maps|google|maps|play store|playstore|spotify|amazon|flipkart|chrome|wikipedia"
    private const val CHAT_APPS =
        "whatsapp business|whatsapp|telegram|instagram|messenger|facebook messenger|signal|snapchat|sms|messages"

    private val I = RegexOption.IGNORE_CASE

    private val leadIns = listOf(
        Regex("^(?:hey|hi|hello|ok|okay)?\\s*(?:ultron|altron|ultran|oltron|ultra on)\\b[\\s,.:!-]*", I),
        Regex("^(?:please|pls|can you|could you|would you|will you|kindly|i want you to|i want to|i wanna|i need you to)\\s+", I)
    )

    // send/write a message to X [on APP] saying M
    private val sendMsgToContact = Regex(
        "^(?:send|write|drop|shoot)\\s+(?:a\\s+|an\\s+)?(?:new\\s+)?(?:message|msg|text|sms|dm)\\s+" +
            "(?:$PREP\\s+($APP)\\s+)?to\\s+(.+?)(?:\\s+$PREP\\s+($APP))?\\s*$SEP\\s*(.+)$", I
    )

    // send M to X on APP
    private val sendMsgFirst = Regex(
        "^(?:send|text|message|msg)\\s+(.+)\\s+to\\s+(.+?)\\s+$PREP\\s+($APP)$", I
    )

    // send M to X   (no app named)
    private val sendMsgNoApp = Regex("^send\\s+(.+)\\s+to\\s+(.+)$", I)

    // message/tell X [on APP] saying M
    private val messageVerb = Regex(
        "^(?:message|msg|text|dm|tell|ping)\\s+(.+?)(?:\\s+$PREP\\s+($APP))?\\s*$SEP\\s*(.+)$", I
    )

    // whatsapp X saying M
    private val appAsVerb = Regex("^($CHAT_APPS)\\s+(.+?)\\s*$SEP\\s*(.+)$", I)

    // rahul ko whatsapp par hello bhejo
    private val hinglishSend = Regex(
        "^(.+?)\\s+ko\\s+(?:($APP)\\s+$HIN_PREP\\s+)?(.+?)\\s+(?:bhejo|bhej do|bhej|send karo|likho|likh do)$", I
    )

    private val playVerb = Regex(
        "^(?:play|put on|start playing|stream|watch|listen to)\\s+(.+?)(?:\\s+(?:$PREP|from)\\s+($APP))?$", I
    )
    private val hinglishPlay = Regex(
        "^(?:($APP)\\s+$HIN_PREP\\s+)?(.+?)\\s+(?:chalao|chala do|bajao|baja do|play karo|laga do|lagao)$", I
    )

    private val searchInApp = Regex("^(?:search|look up|find)\\s+(?:$PREP\\s+)?($SEARCH_APPS)\\s+(?:for\\s+)?(.+)$", I)
    private val searchOnApp = Regex("^(?:search|look up|find|google)\\s+(?:for\\s+)?(.+?)\\s+$PREP\\s+($SEARCH_APPS)$", I)
    private val searchWeb = Regex("^(?:search|google|look up)\\s+(?:for\\s+)?(.+)$", I)
    private val knowledgeRequest = Regex(
        "^(?:" +
            "tell me(?: about)?|" +
            "teach me(?: about)?|" +
            "can you (?:explain|tell me|teach me)|" +
            "could you (?:explain|tell me|teach me)|" +
            "would you (?:explain|tell me)|" +
            "explain|" +
            "what(?:\'s| is| are)|" +
            "how (?:does|do|can|to|is|are|did|was|were)|" +
            "why (?:does|do|is|are|did|was|were)|" +
            "who (?:is|was|are|were)|" +
            "when (?:did|was|is|does)|" +
            "where (?:is|are|was|were)|" +
            "define|meaning of|give me information (?:about|on)" +
        ")\\s+(.+)$", I
    )
    private val hinglishYoutubeSearch = Regex("^youtube\\s+$HIN_PREP\\s+search\\s+(.+)$", I)

    private val instagramDm = Regex(
        "^(?:send|write)\\s+(?:a\\s+)?(?:dm|message|msg)\\s+(?:to\\s+)?instagram\\s+(.+)$", I
    )
    private val instagramDmOn = Regex(
        "^(?:send|write)\\s+(?:a\\s+)?(?:dm|message|msg)\\s+(?:on|in)\\s+instagram\\s+to\\s+(.+)$", I
    )
    private val inAppSection = Regex(
        "^(?:open|show|go to)\\s+(?:the\\s+)?(chats?|messages?|inbox|dms?|direct messages?)\\s+(?:on|in)\\s+instagram$", I
    )
    private val shutdownRequest = Regex("^(?:shut ?down|power off|turn off)(?:\\s+(?:the\\s+)?(?:phone|device))?$", I)
    private val silentMode = Regex("^(?:set|put|make)\\s+(?:the\\s+)?phone\\s+(?:to\\s+)?(?:silent|mute|vibrate)$", I)
    private val volumeMode = Regex("^(?:set|change)\\s+(?:the\\s+)?volume\\s+(?:to\\s+)?(\\d{1,3})\\s*(?:percent|%)?$", I)
    private val brightnessMode = Regex("^(?:set|change)\\s+(?:the\\s+)?brightness\\s+(?:to\\s+)?(\\d{1,3})\\s*(?:percent|%)?$", I)

    private val navigate = Regex("^(?:navigate|directions|take me|drive|route|show route)\\s+(?:me\\s+)?(?:to\\s+)?(.+)$", I)

    private val openVerb = Regex("^(?:open|launch|start|run|go to|switch to)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$", I)
    private val hinglishOpen = Regex("^(.+?)\\s+(?:kholo|khol do|open karo|chalu karo|start karo)$", I)

    private val openAnd = Regex("^(?:open|launch|start)\\s+($APP)\\s+(?:and|then|&)\\s+(.+)$", I)

    private val partialSend = Regex(
        "^(?:send|write)\\s+(?:a\\s+|an\\s+)?(?:new\\s+)?(?:message|msg|text|sms|dm)\\s+" +
            "(?:$PREP\\s+($APP)\\s+)?to\\s+(.+?)(?:\\s+$PREP\\s+($APP))?$", I
    )

    private val skipAd = Regex(
        "^(?:skip|close|dismiss)(?:\\s+(?:the|this))?(?:\\s+(?:ad|add|ads|adds|advert\\w*|commercial))?(?:\\s+(?:on|in)\\s+$APP)?$", I
    )
    private val goBack = Regex("^(?:(?:go|press|tap)\\s+)?back$", I)
    private val goHome = Regex("^(?:go\\s+)?(?:to\\s+)?(?:the\\s+)?home(?:\\s+screen)?$", I)
    private val scroll = Regex("^scroll\\s+(up|down)", I)
    private val tapLabel = Regex("^(?:click|tap|press|select)\\s+(?:on\\s+)?(?:the\\s+)?(.+?)(?:\\s+button)?$", I)

    private val placeholderMessages = setOf("message", "a message", "msg", "text", "a text", "sms", "dm")

    fun parse(raw: String): Command? {
        var text = com.ultron.app.SpeechTextNormalizer.normalize(raw.trim()).trim('.', '!', '?', ',').trim()
        var changed = true
        while (changed) {
            changed = false
            for (rx in leadIns) {
                val next = rx.replace(text, "")
                if (next != text) {
                    text = next.trim()
                    changed = true
                }
            }
        }
        if (text.isBlank()) return null
        if (silentMode.matches(text) || Regex("(?i)^set\\s+phone\\s+to\\s+silent\\s+mode$").matches(text)) return Command.SetRingerMode("silent")
        volumeMode.matchEntire(text)?.let { return Command.SetVolume(it.groupValues[1].toInt().coerceIn(0, 100)) }
        brightnessMode.matchEntire(text)?.let { return Command.SetBrightness(it.groupValues[1].toInt().coerceIn(0, 100)) }
        val urlMatch = Regex("^(?:open|visit|go to)\\s+(https?://\\S+)$", I).find(text)
        if (urlMatch != null) return Command.OpenUrl(urlMatch.groupValues[1].trimEnd('.', ',', '!', '?'))
        if (Regex("^(?:play|open|tap|select)\\s+(?:the\\s+)?(?:first|top)\\s+(?:video|result|song)$", I).matches(text)) {
            return Command.PlayFirstResult
        }

        openAnd.find(text)?.let { m ->
            val app = clean(m.groupValues[1])
            val inner = parse(m.groupValues[2])
            return when (inner) {
                is Command.PlayMedia -> if (inner.app == null) inner.copy(app = app) else inner
                is Command.Search -> if (inner.app == null) inner.copy(app = app) else inner
                is Command.SendMessage -> if (inner.app == null) inner.copy(app = app) else inner
                null -> Command.OpenApp(app)
                else -> inner
            }
        }

        if (shutdownRequest.matches(text)) {
            return Command.NeedMoreInfo("I can't power off Android from this app. You can use your phone's power menu.")
        }
        inAppSection.find(text)?.let { return Command.OpenInstagramInbox }

        parseMessage(text)?.let { return it }
        parseMedia(text)?.let { return it }
        parseSearch(text)?.let { return it }
        knowledgeRequest.find(text)?.let { return Command.Research(clean(it.groupValues[1])) }

        navigate.find(text)?.let { return Command.Navigate(clean(it.groupValues[1])) }

        if (skipAd.matches(text)) return Command.ScreenAction("skip_ad")
        if (goBack.matches(text)) return Command.ScreenAction("back")
        if (goHome.matches(text)) return Command.ScreenAction("home")
        scroll.find(text)?.let { return Command.ScreenAction("scroll_" + it.groupValues[1].lowercase()) }
        tapLabel.find(text)?.let { return Command.ScreenAction("tap", clean(it.groupValues[1])) }

        openVerb.find(text)?.let { return Command.OpenApp(clean(it.groupValues[1])) }
        hinglishOpen.find(text)?.let { return Command.OpenApp(clean(it.groupValues[1])) }
        return null
    }

    private fun parseMessage(text: String): Command? {
        instagramDmOn.find(text)?.let { return buildMessage("instagram", clean(it.groupValues[1]), "") }
        instagramDm.find(text)?.let { return buildMessage("instagram", clean(it.groupValues[1]), "") }
        sendMsgToContact.find(text)?.let { m ->
            val app = m.groupValues[1].ifBlank { m.groupValues[3] }
            return buildMessage(app, m.groupValues[2], m.groupValues[4])
        }
        messageVerb.find(text)?.let { m ->
            val who = clean(m.groupValues[1]).lowercase()
            val notAContact = Regex("^(?:me|us|you|him|her|them|about|what|whats|what's|how|why|who|when|where|which|a|an|the|if|whether)\\b")
            if (!notAContact.containsMatchIn(who)) {
                return buildMessage(m.groupValues[2], m.groupValues[1], m.groupValues[3])
            }
        }
        appAsVerb.find(text)?.let { m ->
            return buildMessage(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        }
        sendMsgFirst.find(text)?.let { m ->
            return buildMessage(m.groupValues[3], m.groupValues[2], m.groupValues[1])
        }
        hinglishSend.find(text)?.let { m ->
            return buildMessage(m.groupValues[2], m.groupValues[1], m.groupValues[3])
        }
        partialSend.find(text)?.let { m ->
            val app = clean(m.groupValues[1].ifBlank { m.groupValues[3] }).ifBlank { null }
            val contact = clean(m.groupValues[2])
            return Command.NeedMoreInfo(
                "What should the message to $contact say?",
                PartialMessage(app, contact, null, "message")
            )
        }
        sendMsgNoApp.find(text)?.let { m ->
            return buildMessage("", m.groupValues[2], m.groupValues[1])
        }
        return null
    }

    private fun buildMessage(app: String, contact: String, message: String): Command {
        val c = clean(contact)
        val msg = clean(message)
        val appName = clean(app).ifBlank { null }
        if (c.isBlank()) {
            return Command.NeedMoreInfo("Who should I send it to?", PartialMessage(appName, null, msg.ifBlank { null }, "contact"))
        }
        if (msg.isBlank() || msg.lowercase() in placeholderMessages) {
            return Command.NeedMoreInfo("What should the message to $c say?", PartialMessage(appName, c, null, "message"))
        }
        return Command.SendMessage(appName, c, msg)
    }

    /**
     * The user answered a follow-up question ("Which app?", "What should it say?").
     * Returns the next command, or null if they cancelled.
     */
    fun continuePartial(partial: PartialMessage, raw: String): Command? {
        val t = clean(raw.trim().trim('.', '!', '?', ','))
        if (Regex("^(?:cancel|stop|never ?mind|leave it|forget it)$", I).matches(t)) return null
        var app = partial.app
        var contact = partial.contact
        var message = partial.message
        when (partial.awaiting) {
            "app" -> app = t
                .replace(Regex("^(?:use|open|on|in|via|using|through|send it (?:on|via|using))\\s+", I), "")
                .replace(Regex("\\s+(?:app|please)$", I), "").trim()
            "contact" -> contact = t.replace(Regex("^(?:to|send it to)\\s+", I), "").trim()
            else -> message = t
                .replace(Regex("^(?:say|saying|that|tell (?:him|her|them)|message is|the message is)\\s+", I), "").trim()
        }
        return buildMessage(app.orEmpty(), contact.orEmpty(), message.orEmpty())
    }

    private fun parseMedia(text: String): Command? {
        playVerb.find(text)?.let { m ->
            val query = clean(m.groupValues[1]).replace(Regex("^(?:some|the)\\s+", I), "")
            if (query.isBlank()) return null
            val app = clean(m.groupValues[2]).ifBlank { null }
            return Command.PlayMedia(query, app)
        }
        hinglishPlay.find(text)?.let { m ->
            val query = clean(m.groupValues[2])
            if (query.isBlank()) return null
            return Command.PlayMedia(query, clean(m.groupValues[1]).ifBlank { null })
        }
        return null
    }

    private fun parseSearch(text: String): Command? {
        hinglishYoutubeSearch.find(text)?.let { return Command.Search(clean(it.groupValues[1]), "youtube") }
        searchInApp.find(text)?.let {
            var query = clean(it.groupValues[2])
            query = query.replace(Regex("^(?:and\\s+)?(?:tell|show|explain)\\s+me\\s+", I), "")
            return Command.Search(query, it.groupValues[1].lowercase())
        }
        searchOnApp.find(text)?.let { return Command.Search(clean(it.groupValues[1]), it.groupValues[2].lowercase()) }
        searchWeb.find(text)?.let { return Command.Search(clean(it.groupValues[1]), null) }
        return null
    }

    private fun clean(value: String): String =
        value.trim().trim('"', '\'', '“', '”', '‘', '’', ',', ':', '-').trim()
}
