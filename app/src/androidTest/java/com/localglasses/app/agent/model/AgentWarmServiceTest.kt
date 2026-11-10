package com.localglasses.app.agent.model

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentWarmServiceTest {

    @Test
    fun serviceRunsInForegroundWithNotification() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ContextCompat.startForegroundService(
            context,
            Intent(context, AgentWarmService::class.java),
        )

        val manager = context.getSystemService(ActivityManager::class.java)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val running = (0 until 20).any {
            Thread.sleep(100)
            manager.getRunningServices(200).any {
                it.service.className == AgentWarmService::class.java.name
            }
        }
        assertTrue("warm service did not start", running)
        val enabled = notificationManager.areNotificationsEnabled()
        val active = notificationManager.activeNotifications
        if (enabled) {
            assertTrue(
                "warm service notification missing (active=${active.size})",
                active.any { it.id == AgentWarmService.NOTIFICATION_ID },
            )
        } else {
            // OnePlus blocks shell grants for POST_NOTIFICATIONS; the in-app request
            // flow (task 2.7) is required before notification visibility can be checked.
            org.junit.Assume.assumeTrue(
                "POST_NOTIFICATIONS not granted; notification visibility pending task 2.7",
                false,
            )
        }

        AgentWarmService.stop(context)
    }
}
