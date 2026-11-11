package com.ultron.app.agent

import com.ultron.app.tools.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalAgentPlannerTest {
    @Test fun researchPlanIsGenericAndBounded() {
        val plan = UniversalAgentPlanner.plan("tell me how quantum tunneling works", Command.Research("quantum tunneling"))
        assertEquals(3, plan.steps.size)
        assertTrue(plan.steps.all { it.risk == UniversalAgentPlanner.Risk.SAFE })
        assertTrue(plan.steps.size <= 12)
    }

    @Test fun messagingPlanRequiresConfirmation() {
        val plan = UniversalAgentPlanner.plan("send it", Command.SendMessage("whatsapp", "Alex", "hello"))
        assertTrue(plan.requiresConfirmation)
    }

    @Test fun genericGoalHasNoTopicSpecificRule() {
        val plan = UniversalAgentPlanner.plan("research something unusual")
        assertFalse(plan.steps.any { it.description.contains("diode", ignoreCase = true) })
    }
}
