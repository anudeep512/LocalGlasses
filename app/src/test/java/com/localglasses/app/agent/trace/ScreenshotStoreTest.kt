package com.localglasses.app.agent.trace

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScreenshotStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun keepsAtMostFiftyFiles() {
        val store = ScreenshotStore(File(temporaryFolder.root, "shots"), maxFiles = 50)

        repeat(55) { index ->
            store.save("shot-$index.png", byteArrayOf(index.toByte()))
        }

        assertEquals(50, store.files().size)
    }

    @Test
    fun clearRemovesAllFiles() {
        val store = ScreenshotStore(File(temporaryFolder.root, "shots"))
        store.save("shot.png", byteArrayOf(1))

        store.clear()

        assertTrue(store.files().isEmpty())
    }
}
