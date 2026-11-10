package com.localglasses.app.agent.model

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelDownloaderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun resumesFromPartialFile() = runBlocking {
        val target = File(temporaryFolder.root, "model.litertlm")
        val partial = File(target.path + ModelDownloader.PART_SUFFIX)
        partial.writeText("abc")
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setBody("def")
                .addHeader("Content-Range", "bytes 3-5/6"),
        )

        val result = downloader().download(manifest("abcdef"), target)

        assertTrue(result.isSuccess)
        assertEquals("abcdef", target.readText())
        assertFalse(partial.exists())
        assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun rejectsChecksumMismatchAndDeletesPartial() = runBlocking {
        val target = File(temporaryFolder.root, "model.litertlm")
        val partial = File(target.path + ModelDownloader.PART_SUFFIX)
        server.enqueue(MockResponse().setBody("abcdef"))

        val result = downloader().download(manifest("abcdef", checksumOf = "wrong"), target)

        assertTrue(result.isFailure)
        assertFalse(target.exists())
        assertFalse(partial.exists())
    }

    @Test
    fun cancellationKeepsPartialFile() = runBlocking {
        val target = File(temporaryFolder.root, "model.litertlm")
        val partial = File(target.path + ModelDownloader.PART_SUFFIX)
        server.enqueue(MockResponse().setBody("abcdef"))

        val job = launch {
            downloader().download(manifest("abcdef"), target) { _, _ ->
                throw CancellationException("stopped by test")
            }
        }
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(partial.exists())
    }

    @Test
    fun refusesWhenFreeSpaceIsInsufficient() = runBlocking {
        val target = File(temporaryFolder.root, "model.litertlm")
        server.enqueue(MockResponse().setBody("abcdef"))

        val result = ModelDownloader(
            client = OkHttpClient(),
            freeSpace = { 0L },
        ).download(manifest("abcdef"), target)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("free space") == true)
    }

    private fun downloader() = ModelDownloader(
        client = OkHttpClient.Builder()
            .readTimeout(5, TimeUnit.SECONDS)
            .build(),
        freeSpace = { Long.MAX_VALUE },
    )

    private fun manifest(
        body: String,
        checksumOf: String = body,
    ) = ModelManifest(
        id = "test-model",
        fileName = "model.litertlm",
        url = server.url("/model.litertlm").toString(),
        sizeBytes = body.toByteArray().size.toLong(),
        sha256 = hash(checksumOf),
    )

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
