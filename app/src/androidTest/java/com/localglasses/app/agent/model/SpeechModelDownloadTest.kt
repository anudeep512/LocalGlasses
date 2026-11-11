package com.localglasses.app.agent.model

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Downloads the pinned speech model and verifies every checksum against the distribution host.
 *
 * Opt in per run, because it transfers roughly 375 MB:
 * `adb shell am instrument -e downloadSpeechModel true ...`
 */
class SpeechModelDownloadTest {

    @Test
    fun downloadsAndVerifiesThePinnedModel() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "pass -e downloadSpeechModel true to run this test",
            arguments.getString("downloadSpeechModel") == "true",
        )

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = SpeechModelRepository.fromContext(context, scope = null)

        val result = repository.download()

        assertTrue(result.exceptionOrNull()?.message, result.isSuccess)
        assertEquals(ModelState.Ready, repository.state.value)
        assertTrue(repository.isReady())
    }
}
