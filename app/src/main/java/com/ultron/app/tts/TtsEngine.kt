package com.ultron.app.tts

interface TtsEngine {
    val isReady: Boolean
    fun speak(text: String)
    fun stop()
    fun release()
}
