package com.localglasses.app.agent.telemetry

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMetricsSourceTest {

    @Test
    fun samplesWithoutThrowingAndKeepsUnavailableFieldsNull() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = AndroidMetricsSource(context)

        val snapshot = source.sample()

        assertNotNull(snapshot.deviceTotalBytes)
        assertTrue((snapshot.deviceTotalBytes ?: 0L) > 0L)
        assertTrue((snapshot.freeStorageBytes ?: 0L) > 0L)
        assertTrue(snapshot.thermalStatus == null || snapshot.thermalStatus!!.isNotBlank())
        assertTrue(snapshot.batteryLevelPercent == null || snapshot.batteryLevelPercent in 0..100)
        assertTrue(snapshot.coreFrequenciesMhz.all { it == null || it > 0 })
    }
}
