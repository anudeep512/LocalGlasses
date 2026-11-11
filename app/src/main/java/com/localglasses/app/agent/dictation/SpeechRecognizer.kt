package com.localglasses.app.agent.dictation

/** What one utterance recognition produced. Text is empty when nothing was recognised. */
data class RecognitionResult(
    val text: String,
    val audioDurationMs: Long,
    val recognitionLatencyMs: Long,
)

/**
 * Transcribes one complete utterance. Implementations own native resources and must be closed.
 */
interface SpeechRecognizer {
    suspend fun transcribe(samples: FloatArray): RecognitionResult

    fun close()
}

/** What ended an utterance, recorded with every dictation including failures. */
enum class EndpointReason { TAP, SILENCE, LENGTH_CAP, INTERRUPTED, FAILED }
