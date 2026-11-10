package com.localglasses.app.agent.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager
import android.os.StatFs

data class ModelRuntimeInfo(
    val modelId: String?,
    val modelFileBytes: Long?,
    val backend: String?,
    val engineLoaded: Boolean?,
)

object ThermalLabels {
    fun of(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "None"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
        else -> "Unknown"
    }

    fun rank(label: String?): Int = when (label) {
        "None" -> 0
        "Light" -> 1
        "Moderate" -> 2
        "Severe" -> 3
        "Critical" -> 4
        "Emergency" -> 5
        "Shutdown" -> 6
        else -> -1
    }
}

/**
 * Best-effort Android reads. Individual sources are allowed to fail; the caller gets
 * nulls for those fields and every other metric still lands in the snapshot.
 */
class AndroidMetricsSource(
    context: Context,
    private val deviceSpecs: DeviceSpecs = AndroidDeviceSpecs(context).read(),
    private val modelInfo: () -> ModelRuntimeInfo = { ModelRuntimeInfo(null, null, null, null) },
    private val cpuSampler: CpuSampler = CpuSampler(),
) : MetricsSource {

    private val appContext = context.applicationContext
    private val activityManager = appContext.getSystemService(ActivityManager::class.java)
    private val powerManager = appContext.getSystemService(PowerManager::class.java)

    override fun sample(): DeviceSnapshot {
        val cpu = runCatching { cpuSampler.sample() }.getOrNull()
        val memory = readMemory()
        val battery = readBattery()
        val thermal = runCatching {
            powerManager?.currentThermalStatus?.let(ThermalLabels::of)
        }.getOrNull()
        val freeStorage = runCatching {
            StatFs(appContext.filesDir.absolutePath).availableBytes
        }.getOrNull()
        val model = runCatching { modelInfo() }.getOrNull()

        return DeviceSnapshot(
            capturedAt = System.currentTimeMillis(),
            deviceTotalBytes = memory.deviceTotalBytes,
            deviceAvailableBytes = memory.deviceAvailableBytes,
            deviceUsedBytes = memory.deviceUsedBytes,
            appPssBytes = memory.appPssBytes,
            javaHeapUsedBytes = memory.javaHeapUsedBytes,
            javaHeapMaxBytes = memory.javaHeapMaxBytes,
            nativeHeapBytes = memory.nativeHeapBytes,
            lowMemory = memory.lowMemory,
            deviceCpuPercent = cpu?.devicePercent,
            appCpuPercent = cpu?.appPercent,
            coreFrequenciesMhz = cpu?.coreFrequenciesMhz.orEmpty(),
            threadCount = cpu?.threadCount,
            thermalStatus = thermal,
            batteryTempC = battery.temperatureC,
            batteryLevelPercent = battery.levelPercent,
            charging = battery.charging,
            freeStorageBytes = freeStorage,
            deviceModel = deviceSpecs.deviceModel,
            soc = deviceSpecs.soc,
            abis = deviceSpecs.abis,
            androidVersion = deviceSpecs.androidVersion,
            memoryClassMb = deviceSpecs.memoryClassMb,
            modelId = model?.modelId,
            modelFileBytes = model?.modelFileBytes,
            backend = model?.backend,
            engineLoaded = model?.engineLoaded,
        )
    }

    private data class MemoryValues(
        val deviceTotalBytes: Long? = null,
        val deviceAvailableBytes: Long? = null,
        val deviceUsedBytes: Long? = null,
        val appPssBytes: Long? = null,
        val javaHeapUsedBytes: Long? = null,
        val javaHeapMaxBytes: Long? = null,
        val nativeHeapBytes: Long? = null,
        val lowMemory: Boolean? = null,
    )

    private fun readMemory(): MemoryValues {
        val manager = runCatching {
            activityManager?.let { am ->
                val info = ActivityManager.MemoryInfo()
                am.getMemoryInfo(info)
                Triple(info.totalMem, info.availMem, info.lowMemory)
            }
        }.getOrNull()

        val debug = runCatching {
            Debug.MemoryInfo().also(Debug::getMemoryInfo)
        }.getOrNull()

        val runtime = Runtime.getRuntime()
        val javaUsed = runCatching { runtime.totalMemory() - runtime.freeMemory() }.getOrNull()
        val javaMax = runCatching { runtime.maxMemory() }.getOrNull()
        val native = runCatching { Debug.getNativeHeapAllocatedSize() }.getOrNull()

        return MemoryValues(
            deviceTotalBytes = manager?.first,
            deviceAvailableBytes = manager?.second,
            deviceUsedBytes = if (manager != null) {
                (manager.first - manager.second).coerceAtLeast(0L)
            } else {
                null
            },
            appPssBytes = debug?.totalPss?.let { it * 1024L },
            javaHeapUsedBytes = javaUsed,
            javaHeapMaxBytes = javaMax,
            nativeHeapBytes = native,
            lowMemory = manager?.third,
        )
    }

    private data class BatteryValues(
        val temperatureC: Double? = null,
        val levelPercent: Int? = null,
        val charging: Boolean? = null,
    )

    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun readBattery(): BatteryValues = runCatching {
        val intent: Intent? = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val temperature = intent
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            ?.takeIf { it >= 0 }
            ?.div(10.0)
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val levelPercent = if (level >= 0 && scale > 0) level * 100 / scale else null
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        BatteryValues(
            temperatureC = temperature,
            levelPercent = levelPercent,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                plugged != 0,
        )
    }.getOrElse { BatteryValues() }
}
