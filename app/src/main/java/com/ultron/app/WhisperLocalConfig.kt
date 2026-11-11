package com.ultron.app

import android.content.Context
import android.net.Uri

object WhisperLocalConfig {
    private const val PREFS = "ultron_whisper"
    private const val URL = "endpoint"
    private const val ENABLED = "enabled"
    const val DEFAULT_ENDPOINT = "http://127.0.0.1:8766/v1/audio/transcriptions"

    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, false)
    fun setEnabled(context: Context, value: Boolean) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, value).apply()
    fun endpoint(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(URL, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
    fun setEndpoint(context: Context, raw: String): Result<String> = runCatching {
        val uri = Uri.parse(raw.trim())
        require(uri.scheme == "http" || uri.scheme == "https") { "Whisper endpoint must be HTTP(S)." }
        require(uri.host != null && isLocalHost(uri.host!!)) { "Whisper endpoint must be localhost or a private-network host." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(URL, raw.trim()).apply()
        raw.trim()
    }

    // Deliberately duplicated as a public validation helper rather than exposing LocalServerConfig internals.
    private fun isLocalHost(host: String): Boolean = host.equals("localhost", true) || host == "127.0.0.1" || host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.16.") || host.startsWith("172.17.") || host.startsWith("172.18.") || host.startsWith("172.19.") || host.startsWith("172.2") || host.startsWith("172.30.") || host.startsWith("172.31.")
}
