package com.localglasses.app.agent.approval

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlanApprovalSheetTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun deletingAStepCallsUpdate() {
        val plan = Plan(
            command = "test",
            steps = listOf(PlanStep.Wait(1), PlanStep.Wait(2)),
        )
        var updated: Plan? = null

        rule.setContent {
            PlanApprovalSheet(
                plan = plan,
                onApprove = {},
                onCancel = {},
                onUpdate = { transform -> updated = transform(plan) },
                onReplan = {},
            )
        }

        rule.onAllNodesWithText("Delete")[0].performClick()

        assertEquals(1, updated?.steps?.size)
        assertEquals(false, updated?.approved)
    }
}
