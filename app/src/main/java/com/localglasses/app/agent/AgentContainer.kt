package com.localglasses.app.agent

import android.content.Context
import java.io.File
import com.localglasses.app.agent.approval.PlanCoordinator
import com.localglasses.app.agent.actions.ActionRegistry
import com.localglasses.app.agent.actions.AndroidContactSource
import com.localglasses.app.agent.actions.ContactsResolver
import com.localglasses.app.agent.actions.SettingsController
import com.localglasses.app.agent.actions.WhatsAppSender
import com.localglasses.app.agent.actions.WhatsAppUiPlanFactory
import com.localglasses.app.agent.actions.YouTubePlayer
import com.localglasses.app.agent.commands.CommandRouter
import com.localglasses.app.agent.dictation.DictationEngineHolder
import com.localglasses.app.agent.dictation.SherpaWhisperRecognizer
import com.localglasses.app.agent.dictation.SpeechMetricsFactory
import com.localglasses.app.agent.model.AndroidMemorySource
import com.localglasses.app.agent.model.EngineHolder
import com.localglasses.app.agent.model.FallbackEngine
import com.localglasses.app.agent.model.LiteRtEngine
import com.localglasses.app.agent.model.MemoryGuard
import com.localglasses.app.agent.model.ModelRepository
import com.localglasses.app.agent.model.SpeechModelRepository
import com.localglasses.app.agent.safety.SafetyPolicy
import com.localglasses.app.agent.automation.ForegroundApp
import com.localglasses.app.agent.telemetry.AndroidMetricsSource
import com.localglasses.app.agent.telemetry.MetricsCollector
import com.localglasses.app.agent.telemetry.ModelRuntimeInfo
import com.localglasses.app.agent.trace.ScreenshotStore
import com.localglasses.app.agent.trace.TraceExporter
import com.localglasses.app.agent.trace.TraceRecorder
import com.localglasses.app.agent.trace.TraceDatabase

/**
 * Manual service locator for the agent layer.
 *
 * The app is a single module with a handful of process-wide collaborators, so a DI
 * framework would add build complexity without a runtime benefit. Everything the UI,
 * services, and the accessibility layer need is created here and owned by the
 * Application.
 */
class AgentContainer(context: Context) {

    val appContext: Context = context.applicationContext

    private companion object {
        const val GPU_CACHE_DIR = "gpu-cache"
    }

    val modelRepository: ModelRepository by lazy { ModelRepository(appContext) }

    /** Whisper small.en, downloaded on first dictation. */
    val speechModelRepository: SpeechModelRepository by lazy {
        SpeechModelRepository.fromContext(appContext)
    }

    val memoryGuard: MemoryGuard by lazy {
        MemoryGuard(availableBytes = { AndroidMemorySource.availableBytes(appContext) })
    }

    val engineHolder: EngineHolder by lazy {
        EngineHolder(
            factory = {
                // GPU first: LiteRT-LM accelerates Gemma on the device GPU (Mali-G615 MC6 on
                // the CPH2717). The first load on a device compiles OpenCL kernels and took
                // ~103 s once; later loads reuse the cache in ~9 s. Some devices ship an
                // incomplete OpenCL driver, so a CPU engine backs it up and telemetry reports
                // whichever backend actually loaded.
                FallbackEngine(
                    primary = {
                        LiteRtEngine(
                            modelFile = modelRepository.modelFile,
                            // GPU weight cache is ~780 MB. It lives in filesDir, not cacheDir,
                            // because Android may evict cacheDir at any time and rebuilding it
                            // costs a ~100 s OpenCL compile on the next command.
                            cacheDir = File(appContext.filesDir, GPU_CACHE_DIR),
                            modelId = modelRepository.manifest.id,
                            backend = com.google.ai.edge.litertlm.Backend.GPU(),
                            backendName = FallbackEngine.PREFERRED_NAME,
                        )
                    },
                    secondary = {
                        LiteRtEngine(
                            modelFile = modelRepository.modelFile,
                            cacheDir = appContext.cacheDir,
                            modelId = modelRepository.manifest.id,
                            backend = com.google.ai.edge.litertlm.Backend.CPU(),
                            backendName = "CPU",
                        )
                    },
                    onFallback = { error ->
                        android.util.Log.w(
                            "LocalGlassesEngine",
                            "GPU backend failed, running on CPU: ${error.message}",
                        )
                        runCatching {
                            java.io.File(appContext.filesDir, "engine.log").writeText(
                                System.currentTimeMillis().toString() +
                                    " GPU failed: " + error::class.java.simpleName +
                                    ": " + error.message + "\n" +
                                    error.stackTraceToString().take(1200) + "\n",
                            )
                        }
                    },
                )
            },
            canLoad = { memoryGuard.canLoad() },
        )
    }

