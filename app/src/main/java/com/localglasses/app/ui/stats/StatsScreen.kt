package com.localglasses.app.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localglasses.app.agent.telemetry.DeviceSnapshot
import com.localglasses.app.agent.telemetry.MetricsFormat
import com.localglasses.app.agent.telemetry.MetricsJson
import com.localglasses.app.agent.trace.TraceMetricRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var live by remember { mutableStateOf<DeviceSnapshot?>(null) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            launch {
                viewModel.liveMetrics.collect { live = it }
            }
            viewModel.refresh()
        }
    }

    StatsContent(
        state = state,
        live = live,
        onRangeSelected = viewModel::setRange,
        modifier = modifier,
    )
}

@Composable
fun StatsContent(
    state: StatsState,
    live: DeviceSnapshot?,
    onRangeSelected: (StatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("stats-list")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Stats", style = MaterialTheme.typography.headlineSmall) }
        item { LiveSnapshotCard(live) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.range == range,
                        onClick = { onRangeSelected(range) },
                        label = { Text(range.label) },
                    )
                }
            }
        }
        item { SummaryCards(state.summary) }

        if (state.rows.isEmpty()) {
            item {
                Text(
                    "No requests in this range.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            item { Text("Trends", style = MaterialTheme.typography.titleSmall) }
            val ordered = state.rows.asReversed()
            item {
                SparklineCard(
                    title = "Latency (ms)",
                    values = ordered.map { it.metrics?.inferenceLatencyMs?.toDouble() },
                )
            }
            item {
                SparklineCard(
                    title = "Decode (tok/s)",
                    values = ordered.map { it.metrics?.decodeTps },
                )
            }
            item {
                SparklineCard(
                    title = "App memory (PSS)",
                    values = ordered.map { it.metrics?.peakAppPssBytes?.toDouble() },
                )
            }
            item {
                SparklineCard(
                    title = "Device CPU (%)",
                    values = ordered.map { it.metrics?.peakDeviceCpuPercent },
                )
            }
            item { Text("Requests", style = MaterialTheme.typography.titleSmall) }
            items(state.rows, key = { it.trace.id }) { row ->
                TraceHistoryRow(row)
            }
        }
    }
}

@Composable
private fun LiveSnapshotCard(live: DeviceSnapshot?) {
    MetricsCard("Live snapshot") {
        MetricLine("Memory used", MetricsFormat.bytes(live?.deviceUsedBytes))
        MetricLine("Available", MetricsFormat.bytes(live?.deviceAvailableBytes))
        MetricLine("Device CPU", MetricsFormat.percent(live?.deviceCpuPercent))
        MetricLine("App CPU", MetricsFormat.percent(live?.appCpuPercent))
        MetricLine("Thermal", MetricsFormat.text(live?.thermalStatus))
        MetricLine("Battery", MetricsFormat.percent(live?.batteryLevelPercent?.toDouble()))
        MetricLine("Free storage", MetricsFormat.bytes(live?.freeStorageBytes))
        MetricLine("Model", MetricsFormat.text(live?.modelId))
        MetricLine(
            "Last updated",
            live?.capturedAt?.let { formatTime(it) } ?: MetricsFormat.UNAVAILABLE,
        )
    }
}

@Composable
private fun SummaryCards(summary: StatsSummary) {
    if (summary.totalRequests == 0) {
        MetricsCard("Summary") {
            Text("No requests recorded.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    MaterialTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("Requests", summary.totalRequests.toString(), Modifier.weight(1f))
                SummaryCard(
                    "Success",
                    summary.successRate?.let { MetricsFormat.percent(it * 100.0) }
                        ?: MetricsFormat.UNAVAILABLE,
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("p50", MetricsFormat.millis(summary.medianLatencyMs), Modifier.weight(1f))
                SummaryCard("p95", MetricsFormat.millis(summary.p95LatencyMs), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("Avg TTFT", MetricsFormat.millis(summary.averageTtftMs), Modifier.weight(1f))
                SummaryCard(
                    "Avg decode",
                    MetricsFormat.tokensPerSecond(summary.averageDecodeTps),
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(
                    "Dictations",
                    summary.dictations.toString(),
                    Modifier.weight(1f),
                )
                SummaryCard(
                    "Avg recognition",
                    MetricsFormat.millis(summary.averageRecognitionMs),
                    Modifier.weight(1f),
                )
                SummaryCard(
                    "Avg RTF",
                    MetricsFormat.factor(summary.averageRealTimeFactor),
                    Modifier.weight(1f),
                )
                SummaryCard("Peak memory", MetricsFormat.bytes(summary.peakAppPssBytes), Modifier.weight(1f))
                SummaryCard(
                    "Avg CPU",
                    MetricsFormat.percent(summary.averageDeviceCpuPercent),
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(
                    "Thermal",
                    MetricsFormat.text(summary.latestThermalStatus),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun SparklineCard(title: String, values: List<Double?>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium)
                Text(
                    if (values.any { it != null }) "" else MetricsFormat.UNAVAILABLE,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Sparkline(values = values)
        }
    }
}

@Composable
private fun TraceHistoryRow(row: TraceMetricRow) {
    var expanded by rememberSaveable(row.trace.id) { mutableStateOf(false) }
    val snapshot = remember(row.metrics?.snapshotJson) {
        row.metrics?.snapshotJson?.let(MetricsJson::decode)
    }
    val metrics = row.metrics

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.trace.command, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${formatTime(row.trace.startedAt)} · ${row.trace.outcome ?: "pending"}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    MetricsFormat.millis(metrics?.inferenceLatencyMs),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    MetricLine("Time to first token", MetricsFormat.millis(metrics?.ttftMs))
                    MetricLine("Decode", MetricsFormat.tokensPerSecond(metrics?.decodeTps))
                    MetricLine("Peak app memory", MetricsFormat.bytes(metrics?.peakAppPssBytes))
                    MetricLine("Peak device CPU", MetricsFormat.percent(metrics?.peakDeviceCpuPercent))
                    MetricLine("Thermal", MetricsFormat.text(metrics?.worstThermalStatus))
                    MetricLine("Prompt tokens", MetricsFormat.count(metrics?.promptTokens))
                    MetricLine("Completion tokens", MetricsFormat.count(metrics?.completionTokens))
                    MetricLine("Backend", MetricsFormat.text(snapshot?.inference?.backend))
                    MetricLine("Device model", MetricsFormat.text(snapshot?.device?.deviceModel))
                    MetricLine("Memory used", MetricsFormat.bytes(snapshot?.device?.deviceUsedBytes))
                    MetricLine("App PSS", MetricsFormat.bytes(snapshot?.device?.appPssBytes))
                    snapshot?.speech?.let { speech ->
                        SpeechMetricsContent(speech = speech, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (metrics == null) {
                        Text(
                            "No metrics were recorded for this trace.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp))
