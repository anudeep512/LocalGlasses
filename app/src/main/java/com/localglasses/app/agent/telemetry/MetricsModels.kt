package com.localglasses.app.agent.telemetry

import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One foreground sample. Every value is nullable because Android and OEM sysfs
 * sources are allowed to fail without failing the command or the sample.
 */
@Serializable
data class DeviceSnapshot(
    val capturedAt: Long = System.currentTimeMillis(),
    val deviceTotalBytes: Long? = null,
    val deviceAvailableBytes: Long? = null,
    val deviceUsedBytes: Long? = null,
    val appPssBytes: Long? = null,
    val javaHeapUsedBytes: Long? = null,
    val javaHeapMaxBytes: Long? = null,
    val nativeHeapBytes: Long? = null,
    val lowMemory: Boolean? = null,
    val deviceCpuPercent: Double? = null,
    val appCpuPercent: Double? = null,
    val coreFrequenciesMhz: List<Int?> = emptyList(),
    val threadCount: Int? = null,
    val thermalStatus: String? = null,
    val batteryTempC: Double? = null,
    val batteryLevelPercent: Int? = null,
    val charging: Boolean? = null,
    val freeStorageBytes: Long? = null,
    val deviceModel: String? = null,
    val soc: String? = null,
    val abis: String? = null,
    val androidVersion: String? = null,
    val memoryClassMb: Int? = null,
    val modelId: String? = null,
    val modelFileBytes: Long? = null,
    val backend: String? = null,
    val engineLoaded: Boolean? = null,
)

@Serializable
data class InferenceMetrics(
    val modelId: String? = null,
    val backend: String? = null,
    val engineLoaded: Boolean? = null,
    val engineInitMs: Long? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val ttftMs: Long? = null,
    val prefillTps: Double? = null,
    val decodeTps: Double? = null,
    val inferenceLatencyMs: Long? = null,
)

@Serializable
data class RequestMetrics(
    val capturedAt: Long = System.currentTimeMillis(),
    val device: DeviceSnapshot = DeviceSnapshot(),
    val inference: InferenceMetrics? = null,
    val peakAppPssBytes: Long? = null,
    val peakDeviceCpuPercent: Double? = null,
    val peakAppCpuPercent: Double? = null,
    val worstThermalStatus: String? = null,
    val commandDurationMs: Long? = null,
)

/** Serialization boundary for the encrypted trace database and the trace export. */
object MetricsJson {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    fun encode(metrics: RequestMetrics): String = json.encodeToString(metrics)

    fun decode(raw: String): RequestMetrics? =
        runCatching { json.decodeFromString<RequestMetrics>(raw) }.getOrNull()
}

/** Formatting rules shared by the live blocks, the Stats page, and the export. */
object MetricsFormat {

    const val UNAVAILABLE = "Unavailable"

    fun bytes(value: Long?): String {
        if (value == null) return UNAVAILABLE
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var amount = value.toDouble()
        var unit = 0
        while (amount >= 1024.0 && unit < units.lastIndex) {
            amount /= 1024.0
            unit += 1
        }
        return if (unit == 0) {
            "${value} ${units[unit]}"
        } else {
            String.format(Locale.US, "%.1f %s", amount, units[unit])
        }
    }

    fun percent(value: Double?): String =
        if (value == null) UNAVAILABLE else String.format(Locale.US, "%.1f%%", value)

    fun millis(value: Long?): String =
        if (value == null) UNAVAILABLE else String.format(Locale.US, "%d ms", value)

    fun seconds(value: Double?): String =
        if (value == null) UNAVAILABLE else String.format(Locale.US, "%.2f s", value)

    fun tokensPerSecond(value: Double?): String =
        if (value == null) UNAVAILABLE else String.format(Locale.US, "%.1f tok/s", value)

    fun count(value: Int?): String =
        if (value == null) UNAVAILABLE else value.toString()

    fun celsius(value: Double?): String =
        if (value == null) UNAVAILABLE else String.format(Locale.US, "%.1f °C", value)

    fun text(value: String?): String = value?.takeIf { it.isNotBlank() } ?: UNAVAILABLE
}
