package com.localglasses.app.agent.dictation

import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.AudioCodec
import com.meta.wearable.dat.camera.types.AudioSampleRate
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * Opens a glasses audio session for one utterance.
 *
 * Audio arrives as 16 kHz mono PCM over a camera stream, which DAT exposes only in dev and beta
 * channels. The session is created per dictation and stopped as soon as the utterance ends, so
 * no camera stream is left running behind the Camera tab.
 */
object DatGlassesAudio {

    private const val START_TIMEOUT_MS = 20_000L

    suspend fun open(): Result<GlassesAudioSession> = runCatching {
        if (Wearables.registrationState.value != RegistrationState.REGISTERED) {
            throw AudioCaptureException("Glasses are not registered with Meta AI")
        }

        val session = Wearables.createSession(AutoDeviceSelector()).fold(
            onSuccess = { it },
            onFailure = { error, _ -> throw AudioCaptureException(error.description) },
        )

        var camera: Camera? = null
        try {
            session.start()
            withTimeout(START_TIMEOUT_MS) {
                session.state.first { it == DeviceSessionState.STARTED }
            }

            camera = session.addCamera(
                StreamConfiguration(
                    audioCodec = AudioCodec.PCM(
                        sampleRate = AudioSampleRate.RATE_16000,
                        numberOfChannels = 1,
                    ),
                    videoQuality = VideoQuality.LOW,
                    frameRate = 15,
                ),
            ).fold(
                onSuccess = { it },
                onFailure = { error, _ -> throw AudioCaptureException(error.description) },
            )

            camera.stream.start().fold(
                onSuccess = { },
                onFailure = { error, _ -> throw AudioCaptureException(error.description) },
            )

            DatSession(session, camera)
        } catch (error: Throwable) {
            camera?.stop()
            session.stop()
            throw error
        }
    }

    private class DatSession(
        private val session: DeviceSession,
        private val camera: Camera,
    ) : GlassesAudioSession {

        override val frames = camera.stream.audioStream.toPcmFrames()

        override fun close() {
            runCatching { camera.stop() }
            runCatching { session.stop() }
        }
    }
}
