package com.localglasses.app.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.localglasses.app.agent.dictation.AudioSourceKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DictationSourceSelectorTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun phoneIsSelectedByDefault() {
        rule.setContent {
            DictationSourceSelector(
                selected = AudioSourceKind.PHONE,
                glassesAvailable = true,
                onSelect = {},
            )
        }

        rule.onNodeWithTag("source-phone").assertIsSelected()
        rule.onNodeWithText("Phone microphone").assertExists()
    }

    @Test
    fun selectingGlassesReportsTheChoice() {
        var selected: AudioSourceKind? = null
        rule.setContent {
            DictationSourceSelector(
                selected = AudioSourceKind.PHONE,
                glassesAvailable = true,
                onSelect = { selected = it },
            )
        }

        rule.onNodeWithTag("source-glasses").performClick()

        assertEquals(AudioSourceKind.GLASSES, selected)
    }

    @Test
    fun glassesSelectedIsReflected() {
        rule.setContent {
            DictationSourceSelector(
                selected = AudioSourceKind.GLASSES,
                glassesAvailable = true,
                onSelect = {},
            )
        }

        rule.onNodeWithTag("source-glasses").assertIsSelected()
    }

    @Test
    fun glassesIsDisabledAndExplainedWhenTheBuildLacksAudioSupport() {
        rule.setContent {
            DictationSourceSelector(
                selected = AudioSourceKind.PHONE,
                glassesAvailable = false,
                onSelect = {},
            )
        }

        rule.onNodeWithTag("source-glasses").assertIsNotEnabled()
        rule.onNodeWithText("Unavailable in this build").assertExists()
    }
}
