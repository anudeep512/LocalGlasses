package com.localglasses.app.agent.automation

import android.app.usage.UsageStatsManager
import android.content.Context

class ForegroundApp(private val context: Context) {

    fun currentPackage(lookbackMs: Long = 60_000): String? {
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        return manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - lookbackMs, end)
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }
}
