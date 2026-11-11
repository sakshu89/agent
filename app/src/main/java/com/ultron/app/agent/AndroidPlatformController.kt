package com.ultron.app.agent

import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.ultron.core.ActionResult
import com.ultron.core.Capability
import com.ultron.core.CapabilityStatus
import com.ultron.core.Observation
import com.ultron.core.PlatformAction
import com.ultron.core.PlatformController

/**
 * Android adapter for the shared ITRA core. Existing Accessibility/OCR implementation
 * remains the OS authority and is supplied through the bridge; this class never fabricates
 * successful UI actions when no Android bridge is connected.
 */
class AndroidPlatformController(
    context: Context,
    private val bridge: AndroidActionBridge
) : PlatformController {
    private val appContext = context.applicationContext
    private val accessibility = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    override fun platformId(): String = "android"

    override fun capabilities(): List<Capability> {
        val enabled = accessibility.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { it.resolveInfo.serviceInfo.packageName == appContext.packageName }
        val screenStatus = when {
            !enabled -> CapabilityStatus.PERMISSION_REQUIRED
            bridge.isScreenControlAvailable() -> CapabilityStatus.AVAILABLE
            else -> CapabilityStatus.TEMPORARILY_UNAVAILABLE
        }
        return listOf(
            Capability("android.screen", "Observe and interact with the Android UI through existing Accessibility/OCR services.", screenStatus),
            Capability("android.apps", "Launch installed Android applications through the existing app resolver.", if (bridge.isAppLaunchAvailable()) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE),
            Capability("android.voice", "Use existing ITRA voice services.", if (bridge.isVoiceAvailable()) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE)
        )
    }

    override fun observe(): Observation = bridge.observe()
    override fun execute(action: PlatformAction): ActionResult {
        val capability = capabilities().firstOrNull { it.id() == action.capabilityId() }
            ?: return ActionResult(false, "Android capability is not registered.", "")
        if (capability.status() != CapabilityStatus.AVAILABLE)
            return ActionResult(false, "Android capability unavailable: ${capability.status()}", "")
        return bridge.execute(action)
    }
}

interface AndroidActionBridge {
    fun observe(): Observation
    fun execute(action: PlatformAction): ActionResult
    fun isScreenControlAvailable(): Boolean
    fun isAppLaunchAvailable(): Boolean
    fun isVoiceAvailable(): Boolean
}
