package com.localglasses.app.agent.approval

import com.localglasses.app.agent.commands.RegistryActionRequest
import com.localglasses.app.agent.commands.RoutingDecision

object PlanFactory {

    fun fromAction(command: String, request: RegistryActionRequest): Plan =
        Plan(
            command = command,
            steps = listOf(PlanStep.RunAction(request.actionId, request.arguments)),
        )

    fun fromDecision(command: String, decision: RoutingDecision): Plan? =
        (decision as? RoutingDecision.Action)?.let { fromAction(command, it.request) }
}
