package com.ultron.app

import android.content.Context
import android.net.Uri
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/** Local inference only. Public Internet endpoints are rejected by design. */
object LocalServerConfig {
    private const val PREFS = "ultron_local_server"
    private const val KEY_URL = "base_url"
    private const val KEY_MODEL = "model_name"
    private const val KEY_PORT = "api_port"
    private const val KEY_LOCALHOST = "api_localhost_only"
    private const val KEY_TOKEN = "api_token"
    const val DEFAULT_URL = "http://127.0.0.1:8080"
    const val DEFAULT_MODEL = "local-model"

    fun getBaseUrl(context: Context): String = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL

    fun getModel(context: Context): String = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL


    fun getApiPort(context: Context): Int = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_PORT, 8765)
    fun setApiPort(context: Context, port: Int): Result<Int> = runCatching { require(port in 1024..65535) { "Port must be between 1024 and 65535." }; context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_PORT, port).apply(); port }
    fun isLocalhostOnly(context: Context): Boolean = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LOCALHOST, true)
    fun setLocalhostOnly(context: Context, value: Boolean) { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_LOCALHOST, value).apply() }
    fun getApiToken(context: Context): String = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, "").orEmpty()
    fun setApiToken(context: Context, token: String) { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, token.take(200)).apply() }

    fun setModel(context: Context, raw: String): Result<String> = runCatching {
        val model = raw.trim()
        require(model.isNotBlank() && model.length <= 120) { "Enter a model name (1–120 characters)." }
        require(model.none { it.isWhitespace() || it.isISOControl() }) { "Model names cannot contain spaces or control characters." }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODEL, model).apply()
        model
    }

    fun setBaseUrl(context: Context, raw: String): Result<String> = runCatching {
        val normalized = raw.trim().trimEnd('/')
        require(normalized.isNotBlank()) { "Enter a local server address." }
        val uri = Uri.parse(normalized)
        require(uri.scheme == "http" || uri.scheme == "https") { "Only HTTP(S) local endpoints are supported." }
        val host = uri.host ?: error("The server address is invalid.")
        require(uri.port == -1 || uri.port in 1..65535) { "The server port is invalid." }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) { "Credentials and query strings are not allowed in the server URL." }
        require(isLocalHost(host)) { "ITRA accepts only localhost or private-network server addresses. Public API endpoints are disabled." }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_URL, normalized).apply()
        normalized
    }

    private fun isLocalHost(host: String): Boolean {
        if (host.equals("localhost", true)) return true
        val literal = host.removePrefix("[").removeSuffix("]")
        val isIpv4Literal = literal.matches(Regex("\\d{1,3}(?:\\.\\d{1,3}){3}"))
        if (!isIpv4Literal && !literal.contains(':')) return false // avoid DNS rebinding / public hostnames
        val addresses = runCatching { listOf(InetAddress.getByName(literal)) }.getOrNull().orEmpty()
        return addresses.isNotEmpty() && addresses.all { address ->
            if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) true
            else if (address is Inet4Address) {
                val b = address.address.map { it.toInt() and 0xff }
                b[0] == 10 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168)
            } else if (address is Inet6Address) {
                val b = address.address.map { it.toInt() and 0xff }
                (b[0] and 0xfe) == 0xfc
            } else false
        }
    }
}
