package com.ultron.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Optional local Whisper-compatible adapter. No cloud endpoint is accepted. */
class WhisperLocalEngine(context: Context) {
    private val app = context.applicationContext
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    fun transcribeOnce(maxMs: Int = 8000, onResult: (Result<String>) -> Unit) {
        Thread {
            try {
                if (!WhisperLocalConfig.enabled(app)) throw IllegalStateException("Local Whisper is disabled.")
                val sampleRate = 16_000
                val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                require(min > 0) { "AudioRecord is unavailable." }
                val bufferSize = min * 2
                val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                require(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone recorder could not initialize." }
                val pcm = ByteArrayOutputStream()
                val buffer = ByteArray(min)
                recorder.startRecording()
                val end = System.currentTimeMillis() + maxMs
                while (System.currentTimeMillis() < end) {
                    val n = recorder.read(buffer, 0, buffer.size)
                    if (n > 0) pcm.write(buffer, 0, n)
                }
                recorder.stop(); recorder.release()
                val wav = wav(pcm.toByteArray(), sampleRate)
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("model", "whisper")
                    .addFormDataPart("language", "auto")
                    .addFormDataPart("file", "audio.wav", wav.toRequestBody("audio/wav".toMediaType()))
                    .build()
                val request = Request.Builder().url(WhisperLocalConfig.endpoint(app)).post(body).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("Whisper HTTP ${response.code}")
                    val text = response.body?.string().orEmpty()
                    val parsed = runCatching { JSONObject(text).optString("text") }.getOrDefault(text).trim()
                    if (parsed.isBlank()) throw IllegalStateException("Whisper returned no text.")
                    onResult(Result.success(SpeechTextNormalizer.normalize(parsed)))
                }
            } catch (t: Throwable) {
                LogStore.e("WHISPER", "Local Whisper transcription failed", t)
                onResult(Result.failure(t))
            }
        }.start()
    }

    private fun wav(pcm: ByteArray, rate: Int): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size)
        fun le(v: Int) { out.write(v and 255); out.write(v shr 8 and 255); out.write(v shr 16 and 255); out.write(v shr 24 and 255) }
        fun sh(v: Int) { out.write(v and 255); out.write(v shr 8 and 255) }
        out.write("RIFF".toByteArray()); le(36 + pcm.size); out.write("WAVEfmt ".toByteArray()); le(16); sh(1); sh(1); le(rate); le(rate * 2); sh(2); sh(16); out.write("data".toByteArray()); le(pcm.size); out.write(pcm); return out.toByteArray()
    }
}
