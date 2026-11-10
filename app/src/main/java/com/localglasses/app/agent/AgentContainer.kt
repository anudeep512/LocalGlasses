package com.localglasses.app.agent

import android.content.Context
import com.localglasses.app.agent.approval.PlanCoordinator
import com.localglasses.app.agent.actions.ActionRegistry
import com.localglasses.app.agent.actions.AndroidContactSource
import com.localglasses.app.agent.actions.ContactsResolver
import com.localglasses.app.agent.actions.SettingsController
import com.localglasses.app.agent.actions.WhatsAppSender
import com.localglasses.app.agent.actions.WhatsAppUiPlanFactory
import com.localglasses.app.agent.actions.YouTubePlayer
import com.localglasses.app.agent.commands.CommandRouter
import com.localglasses.app.agent.model.AndroidMemorySource
import com.localglasses.app.agent.model.EngineHolder
import com.localglasses.app.agent.model.LiteRtEngine
import com.localglasses.app.agent.model.MemoryGuard
import com.localglasses.app.agent.model.ModelRepository
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

    val modelRepository: ModelRepository by lazy { ModelRepository(appContext) }

    val memoryGuard: MemoryGuard by lazy {
        MemoryGuard(availableBytes = { AndroidMemorySource.availableBytes(appContext) })
    }

    val engineHolder: EngineHolder by lazy {
        EngineHolder(
            factory = {
                LiteRtEngine(
                    modelFile = modelRepository.modelFile,
                    cacheDir = appContext.cacheDir,
                    modelId = modelRepository.manifest.id,
                    backendName = "CPU",
                )
            },
            canLoad = { memoryGuard.canLoad() },
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
                        backend = "CPU",
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
