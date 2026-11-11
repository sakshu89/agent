package com.ultron.app

import androidx.compose.ui.graphics.Color

/** Centralized visual intensity policy used by the UI. */
object VisualIntensity {
    data class Profile(val saturation: Float, val glow: Float, val animationSpeed: Float, val accent: Color)

    fun profile(mode: ModeManager.Mode): Profile = when (mode) {
        ModeManager.Mode.LIGHT -> Profile(.78f, .45f, .70f, Color(0xFF65EFFF))
        ModeManager.Mode.MEDIUM -> Profile(1.15f, .85f, 1.05f, Color(0xFF9C7BFF))
        ModeManager.Mode.EXTREME -> Profile(1.55f, 1.35f, 1.45f, Color(0xFFFF4F78))
    }

    fun amplify(color: Color, mode: ModeManager.Mode): Color {
        val p = profile(mode)
        fun channel(v: Float): Float = (0.5f + (v - 0.5f) * p.saturation).coerceIn(0f, 1f)
        return Color(channel(color.red), channel(color.green), channel(color.blue), color.alpha)
    }
}
