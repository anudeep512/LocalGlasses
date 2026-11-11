package com.localglasses.app.agent.commands

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localglasses.app.LocalGlassesApp
import com.localglasses.app.agent.actions.ActionExecution
import com.localglasses.app.agent.automation.AgentAccessibilityService
import com.localglasses.app.agent.automation.AndroidAccessibilityGateway
import com.localglasses.app.agent.automation.PlanExecutor
import com.localglasses.app.agent.automation.UiPlanner
import com.localglasses.app.agent.automation.ScreenshotTaker
import com.localglasses.app.agent.approval.Plan
import com.localglasses.app.agent.approval.PlanCoordinator
import com.localglasses.app.agent.approval.PlanFactory
import com.localglasses.app.agent.approval.PlanReplanner
import com.localglasses.app.agent.approval.PlanState
import com.localglasses.app.agent.approval.PlanStep
import com.localglasses.app.agent.model.AgentWarmService
import com.localglasses.app.agent.PermissionsCoordinator
import com.localglasses.app.agent.model.ModelState
import com.localglasses.app.agent.telemetry.DeviceSnapshot
import com.localglasses.app.agent.telemetry.InferenceMetrics
import com.localglasses.app.agent.telemetry.MetricsCollector
import com.localglasses.app.agent.telemetry.RequestMetrics
import com.localglasses.app.agent.telemetry.PendingSpeechMetrics
import com.localglasses.app.agent.telemetry.RequestMetricsSession
import com.localglasses.app.agent.telemetry.SpeechMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AgentUiState(
    val command: String = "",
    val status: String = "",
    val isSubmitting: Boolean = false,
    val suggestions: List<String> = emptyList(),
)

