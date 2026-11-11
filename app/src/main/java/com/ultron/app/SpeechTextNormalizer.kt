package com.ultron.app

import java.util.Locale

/**
 * Small, deterministic offline normalizer for common Indian-English/Hinglish commands.
 * It does not call a network service. It converts common Hindi command words to English
 * while leaving names and most sentence content untouched.
 */
object SpeechTextNormalizer {
    private val romanWords = mapOf(
        " kholo" to " open", " khol" to " open", " kholna" to " open",
        " kholo na" to " open", " chalao" to " open", " chala" to " open",
        " band" to " close", " bandh" to " close", " band karo" to " close",
        " bhejo" to " send", " bhej" to " send", " bhejna" to " send",
        " batao" to " tell me", " btao" to " tell me", " dikhao" to " show me",
        " sunao" to " play", " bajao" to " play", " rok do" to " stop",
        " ruko" to " stop", " hatao" to " remove", " lagao" to " turn on",
        " chalu karo" to " turn on", " on karo" to " turn on",
        " off karo" to " turn off", " kar do" to " do it",
        " karna" to " do", " karo" to " do", " mujhe" to " me",
        " mera" to " my", " meri" to " my", " mere" to " my",
        " abhi" to " now", " aaj" to " today", " kal" to " tomorrow",
        " yahan" to " here", " wahan" to " there", " kitna" to " how much",
        " kitne" to " how many", " kya" to " what", " kahan" to " where",
        " kab" to " when", " kyun" to " why", " kaise" to " how",
        " aur" to " and", " phir" to " then", " please" to " please",
        " bhai" to "", " yaar" to "", " sir" to ""
    )

    private val devanagariWords = mapOf(
        "खोलो" to "open", "खोल" to "open", "खोलना" to "open",
        "चलाओ" to "open", "चालू" to "turn on", "बंद" to "close",
        "भेजो" to "send", "भेज" to "send", "बताओ" to "tell me",
        "दिखाओ" to "show me", "सुनाओ" to "play", "बजाओ" to "play",
        "रोक" to "stop", "रुको" to "stop", "हटाओ" to "remove",
        "करो" to "do", "करना" to "do", "कर" to "do",
        "मुझे" to "me", "मेरा" to "my", "मेरी" to "my", "मेरे" to "my",
        "अभी" to "now", "आज" to "today", "कल" to "tomorrow",
        "यहाँ" to "here", "वहाँ" to "there", "क्या" to "what",
        "कहाँ" to "where", "कब" to "when", "क्यों" to "why",
        "कैसे" to "how", "और" to "and", "फिर" to "then",
        "भाई" to "", "यार" to ""
    )

    private val phraseRules = listOf(
        Regex("""(?i)^(?:hey\s+)?(?:bhai\s+|yaar\s+)?(.+?)\s+(?:kholo|khol|open karo)$""") to "open",
        Regex("""(?i)^(?:bhai\s+|yaar\s+)?(?:mera|my)\s+phone\s+(?:silent|silent mode)\s+(?:kar do|karo|pe laga do)$""") to "set phone to silent mode",
        Regex("""(?i)^(?:mujhe\s+)?(?:time|samay)\s+(?:batao|btao)$""") to "tell me the time",
        Regex("""(?i)^(?:mujhe\s+)?(?:weather|mausam)\s+(?:batao|btao)$""") to "tell me the weather"
    )

    fun normalize(input: String): String {
        val original = input.trim().trimEnd('.', '!', '?', ',')
        if (original.isBlank()) return original

        for ((pattern, replacement) in phraseRules) {
            if (pattern.matches(original)) {
                val app = pattern.matchEntire(original)?.groupValues?.getOrNull(1)
                if (replacement == "open" && !app.isNullOrBlank()) return "open ${app.trim()}"
                return replacement
            }
        }

        var result = original
        // Replace Devanagari words on word boundaries, longest first.
        for ((word, replacement) in devanagariWords.entries.sortedByDescending { it.key.length }) {
            result = result.replace(Regex("(?<!\\S)${Regex.escape(word)}(?=\\s|$|[,.!?])"), replacement)
        }
        // Roman Hinglish command vocabulary. Phrase entries first to avoid partial replacement.
        for ((word, replacement) in romanWords.entries.sortedByDescending { it.key.length }) {
            result = result.replace(Regex(Regex.escape(word), RegexOption.IGNORE_CASE), replacement)
        }
        result = result.replace(Regex("\\s+"), " ")
            .replace(Regex("(?i)^(?:bhai|yaar|sir|please)\\s+"), "")
            .replace(Regex("\\s+([,.!?])"), "$1")
            .replace(Regex("(?i)\\bopen\\s+open\\b"), "open")
            .replace(Regex("(?i)\\bdo\\s+it\\s+now\\b"), "do it now")
            .trim(' ', ',', '.', ':', ';', '-')
        return result
    }
}
