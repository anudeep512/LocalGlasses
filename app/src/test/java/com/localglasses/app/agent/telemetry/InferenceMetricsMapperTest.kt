package com.localglasses.app.agent.telemetry

import com.google.ai.edge.litertlm.BenchmarkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InferenceMetricsMapperTest {

    @Test
    fun mapsSecondsTokensAndThroughput() {
        val benchmark = BenchmarkInfo(
            initTimeInSecond = 1.25,
            timeToFirstTokenInSecond = 0.4,
            lastPrefillTokenCount = 120,
            lastDecodeTokenCount = 30,
            lastPrefillTokensPerSecond = 90.5,
            lastDecodeTokensPerSecond = 12.3,
            markDurationsInSecond = emptyMap(),
            maxNumTokens = 2_048,
        )

        val metrics = InferenceMetricsMapper.fromBenchmark(
            benchmark = benchmark,
            modelId = "gemma",
            backend = "CPU",
            engineLoaded = true,
            inferenceLatencyMs = 2_000,
        )

        assertEquals(1_250L, metrics.engineInitMs)
        assertEquals(400L, metrics.ttftMs)
        assertEquals(120, metrics.promptTokens)
        assertEquals(30, metrics.completionTokens)
        assertEquals(90.5, metrics.prefillTps!!, 0.001)
        assertEquals(12.3, metrics.decodeTps!!, 0.001)
        assertEquals(2_000L, metrics.inferenceLatencyMs)
    }

    @Test
    fun nullBenchmarkKeepsOnlyContextAndLatency() {
        val metrics = InferenceMetricsMapper.fromBenchmark(
            benchmark = null,
            modelId = "gemma",
            backend = "CPU",
            engineLoaded = true,
            inferenceLatencyMs = 50,
        )

        assertEquals("gemma", metrics.modelId)
        assertEquals("CPU", metrics.backend)
        assertEquals(true, metrics.engineLoaded)
        assertEquals(50L, metrics.inferenceLatencyMs)
        assertNull(metrics.ttftMs)
        assertNull(metrics.promptTokens)
        assertNull(metrics.decodeTps)
    }
}
