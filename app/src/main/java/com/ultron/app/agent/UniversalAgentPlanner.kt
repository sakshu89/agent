package com.ultron.app.agent

import com.ultron.app.ModeManager
import com.ultron.app.tools.Command
import com.ultron.app.tools.CommandParser

/**
 * Small deterministic planner sitting above the existing tool/automation layer.
 * It deliberately does not contain topic- or app-specific workflows.
 */
object UniversalAgentPlanner {
    enum class Risk { SAFE, CONFIRMATION_REQUIRED, BLOCKED }

    data class Step(
        val id: String,
        val capability: String,
        val description: String,
        val risk: Risk
    )

    data class Plan(
        val goal: String,
        val steps: List<Step>,
        val maxRecoveryAttempts: Int = 2
    ) {
        val requiresConfirmation: Boolean get() = steps.any { it.risk == Risk.CONFIRMATION_REQUIRED }
    }

    fun plan(goal: String, command: Command? = null): Plan {
        val trimmed = goal.trim().ifBlank { "user request" }
        val steps = when (command) {
            is Command.Research -> listOf(
                Step("research-search", "web_research", "Search public sources for the requested information.", Risk.SAFE),
                Step("research-read", "web_research", "Read relevant result text with bounded observation.", Risk.SAFE),
                Step("research-synthesize", "conversation", "Synthesize the relevant evidence into a concise answer.", Risk.SAFE)
            )
            is Command.SendMessage -> listOf(
                Step("message-prepare", "messaging", "Prepare the requested message.", Risk.CONFIRMATION_REQUIRED),
                Step("message-send", "messaging", "Send the message only after existing confirmation rules allow it.", Risk.CONFIRMATION_REQUIRED)
            )
            is Command.OpenApp, is Command.OpenUrl, is Command.Search, is Command.Navigate, Command.OpenInstagramInbox,
            is Command.PlayMedia, Command.PlayFirstResult, Command.ReadScreen, is Command.ScreenAction -> listOf(
                Step("observe", "screen_observation", "Observe the current UI before acting.", Risk.SAFE),
                Step("act", CapabilityRegistry.forCommand(command).first().id, "Perform one bounded action.", Risk.SAFE),
                Step("verify", "task_state", "Verify the UI/result changed as expected; recover instead of blindly repeating.", Risk.SAFE)
            )
            is Command.SetRingerMode, is Command.SetVolume, is Command.SetBrightness -> listOf(
                Step("system-change", "system_control", "Apply the requested Android setting through the existing permission boundary.", Risk.SAFE),
                Step("verify", "task_state", "Verify the setting operation returned success.", Risk.SAFE)
            )
            is Command.NeedMoreInfo, null -> listOf(
                Step("understand", "conversation", "Resolve missing or ambiguous information.", Risk.SAFE)
            )
        }
        return Plan(trimmed, steps.take(12), maxRecoveryAttempts = 2)
    }

    fun isAllowedByMode(command: Command): Boolean = ModeManager.allowsCommand(command)

    fun promptContext(goal: String, command: Command? = null): String {
        val resolvedCommand = command ?: CommandParser.parse(goal)
        val plan = plan(goal, resolvedCommand)
        return buildString {
            appendLine("AGENT 8.0 PLAN")
            appendLine("Goal: ${plan.goal}")
            plan.steps.forEachIndexed { index, step ->
                appendLine("${index + 1}. ${step.description} [${step.capability}, risk=${step.risk}]")
            }
            appendLine("Recovery attempts: ${plan.maxRecoveryAttempts}")
            appendLine("Available capabilities:")
            append(CapabilityRegistry.describeForPrompt())
        }
    }
}
