package com.localglasses.app.agent.dictation

/** Decides whether one frame contains speech. Backed by Silero VAD in production. */
fun interface SpeechDetector {
    fun isSpeech(samples: FloatArray): Boolean
}

data class EndpointConfig(
    /** Hard cap on one utterance, after which capture stops regardless of speech. */
    val maxUtteranceMs: Long = 15_000,
    /** Trailing silence that ends the utterance. Two seconds leaves room for thinking. */
    val silenceMs: Long = 2_000,
)

sealed interface EndpointDecision {
    /** Keep capturing. */
    data object Continue : EndpointDecision

    /** Speech was followed by enough silence to treat the utterance as finished. */
    data object SilenceEnd : EndpointDecision

    /** The utterance hit [EndpointConfig.maxUtteranceMs]. */
    data object LengthCap : EndpointDecision
}

/**
 * Tracks one utterance and reports when it ends.
 *
 * Pure Kotlin over a [SpeechDetector], so the timing rules are unit-testable without the
 * native VAD. VAD is used for endpointing only; it never trims audio before recognition.
 */
class Endpointer(
    private val detector: SpeechDetector,
    private val config: EndpointConfig = EndpointConfig(),
) {

    private var samplesSeen = 0L
    private var silenceSamples = 0L

    /** True once any frame has been classified as speech during this utterance. */
    var hasSpeech: Boolean = false
        private set

    val elapsedMs: Long
        get() = PcmCodec.durationMs(samplesSeen)

    fun accept(frame: AudioFrame): EndpointDecision {
        samplesSeen += frame.samples.size

        if (detector.isSpeech(frame.samples)) {
            hasSpeech = true
            silenceSamples = 0
        } else {
            silenceSamples += frame.samples.size
        }

        return when {
            elapsedMs >= config.maxUtteranceMs -> EndpointDecision.LengthCap
            // Trailing silence ends the utterance whether or not speech was heard: an
            // accidental tap must not hold the microphone open for the full cap.
            PcmCodec.durationMs(silenceSamples) >= config.silenceMs -> EndpointDecision.SilenceEnd
            else -> EndpointDecision.Continue
        }
    }

    /** A finished capture with no speech is discarded instead of being transcribed. */
    fun shouldDiscard(): Boolean = !hasSpeech

    fun reset() {
        samplesSeen = 0
        silenceSamples = 0
        hasSpeech = false
    }
}
