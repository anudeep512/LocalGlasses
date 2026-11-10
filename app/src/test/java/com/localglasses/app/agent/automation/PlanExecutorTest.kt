package com.localglasses.app.agent.automation

import com.localglasses.app.agent.approval.Direction
import com.localglasses.app.agent.approval.NodeSelector
import com.localglasses.app.agent.approval.Plan
import com.localglasses.app.agent.approval.PlanStep
import com.localglasses.app.agent.safety.SafetyPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanExecutorTest {

    @Test
    fun executesTapStep() = runTest {
        val gateway = FakeGateway(tapResult = true)
        val executor = PlanExecutor(gateway, SafetyPolicy())

        val result = executor.execute(
            Plan(command = "tap", steps = listOf(PlanStep.Tap(NodeSelector(text = "Send")))),
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun failedStepStops() = runTest {
        val gateway = FakeGateway(tapResult = false)
        val executor = PlanExecutor(gateway, SafetyPolicy())

        val result = executor.execute(
            Plan(command = "tap", steps = listOf(PlanStep.Tap(NodeSelector(text = "Send")))),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun blockedScreenStopsBeforeStep() = runTest {
        val gateway = FakeGateway(tapResult = true, screenText = "Enter CVV")
        val executor = PlanExecutor(gateway, SafetyPolicy())

        val result = executor.execute(
            Plan(command = "tap", steps = listOf(PlanStep.Tap(NodeSelector(text = "Send")))),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun userStopStops() = runTest {
        val gateway = FakeGateway(tapResult = true)
        val executor = PlanExecutor(gateway, SafetyPolicy(), stopRequested = { true })

        val result = executor.execute(
            Plan(command = "tap", steps = listOf(PlanStep.Tap(NodeSelector(text = "Send")))),
        )

        assertTrue(result.isFailure)
    }

    private class FakeGateway(
        private val tapResult: Boolean,
        private val screenText: String = "Chat",
    ) : AccessibilityGateway {
        override fun observe(): ScreenObservation = ScreenObservation(
            packageName = "com.whatsapp",
            nodes = listOf(
                ObservedNode(
                    index = 0,
                    viewId = null,
                    text = screenText,
                    contentDescription = null,
                    className = "TextView",
                    bounds = null,
                    clickable = false,
                    editable = false,
                    scrollable = false,
                    password = false,
                ),
            ),
        )

        override fun tap(target: NodeSelector): Boolean = tapResult
        override fun type(target: NodeSelector, text: String): Boolean = tapResult
        override fun scroll(direction: Direction): Boolean = tapResult
        override fun launch(packageName: String): Boolean = tapResult
    }
}
