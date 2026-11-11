package com.ultron.app.memory

import android.content.ContentValues
import android.content.Context
import org.json.JSONObject

/** User-created shortcuts remain separate from LLM weights and are disabled until explicitly approved. */
object PersonalInstructionManager {
    data class Instruction(val id: Long, val name: String, val trigger: String, val workflowJson: String, val enabled: Boolean, val approved: Boolean)

    private var db: MemoryDatabase? = null
    fun init(context: Context) { if (db == null) db = MemoryDatabase(context) }

    fun save(name: String, trigger: String, workflow: JSONObject, approved: Boolean = false, enabled: Boolean = false): Long {
        val now = System.currentTimeMillis()
        return requireDb().writableDatabase.insertWithOnConflict("instructions", null, ContentValues().apply {
            put("name", name); put("trigger_text", trigger); put("workflow_json", workflow.toString())
            put("enabled", if (enabled && approved) 1 else 0); put("approved", if (approved) 1 else 0)
            put("created_at", now); put("updated_at", now)
        }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun approve(id: Long, enabled: Boolean = true) = updateFlags(id, enabled, true)
    fun setEnabled(id: Long, enabled: Boolean) = updateFlags(id, enabled, approved = null)
    fun delete(id: Long) { requireDb().writableDatabase.delete("instructions", "id=?", arrayOf(id.toString())) }

    fun match(trigger: String): Instruction? {
        requireDb().readableDatabase.rawQuery("SELECT id,name,trigger_text,workflow_json,enabled,approved FROM instructions WHERE enabled=1 AND approved=1", null).use { c ->
            while (c.moveToNext()) if (c.getString(2).equals(trigger.trim(), ignoreCase = true)) return Instruction(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4) == 1, c.getInt(5) == 1)
        }
        return null
    }

    fun list(): List<Instruction> {
        val out = mutableListOf<Instruction>()
        requireDb().readableDatabase.rawQuery("SELECT id,name,trigger_text,workflow_json,enabled,approved FROM instructions ORDER BY name", null).use { c ->
            while (c.moveToNext()) out += Instruction(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4) == 1, c.getInt(5) == 1)
        }
        return out
    }

    private fun updateFlags(id: Long, enabled: Boolean?, approved: Boolean?) {
        val values = ContentValues().apply {
            if (enabled != null) put("enabled", if (enabled && (approved != false)) 1 else 0)
            if (approved != null) put("approved", if (approved) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        requireDb().writableDatabase.update("instructions", values, "id=?", arrayOf(id.toString()))
    }

    private fun requireDb() = db ?: error("PersonalInstructionManager.init() has not been called")
}
