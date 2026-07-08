package com.lqlq.browser.automation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lqlq.browser.AutomationBridge
import com.lqlq.browser.automation.database.AutomationDatabase
import com.lqlq.browser.automation.repository.AutomationJobGraphSnapshot
import com.lqlq.browser.automation.repository.AutomationJobRecord
import com.lqlq.browser.automation.repository.AutomationProjectRecord
import com.lqlq.browser.automation.repository.AutomationRepository
import com.lqlq.browser.automation.repository.AutomationRepositoryErrorCode
import com.lqlq.browser.automation.repository.AutomationRepositoryException
import com.lqlq.browser.automation.repository.AutomationWorkflowDefinitionRecord
import com.lqlq.browser.automation.repository.CreateAutomationJobGraphCommand
import com.lqlq.browser.automation.repository.RoomAutomationRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationBridgeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AutomationDatabase
    private lateinit var repository: RoomAutomationRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AutomationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomAutomationRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun emptyTopicRejected() {
        val bridge = AutomationBridge(AutomationFacade.create(repository))

        val response = JSONObject(
            bridge.startMockAutomation(
                """{"topic":"   ","contentServiceId":"mock-content","voiceServiceId":"mock-voice","videoServiceId":"mock-video","publishServiceId":"mock-publish-draft","publishMode":"review-before-post"}"""
            )
        )

        assertFalse(response.getBoolean("ok"))
        assertEquals("VALIDATION", response.getString("errorCode"))
    }

    @Test
    fun normalTopicAccepted() {
        val bridge = AutomationBridge(AutomationFacade.create(repository))
        val response = JSONObject(bridge.startMockAutomation(validRequestJson("Noi dung binh thuong")))

        assertTrue(response.getBoolean("ok"))
        assertEquals("Noi dung binh thuong", response.getJSONObject("job").getString("topic"))
    }

    @Test
    fun topicLongerThan4000ButWithin50000Accepted() {
        val bridge = AutomationBridge(AutomationFacade.create(repository))
        val longTopic = "a".repeat(12_000)

        val response = JSONObject(
            bridge.startMockAutomation(
                validRequestJson(longTopic)
            )
        )

        assertTrue(response.getBoolean("ok"))
        assertEquals(longTopic, response.getJSONObject("job").getString("topic"))
    }

    @Test
    fun topicLongerThan50000Rejected() {
        val bridge = AutomationBridge(AutomationFacade.create(repository))
        val longTopic = "a".repeat(AutomationFacade.MAX_AUTOMATION_CONTENT_LENGTH + 1)

        val response = JSONObject(bridge.startMockAutomation(validRequestJson(longTopic)))

        assertFalse(response.getBoolean("ok"))
        assertEquals("VALIDATION", response.getString("errorCode"))
    }

    @Test
    fun htmlAndScriptAreTreatedAsPlainText() {
        val bridge = AutomationBridge(AutomationFacade.create(repository))
        val topic = "<script>alert('x')</script><b>hello</b>"

        val response = JSONObject(bridge.startMockAutomation(validRequestJson(topic)))

        assertTrue(response.getBoolean("ok"))
        assertEquals(topic, response.getJSONObject("job").getString("topic"))
    }

    @Test
    fun bridgeCreatesJobWithoutPageWebViewOrDaoExposure() {
        val bridge = AutomationBridge(AutomationFacade.create(repository))

        val response = JSONObject(bridge.startMockAutomation(validRequestJson("Bridge local only")))

        assertTrue(response.getBoolean("ok"))

        val publicTypes = AutomationBridge::class.java.methods
            .filter { it.declaringClass == AutomationBridge::class.java }
            .flatMap { method ->
                listOf(method.returnType.name) + method.parameterTypes.map { it.name }
            }
        assertTrue(publicTypes.none { it.contains(".automation.database.") || it.contains(".dao.") })
    }

    @Test
    fun repositoryErrorIsRedacted() {
        val bridge = AutomationBridge(AutomationFacade.create(FailingRepository()))

        val response = JSONObject(bridge.startMockAutomation(validRequestJson("Redacted error")))
        val message = response.getString("message")

        assertFalse(response.getBoolean("ok"))
        assertEquals(AutomationRepositoryErrorCode.STORAGE.name, response.getString("errorCode"))
        assertFalse(message.contains("C:\\secret\\path"))
        assertFalse(message.contains("SELECT *"))
    }

    private fun validRequestJson(topic: String): String {
        return JSONObject()
            .put("topic", topic)
            .put("contentServiceId", "mock-content")
            .put("voiceServiceId", "mock-voice")
            .put("videoServiceId", "mock-video")
            .put("publishServiceId", "mock-publish-draft")
            .put("publishMode", "review-before-post")
            .toString()
    }

    private class FailingRepository : AutomationRepository {
        override fun isFoundationReady(): Boolean = true

        override suspend fun createProject(project: AutomationProjectRecord): AutomationProjectRecord {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.STORAGE,
                "SELECT * FROM jobs at C:\\secret\\path\\automation.db"
            )
        }

        override suspend fun getProject(projectId: String): AutomationProjectRecord? = null

        override suspend fun saveWorkflowDefinition(
            definition: AutomationWorkflowDefinitionRecord
        ): AutomationWorkflowDefinitionRecord = definition

        override suspend fun getWorkflowDefinition(
            workflowId: String,
            workflowVersion: Int
        ): AutomationWorkflowDefinitionRecord? = null

        override suspend fun createJobGraph(
            command: CreateAutomationJobGraphCommand
        ): AutomationJobGraphSnapshot {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.STORAGE,
                "SELECT * FROM jobs at C:\\secret\\path\\automation.db"
            )
        }

        override suspend fun getJobGraph(jobId: String): AutomationJobGraphSnapshot? = null

        override suspend fun listRecentJobs(
            projectId: String,
            limit: Int
        ): List<AutomationJobRecord> = emptyList()
    }
}
