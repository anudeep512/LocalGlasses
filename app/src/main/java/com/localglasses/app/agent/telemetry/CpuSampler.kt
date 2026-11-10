package com.localglasses.app.agent.telemetry

import android.os.Process
import java.io.File

data class CpuTimes(val total: Long, val idle: Long)

data class CpuSample(
    val devicePercent: Double?,
    val appPercent: Double?,
    val coreFrequenciesMhz: List<Int?>,
    val threadCount: Int?,
)

object ProcStatParser {

    fun parseCpuTimes(text: String): CpuTimes? {
        val line = text.lineSequence()
            .firstOrNull { it.startsWith("cpu ") }
            ?: return null
        val values = line.trim()
            .split(Regex("\\s+"))
            .drop(1)
            .mapNotNull { it.toLongOrNull() }
        if (values.size < 4) return null
        val total = values.sum()
        val idle = values[3] + (values.getOrNull(4) ?: 0L)
        return CpuTimes(total = total, idle = idle)
    }

    fun parseThreadCount(text: String): Int? = text.lineSequence()
        .firstOrNull { it.trimStart().startsWith("Threads:") }
        ?.substringAfter(':')
        ?.trim()
        ?.toIntOrNull()
}

class CpuSampler(
    private val readProcStat: () -> String? = {
        runCatching { File("/proc/stat").readText() }.getOrNull()
    },
    private val readStatus: () -> String? = {
        runCatching { File("/proc/self/status").readText() }.getOrNull()
    },
    private val readAppCpuMs: () -> Long? = {
        runCatching { Process.getElapsedCpuTime() }.getOrNull()
    },
    private val readCoreFreqKhz: (Int) -> Long? = { core ->
        runCatching {
            File("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq")
                .readText()
                .trim()
                .toLongOrNull()
        }.getOrNull()
    },
    private val coreCount: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
    private val clockMs: () -> Long = System::currentTimeMillis,
) {

    private var previousCpuTimes: CpuTimes? = null
    private var previousAppCpuMs: Long? = null
    private var previousWallMs: Long? = null

    fun sample(): CpuSample {
        val now = clockMs()
        val cpuTimes = readProcStat()?.let(ProcStatParser::parseCpuTimes)
        val appCpuMs = readAppCpuMs()

        val devicePercent = if (previousCpuTimes != null && cpuTimes != null) {
            val previous = previousCpuTimes!!
            val totalDelta = cpuTimes.total - previous.total
            val idleDelta = cpuTimes.idle - previous.idle
            if (totalDelta > 0L) {
                ((totalDelta - idleDelta).toDouble() / totalDelta * 100.0)
                    .coerceIn(0.0, 100.0)
            } else {
                null
            }
        } else {
            null
        }

        val appPercent = if (
            previousAppCpuMs != null && previousWallMs != null && appCpuMs != null
        ) {
            val cpuDelta = appCpuMs - previousAppCpuMs!!
            val wallDelta = now - previousWallMs!!
            if (wallDelta > 0L && cpuDelta >= 0L) {
                (cpuDelta.toDouble() / wallDelta * 100.0).coerceAtLeast(0.0)
            } else {
                null
            }
        } else {
            null
        }

        previousCpuTimes = cpuTimes
        previousAppCpuMs = appCpuMs
        previousWallMs = now

        val cores = (0 until coreCount).map { core ->
            readCoreFreqKhz(core)?.let { khz -> (khz / 1000L).toInt() }
        }

        return CpuSample(
            devicePercent = devicePercent,
            appPercent = appPercent,
            coreFrequenciesMhz = cores,
            threadCount = readStatus()?.let(ProcStatParser::parseThreadCount),
        )
    }
}
