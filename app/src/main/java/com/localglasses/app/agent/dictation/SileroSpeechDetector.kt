package com.localglasses.app.agent.dictation

import android.content.Context
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * Silero VAD behind [SpeechDetector].
 *
 * The model is a 629 KB asset, so endpointing needs no download. One instance covers one
 * utterance and is released with the controller's capture.
 */
class SileroSpeechDetector(
    context: Context,
    threshold: Float = 0.5f,
) : SpeechDetector, AutoCloseable {

    private val vad = Vad(
        assetManager = context.applicationContext.assets,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = ASSET_NAME,
                threshold = threshold,
                minSilenceDuration = 0.25f,
                minSpeechDuration = 0.25f,
                windowSize = DICTATION_FRAME_SAMPLES,
                maxSpeechDuration = 10f,
            ),
            sampleRate = DICTATION_SAMPLE_RATE,
            numThreads = 1,
        ),
    )

    override fun isSpeech(samples: FloatArray): Boolean {
        vad.acceptWaveform(samples)
        return vad.isSpeechDetected()
    }

    override fun close() {
        runCatching { vad.release() }
    }

    companion object {
        const val ASSET_NAME = "silero_vad.onnx"
    }
}
