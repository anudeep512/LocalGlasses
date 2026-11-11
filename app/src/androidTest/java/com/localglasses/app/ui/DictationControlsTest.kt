package com.localglasses.app.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.localglasses.app.agent.dictation.DictationState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DictationControlsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(
        state: DictationState = DictationState.Idle,
        level: Float = 0f,
        enabled: Boolean = true,
        onMicTap: () -> Unit = {},
    ) {
        rule.setContent {
            DictationControls(state = state, level = level, enabled = enabled, onMicTap = onMicTap)
        }
    }

    @Test
    fun idleShowsTheDictateControlAndReadyStatus() {
        show()

        rule.onNodeWithTag("dictation-mic").assertIsEnabled()
        rule.onNodeWithText("Dictate").assertExists()
        rule.onNodeWithText("Ready to dictate").assertExists()
        rule.onNodeWithTag("dictation-level").assertDoesNotExist()
    }

    @Test
    fun disabledWhileACommandIsSubmitting() {
        show(enabled = false)

        rule.onNodeWithTag("dictation-mic").assertIsNotEnabled()
    }

    @Test
    fun capturingShowsStopAndTheLevelMeter() {
        show(state = DictationState.Capturing, level = 0.42f)

        rule.onNodeWithText("Stop").assertExists()
        rule.onNodeWithText("Listening").assertExists()
        rule.onNodeWithTag("dictation-level").assertExists()
    }

    @Test
    fun transcribingDisablesTheControl() {
        show(state = DictationState.Transcribing)

        rule.onNodeWithTag("dictation-mic").assertIsNotEnabled()
        rule.onNodeWithText("Transcribing…").assertExists()
    }

    @Test
    fun connectingShowsTheGlassesBringUp() {
        show(state = DictationState.Connecting)

        rule.onNodeWithText("Connecting glasses…").assertExists()
    }

    @Test
    fun anErrorIsShownInsteadOfAStatus() {
        show(state = DictationState.Error("Microphone permission is required"))

        rule.onNodeWithText("Microphone permission is required").assertExists()
    }

    @Test
    fun aTapReachesTheCallback() {
        var taps = 0
        show(onMicTap = { taps++ })

        rule.onNodeWithTag("dictation-mic").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun noTranscriptTextIsRenderedWhileCapturing() {
        show(state = DictationState.Capturing, level = 0.9f)

        // The component never renders dictated text; the level meter is the only progress.
        rule.onNodeWithText("call Amma").assertDoesNotExist()
    }
}
