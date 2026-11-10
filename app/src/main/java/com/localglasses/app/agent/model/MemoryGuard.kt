package com.localglasses.app.agent.model

import android.content.ComponentCallbacks2
import android.content.Context

/**
 * Decides whether the model may be loaded and when it must be released.
 *
 * A resident Gemma 4 E2B engine is roughly 1.5–2 GB. On this 7.65 GB phone the
 * threshold leaves room for the app the agent is about to launch.
 */
class MemoryGuard(
    private val availableBytes: () -> Long,
    private val thresholdBytes: Long = DEFAULT_THRESHOLD_BYTES,
) {

    fun canLoad(): Boolean = availableBytes() >= thresholdBytes

    fun shouldUnloadOnTrim(level: Int): Boolean =
        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE

    companion object {
        const val DEFAULT_THRESHOLD_BYTES = 1L * 1024 * 1024 * 1024
    }
}

object AndroidMemorySource {
    fun availableBytes(context: Context): Long {
        val manager = context.getSystemService(android.app.ActivityManager::class.java)
        val info = android.app.ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.availMem
    }
}

class LowMemoryException :
    IllegalStateException("Available memory is below the model load threshold")
