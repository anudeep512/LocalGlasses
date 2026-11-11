package com.localglasses.app.agent.dictation

import com.localglasses.app.agent.model.ModelState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictationViewModelTest {

    private class ScriptedSource(
        override val kind: AudioSourceKind = AudioSourceKind.PHONE,
        private val frames: List<AudioFrame>,
        private val onFrame: (Int) -> Unit = {},
    ) : AudioSource {
        override fun availability(): SourceAvailability = SourceAvailability.Ready

        override fun open() = kotlinx.coroutines.flow.flow {
            frames.forEachIndexed { index, frame ->
                onFrame(index)
                emit(frame)
            }
        }
    }

    private fun frames(count: Int) = List(count) { AudioFrame(FloatArray(DICTATION_FRAME_SAMPLES)) }

    private fun controller(source: AudioSource) = DictationController(
        audioSource = { source },
        engine = DictationEngineHolder(
            factory = {
                object : SpeechRecognizer {
                    override suspend fun transcribe(samples: FloatArray) =
                        RecognitionResult("call Amma", 1_000, 400)

                    override fun close() = Unit
                }
            },
        ),
        detectorFactory = { SpeechDetector { true } },
        metricsFactory = SpeechMetricsFactory("whisper-test"),
        modelState = { ModelState.Ready },
        modelSizeBytes = 1,
    )

    @Test
    fun `a tap runs an utterance and emits its outcome`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = DictationViewModel(
            controllerFactory = { controller(ScriptedSource(frames = frames(2))) },
            scope = backgroundScope,
        )

        val pending = async { viewModel.outcomes.first() }
        viewModel.onMicTap()

        val outcome = pending.await()
        assertTrue(outcome is DictationOutcome.Transcript)
        assertEquals("call Amma", (outcome as DictationOutcome.Transcript).text)
    }

    @Test
    fun `a second tap stops the running utterance`() = runTest(UnconfinedTestDispatcher()) {
        lateinit var viewModel: DictationViewModel
        val source = ScriptedSource(frames = frames(20)) { index ->
            if (index == 2) viewModel.onMicTap()
        }
        viewModel = DictationViewModel(
            controllerFactory = { controller(source) },
            scope = backgroundScope,
        )

        val pending = async { viewModel.outcomes.first() }
        viewModel.onMicTap()

        val transcript = pending.await() as DictationOutcome.Transcript
        assertEquals("TAP", transcript.metrics.endpointReason)
    }

    @Test
    fun `a missing model emits the download prompt`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = DictationViewModel(
            controllerFactory = {
                DictationController(
                    audioSource = { ScriptedSource(frames = frames(1)) },
                    engine = DictationEngineHolder(factory = {
                        throw IllegalStateException("must not load")
                    }),
                    detectorFactory = { SpeechDetector { true } },
                    metricsFactory = SpeechMetricsFactory("whisper-test"),
                    modelState = { ModelState.Absent },
                    modelSizeBytes = 42L,
                )
            },
            scope = backgroundScope,
        )

        val pending = async { viewModel.outcomes.first() }
        viewModel.onMicTap()

        val outcome = pending.await()
        assertEquals(42L, (outcome as DictationOutcome.NeedsModel).sizeBytes)
    }

    @Test
    fun `dictation is not active before the first tap`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = DictationViewModel(
            controllerFactory = { controller(ScriptedSource(frames = frames(2))) },
            scope = backgroundScope,
        )

        assertFalse(viewModel.isDictating)
        assertEquals(DictationState.Idle, viewModel.state.value)
    }
}
