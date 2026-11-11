package com.localglasses.app.agent.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pinned description of the on-device model artefact.
 *
 * Kept in `assets/model.json` so a model update does not require code changes. The
 * checksum comes from the Hugging Face `x-linked-etag` for the file and is verified
 * after download before the engine is allowed to load it.
 */
@Serializable
data class ModelManifest(
    val id: String,
    val fileName: String,
    override val url: String,
    override val sizeBytes: Long,
    override val sha256: String,
) : DownloadSpec {
    val sha256Bytes: ByteArray
        get() = sha256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

object ModelManifestParser {

    private val json = Json { ignoreUnknownKeys = false }

    fun parse(raw: String): Result<ModelManifest> = runCatching {
        val manifest = json.decodeFromString<ModelManifest>(raw)
        require(manifest.id.isNotBlank()) { "model id is blank" }
        require(manifest.fileName.endsWith(".litertlm")) { "unexpected model file name" }
        require(manifest.url.startsWith("https://")) { "model URL must be https" }
        require(manifest.sizeBytes > 0) { "model size must be positive" }
        require(manifest.sha256.matches(Regex("[0-9a-f]{64}"))) { "model sha256 is malformed" }
        manifest
    }
}
