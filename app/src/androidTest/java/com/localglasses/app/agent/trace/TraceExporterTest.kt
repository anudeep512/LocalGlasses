package com.localglasses.app.agent.trace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

        database.purge()
        assertEquals(0, database.countTraces())
        assertTrue(
            context.contentResolver.openInputStream(uris[0])?.use { it.readBytes().isNotEmpty() } == true,
        )
    }
}
