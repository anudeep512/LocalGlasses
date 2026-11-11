package com.localglasses.app.agent.model

import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One downloaded artefact with a pinned size and checksum.
 *
 * Kept as an interface so a manifest (a serialized descriptor) and a speech-model entry
 * share one download path instead of duplicating resume and verification logic.
 */
interface DownloadSpec {
    val url: String
    val sizeBytes: Long
    val sha256: String
}

/**
 * Owns one file on disk: its partial download, its checksum, and the marker that records a
 * successful verification so a normal app start does not re-hash a large artefact.
 *
 * Holds no Android types and no state flow, so both the agent model and the speech model can
 * wrap it with the state shape their screens need.
 */
class VerifiedFileRepository(
    val file: File,
    private val spec: DownloadSpec,
    private val downloader: ModelDownloader = ModelDownloader(),
) {

    private val markerFile = File(file.path + MARKER_SUFFIX)
    private val downloadMutex = Mutex()

    fun hasFile(): Boolean = file.isFile

    fun isReady(): Boolean = file.isFile && markerMatches()

    fun markerMatches(): Boolean =
        markerFile.isFile &&
            markerFile.readText().trim().equals(spec.sha256, ignoreCase = true)

    /** Drops a stale partial left behind by an interrupted download. */
    fun discardPartial() {
        File(file.path + ModelDownloader.PART_SUFFIX).delete()
    }

    /**
     * Verifies a present file against the pinned checksum, writing or clearing the marker.
     *
     * Returns false when the file is missing, corrupt, or was deleted as a result.
     */
    suspend fun verifyExisting(): Boolean {
        if (!file.isFile) return false
        if (markerMatches()) {
            discardPartial()
            return true
        }
        return if (ModelDownloader.verify(file, spec.sha256)) {
            markerFile.writeText(spec.sha256)
            true
        } else {
            file.delete()
            markerFile.delete()
            false
        }
    }

    /**
     * Downloads the artefact unless it is already verified.
     *
     * The marker is written only after the downloader has checked size and checksum, so a
     * returned success always means the bytes on disk match the pinned hash.
     */
    suspend fun download(
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = downloadMutex.withLock {
        if (isReady()) return@withLock Result.success(file)
        var reportedTotal = spec.sizeBytes
        val result = downloader.download(spec, file) { written, total ->
            reportedTotal = total
            onProgress(written, reportedTotal)
        }
        result.onSuccess { markerFile.writeText(spec.sha256) }
        result
    }

    companion object {
        const val MARKER_SUFFIX = ".sha256"
    }
}
