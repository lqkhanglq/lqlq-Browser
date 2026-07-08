package com.lqlq.browser.automation.repository

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.lqlq.browser.automation.AutomationFacade
import com.lqlq.browser.automation.artifact.AutomationArtifactStore
import com.lqlq.browser.automation.connector.AutomationConnectorRegistry
import com.lqlq.browser.automation.database.AutomationDatabase
import com.lqlq.browser.automation.engine.AutomationEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationProductionWiringTest {

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
    fun productionFacadeCanUseRoomRepositoryWithoutActivityAndWithoutWrites() {
        val repository: AutomationRepository = RoomAutomationRepository(database)
        val facade = AutomationFacade.create(
            repository = repository,
            artifactStore = AutomationArtifactStore.empty(),
            connectorRegistry = AutomationConnectorRegistry.empty()
        )
        val engine = AutomationEngine(
            repository = repository,
            artifactStore = AutomationArtifactStore.empty(),
            connectorRegistry = AutomationConnectorRegistry.empty()
        )

        assertTrue(repository.isFoundationReady())
        assertTrue(facade.getFoundationStatus().repositoryReady)
        assertTrue(engine.getFoundationStatus().repositoryReady)

        val publicFacadeTypes = AutomationFacade::class.java.methods
            .flatMap { listOfNotNull(it.returnType.name) + it.parameterTypes.map { param -> param.name } }
        assertTrue(publicFacadeTypes.none { it.contains(".automation.database.") })

        val constructorParamNames = AutomationEngine::class.java.constructors
            .flatMap { constructor -> constructor.parameterTypes.map { it.name } }
        assertTrue(constructorParamNames.contains(AutomationRepository::class.java.name))
        assertFalse(constructorParamNames.any { it.contains(".automation.database.") })

        assertEquals(0, countRows("automation_projects"))
        assertEquals(0, countRows("automation_jobs"))
        assertEquals(0, countRows("automation_outbox_events"))
    }

    private fun countRows(tableName: String): Int {
        val cursor = database.openHelper.writableDatabase.query(
            SimpleSQLiteQuery("SELECT COUNT(*) FROM $tableName")
        )
        cursor.use {
            it.moveToFirst()
            return it.getInt(0)
        }
    }
}
