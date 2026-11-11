package com.localglasses.app.agent.dictation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localglasses.app.LocalGlassesApp
import com.localglasses.app.agent.commands.AgentViewModel
import com.localglasses.app.agent.model.SpeechModelManifestParser
import com.localglasses.app.agent.model.SpeechModelRepository
import com.localglasses.app.agent.model.SpeechModelPaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end dictation with the real engine: bundled audio in, transcript in the command field.
 *
 * Only the physical microphone is replaced by the fixture; capture, endpointing, the real
 * sherpa-onnx recogniser, metrics, and the ViewModel wiring are the production ones.
 */
@RunWith(AndroidJUnit4::class)
class DictationEndToEndTest {

    private val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = ApplicationProvider.getApplicationContext<LocalGlassesApp>()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun paths(): SpeechModelPaths {
        val raw = context.assets.open(SpeechModelRepository.ASSET_NAME)
            .bufferedReader().use { it.readText() }
        val manifest = SpeechModelManifestParser.parse(raw).getOrThrow()
        val directory = File(context.filesDir, SpeechModelRepository.DIRECTORY)
        return SpeechModelPaths(
            encoder = File(directory, manifest.files.encoder.fileName).absolutePath,
            decoder = File(directory, manifest.files.decoder.fileName).absolutePath,
            tokens = File(directory, manifest.files.tokens.fileName).absolutePath,
        )
    }

    /** Replays the fixture as if it came from a microphone, in VAD-sized frames. */
    private class FixtureAudioSource(private val samples: FloatArray) : AudioSource {
        override val kind = AudioSourceKind.PHONE
        override fun availability() = SourceAvailability.Ready

        override fun open(): Flow<AudioFrame> = flow {
            var index = 0
            while (index < samples.size) {
                val end = minOf(index + DICTATION_FRAME_SAMPLES, samples.size)
                emit(AudioFrame(samples.copyOfRange(index, end)))
                index = end
            }
        }
    }

    @Test
    fun dictatedAudioReachesTheCommandFieldWithoutSubmitting() = runTest {
        var resolved = paths()
        if (listOf(resolved.encoder, resolved.decoder, resolved.tokens).any { !File(it).isFile }) {
            val mayDownload = androidx.test.platform.app.InstrumentationRegistry.getArguments()
                .getString("downloadSpeechModel") == "true"
            assumeTrue("speech model not present on this device", mayDownload)
            val repository = SpeechModelRepository.fromContext(context, scope = null)
            assertTrue("speech model download failed", repository.download().isSuccess)
            resolved = paths()
        }

        val wav = WavReader.read(instrumentation.context.assets.open("fixtures/command.wav"))
        assumeTrue("fixture must be 16 kHz", wav.sampleRate == DICTATION_SAMPLE_RATE)

        val controller = DictationController(
            audioSource = { FixtureAudioSource(wav.samples) },
            engine = DictationEngineHolder(factory = { SherpaWhisperRecognizer(resolved) }),
            detectorFactory = { SileroSpeechDetector(context) },
            metricsFactory = app.agentContainer.speechMetricsFactory,
            modelState = { com.localglasses.app.agent.model.ModelState.Ready },
            modelSizeBytes = 1,
        )

        val outcome = controller.run()

        assertTrue(
            "expected a transcript, got $outcome",
            outcome is DictationOutcome.Transcript,
        )
        val transcript = outcome as DictationOutcome.Transcript
        assertTrue(
            "unexpected transcript: '${transcript.text}'",
            transcript.text.lowercase().contains("nightfall") ||
                transcript.text.lowercase().contains("yellow"),
        )
        assertEquals("PHONE", transcript.metrics.source)
        assertTrue(transcript.metrics.recognitionLatencyMs!! >= 0)

        // The same wiring the screen uses: only a transcript touches the field.
        val agent = AgentViewModel(app)
        agent.onCommandChange("typed before dictation")
        agent.onDictationFinished(transcript.text, transcript.metrics)
        advanceUntilIdle()

        assertEquals(transcript.text, agent.uiState.value.command)
        assertFalse(agent.uiState.value.isSubmitting)
    }
}
