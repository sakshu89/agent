package com.ultron.app

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/** Optional local Hermes companion. Disabled by default; only loopback/private-network URLs are accepted. */
object HermesBridgeConfig {
    private const val PREFS = "itra_hermes_bridge"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_URL = "base_url"
    private const val KEY_TOKEN = "bearer_token_enc"
    private const val KEY_ALIAS = "itra_hermes_bridge_token_v1"
    const val DEFAULT_URL = "http://127.0.0.1:8766"

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply() }
    fun getBaseUrl(context: Context) = prefs(context).getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
    fun getToken(context: Context): String = runCatching { decrypt(prefs(context).getString(KEY_TOKEN, "").orEmpty()) }.getOrDefault("")

    fun save(context: Context, rawUrl: String, token: String): Result<Unit> = runCatching {
        val normalized = rawUrl.trim().trimEnd('/')
        val uri = Uri.parse(normalized)
        require(uri.scheme == "http" || uri.scheme == "https") { "Hermes bridge URL must use HTTP(S)." }
        val host = uri.host ?: error("Invalid Hermes bridge host.")
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) { "Do not put credentials or query parameters in the URL." }
        require(isPrivateHost(host)) { "Hermes bridge must use localhost or a private-network IP." }
        require(token.length in 16..200) { "Enter the bridge bearer token (at least 16 characters)." }
        prefs(context).edit().putString(KEY_URL, normalized).putString(KEY_TOKEN, encrypt(token)).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        val packet = Base64.decode(value, Base64.NO_WRAP)
        require(packet.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packet.copyOfRange(0, 12)))
        return String(cipher.doFinal(packet.copyOfRange(12, packet.size)), Charsets.UTF_8)
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host.equals("localhost", true)) return true
        val literal = host.removePrefix("[").removeSuffix("]")
        if (!literal.matches(Regex("\\d{1,3}(?:\\.\\d{1,3}){3}")) && !literal.contains(':')) return false
        val address = runCatching { InetAddress.getByName(literal) }.getOrNull() ?: return false
        if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) return true
        return when (address) {
            is Inet4Address -> {
                val b = address.address.map { it.toInt() and 0xff }
                b[0] == 10 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168)
            }
            is Inet6Address -> (address.address[0].toInt() and 0xfe) == 0xfc
            else -> false
        }
    }
}
