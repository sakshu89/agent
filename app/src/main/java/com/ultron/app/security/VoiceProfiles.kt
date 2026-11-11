package com.ultron.app.security

import android.content.Context

enum class VoiceRole { OWNER, TRUSTED }

data class VoiceProfile(
    val id: String,
    val displayName: String,
    val role: VoiceRole,
    val permissionLevel: Int
)

/**
 * Metadata-only profile registry. It deliberately does not claim biometric
 * enrollment or verification; a real local speaker model must be integrated.
 * Owner metadata cannot be replaced through trusted-user operations.
 */
class VoiceProfiles(context: Context) {
    private val prefs = context.getSharedPreferences("ultron_voice_profiles", Context.MODE_PRIVATE)

    fun trustedProfiles(): List<VoiceProfile> {
        val ids = prefs.getStringSet("trusted_ids", emptySet()).orEmpty()
        return ids.mapNotNull { id ->
            val name = prefs.getString("name_$id", null) ?: return@mapNotNull null
            VoiceProfile(id, name, VoiceRole.TRUSTED, prefs.getInt("level_$id", 1))
        }
    }

    fun addTrusted(id: String, name: String, permissionLevel: Int = 1): Boolean {
        if (id.isBlank() || name.isBlank()) return false
        val ids = prefs.getStringSet("trusted_ids", emptySet()).orEmpty().toMutableSet()
        ids.add(id)
        prefs.edit()
            .putStringSet("trusted_ids", ids)
            .putString("name_$id", name)
            .putInt("level_$id", permissionLevel.coerceIn(0, 2))
            .apply()
        return true
    }

    fun removeTrusted(id: String) {
        val ids = prefs.getStringSet("trusted_ids", emptySet()).orEmpty().toMutableSet()
        ids.remove(id)
        prefs.edit().putStringSet("trusted_ids", ids).remove("name_$id").remove("level_$id").apply()
    }
}