class AgentViewModel @JvmOverloads constructor(
    app: Application,
    private val routerOverride: CommandRouter? = null,
    private val modelStateOverride: StateFlow<ModelState>? = null,
    private val planCoordinatorOverride: PlanCoordinator? = null,
    private val accessibilityOverride: Boolean? = null,
    private val metricsCollectorOverride: MetricsCollector? = null,
) : AndroidViewModel(app) {

    private val container = (app as LocalGlassesApp).agentContainer
    private val router = routerOverride ?: container.commandRouter
    private val coordinator = planCoordinatorOverride ?: container.planCoordinator
    private val metricsCollector = metricsCollectorOverride ?: container.metricsCollector

    val modelState: StateFlow<ModelState> = modelStateOverride ?: container.modelRepository.state
    val planState: StateFlow<PlanState> = coordinator.state

    private val accessibilityAvailable: Boolean
        get() = accessibilityOverride
            ?: (AgentAccessibilityService.instance != null ||
                AgentAccessibilityService.isEnabled(getApplication()))

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private val _liveMetrics = MutableStateFlow<DeviceSnapshot?>(null)
    val liveMetrics: StateFlow<DeviceSnapshot?> = _liveMetrics.asStateFlow()

    private val _lastRequestMetrics = MutableStateFlow<RequestMetrics?>(null)
    val lastRequestMetrics: StateFlow<RequestMetrics?> = _lastRequestMetrics.asStateFlow()

    private var activeTraceId: String? = null
    private var activeMetricsSession: RequestMetricsSession? = null
    private var activeInferenceMetrics: InferenceMetrics? = null
    private var commandStartedAtMs: Long? = null

    private val pendingSpeech = PendingSpeechMetrics()

    /**
     * Metrics of the most recent dictation, published as soon as recognition finishes.
     *
     * The stats block shows these before the command is submitted, so the Speech Recognition
     * section is populated the moment speech has been processed.
     */
    private val _lastDictationMetrics = MutableStateFlow<SpeechMetrics?>(null)
    val lastDictationMetrics: StateFlow<SpeechMetrics?> = _lastDictationMetrics.asStateFlow()
    private var activeSpeechMetrics: SpeechMetrics? = null

    init {
        viewModelScope.launch {
            modelState.collect { state ->
                if (state == ModelState.Ready && modelStateOverride == null) {
                    AgentWarmService.start(getApplication())
                    // Load the model while the user is still typing instead of on Run.
                    prewarmEngine()
                }
            }
        }
    }

    fun onCommandChange(value: String) {
        pendingSpeech.onTextEdited(value)
        _uiState.update { it.copy(command = value) }
    }

    /**
     * Applies a dictation result: the field takes the transcript and the metrics stay pending
     * until this exact text is submitted.
     */
    fun onDictationFinished(transcript: String, metrics: SpeechMetrics?) {
        if (metrics != null) _lastDictationMetrics.value = metrics
        pendingSpeech.record(transcript, metrics)
        _uiState.update { it.copy(command = transcript) }
    }

    /**
     * Loads the engine in the background so the first command does not pay the whole model
     * initialisation.
     *
     * GPU initialisation takes several seconds on a cold process; overlapping it with the time
     * the user spends typing removes it from the perceived latency. Safe to call repeatedly.
     */
    fun prewarmEngine() {
        if (modelState.value != ModelState.Ready) return
        if (container.engineHolder.isLoaded) return
        viewModelScope.launch {
            val result = runCatching { container.engineHolder.ensureLoaded() }
            result.getOrNull()?.exceptionOrNull()?.let { error ->
                android.util.Log.w(TAG, "prewarm could not load the engine: ${error.message}")
            }
        }
    }

    /** The visible Agent surface owns collection and forwards each sample here. */
    fun onLiveSample(snapshot: DeviceSnapshot) {
        _liveMetrics.value = snapshot
        activeMetricsSession?.observe(snapshot)
    }

    fun metricsStream(): Flow<DeviceSnapshot> = metricsCollector.stream()

    fun submit() {
        val command = _uiState.value.command.trim()
        if (command.isBlank()) {
            setStatus("Enter a command")
            return
        }
        if (planState.value is PlanState.Pending || planState.value is PlanState.Executing) {
            setStatus("Approve or cancel the pending plan first")
            return
        }
        if (modelState.value != ModelState.Ready) {
            setStatus("Model is not ready")
            return
        }

        _uiState.update {
            it.copy(isSubmitting = true, status = "Thinking…", suggestions = emptyList())
        }
        _lastRequestMetrics.value = null
        activeInferenceMetrics = null
        activeSpeechMetrics = pendingSpeech.consumeForRequest(command)
        commandStartedAtMs = System.currentTimeMillis()
        activeTraceId = container.traceRecorder.start(command)

        viewModelScope.launch {
            activeMetricsSession = metricsCollector.beginRequest()
            when (val decision = router.route(command)) {
                is RoutingDecision.Action -> {
                    activeInferenceMetrics = decision.metrics
                    val plan = PlanFactory.fromAction(command, decision.request)
                    recordDecision(
                        command = command,
                        taskType = "action",
                        targetPackage = null,
                        modelPrompt = null,
                        toolCallsJson = decision.request.toString(),
                        planJson = plan.steps.toString(),
                        outcome = "pending_approval",
                        inference = decision.metrics,
                    )
                    if (coordinator.offer(plan)) {
                        setStatus("Plan ready for review")
                    } else {
                        setStatus("A plan is already pending")
                    }
                }
                is RoutingDecision.UiPlan -> {
                    activeInferenceMetrics = decision.metrics
                    if (!accessibilityAvailable) {
                        setStatus("Accessibility service is not enabled")
                        finishActiveTrace(
                            command, "ui_plan", decision.app, "needs_permission", "accessibility",
                        )
                        return@launch
                    }
                    val planResult = container.whatsAppUiPlanFactory
                        .build(command, decision.goal, decision.app)
                        ?.let { Result.success(it) }
                        ?: UiPlanner(container.engineHolder).plan(command, decision.goal, decision.app)
                    planResult.fold(
                        onSuccess = { plan ->
                            recordDecision(
                                command = command,
                                taskType = "ui_plan",
                                targetPackage = decision.app,
                                modelPrompt = UiPlanner.SYSTEM_PROMPT,
                                toolCallsJson = decision.goal,
                                planJson = plan.steps.toString(),
                                outcome = "pending_approval",
                                inference = decision.metrics,
                            )
                            if (coordinator.offer(plan)) {
                                setStatus("Plan ready for review")
                            } else {
                                setStatus("A plan is already pending")
                            }
                        },
                        onFailure = { error ->
                            setStatus("Could not plan: ${error.message}")
                            finishActiveTrace(
                                command, "ui_plan", decision.app, "failed", error.message,
                            )
                        },
                    )
                }
                is RoutingDecision.NoMatch -> {
                    activeInferenceMetrics = decision.metrics
                    recordDecision(
                        command = command,
                        taskType = "no_match",
                        targetPackage = null,
                        modelPrompt = null,
                        toolCallsJson = null,
                        planJson = null,
                        outcome = "no_match",
                        inference = decision.metrics,
                    )
                    finishActiveTrace(command, "no_match", null, "no_match")
                    val suggestions = decision.suggestions.joinToString(", ")
                    setStatus(
                        buildString {
                            append("Not understood. ")
                            if (suggestions.isNotBlank()) append("Try: $suggestions")
                        },
                    )
                    _uiState.update { it.copy(suggestions = decision.suggestions) }
                }
                is RoutingDecision.Blocked -> {
                    activeInferenceMetrics = decision.metrics
                    recordDecision(
                        command = command,
                        taskType = "blocked",
                        targetPackage = null,
                        modelPrompt = null,
                        toolCallsJson = null,
                        planJson = null,
                        outcome = "blocked",
                        inference = decision.metrics,
                    )
                    finishActiveTrace(command, "blocked", null, "blocked", decision.reason)
                    setStatus("Blocked: ${decision.reason}")
                }
                is RoutingDecision.Failed -> {
                    activeInferenceMetrics = decision.metrics
                    recordDecision(
                        command = command,
                        taskType = "failed",
                        targetPackage = null,
                        modelPrompt = null,
                        toolCallsJson = null,
                        planJson = null,
                        outcome = "failed",
                        inference = decision.metrics,
                    )
                    finishActiveTrace(command, "failed", null, "failed", decision.reason)
                    setStatus("Failed: ${decision.reason}")
                }
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun approvePlan() {
        val plan = coordinator.approve() ?: return
        viewModelScope.launch {
            container.safetyPolicy.checkPlan(plan).takeIf { it.blocked }?.let { verdict ->
                val reason = verdict.reason ?: "Blocked by safety policy"
                coordinator.fail(reason)
                finishActiveTrace(plan.command, "blocked", plan.targetPackage, "blocked", reason)
                setStatus("Blocked: $reason")
                return@launch
            }
            val step = plan.steps.singleOrNull() as? PlanStep.RunAction
            if (step == null) {
                executeUiPlan(plan)
                return@launch
            }
            val result = when (step.actionId) {
                "send_whatsapp" -> {
                    val service = AgentAccessibilityService.instance
                    if (service == null) {
                        ActionExecution.NeedsPermission("accessibility")
                    } else {
                        container.whatsAppSender.send(
                            contactQuery = step.arguments["contact"]?.toString(),
                            message = step.arguments["message"]?.toString(),
                            gateway = AndroidAccessibilityGateway(service),
                        )
                    }
                }
                "play_media" -> {
                    val service = AgentAccessibilityService.instance
                    if (service == null) {
                        ActionExecution.NeedsPermission("accessibility")
                    } else {
                        container.youTubePlayer.play(
                            query = step.arguments["query"]?.toString(),
                            service = step.arguments["service"]?.toString(),
                            gateway = AndroidAccessibilityGateway(service),
                        )
                    }
                }
                else -> container.actionRegistry.execute(step.actionId, step.arguments)
            }
            when (result) {
                is ActionExecution.Success -> {
                    coordinator.complete()
                    activeTraceId?.let { traceId ->
                        container.traceRecorder.step(
                            traceId, 0, step.toString(), null, "success", 0,
                        )
                    }
                    finishActiveTrace(plan.command, "action", null, "success")
                    setStatus(result.message)
                }
                is ActionExecution.NeedsPermission -> {
                    coordinator.fail("Missing permission: ${result.permission}")
                    finishActiveTrace(
                        plan.command, "action", null, "needs_permission", result.permission,
                    )
                    setStatus("Missing permission: ${result.permission}")
                }
                is ActionExecution.NeedsChoice -> {
                    val names = result.candidates.joinToString(", ") { it.name }
                    coordinator.fail("Multiple contacts: $names")
                    finishActiveTrace(plan.command, "action", null, "needs_choice", names)
                    setStatus("Multiple contacts: $names")
                }
                is ActionExecution.Failed -> {
                    coordinator.fail(result.reason)
                    activeTraceId?.let { traceId -> captureFailure(traceId) }
                    finishActiveTrace(plan.command, "action", null, "failed", result.reason)
                    setStatus(result.reason)
                }
            }
        }
    }

    fun cancelPlan() {
        val command = (planState.value as? PlanState.Pending)?.plan?.command.orEmpty()
        coordinator.cancel()
        finishActiveTrace(command, "action", null, "cancelled")
        setStatus("Plan cancelled")
    }

    fun replan(correction: String) {
        val pending = planState.value as? PlanState.Pending ?: return
        if (correction.isBlank()) return
        viewModelScope.launch {
            val replanned = PlanReplanner { prompt -> router.route(prompt) }
                .replan(pending.plan, correction)
            if (replanned == null) {
                setStatus("Could not re-plan; approve or cancel the current plan")
            } else {
                coordinator.cancel()
                coordinator.offer(replanned)
                setStatus("Plan updated; approve it again")
            }
        }
    }

    fun updatePlan(transform: (Plan) -> Plan) {
        coordinator.update(transform)
        setStatus("Plan updated; approve it again")
    }

    fun downloadModel() {
        container.modelRepository.enqueueDownload()
    }

    fun retryModel() {
        container.modelRepository.enqueueDownload()
    }

    private suspend fun executeUiPlan(plan: Plan) {
        val service = if (accessibilityAvailable) AgentAccessibilityService.instance else null
        if (service == null) {
            coordinator.fail("Accessibility service is not enabled")
            finishActiveTrace(
                plan.command, "ui_plan", plan.targetPackage, "needs_permission", "accessibility",
            )
            setStatus("Accessibility service is not enabled")
            return
        }
        val executor = PlanExecutor(
            gateway = AndroidAccessibilityGateway(service),
            safety = container.safetyPolicy,
        )
        val result = executor.execute(plan) { index, step ->
            activeTraceId?.let {
                container.traceRecorder.step(it, index, step.toString(), null, "running", 0)
            }
        }
        result.fold(
            onSuccess = {
                coordinator.complete()
                finishActiveTrace(plan.command, "ui_plan", plan.targetPackage, "success")
                setStatus("Plan complete")
            },
            onFailure = { error ->
                coordinator.fail(error.message ?: "UI plan failed")
                setStatus(error.message ?: "UI plan failed")
                activeTraceId?.let { traceId -> captureFailure(traceId) }
                finishActiveTrace(
                    plan.command, "ui_plan", plan.targetPackage, "failed", error.message,
                )
            },
        )
    }

    private fun recordDecision(
        command: String,
        taskType: String?,
        targetPackage: String?,
        modelPrompt: String?,
        toolCallsJson: String?,
        planJson: String?,
        outcome: String,
        inference: InferenceMetrics?,
    ) {
        val id = activeTraceId ?: return
        val metrics = snapshotMetrics(inference)
        container.traceRecorder.decision(
            id = id,
            command = command,
            taskType = taskType,
            targetPackage = targetPackage,
            modelPrompt = modelPrompt,
            toolCallsJson = toolCallsJson,
            planJson = planJson,
            outcome = outcome,
            metrics = metrics,
        )
        _lastRequestMetrics.value = metrics
    }

    private fun finishActiveTrace(
        command: String,
        taskType: String?,
        targetPackage: String?,
        outcome: String,
        errorReason: String? = null,
    ) {
        val id = activeTraceId
        val metrics = snapshotMetrics(activeInferenceMetrics)
        if (id != null) {
            container.traceRecorder.finish(
                id = id,
                command = command,
                taskType = taskType,
                targetPackage = targetPackage,
                outcome = outcome,
                errorReason = errorReason,
                metrics = metrics,
            )
        }
        if (metrics != null) _lastRequestMetrics.value = metrics
        activeTraceId = null
        activeMetricsSession = null
        activeInferenceMetrics = null
        activeSpeechMetrics = null
        commandStartedAtMs = null
    }

    private fun snapshotMetrics(inference: InferenceMetrics?): RequestMetrics? {
        val session = activeMetricsSession ?: return null
        val startedAt = commandStartedAtMs
        val duration = startedAt?.let {
            (System.currentTimeMillis() - it).coerceAtLeast(0L)
        } ?: 0L
        return session.finish(inference, durationMs = duration, speech = activeSpeechMetrics)
    }

    private fun captureFailure(traceId: String) {
        val foreground = container.foregroundApp.currentPackage()
        if (foreground != null && container.safetyPolicy.checkPackage(foreground).blocked) {
            return
        }
        if (!accessibilityAvailable) return
        val service = AgentAccessibilityService.instance ?: return
        ScreenshotTaker(service, container.screenshotStore)
            .capture("$traceId.png")
    }

    /** Status is text only. The app never speaks; voice output was removed. */
    private fun setStatus(message: String) {
        _uiState.update { it.copy(status = message) }
    }

    companion object {
        private const val TAG = "LocalGlassesPrewarm"

        fun statusText(state: ModelState): String = when (state) {
            ModelState.Absent -> "Model not downloaded"
            is ModelState.Downloading -> {
                val percent = if (state.total > 0) state.bytes * 100 / state.total else 0
                "Downloading model ${percent}%"
            }
            ModelState.Verifying -> "Verifying model"
            ModelState.Ready -> "Model ready"
            is ModelState.Failed -> "Model failed: ${state.message}"
        }
    }
}
