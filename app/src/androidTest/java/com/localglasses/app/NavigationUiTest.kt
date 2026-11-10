package com.localglasses.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class NavigationUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsAllVisibleTabsAndOpensStatsAndSettings() {
        rule.onNodeWithText("Camera").assertExists()
        rule.onNodeWithText("Stats").performClick()
        rule.onNodeWithText("Live snapshot").assertExists()
        rule.onNodeWithText("Settings").performClick()
        rule.onAllNodesWithText("Settings").assertCountEquals(2)
    }
}
