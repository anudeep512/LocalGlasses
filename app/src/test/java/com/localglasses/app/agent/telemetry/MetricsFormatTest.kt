package com.localglasses.app.agent.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricsFormatTest {

    @Test
    fun nullsAreUnavailable() {
        assertEquals(MetricsFormat.UNAVAILABLE, MetricsFormat.bytes(null))
        assertEquals(MetricsFormat.UNAVAILABLE, MetricsFormat.percent(null))
        assertEquals(MetricsFormat.UNAVAILABLE, MetricsFormat.millis(null))
        assertEquals(MetricsFormat.UNAVAILABLE, MetricsFormat.tokensPerSecond(null))
        assertEquals(MetricsFormat.UNAVAILABLE, MetricsFormat.count(null))
        assertEquals(MetricsFormat.UNAVAILABLE, MetricsFormat.celsius(null))
        assertEquals(MetricsFormat.UNAVAILABLE, MetricsFormat.text(null))
        assertEquals(MetricsFormat.UNAVAILABLE, MetricsFormat.text("  "))
    }

    @Test
    fun formatsBytesWithBinaryUnits() {
        assertEquals("0 B", MetricsFormat.bytes(0))
        assertEquals("512 B", MetricsFormat.bytes(512))
        assertEquals("1.0 KB", MetricsFormat.bytes(1024))
        assertEquals("1.5 MB", MetricsFormat.bytes(1024L * 1024L + 512L * 1024L))
        assertEquals("1.0 GB", MetricsFormat.bytes(1024L * 1024L * 1024L))
    }

    @Test
    fun formatsNumbersAndZero() {
        assertEquals("0.0%", MetricsFormat.percent(0.0))
        assertEquals("12.3%", MetricsFormat.percent(12.34))
        assertEquals("0 ms", MetricsFormat.millis(0))
        assertEquals("1.2 tok/s", MetricsFormat.tokensPerSecond(1.23))
        assertEquals("0", MetricsFormat.count(0))
        assertEquals("36.6 °C", MetricsFormat.celsius(36.6))
    }

    @Test
    fun roundTripsRequestMetricsAndKeepsNulls() {
        val metrics = RequestMetrics(
            device = DeviceSnapshot(deviceTotalBytes = 8_000_000_000, deviceCpuPercent = null),
            inference = InferenceMetrics(modelId = "gemma", decodeTps = 12.5),
            peakAppPssBytes = 42,
        )

        val decoded = MetricsJson.decode(MetricsJson.encode(metrics))

        assertEquals(metrics, decoded)
        assertNull(decoded?.inference?.ttftMs)
    }
}
