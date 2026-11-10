package com.localglasses.app.agent.trace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TraceRecorderTest {

    private lateinit var context: Context
    private lateinit var database: TraceDatabase
    private lateinit var recorder: TraceRecorder

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TraceDatabase.DB_NAME)
        database = TraceDatabase(context, ByteArray(32) { 3 })
        recorder = TraceRecorder(database)
    }

    @Test
    fun recordsCompletedTraceAndSteps() {
        val id = recorder.start("call Amma")
        recorder.decision(
            id = id,
            command = "call Amma",
            taskType = "action",
            targetPackage = null,
            modelPrompt = "prompt",
            toolCallsJson = """{"name":"call_contact"}""",
            planJson = "Run call_contact",
            outcome = "pending_approval",
        )
        recorder.step(id, 0, "Run call_contact", "Contacts screen", "success", 12)
        recorder.finish(id, "call Amma", "action", null, "success")

        val trace = database.traces().single()
        assertEquals("success", trace.outcome)
        assertEquals(1, database.steps(id).size)
    }

    @Test
    fun redactsOtpInBlockedTrace() {
        val id = recorder.start("read my OTP 483920")
        recorder.finish(id, "read my OTP 483920", "blocked", null, "blocked", "OTP 483920")

        val exported = database.traces().single()
        assertFalse(exported.command.contains("483920"))
        assertTrue(exported.command.contains(RedactorMask))
    }

    private companion object {
        const val RedactorMask = "[REDACTED]"
    }
}
