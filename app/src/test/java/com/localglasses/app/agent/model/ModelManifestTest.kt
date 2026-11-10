package com.localglasses.app.agent.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelManifestTest {

    private val valid = """
        {
          "id": "gemma-4-E2B-it",
          "fileName": "gemma-4-E2B-it.litertlm",
          "url": "https://example.invalid/model.litertlm",
          "sizeBytes": 2588147712,
          "sha256": "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
        }
    """.trimIndent()

    @Test
    fun parsesValidManifest() {
        val manifest = ModelManifestParser.parse(valid).getOrThrow()
        assertEquals("gemma-4-E2B-it", manifest.id)
        assertEquals(2_588_147_712L, manifest.sizeBytes)
        assertEquals(32, manifest.sha256Bytes.size)
    }

    @Test
    fun parsesShippedAsset() {
        val asset = File("src/main/assets/model.json")
        assertTrue("model asset missing", asset.isFile)
        val manifest = ModelManifestParser.parse(asset.readText()).getOrThrow()
        assertEquals("gemma-4-E2B-it.litertlm", manifest.fileName)
    }

    @Test
    fun rejectsMissingField() {
        val broken = valid.replace("\"sizeBytes\": 2588147712,", "")
        assertTrue(ModelManifestParser.parse(broken).isFailure)
    }

    @Test
    fun rejectsMalformedChecksum() {
        val broken = valid.replace(
            "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            "not-a-checksum",
        )
        assertTrue(ModelManifestParser.parse(broken).isFailure)
    }

    @Test
    fun rejectsNonHttpsUrl() {
        val broken = valid.replace("https://example.invalid", "http://example.invalid")
        assertTrue(ModelManifestParser.parse(broken).isFailure)
    }
}
