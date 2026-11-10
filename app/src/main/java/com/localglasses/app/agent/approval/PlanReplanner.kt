package com.localglasses.app.agent.approval

import com.localglasses.app.agent.commands.RoutingDecision

class PlanReplanner(
    private val route: suspend (String) -> RoutingDecision,
) {
    suspend fun replan(plan: Plan, correction: String): Plan? {
        val prompt = buildString {
            appendLine(plan.command)
            appendLine("Current plan: ${plan.steps}")
            append("Correction: $correction")
        }
        return PlanFactory.fromDecision(plan.command, route(prompt))
    }
}
