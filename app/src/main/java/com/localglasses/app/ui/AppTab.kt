package com.localglasses.app.ui

/** Stable navigation routes. Keeping an enum avoids index shifts when Stats is hidden. */
enum class AppTab(val label: String) {
    AGENT("Agent"),
    CAMERA("Camera"),
    STATS("Stats"),
    SETTINGS("Settings"),
}

fun visibleTabs(statsEnabled: Boolean): List<AppTab> =
    AppTab.entries.filter { it != AppTab.STATS || statsEnabled }

fun fallbackTab(selected: AppTab, statsEnabled: Boolean): AppTab =
    if (selected in visibleTabs(statsEnabled)) selected else AppTab.AGENT
