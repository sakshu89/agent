package com.ultron.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ultron.core.ai.ModelMode
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cloud credentials are encrypted with an Android Keystore AES-GCM key. */
object CloudModelConfig {
    private const val PREFS = "ultron_hybrid_ai"
    private const val KEY_ALIAS = "ultron_cloud_api_key_v1"
    private const val KEY_MODE = "mode"
    private const val KEY_ENDPOINT = "cloud_endpoint"
    private const val KEY_MODEL = "cloud_model"
    private const val KEY_SECRET = "cloud_secret"

    fun mode(context: Context): ModelMode = ModelMode.from(prefs(context).getString(KEY_MODE, "LOCAL"))
    fun endpoint(context: Context) = prefs(context).getString(KEY_ENDPOINT, "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
    fun model(context: Context) = prefs(context).getString(KEY_MODEL, "") ?: ""
    fun hasKey(context: Context) = prefs(context).getString(KEY_SECRET, null)?.isNotBlank() == true

    fun save(context: Context, mode: ModelMode, endpoint: String, model: String, apiKey: String): Result<Unit> = runCatching {
        val normalized = endpoint.trim().trimEnd('/')
        val cloudReadyRequested = mode == ModelMode.CLOUD || apiKey.isNotBlank() || hasKey(context)
        if (cloudReadyRequested) {
            require(normalized.startsWith("https://", true)) { "Cloud endpoint must use HTTPS." }
            require(android.net.Uri.parse(normalized).host != null) { "Cloud endpoint is invalid." }
            require(model.isNotBlank() && model.length <= 160) { "Enter a valid cloud model name." }
        }
        if (mode == ModelMode.CLOUD) require(apiKey.isNotBlank() || hasKey(context)) { "Enter an API key before enabling cloud mode." }
        val edit = prefs(context).edit().putString(KEY_MODE, mode.name).putString(KEY_ENDPOINT, normalized).putString(KEY_MODEL, model.trim())
        if (apiKey.isNotBlank()) edit.putString(KEY_SECRET, encrypt(apiKey.trim()))
        edit.apply()
    }

    fun apiKey(context: Context): String = runCatching {
        val stored = prefs(context).getString(KEY_SECRET, null) ?: return ""
        decrypt(stored)
    }.getOrDefault("")

    fun clear(context: Context) { prefs(context).edit().remove(KEY_SECRET).apply() }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv; val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String {
        val packet = Base64.decode(value, Base64.NO_WRAP); require(packet.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packet.copyOfRange(0, 12)))
        return String(cipher.doFinal(packet.copyOfRange(12, packet.size)), Charsets.UTF_8)
    }
}
