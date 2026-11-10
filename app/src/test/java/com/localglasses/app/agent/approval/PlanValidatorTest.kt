package com.localglasses.app.agent.approval

import org.junit.Assert.assertTrue
import org.junit.Test

class PlanValidatorTest {

    @Test
    fun rejectsEmptyPlan() {
        assertTrue(PlanValidator.validate(Plan(command = "x", steps = emptyList())).isFailure)
    }

    @Test
    fun rejectsLongWait() {
        assertTrue(
            PlanValidator.validate(
                Plan(command = "x", steps = listOf(PlanStep.Wait(60_000))),
            ).isFailure,
        )
    }

    @Test
    fun acceptsValidPlan() {
        assertTrue(
            PlanValidator.validate(
                Plan(
                    command = "x",
                    steps = listOf(
                        PlanStep.OpenApp("com.whatsapp"),
                        PlanStep.Tap(NodeSelector(contentDescription = "Send")),
                    ),
                    targetPackage = "com.whatsapp",
                ),
            ).isSuccess,
        )
    }
}
