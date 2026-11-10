package com.localglasses.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class AgentCommandUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun blankCommandCannotRun() {
        rule.onNodeWithText("Run").assertIsNotEnabled()
    }

    @Test
    fun typingEnablesRun() {
        rule.onNode(hasSetTextAction()).performTextInput("call Amma")
        rule.onNodeWithText("Run").assertIsEnabled()
    }
}
