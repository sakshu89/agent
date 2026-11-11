package com.ultron.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Speech in and out. Two modes:
 *  - one-shot: [startListening] listens once (tap to talk)
 *  - continuous: [startContinuous] keeps listening and restarts after every phrase (hands-free)
 * Listening pauses while ITRA is speaking so it does not hear itself.
 * All calls must be made on the main thread.
 */
class VoiceManager(context: Context) : com.ultron.app.tts.TtsEngine {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    @Volatile
    private var speaking = false
    private var speakStartedAt = 0L

    private var oneShot: ((String) -> Unit)? = null
    private var continuous = false
    private var onHeard: ((String) -> Unit)? = null
    private var onListeningChanged: ((Boolean) -> Unit)? = null
    private var recognizing = false
    private var lastNormalized = ""
    private var lastNormalizedAt = 0L
    @Volatile var lastAlternatives: List<RecognitionAlternative> = emptyList()
        private set

    data class RecognitionAlternative(val text: String, val confidence: Float?)

    // Offline engine (Vosk), used instead of the Android recognizer when a model is imported.
    private var vosk: VoskEngine? = null
    private val whisperLocal = WhisperLocalEngine(appContext)
    private var voskActive = false

    private val restart = Runnable { beginRecognition() }

    init {
        initializeTts()
    }

