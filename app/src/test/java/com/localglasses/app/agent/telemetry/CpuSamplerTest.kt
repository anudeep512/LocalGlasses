package com.localglasses.app.agent.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CpuSamplerTest {

    @Test
    fun parsesCpuTimesAndThreads() {
        val times = ProcStatParser.parseCpuTimes(
            """
            cpu  100 0 20 40 10 0 0 0 0 0
            cpu0 50 0 10 20 5 0 0 0 0 0
            """.trimIndent(),
        )

        assertEquals(CpuTimes(total = 170, idle = 50), times)
        assertEquals(42, ProcStatParser.parseThreadCount("Name:\tapp\nThreads:\t42\n"))
        assertNull(ProcStatParser.parseThreadCount("Name:\tapp\n"))
    }

    @Test
    fun firstSampleHasNullPercentages() {
        val sampler = sampler(
            proc = "cpu 100 0 20 40 10 0 0 0 0 0",
            appCpuMs = 100,
            wallMs = 1_000,
            cores = listOf(1_800_000L),
        )

        val sample = sampler.sample()

        assertNull(sample.devicePercent)
        assertNull(sample.appPercent)
        assertEquals(listOf(1_800), sample.coreFrequenciesMhz)
    }

    @Test
    fun knownDeltasProducePercentages() {
        var wall = 1_000L
        val sampler = sampler(
            proc = { if (wall == 1_000L) "cpu 100 0 20 40 10" else "cpu 200 0 40 60 10" },
            appCpuMs = { if (wall == 1_000L) 100 else 300 },
            wallMs = { wall },
            cores = listOf(1_800_000L),
        )
        sampler.sample()

        wall = 2_000L
        val sample = sampler.sample()

        assertEquals(120.0 / 140.0 * 100.0, sample.devicePercent!!, 0.001)
        assertEquals(20.0, sample.appPercent!!, 0.001)
    }

    @Test
    fun missingCoreAndThreadCountAreNull() {
        val sampler = sampler(
            proc = "cpu 100 0 20 40 10",
            appCpuMs = 100,
            wallMs = 1_000,
            cores = listOf(1_800_000L, null),
            status = "Name:\tapp\n",
        )

        val sample = sampler.sample()

        assertEquals(listOf(1_800, null), sample.coreFrequenciesMhz)
        assertNull(sample.threadCount)
    }

    private fun sampler(
        proc: String,
        appCpuMs: Long,
        wallMs: Long,
        cores: List<Long?>,
        status: String? = "Threads:\t7\n",
    ): CpuSampler = CpuSampler(
        readProcStat = { proc },
        readStatus = { status },
        readAppCpuMs = { appCpuMs },
        readCoreFreqKhz = { core -> cores.getOrNull(core) },
        coreCount = cores.size,
        clockMs = { wallMs },
    )

    private fun sampler(
        proc: () -> String,
        appCpuMs: () -> Long,
        wallMs: () -> Long,
        cores: List<Long?>,
    ): CpuSampler = CpuSampler(
        readProcStat = { proc() },
        readStatus = { "Threads:\t7\n" },
        readAppCpuMs = { appCpuMs() },
        readCoreFreqKhz = { core -> cores.getOrNull(core) },
        coreCount = cores.size,
        clockMs = { wallMs() },
    )
}
