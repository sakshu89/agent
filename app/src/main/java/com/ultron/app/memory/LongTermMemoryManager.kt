package com.ultron.app.memory

import android.content.ContentValues
import android.content.Context

/** Explicit, user-controlled long-term memories. Sensitive data should not be stored unless the user asks. */
object LongTermMemoryManager {
    data class Memory(val id: Long, val category: String, val key: String, val value: String, val source: String, val updatedAt: Long)
    private var db: MemoryDatabase? = null

    fun init(context: Context) { if (db == null) db = MemoryDatabase(context) }

    @Synchronized fun remember(category: String, key: String, value: String, source: String = "user"): Long {
        require(category.isNotBlank() && key.isNotBlank() && value.isNotBlank())
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("category", category.trim().take(40)); put("memory_key", key.trim().take(120))
            put("memory_value", value.trim().take(2000)); put("source", source.take(40)); put("updated_at", now)
        }
        return database.writableDatabase.insertWithOnConflict("memories", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun search(query: String, limit: Int = 20): List<Memory> {
        val q = "%${query.trim()}%"
        val out = mutableListOf<Memory>()
        database.readableDatabase.rawQuery(
            "SELECT id,category,memory_key,memory_value,source,updated_at FROM memories WHERE memory_key LIKE ? OR memory_value LIKE ? OR category LIKE ? ORDER BY updated_at DESC LIMIT ?",
            arrayOf(q, q, q, limit.coerceIn(1, 100).toString())
        ).use { c -> while (c.moveToNext()) out += row(c) }
        return out
    }

    fun recent(limit: Int = 30): List<Memory> = search("", limit)

    fun delete(id: Long): Boolean = database.writableDatabase.delete("memories", "id=?", arrayOf(id.toString())) > 0
    fun delete(category: String, key: String): Boolean = database.writableDatabase.delete("memories", "category=? AND memory_key=?", arrayOf(category, key)) > 0

    /** Small context packet suitable for a local model prompt. */
    fun contextFor(query: String, limit: Int = 8): String = search(query, limit).joinToString("\n") {
        "- [${it.category}] ${it.key}: ${it.value}"
    }

    private val database get() = db ?: error("LongTermMemoryManager.init() has not been called")
    private fun row(c: android.database.Cursor) = Memory(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5))
}
