package com.ultron.app.model

import android.content.Context
import android.net.Uri
import com.ultron.app.LogStore
import java.io.File
import java.util.zip.ZipInputStream

/** Registry for optional on-device model packages. Runtime-specific engines consume these packages. */
object LocalModelManager {
    enum class Kind { SPEECH, NLU, OCR, VISION, TEXT }
    data class Model(val name: String, val kind: Kind, val directory: File, val sizeBytes: Long)
    private const val ROOT = "local_models"

    fun list(context: Context): List<Model> = root(context).listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
        val meta = File(dir, "model.properties")
        val props = java.util.Properties().apply { if (meta.isFile) meta.inputStream().use(::load) }
        val kind = runCatching { Kind.valueOf(props.getProperty("kind", "TEXT").uppercase()) }.getOrDefault(Kind.TEXT)
        Model(dir.name, kind, dir, dir.walkTopDown().filter { it.isFile }.sumOf { it.length() })
    }?.sortedBy { it.name } ?: emptyList()

    fun importPackage(context: Context, uri: Uri, kind: Kind, name: String = "model-${System.currentTimeMillis()}"): Result<Model> = runCatching {
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "model" }
        val temp = File(context.cacheDir, "model-import-${System.currentTimeMillis()}").apply { deleteRecursively(); mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                val base = temp.canonicalPath + File.separator
                while (true) {
                    val e = zip.nextEntry ?: break
                    val out = File(temp, e.name)
                    require(out.canonicalPath.startsWith(base)) { "Unsafe model archive path" }
                    if (e.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); out.outputStream().use { zip.copyTo(it) } }
                }
            }
        } ?: error("Cannot open model file")
        val dest = File(root(context), safeName).let { if (!it.exists()) it else File(root(context), "$safeName-${System.currentTimeMillis()}") }
        temp.renameTo(dest).also { if (!it) temp.copyRecursively(dest) }
        temp.deleteRecursively()
        val props = java.util.Properties().apply { setProperty("kind", kind.name); setProperty("name", dest.name); File(dest, "model.properties").outputStream().use { out -> store(out, "ITRA model metadata") } }
        LogStore.a("MODEL", "Imported ${kind.name} model ${dest.name}")
        Model(dest.name, kind, dest, dest.walkTopDown().filter { it.isFile }.sumOf { it.length() })
    }

    fun remove(context: Context, name: String): Boolean = File(root(context), name).takeIf { it.isDirectory }?.deleteRecursively() == true
    fun totalBytes(context: Context): Long = list(context).sumOf { it.sizeBytes }
    private fun root(context: Context) = File(context.filesDir, ROOT).apply { mkdirs() }
}
