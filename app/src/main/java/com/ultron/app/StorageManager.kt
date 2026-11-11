package com.ultron.app

import android.content.Context
import java.io.File

/** Private, quota-aware ULTRON storage. No automatic deletion is performed at the quota boundary. */
object StorageManager {
    const val MAX_BYTES = 5L * 1024L * 1024L * 1024L
    private lateinit var root: File

    fun init(context: Context) {
        root = File(context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir, "ULTRON").also {
            listOf("Memory/Sessions", "Memory/PersonalMemory", "Memory/LearnedInstructions", "Memory/Index", "Exports").forEach { p -> File(it, p).mkdirs() }
        }
    }

    fun root(): File = root
    fun sessions(): File = File(root, "Memory/Sessions")
    fun usageBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    fun remainingBytes(): Long = (MAX_BYTES - usageBytes()).coerceAtLeast(0)
    fun usageFraction(): Double = usageBytes().toDouble() / MAX_BYTES.toDouble()
    fun warningLevel(): Int = when { usageFraction() >= 1.0 -> 100; usageFraction() >= .90 -> 90; usageFraction() >= .80 -> 80; else -> 0 }

    fun accessibleFolderIntent(): android.content.Intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }
}
