package com.lqlq.browser.automation.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AutomationDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dbName: String
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        dbName = "automation-migration-${System.currentTimeMillis()}.db"
        dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @After
    fun tearDown() {
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @Test
    fun autoMigrationPreservesV1MetadataAndCreatesV2Tables() {
        val sqliteDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqliteDb.execSQL(
            "CREATE TABLE IF NOT EXISTS `automation_database_metadata` " +
                "(`key` TEXT NOT NULL, `value` TEXT NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`key`))"
        )
        sqliteDb.execSQL(
            "INSERT INTO `automation_database_metadata` (`key`, `value`, `updatedAtEpochMs`) " +
                "VALUES ('schema_state', 'v1', 100)"
        )
        sqliteDb.execSQL(
            "CREATE TABLE IF NOT EXISTS room_master_table " +
                "(id INTEGER PRIMARY KEY,identity_hash TEXT)"
        )
        sqliteDb.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                "VALUES (42, 'ba0e26c52cf35a1d244652cb730fbe31')"
        )
        sqliteDb.execSQL("PRAGMA user_version = 1")
        sqliteDb.close()

        val database = Room.databaseBuilder(context, AutomationDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()

        try {
            val metadata = database.metadataDao().getByKey("schema_state")
            assertEquals("v1", metadata?.value)

            val tableCursor = database.openHelper.writableDatabase.query(
                SimpleSQLiteQuery("SELECT name FROM sqlite_master WHERE type = 'table'")
            )
            val tableNames = mutableSetOf<String>()
            tableCursor.use { cursor ->
                while (cursor.moveToNext()) {
                    tableNames += cursor.getString(0)
                }
            }

            assertTrue("automation_database_metadata" in tableNames)
            assertTrue("automation_projects" in tableNames)
            assertTrue("automation_workflow_definitions" in tableNames)
            assertTrue("automation_jobs" in tableNames)
            assertTrue("automation_connector_bindings" in tableNames)
            assertTrue("automation_steps" in tableNames)
            assertTrue("automation_step_dependencies" in tableNames)
            assertTrue("automation_step_attempts" in tableNames)
            assertTrue("automation_artifacts" in tableNames)
            assertTrue("automation_step_inputs" in tableNames)
            assertTrue("automation_step_outputs" in tableNames)
            assertTrue("automation_outbox_events" in tableNames)

            val versionCursor = database.openHelper.writableDatabase.query(
                SimpleSQLiteQuery("PRAGMA user_version")
            )
            versionCursor.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
        } finally {
            database.close()
        }
    }
}
