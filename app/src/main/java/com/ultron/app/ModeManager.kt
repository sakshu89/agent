package com.ultron.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ultron.app.tools.Command

/** Three user-selectable operating modes. The mode gates capabilities; it never bypasses Android security. */
object ModeManager {
    enum class Mode { LIGHT, MEDIUM, EXTREME }

    private const val PREFS = "ultron_preferences"
    private const val KEY_MODE = "operating_mode"
    private var context: Context? = null

    private var _mode by mutableStateOf(Mode.LIGHT)
    val mode: Mode get() = _mode

    fun init(context: Context) {
        if (this.context != null) return
        this.context = context.applicationContext
        val saved = prefs().getString(KEY_MODE, Mode.LIGHT.name).orEmpty()
        _mode = runCatching { Mode.valueOf(saved) }.getOrDefault(Mode.LIGHT)
    }

    fun setMode(next: Mode) {
        _mode = next
        context?.let { prefs().edit().putString(KEY_MODE, next.name).apply() }
        LogStore.a("MODE", "Operating mode: ${next.name}")
    }

    fun allowsAutomation(): Boolean = mode != Mode.LIGHT
    fun allowsComplexAutomation(): Boolean = mode == Mode.EXTREME

    /** Medium permits low-impact actions; Extreme additionally permits named screen taps and messaging after confirmation. */
    fun allowsCommand(command: Command): Boolean = when (mode) {
        Mode.LIGHT -> command is Command.Research || command is Command.NeedMoreInfo
        Mode.MEDIUM -> when (command) {
            is Command.OpenApp, is Command.PlayMedia, is Command.Search, is Command.Research, is Command.OpenUrl, is Command.Navigate, Command.ReadScreen,
            Command.PlayFirstResult, Command.OpenInstagramInbox -> true
            is Command.ScreenAction -> command.kind in setOf("back", "home", "scroll_up", "scroll_down", "skip_ad")
            is Command.SetRingerMode, is Command.SetVolume, is Command.SetBrightness -> true
            else -> false
        }
        Mode.EXTREME -> true
    }

    private fun prefs() = context!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
