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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomAutomationRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AutomationDatabase
    private lateinit var repository: RoomAutomationRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AutomationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomAutomationRepository(
            database = database,
            dependencies = AutomationRepositoryDependencies(
                clock = AutomationClock { 1_000L },
                idGenerator = AutomationIdGenerator { "generated-id" }
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun repositoryCanPersistAndReadProjectWorkflowAndJobGraph() = runBlocking {
        val project = validProject()
        val workflow = validWorkflow()

        val createdProject = repository.createProject(project)
        assertEquals(project, createdProject)
        assertEquals(project, repository.getProject(project.projectId))

        val savedWorkflow = repository.saveWorkflowDefinition(workflow)
        assertEquals(workflow, savedWorkflow)
        assertEquals(
            workflow,
            repository.getWorkflowDefinition(workflow.workflowId, workflow.workflowVersion)
        )

        val command = validCommand()
        val createdGraph = repository.createJobGraph(command)
        val loadedGraph = repository.getJobGraph(command.job.jobId)

        assertEquals(command.job, createdGraph.job)
        assertEquals(2, createdGraph.connectorBindings.size)
        assertEquals(3, createdGraph.steps.size)
        assertEquals(2, createdGraph.dependencies.size)
        assertEquals(1, createdGraph.outboxEvents.size)

        assertNotNull(loadedGraph)
        assertEquals(createdGraph, loadedGraph)
        assertEquals(
            listOf("bind-job", "bind-project"),
            createdGraph.connectorBindings.map { it.bindingId }.sorted()
        )
        assertEquals(
            listOf("step-a", "step-b", "step-c"),
            createdGraph.steps.map { it.stepId }
        )
        assertEquals(
            listOf("dep-ab", "dep-bc"),
            createdGraph.dependencies.map { it.dependencyId }
        )
        assertEquals(
            listOf(command.job.jobId),
            createdGraph.outboxEvents.map { it.aggregateId }
        )

        val recentJobs = repository.listRecentJobs(project.projectId, 10)
        assertEquals(1, recentJobs.size)
        assertEquals(command.job.jobId, recentJobs.single().jobId)
    }

    @Test
    fun repositoryRejectsInvalidGraphsAndLeavesDatabaseClean() = runBlocking {
        repository.createProject(validProject())
        repository.saveWorkflowDefinition(validWorkflow())

        assertValidationFailure(validCommand().copy(steps = emptyList()))
        assertValidationFailure(
            validCommand().copy(
                steps = validCommand().steps + validCommand().steps.first().copy(stepId = "step-a")
            )
        )
        assertValidationFailure(
            validCommand().copy(
                steps = listOf(
                    validCommand().steps[0],
                    validCommand().steps[1].copy(stepId = "step-b2", stepKey = "alpha"),
                    validCommand().steps[2]
                )
            )
        )
        assertValidationFailure(
            validCommand().copy(
                dependencies = listOf(
                    validCommand().dependencies.first().copy(toStepId = "missing-step")
                )
            )
        )
        assertValidationFailure(
            validCommand().copy(
                dependencies = listOf(
                    validCommand().dependencies.first().copy(fromStepId = "step-a", toStepId = "step-a")
                )
            )
        )
        assertValidationFailure(
            validCommand().copy(
                steps = listOf(
                    validCommand().steps[0].copy(connectorBindingId = "missing-binding"),
                    validCommand().steps[1],
                    validCommand().steps[2]
                )
            )
        )
        assertValidationFailure(
            validCommand().copy(
                job = validCommand().job.copy(workflowVersion = 0)
            )
        )

        val deletedProject = validProject(
            projectId = "project-deleted",
            deletedAtEpochMs = 10L
        )
        repository.createProject(deletedProject)
        val deletedCommand = validCommand(projectId = deletedProject.projectId, jobId = "job-deleted")
        val deletedError = runCatching { repository.createJobGraph(deletedCommand) }.exceptionOrNull()
        assertTrue(deletedError is AutomationRepositoryException)
        assertEquals(AutomationRepositoryErrorCode.CONSTRAINT, (deletedError as AutomationRepositoryException).code)
        assertNull(repository.getJobGraph("job-deleted"))
    }

    private suspend fun assertValidationFailure(command: CreateAutomationJobGraphCommand) {
        val error = runCatching { repository.createJobGraph(command) }.exceptionOrNull()
        assertTrue(error is AutomationRepositoryException)
        assertEquals(AutomationRepositoryErrorCode.VALIDATION, (error as AutomationRepositoryException).code)
        assertNull(repository.getJobGraph(command.job.jobId))
        command.connectorBindings.forEach {
            assertNull(database.connectorBindingDao().getById(it.bindingId))
        }
        command.steps.forEach {
            assertNull(database.stepDao().getById(it.stepId))
        }
        command.initialOutboxEvents.forEach {
            assertNull(database.outboxDao().getById(it.outboxEventId))
        }
    }

    private fun validProject(
        projectId: String = "project-1",
        deletedAtEpochMs: Long? = null
    ): AutomationProjectRecord = AutomationProjectRecord(
        projectId = projectId,
        name = "Project",
        topicTemplate = "topic",
        contentType = "text/plain",
        approvalPolicy = "MANUAL",
        enabled = true,
        configJson = """{"mode":"safe"}""",
        configSchemaVersion = 1,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 2L,
        deletedAtEpochMs = deletedAtEpochMs
    )

    private fun validWorkflow(): AutomationWorkflowDefinitionRecord =
        AutomationWorkflowDefinitionRecord(
            workflowId = "workflow-1",
            workflowVersion = 1,
            status = "ACTIVE",
            minimumAppVersionCode = 0,
            definitionSchemaVersion = 1,
            stepContractJson = """{"steps":["alpha","beta","gamma"]}""",
            dependencyContractJson = """{"edges":[["alpha","beta"],["beta","gamma"]]}""",
            seededFromAppVersionCode = 79,
            insertedAtEpochMs = 3L
        )

    private fun validCommand(
        projectId: String = "project-1",
        jobId: String = "job-1"
    ): CreateAutomationJobGraphCommand {
        val job = AutomationJobRecord(
            jobId = jobId,
            projectId = projectId,
            workflowId = "workflow-1",
            workflowVersion = 1,
            status = AutomationJobStatus.DRAFT.name,
            createdAtEpochMs = 4L,
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
        )
        val bindings = listOf(
            AutomationConnectorBindingRecord(
                bindingId = "bind-project",
                bindingScope = "PROJECT",
                projectId = projectId,
                jobId = null,
                connectorId = "connector-project",
                connectorVersion = 1,
                category = "READ",
                configSchemaVersion = 1,
                configJson = """{"scope":"project"}""",
                capabilitySnapshotJson = """{"read":true}""",
                enabled = true,
                createdAtEpochMs = 5L
            ),
            AutomationConnectorBindingRecord(
                bindingId = "bind-job",
                bindingScope = "JOB",
                projectId = projectId,
                jobId = jobId,
                connectorId = "connector-job",
                connectorVersion = 1,
                category = "WRITE",
                configSchemaVersion = 1,
                configJson = """{"scope":"job"}""",
                capabilitySnapshotJson = """{"write":true}""",
                enabled = true,
                createdAtEpochMs = 6L
            )
        )
        val steps = listOf(
            AutomationStepRecord(
                stepId = "step-a",
                jobId = jobId,
                stepKey = "alpha",
                stepType = "FETCH",
                connectorBindingId = "bind-project",
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
                stepId = "step-b",
                jobId = jobId,
                stepKey = "beta",
                stepType = "TRANSFORM",
                connectorBindingId = null,
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
                stepId = "step-c",
                jobId = jobId,
                stepKey = "gamma",
                stepType = "PUBLISH",
                connectorBindingId = "bind-job",
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
        )
        val dependencies = listOf(
            AutomationStepDependencyRecord(
                dependencyId = "dep-ab",
                jobId = jobId,
                fromStepId = "step-a",
                toStepId = "step-b",
                dependencyKind = "SUCCESS",
                conditionType = "ALWAYS",
                conditionParamJson = null,
                allowSkippedUpstream = false
            ),
            AutomationStepDependencyRecord(
                dependencyId = "dep-bc",
                jobId = jobId,
                fromStepId = "step-b",
                toStepId = "step-c",
                dependencyKind = "SUCCESS",
                conditionType = "ALWAYS",
                conditionParamJson = null,
                allowSkippedUpstream = false
            )
        )
        val outbox = listOf(
            AutomationOutboxEventRecord(
                outboxEventId = "outbox-1",
                eventType = "JOB_CREATED",
                aggregateType = "JOB",
                aggregateId = jobId,
                payloadJson = """{"jobId":"$jobId"}""",
                payloadSchemaVersion = 1,
                dedupeKey = "job-created:$jobId",
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
        return CreateAutomationJobGraphCommand(
            job = job,
            connectorBindings = bindings,
            steps = steps,
            dependencies = dependencies,
            initialOutboxEvents = outbox
        )
    }
}
