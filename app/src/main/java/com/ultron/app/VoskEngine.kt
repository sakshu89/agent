package com.ultron.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Offline speech recognition with a Vosk model imported by the user.
 * Created and used on the main thread; the model itself loads on a background thread.
 */
class VoskEngine(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var model: Model? = null
    private var loadedGeneration = -1
    private var loading = false
    private val waiting = ArrayList<(Boolean) -> Unit>()

    private var service: SpeechService? = null

    val isRunning: Boolean get() = service != null

    /** Loads (or reloads) the model, then calls [onReady] on the main thread. */
    fun load(onReady: (Boolean) -> Unit) {
        val generation = VoskModelManager.generation
        if (model != null && loadedGeneration == generation) {
            onReady(true)
            return
        }
        waiting.add(onReady)
        if (loading) return
        val dir = VoskModelManager.modelDir(appContext)
        if (dir == null) {
            finishLoading(false)
            return
        }
        stop()
        runCatching { model?.close() }
        model = null
        loading = true
        LogStore.i("VOSK", "Loading model...")
        Thread {
            var loaded: Model? = null
            try {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                loaded = Model(dir.absolutePath)
            } catch (t: Throwable) {
                LogStore.e("VOSK", "Could not load the model", t)
            }
            main.post {
                model = loaded
                loadedGeneration = generation
                loading = false
                if (loaded != null) LogStore.i("VOSK", "Model loaded.")
                finishLoading(loaded != null)
            }
        }.start()
    }

    private fun finishLoading(ok: Boolean) {
        val callbacks = ArrayList(waiting)
        waiting.clear()
        callbacks.forEach { it(ok) }
    }

    /**
     * Starts listening. [onText] receives every finished phrase. With [timeoutMs] > 0 listening
     * stops by itself and [onTimeout] is called.
     */
    fun start(
        onText: (String) -> Unit,
        onFailure: (String) -> Unit,
        timeoutMs: Int = 0,
        onTimedOut: () -> Unit = {}
    ) {
        stop()
        val m = model
        if (m == null) {
            onFailure("Vosk model is not loaded.")
            return
        }
        try {
            val recognizer = Recognizer(m, 16000.0f)
            val speech = SpeechService(recognizer, 16000.0f)
            service = speech
            val listener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) = Unit

                override fun onResult(hypothesis: String?) {
                    val text = parse(hypothesis)
                    if (text.isNotBlank()) main.post { onText(text) }
                }

                override fun onFinalResult(hypothesis: String?) {
                    val text = parse(hypothesis)
                    if (text.isNotBlank()) main.post { onText(text) }
                }

                override fun onError(exception: Exception?) {
                    LogStore.e("VOSK", "Recognizer error", exception)
                    main.post { onFailure(exception?.message ?: "Recognizer error") }
                }

                override fun onTimeout() {
                    main.post { onTimedOut() }
                }
            }
            if (timeoutMs > 0) speech.startListening(listener, timeoutMs) else speech.startListening(listener)
        } catch (t: Throwable) {
            LogStore.e("VOSK", "Could not start listening", t)
            service = null
            onFailure(t.message ?: t.javaClass.simpleName)
        }
    }

    /** Pause while ITRA speaks so it does not hear itself. */
    fun pause(paused: Boolean) {
        runCatching { service?.setPause(paused) }
    }

    fun stop() {
        val s = service ?: return
        service = null
        runCatching { s.stop() }
        runCatching { s.shutdown() }
    }

    fun release() {
        stop()
        runCatching { model?.close() }
        model = null
        loadedGeneration = -1
    }

    private fun parse(json: String?): String = try {
        val root = JSONObject(json ?: "{}")
        val text = SpeechTextNormalizer.normalize(root.optString("text", "").trim())
        if (text.isNotBlank()) {
            VoiceManagerHolder.lastVoskAlternatives = listOf(VoiceManager.RecognitionAlternative(text, null))
        }
        text
    } catch (_: Exception) { "" }

    private object VoiceManagerHolder {
        @Volatile var lastVoskAlternatives: List<VoiceManager.RecognitionAlternative> = emptyList()
    }
}
