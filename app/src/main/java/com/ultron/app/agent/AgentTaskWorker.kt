package com.ultron.app.agent

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ultron.app.LlmClient
import com.ultron.app.ModeManager
import com.ultron.app.ToolRegistry
import com.ultron.app.tools.CommandExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Resumable local task runner. It never auto-sends messages or performs consequential actions. */
class AgentTaskWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        ModeManager.init(applicationContext)
        val queue = AgentTaskQueue.get(applicationContext)
        val task = queue.get(id) ?: return Result.failure()
        if (task.status == "CANCELLED" || task.status == "COMPLETED") return Result.success()
        if (task.status == "AWAITING_APPROVAL") return Result.success()
        queue.update(id, "RUNNING", task.currentStep)
        var index = task.currentStep
        while (index < task.steps.size) {
            if (isStopped) { queue.update(id, "QUEUED", index); return Result.retry() }
            val step = task.steps[index]
            if ((step.requiresConfirmation || step.toolName == ToolRegistry.SEND_MESSAGE_TOOL) && task.approvedStep != index) {
                queue.update(id, "AWAITING_APPROVAL", index, "This step requires explicit user confirmation.")
                return Result.success()
            }
            val call = LlmClient.ToolCall("queued-$index", step.toolName, step.appName, step.contactName, step.message, step.query, step.action, step.target)
            val command = ToolRegistry.toCommand(call) ?: run {
                queue.update(id, "FAILED", index, "Unsupported or invalid tool: ${step.toolName}")
                return Result.failure()
            }
            if (!ModeManager.allowsCommand(command)) {
                queue.update(id, "FAILED", index, "The current operating mode does not permit this action.")
                return Result.failure()
            }
            var outcome: Pair<Boolean, String>? = null
            // Never automatically retry a consequential external action: a timeout can
            // happen after the remote side already accepted the action, which could
            // otherwise create duplicate messages. Safe actions may use two bounded attempts.
            val maxAttempts = if (command is com.ultron.app.tools.Command.SendMessage) 1 else 2
            repeat(maxAttempts) { attempt ->
                if (outcome?.first == true) return@repeat
                outcome = withTimeoutOrNull(60_000L) {
                    suspendCancellableCoroutine<Pair<Boolean,String>> { continuation ->
                        CommandExecutor.run(applicationContext, command, { status ->
                            queue.update(id, "RUNNING", index, "Step ${index + 1}/${task.steps.size}: $status")
                        }, { ok, message -> if (continuation.isActive) continuation.resume(ok to message) })
                    }
                } ?: (false to "Step timed out after 60 seconds.")
                if (outcome?.first != true && attempt + 1 < maxAttempts) kotlinx.coroutines.delay(750L * (attempt + 1))
            }
            if (outcome?.first != true) {
                queue.update(id, "FAILED", index, outcome?.second?.take(500) ?: "Step failed.")
                return Result.failure()
            }
            index++
            queue.update(id, "RUNNING", index, null)
        }
        queue.update(id, "COMPLETED", index)
        return Result.success()
    }
    companion object { const val KEY_TASK_ID = "task_id" }
}
