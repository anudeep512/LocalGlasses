package com.localglasses.app.agent.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechMetricsJsonTest {

    @Test
    fun `decodes a snapshot written before speech metrics existed`() {
        val legacy = """
            {
              "capturedAt": 1700000000000,
              "device": {"deviceModel": "OnePlus Nord CE5 Lite"},
              "inference": {"modelId": "gemma-4-E2B-it"},
              "peakAppPssBytes": 123456,
              "commandDurationMs": 4321
            }
        """.trimIndent()

        val decoded = MetricsJson.decode(legacy)

        assertNull(decoded!!.speech)
        assertEquals("OnePlus Nord CE5 Lite", decoded.device.deviceModel)
        assertEquals(123456L, decoded.peakAppPssBytes)
        assertEquals(4321L, decoded.commandDurationMs)
    }

    @Test
    fun `round trips speech metrics`() {
        val original = RequestMetrics(
            speech = SpeechMetrics(
                modelId = "whisper-small.en-int8",
                source = "GLASSES",
                audioDurationMs = 2_000,
                recognitionLatencyMs = 1_500,
                realTimeFactor = 0.75,
                endpointReason = "LENGTH_CAP",
                peakAppPssBytes = 987_654,
                wordCount = 5,
            ),
        )

        val decoded = MetricsJson.decode(MetricsJson.encode(original))!!

        assertEquals(original.speech, decoded.speech)
        assertEquals("GLASSES", decoded.speech!!.source)
    }

    @Test
    fun `a typed request encodes a null speech block`() {
        val decoded = MetricsJson.decode(MetricsJson.encode(RequestMetrics()))!!

        assertNull(decoded.speech)
    }

    @Test
    fun `unknown speech fields are ignored`() {
        val future = """
            {"capturedAt": 1, "speech": {"modelId": "x", "futureField": 42}}
        """.trimIndent()

        val decoded = MetricsJson.decode(future)!!

        assertEquals("x", decoded.speech!!.modelId)
        assertNull(decoded.speech!!.audioDurationMs)
    }
}
