package com.ultron.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.os.UserManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Private privileged capability gate. It never bypasses Android permissions.
 * The activation/deactivation phrases are intentionally not surfaced in normal UI.
 */
object GhostModeManager {
    private var context: Context? = null
    private var _active by mutableStateOf(false)
    val active: Boolean get() = _active

    private const val ACTIVATION = "arise"
    private const val DEACTIVATION = "shadow"

    fun init(context: Context) { this.context = context.applicationContext; _active = false }

    fun isPrivatePhrase(raw: String): Boolean = normalize(raw) == ACTIVATION || normalize(raw) == DEACTIVATION

    fun intercept(raw: String): String? {
        val normalized = normalize(raw)
        return when (normalized) {
            ACTIVATION -> { _active = true; LogStore.a("SYSTEM", "Privileged capability gate enabled."); "Privileged controls enabled." }
            DEACTIVATION -> { _active = false; LogStore.a("SYSTEM", "Privileged capability gate disabled."); "Privileged controls disabled." }
            else -> null
        }
    }

    private fun normalize(raw: String): String = raw.trim().lowercase().replace(Regex("[.!?,]+$"), "").trim()

    fun canUsePrivilegedControls(): Boolean = active

    fun lockDevice(): Result<Unit> = runCatching {
        val c = context ?: error("ITRA is not initialized")
        val dpm = c.getSystemService(DevicePolicyManager::class.java)
        val admin = findAdmin(c)
        if (admin == null || !dpm.isAdminActive(admin)) error("Device administration is not enabled for ITRA.")
        dpm.lockNow()
        Unit
    }

    fun rebootDevice(): Result<Unit> = runCatching {
        val c = context ?: error("ITRA is not initialized")
        val dpm = c.getSystemService(DevicePolicyManager::class.java)
        val admin = findAdmin(c)
        if (admin == null || !dpm.isDeviceOwnerApp(c.packageName)) error("Reboot requires ITRA to be the device owner.")
        dpm.reboot(admin)
        Unit
    }

    fun isDeviceOwner(): Boolean = runCatching {
        val c = context ?: return false
        c.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(c.packageName)
    }.getOrDefault(false)

    fun capabilitySummary(): List<Pair<String, String>> = listOf(
        "Privileged gate" to if (active) "ACTIVE" else "INACTIVE",
        "Device owner" to if (isDeviceOwner()) "AVAILABLE" else "NOT CONFIGURED",
        "Lock device" to "DEVICE ADMIN / DEVICE OWNER",
        "Reboot device" to "DEVICE OWNER ONLY",
        "Shutdown" to "ANDROID POWER MENU / OEM CONTROL"
    )

    private fun findAdmin(c: Context): ComponentName = ComponentName(c, DeviceAdminReceiver::class.java)
}
