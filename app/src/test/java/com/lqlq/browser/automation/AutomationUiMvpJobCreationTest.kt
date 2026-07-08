package com.lqlq.browser.automation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lqlq.browser.automation.database.AutomationDatabase
import com.lqlq.browser.automation.repository.RoomAutomationRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationUiMvpJobCreationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AutomationDatabase
    private lateinit var repository: RoomAutomationRepository
    private lateinit var facade: AutomationFacade

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AutomationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomAutomationRepository(database)
        facade = AutomationFacade.create(repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun validRequestCreatesPersistedFourStepJobGraph() = runBlocking {
        val snapshot = facade.startMockAutomation(validRequest())
        val persisted = repository.getJobGraph(snapshot.jobId)

        requireNotNull(persisted)
        assertEquals("Mẹo quay video món ăn tại nhà", snapshot.topic)
        assertEquals(
            listOf("CONTENT", "VOICE", "VIDEO", "PUBLISH_DRAFT"),
            snapshot.steps.map { it.stepType }
        )
        assertEquals(4, persisted.steps.size)
        assertEquals(
            listOf("mock-content", "mock-voice", "mock-video", "mock-publish-draft"),
            persisted.connectorBindings.map { it.connectorId }
        )

        val stepIdsByType = persisted.steps.associateBy({ it.stepType }, { it.stepId })
        assertEquals(
            listOf(
                stepIdsByType.getValue("CONTENT") to stepIdsByType.getValue("VOICE"),
                stepIdsByType.getValue("VOICE") to stepIdsByType.getValue("VIDEO"),
                stepIdsByType.getValue("VIDEO") to stepIdsByType.getValue("PUBLISH_DRAFT")
            ),
            persisted.dependencies.map { it.fromStepId to it.toStepId }
        )
        assertEquals(1, persisted.outboxEvents.size)
        assertEquals("JOB_CREATED", persisted.outboxEvents.single().eventType)
    }

    @Test
    fun recentJobsComeBackFromLocalRepositoryOnly() = runBlocking {
        val first = facade.startMockAutomation(validRequest(topic = "Video 1"))
        val second = facade.startMockAutomation(validRequest(topic = "Video 2"))

        val recentJobs = facade.listRecentAutomationJobs()

        assertEquals(2, recentJobs.size)
        assertEquals(second.jobId, recentJobs[0].jobId)
        assertEquals(first.jobId, recentJobs[1].jobId)
        assertTrue(recentJobs.all { it.topic.startsWith("Video") })
    }

    private fun validRequest(
        topic: String = "Mẹo quay video món ăn tại nhà"
    ): AutomationStartRequest {
        return AutomationStartRequest(
            topic = topic,
            contentServiceId = "mock-content",
            voiceServiceId = "mock-voice",
            videoServiceId = "mock-video",
            publishServiceId = "mock-publish-draft",
            publishMode = "review-before-post"
        )
    }
}
