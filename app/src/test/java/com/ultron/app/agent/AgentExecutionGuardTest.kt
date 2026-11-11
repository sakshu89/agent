package com.ultron.app.agent

import com.ultron.app.tools.Command
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentExecutionGuardTest {
    @Test fun blocksImmediateDuplicateDispatch() {
        AgentExecutionGuard.clear()
        val command = Command.ScreenAction("tap", "Search")
        assertTrue(AgentExecutionGuard.allow(command, 1_000L))
        assertFalse(AgentExecutionGuard.allow(command, 1_500L))
        assertTrue(AgentExecutionGuard.allow(command, 2_500L))
    }

    @Test fun clockMovingBackwardsDoesNotCreateAnAccidentalDuplicateBlock() {
        AgentExecutionGuard.clear()
        val command = Command.ScreenAction("tap", "Search")
        assertTrue(AgentExecutionGuard.allow(command, 5_000L))
        assertTrue(AgentExecutionGuard.allow(command, 4_000L))
    }
}
