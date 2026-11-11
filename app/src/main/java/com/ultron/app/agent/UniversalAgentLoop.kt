package com.ultron.app.agent

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.ultron.app.LogStore
import kotlinx.coroutines.delay

/**
 * Bounded Observe -> Act -> Verify loop for universal UI actions.
 * The loop never blindly repeats an action: every recovery attempt must first
 * observe a new screen state and may use a different candidate.
 */
class UniversalAgentLoop(
    private val rootProvider: () -> AccessibilityNodeInfo?,
    private val nodesProvider: (AccessibilityNodeInfo) -> List<AccessibilityNodeInfo>,
    private val action: suspend (AccessibilityNodeInfo) -> Boolean,
    private val fingerprint: () -> String,
    private val tag: String = "AGENT_LOOP"
) {
    data class Result(
        val success: Boolean,
        val attempts: Int,
        val recovered: Boolean,
        val reason: String
    )

    suspend fun findAndAct(
        matcher: (AccessibilityNodeInfo) -> Boolean,
        maxRecoveryAttempts: Int = 2,
        settleMs: Long = 180L,
        verifyMs: Long = 900L
    ): Result {
        var attempts = 0
        var recovered = false
        val seen = HashSet<String>()

        repeat(maxRecoveryAttempts + 1) { pass ->
            val root = rootProvider() ?: return Result(false, attempts, recovered, "No active accessibility window")
            val candidates = nodesProvider(root).filter { it.isVisibleToUser && matcher(it) }
            // Never fall back to a candidate we already attempted. Repeating the same
            // node after an unverified action is the exact failure mode that caused
            // runaway duplicate clicks in earlier versions.
            val candidate = candidates.firstOrNull { candidateKey(it) !in seen }

            if (candidate == null) {
                if (pass == maxRecoveryAttempts) return Result(false, attempts, recovered, "Target not visible")
                recovered = true
                delay(settleMs)
                return@repeat
            }

            val key = candidateKey(candidate)
            seen += key
            val before = fingerprint()
            attempts++
            val acted = action(candidate)
            if (!acted) {
                LogStore.w(tag, "Action dispatch failed; re-observing instead of retrying immediately.")
                recovered = true
                delay(settleMs)
                return@repeat
            }

            delay(settleMs)
            val deadline = SystemClock.uptimeMillis() + verifyMs
            while (SystemClock.uptimeMillis() < deadline) {
                val after = fingerprint()
                if (after != before) return Result(true, attempts, recovered, "UI changed after action")
                delay(100L)
            }

            // No meaningful state change. Never hammer the same node. Re-observe and
            // allow at most one alternate candidate/recovery path.
            recovered = true
            LogStore.w(tag, "No state change after action; attempting bounded recovery pass $pass.")
        }

        return Result(false, attempts, recovered, "Action could not be verified")
    }

    private fun candidateKey(node: AccessibilityNodeInfo): String {
        val b = android.graphics.Rect()
        node.getBoundsInScreen(b)
        return listOf(
            node.packageName?.toString().orEmpty(),
            node.viewIdResourceName.orEmpty(),
            node.text?.toString()?.trim()?.lowercase().orEmpty(),
            node.contentDescription?.toString()?.trim()?.lowercase().orEmpty(),
            b.left, b.top, b.right, b.bottom
        ).joinToString("|")
    }
}
