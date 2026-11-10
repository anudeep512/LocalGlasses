package com.localglasses.app.agent.trace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localglasses.app.agent.telemetry.DeviceSnapshot
import com.localglasses.app.agent.telemetry.InferenceMetrics
import com.localglasses.app.agent.telemetry.RequestMetrics
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TraceDatabaseTest {

    private lateinit var context: Context
    private val key = ByteArray(32) { 7 }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TraceDatabase.DB_NAME)
    }

    @Test
    fun storesTracesInAnEncryptedFile() {
        TraceDatabase(context, key).use { db ->
            db.insertTrace("trace-1", "call Amma", "action", null, "done")
            assertEquals(1, db.countTraces())
        }

        val file = context.getDatabasePath(TraceDatabase.DB_NAME)
        assertTrue(file.isFile)
        val header = file.inputStream().use { it.readNBytes(16).toString(Charsets.US_ASCII) }
        assertFalse(header.startsWith("SQLite format 3"))
    }

    @Test
    fun wrongKeyCannotReadTheDatabase() {
        TraceDatabase(context, key).use { db ->
            db.insertTrace("trace-1", "call Amma", "action", null, "done")
        }

        val file = context.getDatabasePath(TraceDatabase.DB_NAME)
        var failed = false
        try {
            SQLiteDatabase.openOrCreateDatabase(file, ByteArray(32) { 9 }, null, null).use { wrong ->
                wrong.rawQuery("SELECT COUNT(*) FROM traces", null).use { it.moveToFirst() }
            }
        } catch (error: Exception) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun purgeRemovesTraces() {
        TraceDatabase(context, key).use { db ->
            db.insertTrace("trace-1", "call Amma", "action", null, "done")
            db.upsertMetrics("trace-1", metrics(10))
            db.purge()
            assertEquals(0, db.countTraces())
            assertNull(db.metrics("trace-1"))
        }
    }

    @Test
    fun metricsRoundTripAndUpsertOneRowPerTrace() {
        TraceDatabase(context, key).use { db ->
            db.insertTrace("trace-1", "call Amma", "action", null, "done")
            db.upsertMetrics("trace-1", metrics(10))
            db.upsertMetrics("trace-1", metrics(20))

            assertEquals(20L, db.metrics("trace-1")!!.inferenceLatencyMs)
            assertEquals(1, db.metricsSince(0).size)
            assertEquals(3, db.metricsSince(0).first().metrics!!.promptTokens)
        }

        TraceDatabase(context, key).use { db ->
            assertEquals(20L, db.metrics("trace-1")!!.inferenceLatencyMs)
        }
    }

    @Test
    fun additiveMigrationKeepsExistingTraceAndSteps() {
        val file = context.getDatabasePath(TraceDatabase.DB_NAME)
        file.parentFile?.mkdirs()
        System.loadLibrary("sqlcipher")
        SQLiteDatabase.openOrCreateDatabase(file, key, null, null).use { old ->
            old.execSQL(
                """CREATE TABLE traces(
                    id TEXT PRIMARY KEY, started_at INTEGER NOT NULL, finished_at INTEGER,
                    command TEXT NOT NULL, task_type TEXT, target_package TEXT, outcome TEXT,
                    model_prompt TEXT, tool_calls_json TEXT, plan_json TEXT, error_reason TEXT)""",
            )
            old.execSQL(
                """CREATE TABLE trace_steps(
                    id INTEGER PRIMARY KEY AUTOINCREMENT, trace_id TEXT NOT NULL,
                    ordinal INTEGER NOT NULL, step_json TEXT, screen_text TEXT,
                    post_condition TEXT, outcome TEXT, duration_ms INTEGER)""",
            )
            old.execSQL("INSERT INTO traces(id, started_at, command, outcome) VALUES('old', 1, 'call Amma', 'success')")
            old.execSQL("INSERT INTO trace_steps(trace_id, ordinal, outcome, duration_ms) VALUES('old', 0, 'success', 5)")
        }

        TraceDatabase(context, key).use { db ->
            assertEquals(1, db.countTraces())
            assertEquals(1, db.steps("old").size)
            assertNull(db.metrics("old"))
        }
    }

    private fun metrics(latencyMs: Long) = RequestMetrics(
        device = DeviceSnapshot(deviceTotalBytes = 8_000_000_000, appPssBytes = 42),
        inference = InferenceMetrics(
            modelId = "gemma",
            promptTokens = 3,
            completionTokens = 4,
            inferenceLatencyMs = latencyMs,
        ),
        peakAppPssBytes = 64,
        peakDeviceCpuPercent = 12.0,
        worstThermalStatus = "Light",
    )

    @Test
    fun corruptFileRecovers() {
        val file = context.getDatabasePath(TraceDatabase.DB_NAME)
        file.parentFile?.mkdirs()
        file.writeText("not a database")

        TraceDatabase(context, key).use { db ->
            assertEquals(0, db.countTraces())
        }
    }
}
