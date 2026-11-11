package com.localglasses.app.agent.dictation

import androidx.test.platform.app.InstrumentationRegistry
import com.localglasses.app.agent.model.SpeechModelManifest
import com.localglasses.app.agent.model.SpeechModelManifestParser
import com.localglasses.app.agent.model.SpeechModelPaths
import com.localglasses.app.agent.model.SpeechModelRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * End-to-end recognition against the real sherpa-onnx engine.
 *
 * Skips when the speech model has not been downloaded on this device, so the suite stays green
 * on a clean install.
 */
class DictationRecognitionTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun paths(): SpeechModelPaths {
        val raw = context.assets.open(SpeechModelRepository.ASSET_NAME)
            .bufferedReader().use { it.readText() }
        val manifest: SpeechModelManifest = SpeechModelManifestParser.parse(raw).getOrThrow()
        val directory = File(context.filesDir, SpeechModelRepository.DIRECTORY)
        return SpeechModelPaths(
            encoder = File(directory, manifest.files.encoder.fileName).absolutePath,
            decoder = File(directory, manifest.files.decoder.fileName).absolutePath,
            tokens = File(directory, manifest.files.tokens.fileName).absolutePath,
        )
    }

    @Test
    fun transcribesBundledFixtureOnDevice() = runBlocking {
        var paths = paths()
        if (listOf(paths.encoder, paths.decoder, paths.tokens).any { !File(it).isFile }) {
            val mayDownload = InstrumentationRegistry.getArguments()
                .getString("downloadSpeechModel") == "true"
            assumeTrue(
                "speech model not present on this device",
                mayDownload,
            )
            val repository = SpeechModelRepository.fromContext(context, scope = null)
            assertTrue(
                "speech model download failed",
                repository.download().isSuccess,
            )
            paths = paths()
        }

        // Test assets live in the instrumentation APK, not the app under test.
        val wav = WavFixture.read(instrumentation.context, "fixtures/command.wav")
        assumeTrue("fixture must be 16 kHz", wav.sampleRate == DICTATION_SAMPLE_RATE)

        val recognizer = SherpaWhisperRecognizer(paths)
        val result = try {
            recognizer.transcribe(wav.samples)
        } finally {
            recognizer.close()
        }

        val normalised = result.text.lowercase()
        assertTrue(
            "unexpected transcript: '${result.text}'",
            normalised.contains("nightfall") || normalised.contains("yellow"),
        )
        assertTrue(result.audioDurationMs > 0)
        assertTrue(result.recognitionLatencyMs >= 0)
    }
}
