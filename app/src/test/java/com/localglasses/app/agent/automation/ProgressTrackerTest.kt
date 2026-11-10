package com.localglasses.app.agent.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTrackerTest {

    @Test
    fun repeatedFailureStops() {
        val tracker = ProgressTracker()
        assertFalse(tracker.record("same", success = false).stopped)
        assertTrue(tracker.record("same", success = false).stopped)
    }

    @Test
    fun threeFailuresStop() {
        val tracker = ProgressTracker()
        tracker.record("a", success = false)
        tracker.record("b", success = false)
        assertTrue(tracker.record("c", success = false).stopped)
    }

    @Test
    fun successResetsFailureCount() {
        val tracker = ProgressTracker()
        tracker.record("a", success = false)
        tracker.record("b", success = false)
        tracker.record("c", success = true)
        assertFalse(tracker.record("d", success = false).stopped)
    }

    @Test
    fun sixtyProgressingStepsDoNotStop() {
        val tracker = ProgressTracker()
        repeat(60) { index ->
            assertFalse(tracker.record("screen-$index", success = true).stopped)
        }
    }
}
