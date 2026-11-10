package com.localglasses.app.agent.approval

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PlanState {
    data object Idle : PlanState
    data class Pending(val plan: Plan) : PlanState
    data class Executing(val plan: Plan, val step: Int) : PlanState
    data object Done : PlanState
    data class Failed(val reason: String) : PlanState
}

/**
 * Holds exactly one plan at a time. Edits always clear approval, and a second command
 * is rejected until the pending plan is approved or cancelled.
 */
class PlanCoordinator {

    private val _state = MutableStateFlow<PlanState>(PlanState.Idle)
    val state: StateFlow<PlanState> = _state.asStateFlow()

    fun offer(plan: Plan): Boolean {
        if (_state.value is PlanState.Pending || _state.value is PlanState.Executing) {
            return false
        }
        _state.value = PlanState.Pending(plan.copy(approved = false))
        return true
    }

    fun update(transform: (Plan) -> Plan): Plan? {
        val pending = _state.value as? PlanState.Pending ?: return null
        val updated = transform(pending.plan).copy(approved = false)
        _state.value = PlanState.Pending(updated)
        return updated
    }

    fun approve(): Plan? {
        val pending = _state.value as? PlanState.Pending ?: return null
        val approved = pending.plan.copy(approved = true)
        _state.value = PlanState.Executing(approved, 0)
        return approved
    }

    fun progress(step: Int) {
        val executing = _state.value as? PlanState.Executing ?: return
        _state.value = executing.copy(step = step)
    }

    fun complete() {
        if (_state.value is PlanState.Executing) {
            _state.value = PlanState.Done
        }
    }

    fun fail(reason: String) {
        if (_state.value is PlanState.Executing) {
            _state.value = PlanState.Failed(reason)
        }
    }

    fun cancel() {
        if (_state.value is PlanState.Pending) {
            _state.value = PlanState.Idle
        }
    }
}
