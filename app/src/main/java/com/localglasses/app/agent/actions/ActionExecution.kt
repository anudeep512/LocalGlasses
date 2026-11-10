package com.localglasses.app.agent.actions

sealed interface ActionExecution {
    data class Success(val message: String) : ActionExecution
    data class NeedsPermission(val permission: String) : ActionExecution
    data class NeedsChoice(val candidates: List<ContactCandidate>) : ActionExecution
    data class Failed(val reason: String) : ActionExecution
}
