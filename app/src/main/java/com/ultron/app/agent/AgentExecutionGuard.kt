package com.ultron.app.agent

import com.ultron.app.tools.Command
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local duplicate dispatch guard. It is intentionally short-lived so an
 * intentional later repeat is still possible. UI verification remains authoritative.
 */
object AgentExecutionGuard {
    private const val WINDOW_MS = 1_200L
    private val lastDispatch = ConcurrentHashMap<String, Long>()

    fun allow(command: Command, nowMs: Long = System.currentTimeMillis()): Boolean {
        val key = key(command)
        val previous = lastDispatch[key]
        if (previous != null && nowMs - previous in 0..WINDOW_MS) return false
        lastDispatch[key] = nowMs
        cleanup(nowMs)
        return true
    }

    fun clear() = lastDispatch.clear()

    private fun cleanup(nowMs: Long) {
        lastDispatch.entries.removeIf { nowMs - it.value > WINDOW_MS * 4 }
    }

    private fun key(command: Command): String = when (command) {
        is Command.OpenApp -> "open_app:${command.app.trim().lowercase()}"
        is Command.PlayMedia -> "play:${command.app.orEmpty().lowercase()}:${command.query.trim().lowercase()}"
        Command.PlayFirstResult -> "play_first"
        Command.OpenInstagramInbox -> "instagram_inbox"
        is Command.Search -> "search:${command.app.orEmpty().lowercase()}:${command.query.trim().lowercase()}"
        is Command.Research -> "research:${command.query.trim().lowercase()}"
        is Command.OpenUrl -> "url:${command.url.trim()}"
        is Command.Navigate -> "navigate:${command.place.trim().lowercase()}"
        is Command.SendMessage -> "message:${command.app.orEmpty().lowercase()}:${command.contact.trim().lowercase()}:${command.message}"
        is Command.ScreenAction -> "screen:${command.kind.lowercase()}:${command.arg.orEmpty().trim().lowercase()}"
        Command.ReadScreen -> "read_screen"
        is Command.SetRingerMode -> "ringer:${command.mode.lowercase()}"
        is Command.SetVolume -> "volume:${command.percent}"
        is Command.SetBrightness -> "brightness:${command.percent}"
        is Command.NeedMoreInfo -> "info:${command.question.trim().lowercase()}"
    }
}
