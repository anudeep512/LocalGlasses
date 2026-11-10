package com.localglasses.app.agent.trace

import com.localglasses.app.agent.safety.Redactor
import com.localglasses.app.agent.telemetry.RequestMetrics
import java.util.UUID

/**
 * Writes the trace for one command. Redaction happens here, before any value reaches
 * SQLCipher, so a journal or backup cannot contain a raw OTP or credential.
 */
class TraceRecorder(private val database: TraceDatabase) {

    fun start(command: String, modelPrompt: String? = null): String {
        val id = UUID.randomUUID().toString()
        database.insertTrace(
            id = id,
            command = Redactor.redact(command),
            taskType = null,
            targetPackage = null,
            outcome = "pending",
            modelPrompt = modelPrompt?.let(Redactor::redact),
        )
        return id
    }

    fun decision(
        id: String,
        command: String,
        taskType: String?,
        targetPackage: String?,
        modelPrompt: String?,
        toolCallsJson: String?,
        planJson: String?,
        outcome: String,
        metrics: RequestMetrics? = null,
    ) {
        database.insertTrace(
            id = id,
            command = Redactor.redact(command),
            taskType = taskType,
            targetPackage = targetPackage,
            outcome = outcome,
            modelPrompt = modelPrompt?.let(Redactor::redact),
            toolCallsJson = toolCallsJson?.let(Redactor::redact),
            planJson = planJson?.let(Redactor::redact),
        )
        metrics?.let { database.upsertMetrics(id, it) }
    }

    fun metrics(traceId: String, metrics: RequestMetrics) {
        database.upsertMetrics(traceId, metrics)
    }

    fun step(
        traceId: String,
        ordinal: Int,
        stepJson: String?,
        screenText: String?,
        outcome: String,
        durationMs: Long,
    ) {
        database.insertStep(
            traceId = traceId,
            ordinal = ordinal,
            stepJson = stepJson?.let(Redactor::redact),
            screenText = screenText?.let(Redactor::redact),
            postCondition = null,
            outcome = outcome,
            durationMs = durationMs,
        )
    }

    fun finish(
        id: String,
        command: String,
        taskType: String?,
        targetPackage: String?,
        outcome: String,
        errorReason: String? = null,
        metrics: RequestMetrics? = null,
    ) {
        database.updateOutcome(
            id = id,
            outcome = outcome,
            errorReason = errorReason?.let(Redactor::redact),
        )
        metrics?.let { database.upsertMetrics(id, it) }
    }
}
