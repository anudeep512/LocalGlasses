package com.localglasses.app.agent.dictation

import kotlinx.coroutines.flow.Flow

/** Sample rate every dictation source delivers. Matches the recogniser and VAD window. */
const val DICTATION_SAMPLE_RATE = 16_000

/** Samples per VAD window: 512 samples at 16 kHz, i.e. 32 ms. */
const val DICTATION_FRAME_SAMPLES = 512

enum class AudioSourceKind { PHONE, GLASSES }

/**
 * Why a source cannot start. Modelled as data rather than an exception so the controller can
 * show an actionable message and tests can assert it without Android.
 */
sealed interface SourceAvailability {
    data object Ready : SourceAvailability

    data class Unavailable(val reason: String) : SourceAvailability
}

/**
 * One microphone that can deliver 16 kHz mono PCM for a single utterance.
 *
 * [open] is cold: collecting starts capture, cancelling the collection stops and releases the
 * hardware. Implementations must not deliver audio after cancellation completes.
 */
interface AudioSource {
    val kind: AudioSourceKind

    fun availability(): SourceAvailability

    fun open(): Flow<AudioFrame>
}

/** One block of mono float samples in the nominal range -1.0 to 1.0. */
class AudioFrame(val samples: FloatArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is AudioFrame && samples.contentEquals(other.samples))

    override fun hashCode(): Int = samples.contentHashCode()
}

/** Raised when a source was available but capture could not continue. */
class AudioCaptureException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
