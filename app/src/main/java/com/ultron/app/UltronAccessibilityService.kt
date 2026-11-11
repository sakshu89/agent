package com.ultron.app

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class UltronAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "ITRA_ACCESSIBILITY"

        @Volatile
        private var instance: UltronAccessibilityService? = null

        fun getInstance(): UltronAccessibilityService? = instance
    }

    private lateinit var automationManager: AutomationManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogStore.init(applicationContext)
        automationManager = AutomationManager(applicationContext, this)
        LogStore.i("ACCESS", "Accessibility service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // The automation manager reads the active window only when a user-requested
        // action needs it. Do not continuously retain or inspect screen contents.
    }

    override fun onInterrupt() {
        LogStore.w("ACCESS", "Accessibility service interrupted.")
    }

    fun sendAppMessage(
        appPackage: String,
        contactName: String,
        message: String,
        onStatus: (String) -> Unit = {},
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (!::automationManager.isInitialized) {
            onStatus("Accessibility service is not ready.")
            onDone(false, "Accessibility service is not ready.")
            return
        }
        automationManager.sendAppMessage(appPackage, contactName, message, onStatus, onDone)
    }

    fun openInstagramInbox(
        onStatus: (String) -> Unit = {},
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (!::automationManager.isInitialized) {
            onDone(false, "Accessibility service is not ready.")
            return
        }
        automationManager.openInstagramInbox(onStatus, onDone)
    }

    fun playFirstYouTubeResult(
        onStatus: (String) -> Unit = {},
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (!::automationManager.isInitialized) {
            onDone(false, "Accessibility service is not ready.")
            return
        }
        automationManager.playFirstYouTubeResult(onStatus, onDone)
    }

    fun researchWeb(
        query: String,
        onStatus: (String) -> Unit = {},
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (!::automationManager.isInitialized) {
            onDone(false, "Accessibility service is not ready.")
            return
        }
        automationManager.researchWeb(query, onStatus, onDone)
    }

    fun emergencyStop(): Boolean = if (::automationManager.isInitialized) automationManager.cancelCurrentTask() else false

    fun screenAction(kind: String, arg: String?, onDone: (Boolean, String) -> Unit) {
        if (!::automationManager.isInitialized) {
            onDone(false, "Accessibility service is not ready.")
            return
        }
        automationManager.screenAction(kind, arg, onDone)
    }

    fun getCurrentRoot(): AccessibilityNodeInfo? = rootInActiveWindow

    /** Call only in response to an explicit user request; screen text is not retained. */
    fun readVisibleScreen(onResult: (Result<String>) -> Unit) {
        ScreenUnderstanding.captureAndRead(this, onResult)
    }

    override fun onDestroy() {
        LogStore.w("ACCESS", "Accessibility service stopped.")
        if (::automationManager.isInitialized) {
            automationManager.destroy()
        }
        instance = null
        super.onDestroy()
    }
}
