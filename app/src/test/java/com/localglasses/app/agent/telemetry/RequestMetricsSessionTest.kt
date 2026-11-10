package com.localglasses.app.agent.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RequestMetricsSessionTest {

    private val baseline = DeviceSnapshot(
        appPssBytes = 100,
        deviceCpuPercent = 5.0,
        thermalStatus = "Light",
    )

    @Test
    fun noObservationLeavesPeaksNull() {
        val session = RequestMetricsSession(baseline, startedAtMs = 100)

        val metrics = session.finish(InferenceMetrics(modelId = "gemma"), durationMs = 900)

        assertEquals(baseline, metrics.device)
        assertNull(metrics.peakAppPssBytes)
        assertNull(metrics.peakDeviceCpuPercent)
        assertNull(metrics.worstThermalStatus)
        assertEquals(900L, metrics.commandDurationMs)
        assertEquals("gemma", metrics.inference?.modelId)
    }

    @Test
    fun observesMaximumMemoryCpuAndWorstThermal() {
        val session = RequestMetricsSession(baseline, startedAtMs = 100)
        session.observe(baseline.copy(appPssBytes = 250, deviceCpuPercent = 12.0, thermalStatus = "Moderate"))
        session.observe(baseline.copy(appPssBytes = 200, deviceCpuPercent = 9.0, thermalStatus = "Severe"))
        session.observe(baseline.copy(appPssBytes = 300, deviceCpuPercent = 7.0, thermalStatus = "Light"))

        val metrics = session.finish(null, durationMs = 500)

        assertEquals(300L, metrics.peakAppPssBytes)
        assertEquals(12.0, metrics.peakDeviceCpuPercent!!, 0.001)
        assertEquals("Severe", metrics.worstThermalStatus)
        assertNull(metrics.inference)
    }
}
