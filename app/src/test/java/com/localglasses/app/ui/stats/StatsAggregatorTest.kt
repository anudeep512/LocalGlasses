package com.localglasses.app.ui.stats

import com.localglasses.app.agent.telemetry.MetricsJson
import com.localglasses.app.agent.telemetry.RequestMetrics
import com.localglasses.app.agent.telemetry.SpeechMetrics
import com.localglasses.app.agent.trace.TraceMetricRow
import com.localglasses.app.agent.trace.TraceMetricsRow
import com.localglasses.app.agent.trace.TraceRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsAggregatorTest {

    @Test
    fun emptyRowsProduceEmptySummary() {
        val summary = StatsAggregator.summarize(emptyList())

        assertEquals(0, summary.totalRequests)
        assertNull(summary.successRate)
        assertNull(summary.medianLatencyMs)
        assertNull(summary.p95LatencyMs)
        assertNull(summary.averageTtftMs)
        assertNull(summary.averageDecodeTps)
        assertNull(summary.peakAppPssBytes)
        assertNull(summary.averageDeviceCpuPercent)
        assertNull(summary.latestThermalStatus)
    }

    @Test
    fun aggregatesExcludeNullValues() {
        val rows = listOf(
            row("success", 100, 10, 20.0, 1_000, 10.0, "Light"),
            row("failed", 300, null, 40.0, 2_000, null, "Moderate"),
            row("success", null, null, null, null, null, null),
        )

        val summary = StatsAggregator.summarize(rows)

        assertEquals(3, summary.totalRequests)
        assertEquals(2.0 / 3.0, summary.successRate!!, 0.001)
        assertEquals(100L, summary.medianLatencyMs)
        assertEquals(300L, summary.p95LatencyMs)
        assertEquals(10L, summary.averageTtftMs)
        assertEquals(30.0, summary.averageDecodeTps!!, 0.001)
        assertEquals(2_000L, summary.peakAppPssBytes)
        assertEquals(10.0, summary.averageDeviceCpuPercent!!, 0.001)
        assertEquals("Light", summary.latestThermalStatus)
    }

    @Test
    fun aggregatesSpeechMetricsFromRequestSnapshots() {
        val dictated = row(
            "success", 100, 10, 20.0, 1_000, 10.0, "Light",
            snapshot = speechSnapshot(recognitionMs = 1_000, realTimeFactor = 0.5, words = 2),
        )
        val alsoDictated = row(
            "success", 200, 20, 20.0, 1_000, 10.0, "Light",
            snapshot = speechSnapshot(recognitionMs = 2_000, realTimeFactor = 0.7, words = 4),
        )
        val typed = row("success", 300, 30, 20.0, 1_000, 10.0, "Light")

        val summary = StatsAggregator.summarize(listOf(dictated, alsoDictated, typed))

        assertEquals(3, summary.totalRequests)
        assertEquals(2, summary.dictations)
        assertEquals(1_500L, summary.averageRecognitionMs)
        assertEquals(0.6, summary.averageRealTimeFactor!!, 0.001)
        assertEquals(3.0, summary.averageWords!!, 0.001)
    }

    @Test
    fun typedOnlyHistoryReportsNoSpeechMetrics() {
        val summary = StatsAggregator.summarize(listOf(row("success", 100, 10, 20.0, 1_000, 10.0, "Light")))

        assertEquals(0, summary.dictations)
        assertNull(summary.averageRecognitionMs)
        assertNull(summary.averageRealTimeFactor)
        assertNull(summary.averageWords)
    }

    @Test
    fun percentileUsesNearestRank() {
        assertEquals(200L, StatsAggregator.percentile(listOf(100, 200, 300, 400), 50.0))
        assertEquals(400L, StatsAggregator.percentile(listOf(100, 200, 300, 400), 95.0))
    }

    private fun row(
        outcome: String,
        latency: Long?,
        ttft: Long?,
        decode: Double?,
        pss: Long?,
        cpu: Double?,
        thermal: String?,
        snapshot: String = "{}",
    ) = TraceMetricRow(
        trace = TraceRow(
            id = "$outcome-$latency",
            command = "command",
            taskType = "action",
            targetPackage = null,
            outcome = outcome,
            modelPrompt = null,
            toolCallsJson = null,
            planJson = null,
            errorReason = null,
            startedAt = 1,
        ),
        metrics = TraceMetricsRow(
            traceId = "$outcome-$latency",
            capturedAt = 1,
            inferenceLatencyMs = latency,
            ttftMs = ttft,
            promptTokens = null,
            completionTokens = null,
            prefillTps = null,
            decodeTps = decode,
            peakAppPssBytes = pss,
            peakDeviceCpuPercent = cpu,
            worstThermalStatus = thermal,
            snapshotJson = snapshot,
        ),
    )

    private fun speechSnapshot(recognitionMs: Long, realTimeFactor: Double, words: Int): String =
        MetricsJson.encode(
            RequestMetrics(
                speech = SpeechMetrics(
                    modelId = "whisper-small.en-int8",
                    source = "PHONE",
                    audioDurationMs = 2_000,
                    recognitionLatencyMs = recognitionMs,
                    realTimeFactor = realTimeFactor,
                    endpointReason = "SILENCE",
                    wordCount = words,
                ),
            ),
        )
}
