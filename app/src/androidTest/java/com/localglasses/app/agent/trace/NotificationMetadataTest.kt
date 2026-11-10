package com.localglasses.app.agent.trace

import android.app.Notification
import android.content.Context
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationMetadataTest {

    private lateinit var context: Context
    private lateinit var database: TraceDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TraceDatabase.DB_NAME)
        database = TraceDatabase(context, ByteArray(32) { 11 })
    }

    @Test
    fun storesMetadataButNotOtpText() {
        val notification = Notification.Builder(context, "test")
            .setContentTitle("Your OTP is 483920")
            .setContentText("Code 483920")
            .build()
        val sbn = StatusBarNotification(
            "com.example",
            "com.example",
            1,
            null,
            1,
            1,
            0,
            notification,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis(),
        )

        val metadata = NotificationMetadataMapper.from(sbn)
        assertFalse(metadata.toString().contains("483920"))
        database.recordNotification(
            metadata.packageName,
            metadata.postedAt,
            metadata.channel,
            metadata.groupKey,
        )

        assertEquals(1, database.countNotifications())
    }
}
