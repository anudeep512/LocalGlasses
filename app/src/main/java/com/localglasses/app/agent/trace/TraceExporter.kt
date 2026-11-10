package com.localglasses.app.agent.trace

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class TraceExporter(
    private val context: Context,
    private val database: TraceDatabase,
) {

    suspend fun export(): Result<List<Uri>> = withContext(Dispatchers.IO) {
        runCatching {
            val traces = database.traces()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val jsonl = buildString {
                traces.forEach { trace ->
                    appendLine(
                        JSONObject()
                            .put("id", trace.id)
                            .put("command", trace.command)
                            .put("taskType", trace.taskType)
                            .put("targetPackage", trace.targetPackage)
                            .put("outcome", trace.outcome)
                            .put("modelPrompt", trace.modelPrompt)
                            .put("toolCalls", trace.toolCallsJson)
                            .put("plan", trace.planJson)
                            .put("error", trace.errorReason)
                            .put("startedAt", trace.startedAt)
                            .put(
                                "steps",
                                database.steps(trace.id).map { step ->
                                    JSONObject()
                                        .put("ordinal", step.ordinal)
                                        .put("step", step.stepJson)
                                        .put("screenText", step.screenText)
                                        .put("outcome", step.outcome)
                                        .put("durationMs", step.durationMs)
                                },
                            )
                            .toString(),
                    )
                }
            }
            val markdown = buildString {
                appendLine("# LocalGlasses agent traces")
                appendLine()
                traces.forEach { trace ->
                    appendLine("## ${trace.command}")
                    appendLine()
                    appendLine("- id: ${trace.id}")
                    appendLine("- outcome: ${trace.outcome}")
                    appendLine("- steps: ${database.steps(trace.id).size}")
                    appendLine()
                }
            }
            listOf(
                writeFile("agent-traces-$stamp.jsonl", "application/json", jsonl),
                writeFile("agent-traces-$stamp.md", "text/markdown", markdown),
            )
        }
    }

    private fun writeFile(name: String, mime: String, content: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/LocalGlasses",
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values,
        ) ?: error("cannot create Downloads entry")
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(content.toByteArray())
        } ?: error("cannot open $uri")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        return uri
    }
}
