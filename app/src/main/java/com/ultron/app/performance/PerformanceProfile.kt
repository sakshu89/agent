package com.ultron.app.performance

import android.content.Context
import android.os.Build
import android.view.Display
import android.view.WindowManager

enum class PerformanceMode { HIGH_PERFORMANCE, BALANCED, BATTERY_SAVER }

data class DevicePerformanceProfile(
    val mode: PerformanceMode,
    val displayRefreshRateHz: Float,
    val animationScale: Float,
    val description: String
)

/**
 * Reads device capabilities and recommends animation behavior.
 * It never requests a display mode or forces a frame rate.
 */
object PerformanceProfile {
    private const val PREFS = "ultron_preferences"
    private const val KEY_MODE = "performance_mode"

    fun getMode(context: Context): PerformanceMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, PerformanceMode.BALANCED.name)
        return runCatching { PerformanceMode.valueOf(raw ?: "") }
            .getOrDefault(PerformanceMode.BALANCED)
    }

    fun setMode(context: Context, mode: PerformanceMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.name).apply()
    }

    @Suppress("DEPRECATION")
    fun inspect(context: Context): DevicePerformanceProfile {
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val refresh = if (Build.VERSION.SDK_INT >= 30) {
            context.display?.refreshRate ?: manager.defaultDisplay.refreshRate
        } else {
            manager.defaultDisplay.refreshRate
        }
        val mode = getMode(context)
        val scale = when (mode) {
            PerformanceMode.HIGH_PERFORMANCE -> if (refresh >= 90f) 1f else 0.85f
            PerformanceMode.BALANCED -> 0.85f
            PerformanceMode.BATTERY_SAVER -> 0.55f
        }
        return DevicePerformanceProfile(
            mode = mode,
            displayRefreshRateHz = refresh,
            animationScale = scale,
            description = "${refresh.toInt()} Hz display detected; animations adapt without forcing refresh rate."
        )
    }
}
