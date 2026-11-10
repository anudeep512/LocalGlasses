package com.localglasses.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppTabTest {

    @Test
    fun statsIsVisibleWhenEnabled() {
        assertEquals(
            listOf(AppTab.AGENT, AppTab.CAMERA, AppTab.STATS, AppTab.SETTINGS),
            visibleTabs(statsEnabled = true),
        )
    }

    @Test
    fun statsIsHiddenWhenDisabled() {
        assertEquals(
            listOf(AppTab.AGENT, AppTab.CAMERA, AppTab.SETTINGS),
            visibleTabs(statsEnabled = false),
        )
    }

    @Test
    fun hiddenSelectionFallsBackToAgent() {
        assertEquals(AppTab.AGENT, fallbackTab(AppTab.STATS, statsEnabled = false))
        assertEquals(AppTab.STATS, fallbackTab(AppTab.STATS, statsEnabled = true))
    }
}
