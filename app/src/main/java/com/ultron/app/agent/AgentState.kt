package com.ultron.app.agent

enum class AgentState {
    IDLE, WAKE_DETECTED, AUTHENTICATING, LISTENING, PROCESSING,
    SEARCHING, EXECUTING, SPEAKING, ERROR, STOPPED
}

/** Wake-word and speaker identity are extension points, not simulated security. */
interface WakeWordDetector {
    val isAvailable: Boolean
    fun start(onWakeWord: () -> Unit)
    fun stop()
}

interface SpeakerVerifier {
    val isAvailable: Boolean
    suspend fun verify(sample: ByteArray): VerificationResult
}

data class VerificationResult(
    val authorized: Boolean,
    val confidence: Float,
    val profileId: String? = null
)
