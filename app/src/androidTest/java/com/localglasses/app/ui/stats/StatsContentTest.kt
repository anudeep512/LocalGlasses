package com.localglasses.app.ui.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.localglasses.app.agent.telemetry.DeviceSnapshot
import com.localglasses.app.agent.telemetry.InferenceMetrics
import com.localglasses.app.agent.telemetry.MetricsJson
import com.localglasses.app.agent.telemetry.RequestMetrics
import com.localglasses.app.agent.trace.TraceMetricRow
import com.localglasses.app.agent.trace.TraceMetricsRow
import com.localglasses.app.agent.trace.TraceRow
import org.junit.Rule
import org.junit.Test

class StatsContentTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun emptyRangeShowsEmptyStateWithoutFabricatedZeros() {
        rule.setContent {
            StatsContent(
                state = StatsState(),
                live = null,
                onRangeSelected = {},
            )
        }

        rule.onNodeWithText("No requests in this range.").assertExists()
        rule.onNodeWithText("0.0%").assertDoesNotExist()
        rule.onNodeWithText("Export traces").assertDoesNotExist()
        rule.onNodeWithText("Purge traces").assertDoesNotExist()
    }

    @Test
    fun liveSnapshotShowsValuesAndUnavailableFields() {
        rule.setContent {
            StatsContent(
                state = StatsState(),
                live = DeviceSnapshot(deviceUsedBytes = 1_000_000_000, deviceCpuPercent = null),
                onRangeSelected = {},
            )
        }

        rule.onNodeWithText("Live snapshot").assertExists()
        rule.onAllNodesWithText("953.7 MB")[0].assertExists()
        rule.onAllNodesWithText("Unavailable")[0].assertExists()
    }

    @Test
    fun summaryCardsShowAggregatedValues() {
        val row = sampleRow()
        rule.setContent {
            StatsContent(
                state = StatsState(
                    rows = listOf(row),
                    summary = StatsAggregator.summarize(listOf(row)),
                ),
                live = null,
                onRangeSelected = {},
            )
        }

        rule.onNodeWithText("Requests").assertExists()
        rule.onNodeWithText("100.0%").assertExists()
        rule.onAllNodesWithText("700 ms")[0].assertExists()
    }

    @Test
    fun rangeChipsReloadTheSelectedRange() {
        rule.setContent {
            var range by remember { mutableStateOf(StatsRange.DAY) }
            StatsContent(
                state = StatsState(range = range),
                live = null,
                onRangeSelected = { range = it },
            )
        }

        rule.onNodeWithText("7d").performClick()
        rule.onNodeWithText("7d").assertIsSelected()
        rule.onNodeWithText("All").performClick()
        rule.onNodeWithText("All").assertIsSelected()
    }

    @Test
    fun traceRowExpandsToFullSnapshot() {
        val row = sampleRow()
        rule.setContent {
            StatsContent(
                state = StatsState(
                    rows = listOf(row),
                    summary = StatsAggregator.summarize(listOf(row)),
                ),
                live = null,
                onRangeSelected = {},
            )
        }

        rule.onNodeWithTag("stats-list").performScrollToNode(hasText("call Amma"))
        rule.onNodeWithText("call Amma").performClick()
        rule.onNodeWithText("Time to first token").assertExists()
        rule.onAllNodesWithText("700 ms")[0].assertExists()
        rule.onAllNodesWithText("Light")[0].assertExists()
        rule.onNodeWithText("No requests in this range.").assertDoesNotExist()
    }

    private fun sampleRow(): TraceMetricRow {
        val metrics = RequestMetrics(
            device = DeviceSnapshot(
                deviceModel = "CPH2717",
                deviceUsedBytes = 1_000_000_000,
                appPssBytes = 200_000_000,
            ),
            inference = InferenceMetrics(
                modelId = "gemma",
                backend = "CPU",
                ttftMs = 250,
                decodeTps = 12.5,
                inferenceLatencyMs = 700,
            ),
            peakAppPssBytes = 220_000_000,
            peakDeviceCpuPercent = 45.0,
            worstThermalStatus = "Light",
        )
        return TraceMetricRow(
            trace = TraceRow(
                id = "trace-1",
                command = "call Amma",
                taskType = "action",
                targetPackage = null,
                outcome = "success",
                modelPrompt = null,
                toolCallsJson = null,
                planJson = null,
                errorReason = null,
                startedAt = 1_000,
            ),
            metrics = TraceMetricsRow(
                traceId = "trace-1",
                capturedAt = 1_000,
                inferenceLatencyMs = 700,
                ttftMs = 250,
                promptTokens = 100,
                completionTokens = 20,
                prefillTps = 80.0,
                decodeTps = 12.5,
                peakAppPssBytes = 220_000_000,
                peakDeviceCpuPercent = 45.0,
                worstThermalStatus = "Light",
                snapshotJson = MetricsJson.encode(metrics),
            ),
        )
    }
}
