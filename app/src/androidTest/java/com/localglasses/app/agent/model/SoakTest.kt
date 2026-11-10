package com.localglasses.app.agent.model

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.localglasses.app.LocalGlassesApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoakTest {

    private val prompts = listOf(
        "call 7396512369",
        "send an SMS to 7396512369 saying hello",
        "send a WhatsApp message to 7396512369 saying hello",
        "play Kanye West on YouTube",
        "change the screen timeout to 60 seconds",
        "set brightness to 40 percent",
        "open WhatsApp",
    )

    @Test
    fun twentyOneCommandsWithAppSwitchingAndReload() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as LocalGlassesApp
        val model = app.agentContainer.modelRepository.modelFile
        assumeTrue("model not downloaded", model.isFile)

        var engine = LiteRtEngine(model, context.cacheDir, Backend.CPU())
        engine.load()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            repeat(3) { batch ->
                ModelBenchmarker(engine).run(prompts, repetitions = 1)

                shell(instrumentation, "monkey -p com.whatsapp 1")
                Thread.sleep(1200)
                shell(instrumentation, "monkey -p com.google.android.youtube 1")
                Thread.sleep(1200)
                shell(instrumentation, "am start -n com.localglasses.app/.MainActivity")
                Thread.sleep(1000)

                val memory = ActivityManager.MemoryInfo()
                context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
                val power = context.getSystemService(PowerManager::class.java)
                val battery = context.getSystemService(BatteryManager::class.java)
                Log.i(
                    "Soak",
                    "batch=$batch commands=${(batch + 1) * prompts.size} " +
                        "avail=${memory.availMem / 1_000_000}MB low=${memory.lowMemory} " +
                        "thermal=${power.currentThermalStatus} " +
                        "battery=${battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%",
                )

                if (batch == 1) {
                    engine.close()
                    engine = LiteRtEngine(model, context.cacheDir, Backend.CPU())
                    engine.load()
                    Log.i("Soak", "reloaded engine after batch $batch")
                }
            }
            assertTrue(true)
        } finally {
            engine.close()
        }
    }

    private fun shell(instrumentation: android.app.Instrumentation, command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
    }
}
