package com.localglasses.app.agent.telemetry

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MetricsCollectorTest {

    @Test
    fun streamEmitsImmediatelyThenOncePerSecondAndStopsOnCancel() = runTest {
        val source = FakeSource()
        val collector = MetricsCollector(
            source = source,
            intervalMs = 1_000,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val values = mutableListOf<DeviceSnapshot>()
        val job = launch { collector.stream().collect { values += it } }

        runCurrent()
        assertEquals(1, source.calls)
        assertEquals(1, values.size)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, source.calls)
        assertEquals(2, values.size)

        job.cancel()
        advanceUntilIdle()
        assertEquals(2, source.calls)
    }

    @Test
    fun snapshotDelegatesToSource() = runTest {
        val source = FakeSource()
        val collector = MetricsCollector(source, ioDispatcher = UnconfinedTestDispatcher(testScheduler))

        val snapshot = collector.snapshot()

        assertEquals(7L, snapshot.deviceTotalBytes)
        assertEquals(1, source.calls)
    }

    private class FakeSource : MetricsSource {
        var calls = 0
        override fun sample(): DeviceSnapshot {
            calls += 1
            return DeviceSnapshot(deviceTotalBytes = 7L)
        }
    }
}
