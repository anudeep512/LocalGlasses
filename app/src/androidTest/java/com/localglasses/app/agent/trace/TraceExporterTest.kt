package com.localglasses.app.agent.trace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localglasses.app.agent.telemetry.DeviceSnapshot
import com.localglasses.app.agent.telemetry.InferenceMetrics
import com.localglasses.app.agent.telemetry.RequestMetrics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TraceExporterTest {

    private lateinit var context: Context
    private lateinit var database: TraceDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TraceDatabase.DB_NAME)
        database = TraceDatabase(context, ByteArray(32) { 5 })
        database.insertTrace("trace-1", "call Amma", "action", null, "success")
        database.upsertMetrics(
            "trace-1",
            RequestMetrics(
                device = DeviceSnapshot(deviceTotalBytes = 8_000_000_000),
                inference = InferenceMetrics(
                    modelId = "gemma",
                    promptTokens = 10,
                    completionTokens = 5,
                    ttftMs = 300,
                    decodeTps = 12.5,
                    inferenceLatencyMs = 700,
                ),
                peakAppPssBytes = 1_500_000,
                peakDeviceCpuPercent = 42.5,
                worstThermalStatus = "Light",
            ),
        )
        database.insertTrace("trace-2", "blocked command", "blocked", null, "blocked")
    }

    @Test
    fun exportsJsonlAndMarkdownThenPurgesStore() = runBlocking {
        val exporter = TraceExporter(context, database)
        val uris = exporter.export().getOrThrow()

        assertEquals(2, uris.size)
        val jsonl = context.contentResolver.openInputStream(uris[0])?.bufferedReader()?.use {
            it.readText()
        }.orEmpty()
        assertTrue(jsonl.contains("call Amma"))
        assertTrue(jsonl.contains("\"metrics\""))
        assertTrue(jsonl.contains("inferenceLatencyMs"))
        assertTrue(jsonl.contains("\"metrics\":null"))

        val markdown = context.contentResolver.openInputStream(uris[1])?.bufferedReader()?.use {
            it.readText()
        }.orEmpty()
        assertTrue(markdown.contains("inference latency: 700 ms"))
        assertTrue(markdown.contains("decode: 12.5 tok/s"))
        assertTrue(markdown.contains("metrics: unavailable"))

        database.purge()
        assertEquals(0, database.countTraces())
        assertNull(database.metrics("trace-1"))
        assertTrue(
            context.contentResolver.openInputStream(uris[0])?.use { it.readBytes().isNotEmpty() } == true,
        )
    }
}
