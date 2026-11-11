package com.ultron.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Imports and stores a Vosk speech model (the zip you download from alphacephei.com/vosk/models).
 * The zip is unpacked into the app's private storage; nothing leaves the phone.
 */
object VoskModelManager {
    private const val PREFS = "ultron_preferences"
    private const val KEY_ENABLED = "vosk_enabled"
    private const val KEY_NAME = "vosk_model_name"
    private const val MODEL_DIR = "vosk_model" // legacy single-model location
    private const val MODELS_DIR = "vosk_models"
    private const val KEY_MODEL_DIR = "vosk_model_dir"
    private const val TMP_DIR = "vosk_import"

    private val main = Handler(Looper.getMainLooper())

    // Observed by the Settings screen.
    var installed by mutableStateOf(false)
        private set
    var modelName by mutableStateOf("")
        private set
    var importing by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var enabled by mutableStateOf(true)
        private set

    /** Bumped whenever the model files change, so the engine knows to reload. */
    @Volatile
    var generation = 0
        private set

    fun refresh(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        installed = modelDir(context) != null
        modelName = if (installed) prefs.getString(KEY_NAME, "").orEmpty() else ""
        enabled = prefs.getBoolean(KEY_ENABLED, true)
        if (message.isBlank()) {
            message = if (installed) "Model ready: $modelName" else "No model imported yet."
        }
    }

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, value).apply()
        enabled = value
        generation++
        LogStore.i("VOSK", "Offline voice (Vosk) ${if (value) "enabled" else "disabled"}.")
    }

    /** True when a model exists and the user wants to use it. */
    fun shouldUse(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true) &&
            modelDir(context) != null

    fun modelDir(context: Context): File? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val selected = prefs.getString(KEY_MODEL_DIR, null)
        val dir = selected?.let { File(File(context.filesDir, MODELS_DIR), it) }
            ?: File(context.filesDir, MODEL_DIR)
        return dir.takeIf { it.isDirectory && looksLikeModel(it) }
    }

    fun installedModels(context: Context): List<String> {
        val root = File(context.filesDir, MODELS_DIR)
        return root.listFiles()?.filter { it.isDirectory && looksLikeModel(it) }?.map { it.name }?.sorted().orEmpty()
    }

    fun selectModel(context: Context, name: String): Boolean {
        if (name !in installedModels(context)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODEL_DIR, name).putString(KEY_NAME, name).apply()
        generation++
        refresh(context)
        AssistantController.onVoskModelChanged()
        return true
    }

    private fun looksLikeModel(dir: File): Boolean =
        File(dir, "am").isDirectory && File(dir, "conf").isDirectory

    fun remove(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val selected = prefs.getString(KEY_MODEL_DIR, null)
        if (selected != null) File(File(context.filesDir, MODELS_DIR), selected).deleteRecursively()
        else File(context.filesDir, MODEL_DIR).deleteRecursively()
        prefs.edit().remove(KEY_NAME).remove(KEY_MODEL_DIR).apply()
        generation++
        installed = false
        modelName = ""
        message = "Model removed."
        LogStore.w("VOSK", "Model removed.")
        AssistantController.onVoskModelChanged()
    }

    /** Unpacks the chosen zip. Runs in the background; [onDone] is called on the main thread. */
    fun importZip(context: Context, uri: Uri, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        if (importing) return
        val app = context.applicationContext
        importing = true
        post("Importing model... this can take a minute.")
        LogStore.a("VOSK", "Importing model from $uri")

        Thread {
            var ok = false
            var result = ""
            val tmp = File(app.filesDir, TMP_DIR)
            try {
                tmp.deleteRecursively()
                tmp.mkdirs()
                val input = app.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open the selected file.")
                var total = 0L
                var nextReport = 25L * 1024 * 1024
                val rootPath = tmp.canonicalPath + File.separator
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val out = File(tmp, entry.name)
                        // Refuse entries that would escape the target folder.
                        if (!out.canonicalPath.startsWith(rootPath)) {
                            throw SecurityException("Unsafe path in zip: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { stream ->
                                while (true) {
                                    val n = zip.read(buffer)
                                    if (n < 0) break
                                    stream.write(buffer, 0, n)
                                    total += n
                                }
                            }
                            if (total > nextReport) {
                                nextReport += 25L * 1024 * 1024
                                post("Extracting... ${total / (1024 * 1024)} MB")
                            }
                        }
                        zip.closeEntry()
                    }
                }

                val root = tmp.walkTopDown().maxDepth(4)
                    .firstOrNull { it.isDirectory && looksLikeModel(it) }
                    ?: throw IllegalStateException(
                        "This zip does not look like a Vosk model (no am/ and conf/ folders). " +
                            "Download a model zip from alphacephei.com/vosk/models and try again."
                    )

                val name = (if (root == tmp) "vosk-model" else root.name)
                    .replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifBlank { "vosk-model" }
                val released = java.util.concurrent.CountDownLatch(1)
                main.post {
                    try { AssistantController.onVoskModelChanged(releaseOnly = true) } finally { released.countDown() }
                }
                released.await(5, java.util.concurrent.TimeUnit.SECONDS)
                val modelsRoot = File(app.filesDir, MODELS_DIR).apply { mkdirs() }
                var target = File(modelsRoot, name)
                if (target.exists()) target = File(modelsRoot, "$name-${System.currentTimeMillis()}")
                if (!root.renameTo(target)) root.copyRecursively(target, overwrite = false)
                val selectedName = target.name
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_NAME, selectedName).putString(KEY_MODEL_DIR, selectedName).apply()
                generation++
                ok = true
                result = "Model imported: $name"
            } catch (t: Throwable) {
                LogStore.e("VOSK", "Model import failed", t)
                result = "Import failed: ${t.message ?: t.javaClass.simpleName}"
            } finally {
                runCatching { tmp.deleteRecursively() }
            }

            main.post {
                importing = false
                refresh(app)
                message = result
                if (ok) LogStore.a("VOSK", result) else LogStore.e("VOSK", result)
                AssistantController.onVoskModelChanged()
                onDone(ok, result)
            }
        }.start()
    }

    private fun post(text: String) {
        main.post { message = text }
    }
}
