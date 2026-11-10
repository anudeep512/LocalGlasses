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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the model file, its verification marker, and the observable download state.
 *
 * A `.sha256` marker records the verified artefact, so a normal app start does not
 * re-hash 2.5 GB. If the marker is missing, the file is verified once before use.
 */
class ModelRepository(
    context: Context,
    private val downloader: ModelDownloader = ModelDownloader(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val appContext = context.applicationContext
    val manifest: ModelManifest = loadManifest(appContext)
    val modelFile: File = File(appContext.filesDir, "models/${manifest.fileName}")
    private val markerFile = File(modelFile.path + ".sha256")
    private val downloadMutex = Mutex()

    private val _state = MutableStateFlow<ModelState>(ModelState.Absent)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    init {
        scope.launch { refresh() }
    }

    suspend fun refresh() {
        if (!modelFile.isFile) {
            _state.value = ModelState.Absent
            return
        }

        val markerMatches = markerFile.isFile &&
            markerFile.readText().trim().equals(manifest.sha256, ignoreCase = true)
        if (markerMatches) {
            File(modelFile.path + ModelDownloader.PART_SUFFIX).delete()
            _state.value = ModelState.Ready
            return
        }

        _state.value = ModelState.Verifying
        val verified = ModelDownloader.verify(modelFile, manifest.sha256)
        if (verified) {
            markerFile.writeText(manifest.sha256)
            _state.value = ModelState.Ready
        } else {
            modelFile.delete()
            markerFile.delete()
            _state.value = ModelState.Absent
        }
    }

    suspend fun download(
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = downloadMutex.withLock {
        if (isReady()) {
            return@withLock Result.success(modelFile)
        }
        _state.value = ModelState.Downloading(0, manifest.sizeBytes)
        val result = downloader.download(manifest, modelFile) { written, total ->
            _state.value = ModelState.Downloading(written, total)
            onProgress(written, total)
        }
        result.fold(
            onSuccess = {
                markerFile.writeText(manifest.sha256)
                _state.value = ModelState.Ready
            },
            onFailure = { error ->
                _state.value = ModelState.Failed(error.message ?: "Model download failed")
            },
        )
        result
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
