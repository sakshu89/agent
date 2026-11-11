package com.ultron.app.tools

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.net.Uri
import com.ultron.app.LogStore
import com.ultron.app.UltronAccessibilityService

/**
 * Carries out a [Command]. [onDone] is always called exactly once, on the main thread,
 * with (success, sentence-to-show-and-speak).
 */
object CommandExecutor {
    private const val TAG = "EXEC"
    private const val PREFS = "ultron_preferences"
    private const val KEY_LAST_CHAT_APP = "last_chat_app"

    fun run(
        context: Context,
        command: Command,
        onStatus: (String) -> Unit,
        onDone: (Boolean, String) -> Unit
    ) {
        LogStore.a(TAG, "Running $command")
        when (command) {
            is Command.OpenApp -> openApp(context, command.app, onDone)
            is Command.PlayMedia -> playMedia(context, command.query, command.app, onStatus, onDone)
            is Command.PlayFirstResult -> playFirstResult(onStatus, onDone)
            is Command.OpenInstagramInbox -> openInstagramInbox(onStatus, onDone)
            is Command.Search -> search(context, command.query, command.app, onDone)
            is Command.Research -> research(context, command.query, onStatus, onDone)
            is Command.OpenUrl -> openUrl(context, command.url, onDone)
            is Command.Navigate -> navigate(context, command.place, onDone)
            is Command.SendMessage -> sendMessage(context, command.app, command.contact, command.message, onStatus, onDone)
            is Command.ScreenAction -> screenAction(command, onDone)
            Command.ReadScreen -> {
                val service = UltronAccessibilityService.getInstance()
                if (service == null) onDone(false, "Enable ITRA in Android Accessibility settings to read the visible screen.")
                else service.readVisibleScreen { result ->
                    result.fold(
                        onSuccess = { text -> onDone(true, if (text.isBlank()) "I couldn't detect readable text on this screen." else "Visible screen text:\n${text.take(6000)}") },
                        onFailure = { error -> onDone(false, "I couldn't read the screen: ${error.message ?: "unknown error"}") }
                    )
                }
            }
            is Command.SetRingerMode -> setRingerMode(context, command.mode, onDone)
            is Command.SetVolume -> setVolume(context, command.percent, onDone)
            is Command.SetBrightness -> setBrightness(context, command.percent, onDone)
            is Command.NeedMoreInfo -> onDone(false, command.question)
        }
    }

