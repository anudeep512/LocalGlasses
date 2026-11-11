package com.localglasses.app.agent.dictation

import android.os.SystemClock
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.localglasses.app.agent.model.SpeechModelPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Whisper small.en through sherpa-onnx's offline recogniser.
 *
 * Whisper has no streaming decoder, so each utterance is decoded in one pass after capture
 * ends. The native recogniser is created on first use and released by [close].
 */
class SherpaWhisperRecognizer(
    private val paths: SpeechModelPaths,
    private val threads: Int = 2,
) : SpeechRecognizer {

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    private fun engine(): OfflineRecognizer =
        recognizer ?: OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = DICTATION_SAMPLE_RATE,
                    featureDim = FEATURE_DIM,
                ),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = paths.encoder,
                        decoder = paths.decoder,
                        language = "en",
                        task = "transcribe",
                    ),
                    tokens = paths.tokens,
                    numThreads = threads,
                    modelType = "whisper",
                ),
            ),
        ).also { recognizer = it }

    override suspend fun transcribe(samples: FloatArray): RecognitionResult =
        withContext(Dispatchers.Default) {
            val engine = engine()
            val stream = engine.createStream()
            try {
                val startedAt = SystemClock.elapsedRealtime()
                stream.acceptWaveform(samples, DICTATION_SAMPLE_RATE)
                engine.decode(stream)
                val text = engine.getResult(stream).text.trim()
                RecognitionResult(
                    text = text,
                    audioDurationMs = PcmCodec.durationMs(samples.size.toLong()),
                    recognitionLatencyMs = SystemClock.elapsedRealtime() - startedAt,
                )
            } finally {
                stream.release()
            }
        }

    override fun close() {
        recognizer?.let { runCatching { it.release() } }
        recognizer = null
    }

    private companion object {
        const val FEATURE_DIM = 80
    }
}
