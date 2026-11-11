package com.localglasses.app.ui.stats

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.localglasses.app.agent.telemetry.DeviceSnapshot
import com.localglasses.app.agent.telemetry.MetricsFormat
import com.localglasses.app.agent.telemetry.RequestMetrics
import com.localglasses.app.agent.telemetry.SpeechMetrics
import org.junit.Rule
import org.junit.Test

class SpeechMetricsPresentationTest {

    @get:Rule
    val rule = createComposeRule()

    private val snapshot = DeviceSnapshot(deviceModel = "CPH2717", appPssBytes = 900_000_000)

    private val dictated = RequestMetrics(
        device = snapshot,
        speech = SpeechMetrics(
            modelId = "whisper-small.en-int8",
            source = "PHONE",
            audioDurationMs = 2_400,
            recognitionLatencyMs = 1_100,
            realTimeFactor = 0.46,
            endpointReason = "SILENCE",
            peakAppPssBytes = 950_000_000,
            wordCount = 3,
        ),
    )

    private fun showBlock(last: RequestMetrics?) {
        rule.setContent {
            StatsForNerdsBlock(
                snapshot = snapshot,
                live = null,
                last = last,
                isSubmitting = false,
            )
        }
        rule.onNodeWithText("Stats for nerds").performClick()
    }

    @Test
    fun dictatedRequestShowsSpeechMetrics() {
        showBlock(dictated)

        rule.onNodeWithText("Speech Recognition").assertExists()
        rule.onNodeWithText("whisper-small.en-int8").assertExists()
        rule.onNodeWithText("PHONE").assertExists()
        rule.onNodeWithText("0.46\u00d7").assertExists()
        rule.onNodeWithText("SILENCE").assertExists()
    }

    @Test
    fun typedRequestStillShowsTheSectionWithUnavailableValues() {
        showBlock(RequestMetrics(device = snapshot))

        rule.onNodeWithText("Speech Recognition").assertExists()
        rule.onNodeWithText("Speech model").assertExists()
        rule.onNodeWithText(MetricsFormat.UNAVAILABLE).assertExists()
    }

    @Test
    fun missingSpeechValuesArePresentedAsUnavailable() {
        showBlock(
            RequestMetrics(
                device = snapshot,
                speech = SpeechMetrics(modelId = "whisper-small.en-int8"),
            ),
        )

        rule.onNodeWithText("Speech Recognition").assertExists()
        rule.onNodeWithText("Real-time factor").assertExists()
        rule.onNodeWithText(MetricsFormat.UNAVAILABLE).assertExists()
    }
}
