package com.localglasses.app.agent.dictation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointerTest {

    private val frameMs = 32L

    private fun frame() = AudioFrame(FloatArray(DICTATION_FRAME_SAMPLES))

    /** Detector that reports speech for the frames whose indices are in [speechFrames]. */
    private fun detector(vararg speechFrames: Int): SpeechDetector {
        var index = 0
        return SpeechDetector {
            val isSpeech = speechFrames.contains(index)
            index++
            isSpeech
        }
    }

    @Test
    fun `silence only ends on silence and is discarded`() {
        val endpointer = Endpointer(detector())
        var decisions = mutableListOf<EndpointDecision>()

        repeat(80) { decisions += endpointer.accept(frame()) }

        assertEquals(EndpointDecision.SilenceEnd, decisions.last())
        assertFalse(decisions.contains(EndpointDecision.LengthCap))
        assertTrue(endpointer.shouldDiscard())
    }

    @Test
    fun `continuous speech still hits the cap`() {
        val endpointer = Endpointer(SpeechDetector { true })

        var decision: EndpointDecision = EndpointDecision.Continue
        var frames = 0
        while (decision == EndpointDecision.Continue && frames < 1_000) {
            decision = endpointer.accept(frame())
            frames++
        }

        assertEquals(EndpointDecision.LengthCap, decision)
        assertTrue(endpointer.hasSpeech)
        assertFalse(endpointer.shouldDiscard())
    }

    @Test
    fun `speech then trailing silence ends on silence`() {
        val endpointer = Endpointer(detector(0))
        assertEquals(EndpointDecision.Continue, endpointer.accept(frame()))

        var elapsed = frameMs
        var decision: EndpointDecision = EndpointDecision.Continue
        var silentFrames = 0
        while (decision == EndpointDecision.Continue) {
            decision = endpointer.accept(frame())
            silentFrames++
            elapsed += frameMs
        }

        assertEquals(EndpointDecision.SilenceEnd, decision)
        assertTrue("ended after ${silentFrames * frameMs} ms of silence", elapsed >= 2_000)
        assertFalse(endpointer.shouldDiscard())
    }

    @Test
    fun `speech resets the silence counter`() {
        val endpointer = Endpointer(detector(0, 3, 5))
        endpointer.accept(frame())
        repeat(2) { endpointer.accept(frame()) }
        endpointer.accept(frame())
        repeat(2) { endpointer.accept(frame()) }
        endpointer.accept(frame())

        // Only 2 frames (64 ms) of silence have accumulated since the last speech frame.
        assertEquals(EndpointDecision.Continue, endpointer.accept(frame()))
        assertTrue(endpointer.hasSpeech)
    }

    @Test
    fun `cap wins over silence when both apply`() {
        val endpointer = Endpointer(
            detector = SpeechDetector { true },
            config = EndpointConfig(maxUtteranceMs = 3 * frameMs, silenceMs = 0),
        )
        endpointer.accept(frame())
        endpointer.accept(frame())

        assertEquals(EndpointDecision.LengthCap, endpointer.accept(frame()))
    }

    @Test
    fun `reset clears the utterance state`() {
        val endpointer = Endpointer(detector(0))
        endpointer.accept(frame())
        assertTrue(endpointer.hasSpeech)

        endpointer.reset()

        assertFalse(endpointer.hasSpeech)
        assertEquals(0, endpointer.elapsedMs)
        assertTrue(endpointer.shouldDiscard())
    }

    @Test
    fun `elapsed time tracks accepted samples`() {
        val endpointer = Endpointer(detector())

        repeat(10) { endpointer.accept(frame()) }

        assertEquals(320, endpointer.elapsedMs)
    }
}
