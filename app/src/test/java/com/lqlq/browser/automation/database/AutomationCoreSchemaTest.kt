package com.lqlq.browser.automation.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.lqlq.browser.automation.database.entity.AutomationArtifactEntity
import com.lqlq.browser.automation.database.entity.AutomationConnectorBindingEntity
import com.lqlq.browser.automation.database.entity.AutomationDatabaseMetadataEntity
import com.lqlq.browser.automation.database.entity.AutomationJobEntity
import com.lqlq.browser.automation.database.entity.AutomationOutboxEventEntity
import com.lqlq.browser.automation.database.entity.AutomationProjectEntity
import com.lqlq.browser.automation.database.entity.AutomationStepAttemptEntity
import com.lqlq.browser.automation.database.entity.AutomationStepDependencyEntity
import com.lqlq.browser.automation.database.entity.AutomationStepEntity
import com.lqlq.browser.automation.database.entity.AutomationStepInputEntity
import com.lqlq.browser.automation.database.entity.AutomationStepOutputEntity
import com.lqlq.browser.automation.database.entity.AutomationWorkflowDefinitionEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationCoreSchemaTest {

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
    fun coreSchemaSupportsMinimalWorkflowGraphAndConstraints() {
        database.metadataDao().upsert(
            AutomationDatabaseMetadataEntity(
                key = "schema_state",
                value = "v2",
                updatedAtEpochMs = 1L
            )
        )

        val project = AutomationProjectEntity(
            projectId = "project-1",
            name = "Project",
            topicTemplate = "topic",
            contentType = "text/plain",
            approvalPolicy = "MANUAL",
            enabled = true,
            configJson = """{"mode":"safe"}""",
            configSchemaVersion = 1,
            createdAtEpochMs = 10L,
            updatedAtEpochMs = 10L,
            deletedAtEpochMs = null
        )
        database.projectDao().insert(project)

        val workflow = AutomationWorkflowDefinitionEntity(
            workflowId = "workflow-1",
            workflowVersion = 1,
            status = "ACTIVE",
            minimumAppVersionCode = 79,
            definitionSchemaVersion = 1,
            stepContractJson = """{"steps":["fetch","publish"]}""",
            dependencyContractJson = """{"edges":[["fetch","publish"]]}""",
            seededFromAppVersionCode = 79,
            insertedAtEpochMs = 11L
        )
        database.workflowDefinitionDao().insert(workflow)

        val job = AutomationJobEntity(
            jobId = "job-1",
            projectId = project.projectId,
            workflowId = workflow.workflowId,
            workflowVersion = workflow.workflowVersion,
            status = "RUNNING",
            createdAtEpochMs = 12L,
            scheduledAtEpochMs = 13L,
            startedAtEpochMs = 14L,
            completedAtEpochMs = null,
            currentStepId = "step-1",
            lastErrorCode = null,
            lastErrorSummary = null,
            cancelRequested = false,
            pauseRequested = false,
            revision = 1L,
            retryOfJobId = null,
            scheduleOccurrenceAtEpochMs = null
        )
        database.jobDao().insert(job)

        val binding = AutomationConnectorBindingEntity(
            bindingId = "binding-1",
            bindingScope = "JOB",
            projectId = null,
            jobId = job.jobId,
            connectorId = "connector-1",
            connectorVersion = 1,
            category = "PUBLISH",
            configSchemaVersion = 1,
            configJson = """{"channel":"test"}""",
            capabilitySnapshotJson = """{"publish":true}""",
            enabled = true,
            createdAtEpochMs = 15L
        )
        database.connectorBindingDao().insertAll(listOf(binding))

        val stepOne = AutomationStepEntity(
            stepId = "step-1",
            jobId = job.jobId,
            stepKey = "fetch",
            stepType = "FETCH",
            connectorBindingId = binding.bindingId,
            status = "COMPLETED",
            attemptCount = 1,
            nextRetryAtEpochMs = null,
            startedAtEpochMs = 16L,
            completedAtEpochMs = 17L,
            errorCategory = null,
            redactedErrorDetail = null,
            executionLeaseOwner = "worker-a",
            executionLeaseExpiresAtEpochMs = 18L,
            executionLeaseBootMarker = "boot-a",
            revision = 1L,
            waitingReason = null
        )
        val stepTwo = AutomationStepEntity(
            stepId = "step-2",
            jobId = job.jobId,
            stepKey = "publish",
            stepType = "PUBLISH",
            connectorBindingId = binding.bindingId,
            status = "PENDING",
            attemptCount = 0,
            nextRetryAtEpochMs = null,
            startedAtEpochMs = null,
            completedAtEpochMs = null,
            errorCategory = null,
            redactedErrorDetail = null,
            executionLeaseOwner = null,
            executionLeaseExpiresAtEpochMs = null,
            executionLeaseBootMarker = null,
            revision = 1L,
            waitingReason = null
        )
        database.stepDao().insertAll(listOf(stepOne, stepTwo))

        val dependency = AutomationStepDependencyEntity(
            dependencyId = "dep-1",
            jobId = job.jobId,
            fromStepId = stepOne.stepId,
            toStepId = stepTwo.stepId,
            dependencyKind = "SUCCESS",
            conditionType = "ALWAYS",
            conditionParamJson = null,
            allowSkippedUpstream = false
        )
        database.stepDependencyDao().insertAll(listOf(dependency))

        val attempt = AutomationStepAttemptEntity(
            attemptId = "attempt-1",
            stepId = stepOne.stepId,
            attemptNumber = 1,
            leaseOwner = "worker-a",
            startedAtEpochMs = 19L,
            finishedAtEpochMs = 20L,
            outcome = "SUCCESS",
            errorCategory = null,
            redactedErrorDetail = null,
            idempotencyKey = "idem-1"
        )
        database.stepAttemptDao().insert(attempt)

        val artifact = AutomationArtifactEntity(
            artifactId = "artifact-1",
            jobId = job.jobId,
            producerStepId = stepOne.stepId,
            artifactType = "TEXT",
            storageClass = "APP_PRIVATE",
            storageUri = "automation://artifact/artifact-1",
            mimeType = "text/plain",
            sizeBytes = 128L,
            checksumSha256 = "abc123",
            providerId = null,
            createdAtEpochMs = 21L,
            expiresAtEpochMs = null,
            sensitivityLevel = "LOW",
            integrityStatus = "VERIFIED",
            retentionPolicy = "KEEP",
            remoteReferenceId = null,
            exportedContentUri = null,
            finalizedAtEpochMs = 22L
        )
        database.artifactDao().insert(artifact)

        database.stepArtifactDao().insertOutputs(
            listOf(
                AutomationStepOutputEntity(
                    stepOutputId = "output-1",
                    stepId = stepOne.stepId,
                    artifactId = artifact.artifactId,
                    role = "PRIMARY",
                    ordinal = 0,
                    declaredByWorkflow = true,
                    replacesArtifactId = null
                )
            )
        )
        database.stepArtifactDao().insertInputs(
            listOf(
                AutomationStepInputEntity(
                    stepInputId = "input-1",
                    stepId = stepTwo.stepId,
                    artifactId = artifact.artifactId,
                    role = "PRIMARY",
                    ordinal = 0,
                    required = true
                )
            )
        )

        val outbox = AutomationOutboxEventEntity(
            outboxEventId = "outbox-1",
            eventType = "JOB_UPDATED",
            aggregateType = "JOB",
            aggregateId = job.jobId,
            payloadJson = """{"jobId":"job-1"}""",
            payloadSchemaVersion = 1,
            dedupeKey = "dedupe-1",
            status = "PENDING",
            availableAtEpochMs = 23L,
            claimedAtEpochMs = null,
            claimOwner = null,
            claimExpiresAtEpochMs = null,
            dispatchedWorkName = null,
            processedAtEpochMs = null,
            attemptCount = 0,
            lastError = null,
            deadLetteredAtEpochMs = null,
            revision = 1L
        )
        database.outboxDao().insert(outbox)

        assertNotNull(database.projectDao().getById(project.projectId))
        assertEquals(1, database.projectDao().listActive().size)
        assertNotNull(
            database.workflowDefinitionDao()
                .getByIdAndVersion(workflow.workflowId, workflow.workflowVersion)
        )
        assertEquals(
            workflow.workflowVersion,
            database.workflowDefinitionDao()
                .getLatestActiveVersion(workflow.workflowId)
                ?.workflowVersion
        )
        assertEquals(1, database.jobDao().listByStatus("RUNNING").size)
        assertEquals(1, database.jobDao().listRecentByProject(project.projectId, 10).size)
        assertEquals(1, database.connectorBindingDao().listByJob(job.jobId).size)
        assertEquals(2, database.stepDao().listByJob(job.jobId).size)
        assertEquals(1, database.stepDao().listByJobAndStatus(job.jobId, "PENDING").size)
        assertEquals(1, database.stepDependencyDao().listOutgoing(stepOne.stepId).size)
        assertEquals(1, database.stepDependencyDao().listIncoming(stepTwo.stepId).size)
        assertEquals(1, database.stepAttemptDao().listByStep(stepOne.stepId).size)
        assertEquals(1, database.artifactDao().listByJob(job.jobId).size)
        assertEquals(1, database.stepArtifactDao().listOutputs(stepOne.stepId).size)
        assertEquals(1, database.stepArtifactDao().listInputs(stepTwo.stepId).size)
        assertEquals(1, database.outboxDao().listPendingAvailable(availableAtOrBeforeEpochMs = 23L).size)

        assertUniqueConstraintViolation {
            database.stepDao().insertAll(
                listOf(
                    stepOne.copy(stepId = "step-duplicate")
                )
            )
        }
        assertUniqueConstraintViolation {
            database.stepAttemptDao().insert(
                attempt.copy(attemptId = "attempt-duplicate")
            )
        }
        assertUniqueConstraintViolation {
            database.outboxDao().insert(
                outbox.copy(outboxEventId = "outbox-duplicate")
            )
        }

        assertForeignKeyViolation {
            database.stepArtifactDao().insertInputs(
                listOf(
                    AutomationStepInputEntity(
                        stepInputId = "input-bad",
                        stepId = stepTwo.stepId,
                        artifactId = "missing-artifact",
                        role = "SECONDARY",
                        ordinal = 1,
                        required = false
                    )
                )
            )
        }

        val tableCursor = database.openHelper.writableDatabase.query(
            SimpleSQLiteQuery("SELECT name FROM sqlite_master WHERE type = 'table'")
        )
        val tableNames = mutableSetOf<String>()
        tableCursor.use { cursor ->
            while (cursor.moveToNext()) {
                tableNames += cursor.getString(0)
            }
        }
        assertTrue("automation_outbox_events" in tableNames)
        assertTrue("automation_step_outputs" in tableNames)
    }

    private fun assertUniqueConstraintViolation(block: () -> Unit) {
        val exception = runCatching(block).exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception.toString().contains("UNIQUE", ignoreCase = true))
    }

    private fun assertForeignKeyViolation(block: () -> Unit) {
        val exception = runCatching(block).exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception.toString().contains("FOREIGN KEY", ignoreCase = true))
    }
}
