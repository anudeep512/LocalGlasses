package com.localglasses.app.agent.model

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.benchmark
import com.localglasses.app.LocalGlassesApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelBenchmarkTest {

    private val prompts = listOf(
        "call 7396512369",
        "send an SMS to 7396512369 saying hello",
        "send a WhatsApp message to 7396512369 saying hello",
        "play Kanye West on YouTube",
        "change the screen timeout to 60 seconds",
        "set brightness to 40 percent",
        "open WhatsApp",
    )

    @OptIn(ExperimentalApi::class)
    @Test
    fun benchmarksCpuAndGpuBackends() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as LocalGlassesApp
        val model = app.agentContainer.modelRepository.modelFile
        assumeTrue("model not downloaded", model.isFile)

        val cpu = runBackend(model, Backend.CPU(), "CPU", context.cacheDir)
        assertTrue(cpu.isNotEmpty())
        assertTrue(cpu.all { it.successes == it.runs })

        val cpuTokens = runCatching {
            benchmark(
                model.absolutePath,
                Backend.CPU(),
                null,
                null,
                2048,
                1,
                context.cacheDir.absolutePath,
                Contents.of("Call 7396512369"),
            )
        }.onFailure { Log.w("ModelBenchmark", "CPU token benchmark failed: ${it.message}") }
        cpuTokens.getOrNull()?.let {
            Log.i(
                "ModelBenchmark",
                "CPU TOKENS prefill=${it.lastPrefillTokensPerSecond} " +
                    "decode=${it.lastDecodeTokensPerSecond} ttft=${it.timeToFirstTokenInSecond}",
            )
        }

        runCatching { runBackend(model, Backend.GPU(), "GPU", context.cacheDir) }
            .onFailure { Log.w("ModelBenchmark", "GPU command benchmark unavailable: ${it.message}") }
        val gpuTokens = runCatching {
            benchmark(
                model.absolutePath,
                Backend.GPU(),
                null,
                null,
                2048,
                1,
                context.cacheDir.absolutePath,
                Contents.of("Call 7396512369"),
            )
        }.onFailure { Log.w("ModelBenchmark", "GPU token benchmark unavailable: ${it.message}") }
        gpuTokens.getOrNull()?.let {
            Log.i(
                "ModelBenchmark",
                "GPU TOKENS prefill=${it.lastPrefillTokensPerSecond} " +
                    "decode=${it.lastDecodeTokensPerSecond} ttft=${it.timeToFirstTokenInSecond}",
            )
        }
    }

    private suspend fun runBackend(
        model: java.io.File,
        backend: Backend,
        label: String,
        cacheDir: java.io.File,
    ): List<BenchmarkSummary> {
        val engine = LiteRtEngine(model, cacheDir, backend)
        engine.load()
        return try {
            val summaries = ModelBenchmarker(engine).run(prompts, repetitions = 2) { run ->
                Log.i(
                    "ModelBenchmark",
                    "$label ${run.prompt} #${run.repetition} ${run.latencyMs}ms " +
                        "prefill=${run.prefillTokensPerSecond} decode=${run.decodeTokensPerSecond}",
                )
            }
            summaries.forEach { summary ->
                Log.i(
                    "ModelBenchmark",
                    "$label SUMMARY ${summary.prompt} median=${summary.medianMs}ms " +
                        "min=${summary.minMs} max=${summary.maxMs} " +
                        "prefill=${summary.prefillTokensPerSecond} " +
                        "decode=${summary.decodeTokensPerSecond}",
                )
            }
            summaries
        } finally {
            engine.close()
        }
    }
}
