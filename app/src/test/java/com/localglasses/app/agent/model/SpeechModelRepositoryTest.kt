package com.localglasses.app.agent.model

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

class SpeechModelRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: MockWebServer

    private val encoderBytes = ByteArray(2048) { (it % 251).toByte() }
    private val decoderBytes = ByteArray(4096) { (it % 253).toByte() }
    private val tokensBytes = "1|the\n2|call\n".toByteArray()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun fileSpec(name: String, bytes: ByteArray) = SpeechModelFile(
        fileName = name,
        url = server.url("/$name").toString(),
        sizeBytes = bytes.size.toLong(),
        sha256 = sha256(bytes),
    )

    private fun manifest() = SpeechModelManifest(
        id = "test-speech",
        language = "en",
        files = SpeechModelFiles(
            encoder = fileSpec("enc.onnx", encoderBytes),
            decoder = fileSpec("dec.onnx", decoderBytes),
            tokens = fileSpec("tokens.txt", tokensBytes),
        ),
    )

    private fun enqueueAll() {
        listOf(encoderBytes, decoderBytes, tokensBytes).forEach { bytes ->
            server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)).setResponseCode(200))
        }
    }

    private fun repository(manifest: SpeechModelManifest = manifest()) = SpeechModelRepository(
        directory = temp.newFolder("speech"),
        manifest = manifest,
        scope = null,
    )

    @Test
    fun `starts absent when nothing is on disk`() {
        assertEquals(ModelState.Absent, repository().state.value)
    }

    @Test
    fun `downloads every file and becomes ready`() = runBlocking {
        enqueueAll()
        val repository = repository()

        val result = repository.download()

        assertTrue(result.isSuccess)
        assertEquals(ModelState.Ready, repository.state.value)
        assertTrue(repository.isReady())
        val paths = repository.paths()
        assertEquals(encoderBytes.size.toLong(), File(paths.encoder).length())
        assertEquals(decoderBytes.size.toLong(), File(paths.decoder).length())
        assertEquals(tokensBytes.size.toLong(), File(paths.tokens).length())
    }

    @Test
    fun `verifies an existing file without downloading again`() = runBlocking {
        enqueueAll()
        val first = repository()
        first.download()
        val paths = first.paths()

        val reopened = SpeechModelRepository(
            directory = File(paths.encoder).parentFile!!,
            manifest = manifest(),
            scope = null,
        )
        reopened.refresh()

        assertEquals(ModelState.Ready, reopened.state.value)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a corrupt file returns the set to absent`() = runBlocking {
        val repository = repository()
        val paths = repository.paths()
        File(paths.encoder).parentFile!!.mkdirs()
        File(paths.encoder).writeBytes(ByteArray(2048) { 0x7F })

        repository.refresh()

        assertEquals(ModelState.Absent, repository.state.value)
        assertFalse(File(paths.encoder).exists())
    }

    @Test
    fun `a server error reports failed`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val repository = repository()

        val result = repository.download()

        assertTrue(result.isFailure)
        assertTrue(repository.state.value is ModelState.Failed)
    }

    @Test
    fun `progress reaches the total size`() = runBlocking {
        enqueueAll()
        val repository = repository()
        val seen = mutableListOf<Pair<Long, Long>>()

        repository.download { written, total -> seen += written to total }

        val total = (encoderBytes.size + decoderBytes.size + tokensBytes.size).toLong()
        assertEquals(total, seen.last().first)
        assertEquals(total, seen.last().second)
    }

    @Test
    fun `cancelling keeps the set absent and the partial file`() = runBlocking {
        val slow = MockResponse()
            .setBody(okio.Buffer().write(ByteArray(4096)))
            .throttleBody(1, 1, TimeUnit.SECONDS)
        server.enqueue(slow)
        val repository = repository()

        val job = launch(Dispatchers.IO, start = CoroutineStart.DEFAULT) {
            repository.download()
        }
        delay(300)
        job.cancel()
        job.join()

        assertEquals(ModelState.Absent, repository.state.value)
        assertFalse(repository.isReady())
    }
}
