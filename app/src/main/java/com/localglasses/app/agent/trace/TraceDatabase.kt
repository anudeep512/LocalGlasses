package com.localglasses.app.agent.trace

import android.content.ContentValues
import android.content.Context
import com.localglasses.app.agent.telemetry.MetricsJson
import com.localglasses.app.agent.telemetry.RequestMetrics
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase

data class TraceRow(
    val id: String,
    val command: String,
    val taskType: String?,
    val targetPackage: String?,
    val outcome: String?,
    val modelPrompt: String?,
    val toolCallsJson: String?,
    val planJson: String?,
    val errorReason: String?,
    val startedAt: Long,
)

data class StepRow(
    val ordinal: Int,
    val stepJson: String?,
    val screenText: String?,
    val outcome: String?,
    val durationMs: Long,
)

data class TraceMetricsRow(
    val traceId: String,
    val capturedAt: Long,
    val inferenceLatencyMs: Long?,
    val ttftMs: Long?,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val prefillTps: Double?,
    val decodeTps: Double?,
    val peakAppPssBytes: Long?,
    val peakDeviceCpuPercent: Double?,
    val worstThermalStatus: String?,
    val snapshotJson: String,
)

data class TraceMetricRow(
    val trace: TraceRow,
    val metrics: TraceMetricsRow?,
)

