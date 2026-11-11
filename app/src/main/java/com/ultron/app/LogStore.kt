package com.ultron.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * App-wide activity log. Entries are kept in memory (for the LOGS screen) and
 * appended to a private file so they survive restarts. Safe to call from any thread.
 */
object LogStore {
    enum class Level { INFO, WARN, ERROR, ACTION }

    data class Entry(
        val time: Long,
        val level: Level,
        val tag: String,
        val message: String
    )

    private const val MAX_ENTRIES = 600
    private const val MAX_FILE_BYTES = 512 * 1024L
    private const val FILE_NAME = "ultron.log"

    val entries = mutableStateListOf<Entry>()

    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    private var file: File? = null

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val target = File(context.applicationContext.filesDir, FILE_NAME)
        file = target
        val restored = runCatching { readFile(target) }.getOrDefault(emptyList())
        main.post {
            if (entries.isEmpty()) entries.addAll(restored)
        }
    }

    fun i(tag: String, message: String) = add(Level.INFO, tag, message)
    fun w(tag: String, message: String) = add(Level.WARN, tag, message)
    fun a(tag: String, message: String) = add(Level.ACTION, tag, message)
    fun e(tag: String, message: String, error: Throwable? = null) {
        val full = if (error != null) "$message (${error.javaClass.simpleName}: ${error.message})" else message
        add(Level.ERROR, tag, full)
    }

    private fun add(level: Level, tag: String, message: String) {
        val entry = Entry(System.currentTimeMillis(), level, tag, message)
        when (level) {
            Level.ERROR -> Log.e("ITRA_$tag", message)
            Level.WARN -> Log.w("ITRA_$tag", message)
            else -> Log.i("ITRA_$tag", message)
        }
        main.post {
            entries.add(entry)
            while (entries.size > MAX_ENTRIES) entries.removeAt(0)
        }
        val target = file ?: return
        writer.execute {
            runCatching {
                if (target.length() > MAX_FILE_BYTES) {
                    val keep = readFile(target).takeLast(MAX_ENTRIES / 2)
                    target.writeText(keep.joinToString("") { serialize(it) })
                }
                target.appendText(serialize(entry))
            }
        }
    }

    fun clear() {
        main.post { entries.clear() }
        val target = file ?: return
        writer.execute { runCatching { target.writeText("") } }
    }

    fun format(entry: Entry): String =
        "${timeFormat(entry.time)} ${entry.level.name.padEnd(6)} ${entry.tag}: ${entry.message}"

    fun exportText(): String = entries.toList().joinToString("\n") { format(it) }

    fun timeFormat(time: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(time))

    private fun serialize(e: Entry): String =
        "${e.time}\t${e.level.name}\t${e.tag}\t${e.message.replace("\\", "\\\\").replace("\n", "\\n")}\n"

    private fun readFile(f: File): List<Entry> {
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            val p = line.split('\t', limit = 4)
            if (p.size < 4) return@mapNotNull null
            val t = p[0].toLongOrNull() ?: return@mapNotNull null
            val lvl = runCatching { Level.valueOf(p[1]) }.getOrDefault(Level.INFO)
            Entry(t, lvl, p[2], p[3].replace("\\n", "\n").replace("\\\\", "\\"))
        }.takeLast(MAX_ENTRIES)
    }
}
