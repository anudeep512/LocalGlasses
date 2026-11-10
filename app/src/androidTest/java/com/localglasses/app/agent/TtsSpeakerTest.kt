package com.localglasses.app.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TtsSpeakerTest {

    @Test
    fun initializesAndSpeaksOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val speaker = TtsSpeaker(context)
        try {
            repeat(50) {
                if (speaker.isReady) return@repeat
                Thread.sleep(100)
            }
            assertTrue(speaker.isReady)
            speaker.speak("LocalGlasses test")
        } finally {
            speaker.close()
        }
    }
}
