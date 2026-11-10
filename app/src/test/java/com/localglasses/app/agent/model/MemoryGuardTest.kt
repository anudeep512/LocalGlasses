package com.localglasses.app.agent.model

import android.content.ComponentCallbacks2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGuardTest {

    @Test
    fun requiresThresholdBeforeLoad() {
        var available = 99L
        val guard = MemoryGuard({ available }, thresholdBytes = 100L)

        assertFalse(guard.canLoad())
        available = 100L
        assertTrue(guard.canLoad())
    }

    @Test
    fun unloadsOnCriticalTrim() {
        val guard = MemoryGuard({ Long.MAX_VALUE })

        assertTrue(guard.shouldUnloadOnTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
        assertTrue(guard.shouldUnloadOnTrim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
        assertFalse(guard.shouldUnloadOnTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
        assertFalse(guard.shouldUnloadOnTrim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
    }
}
