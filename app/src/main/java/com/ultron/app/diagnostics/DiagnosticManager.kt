package com.ultron.app.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.ultron.app.LocalServerConfig
import com.ultron.app.LogStore
import com.ultron.app.UltronAccessibilityService
import com.ultron.app.VoskModelManager
import com.ultron.app.memory.LongTermMemoryManager
import com.ultron.app.memory.SessionManager
import java.io.File

object DiagnosticManager {
    data class Check(val name: String, val ok: Boolean, val detail: String)

    fun run(context: Context): List<Check> = listOf(
        Check("Microphone", ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED, "RECORD_AUDIO permission"),
        Check("Accessibility", UltronAccessibilityService.getInstance() != null, "ITRA accessibility service"),
        Check("Vosk", !VoskModelManager.shouldUse(context) || VoskModelManager.modelDir(context) != null, if (VoskModelManager.shouldUse(context)) "Selected offline speech model" else "Vosk disabled or not installed"),
        Check("Memory DB", runCatching { LongTermMemoryManager.search("", 1); true }.getOrDefault(false), "SQLite memory store"),
        Check("Sessions DB", runCatching { SessionManager.list(1); true }.getOrDefault(false), "SQLite session store"),
        Check("Local endpoint", runCatching { LocalServerConfig.getBaseUrl(context); true }.getOrDefault(false), LocalServerConfig.getBaseUrl(context)),
        Check("Storage", context.filesDir.exists() && context.filesDir.canWrite(), context.filesDir.absolutePath),
        Check("Notifications", Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED, "POST_NOTIFICATIONS permission")
    )

    fun exportReport(context: Context): File {
        val file = File(context.cacheDir, "ultron-diagnostic-${System.currentTimeMillis()}.txt")
        file.writeText(buildString {
            appendLine("ITRA 5.0 diagnostic report")
            appendLine("Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            appendLine("Device ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Local endpoint ${LocalServerConfig.getBaseUrl(context)}")
            appendLine()
            run(context).forEach { appendLine("${if (it.ok) "OK" else "FAIL"} | ${it.name} | ${it.detail}") }
            appendLine()
            appendLine("Recent logs")
            appendLine(LogStore.exportText())
        })
        return file
    }
}
