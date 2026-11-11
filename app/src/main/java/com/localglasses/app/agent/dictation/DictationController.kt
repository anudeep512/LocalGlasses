package com.localglasses.app.agent.dictation

import com.localglasses.app.agent.model.ModelState
import com.localglasses.app.agent.telemetry.SpeechMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException

/** What the UI shows while a dictation is in progress. */
sealed interface DictationState {
    data object Idle : DictationState

    /** Waiting for the glasses session and stream to come up. */
    data object Connecting : DictationState

    data object Capturing : DictationState

    data object Transcribing : DictationState

    data class Error(val message: String) : DictationState
}

/** What one dictation produced. Only [Transcript] may change the command field. */
sealed interface DictationOutcome {
    data class Transcript(val text: String, val metrics: SpeechMetrics) : DictationOutcome

    data class NoSpeech(val message: String, val metrics: SpeechMetrics) : DictationOutcome

    data class Unavailable(val message: String) : DictationOutcome

    data class NeedsModel(val sizeBytes: Long) : DictationOutcome

    data class Failed(val message: String, val metrics: SpeechMetrics?) : DictationOutcome
}

/**
 * Runs one push-to-dictate utterance: precondition checks, capture, endpointing, recognition,
 * and measurement.
 *
 * The controller owns no Android views and no ViewModel state, so every branch — including a
 * missing model and an unavailable source — is unit-testable with a scripted audio source.
 */
private const val LEVEL_SCALE = 4f

class DictationController(
    private val audioSource: () -> AudioSource,
    private val engine: DictationEngineHolder,
    private val detectorFactory: () -> SpeechDetector,
    private val metricsFactory: SpeechMetricsFactory,
    private val modelState: () -> ModelState,
    private val modelSizeBytes: Long,
    private val endpointerConfig: EndpointConfig = EndpointConfig(),
) {

    private fun inputLevel(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (sample in samples) sum += sample.toDouble() * sample
        val rms = kotlin.math.sqrt(sum / samples.size).toFloat()
        return (rms * LEVEL_SCALE).coerceIn(0f, 1f)
    }

    private class EndOfUtterance(val decision: EndpointDecision) :
        RuntimeException(null, null, false, false)

    private val _state = MutableStateFlow<DictationState>(DictationState.Idle)
    val state: StateFlow<DictationState> = _state.asStateFlow()

    /** Smoothed input level in the range 0..1, for the recording meter. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    @Volatile
    private var stopRequested = false

    /** Requests the current utterance to end, as a second tap on the microphone does. */
    fun requestStop() {
        stopRequested = true
    }

    suspend fun run(): DictationOutcome {
        stopRequested = false

        if (modelState() != ModelState.Ready) {
            _state.value = DictationState.Idle
            return DictationOutcome.NeedsModel(modelSizeBytes)
        }

        val source = audioSource()
        val availability = source.availability()
        if (availability is SourceAvailability.Unavailable) {
            _state.value = DictationState.Error(availability.reason)
            return DictationOutcome.Unavailable(availability.reason)
        }

        val recognizer = engine.engine().getOrElse { error ->
            val message = error.message ?: "Speech engine could not start"
            _state.value = DictationState.Error(message)
            return DictationOutcome.Failed(message, null)
        }

        _state.value = if (source.kind == AudioSourceKind.GLASSES) {
            DictationState.Connecting
        } else {
            DictationState.Capturing
        }

        val samples = ArrayList<Float>()
        val detector = detectorFactory()
        val endpointer = Endpointer(detector, endpointerConfig)
        var decision: EndpointDecision = EndpointDecision.Continue
        var audioFailure: Throwable? = null

        try {
            source.open().collect { frame ->
                if (_state.value == DictationState.Connecting) {
                    _state.value = DictationState.Capturing
                }
                samples.addAll(frame.samples.asList())
                _level.value = inputLevel(frame.samples)
                val reached = if (stopRequested) {
                    EndpointDecision.SilenceEnd
                } else {
                    endpointer.accept(frame)
                }
                if (reached != EndpointDecision.Continue) {
                    throw EndOfUtterance(reached)
                }
            }
        } catch (ended: EndOfUtterance) {
            decision = ended.decision
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            audioFailure = error
        } finally {
            (detector as? AutoCloseable)?.let { runCatching { it.close() } }
        }

        _level.value = 0f
        val endpointReason = when {
            stopRequested -> EndpointReason.TAP
            decision == EndpointDecision.LengthCap -> EndpointReason.LENGTH_CAP
            audioFailure != null -> EndpointReason.FAILED
            else -> EndpointReason.SILENCE
        }
        val audioDurationMs = PcmCodec.durationMs(samples.size.toLong())

        if (audioFailure != null) {
            _state.value = DictationState.Error(audioFailure!!.message ?: "Capture failed")
            return DictationOutcome.Failed(
                message = audioFailure!!.message ?: "Capture failed",
                metrics = metricsFactory.create(
                    source = source.kind,
                    endpointReason = EndpointReason.FAILED,
                    audioDurationMs = audioDurationMs,
                    recognitionLatencyMs = 0,
                    transcript = null,
                ),
            )
        }

        if (endpointer.shouldDiscard()) {
            _state.value = DictationState.Idle
            return DictationOutcome.NoSpeech(
                message = "No speech heard",
                metrics = metricsFactory.create(
                    source = source.kind,
                    endpointReason = endpointReason,
                    audioDurationMs = audioDurationMs,
                    recognitionLatencyMs = 0,
                    transcript = null,
                ),
            )
        }

        _state.value = DictationState.Transcribing
        val result = runCatching { recognizer.transcribe(samples.toFloatArray()) }
        _state.value = DictationState.Idle

        return result.fold(
            onSuccess = { recognition ->
                val metrics = metricsFactory.create(
                    source = source.kind,
                    endpointReason = endpointReason,
                    audioDurationMs = recognition.audioDurationMs,
                    recognitionLatencyMs = recognition.recognitionLatencyMs,
                    transcript = recognition.text,
                )
                if (recognition.text.isBlank()) {
                    DictationOutcome.NoSpeech("Nothing was recognised", metrics)
                } else {
                    DictationOutcome.Transcript(recognition.text, metrics)
                }
            },
            onFailure = { error ->
                DictationOutcome.Failed(
                    message = error.message ?: "Recognition failed",
                    metrics = metricsFactory.create(
                        source = source.kind,
                        endpointReason = EndpointReason.FAILED,
                        audioDurationMs = audioDurationMs,
                        recognitionLatencyMs = 0,
                        transcript = null,
                    ),
                )
            },
        )
    }
}
