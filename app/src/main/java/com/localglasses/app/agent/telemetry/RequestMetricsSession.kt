package com.localglasses.app.agent.telemetry

/** One request's observed peak values. Peaks stay null when no sample was seen. */
class RequestMetricsSession(
    private val baseline: DeviceSnapshot,
    private val startedAtMs: Long = System.currentTimeMillis(),
) {

    private var latest: DeviceSnapshot = baseline
    private var peakAppPssBytes: Long? = null
    private var peakDeviceCpuPercent: Double? = null
    private var peakAppCpuPercent: Double? = null
    private var worstThermalStatus: String? = null

    fun observe(snapshot: DeviceSnapshot) {
        latest = snapshot
        peakAppPssBytes = maxNullable(peakAppPssBytes, snapshot.appPssBytes)
        peakDeviceCpuPercent = maxNullable(peakDeviceCpuPercent, snapshot.deviceCpuPercent)
        peakAppCpuPercent = maxNullable(peakAppCpuPercent, snapshot.appCpuPercent)
        if (ThermalLabels.rank(snapshot.thermalStatus) > ThermalLabels.rank(worstThermalStatus)) {
            worstThermalStatus = snapshot.thermalStatus
        }
    }

    fun finish(
        inference: InferenceMetrics?,
        durationMs: Long = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L),
    ): RequestMetrics = RequestMetrics(
        device = latest,
        inference = inference,
        peakAppPssBytes = peakAppPssBytes,
        peakDeviceCpuPercent = peakDeviceCpuPercent,
        peakAppCpuPercent = peakAppCpuPercent,
        worstThermalStatus = worstThermalStatus,
        commandDurationMs = durationMs,
    )

    private fun maxNullable(left: Long?, right: Long?): Long? = when {
        left == null -> right
        right == null -> left
        else -> kotlin.math.max(left, right)
    }

    private fun maxNullable(left: Double?, right: Double?): Double? = when {
        left == null -> right
        right == null -> left
        else -> kotlin.math.max(left, right)
    }
}