    private fun initializeTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setSpeechRate(UltronSettings.speechRate)
                tts?.setPitch(0.98f)
                val result = tts?.setLanguage(Locale.getDefault())
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        speaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        speaking = false
                        main.post { afterSpeech() }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        speaking = false
                        main.post { afterSpeech() }
                    }
                })
            } else {
                LogStore.e("VOICE", "Text-to-speech failed to start (status $status).")
            }
        }
    }

    private fun createRecognizer(): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            LogStore.e("VOICE", "No speech recognition service is available on this phone.")
            return null
        }
        val speech = SpeechRecognizer.createSpeechRecognizer(appContext)
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                recognizing = false
                if (continuous) {
                    val delay = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 150L
                        SpeechRecognizer.ERROR_CLIENT -> 500L
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            recreateRecognizer()
                            1500L
                        }
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            LogStore.e("VOICE", "Microphone permission missing. Hands-free stopped.")
                            stopContinuous()
                            return
                        }
                        else -> {
                            LogStore.w("VOICE", "Recognizer error $error, retrying.")
                            1500L
                        }
                    }
                    scheduleRestart(delay)
                } else {
                    val callback = oneShot
                    oneShot = null
                    callback?.invoke("")
                }
            }

            override fun onResults(results: Bundle?) {
                recognizing = false
                val rawAlternatives = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                val alternatives = rawAlternatives.mapIndexed { index, value ->
                    RecognitionAlternative(SpeechTextNormalizer.normalize(value.trim()), confidence?.getOrNull(index)?.takeIf { it >= 0f })
                }.filter { it.text.isNotBlank() }
                lastAlternatives = alternatives
                val normalizedText = alternatives.firstOrNull()?.text.orEmpty()
                val duplicate = normalizedText.isNotBlank() && normalizedText.equals(lastNormalized, true) && System.currentTimeMillis() - lastNormalizedAt < 2500L
                if (normalizedText.isNotBlank() && !duplicate) { lastNormalized = normalizedText; lastNormalizedAt = System.currentTimeMillis() }

                if (continuous) {
                    if (normalizedText.isNotBlank() && !duplicate) {
                        if (!speaking || isStopPhrase(normalizedText)) onHeard?.invoke(normalizedText)
                    }
                    scheduleRestart(300)
                } else {
                    val callback = oneShot
                    oneShot = null
                    callback?.invoke(if (duplicate) "" else normalizedText)
                }
            }
        })
        return speech
    }

    private fun recreateRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
        putExtra("android.speech.extra.PREFER_OFFLINE", true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
    }

    private fun isStopPhrase(text: String): Boolean = VoiceInterruptCommand.matches(text)

    private fun afterSpeech() {
        if (voskActive) {
            main.postDelayed({ vosk?.pause(false) }, 450)
        } else if (continuous) {
            scheduleRestart(500)
        }
    }

    // ------------------------------------------------------------------ Vosk (offline)

    private fun useVosk(): Boolean = VoskModelManager.shouldUse(appContext)

    private fun voskEngine(): VoskEngine = vosk ?: VoskEngine(appContext).also { vosk = it }

    private fun startVoskContinuous() {
        voskActive = true
        voskEngine().load { ok ->
            if (!continuous) return@load
            if (!ok) {
                LogStore.w("VOSK", "Vosk model could not load; using the Android recognizer instead.")
                voskActive = false
                beginRecognition()
                return@load
            }
            voskEngine().start(
                onText = { text -> if (!speaking || isStopPhrase(text)) onHeard?.invoke(text) },
                onFailure = { reason ->
                    LogStore.w("VOSK", "Vosk stopped ($reason); using the Android recognizer instead.")
                    voskActive = false
                    if (continuous) beginRecognition()
                }
            )
            onListeningChanged?.invoke(true)
            LogStore.i("VOSK", "Listening offline with Vosk.")
        }
    }

    private fun startVoskOnce(callback: (String) -> Unit) {
        voskEngine().load { ok ->
            if (!ok) {
                LogStore.w("VOSK", "Vosk model could not load.")
                callback("")
                return@load
            }
            var finished = false
            fun finish(text: String) {
                if (finished) return
                finished = true
                voskEngine().stop()
                callback(text)
            }
            voskEngine().start(
                onText = { text -> finish(text) },
                onFailure = { finish("") },
                timeoutMs = 10000,
                onTimedOut = { finish("") }
            )
        }
    }

    /** The model was imported, replaced or removed. */
    fun reloadVosk(releaseOnly: Boolean = false) {
        vosk?.stop()
        if (releaseOnly) {
            vosk?.release()
            return
        }
        if (continuous) {
            voskActive = false
            main.removeCallbacks(restart)
            runCatching { recognizer?.cancel() }
            recognizing = false
            if (useVosk()) startVoskContinuous() else beginRecognition()
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        main.removeCallbacks(restart)
        if (continuous || oneShot != null) main.postDelayed(restart, delayMs)
    }

    private fun beginRecognition() {
        if (!continuous && oneShot == null) return
        if (voskActive) return
        if (speaking && System.currentTimeMillis() - speakStartedAt > 25000) speaking = false
        if (speaking && !continuous) {
            scheduleRestart(400)
            return
        }
        if (recognizing) return
        val speech = recognizer ?: createRecognizer().also { recognizer = it }
        if (speech == null) {
            val callback = oneShot
            oneShot = null
            callback?.invoke("")
            return
        }
        try {
            recognizing = true
            speech.startListening(recognizerIntent())
            onListeningChanged?.invoke(true)
        } catch (e: Exception) {
            recognizing = false
            LogStore.e("VOICE", "Could not start listening", e)
            recreateRecognizer()
            scheduleRestart(1500)
        }
    }

    // ------------------------------------------------------------------ public API

    fun startListening(onResult: (String) -> Unit = {}) {
        if (WhisperLocalConfig.enabled(appContext)) {
            whisperLocal.transcribeOnce { result -> main.post { onResult(result.getOrDefault("")) } }
            return
        }
        if (useVosk()) {
            oneShot = null
            startVoskOnce(onResult)
            return
        }
        oneShot = onResult
        beginRecognition()
    }

    fun stopListening() {
        if (!voskActive) vosk?.stop()
        oneShot = null
        runCatching { recognizer?.cancel() }
        recognizing = false
    }

    fun startContinuous(onHeard: (String) -> Unit, onListeningChanged: (Boolean) -> Unit = {}) {
        this.onHeard = onHeard
        this.onListeningChanged = onListeningChanged
        continuous = true
        LogStore.i("VOICE", "Hands-free listening started.")
        if (useVosk()) startVoskContinuous() else beginRecognition()
    }

    fun stopContinuous() {
        continuous = false
        voskActive = false
        vosk?.stop()
        main.removeCallbacks(restart)
        runCatching { recognizer?.cancel() }
        recognizing = false
        onListeningChanged?.invoke(false)
        onHeard = null
        LogStore.i("VOICE", "Hands-free listening stopped.")
    }

    val isContinuous: Boolean get() = continuous

    override val isReady: Boolean get() = ttsReady

    val isSpeaking: Boolean get() = speaking

    override fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        speaking = true
        speakStartedAt = System.currentTimeMillis()
        // Keep hands-free recognition active during TTS so the user can interrupt with
        // a short "stop" command. While speaking, every other recognized phrase is ignored.
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ITRA_TTS_${System.currentTimeMillis()}")
        if (result != TextToSpeech.SUCCESS) {
            speaking = false
            afterSpeech()
        }
    }

    fun speakQueued(text: String) {
        if (!ttsReady || text.isBlank()) return
        speaking = true
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "ITRA_TTS_${System.currentTimeMillis()}")
    }

    override fun stop() {
        tts?.stop()
        speaking = false
    }

    /** Apply persisted voice settings to the live TTS engine without restarting the assistant. */
    fun applySettings() {
        if (ttsReady) {
            runCatching { tts?.setSpeechRate(UltronSettings.speechRate) }
        }
    }

    fun stopSpeaking() = stop()

    override fun release() {
        continuous = false
        voskActive = false
        vosk?.release()
        vosk = null
        main.removeCallbacks(restart)
        oneShot = null
        recreateRecognizer()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    fun destroy() = release()
}
