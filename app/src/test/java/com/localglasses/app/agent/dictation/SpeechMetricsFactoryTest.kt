package com.localglasses.app.agent.dictation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechMetricsFactoryTest {

    private val factory = SpeechMetricsFactory(
        modelId = "whisper-small.en-int8",
        peakPssReader = { 812_345_678L },
    )

    @Test
    fun `successful dictation records the measured block`() {
        val metrics = factory.create(
            source = AudioSourceKind.PHONE,
            endpointReason = EndpointReason.SILENCE,
            audioDurationMs = 2_000,
            recognitionLatencyMs = 1_000,
            transcript = "call Amma now",
        )

        assertEquals("whisper-small.en-int8", metrics.modelId)
        assertEquals("PHONE", metrics.source)
        assertEquals(2_000L, metrics.audioDurationMs)
        assertEquals(1_000L, metrics.recognitionLatencyMs)
        assertEquals(0.5, metrics.realTimeFactor!!, 0.0001)
        assertEquals("SILENCE", metrics.endpointReason)
        assertEquals(812_345_678L, metrics.peakAppPssBytes)
        assertEquals(3, metrics.wordCount)
    }

    @Test
    fun `failed dictation keeps timings and has no word count`() {
        val metrics = factory.create(
            source = AudioSourceKind.GLASSES,
            endpointReason = EndpointReason.FAILED,
            audioDurationMs = 1_500,
            recognitionLatencyMs = 900,
            transcript = null,
        )

        assertEquals("FAILED", metrics.endpointReason)
        assertEquals(1_500L, metrics.audioDurationMs)
        assertEquals(0.6, metrics.realTimeFactor!!, 0.0001)
        assertNull(metrics.wordCount)
        assertEquals("GLASSES", metrics.source)
    }

    @Test
    fun `empty transcript has no word count`() {
        val metrics = factory.create(
            source = AudioSourceKind.PHONE,
            endpointReason = EndpointReason.SILENCE,
            audioDurationMs = 1_000,
            recognitionLatencyMs = 400,
            transcript = "   ",
        )

        assertNull(metrics.wordCount)
    }

    @Test
    fun `zero length audio produces no factor instead of a fake value`() {
        val metrics = factory.create(
            source = AudioSourceKind.PHONE,
            endpointReason = EndpointReason.INTERRUPTED,
            audioDurationMs = 0,
            recognitionLatencyMs = 0,
            transcript = null,
        )

        assertNull(metrics.audioDurationMs)
        assertNull(metrics.realTimeFactor)
        assertEquals(0L, metrics.recognitionLatencyMs)
    }

    @Test
    fun `an unreadable pss is absent`() {
        val noPss = SpeechMetricsFactory(modelId = "x", peakPssReader = { null })

        val metrics = noPss.create(
            source = AudioSourceKind.PHONE,
            endpointReason = EndpointReason.TAP,
            audioDurationMs = 500,
            recognitionLatencyMs = 250,
            transcript = "hello",
        )

        assertNull(metrics.peakAppPssBytes)
        assertEquals(1, metrics.wordCount)
    }

    @Test
    fun `an explicitly measured pss wins over the reader`() {
        val metrics = factory.create(
            source = AudioSourceKind.PHONE,
            endpointReason = EndpointReason.TAP,
            audioDurationMs = 500,
            recognitionLatencyMs = 250,
            transcript = "hello there",
            peakPssBytes = 123L,
        )

        assertEquals(123L, metrics.peakAppPssBytes)
        assertEquals(2, metrics.wordCount)
    }
}
