package com.localglasses.app.agent.automation

import com.localglasses.app.agent.approval.Plan
import com.localglasses.app.agent.approval.PlanStep
import com.localglasses.app.agent.safety.SafetyPolicy
import kotlinx.coroutines.delay

class PlanExecutor(
    private val gateway: AccessibilityGateway,
    private val safety: SafetyPolicy,
    private val stopRequested: () -> Boolean = { false },
    private val postConditionTimeoutMs: Long = 5_000,
) {

    suspend fun execute(
        plan: Plan,
        onStep: (Int, PlanStep) -> Unit = { _, _ -> },
    ): Result<Unit> {
        val tracker = ProgressTracker()
        for ((index, step) in plan.steps.withIndex()) {
            if (stopRequested()) return Result.failure(IllegalStateException("Stopped by user"))
            val observation = gateway.observe()
            safety.checkTarget(
                packageName = observation.packageName,
                targetText = null,
                isPassword = false,
                screenText = observation.readableText(),
            ).takeIf { it.blocked }?.let {
                return Result.failure(IllegalStateException(it.reason ?: "Blocked"))
            }

            val before = gateway.observe()
            val success = when (step) {
                is PlanStep.OpenApp -> gateway.launch(step.packageName)
                is PlanStep.Tap -> gateway.tap(step.target)
                is PlanStep.TypeText -> gateway.type(step.target, step.text)
                is PlanStep.Scroll -> gateway.scroll(step.direction)
                is PlanStep.Wait -> {
                    delay(step.millis)
                    true
                }
                is PlanStep.RunAction -> false
            }
            onStep(index, step)
            val post = if (step is PlanStep.Wait) {
                gateway.observe()
            } else {
                waitForChange(before.signature)
            }
            if (post == null) {
                tracker.record(before.signature, success = false)
                return Result.failure(IllegalStateException("Step ${index + 1} timed out"))
            }
            val state = tracker.record(post.signature, success)
            if (state.stopped) {
                return Result.failure(IllegalStateException(state.reason ?: "No progress"))
            }
            if (!success) {
                return Result.failure(IllegalStateException("Step ${index + 1} failed"))
            }
        }
        return Result.success(Unit)
    }

    private suspend fun waitForChange(previousSignature: String): ScreenObservation? {
        val deadline = System.currentTimeMillis() + postConditionTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            val observation = gateway.observe()
            if (observation.signature != previousSignature) return observation
            delay(50)
        }
        return null
    }
}
