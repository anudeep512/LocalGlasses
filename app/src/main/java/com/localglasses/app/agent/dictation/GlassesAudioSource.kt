package com.localglasses.app.agent.dictation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * One open glasses audio session. Closing it must release the glasses session, because the
 * session exists only for the duration of one utterance.
 */
interface GlassesAudioSession {
    val frames: Flow<AudioFrame>

    fun close()
}

/**
 * Glasses microphone capture over a DAT camera audio stream.
 *
 * The opener is injected so the capture contract (open on collect, close on end or
 * cancellation, typed failure when the source is unavailable) is testable without glasses.
 */
class GlassesAudioSource(
    private val sessionOpener: suspend () -> Result<GlassesAudioSession>,
    private val supported: () -> Boolean = { true },
) : AudioSource {

    override val kind: AudioSourceKind = AudioSourceKind.GLASSES

    override fun availability(): SourceAvailability =
        if (supported()) {
            SourceAvailability.Ready
        } else {
            SourceAvailability.Unavailable(UNAVAILABLE_IN_BUILD)
        }

    override fun open(): Flow<AudioFrame> = flow {
        val session = sessionOpener().getOrElse { error ->
            throw if (error is AudioCaptureException) {
                error
            } else {
                AudioCaptureException(
                    error.message ?: "Glasses audio is unavailable",
                    error,
                )
            }
        }
        try {
            session.frames.collect { emit(it) }
        } finally {
            session.close()
        }
    }

    companion object {
        const val UNAVAILABLE_IN_BUILD = "Glasses audio is unavailable in this build"
    }
}

/** Converts the raw PCM bytes DAT delivers into the float samples the recogniser needs. */
fun Flow<com.meta.wearable.dat.camera.types.AudioFrame>.toPcmFrames(): Flow<AudioFrame> =
    map { frame -> AudioFrame(PcmCodec.littleEndianToFloat(frame.buffer)) }
