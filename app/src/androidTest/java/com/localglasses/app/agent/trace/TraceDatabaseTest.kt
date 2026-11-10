package com.localglasses.app.agent.trace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TraceDatabaseTest {

    private lateinit var context: Context
    private val key = ByteArray(32) { 7 }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TraceDatabase.DB_NAME)
    }

    @Test
    fun storesTracesInAnEncryptedFile() {
        TraceDatabase(context, key).use { db ->
            db.insertTrace("trace-1", "call Amma", "action", null, "done")
            assertEquals(1, db.countTraces())
        }

        val file = context.getDatabasePath(TraceDatabase.DB_NAME)
        assertTrue(file.isFile)
        val header = file.inputStream().use { it.readNBytes(16).toString(Charsets.US_ASCII) }
        assertFalse(header.startsWith("SQLite format 3"))
    }

    @Test
    fun wrongKeyCannotReadTheDatabase() {
        TraceDatabase(context, key).use { db ->
            db.insertTrace("trace-1", "call Amma", "action", null, "done")
        }

        val file = context.getDatabasePath(TraceDatabase.DB_NAME)
        var failed = false
        try {
            SQLiteDatabase.openOrCreateDatabase(file, ByteArray(32) { 9 }, null, null).use { wrong ->
                wrong.rawQuery("SELECT COUNT(*) FROM traces", null).use { it.moveToFirst() }
            }
        } catch (error: Exception) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun purgeRemovesTraces() {
        TraceDatabase(context, key).use { db ->
            db.insertTrace("trace-1", "call Amma", "action", null, "done")
            db.purge()
            assertEquals(0, db.countTraces())
        }
    }

    @Test
    fun corruptFileRecovers() {
        val file = context.getDatabasePath(TraceDatabase.DB_NAME)
        file.parentFile?.mkdirs()
        file.writeText("not a database")

        TraceDatabase(context, key).use { db ->
            assertEquals(0, db.countTraces())
        }
    }
}
