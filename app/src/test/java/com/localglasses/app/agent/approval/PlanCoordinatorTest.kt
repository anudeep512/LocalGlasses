package com.localglasses.app.agent.approval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanCoordinatorTest {

    private fun plan() = Plan(command = "call Amma", steps = listOf(PlanStep.Wait(10)))

    @Test
    fun holdsOnePlanAtATime() {
        val coordinator = PlanCoordinator()
        assertTrue(coordinator.offer(plan()))
        assertFalse(coordinator.offer(plan()))
        assertTrue(coordinator.state.value is PlanState.Pending)
    }

    @Test
    fun editClearsApproval() {
        val coordinator = PlanCoordinator()
        coordinator.offer(plan())
        val updated = coordinator.update { it.copy(approved = true) }
        assertTrue(updated != null)
        assertFalse(updated!!.approved)
    }

    @Test
    fun approvalMovesToExecuting() {
        val coordinator = PlanCoordinator()
        coordinator.offer(plan())
        val approved = coordinator.approve()
        assertTrue(approved != null)
        assertTrue(approved!!.approved)
        assertTrue(coordinator.state.value is PlanState.Executing)
    }

    @Test
    fun completionOnlyFromExecuting() {
        val coordinator = PlanCoordinator()
        coordinator.complete()
        assertTrue(coordinator.state.value is PlanState.Idle)
        coordinator.offer(plan())
        coordinator.approve()
        coordinator.complete()
        assertTrue(coordinator.state.value is PlanState.Done)
    }

    @Test
    fun cancelOnlyCancelsPending() {
        val coordinator = PlanCoordinator()
        coordinator.offer(plan())
        coordinator.cancel()
        assertEquals(PlanState.Idle, coordinator.state.value)
        assertNull(coordinator.approve())
    }
}
