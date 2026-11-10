package com.localglasses.app.ui.stats

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import org.junit.Rule
import org.junit.Test

class SparklineTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersSingleMultiAndAllNullSeries() {
        rule.setContent {
            androidx.compose.foundation.layout.Column {
                Sparkline(values = listOf(1.0))
                Sparkline(values = listOf(1.0, null, 3.0))
                Sparkline(values = listOf(null, null))
            }
        }

        rule.onAllNodesWithTag("sparkline").assertCountEquals(3)
    }
}
