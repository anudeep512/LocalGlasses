package com.localglasses.app.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.localglasses.app.agent.telemetry.SpeechMetrics
import org.junit.Rule
import org.junit.Test

/** The Speech Recognition section fills in as soon as an utterance is processed. */
class LiveSpeechMetricsTest {

    @get:Rule
    val rule = createComposeRule()

    private val dictation = SpeechMetrics(
        modelId = "whisper-small.en-int8",
        source = "PHONE",
        audioDurationMs = 2_400,
        recognitionLatencyMs = 1_100,
        realTimeFactor = 0.46,
        endpointReason = "SILENCE",
        wordCount = 3,
    )

    @Test
    fun speechNumbersAppearBeforeAnyCommandIsSubmitted() {
        rule.setContent {
            Column {
                RequestMetricsContent(
                    live = null,
                    last = null,
                    isSubmitting = false,
                    speech = dictation,
                )
            }
        }

        rule.onNodeWithText("Speech Recognition").assertExists()
        rule.onNodeWithText("whisper-small.en-int8").assertExists()
        rule.onNodeWithText("0.46\u00d7").assertExists()
        rule.onNodeWithText("No request yet").assertExists()
    }

    @Test
    fun withoutADictationTheSectionIsStillThere() {
        rule.setContent {
            Column {
                RequestMetricsContent(live = null, last = null, isSubmitting = false)
            }
        }

        rule.onNodeWithText("Speech Recognition").assertExists()
        rule.onNodeWithText("Speech model").assertExists()
    }
}
