package com.ultron.app.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Compact resumable context for the active goal. Sensitive data is not inferred or stored. */
class TaskContextStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ultron_agent_context", Context.MODE_PRIVATE)
    private val key = "active"
    private val maxEventsPerBucket = 24

    @Synchronized fun begin(goal: String) {
        val now = System.currentTimeMillis()
        prefs.edit().putString(key, JSONObject().apply {
            put("goal", goal.take(2000))
            put("startedAt", now)
            put("updatedAt", now)
            put("completed", JSONArray())
            put("failures", JSONArray())
            put("recoveries", JSONArray())
        }.toString()).apply()
    }

    @Synchronized fun recordStep(step: String, result: String, success: Boolean) {
        val root = read() ?: return
        val arrayName = if (success) "completed" else "failures"
        val bucket = root.getJSONArray(arrayName)
        bucket.put(JSONObject().apply {
            put("step", step.take(500)); put("result", result.take(1200)); put("at", System.currentTimeMillis())
        })
        while (bucket.length() > maxEventsPerBucket) bucket.remove(0)
        root.put("updatedAt", System.currentTimeMillis())
        write(root)
    }

    @Synchronized fun recordRecovery(reason: String) {
        val root = read() ?: return
        val bucket = root.getJSONArray("recoveries")
        bucket.put(JSONObject().apply { put("reason", reason.take(800)); put("at", System.currentTimeMillis()) })
        while (bucket.length() > maxEventsPerBucket) bucket.remove(0)
        root.put("updatedAt", System.currentTimeMillis())
        write(root)
    }

    @Synchronized fun summary(): String {
        val root = read() ?: return "No active task context."
        return buildString {
            appendLine("Goal: ${root.optString("goal")}")
            appendLine("Completed: ${summarize(root.optJSONArray("completed"))}")
            appendLine("Failures: ${summarize(root.optJSONArray("failures"))}")
            appendLine("Recoveries: ${summarize(root.optJSONArray("recoveries"))}")
        }.trim()
    }

    @Synchronized fun clear() { prefs.edit().remove(key).apply() }

    private fun summarize(array: JSONArray?): String {
        if (array == null || array.length() == 0) return "none"
        return (0 until array.length()).joinToString(" | ") { index ->
            val item = array.optJSONObject(index) ?: return@joinToString ""
            val step = item.optString("step", item.optString("reason"))
            val result = item.optString("result", "")
            if (result.isBlank()) step else "$step -> $result"
        }.take(3000)
    }

    private fun read(): JSONObject? = prefs.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    private fun write(root: JSONObject) { prefs.edit().putString(key, root.toString()).apply() }
}
