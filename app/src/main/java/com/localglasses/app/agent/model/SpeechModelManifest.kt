package com.localglasses.app.agent.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One file of the speech model set, pinned to a size and a checksum.
 *
 * Whisper needs all three artefacts together: a missing or mismatched file produces unusable
 * output rather than a clean failure, so each one is verified independently.
 */
@Serializable
data class SpeechModelFile(
    val fileName: String,
    override val url: String,
    override val sizeBytes: Long,
    override val sha256: String,
) : DownloadSpec

@Serializable
data class SpeechModelFiles(
    val encoder: SpeechModelFile,
    val decoder: SpeechModelFile,
    val tokens: SpeechModelFile,
) {
    fun all(): List<SpeechModelFile> = listOf(encoder, decoder, tokens)
}

/**
 * Pinned description of the on-device speech model, kept in `assets/speech-model.json` so a
 * model update does not require a code change.
 */
@Serializable
data class SpeechModelManifest(
    val id: String,
    val language: String,
    val files: SpeechModelFiles,
) {
    fun allFiles(): List<SpeechModelFile> = files.all()
}

object SpeechModelManifestParser {

    private val json = Json { ignoreUnknownKeys = false }
    private val sha256Pattern = Regex("[0-9a-f]{64}")

    fun parse(raw: String): Result<SpeechModelManifest> = runCatching {
        val manifest = json.decodeFromString<SpeechModelManifest>(raw)
        require(manifest.id.isNotBlank()) { "speech model id is blank" }
        require(manifest.language.isNotBlank()) { "speech model language is blank" }

        val files = manifest.allFiles()
        files.forEach { file -> validate(file) }

        val names = files.map { it.fileName }
        require(names.toSet().size == names.size) { "speech model files must have distinct names" }

        manifest
    }

    private fun validate(file: SpeechModelFile) {
        require(file.fileName.isNotBlank()) { "speech model file name is blank" }
        require(
            file.fileName.endsWith(".onnx") || file.fileName.endsWith(".txt"),
        ) { "unexpected speech model file name: ${file.fileName}" }
        require(file.url.startsWith("https://")) { "speech model URL must be https" }
        require(file.sizeBytes > 0) { "speech model size must be positive" }
        require(sha256Pattern.matches(file.sha256)) { "speech model sha256 is malformed" }
    }
}
