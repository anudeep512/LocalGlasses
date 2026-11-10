package com.localglasses.app.agent.approval

import java.util.UUID

data class NodeSelector(
    val viewId: String? = null,
    val text: String? = null,
    val textContains: String? = null,
    val contentDescription: String? = null,
    val contentDescriptionContains: String? = null,
    val index: Int? = null,
)

enum class Direction { FORWARD, BACKWARD }

sealed interface PlanStep {
    data class RunAction(
        val actionId: String,
        val arguments: Map<String, Any?>,
    ) : PlanStep

    data class OpenApp(val packageName: String) : PlanStep

    data class Tap(val target: NodeSelector) : PlanStep

    data class TypeText(val target: NodeSelector, val text: String) : PlanStep

    data class Scroll(val direction: Direction) : PlanStep

    data class Wait(val millis: Long) : PlanStep
}

data class Plan(
    val id: String = UUID.randomUUID().toString(),
    val command: String,
    val steps: List<PlanStep>,
    val targetPackage: String? = null,
    val approved: Boolean = false,
)

object PlanValidator {

    fun validate(plan: Plan): Result<Plan> = runCatching {
        require(plan.command.isNotBlank()) { "plan has no command" }
        require(plan.steps.isNotEmpty()) { "plan has no steps" }
        plan.steps.forEach { step ->
            when (step) {
                is PlanStep.RunAction -> {
                    require(step.actionId.isNotBlank()) { "action id is blank" }
                }
                is PlanStep.OpenApp -> {
                    require(step.packageName.isNotBlank()) { "package is blank" }
                }
                is PlanStep.Tap -> require(step.target != NodeSelector()) { "tap has no target" }
                is PlanStep.TypeText -> {
                    require(step.text.isNotBlank()) { "typed text is blank" }
                    require(step.target != NodeSelector()) { "type step has no target" }
                }
                is PlanStep.Scroll -> Unit
                is PlanStep.Wait -> require(step.millis in 0..30_000) { "wait is out of range" }
            }
        }
        plan
    }
}
