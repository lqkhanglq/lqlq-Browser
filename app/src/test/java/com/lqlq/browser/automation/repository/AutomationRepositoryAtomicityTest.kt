package com.lqlq.browser.automation.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lqlq.browser.automation.database.AutomationDatabase
import com.lqlq.browser.automation.model.AutomationJobStatus
import com.lqlq.browser.automation.model.AutomationStepStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationRepositoryAtomicityTest {

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
    fun createJobGraphRollsBackWhenOutboxInsertConflictsMidTransaction() = runBlocking {
        repository.createProject(
            AutomationProjectRecord(
                projectId = "project-1",
                name = "Project",
                topicTemplate = "topic",
                contentType = "text/plain",
                approvalPolicy = "MANUAL",
                enabled = true,
                configJson = "{}",
                configSchemaVersion = 1,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 2L,
                deletedAtEpochMs = null
            )
        )
        repository.saveWorkflowDefinition(
            AutomationWorkflowDefinitionRecord(
                workflowId = "workflow-1",
                workflowVersion = 1,
                status = "ACTIVE",
                minimumAppVersionCode = 0,
                definitionSchemaVersion = 1,
                stepContractJson = "{}",
                dependencyContractJson = "{}",
                seededFromAppVersionCode = 79,
                insertedAtEpochMs = 3L
            )
        )
        database.outboxDao().insert(
            AutomationOutboxEventRecord(
                outboxEventId = "existing-outbox",
                eventType = "JOB_CREATED",
                aggregateType = "JOB",
                aggregateId = "job-existing",
                payloadJson = """{"jobId":"job-existing"}""",
                payloadSchemaVersion = 1,
                dedupeKey = "atomic-conflict",
                status = "PENDING",
                availableAtEpochMs = 4L,
                claimedAtEpochMs = null,
                claimOwner = null,
                claimExpiresAtEpochMs = null,
                dispatchedWorkName = null,
                processedAtEpochMs = null,
                attemptCount = 0,
                lastError = null,
                deadLetteredAtEpochMs = null,
                revision = 0L
            ).toEntity()
        )

        val command = CreateAutomationJobGraphCommand(
            job = AutomationJobRecord(
                jobId = "job-new",
                projectId = "project-1",
                workflowId = "workflow-1",
                workflowVersion = 1,
                status = AutomationJobStatus.DRAFT.name,
                createdAtEpochMs = 5L,
                scheduledAtEpochMs = null,
                startedAtEpochMs = null,
                completedAtEpochMs = null,
                currentStepId = null,
                lastErrorCode = null,
                lastErrorSummary = null,
                cancelRequested = false,
                pauseRequested = false,
                revision = 0L,
                retryOfJobId = null,
                scheduleOccurrenceAtEpochMs = null
            ),
            connectorBindings = listOf(
                AutomationConnectorBindingRecord(
                    bindingId = "binding-new",
                    bindingScope = "JOB",
                    projectId = "project-1",
                    jobId = "job-new",
                    connectorId = "connector",
                    connectorVersion = 1,
                    category = "WRITE",
                    configSchemaVersion = 1,
                    configJson = "{}",
                    capabilitySnapshotJson = "{}",
                    enabled = true,
                    createdAtEpochMs = 6L
                )
            ),
            steps = listOf(
                AutomationStepRecord(
                    stepId = "step-1",
                    jobId = "job-new",
                    stepKey = "alpha",
                    stepType = "FETCH",
                    connectorBindingId = "binding-new",
                    status = AutomationStepStatus.PENDING.name,
                    attemptCount = 0,
                    nextRetryAtEpochMs = null,
                    startedAtEpochMs = null,
                    completedAtEpochMs = null,
                    errorCategory = null,
                    redactedErrorDetail = null,
                    executionLeaseOwner = null,
                    executionLeaseExpiresAtEpochMs = null,
                    executionLeaseBootMarker = null,
                    revision = 0L,
                    waitingReason = null
                ),
                AutomationStepRecord(
                    stepId = "step-2",
                    jobId = "job-new",
                    stepKey = "beta",
                    stepType = "PUBLISH",
                    connectorBindingId = "binding-new",
                    status = AutomationStepStatus.PENDING.name,
                    attemptCount = 0,
                    nextRetryAtEpochMs = null,
                    startedAtEpochMs = null,
                    completedAtEpochMs = null,
                    errorCategory = null,
                    redactedErrorDetail = null,
                    executionLeaseOwner = null,
                    executionLeaseExpiresAtEpochMs = null,
                    executionLeaseBootMarker = null,
                    revision = 0L,
                    waitingReason = null
                )
            ),
            dependencies = listOf(
                AutomationStepDependencyRecord(
                    dependencyId = "dep-1",
                    jobId = "job-new",
                    fromStepId = "step-1",
                    toStepId = "step-2",
                    dependencyKind = "SUCCESS",
                    conditionType = "ALWAYS",
                    conditionParamJson = null,
                    allowSkippedUpstream = false
                )
            ),
            initialOutboxEvents = listOf(
                AutomationOutboxEventRecord(
                    outboxEventId = "outbox-new",
                    eventType = "JOB_CREATED",
                    aggregateType = "JOB",
                    aggregateId = "job-new",
                    payloadJson = """{"jobId":"job-new"}""",
                    payloadSchemaVersion = 1,
                    dedupeKey = "atomic-conflict",
                    status = "PENDING",
                    availableAtEpochMs = 7L,
                    claimedAtEpochMs = null,
                    claimOwner = null,
                    claimExpiresAtEpochMs = null,
                    dispatchedWorkName = null,
                    processedAtEpochMs = null,
                    attemptCount = 0,
                    lastError = null,
                    deadLetteredAtEpochMs = null,
                    revision = 0L
                )
            )
        )

        val error = runCatching { repository.createJobGraph(command) }.exceptionOrNull()
        assertNotNull(error)
        assertEquals(
            AutomationRepositoryErrorCode.CONFLICT,
            (error as AutomationRepositoryException).code
        )

        assertNull(database.jobDao().getById("job-new"))
        assertNull(database.connectorBindingDao().getById("binding-new"))
        assertNull(database.stepDao().getById("step-1"))
        assertNull(database.stepDao().getById("step-2"))
        assertNull(database.outboxDao().getById("outbox-new"))
        assertNotNull(database.outboxDao().getById("existing-outbox"))
        assertEquals(0, database.stepDependencyDao().listByJob("job-new").size)
    }
}
