package com.lqlq.browser.automation.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lqlq.browser.automation.database.entity.AutomationDatabaseMetadataEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationDatabaseSmokeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AutomationDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AutomationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun metadataDaoSupportsUpsertAndReadback() {
        val dao = database.metadataDao()
        val created = AutomationDatabaseMetadataEntity(
            key = "schema_state",
            value = "initial",
            updatedAtEpochMs = 1L
        )

        dao.upsert(created)

        val afterInsert = dao.getByKey("schema_state")
        assertNotNull(afterInsert)
        assertEquals("initial", afterInsert?.value)
        assertEquals(1L, afterInsert?.updatedAtEpochMs)

        dao.upsert(
            created.copy(
                value = "updated",
                updatedAtEpochMs = 2L
            )
        )

        val afterUpdate = dao.getByKey("schema_state")
        assertNotNull(afterUpdate)
        assertEquals("updated", afterUpdate?.value)
        assertEquals(2L, afterUpdate?.updatedAtEpochMs)

        dao.deleteByKey("schema_state")

        assertNull(dao.getByKey("schema_state"))
    }
}
