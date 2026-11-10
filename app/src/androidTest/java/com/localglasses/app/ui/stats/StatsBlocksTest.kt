package com.localglasses.app.ui.stats

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.localglasses.app.agent.telemetry.DeviceSnapshot
import com.localglasses.app.agent.telemetry.InferenceMetrics
import com.localglasses.app.agent.telemetry.RequestMetrics
import org.junit.Rule
import org.junit.Test

class StatsBlocksTest {

    @get:Rule
    val rule = createComposeRule()

    private val snapshot = DeviceSnapshot(
        deviceModel = "CPH2717",
        soc = "Dimensity 7300",
        abis = "arm64-v8a",
        androidVersion = "16",
        memoryClassMb = 512,
        modelId = "gemma-4-E2B-it",
        modelFileBytes = 2_588_147_712,
        backend = "CPU",
        engineLoaded = true,
        deviceUsedBytes = 1_000_000_000,
        appCpuPercent = 12.0,
    )

    @Test
    fun statsForNerdsStartsCollapsedAndExpandsToRequestMetricsAndSpecs() {
        rule.setContent {
            StatsForNerdsBlock(
                snapshot = snapshot,
                live = null,
                last = null,
                isSubmitting = false,
            )
        }

        rule.onNodeWithText("Device model").assertDoesNotExist()
        rule.onNodeWithText("Stats for nerds").performClick()
        rule.onNodeWithText("Request metrics").assertExists()
        rule.onNodeWithText("No request yet").assertExists()
        rule.onNodeWithText("CPH2717").assertExists()
        rule.onNodeWithText("Dimensity 7300").assertExists()
        rule.onNodeWithText("No public Android API available").assertExists()
    }

    @Test
    fun statsForNerdsAutoExpandsWhileRequestIsRunning() {
        rule.setContent {
            StatsForNerdsBlock(
                snapshot = snapshot,
                live = snapshot,
                last = null,
                isSubmitting = true,
            )
        }

        rule.onNodeWithText("Live").assertExists()
        rule.onNodeWithText("Memory used").assertExists()
        rule.onNodeWithText("Request metrics").assertExists()
    }

    @Test
    fun statsForNerdsShowsCompletedRequestAndCollapses() {
        val last = RequestMetrics(
            device = snapshot,
            inference = InferenceMetrics(
                modelId = "gemma",
                backend = "CPU",
                inferenceLatencyMs = 700,
                decodeTps = 12.5,
            ),
        )

        rule.setContent {
            StatsForNerdsBlock(
                snapshot = snapshot,
                live = snapshot,
                last = last,
                isSubmitting = false,
            )
        }
        rule.onNodeWithText("Stats for nerds").performClick()
        rule.onNodeWithText("700 ms").assertExists()
        rule.onNodeWithText("12.5 tok/s").assertExists()
        rule.onNodeWithText("Stats for nerds").performClick()
        rule.onNodeWithText("Time to first token").assertDoesNotExist()
    }
}
