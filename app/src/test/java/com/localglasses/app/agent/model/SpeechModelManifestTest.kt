package com.localglasses.app.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechModelManifestTest {

    private val sha = "a".repeat(64)

    private fun file(name: String, url: String = "https://example.test/$name") =
        """
        {
          "fileName": "$name",
          "url": "$url",
          "sizeBytes": 1024,
          "sha256": "$sha"
        }
        """.trimIndent()

    private fun manifest(
        id: String = "whisper-small.en-int8",
        language: String = "en",
        encoder: String = file("small.en-encoder.int8.onnx"),
        decoder: String = file("small.en-decoder.int8.onnx"),
        tokens: String = file("small.en-tokens.txt"),
    ) = """
        {
          "id": "$id",
          "language": "$language",
          "files": { "encoder": $encoder, "decoder": $decoder, "tokens": $tokens }
        }
    """.trimIndent()

    @Test
    fun `parses a complete manifest`() {
        val parsed = SpeechModelManifestParser.parse(manifest()).getOrThrow()

        assertEquals("whisper-small.en-int8", parsed.id)
        assertEquals("en", parsed.language)
        assertEquals(3, parsed.allFiles().size)
        assertEquals("small.en-encoder.int8.onnx", parsed.files.encoder.fileName)
    }

    @Test
    fun `rejects a manifest missing the decoder`() {
        val raw = """
            {
              "id": "x",
              "language": "en",
              "files": { "encoder": ${file("a.onnx")}, "tokens": ${file("t.txt")} }
            }
        """.trimIndent()

        assertTrue(SpeechModelManifestParser.parse(raw).isFailure)
    }

    @Test
    fun `rejects a non https url`() {
        val raw = manifest(encoder = file("a.onnx", url = "http://example.test/a.onnx"))

        val error = SpeechModelManifestParser.parse(raw).exceptionOrNull()

        assertTrue(error != null && error.message!!.contains("https"))
    }

    @Test
    fun `rejects a malformed checksum`() {
        val broken = """
            {"fileName":"a.onnx","url":"https://example.test/a.onnx","sizeBytes":10,"sha256":"abc"}
        """.trimIndent()

        val error = SpeechModelManifestParser.parse(manifest(encoder = broken)).exceptionOrNull()

        assertTrue(error != null && error.message!!.contains("sha256"))
    }

    @Test
    fun `rejects a blank id`() {
        assertTrue(SpeechModelManifestParser.parse(manifest(id = "")).isFailure)
    }

    @Test
    fun `rejects duplicate file names`() {
        val duplicate = file("same.onnx")

        val raw = manifest(encoder = duplicate, decoder = duplicate)

        val error = SpeechModelManifestParser.parse(raw).exceptionOrNull()

        assertTrue(error != null && error.message!!.contains("distinct"))
    }

    @Test
    fun `rejects an unsupported file type`() {
        val raw = manifest(tokens = file("tokens.bin"))

        assertTrue(SpeechModelManifestParser.parse(raw).isFailure)
    }

    @Test
    fun `rejects a zero size`() {
        val broken = """
            {"fileName":"a.onnx","url":"https://example.test/a.onnx","sizeBytes":0,"sha256":"$sha"}
        """.trimIndent()

        assertTrue(SpeechModelManifestParser.parse(manifest(encoder = broken)).isFailure)
    }
}
