package com.ultron.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Persisted user settings. Values are Compose state so switches and sliders update immediately. */
object UltronSettings {
    private const val P = "ultron_3_settings"
    private fun p(c: Context) = c.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE)

    var wakeSensitivity by mutableStateOf(.72f); private set
    var micSensitivity by mutableStateOf(.70f); private set
    var noiseSuppression by mutableStateOf(true); private set
    var ownerVerification by mutableStateOf(false); private set
    var speechRate by mutableStateOf(1f); private set
    var animationScale by mutableStateOf(1f); private set
    var personality by mutableStateOf("FRIENDLY"); private set
    var compactReplies by mutableStateOf(true); private set
    var privacyMode by mutableStateOf(false); private set

    fun load(c: Context) {
        val s = p(c)
        wakeSensitivity = s.getFloat("wake", wakeSensitivity)
        micSensitivity = s.getFloat("mic", micSensitivity)
        noiseSuppression = s.getBoolean("noise", noiseSuppression)
        ownerVerification = s.getBoolean("owner", ownerVerification)
        speechRate = s.getFloat("rate", speechRate)
        animationScale = s.getFloat("anim", animationScale)
        personality = s.getString("personality", personality) ?: personality
        compactReplies = s.getBoolean("compact", compactReplies)
        privacyMode = s.getBoolean("privacy", privacyMode)
    }

    fun setWake(c: Context, v: Float) { wakeSensitivity = v.coerceIn(0f, 1f); p(c).edit().putFloat("wake", wakeSensitivity).apply() }
    fun setMic(c: Context, v: Float) { micSensitivity = v.coerceIn(0f, 1f); p(c).edit().putFloat("mic", micSensitivity).apply() }
    fun setNoise(c: Context, v: Boolean) { noiseSuppression = v; p(c).edit().putBoolean("noise", v).apply() }
    fun setOwner(c: Context, v: Boolean) { ownerVerification = v; p(c).edit().putBoolean("owner", v).apply() }
    fun setRate(c: Context, v: Float) { speechRate = v.coerceIn(.5f, 2f); p(c).edit().putFloat("rate", speechRate).apply() }
    fun setAnimation(c: Context, v: Float) { animationScale = v.coerceIn(.25f, 1.5f); p(c).edit().putFloat("anim", animationScale).apply() }
    fun setPersonality(c: Context, v: String) { personality = v; p(c).edit().putString("personality", v).apply() }
    fun setCompact(c: Context, v: Boolean) { compactReplies = v; p(c).edit().putBoolean("compact", v).apply() }
    fun setPrivacy(c: Context, v: Boolean) { privacyMode = v; p(c).edit().putBoolean("privacy", v).apply() }
}
