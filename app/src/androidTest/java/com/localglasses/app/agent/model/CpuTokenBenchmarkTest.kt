package com.localglasses.app.agent.model

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.benchmark
import com.localglasses.app.LocalGlassesApp
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CpuTokenBenchmarkTest {

    @OptIn(ExperimentalApi::class)
    @Test
    fun cpuTokenThroughput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as LocalGlassesApp
        val model = app.agentContainer.modelRepository.modelFile
        assumeTrue("model not downloaded", model.isFile)

        val info = benchmark(
            model.absolutePath,
            Backend.CPU(),
            null,
            null,
            2048,
            1,
            context.cacheDir.absolutePath,
            Contents.of("Call 7396512369"),
        )

        Log.i(
            "ModelBenchmark",
            "CPU TOKENS prefill=${info.lastPrefillTokensPerSecond} " +
                "decode=${info.lastDecodeTokensPerSecond} ttft=${info.timeToFirstTokenInSecond}",
        )
        assertTrue(info.lastDecodeTokensPerSecond > 0.0)
    }
}
