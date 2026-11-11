package com.localglasses.app.ui.stats

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.localglasses.app.BuildConfig
import com.localglasses.app.agent.telemetry.RequestMetrics
import com.localglasses.app.agent.telemetry.SpeechMetrics
import com.localglasses.app.ui.AppTab
import com.localglasses.app.ui.visibleTabs
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * With the stats flag off, no stats surface is composed at all, so no speech metrics can be
 * presented. Dictation itself does not depend on the flag.
 */
class SpeechMetricsDisabledTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun speechMetricsAreHiddenWhenStatsAreDisabled() {
        assumeTrue("stats must be disabled for this build", !BuildConfig.STATS_ENABLED)

        rule.setContent {
            val visible = visibleTabs(BuildConfig.STATS_ENABLED)
            assertTrue(visible.none { it == AppTab.STATS })
        }

        rule.onNodeWithText("Speech Recognition").assertDoesNotExist()
        // Dictation itself is unaffected by the stats flag.
    }

    @Test
    fun speechMetricsSectionRendersOnlyForADictation() {
        assumeTrue("stats must be enabled for this build", BuildConfig.STATS_ENABLED)

        rule.setContent {
            StatsForNerdsBlock(
                snapshot = null,
                live = null,
                last = RequestMetrics(speech = SpeechMetrics(source = "GLASSES")),
                isSubmitting = false,
            )
        }
        rule.onNodeWithText("Stats for nerds").performClick()
        rule.onNodeWithText("Speech Recognition").assertExists()
        rule.onNodeWithText("GLASSES").assertExists()
    }
}
