package com.localglasses.app.agent.dictation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Phone microphone capture: 16 kHz, mono, 16-bit PCM.
 *
 * [MediaRecorder.AudioSource.VOICE_RECOGNITION] is the source Android documents for speech
 * input, so the platform noise suppressor and gain control are applied for us.
 *
 * Android types are injected rather than referenced in the constructor so the availability and
 * conversion logic can be unit-tested on the JVM.
 */
class PhoneAudioSource(
    private val hasPermission: () -> Boolean,
    private val recordFactory: () -> AudioRecord?,
) : AudioSource {

    override val kind: AudioSourceKind = AudioSourceKind.PHONE

    override fun availability(): SourceAvailability =
        when {
            !hasPermission() -> SourceAvailability.Unavailable(MISSING_PERMISSION)
            else -> SourceAvailability.Ready
        }

    override fun open(): Flow<AudioFrame> = callbackFlow {
        val record = recordFactory()
            ?: throw AudioCaptureException("Microphone is not available")

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw AudioCaptureException("Microphone could not be initialised")
        }

        try {
            record.startRecording()
        } catch (error: IllegalStateException) {
            record.release()
            throw AudioCaptureException("Microphone could not start", error)
        }

        val reader = launch(Dispatchers.Default) {
            val buffer = ShortArray(DICTATION_FRAME_SAMPLES)
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    send(AudioFrame(PcmCodec.pcm16ToFloat(buffer, read)))
                } else if (read < 0) {
                    throw AudioCaptureException("Microphone read failed ($read)")
                }
            }
        }

        awaitClose {
            reader.cancel()
            runCatching { record.stop() }
            record.release()
        }
    }

    companion object {
        const val MISSING_PERMISSION = "Microphone permission is required"

        /** Production source, bound to the app context and the real recorder. */
        fun from(context: Context): PhoneAudioSource {
            val appContext = context.applicationContext
            return PhoneAudioSource(
                hasPermission = {
                    ContextCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                },
                recordFactory = {
                    val minBuffer = AudioRecord.getMinBufferSize(
                        DICTATION_SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    )
                    if (minBuffer <= 0) {
                        null
                    } else {
                        val bytes = maxOf(minBuffer, DICTATION_FRAME_SAMPLES * 2 * 4)
                        AudioRecord(
                            MediaRecorder.AudioSource.VOICE_RECOGNITION,
                            DICTATION_SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bytes,
                        )
                    }
                },
            )
        }
    }
}
