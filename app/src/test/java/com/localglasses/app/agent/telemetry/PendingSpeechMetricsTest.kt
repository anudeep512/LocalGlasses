package com.localglasses.app.agent.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSpeechMetricsTest {

    private val metrics = SpeechMetrics(
        modelId = "whisper-small.en-int8",
        source = "PHONE",
        audioDurationMs = 1_200,
        recognitionLatencyMs = 900,
        realTimeFactor = 0.75,
        endpointReason = "SILENCE",
        wordCount = 4,
    )

    @Test
    fun `metrics attach when the dictated text is submitted unchanged`() {
        val pending = PendingSpeechMetrics()
        pending.record("call Amma", metrics)

        assertTrue(pending.isPending)
        assertEquals(metrics, pending.consumeForRequest("call Amma"))
    }

    @Test
    fun `metrics are consumed only once`() {
        val pending = PendingSpeechMetrics()
        pending.record("call Amma", metrics)
        pending.consumeForRequest("call Amma")

        assertFalse(pending.isPending)
        assertNull(pending.consumeForRequest("call Amma"))
    }

    @Test
    fun `a manual edit drops the metrics`() {
        val pending = PendingSpeechMetrics()
        pending.record("call Amma", metrics)

        pending.onTextEdited("call Amma and tell her")

        assertFalse(pending.isPending)
        assertNull(pending.consumeForRequest("call Amma and tell her"))
    }

    @Test
    fun `the text change that carried the transcript does not drop the metrics`() {
        val pending = PendingSpeechMetrics()
        pending.record("call Amma", metrics)

        pending.onTextEdited("call Amma")

        assertTrue(pending.isPending)
    }

    @Test
    fun `clearing the field drops the metrics`() {
        val pending = PendingSpeechMetrics()
        pending.record("call Amma", metrics)

        pending.onTextEdited("")

        assertFalse(pending.isPending)
    }

    @Test
    fun `a typed command has no speech metrics`() {
        val pending = PendingSpeechMetrics()

        assertNull(pending.consumeForRequest("call Amma"))
    }

    @Test
    fun `a failed dictation records no metrics`() {
        val pending = PendingSpeechMetrics()
        pending.record("", null)

        assertNull(pending.consumeForRequest(""))
    }
}
