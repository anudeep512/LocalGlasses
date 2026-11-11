package com.localglasses.app.agent.model

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resumable, checksum-verified model download.
 *
 * Kept free of Android types so the resume and verification paths can be tested with
 * MockWebServer on the JVM. The partial file survives cancellation so a later attempt
 * can continue from the bytes already stored.
 */
class ModelDownloader(
    private val client: OkHttpClient = OkHttpClient(),
    private val freeSpace: (File) -> Long = { it.usableSpace },
) {

    suspend fun download(
        spec: DownloadSpec,
        target: File,
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            downloadBlocking(spec, target, onProgress)
            Result.success(target)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun downloadBlocking(
        spec: DownloadSpec,
        target: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        val directory = target.parentFile ?: throw IOException("model target has no parent")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("cannot create ${directory.absolutePath}")
        }

        val partial = File(target.path + PART_SUFFIX)
        var existing = if (partial.exists()) partial.length() else 0L
        if (existing > spec.sizeBytes) {
            partial.delete()
            existing = 0L
        }

        val required = (spec.sizeBytes - existing).coerceAtLeast(0L) + FREE_SPACE_MARGIN_BYTES
        val available = freeSpace(directory)
        if (available < required) {
            throw IOException(
                "Not enough free space: need ${required / 1_000_000} MB, " +
                    "have ${available / 1_000_000} MB",
            )
        }

        if (existing < spec.sizeBytes) {
            val request = Request.Builder()
                .url(spec.url)
                .header("Range", "bytes=$existing-")
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                if (code == 416 && existing == spec.sizeBytes) {
                    // The partial file is already complete; verification below decides.
                } else if (code != 200 && code != 206) {
                    throw IOException("Model download failed with HTTP $code")
                } else {
                    val append = code == 206
                    if (!append && existing > 0L) {
                        // Server ignored Range; restart rather than corrupt the file.
                        partial.delete()
                        existing = 0L
                    }
                    val body = response.body
                    var written = existing
                    body.byteStream().use { input ->
                        FileOutputStream(partial, append).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                written += read
                                onProgress(written, spec.sizeBytes)
                            }
                            output.fd.sync()
                        }
                    }
                }
            }
        }

        if (partial.length() != spec.sizeBytes) {
            throw IOException(
                "Model size mismatch: expected ${spec.sizeBytes}, got ${partial.length()}",
            )
        }

        if (!verify(partial, spec.sha256)) {
            partial.delete()
            throw IOException("Model checksum mismatch")
        }

        if (target.exists() && !target.delete()) {
            throw IOException("cannot replace ${target.absolutePath}")
        }
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
    }

    companion object {
        const val PART_SUFFIX = ".part"
        private const val FREE_SPACE_MARGIN_BYTES = 256L * 1024 * 1024

        fun verify(file: File, expectedSha256: String): Boolean {
            if (!file.isFile) return false
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            return actual.equals(expectedSha256, ignoreCase = true)
        }
    }
}
