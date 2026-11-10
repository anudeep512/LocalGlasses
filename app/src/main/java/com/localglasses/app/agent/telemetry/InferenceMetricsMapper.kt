package com.localglasses.app.agent.telemetry

import com.google.ai.edge.litertlm.BenchmarkInfo
import kotlin.math.roundToLong

object InferenceMetricsMapper {

    fun fromBenchmark(
        benchmark: BenchmarkInfo?,
        modelId: String?,
        backend: String?,
        engineLoaded: Boolean?,
        inferenceLatencyMs: Long?,
    ): InferenceMetrics = InferenceMetrics(
        modelId = modelId,
        backend = backend,
        engineLoaded = engineLoaded,
        engineInitMs = benchmark?.initTimeInSecond?.let(::secondsToMillis),
        promptTokens = benchmark?.lastPrefillTokenCount,
        completionTokens = benchmark?.lastDecodeTokenCount,
        ttftMs = benchmark?.timeToFirstTokenInSecond?.let(::secondsToMillis),
        prefillTps = benchmark?.lastPrefillTokensPerSecond,
        decodeTps = benchmark?.lastDecodeTokensPerSecond,
        inferenceLatencyMs = inferenceLatencyMs,
    )

    fun secondsToMillis(seconds: Double): Long = (seconds * 1_000.0).roundToLong()
}
