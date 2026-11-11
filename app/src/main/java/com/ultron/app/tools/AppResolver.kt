package com.ultron.app.tools

import android.content.Context
import android.content.Intent

/** Finds an installed app from a spoken name ("whatsapp", "messages", "yt music"...). */
object AppResolver {
    data class AppInfo(val label: String, val pkg: String)

    const val YOUTUBE = "com.google.android.youtube"
    const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
    const val SPOTIFY = "com.spotify.music"
    const val MAPS = "com.google.android.apps.maps"
    const val INSTAGRAM = "com.instagram.android"

    private val aliases: Map<String, List<String>> = mapOf(
        "whatsapp" to listOf("com.whatsapp"),
        "whatsapp business" to listOf("com.whatsapp.w4b"),
        "telegram" to listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"),
        "instagram" to listOf(INSTAGRAM),
        "insta" to listOf(INSTAGRAM),
        "messenger" to listOf("com.facebook.orca"),
        "facebook messenger" to listOf("com.facebook.orca"),
        "facebook" to listOf("com.facebook.katana"),
        "signal" to listOf("org.thoughtcrime.securesms"),
        "snapchat" to listOf("com.snapchat.android"),
        "snap" to listOf("com.snapchat.android"),
        "sms" to listOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms"),
        "messages" to listOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms"),
        "text messages" to listOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms"),
        "gmail" to listOf("com.google.android.gm"),
        "mail" to listOf("com.google.android.gm"),
        "slack" to listOf("com.Slack"),
        "discord" to listOf("com.discord"),
        "teams" to listOf("com.microsoft.teams"),
        "viber" to listOf("com.viber.voip"),
        "wechat" to listOf("com.tencent.mm"),
        "line" to listOf("jp.naver.line.android"),
        "twitter" to listOf("com.twitter.android"),
        "x" to listOf("com.twitter.android"),
        "youtube" to listOf(YOUTUBE),
        "yt" to listOf(YOUTUBE),
        "youtube music" to listOf(YOUTUBE_MUSIC),
        "yt music" to listOf(YOUTUBE_MUSIC),
        "spotify" to listOf(SPOTIFY),
        "chrome" to listOf("com.android.chrome"),
        "browser" to listOf("com.android.chrome", "com.sec.android.app.sbrowser"),
        "maps" to listOf(MAPS),
        "google maps" to listOf(MAPS),
        "play store" to listOf("com.android.vending"),
        "playstore" to listOf("com.android.vending"),
        "settings" to listOf("com.android.settings"),
        "camera" to listOf("com.android.camera", "com.google.android.GoogleCamera", "com.sec.android.app.camera"),
        "phone" to listOf("com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer"),
        "calculator" to listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator", "com.android.calculator2")
    )

    fun normalize(name: String): String = name.lowercase()
        .replace(Regex("\\b(the|app|application)\\b"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun isInstalled(context: Context, pkg: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(pkg) != null

    fun launchableApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(intent, 0).map {
                AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName)
            }.distinctBy { it.pkg }
        }.getOrDefault(emptyList())
    }

    /** Resolves a spoken app name to an installed launchable app, or null. */
    fun resolve(context: Context, spoken: String): AppInfo? {
        val key = normalize(spoken)
        if (key.isBlank()) return null

        aliases[key]?.firstOrNull { isInstalled(context, it) }?.let { pkg ->
            return AppInfo(labelOf(context, pkg) ?: key, pkg)
        }

        // Looks like a package name already.
        if (key.contains('.') && !key.contains(' ') && isInstalled(context, key)) {
            return AppInfo(labelOf(context, key) ?: key, key)
        }

        val apps = launchableApps(context)
        val compact = key.replace(" ", "")
        fun norm(s: String) = s.lowercase().replace(" ", "")
        return apps.firstOrNull { norm(it.label) == compact }
            ?: apps.firstOrNull { norm(it.label).startsWith(compact) }
            ?: apps.firstOrNull { compact.length >= 3 && norm(it.label).contains(compact) }
            ?: apps.firstOrNull { it.pkg.endsWith(".$compact") }
            ?: apps.filter { compact.length >= 4 }.minByOrNull { editDistance(norm(it.label), compact) }
                ?.takeIf { editDistance(norm(it.label), compact) <= maxOf(1, compact.length / 4) }
    }


    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    fun labelOf(context: Context, pkg: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    /** Messaging apps that are installed, for the "which app?" prompt. */
    fun installedMessagingApps(context: Context): List<AppInfo> {
        val names = listOf(
            "whatsapp", "whatsapp business", "telegram", "instagram", "messenger", "signal",
            "snapchat", "sms", "viber", "wechat", "line", "discord", "slack"
        )
        return names.mapNotNull { resolve(context, it) }.distinctBy { it.pkg }
    }
}
