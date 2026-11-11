package com.localglasses.app.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.localglasses.app.agent.telemetry.DeviceSnapshot
import com.localglasses.app.agent.telemetry.InferenceMetrics
import com.localglasses.app.agent.telemetry.RequestMetrics
import com.localglasses.app.agent.telemetry.SpeechMetrics
import org.junit.Rule
import org.junit.Test

/** The stats block groups numbers by what produced them. */
class StatsSectionsTest {

    @get:Rule
    val rule = createComposeRule()

    private val device = DeviceSnapshot(
        deviceUsedBytes = 5_000_000_000,
        deviceAvailableBytes = 2_000_000_000,
        deviceCpuPercent = 21.0,
        appPssBytes = 900_000_000,
        appCpuPercent = 9.5,
        threadCount = 74,
        thermalStatus = "Light",
        modelId = "gemma-4-E2B-it",
        backend = "GPU",
        engineLoaded = true,
    )

    private val inference = InferenceMetrics(
        modelId = "gemma-4-E2B-it",
        backend = "GPU",
        engineLoaded = true,
        engineInitMs = 8_748,
        promptTokens = 882,
        completionTokens = 27,
        ttftMs = 1_783,
        prefillTps = 536.2,
        decodeTps = 10.8,
        inferenceLatencyMs = 4_568,
    )

    private val speech = SpeechMetrics(
        modelId = "whisper-small.en-int8",
        source = "PHONE",
        audioDurationMs = 2_400,
        recognitionLatencyMs = 1_100,
        realTimeFactor = 0.46,
        endpointReason = "SILENCE",
        wordCount = 3,
    )

    @Composable
    private fun Content(metrics: RequestMetrics?) {
        Column {
            RequestMetricsContent(live = device, last = metrics, isSubmitting = false)
        }
    }

    @Test
    fun sectionsAreGroupedByProducer() {
        rule.setContent { Content(RequestMetrics(device = device, inference = inference)) }

        rule.onNodeWithText("LLM Inference Metrics").assertExists()
        rule.onNodeWithText("App").assertExists()
        rule.onNodeWithText("Device Live").assertExists()
        rule.onNodeWithText("Speech Recognition").assertExists()
    }

    @Test
    fun speechValuesAppearNextToTheInferenceNumbers() {
        rule.setContent {
            Content(RequestMetrics(device = device, inference = inference, speech = speech))
        }

        rule.onNodeWithText("Speech Recognition").assertExists()
        rule.onNodeWithText("whisper-small.en-int8").assertExists()
        rule.onNodeWithText("0.46\u00d7").assertExists()
    }

    @Test
    fun modelFiguresSitInTheInferenceSection() {
        rule.setContent { Content(RequestMetrics(device = device, inference = inference)) }

        rule.onNodeWithText("GPU").assertExists()
        rule.onNodeWithText("8748 ms").assertExists()
        rule.onNodeWithText("536.2 tok/s").assertExists()
        rule.onNodeWithText("1783 ms").assertExists()
    }
}
