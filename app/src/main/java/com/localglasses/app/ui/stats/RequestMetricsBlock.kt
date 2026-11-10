package com.localglasses.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.localglasses.app.agent.telemetry.DeviceSnapshot
import com.localglasses.app.agent.telemetry.MetricsFormat
import com.localglasses.app.agent.telemetry.RequestMetrics

@Composable
fun RequestMetricsContent(
    live: DeviceSnapshot?,
    last: RequestMetrics?,
    isSubmitting: Boolean,
    modifier: Modifier = Modifier,
) {
    val snapshot = live ?: last?.device
    val inference = last?.inference

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Request metrics", style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    isSubmitting -> "Live"
                    last != null -> "Last request"
                    else -> "No request yet"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        MetricLine("Memory used", MetricsFormat.bytes(snapshot?.deviceUsedBytes))
        MetricLine("Available", MetricsFormat.bytes(snapshot?.deviceAvailableBytes))
        MetricLine("App PSS", MetricsFormat.bytes(snapshot?.appPssBytes))
        MetricLine("Device CPU", MetricsFormat.percent(snapshot?.deviceCpuPercent))
        MetricLine("App CPU", MetricsFormat.percent(snapshot?.appCpuPercent))
        MetricLine("Thermal", MetricsFormat.text(snapshot?.thermalStatus))
        MetricLine("Battery", MetricsFormat.percent(snapshot?.batteryLevelPercent?.toDouble()))
        MetricLine("Free storage", MetricsFormat.bytes(snapshot?.freeStorageBytes))
        MetricLine("Model", MetricsFormat.text(inference?.modelId ?: snapshot?.modelId))
        MetricLine("Backend", MetricsFormat.text(inference?.backend ?: snapshot?.backend))
        MetricLine("Latency", MetricsFormat.millis(inference?.inferenceLatencyMs))
        MetricLine("Time to first token", MetricsFormat.millis(inference?.ttftMs))
        MetricLine("Prompt tokens", MetricsFormat.count(inference?.promptTokens))
        MetricLine("Completion tokens", MetricsFormat.count(inference?.completionTokens))
        MetricLine("Prefill", MetricsFormat.tokensPerSecond(inference?.prefillTps))
        MetricLine("Decode", MetricsFormat.tokensPerSecond(inference?.decodeTps))
        MetricLine("Engine init", MetricsFormat.millis(inference?.engineInitMs))
        MetricLine("Peak app memory", MetricsFormat.bytes(last?.peakAppPssBytes))
        MetricLine("Peak device CPU", MetricsFormat.percent(last?.peakDeviceCpuPercent))
    }
}
