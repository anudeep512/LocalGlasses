package com.localglasses.app.agent.dictation

import com.localglasses.app.agent.telemetry.SpeechMetrics

/**
 * Builds the metrics block for one dictation.
 *
 * Every field is either measured or absent: nothing is defaulted to zero, and the transcript is
 * reduced to a word count so spoken content never enters the metrics store.
 */
class SpeechMetricsFactory(
    private val modelId: String,
    private val peakPssReader: () -> Long? = { null },
) {

    fun create(
        source: AudioSourceKind,
        endpointReason: EndpointReason,
        audioDurationMs: Long,
        recognitionLatencyMs: Long,
        transcript: String?,
        peakPssBytes: Long? = peakPssReader(),
    ): SpeechMetrics = SpeechMetrics(
        modelId = modelId,
        source = source.name,
        audioDurationMs = audioDurationMs.takeIf { it > 0 },
        recognitionLatencyMs = recognitionLatencyMs.takeIf { it >= 0 },
        realTimeFactor = if (audioDurationMs > 0 && recognitionLatencyMs >= 0) {
            recognitionLatencyMs.toDouble() / audioDurationMs
        } else {
            null
        },
        endpointReason = endpointReason.name,
        peakAppPssBytes = peakPssBytes,
        wordCount = transcript?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
            text.split(WHITESPACE).count { it.isNotBlank() }
        },
    )

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
