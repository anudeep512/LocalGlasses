package com.localglasses.app.agent.dictation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.localglasses.app.LocalGlassesApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Screen-facing surface over [DictationController].
 *
 * The scope is injected so the tap behaviour is unit-testable; production passes
 * [viewModelScope]. The screen forwards only a [DictationOutcome.Transcript] to the command
 * field, which keeps `AgentViewModel` the single owner of the command text.
 */
class DictationViewModel(
    private val controllerFactory: () -> DictationController,
    private val downloadModel: (suspend ((Long, Long) -> Unit) -> Result<Unit>)? = null,
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    private val controller: DictationController by lazy(controllerFactory)

    private val _outcomes = MutableSharedFlow<DictationOutcome>(extraBufferCapacity = 4)
    val outcomes: SharedFlow<DictationOutcome> = _outcomes.asSharedFlow()

    private val _needsModel = MutableStateFlow<Long?>(null)
    val needsModel: StateFlow<Long?> = _needsModel.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Pair<Long, Long>?>(null)
    val downloadProgress: StateFlow<Pair<Long, Long>?> = _downloadProgress.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var job: Job? = null

    /**
     * Set before the coroutine starts and cleared in its finally block.
     *
     * A field rather than `job?.isActive` because `job` is only assigned after `launch`
     * returns, so an eagerly dispatched coroutine would make the second tap start another
     * utterance instead of ending the first.
     */
    @Volatile
    private var running = false

    val state: StateFlow<DictationState>
        get() = controller.state

    val level: StateFlow<Float>
        get() = controller.level

    val isDictating: Boolean
        get() = running

    private val coroutineScope: CoroutineScope
        get() = scope ?: viewModelScope

    /** One tap starts an utterance; a second tap asks the current one to end. */
    fun onMicTap() {
        if (running) {
            controller.requestStop()
            return
        }
        running = true
        _message.value = null
        job = coroutineScope.launch {
            try {
                val outcome = controller.run()
                when (outcome) {
                    is DictationOutcome.NeedsModel -> _needsModel.value = outcome.sizeBytes
                    is DictationOutcome.NoSpeech -> _message.value = outcome.message
                    is DictationOutcome.Unavailable -> _message.value = outcome.message
                    is DictationOutcome.Failed -> _message.value = outcome.message
                    is DictationOutcome.Transcript -> Unit
                }
                _outcomes.emit(outcome)
            } finally {
                running = false
            }
        }
    }

    /** Downloads the pinned speech model after the user accepts the prompt. */
    fun downloadSpeechModel() {
        val download = downloadModel ?: return
        if (job?.isActive == true) return
        running = true
        _message.value = null
        job = coroutineScope.launch {
            try {
                val result = download { written, total ->
                    _downloadProgress.value = written to total
                }
                result.fold(
                    onSuccess = { _needsModel.value = null },
                    onFailure = { error ->
                        _message.value = error.message ?: "Speech model download failed"
                    },
                )
            } finally {
                _downloadProgress.value = null
                running = false
            }
        }
    }

    fun dismissModelPrompt() {
        _needsModel.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    override fun onCleared() {
        job?.cancel()
    }

    companion object {
        /** Production wiring: container engines, the saved source choice, and the model download. */
        fun factory(context: Context): ViewModelProvider.Factory {
            val app = context.applicationContext as LocalGlassesApp
            return viewModelFactory {
                initializer {
                    val container = app.agentContainer
                    val preference = DictationSourcePreference(app)
                    DictationViewModel(
                        controllerFactory = {
                            DictationController(
                                audioSource = { DictationSources.resolve(app, preference) },
                                engine = container.dictationEngineHolder,
                                detectorFactory = { SileroSpeechDetector(app) },
                                metricsFactory = container.speechMetricsFactory,
                                modelState = { container.speechModelRepository.state.value },
                                modelSizeBytes = container.speechModelRepository.totalBytes,
                            )
                        },
                        downloadModel = { onProgress ->
                            container.speechModelRepository.download(onProgress)
                        },
                    )
                }
            }
        }
    }
}
