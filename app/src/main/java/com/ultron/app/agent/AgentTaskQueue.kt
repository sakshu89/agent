package com.ultron.app.agent

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Durable task plan. Plans are stored locally and survive process death/reboot. */
class AgentTaskQueue private constructor(context: Context) {
    private val db = QueueDb(context.applicationContext)
    private val gson = Gson()

    data class Step(val toolName: String, val appName: String = "", val contactName: String = "", val message: String = "", val query: String = "", val action: String = "", val target: String = "", val requiresConfirmation: Boolean = false)
    data class Task(val id: String, val title: String, val steps: List<Step>, val status: String, val currentStep: Int, val approvedStep: Int, val lastError: String?)

    @Synchronized fun enqueue(context: Context, title: String, steps: List<Step>, requiresUserApproval: Boolean = true): String {
        require(title.isNotBlank() && steps.isNotEmpty())
        require(steps.size <= 30) { "A task plan is limited to 30 steps." }
        val id = UUID.randomUUID().toString()
        val status = if (requiresUserApproval || steps.any { it.requiresConfirmation || it.toolName == "send_message_tool" }) "AWAITING_APPROVAL" else "QUEUED"
        db.writableDatabase.execSQL("INSERT INTO tasks(id,title,steps,status,current_step,last_error,updated_at) VALUES(?,?,?,?,?,?,?)", arrayOf(id,title,gson.toJson(steps),status,0,null,System.currentTimeMillis()))
        if (status == "QUEUED") schedule(context, id)
        return id
    }

    @Synchronized fun approve(context: Context, id: String): Boolean {
        val step = get(id)?.currentStep ?: return false
        val values = android.content.ContentValues().apply {
            put("status", "QUEUED")
            put("approved_step", step)
            put("updated_at", System.currentTimeMillis())
        }
        val changed = db.writableDatabase.update("tasks", values, "id=? AND status='AWAITING_APPROVAL'", arrayOf(id)) > 0
        if (changed) schedule(context, id)
        return changed
    }

    @Synchronized fun cancel(id: String): Boolean = db.writableDatabase.update("tasks", android.content.ContentValues().apply { put("status", "CANCELLED"); put("updated_at", System.currentTimeMillis()) }, "id=? AND status IN ('QUEUED','RUNNING','AWAITING_APPROVAL')", arrayOf(id)) > 0

    @Synchronized fun get(id: String): Task? {
        db.readableDatabase.rawQuery("SELECT id,title,steps,status,current_step,approved_step,last_error FROM tasks WHERE id=?", arrayOf(id)).use { c ->
            if (!c.moveToFirst()) return null
            val type = object : TypeToken<List<Step>>() {}.type
            return Task(c.getString(0), c.getString(1), gson.fromJson(c.getString(2), type), c.getString(3), c.getInt(4), c.getInt(5), c.getString(6))
        }
    }

    @Synchronized internal fun update(id: String, status: String, step: Int, error: String? = null) {
        db.writableDatabase.update("tasks", android.content.ContentValues().apply { put("status",status); put("current_step",step); put("last_error",error); put("updated_at",System.currentTimeMillis()) }, "id=?", arrayOf(id))
    }

    private fun schedule(context: Context, id: String) {
        val request = OneTimeWorkRequestBuilder<AgentTaskWorker>().setInputData(workDataOf(AgentTaskWorker.KEY_TASK_ID to id))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("ultron-task-$id", ExistingWorkPolicy.KEEP, request)
    }

    private class QueueDb(context: Context) : SQLiteOpenHelper(context, "ultron_agent_tasks.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE tasks(id TEXT PRIMARY KEY,title TEXT NOT NULL,steps TEXT NOT NULL,status TEXT NOT NULL,current_step INTEGER NOT NULL,approved_step INTEGER NOT NULL DEFAULT -1,last_error TEXT,updated_at INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX task_status_idx ON tasks(status,updated_at)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object { @Volatile private var instance: AgentTaskQueue? = null; fun get(context: Context) = instance ?: synchronized(this) { instance ?: AgentTaskQueue(context).also { instance = it } } }
}
