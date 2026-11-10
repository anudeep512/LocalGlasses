package com.localglasses.app.agent.approval

import com.localglasses.app.agent.commands.RegistryActionRequest
import com.localglasses.app.agent.commands.RoutingDecision
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanReplannerTest {

    @Test
    fun replacesPlanWithReplannedAction() = runTest {
        val replanner = PlanReplanner { prompt ->
            assertTrue(prompt.contains("Current plan"))
            RoutingDecision.Action(
                RegistryActionRequest(
                    "send_sms",
                    mapOf("contact" to "Amma", "message" to "running late"),
                ),
            )
        }
        val old = Plan(command = "message Amma", steps = listOf(PlanStep.Wait(1)))

        val updated = replanner.replan(old, "say running late instead")

        assertEquals("send_sms", (updated!!.steps.single() as PlanStep.RunAction).actionId)
        assertTrue(updated.steps.single().toString().contains("running late"))
    }

    @Test
    fun keepsNothingWhenReplanDoesNotResolve() = runTest {
        val replanner = PlanReplanner { RoutingDecision.NoMatch("no") }
        val old = Plan(command = "x", steps = listOf(PlanStep.Wait(1)))

        assertEquals(null, replanner.replan(old, "fix it"))
    }
}
