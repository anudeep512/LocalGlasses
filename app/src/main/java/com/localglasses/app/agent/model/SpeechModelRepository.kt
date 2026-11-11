package com.localglasses.app.agent.model

import android.content.Context
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Absolute paths the recogniser needs. All three files must exist and be verified. */
data class SpeechModelPaths(
    val encoder: String,
    val decoder: String,
    val tokens: String,
)

/**
 * Owns the three-file speech model: its directory, verification, and download state.
 *
 * All three files are required together, so the repository reports one state for the set and
 * only reaches [ModelState.Ready] when every file matches its pinned checksum.
 */
class SpeechModelRepository(
    private val directory: File,
    val manifest: SpeechModelManifest,
    private val downloader: ModelDownloader = ModelDownloader(),
    scope: CoroutineScope? = null,
) {

    private val verified: Map<String, VerifiedFileRepository> = manifest.allFiles().associate {
        it.fileName to VerifiedFileRepository(File(directory, it.fileName), it, downloader)
    }

    private val downloadMutex = Mutex()

    private val _state = MutableStateFlow<ModelState>(ModelState.Absent)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    /** Bytes the first-use download will transfer. */
    val totalBytes: Long = manifest.allFiles().sumOf { it.sizeBytes }

    init {
        val resolvedScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
        resolvedScope.launch { refresh() }
    }

    fun isReady(): Boolean =
        _state.value == ModelState.Ready && manifest.allFiles().all { file ->
            verified.getValue(file.fileName).isReady()
        }

    fun paths(): SpeechModelPaths = SpeechModelPaths(
        encoder = File(directory, manifest.files.encoder.fileName).absolutePath,
        decoder = File(directory, manifest.files.decoder.fileName).absolutePath,
        tokens = File(directory, manifest.files.tokens.fileName).absolutePath,
    )

    suspend fun refresh() {
        if (manifest.allFiles().none { verified.getValue(it.fileName).hasFile() }) {
            _state.value = ModelState.Absent
            return
        }
        if (manifest.allFiles().all { verified.getValue(it.fileName).isReady() }) {
            manifest.allFiles().forEach { verified.getValue(it.fileName).discardPartial() }
            _state.value = ModelState.Ready
            return
        }
        _state.value = ModelState.Verifying
        val allVerified = manifest.allFiles().all { verified.getValue(it.fileName).verifyExisting() }
        _state.value = if (allVerified) ModelState.Ready else ModelState.Absent
    }

    /**
     * Downloads any file that is not yet verified, reporting progress across the whole set.
     *
     * A cancellation leaves the state [ModelState.Absent] so the partial files can be resumed,
     * while a failure is reported as [ModelState.Failed].
     */
    suspend fun download(
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<Unit> = downloadMutex.withLock {
        if (isReady()) return@withLock Result.success(Unit)

        _state.value = ModelState.Downloading(0, totalBytes)
        var completed = 0L

        for (file in manifest.allFiles()) {
            val repository = verified.getValue(file.fileName)
            if (repository.isReady()) {
                completed += file.sizeBytes
                onProgress(completed, totalBytes)
                continue
            }
            val result = try {
                repository.download { written, _ ->
                    _state.value = ModelState.Downloading(completed + written, totalBytes)
                    onProgress(completed + written, totalBytes)
                }
            } catch (cancelled: CancellationException) {
                // Keep the partial file and report the set as not present, so a later attempt
                // resumes instead of showing a failure the user cannot act on.
                _state.value = ModelState.Absent
                throw cancelled
            }
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                if (error is CancellationException) {
                    _state.value = ModelState.Absent
                    throw error
                }
                _state.value = ModelState.Failed(error?.message ?: "Speech model download failed")
                return@withLock Result.failure(error ?: IllegalStateException("download failed"))
            }
            completed += file.sizeBytes
            onProgress(completed, totalBytes)
        }

        _state.value = ModelState.Ready
        Result.success(Unit)
    }

    companion object {
        const val ASSET_NAME = "speech-model.json"
        const val DIRECTORY = "models/speech"

        fun fromContext(
            context: Context,
            downloader: ModelDownloader = ModelDownloader(),
            scope: CoroutineScope? = null,
        ): SpeechModelRepository {
            val appContext = context.applicationContext
            val raw = appContext.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            val manifest = SpeechModelManifestParser.parse(raw).getOrElse { error ->
                throw IllegalStateException("Invalid speech model manifest", error)
            }
            return SpeechModelRepository(
                directory = File(appContext.filesDir, DIRECTORY),
                manifest = manifest,
                downloader = downloader,
                scope = scope,
            )
        }
    }
}
