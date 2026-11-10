package com.localglasses.app.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localglasses.app.agent.telemetry.DeviceSnapshot
import com.localglasses.app.agent.telemetry.MetricsFormat
import com.localglasses.app.agent.telemetry.RequestMetrics

@Composable
fun StatsForNerdsBlock(
    snapshot: DeviceSnapshot?,
    live: DeviceSnapshot?,
    last: RequestMetrics?,
    isSubmitting: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isSubmitting) {
        if (isSubmitting) expanded = true
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Stats for nerds", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (expanded) "Hide" else "Show",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    RequestMetricsContent(
                        live = live,
                        last = last,
                        isSubmitting = isSubmitting,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text("Device and model", style = MaterialTheme.typography.titleSmall)
                    MetricLine("Device model", MetricsFormat.text(snapshot?.deviceModel))
                    MetricLine("System on chip", MetricsFormat.text(snapshot?.soc))
                    MetricLine("ABIs", MetricsFormat.text(snapshot?.abis))
                    MetricLine("Android", MetricsFormat.text(snapshot?.androidVersion))
                    MetricLine(
                        "Memory class",
                        snapshot?.memoryClassMb?.let { "$it MB" } ?: MetricsFormat.UNAVAILABLE,
                    )
                    MetricLine("Model", MetricsFormat.text(snapshot?.modelId))
                    MetricLine("Model file", MetricsFormat.bytes(snapshot?.modelFileBytes))
                    MetricLine("Backend", MetricsFormat.text(snapshot?.backend))
                    MetricLine(
                        "Engine loaded",
                        snapshot?.engineLoaded?.let { if (it) "Yes" else "No" }
                            ?: MetricsFormat.UNAVAILABLE,
                    )
                    MetricLine("GPU", "No public Android API available")
                }
            }
        }
    }
}
