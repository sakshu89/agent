package com.ultron.app.notifications

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/** Local-only notification insights. Content is retained only in memory and only from allowlisted apps. */
object NotificationInsightManager {
    private val recent = CopyOnWriteArrayList<UltronNotificationListener.NotificationEvent>()
    @Volatile private var installed = false

    private val listener: (UltronNotificationListener.NotificationEvent) -> Unit = { event ->
        recent.add(event)
        while (recent.size > 100) recent.removeAt(0)
    }

    fun init() {
        if (installed) return
        UltronNotificationListener.addListener(listener)
        installed = true
    }

    fun recent(limit: Int = 20): List<UltronNotificationListener.NotificationEvent> = recent.takeLast(limit.coerceIn(1, 100)).asReversed()

    fun important(limit: Int = 20): List<UltronNotificationListener.NotificationEvent> = recent(limit).filter { it.category in setOf("SECURITY", "FINANCE", "CALENDAR", "DELIVERY") }.take(limit)

    fun dailySummary(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val today = recent.filter { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it.postedAt)) == date }
        if (today.isEmpty()) return "No allowlisted notifications captured today."
        val counts = today.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }
        return buildString {
            append("Today: ${today.size} allowlisted notifications. ")
            append(counts.joinToString(", ") { "${it.key.lowercase()} ${it.value}" })
            val important = today.firstOrNull { it.category in setOf("SECURITY", "FINANCE", "CALENDAR", "DELIVERY") }
            if (important != null) append(" Important: ${important.title.ifBlank { important.text }.take(160)}")
        }
    }
}
