package com.localglasses.app.ui.stats

import com.localglasses.app.agent.trace.TraceMetricRow
import kotlin.math.ceil

enum class StatsRange(val label: String, val durationMs: Long) {
    DAY("24h", 24L * 60L * 60L * 1_000L),
    WEEK("7d", 7L * 24L * 60L * 60L * 1_000L),
    ALL("All", 0L),
}

data class StatsSummary(
    val totalRequests: Int = 0,
    val successRate: Double? = null,
    val medianLatencyMs: Long? = null,
    val p95LatencyMs: Long? = null,
    val averageTtftMs: Long? = null,
    val averageDecodeTps: Double? = null,
    val peakAppPssBytes: Long? = null,
    val averageDeviceCpuPercent: Double? = null,
    val latestThermalStatus: String? = null,
)

object StatsAggregator {

    fun summarize(rows: List<TraceMetricRow>): StatsSummary {
        if (rows.isEmpty()) return StatsSummary()

        val latencies = rows.mapNotNull { it.metrics?.inferenceLatencyMs }.sorted()
        val ttft = rows.mapNotNull { it.metrics?.ttftMs }
        val decode = rows.mapNotNull { it.metrics?.decodeTps }
        val pss = rows.mapNotNull { it.metrics?.peakAppPssBytes }
        val cpu = rows.mapNotNull { it.metrics?.peakDeviceCpuPercent }
        val successes = rows.count { it.trace.outcome == "success" }

        return StatsSummary(
            totalRequests = rows.size,
            successRate = successes.toDouble() / rows.size,
            medianLatencyMs = percentile(latencies, 50.0),
            p95LatencyMs = percentile(latencies, 95.0),
            averageTtftMs = ttft.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            averageDecodeTps = decode.takeIf { it.isNotEmpty() }?.average(),
            peakAppPssBytes = pss.maxOrNull(),
            averageDeviceCpuPercent = cpu.takeIf { it.isNotEmpty() }?.average(),
            latestThermalStatus = rows.firstOrNull()?.metrics?.worstThermalStatus,
        )
    }

    fun percentile(sorted: List<Long>, percentile: Double): Long? {
        if (sorted.isEmpty()) return null
        val rank = ceil(percentile / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}
