package com.localglasses.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localglasses.app.agent.telemetry.DeviceSnapshot
import com.localglasses.app.agent.telemetry.MetricsFormat
import com.localglasses.app.agent.telemetry.RequestMetrics
import com.localglasses.app.agent.telemetry.SpeechMetrics

/**
 * Request metrics, grouped by what produced them.
 *
 * The split keeps the four sources of a number distinguishable: the language model, the speech
 * recogniser, this app's own process, and the device. A flat list made an app figure look like a
 * model figure.
 */
@Composable
fun RequestMetricsContent(
    live: DeviceSnapshot?,
    last: RequestMetrics?,
    isSubmitting: Boolean,
    modifier: Modifier = Modifier,
    speech: SpeechMetrics? = last?.speech,
) {
    val device = live ?: last?.device
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

        MetricsSection("LLM Inference Metrics", first = true) {
            MetricLine("Model", MetricsFormat.text(inference?.modelId ?: device?.modelId))
            MetricLine("Backend", MetricsFormat.text(inference?.backend ?: device?.backend))
            MetricLine("Engine loaded", yesNo(inference?.engineLoaded ?: device?.engineLoaded))
            MetricLine("Engine init", MetricsFormat.millis(inference?.engineInitMs))
            MetricLine("Latency", MetricsFormat.millis(inference?.inferenceLatencyMs))
            MetricLine("Time to first token", MetricsFormat.millis(inference?.ttftMs))
            MetricLine("Prompt tokens", MetricsFormat.count(inference?.promptTokens))
            MetricLine("Completion tokens", MetricsFormat.count(inference?.completionTokens))
            MetricLine("Prefill", MetricsFormat.tokensPerSecond(inference?.prefillTps))
            MetricLine("Decode", MetricsFormat.tokensPerSecond(inference?.decodeTps))
        }

        // Always present, like the inference section: absent values read as Unavailable so
        // the numbers appear the moment the first utterance is processed.
        MetricsSection("Speech Recognition") {
            SpeechMetricsLines(speech)
        }

        MetricsSection("App") {
            MetricLine("App PSS", MetricsFormat.bytes(device?.appPssBytes))
            MetricLine("App CPU", MetricsFormat.percent(device?.appCpuPercent))
            MetricLine("Peak app memory", MetricsFormat.bytes(last?.peakAppPssBytes))
            MetricLine("Threads", MetricsFormat.count(device?.threadCount))
            MetricLine("Java heap", MetricsFormat.bytes(device?.javaHeapUsedBytes))
            MetricLine("Native heap", MetricsFormat.bytes(device?.nativeHeapBytes))
        }

        MetricsSection("Device Live") {
            MetricLine("Memory used", MetricsFormat.bytes(device?.deviceUsedBytes))
            MetricLine("Available", MetricsFormat.bytes(device?.deviceAvailableBytes))
            MetricLine("Device CPU", MetricsFormat.percent(device?.deviceCpuPercent))
            MetricLine("Thermal", MetricsFormat.text(device?.thermalStatus))
            MetricLine("Battery", MetricsFormat.percent(device?.batteryLevelPercent?.toDouble()))
            MetricLine("Free storage", MetricsFormat.bytes(device?.freeStorageBytes))
        }
    }
}

@Composable
private fun MetricsSection(
    title: String,
    first: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!first) {
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
    }
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = if (first) 8.dp else 0.dp, bottom = 2.dp),
    )
    content()
}

/** Speech recognition metrics for a dictated command. */
@Composable
fun SpeechMetricsContent(speech: SpeechMetrics?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("Speech Recognition", style = MaterialTheme.typography.titleSmall)
        SpeechMetricsLines(speech)
    }
}

@Composable
private fun SpeechMetricsLines(speech: SpeechMetrics?) {
    MetricLine("Speech model", MetricsFormat.text(speech?.modelId))
    MetricLine("Audio source", MetricsFormat.text(speech?.source))
    MetricLine("Audio", MetricsFormat.millis(speech?.audioDurationMs))
    MetricLine("Recognition", MetricsFormat.millis(speech?.recognitionLatencyMs))
    MetricLine("Real-time factor", MetricsFormat.factor(speech?.realTimeFactor))
    MetricLine("Endpoint", MetricsFormat.text(speech?.endpointReason))
    MetricLine("Words", MetricsFormat.count(speech?.wordCount))
    MetricLine("Peak app memory", MetricsFormat.bytes(speech?.peakAppPssBytes))
}

private fun yesNo(value: Boolean?): String = when (value) {
    null -> MetricsFormat.UNAVAILABLE
    true -> "Yes"
    false -> "No"
}
