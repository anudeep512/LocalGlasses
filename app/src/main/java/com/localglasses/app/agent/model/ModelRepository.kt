package com.localglasses.app.agent.model

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the agent model file and the observable download state.
 *
 * Download, checksum, and verified-marker handling live in [VerifiedFileRepository] so the
 * speech model can reuse them. This class keeps the app-specific parts: the pinned manifest,
 * the on-disk location, the state flow the UI observes, and the WorkManager request.
 */
class ModelRepository(
    context: Context,
    private val downloader: ModelDownloader = ModelDownloader(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val appContext = context.applicationContext
    val manifest: ModelManifest = loadManifest(appContext)
    val modelFile: File = File(appContext.filesDir, "models/${manifest.fileName}")
    private val verified = VerifiedFileRepository(modelFile, manifest, downloader)

    private val _state = MutableStateFlow<ModelState>(ModelState.Absent)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    init {
        scope.launch { refresh() }
    }

    suspend fun refresh() {
        if (!verified.hasFile()) {
            _state.value = ModelState.Absent
            return
        }
        if (verified.markerMatches()) {
            verified.discardPartial()
            _state.value = ModelState.Ready
            return
        }
        _state.value = ModelState.Verifying
        _state.value = if (verified.verifyExisting()) ModelState.Ready else ModelState.Absent
    }

    suspend fun download(
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> {
        if (isReady()) return Result.success(modelFile)
        _state.value = ModelState.Downloading(0, manifest.sizeBytes)
        val result = verified.download { written, total ->
            _state.value = ModelState.Downloading(written, total)
            onProgress(written, total)
        }
        result.fold(
            onSuccess = { _state.value = ModelState.Ready },
            onFailure = { error ->
                _state.value = ModelState.Failed(error.message ?: "Model download failed")
            },
        )
        return result
    }

    fun enqueueDownload() {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            ModelDownloadWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun isReady(): Boolean = _state.value == ModelState.Ready && modelFile.isFile

    private fun loadManifest(context: Context): ModelManifest {
        val raw = context.assets.open("model.json").bufferedReader().use { it.readText() }
        return ModelManifestParser.parse(raw).getOrElse { error ->
            throw IllegalStateException("Invalid model manifest", error)
        }
    }
}
