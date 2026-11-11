package com.ultron.app

/** Exact, intentionally small set of phrases allowed to interrupt spoken output. */
object VoiceInterruptCommand {
    private val stopPattern = Regex(
        "^(?:stop|(?:itra|ultron)\\s+stop|stop\\s+speaking|be quiet|(?:itra|ultron)\\s+be quiet|quiet)\\W*$",
        RegexOption.IGNORE_CASE
    )

    fun matches(text: String): Boolean = stopPattern.matches(text.trim())
}