    /**
     * Whisper engine, created on the first dictation and released under memory pressure.
     * Shares the agent model's [MemoryGuard] policy.
     */
    val dictationEngineHolder: DictationEngineHolder by lazy {
        DictationEngineHolder(
            factory = {
                SherpaWhisperRecognizer(paths = speechModelRepository.paths())
            },
            canLoad = { memoryGuard.canLoad() },
        )
    }

    val speechMetricsFactory: SpeechMetricsFactory by lazy {
        SpeechMetricsFactory(
            modelId = speechModelRepository.manifest.id,
            peakPssReader = {
                android.os.Debug.MemoryInfo().also { info ->
                    android.os.Debug.getMemoryInfo(info)
                }.totalPss.toLong() * 1024L
            },
        )
    }

    val metricsCollector: MetricsCollector by lazy {
        MetricsCollector(
            AndroidMetricsSource(
                context = appContext,
                modelInfo = {
                    val ready = modelRepository.isReady()
                    ModelRuntimeInfo(
                        modelId = if (ready) modelRepository.manifest.id else null,
                        modelFileBytes = if (ready) modelRepository.manifest.sizeBytes else null,
                        backend = engineHolder.backendNameOrNull(),
                        engineLoaded = engineHolder.isLoaded,
                    )
                },
            ),
        )
    }

    val safetyPolicy: SafetyPolicy by lazy { SafetyPolicy.fromAssets(appContext) }

    val planCoordinator: PlanCoordinator by lazy { PlanCoordinator() }

    val ttsSpeaker: TtsSpeaker by lazy { TtsSpeaker(appContext) }

    val commandRouter: CommandRouter by lazy {
        CommandRouter(engineHolder, safetyPolicy)
    }

    val traceDatabase: TraceDatabase by lazy { TraceDatabase(appContext) }

    val traceRecorder: TraceRecorder by lazy { TraceRecorder(traceDatabase) }

    val traceExporter: TraceExporter by lazy { TraceExporter(appContext, traceDatabase) }

    val screenshotStore: ScreenshotStore by lazy {
        ScreenshotStore(java.io.File(appContext.filesDir, "traces/shots"))
    }

    val foregroundApp: ForegroundApp by lazy { ForegroundApp(appContext) }

    val contactsResolver: ContactsResolver by lazy {
        ContactsResolver(AndroidContactSource(appContext))
    }

    val settingsController: SettingsController by lazy { SettingsController(appContext) }

    val actionRegistry: ActionRegistry by lazy {
        ActionRegistry(appContext, contactsResolver, settingsController)
    }

    val whatsAppSender: WhatsAppSender by lazy {
        WhatsAppSender(appContext, contactsResolver)
    }

    val whatsAppUiPlanFactory: WhatsAppUiPlanFactory by lazy {
        WhatsAppUiPlanFactory(appContext, contactsResolver)
    }

    val youTubePlayer: YouTubePlayer by lazy { YouTubePlayer(appContext) }

    fun initialize() = Unit
}
