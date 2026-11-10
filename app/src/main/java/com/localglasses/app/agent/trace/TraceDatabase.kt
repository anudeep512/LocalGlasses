package com.localglasses.app.agent.trace

import android.content.ContentValues
import android.content.Context
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

data class ContactAlias(
    val alias: String,
    val contactId: String,
    val displayName: String,
    val phone: String,
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
            CREATE TABLE IF NOT EXISTS aliases(
                alias TEXT PRIMARY KEY,
                contact_id TEXT NOT NULL,
                display_name TEXT NOT NULL,
                phone_e164 TEXT NOT NULL
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

    fun recordNotification(packageName: String, postedAt: Long, channel: String?, groupKey: String?) {
        val values = ContentValues().apply {
            put("package", packageName)
            put("posted_at", postedAt)
            put("channel", channel)
            put("group_key", groupKey)
        }
        db.insert("notifications", null, values)
    }

    fun upsertAlias(alias: ContactAlias) {
        val values = ContentValues().apply {
            put("alias", alias.alias)
            put("contact_id", alias.contactId)
            put("display_name", alias.displayName)
            put("phone_e164", alias.phone)
        }
        db.insertWithOnConflict("aliases", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun alias(name: String): ContactAlias? =
        db.query(
            "aliases",
            null,
            "alias = ? COLLATE NOCASE",
            arrayOf(name),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            ContactAlias(
                alias = cursor.getString(cursor.getColumnIndexOrThrow("alias")),
                contactId = cursor.getString(cursor.getColumnIndexOrThrow("contact_id")),
                displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
                phone = cursor.getString(cursor.getColumnIndexOrThrow("phone_e164")),
            )
        }

    fun aliases(): List<ContactAlias> =
        db.query("aliases", null, null, null, null, null, "alias COLLATE NOCASE").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ContactAlias(
                            alias = cursor.getString(cursor.getColumnIndexOrThrow("alias")),
                            contactId = cursor.getString(cursor.getColumnIndexOrThrow("contact_id")),
                            displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
                            phone = cursor.getString(cursor.getColumnIndexOrThrow("phone_e164")),
                        ),
                    )
                }
            }
        }

    fun deleteAlias(name: String) {
        db.delete("aliases", "alias = ? COLLATE NOCASE", arrayOf(name))
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

    fun purge() {
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