    private fun setRingerMode(context: Context, mode: String, onDone: (Boolean, String) -> Unit) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (mode == "silent" && android.app.NotificationManager::class.java.let { context.getSystemService(it) }.let { it != null && !(it as android.app.NotificationManager).isNotificationPolicyAccessGranted }) {
            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            onDone(false, "Android requires Do Not Disturb policy access before ITRA can set silent mode. I opened the relevant settings page.")
            return
        }
        runCatching { audio.ringerMode = if (mode == "silent") AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_VIBRATE }
            .onSuccess { onDone(true, if (mode == "silent") "Phone set to silent." else "Phone set to vibrate.") }
            .onFailure { onDone(false, "Android did not allow the ringer mode change: ${it.message ?: "permission denied"}") }
    }

    private fun setVolume(context: Context, percent: Int, onDone: (Boolean, String) -> Unit) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (max * percent / 100f).toInt().coerceIn(0, max), 0)
        onDone(true, "Media volume set to $percent percent.")
    }

    private fun setBrightness(context: Context, percent: Int, onDone: (Boolean, String) -> Unit) {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            onDone(false, "Android requires the Modify System Settings permission before ITRA can change brightness.")
            return
        }
        val value = (255 * percent / 100f).toInt().coerceIn(1, 255)
        runCatching { Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value) }
            .onSuccess { onDone(true, "Brightness set to $percent percent.") }
            .onFailure { onDone(false, "I couldn't change brightness: ${it.message ?: "permission denied"}") }
    }

    // ---------------------------------------------------------------- messaging

    /** The messaging app to use when the user did not name one. */
    fun defaultChatApp(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_CHAT_APP, null)

    fun sendMessage(
        context: Context,
        appName: String?,
        contact: String,
        message: String,
        onStatus: (String) -> Unit,
        onDone: (Boolean, String) -> Unit
    ) {
        val name = appName?.takeIf { it.isNotBlank() } ?: defaultChatApp(context)
        if (name == null) {
            val installed = AppResolver.installedMessagingApps(context).joinToString(", ") { it.label }
            onDone(false, "Which app should I use? " + if (installed.isNotBlank()) "You have $installed." else "")
            return
        }
        val app = AppResolver.resolve(context, name)
        if (app == null) {
            onDone(false, "I couldn't find an app called $name on this phone.")
            return
        }
        val service = UltronAccessibilityService.getInstance()
        if (service == null) {
            onDone(false, "Accessibility service is not enabled. Turn on ITRA in Accessibility settings first.")
            return
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_CHAT_APP, app.label).apply()
        service.sendAppMessage(app.pkg, contact, message, onStatus, onDone)
    }

    // ---------------------------------------------------------------- on-screen actions

    private fun screenAction(command: Command.ScreenAction, onDone: (Boolean, String) -> Unit) {
        val service = UltronAccessibilityService.getInstance()
        if (service == null) {
            onDone(false, "Accessibility service is not enabled. Turn on ITRA in Accessibility settings first.")
            return
        }
        service.screenAction(command.kind, command.arg, onDone)
    }

    // ---------------------------------------------------------------- universal research

    private fun research(
        context: Context,
        query: String,
        onStatus: (String) -> Unit,
        onDone: (Boolean, String) -> Unit
    ) {
        val service = UltronAccessibilityService.getInstance()
        if (service == null) {
            onDone(false, "Turn on ITRA in Accessibility settings so I can search and read the answer for you.")
            return
        }
        onStatus("SEARCHING")
        service.researchWeb(query, onStatus, onDone)
    }

    // ---------------------------------------------------------------- apps

    private fun openApp(context: Context, name: String, onDone: (Boolean, String) -> Unit) {
        val app = AppResolver.resolve(context, name)
        if (app == null) {
            onDone(false, "I couldn't find an app called $name on this phone.")
            return
        }
        val intent = context.packageManager.getLaunchIntentForPackage(app.pkg)
        if (intent == null) {
            onDone(false, "${app.label} can't be opened.")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            onDone(true, "Opening ${app.label}.")
        } catch (e: Exception) {
            LogStore.e(TAG, "Could not open ${app.pkg}", e)
            onDone(false, "I couldn't open ${app.label}.")
        }
    }

    // ---------------------------------------------------------------- media

    private fun playMedia(
        context: Context,
        query: String,
        appName: String?,
        onStatus: (String) -> Unit,
        onDone: (Boolean, String) -> Unit
    ) {
        val key = AppResolver.normalize(appName ?: "youtube")
        when (key) {
            "spotify" -> {
                val ok = view(context, Uri.parse("spotify:search:" + Uri.encode(query)), AppResolver.SPOTIFY)
                onDone(ok, if (ok) "Opened Spotify search for $query." else "I couldn't open Spotify.")
            }
            "youtube music", "yt music" -> {
                val uri = Uri.parse("https://music.youtube.com/search").buildUpon()
                    .appendQueryParameter("q", query).build()
                val ok = view(context, uri, AppResolver.YOUTUBE_MUSIC)
                onDone(ok, if (ok) "Opened YouTube Music search for $query." else "I couldn't open YouTube Music.")
            }
            else -> playOnYouTube(context, query, onStatus, onDone)
        }
    }

    private fun playOnYouTube(
        context: Context,
        query: String,
        onStatus: (String) -> Unit,
        onDone: (Boolean, String) -> Unit
    ) {
        val uri = Uri.parse("https://www.youtube.com/results").buildUpon()
            .appendQueryParameter("search_query", query).build()
        val pkg = if (AppResolver.isInstalled(context, AppResolver.YOUTUBE)) AppResolver.YOUTUBE else null
        if (!view(context, uri, pkg)) {
            onDone(false, "I couldn't open YouTube on this device.")
            return
        }
        val service = UltronAccessibilityService.getInstance()
        if (service == null || pkg == null) {
            onDone(
                true,
                "I opened YouTube results for $query. Turn on ITRA in Accessibility settings and I'll start the first video for you."
            )
            return
        }
        onStatus("Opening YouTube results")
        service.playFirstYouTubeResult(onStatus) { ok, text ->
            if (ok) onDone(true, "Playing $query on YouTube.")
            else onDone(true, "I opened YouTube results for $query, but couldn't start the video. $text")
        }
    }


    private fun openInstagramInbox(
        onStatus: (String) -> Unit,
        onDone: (Boolean, String) -> Unit
    ) {
        val service = UltronAccessibilityService.getInstance()
        if (service == null) {
            onDone(false, "Turn on ITRA in Accessibility settings to open Instagram chats.")
            return
        }
        service.openInstagramInbox(onStatus, onDone)
    }

    private fun playFirstResult(
        onStatus: (String) -> Unit,
        onDone: (Boolean, String) -> Unit
    ) {
        val service = UltronAccessibilityService.getInstance()
        if (service == null) {
            onDone(false, "Turn on ITRA in Accessibility settings, then say 'play the first video' again.")
            return
        }
        service.playFirstYouTubeResult(onStatus) { ok, text ->
            onDone(ok, if (ok) "Playing the first YouTube result." else text)
        }
    }

    // ---------------------------------------------------------------- search / navigate

    private fun search(context: Context, query: String, appName: String?, onDone: (Boolean, String) -> Unit) {
        if (query.isBlank()) {
            onDone(false, "What should I search for?")
            return
        }
        val q = Uri.encode(query)
        val key = appName?.let { AppResolver.normalize(it) }
        val (uri, pkg) = when (key) {
            "youtube" -> Uri.parse("https://www.youtube.com/results?search_query=$q") to AppResolver.YOUTUBE
            "youtube music" -> Uri.parse("https://music.youtube.com/search?q=$q") to AppResolver.YOUTUBE_MUSIC
            "maps", "google maps" -> Uri.parse("geo:0,0?q=$q") to AppResolver.MAPS
            "play store", "playstore" -> Uri.parse("market://search?q=$q") to "com.android.vending"
            "spotify" -> Uri.parse("spotify:search:$q") to AppResolver.SPOTIFY
            "amazon" -> Uri.parse("https://www.amazon.com/s?k=$q") to null
            "flipkart" -> Uri.parse("https://www.flipkart.com/search?q=$q") to null
            "wikipedia" -> Uri.parse("https://en.wikipedia.org/w/index.php?search=$q") to null
            else -> Uri.parse("https://www.google.com/search?q=$q") to null
        }
        val installedPkg = pkg?.takeIf { AppResolver.isInstalled(context, it) }
        val ok = view(context, uri, installedPkg)
        val where = if (key == null || key == "google" || key == "chrome") "Google" else appName
        onDone(ok, if (ok) "Searching $where for $query." else "I couldn't open that search.")
    }

    private fun openUrl(context: Context, raw: String, onDone: (Boolean, String) -> Unit) {
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            onDone(false, "Please provide a valid website address beginning with https:// or http://.")
            return
        }
        val ok = view(context, uri, null)
        onDone(ok, if (ok) "Opening ${uri.host}." else "I couldn't open that website.")
    }

    private fun navigate(context: Context, place: String, onDone: (Boolean, String) -> Unit) {
        val uri = Uri.parse("google.navigation:q=" + Uri.encode(place))
        val pkg = AppResolver.MAPS.takeIf { AppResolver.isInstalled(context, it) }
        var ok = view(context, uri, pkg)
        if (!ok) ok = view(context, Uri.parse("geo:0,0?q=" + Uri.encode(place)), null)
        onDone(ok, if (ok) "Navigating to $place." else "I couldn't open a maps app.")
    }

    // ---------------------------------------------------------------- helpers

    private fun view(context: Context, uri: Uri, pkg: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkg != null) intent.setPackage(pkg)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            if (pkg != null) {
                LogStore.w(TAG, "$pkg could not handle $uri, retrying without package")
                view(context, uri, null)
            } else {
                LogStore.e(TAG, "No app could open $uri", e)
                false
            }
        }
    }
}
