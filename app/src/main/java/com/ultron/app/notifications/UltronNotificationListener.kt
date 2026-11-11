package com.ultron.app.notifications

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArrayList

/** Notification text is exposed only after explicit opt-in and package allowlisting. */
class UltronNotificationListener : NotificationListenerService() {
    data class NotificationEvent(val packageName: String, val title: String, val text: String, val postedAt: Long, val category: String)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val item = sbn ?: return
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return
        val allowed = prefs.getStringSet(KEY_ALLOWED, emptySet()).orEmpty()
        if (item.packageName !in allowed) return
        val extras = item.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty().take(200)
        val text = (extras.getCharSequence("android.bigText") ?: extras.getCharSequence("android.text"))
            ?.toString().orEmpty().take(2000)
        if (title.isBlank() && text.isBlank()) return
        val event = NotificationEvent(item.packageName, title, text, item.postTime, classify(item.packageName, title, text))
        listeners.forEach { listener -> runCatching { listener(event) } }
        // No disk persistence, forwarding, or LLM upload. Consumers must request processing separately.
    }

    private fun classify(packageName: String, title: String, text: String): String {
        val value = "$packageName $title $text".lowercase()
        return when {
            Regex("\\b(otp|one.time.password|verification code|login code|security alert)\\b").containsMatchIn(value) -> "SECURITY"
            Regex("\\b(meeting|calendar|event|invitation|reminder)\\b").containsMatchIn(value) -> "CALENDAR"
            Regex("\\b(delivery|shipped|out for delivery|order dispatched|tracking)\\b").containsMatchIn(value) -> "DELIVERY"
            Regex("\\b(payment|debited|credited|transaction|bank|upi)\\b").containsMatchIn(value) -> "FINANCE"
            packageName.contains("whatsapp") || packageName.contains("telegram") || packageName.contains("messenger") || packageName.contains("messages") -> "MESSAGE"
            else -> "GENERAL"
        }
    }

    companion object {
        private const val PREFS = "ultron_notifications"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ALLOWED = "allowed_packages"
        private val listeners = CopyOnWriteArrayList<(NotificationEvent) -> Unit>()

        fun isEnabled(context: Context): Boolean = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

        fun allowedPackages(context: Context): Set<String> = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_ALLOWED, emptySet()).orEmpty().toSet()

        fun configure(context: Context, enabled: Boolean, allowedPackages: Set<String>) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putStringSet(KEY_ALLOWED, allowedPackages.toSet())
                .apply()
        }

        fun addListener(listener: (NotificationEvent) -> Unit) { listeners.add(listener) }
        fun removeListener(listener: (NotificationEvent) -> Unit) { listeners.remove(listener) }
    }
}
