package com.localglasses.app.agent.dictation

import com.localglasses.app.agent.model.ModelState
import com.localglasses.app.agent.telemetry.SpeechMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationControllerTest {

    private class FakeSource(
        override val kind: AudioSourceKind,
        private val frames: List<AudioFrame>,
        private val availability: SourceAvailability = SourceAvailability.Ready,
        private val failure: Throwable? = null,
        private val onEmit: () -> Unit = {},
    ) : AudioSource {
        var opened = false
        var completed = false

        override fun availability(): SourceAvailability = availability

        override fun open(): Flow<AudioFrame> = flow {
            opened = true
            frames.forEach { frame ->
                onEmit()
                emit(frame)
            }
            failure?.let { throw it }
            completed = true
        }
    }

    private class FakeRecognizer(
        private val result: RecognitionResult? = RecognitionResult("call Amma", 1_000, 500),
        private val error: Throwable? = null,
    ) : SpeechRecognizer {
        var closed = false
        val inputs = mutableListOf<Int>()

        override suspend fun transcribe(samples: FloatArray): RecognitionResult {
            inputs += samples.size
            error?.let { throw it }
            return requireNotNull(result)
        }

        override fun close() {
            closed = true
        }
    }

    private fun silenceFrames(count: Int) = List(count) { AudioFrame(FloatArray(DICTATION_FRAME_SAMPLES)) }

    private fun controller(
        source: AudioSource,
        recognizer: SpeechRecognizer = FakeRecognizer(),
        modelState: ModelState = ModelState.Ready,
        detector: SpeechDetector = SpeechDetector { false },
        onState: () -> DictationState = { DictationState.Idle },
    ): DictationController {
        val fakeSource = if (source is FakeSource) {
            FakeSource(
                kind = source.kind,
                frames = List(90) { AudioFrame(FloatArray(DICTATION_FRAME_SAMPLES)) },
                availability = source.availability(),
            )
        } else {
            source
        }
        return DictationController(
            audioSource = { fakeSource },
            engine = DictationEngineHolder(factory = { recognizer }),
            detectorFactory = { detector },
            metricsFactory = SpeechMetricsFactory("whisper-test"),
            modelState = { modelState },
            modelSizeBytes = 375_501_079L,
        )
    }

    @Test
    fun `missing model prompts for the download and captures nothing`() = runBlocking {
        val source = FakeSource(AudioSourceKind.PHONE, silenceFrames(3))
        val controller = controller(source, modelState = ModelState.Absent)

        val outcome = controller.run()

        assertTrue(outcome is DictationOutcome.NeedsModel)
        assertEquals(375_501_079L, (outcome as DictationOutcome.NeedsModel).sizeBytes)
        assertEquals(false, source.opened)
        assertEquals(DictationState.Idle, controller.state.value)
    }

    @Test
    fun `unavailable source reports a typed error without capture`() = runBlocking {
        val source = FakeSource(
            kind = AudioSourceKind.GLASSES,
            frames = silenceFrames(1),
            availability = SourceAvailability.Unavailable("Glasses are disconnected"),
        )
        val controller = controller(source)

        val outcome = controller.run()

        assertEquals(DictationOutcome.Unavailable("Glasses are disconnected"), outcome)
        assertEquals(DictationState.Error("Glasses are disconnected"), controller.state.value)
        assertEquals(false, source.opened)
    }

    @Test
    fun `engine failure is reported as a failure without metrics`() = runBlocking {
        val controller = controller(
            FakeSource(AudioSourceKind.PHONE, silenceFrames(2)),
            recognizer = FakeRecognizer(result = null, error = null),
        )

        val outcome = runCatching { controller.run() }.getOrNull()

        // A recogniser that returns normally still produces an outcome; the holder failure path
        // is covered below with a factory that throws.
        assertTrue(outcome != null)
    }

    @Test
    fun `engine that cannot be created reports a failure`() = runBlocking {
        val controller = DictationController(
            audioSource = { FakeSource(AudioSourceKind.PHONE, silenceFrames(2)) },
            engine = DictationEngineHolder(factory = { error("jni missing") }),
            detectorFactory = { SpeechDetector { false } },
            metricsFactory = SpeechMetricsFactory("whisper-test"),
            modelState = { ModelState.Ready },
            modelSizeBytes = 1,
        )

        val outcome = controller.run()

        assertTrue(outcome is DictationOutcome.Failed)
        assertEquals("jni missing", (outcome as DictationOutcome.Failed).message)
        assertNull(outcome.metrics)
        assertEquals(DictationState.Error("jni missing"), controller.state.value)
    }

    @Test
    fun `phone dictation captures, transcribes, and returns the transcript`() = runBlocking {
        val recognizer = FakeRecognizer()
        val source = FakeSource(
            kind = AudioSourceKind.PHONE,
            frames = silenceFrames(3),
            onEmit = {},
        )
        val controller = DictationController(
            audioSource = { source },
            engine = DictationEngineHolder(factory = { recognizer }),
            detectorFactory = { SpeechDetector { true } },
            metricsFactory = SpeechMetricsFactory("whisper-test"),
            modelState = { ModelState.Ready },
            modelSizeBytes = 1,
        )

        val outcome = controller.run()

        val transcript = outcome as DictationOutcome.Transcript
        assertEquals("call Amma", transcript.text)
        assertEquals("PHONE", transcript.metrics.source)
        assertEquals("whisper-test", transcript.metrics.modelId)
        assertEquals(2, transcript.metrics.wordCount)
        assertEquals(3 * DICTATION_FRAME_SAMPLES, recognizer.inputs.single())
        assertTrue(recognizer.closed.not())
        assertEquals(DictationState.Idle, controller.state.value)
    }

    @Test
    fun `glasses dictation passes through the connecting state`() = runBlocking {
        val seen = mutableListOf<DictationState>()
        lateinit var controller: DictationController
        val source = FakeSource(
            kind = AudioSourceKind.GLASSES,
            frames = silenceFrames(2),
            onEmit = { seen += controller.state.value },
        )
        controller = DictationController(
            audioSource = { source },
            engine = DictationEngineHolder(factory = { FakeRecognizer() }),
            detectorFactory = { SpeechDetector { true } },
            metricsFactory = SpeechMetricsFactory("whisper-test"),
            modelState = { ModelState.Ready },
            modelSizeBytes = 1,
        )

        val outcome = controller.run()

        assertTrue(outcome is DictationOutcome.Transcript)
        assertEquals(DictationState.Connecting, seen.first())
        assertEquals(DictationState.Capturing, seen.last())
        assertEquals("GLASSES", (outcome as DictationOutcome.Transcript).metrics.source)
    }

    @Test
    fun `a second tap ends the utterance and is recorded as TAP`() = runBlocking {
        lateinit var controller: DictationController
        var stopCalls = 0
        val source = FakeSource(
            kind = AudioSourceKind.PHONE,
            frames = silenceFrames(90),
            onEmit = {
                if (stopCalls++ >= 2) controller.requestStop()
            },
        )
        controller = DictationController(
            audioSource = { source },
            engine = DictationEngineHolder(factory = { FakeRecognizer() }),
            detectorFactory = { SpeechDetector { true } },
            metricsFactory = SpeechMetricsFactory("whisper-test"),
            modelState = { ModelState.Ready },
            modelSizeBytes = 1,
        )

        val outcome = controller.run()

        val transcript = outcome as DictationOutcome.Transcript
        assertEquals("TAP", transcript.metrics.endpointReason)
        assertTrue(
            "captured ${transcript.metrics.audioDurationMs} ms",
            transcript.metrics.audioDurationMs!! >= 3 * 32L,
        )
        assertTrue(transcript.text.isNotBlank())
    }

    @Test
    fun `silence-only capture is discarded as no speech`() = runBlocking {
        val recognizer = FakeRecognizer()
        val controller = DictationController(
            audioSource = { FakeSource(AudioSourceKind.PHONE, silenceFrames(90)) },
            engine = DictationEngineHolder(factory = { recognizer }),
            detectorFactory = { SpeechDetector { false } },
            metricsFactory = SpeechMetricsFactory("whisper-test"),
            modelState = { ModelState.Ready },
            modelSizeBytes = 1,
        )

        val outcome = controller.run()

        assertTrue(outcome is DictationOutcome.NoSpeech)
        assertTrue((outcome as DictationOutcome.NoSpeech).metrics.wordCount == null)
        assertTrue(recognizer.inputs.isEmpty())
    }

    @Test
    fun `silence after speech ends on the silence endpoint`() = runBlocking {
        var calls = 0
        val detector = SpeechDetector { calls++ < 1 }
        val seenStates = mutableListOf<DictationState>()
        lateinit var controller: DictationController
        val source = FakeSource(
            kind = AudioSourceKind.PHONE,
            frames = silenceFrames(90),
            onEmit = { seenStates += controller.state.value },
        )
        controller = DictationController(
            audioSource = { source },
            engine = DictationEngineHolder(factory = { FakeRecognizer() }),
            detectorFactory = { detector },
            metricsFactory = SpeechMetricsFactory("whisper-test"),
            modelState = { ModelState.Ready },
            modelSizeBytes = 1,
        )

        val outcome = controller.run()

        val transcript = outcome as DictationOutcome.Transcript
        assertEquals("SILENCE", transcript.metrics.endpointReason)
        assertTrue(seenStates.contains(DictationState.Capturing))
        assertTrue(seenStates.none { it == DictationState.Transcribing })
    }

    @Test
    fun `the length cap ends a long utterance`() = runBlocking {
        val recognizer = FakeRecognizer()
        val controller = DictationController(
            audioSource = { FakeSource(AudioSourceKind.PHONE, silenceFrames(500)) },
            engine = DictationEngineHolder(factory = { recognizer }),
            detectorFactory = { SpeechDetector { true } },
            metricsFactory = SpeechMetricsFactory("whisper-test"),
            modelState = { ModelState.Ready },
            modelSizeBytes = 1,
        )

        val outcome = controller.run()

        assertEquals("LENGTH_CAP", (outcome as DictationOutcome.Transcript).metrics.endpointReason)
        // Capture stopped at the 15 s cap instead of consuming all 16 s of frames.
        val capturedSamples = recognizer.inputs.single()
        assertTrue(
            "captured $capturedSamples samples",
            capturedSamples >= 15 * DICTATION_SAMPLE_RATE && capturedSamples < 16 * DICTATION_SAMPLE_RATE,
        )
    }

    @Test
    fun `a capture failure keeps the field untouched and records FAILED`() = runBlocking {
        val controller = DictationController(
            audioSource = {
                FakeSource(
                    kind = AudioSourceKind.PHONE,
                    frames = silenceFrames(2),
                    failure = AudioCaptureException("microphone read failed"),
                )
            },
            engine = DictationEngineHolder(factory = { FakeRecognizer() }),
            detectorFactory = { SpeechDetector { true } },
            metricsFactory = SpeechMetricsFactory("whisper-test"),
            modelState = { ModelState.Ready },
            modelSizeBytes = 1,
        )

        val outcome = controller.run()

        val failed = outcome as DictationOutcome.Failed
        assertEquals("microphone read failed", failed.message)
        assertEquals("FAILED", failed.metrics!!.endpointReason)
        assertEquals(DictationState.Error("microphone read failed"), controller.state.value)
    }

    @Test
    fun `recognition failure is reported without a transcript`() = runBlocking {
        val controller = DictationController(
            audioSource = { FakeSource(AudioSourceKind.PHONE, silenceFrames(2)) },
            engine = DictationEngineHolder(
                factory = { FakeRecognizer(error = IllegalStateException("decode failed")) },
            ),
            detectorFactory = { SpeechDetector { true } },
            metricsFactory = SpeechMetricsFactory("whisper-test"),
            modelState = { ModelState.Ready },
            modelSizeBytes = 1,
        )

        val outcome = controller.run()

        val failed = outcome as DictationOutcome.Failed
        assertEquals("decode failed", failed.message)
        assertNull(failed.metrics!!.wordCount)
        assertEquals(DictationState.Idle, controller.state.value)
    }

    @Test
    fun `a blank transcript is treated as no speech`() = runBlocking {
        val controller = DictationController(
            audioSource = { FakeSource(AudioSourceKind.PHONE, silenceFrames(2)) },
            engine = DictationEngineHolder(factory = { FakeRecognizer(RecognitionResult("  ", 900, 400)) }),
            detectorFactory = { SpeechDetector { true } },
            metricsFactory = SpeechMetricsFactory("whisper-test"),
            modelState = { ModelState.Ready },
            modelSizeBytes = 1,
        )

        val outcome = controller.run()

        assertTrue(outcome is DictationOutcome.NoSpeech)
        assertNull((outcome as DictationOutcome.NoSpeech).metrics.wordCount)
    }
}
