package com.ultron.app.memory

import android.content.ContentValues
import com.ultron.app.StorageManager
import android.content.Context
import java.util.UUID

/** Persistent episodic conversation storage. Writes are incremental, so shutdown is not required. */
object SessionManager {
    data class Session(
        val id: String,
        val title: String,
        val summary: String,
        val startedAt: Long,
        val endedAt: Long?,
        val durationMs: Long
    )

    data class Message(val role: String, val content: String, val timestamp: Long)

    private var db: MemoryDatabase? = null
    private var activeSessionId: String? = null

    fun init(context: Context) {
        if (db == null) db = MemoryDatabase(context)
    }

    @Synchronized
    fun start(title: String = "Untitled session"): String {
        val database = requireDb()
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.writableDatabase.insertOrThrow("sessions", null, ContentValues().apply {
            put("id", id); put("title", title.ifBlank { "Untitled session" }); put("started_at", now)
        })
        activeSessionId = id
        return id
    }

    @Synchronized
    fun ensureActive(): String = activeSessionId ?: start()

    fun addMessage(role: String, content: String, sessionId: String = ensureActive()) {
        if (content.isBlank()) return
        requireDb().writableDatabase.insert("messages", null, ContentValues().apply {
            put("session_id", sessionId); put("role", role); put("content", content); put("timestamp", System.currentTimeMillis())
        })
    }

    fun finish(summary: String = "", title: String? = null, sessionId: String? = activeSessionId) {
        val id = sessionId ?: return
        val database = requireDb().writableDatabase
        val ended = System.currentTimeMillis()
        val started = database.rawQuery("SELECT started_at FROM sessions WHERE id = ?", arrayOf(id)).use {
            if (it.moveToFirst()) it.getLong(0) else ended
        }
        database.update("sessions", ContentValues().apply {
            put("ended_at", ended); put("duration_ms", (ended - started).coerceAtLeast(0));
            if (!summary.isNullOrBlank()) put("summary", summary)
            if (!title.isNullOrBlank()) put("title", title)
        }, "id = ?", arrayOf(id))
        runCatching {
            val file = java.io.File(StorageManager.sessions(), "${id}.md")
            file.writeText(export(id))
        }
        if (activeSessionId == id) activeSessionId = null
    }

    fun updateTitleAndSummary(sessionId: String, title: String, summary: String) {
        requireDb().writableDatabase.update("sessions", ContentValues().apply {
            put("title", title.ifBlank { "Untitled session" }); put("summary", summary)
        }, "id = ?", arrayOf(sessionId))
    }

    fun list(limit: Int = 100): List<Session> {
        val out = mutableListOf<Session>()
        requireDb().readableDatabase.rawQuery(
            "SELECT id,title,summary,started_at,ended_at,duration_ms FROM sessions ORDER BY started_at DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 500).toString())
        ).use { c ->
            while (c.moveToNext()) out += Session(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), if (c.isNull(4)) null else c.getLong(4), c.getLong(5))
        }
        return out
    }

    fun messages(sessionId: String): List<Message> {
        val out = mutableListOf<Message>()
        requireDb().readableDatabase.rawQuery("SELECT role,content,timestamp FROM messages WHERE session_id=? ORDER BY timestamp ASC", arrayOf(sessionId)).use { c ->
            while (c.moveToNext()) out += Message(c.getString(0), c.getString(1), c.getLong(2))
        }
        return out
    }

    fun search(query: String, limit: Int = 30): List<Session> {
        val q = "%${query.trim()}%"
        if (query.isBlank()) return list(limit)
        val out = mutableListOf<Session>()
        requireDb().readableDatabase.rawQuery(
            "SELECT id,title,summary,started_at,ended_at,duration_ms FROM sessions WHERE title LIKE ? OR summary LIKE ? ORDER BY started_at DESC LIMIT ?",
            arrayOf(q, q, limit.coerceIn(1, 200).toString())
        ).use { c -> while (c.moveToNext()) out += Session(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), if (c.isNull(4)) null else c.getLong(4), c.getLong(5)) }
        return out
    }

    fun delete(sessionId: String) {
        requireDb().writableDatabase.delete("messages", "session_id=?", arrayOf(sessionId))
        requireDb().writableDatabase.delete("sessions", "id=?", arrayOf(sessionId))
        if (activeSessionId == sessionId) activeSessionId = null
    }

    fun export(sessionId: String): String = buildString {
        val s = list(500).firstOrNull { it.id == sessionId } ?: return@buildString
        appendLine("# ${s.title}")
        appendLine("Started: ${java.util.Date(s.startedAt)}")
        if (s.summary.isNotBlank()) appendLine("Summary: ${s.summary}")
        appendLine()
        messages(sessionId).forEach { appendLine("${it.role.uppercase()}: ${it.content}") }
    }

    private fun requireDb(): MemoryDatabase = db ?: error("SessionManager.init() has not been called")
}