class TraceDatabase(
    context: Context,
    passphrase: ByteArray? = null,
) : AutoCloseable {

    private val appContext = context.applicationContext
    private val databaseFile: File = appContext.getDatabasePath(DB_NAME)
    private val key: ByteArray = run {
        if (passphrase != null) return@run passphrase
        try {
            TraceKeyManager.passphrase(appContext)
        } catch (error: Exception) {
            databaseFile.delete()
            TraceKeyManager.reset(appContext)
            TraceKeyManager.passphrase(appContext)
        }
    }

    private val db: SQLiteDatabase = try {
        open(key)
    } catch (error: Exception) {
        databaseFile.delete()
        open(key)
    }

    init {
        createSchema()
        db.execSQL("DROP TABLE IF EXISTS aliases")
    }

    private fun open(key: ByteArray): SQLiteDatabase {
        databaseFile.parentFile?.mkdirs()
        System.loadLibrary("sqlcipher")
        return SQLiteDatabase.openOrCreateDatabase(databaseFile, key, null, null)
    }

    private fun createSchema() {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS traces(
                id TEXT PRIMARY KEY,
                started_at INTEGER NOT NULL,
                finished_at INTEGER,
                command TEXT NOT NULL,
                task_type TEXT,
                target_package TEXT,
                outcome TEXT,
                model_prompt TEXT,
                tool_calls_json TEXT,
                plan_json TEXT,
                error_reason TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS trace_steps(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                trace_id TEXT NOT NULL,
                ordinal INTEGER NOT NULL,
                step_json TEXT,
                screen_text TEXT,
                post_condition TEXT,
                outcome TEXT,
                duration_ms INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notifications(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                package TEXT NOT NULL,
                posted_at INTEGER NOT NULL,
                channel TEXT,
                group_key TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS trace_metrics(
                trace_id TEXT PRIMARY KEY,
                captured_at INTEGER NOT NULL,
                inference_latency_ms INTEGER,
                ttft_ms INTEGER,
                prompt_tokens INTEGER,
                completion_tokens INTEGER,
                prefill_tps REAL,
                decode_tps REAL,
                peak_app_pss_bytes INTEGER,
                peak_device_cpu_percent REAL,
                worst_thermal_status TEXT,
                snapshot_json TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }

    fun insertTrace(
        id: String,
        command: String,
        taskType: String?,
        targetPackage: String?,
        outcome: String,
        modelPrompt: String? = null,
        toolCallsJson: String? = null,
        planJson: String? = null,
        errorReason: String? = null,
    ) {
        val values = ContentValues().apply {
            put("id", id)
            put("started_at", System.currentTimeMillis())
            put("command", command)
            put("task_type", taskType)
            put("target_package", targetPackage)
            put("outcome", outcome)
            put("model_prompt", modelPrompt)
            put("tool_calls_json", toolCallsJson)
            put("plan_json", planJson)
            put("error_reason", errorReason)
        }
        db.insertWithOnConflict("traces", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateOutcome(id: String, outcome: String, errorReason: String?) {
        val values = ContentValues().apply {
            put("finished_at", System.currentTimeMillis())
            put("outcome", outcome)
            put("error_reason", errorReason)
        }
        db.update("traces", values, "id = ?", arrayOf(id))
    }

    fun insertStep(
        traceId: String,
        ordinal: Int,
        stepJson: String?,
        screenText: String?,
        postCondition: String?,
        outcome: String,
        durationMs: Long,
    ) {
        val values = ContentValues().apply {
            put("trace_id", traceId)
            put("ordinal", ordinal)
            put("step_json", stepJson)
            put("screen_text", screenText)
            put("post_condition", postCondition)
            put("outcome", outcome)
            put("duration_ms", durationMs)
        }
        db.insert("trace_steps", null, values)
    }

    fun upsertMetrics(traceId: String, metrics: RequestMetrics) {
        val inference = metrics.inference
        val values = ContentValues().apply {
            put("trace_id", traceId)
            put("captured_at", metrics.capturedAt)
            put("inference_latency_ms", inference?.inferenceLatencyMs)
            put("ttft_ms", inference?.ttftMs)
            put("prompt_tokens", inference?.promptTokens)
            put("completion_tokens", inference?.completionTokens)
            put("prefill_tps", inference?.prefillTps)
            put("decode_tps", inference?.decodeTps)
            put("peak_app_pss_bytes", metrics.peakAppPssBytes)
            put("peak_device_cpu_percent", metrics.peakDeviceCpuPercent)
            put("worst_thermal_status", metrics.worstThermalStatus)
            put("snapshot_json", MetricsJson.encode(metrics))
        }
        db.insertWithOnConflict("trace_metrics", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun metrics(traceId: String): TraceMetricsRow? =
        db.query(
            "trace_metrics",
            null,
            "trace_id = ?",
            arrayOf(traceId),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toMetricsRow() else null
        }

    fun metricsSince(since: Long): List<TraceMetricRow> {
        val sql = """
            SELECT t.*,
                   m.trace_id AS metric_trace_id,
                   m.captured_at AS metric_captured_at,
                   m.inference_latency_ms AS metric_inference_latency_ms,
                   m.ttft_ms AS metric_ttft_ms,
                   m.prompt_tokens AS metric_prompt_tokens,
                   m.completion_tokens AS metric_completion_tokens,
                   m.prefill_tps AS metric_prefill_tps,
                   m.decode_tps AS metric_decode_tps,
                   m.peak_app_pss_bytes AS metric_peak_app_pss_bytes,
                   m.peak_device_cpu_percent AS metric_peak_device_cpu_percent,
                   m.worst_thermal_status AS metric_worst_thermal_status,
                   m.snapshot_json AS metric_snapshot_json
            FROM traces t
            LEFT JOIN trace_metrics m ON m.trace_id = t.id
            WHERE t.started_at >= ?
            ORDER BY t.started_at DESC
        """.trimIndent()
        return db.rawQuery(sql, arrayOf(since.toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        TraceMetricRow(
                            trace = cursor.toTraceRow(),
                            metrics = if (cursor.isNull(cursor.getColumnIndexOrThrow("metric_trace_id"))) {
                                null
                            } else {
                                cursor.toMetricsRow("metric_")
                            },
                        ),
                    )
                }
            }
        }
    }

    private fun android.database.Cursor.toTraceRow(): TraceRow = TraceRow(
        id = getString(getColumnIndexOrThrow("id")),
        command = getString(getColumnIndexOrThrow("command")),
        taskType = getString(getColumnIndexOrThrow("task_type")),
        targetPackage = getString(getColumnIndexOrThrow("target_package")),
        outcome = getString(getColumnIndexOrThrow("outcome")),
        modelPrompt = getString(getColumnIndexOrThrow("model_prompt")),
        toolCallsJson = getString(getColumnIndexOrThrow("tool_calls_json")),
        planJson = getString(getColumnIndexOrThrow("plan_json")),
        errorReason = getString(getColumnIndexOrThrow("error_reason")),
        startedAt = getLong(getColumnIndexOrThrow("started_at")),
    )

    private fun android.database.Cursor.toMetricsRow(prefix: String = ""): TraceMetricsRow =
        TraceMetricsRow(
            traceId = getString(getColumnIndexOrThrow("${prefix}trace_id")),
            capturedAt = getLong(getColumnIndexOrThrow("${prefix}captured_at")),
            inferenceLatencyMs = getNullableLong("${prefix}inference_latency_ms"),
            ttftMs = getNullableLong("${prefix}ttft_ms"),
            promptTokens = getNullableInt("${prefix}prompt_tokens"),
            completionTokens = getNullableInt("${prefix}completion_tokens"),
            prefillTps = getNullableDouble("${prefix}prefill_tps"),
            decodeTps = getNullableDouble("${prefix}decode_tps"),
            peakAppPssBytes = getNullableLong("${prefix}peak_app_pss_bytes"),
            peakDeviceCpuPercent = getNullableDouble("${prefix}peak_device_cpu_percent"),
            worstThermalStatus = getStringOrNull("${prefix}worst_thermal_status"),
            snapshotJson = getString(getColumnIndexOrThrow("${prefix}snapshot_json")),
        )

    private fun android.database.Cursor.getNullableLong(name: String): Long? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getLong(index)
    }

    private fun android.database.Cursor.getNullableInt(name: String): Int? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getInt(index)
    }

    private fun android.database.Cursor.getNullableDouble(name: String): Double? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getDouble(index)
    }

    private fun android.database.Cursor.getStringOrNull(name: String): String? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getString(index)
    }

    fun recordNotification(packageName: String, postedAt: Long, channel: String?, groupKey: String?) {
        val values = ContentValues().apply {
            put("package", packageName)
            put("posted_at", postedAt)
            put("channel", channel)
            put("group_key", groupKey)
        }
        db.insert("notifications", null, values)
    }

    fun traces(): List<TraceRow> =
        db.query("traces", null, null, null, null, null, "started_at ASC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        TraceRow(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            command = cursor.getString(cursor.getColumnIndexOrThrow("command")),
                            taskType = cursor.getString(cursor.getColumnIndexOrThrow("task_type")),
                            targetPackage = cursor.getString(cursor.getColumnIndexOrThrow("target_package")),
                            outcome = cursor.getString(cursor.getColumnIndexOrThrow("outcome")),
                            modelPrompt = cursor.getString(cursor.getColumnIndexOrThrow("model_prompt")),
                            toolCallsJson = cursor.getString(cursor.getColumnIndexOrThrow("tool_calls_json")),
                            planJson = cursor.getString(cursor.getColumnIndexOrThrow("plan_json")),
                            errorReason = cursor.getString(cursor.getColumnIndexOrThrow("error_reason")),
                            startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
                        ),
                    )
                }
            }
        }

    fun steps(traceId: String): List<StepRow> =
        db.query(
            "trace_steps",
            null,
            "trace_id = ?",
            arrayOf(traceId),
            null,
            null,
            "ordinal ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        StepRow(
                            ordinal = cursor.getInt(cursor.getColumnIndexOrThrow("ordinal")),
                            stepJson = cursor.getString(cursor.getColumnIndexOrThrow("step_json")),
                            screenText = cursor.getString(cursor.getColumnIndexOrThrow("screen_text")),
                            outcome = cursor.getString(cursor.getColumnIndexOrThrow("outcome")),
                            durationMs = cursor.getLong(cursor.getColumnIndexOrThrow("duration_ms")),
                        ),
                    )
                }
            }
        }

    fun countTraces(): Long =
        db.rawQuery("SELECT COUNT(*) FROM traces", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }

    fun countNotifications(): Long =
        db.rawQuery("SELECT COUNT(*) FROM notifications", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }

    fun purge() {
        db.delete("trace_metrics", null, null)
        db.delete("trace_steps", null, null)
        db.delete("traces", null, null)
        db.delete("notifications", null, null)
        db.execSQL("VACUUM")
    }

    override fun close() {
        db.close()
    }

    companion object {
        const val DB_NAME = "agent_trace.db"
    }
}
