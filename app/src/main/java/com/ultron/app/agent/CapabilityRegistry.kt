package com.ultron.app.agent

import com.ultron.app.ModeManager
import com.ultron.app.tools.Command

/**
 * Runtime capability catalogue used by the 8.0 planner. Capabilities describe what
 * ITRA can attempt; Android permissions and ModeManager remain the final authority.
 */
object CapabilityRegistry {
    data class Capability(
        val id: String,
        val description: String,
        val safeByDefault: Boolean,
        val supportedModes: Set<ModeManager.Mode>
    )

    private val allModes = setOf(ModeManager.Mode.LIGHT, ModeManager.Mode.MEDIUM, ModeManager.Mode.EXTREME)
    private val automationModes = setOf(ModeManager.Mode.MEDIUM, ModeManager.Mode.EXTREME)

    private val capabilities = listOf(
        Capability("conversation", "Answer and reason about the user's request.", true, allModes),
        Capability("web_research", "Search/read public information and synthesize an answer.", true, allModes),
        Capability("screen_observation", "Observe the visible Android UI using accessibility/OCR.", true, automationModes),
        Capability("navigation", "Open apps, URLs, search, navigate and scroll.", true, automationModes),
        Capability("media", "Search and play media.", true, automationModes),
        Capability("screen_action", "Perform explicitly requested low-impact visible-screen actions.", true, automationModes),
        Capability("system_control", "Change supported ringer, volume and brightness settings.", true, automationModes),
        Capability("messaging", "Prepare/send an external message after the existing confirmation rules.", false, setOf(ModeManager.Mode.EXTREME)),
        Capability("persistent_memory", "Store/retrieve explicit user-approved long-term memory.", true, allModes),
        Capability("task_state", "Track goal, completed steps, failures and recovery state.", true, allModes)
    )

    fun all(): List<Capability> = capabilities

    fun forCommand(command: Command): List<Capability> = when (command) {
        is Command.Research -> listOf(require("web_research"), require("task_state"))
        is Command.OpenApp, is Command.OpenUrl, is Command.Search, is Command.Navigate, Command.OpenInstagramInbox -> listOf(require("navigation"), require("screen_observation"), require("task_state"))
        is Command.PlayMedia, Command.PlayFirstResult -> listOf(require("media"), require("screen_observation"), require("task_state"))
        is Command.ReadScreen -> listOf(require("screen_observation"))
        is Command.ScreenAction -> listOf(require("screen_action"), require("screen_observation"), require("task_state"))
        is Command.SendMessage -> listOf(require("messaging"), require("screen_observation"), require("task_state"))
        is Command.SetRingerMode, is Command.SetVolume, is Command.SetBrightness -> listOf(require("system_control"), require("task_state"))
        is Command.NeedMoreInfo -> listOf(require("conversation"))
    }

    fun describeForPrompt(): String = capabilities.joinToString("\n") {
        "- ${it.id}: ${it.description} safeByDefault=${it.safeByDefault}"
    }

    private fun require(id: String): Capability = capabilities.first { it.id == id }
}
