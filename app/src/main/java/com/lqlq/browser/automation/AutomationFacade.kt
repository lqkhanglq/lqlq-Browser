package com.lqlq.browser.automation

import com.lqlq.browser.BuildConfig
import com.lqlq.browser.automation.artifact.AutomationArtifactStore
import com.lqlq.browser.automation.connector.AutomationConnectorRegistry
import com.lqlq.browser.automation.engine.AutomationEngine
import com.lqlq.browser.automation.model.AutomationFoundationStatus
import com.lqlq.browser.automation.model.AutomationJobStatus
import com.lqlq.browser.automation.model.AutomationStepStatus
import com.lqlq.browser.automation.repository.AutomationRepository
import com.lqlq.browser.automation.repository.AutomationJobGraphSnapshot
import com.lqlq.browser.automation.repository.AutomationJobRecord
import com.lqlq.browser.automation.repository.AutomationOutboxEventRecord
import com.lqlq.browser.automation.repository.AutomationProjectRecord
import com.lqlq.browser.automation.repository.AutomationStepDependencyRecord
import com.lqlq.browser.automation.repository.AutomationStepRecord
import com.lqlq.browser.automation.repository.AutomationWorkflowDefinitionRecord
import com.lqlq.browser.automation.repository.AutomationConnectorBindingRecord
import com.lqlq.browser.automation.repository.CreateAutomationJobGraphCommand
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AutomationFacade private constructor(
    private val engine: AutomationEngine,
    private val repository: AutomationRepository
) {
    fun getFoundationStatus(): AutomationFoundationStatus = engine.getFoundationStatus()

    suspend fun startMockAutomation(
        request: AutomationStartRequest
    ): AutomationUiJobSnapshot {
        val normalizedRequest = request.normalized()
        ensureMockProject()
        ensureMockWorkflow()

        val createdAtEpochMs = System.currentTimeMillis()
        val jobId = "job-ui-${createdAtEpochMs}-${shortId()}"
        val workflowVersion = MOCK_WORKFLOW_VERSION

        val bindings = listOf(
            connectorBinding(
                bindingId = "binding-content-$jobId",
                projectId = MOCK_PROJECT_ID,
                jobId = jobId,
                connectorId = normalizedRequest.contentServiceId,
                category = "CONTENT",
                createdAtEpochMs = createdAtEpochMs
            ),
            connectorBinding(
                bindingId = "binding-voice-$jobId",
                projectId = MOCK_PROJECT_ID,
                jobId = jobId,
                connectorId = normalizedRequest.voiceServiceId,
                category = "VOICE",
                createdAtEpochMs = createdAtEpochMs + 1
            ),
            connectorBinding(
                bindingId = "binding-video-$jobId",
                projectId = MOCK_PROJECT_ID,
                jobId = jobId,
                connectorId = normalizedRequest.videoServiceId,
                category = "VIDEO",
                createdAtEpochMs = createdAtEpochMs + 2
            ),
            connectorBinding(
                bindingId = "binding-publish-$jobId",
                projectId = MOCK_PROJECT_ID,
                jobId = jobId,
                connectorId = normalizedRequest.publishServiceId,
                category = "PUBLISH",
                createdAtEpochMs = createdAtEpochMs + 3
            )
        )

        val steps = listOf(
            step(
                stepId = "step-content-$jobId",
                jobId = jobId,
                stepKey = "content",
                stepType = "CONTENT",
                connectorBindingId = bindings[0].bindingId
            ),
            step(
                stepId = "step-voice-$jobId",
                jobId = jobId,
                stepKey = "voice",
                stepType = "VOICE",
                connectorBindingId = bindings[1].bindingId
            ),
            step(
                stepId = "step-video-$jobId",
                jobId = jobId,
                stepKey = "video",
                stepType = "VIDEO",
                connectorBindingId = bindings[2].bindingId
            ),
            step(
                stepId = "step-publish-$jobId",
                jobId = jobId,
                stepKey = "publish-draft",
                stepType = "PUBLISH_DRAFT",
                connectorBindingId = bindings[3].bindingId
            )
        )

        val dependencies = listOf(
            dependency(
                dependencyId = "dep-01-$jobId",
                jobId = jobId,
                fromStepId = steps[0].stepId,
                toStepId = steps[1].stepId
            ),
            dependency(
                dependencyId = "dep-02-$jobId",
                jobId = jobId,
                fromStepId = steps[1].stepId,
                toStepId = steps[2].stepId
            ),
            dependency(
                dependencyId = "dep-03-$jobId",
                jobId = jobId,
                fromStepId = steps[2].stepId,
                toStepId = steps[3].stepId
            )
        )

        val job = AutomationJobRecord(
            jobId = jobId,
            projectId = MOCK_PROJECT_ID,
            workflowId = MOCK_WORKFLOW_ID,
            workflowVersion = workflowVersion,
            status = AutomationJobStatus.DRAFT.name,
            createdAtEpochMs = createdAtEpochMs,
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

        val outbox = AutomationOutboxEventRecord(
            outboxEventId = "outbox-$jobId",
            eventType = "JOB_CREATED",
            aggregateType = "JOB",
            aggregateId = jobId,
            payloadJson = JSONObject()
                .put("jobId", jobId)
                .put("projectId", MOCK_PROJECT_ID)
                .put("topic", normalizedRequest.topic)
                .put("publishMode", normalizedRequest.publishMode)
                .put(
                    "services",
                    JSONObject()
                        .put("content", normalizedRequest.contentServiceId)
                        .put("voice", normalizedRequest.voiceServiceId)
                        .put("video", normalizedRequest.videoServiceId)
                        .put("publish", normalizedRequest.publishServiceId)
                )
                .put(
                    "stepTypes",
                    JSONArray()
                        .put("CONTENT")
                        .put("VOICE")
                        .put("VIDEO")
                        .put("PUBLISH_DRAFT")
                )
                .toString(),
            payloadSchemaVersion = 1,
            dedupeKey = "job-created-$jobId",
            status = "PENDING",
            availableAtEpochMs = createdAtEpochMs,
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

        val snapshot = repository.createJobGraph(
            CreateAutomationJobGraphCommand(
                job = job,
                connectorBindings = bindings,
                steps = steps,
                dependencies = dependencies,
                initialOutboxEvents = listOf(outbox)
            )
        )
        return snapshot.toUiSnapshot(topicFallback = normalizedRequest.topic)
    }

    suspend fun getAutomationJob(
        jobId: String
    ): AutomationUiJobSnapshot? {
        require(jobId.isNotBlank()) { "Job ID is required." }
        return repository.getJobGraph(jobId)?.toUiSnapshot()
    }

    suspend fun listRecentAutomationJobs(
        projectId: String = MOCK_PROJECT_ID,
        limit: Int = DEFAULT_RECENT_LIMIT
    ): List<AutomationUiRecentJob> {
        require(projectId.isNotBlank()) { "Project ID is required." }
        require(limit > 0) { "Limit must be positive." }

        return repository.listRecentJobs(projectId, limit).mapNotNull { job ->
            repository.getJobGraph(job.jobId)?.toRecentJob()
        }
    }

    private suspend fun ensureMockProject() {
        if (repository.getProject(MOCK_PROJECT_ID) != null) {
            return
        }
        val now = System.currentTimeMillis()
        repository.createProject(
            AutomationProjectRecord(
                projectId = MOCK_PROJECT_ID,
                name = "Automation Center MVP",
                topicTemplate = "Tao video ngan ve: {{topic}}",
                contentType = "video/automation-mock",
                approvalPolicy = "REVIEW_BEFORE_POST",
                enabled = true,
                configJson = JSONObject()
                    .put("surface", "automation-center-shell")
                    .put("mode", "mock-only")
                    .put("publishMode", "review-before-post")
                    .toString(),
                configSchemaVersion = 1,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                deletedAtEpochMs = null
            )
        )
    }

    private suspend fun ensureMockWorkflow() {
        if (repository.getWorkflowDefinition(MOCK_WORKFLOW_ID, MOCK_WORKFLOW_VERSION) != null) {
            return
        }
        repository.saveWorkflowDefinition(
            AutomationWorkflowDefinitionRecord(
                workflowId = MOCK_WORKFLOW_ID,
                workflowVersion = MOCK_WORKFLOW_VERSION,
                status = "ACTIVE",
                minimumAppVersionCode = 0,
                definitionSchemaVersion = 1,
                stepContractJson = JSONObject()
                    .put(
                        "steps",
                        JSONArray()
                            .put("CONTENT")
                            .put("VOICE")
                            .put("VIDEO")
                            .put("PUBLISH_DRAFT")
                    )
                    .toString(),
                dependencyContractJson = JSONObject()
                    .put(
                        "edges",
                        JSONArray()
                            .put(JSONArray().put("CONTENT").put("VOICE"))
                            .put(JSONArray().put("VOICE").put("VIDEO"))
                            .put(JSONArray().put("VIDEO").put("PUBLISH_DRAFT"))
                    )
                    .toString(),
                seededFromAppVersionCode = BuildConfig.VERSION_CODE,
                insertedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    private fun connectorBinding(
        bindingId: String,
        projectId: String,
        jobId: String,
        connectorId: String,
        category: String,
        createdAtEpochMs: Long
    ): AutomationConnectorBindingRecord {
        return AutomationConnectorBindingRecord(
            bindingId = bindingId,
            bindingScope = "JOB",
            projectId = projectId,
            jobId = jobId,
            connectorId = connectorId,
            connectorVersion = 1,
            category = category,
            configSchemaVersion = 1,
            configJson = JSONObject()
                .put("mode", "mock")
                .put("connectorId", connectorId)
                .toString(),
            capabilitySnapshotJson = JSONObject()
                .put("mock", true)
                .put("category", category)
                .toString(),
            enabled = true,
            createdAtEpochMs = createdAtEpochMs
        )
    }

    private fun step(
        stepId: String,
        jobId: String,
        stepKey: String,
        stepType: String,
        connectorBindingId: String
    ): AutomationStepRecord {
        return AutomationStepRecord(
            stepId = stepId,
            jobId = jobId,
            stepKey = stepKey,
            stepType = stepType,
            connectorBindingId = connectorBindingId,
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
            waitingReason = "WAITING_FOR_MOCK_EXECUTION"
        )
    }

    private fun dependency(
        dependencyId: String,
        jobId: String,
        fromStepId: String,
        toStepId: String
    ): AutomationStepDependencyRecord {
        return AutomationStepDependencyRecord(
            dependencyId = dependencyId,
            jobId = jobId,
            fromStepId = fromStepId,
            toStepId = toStepId,
            dependencyKind = "SUCCESS",
            conditionType = "ALWAYS",
            conditionParamJson = null,
            allowSkippedUpstream = false
        )
    }

    private fun shortId(): String = UUID.randomUUID().toString().substring(0, 8)

    private fun AutomationStartRequest.normalized(): AutomationStartRequest {
        val normalizedTopic = topic.trim().replace(Regex("\\s+"), " ")
        require(normalizedTopic.isNotBlank()) { "Topic is required." }
        require(normalizedTopic.length <= MAX_TOPIC_LENGTH) { "Topic is too long." }
        return copy(topic = normalizedTopic)
    }

    private fun AutomationJobGraphSnapshot.toUiSnapshot(
        topicFallback: String? = null
    ): AutomationUiJobSnapshot {
        val topic = resolveTopicFromOutbox().ifBlank { topicFallback.orEmpty() }
        return AutomationUiJobSnapshot(
            jobId = job.jobId,
            projectId = job.projectId,
            workflowId = job.workflowId,
            workflowVersion = job.workflowVersion,
            topic = topic,
            status = job.status,
            createdAtEpochMs = job.createdAtEpochMs,
            publishMode = resolvePublishModeFromOutbox(),
            steps = steps
                .sortedBy { STEP_DISPLAY_ORDER[it.stepType] ?: Int.MAX_VALUE }
                .map { step ->
                AutomationUiStepSnapshot(
                    stepId = step.stepId,
                    stepKey = step.stepKey,
                    stepType = step.stepType,
                    status = step.status,
                    connectorBindingId = step.connectorBindingId,
                    waitingReason = step.waitingReason
                )
            },
            dependencies = dependencies.map { dependency ->
                AutomationUiDependencySnapshot(
                    dependencyId = dependency.dependencyId,
                    fromStepId = dependency.fromStepId,
                    toStepId = dependency.toStepId
                )
            }
        )
    }

    private fun AutomationJobGraphSnapshot.toRecentJob(): AutomationUiRecentJob {
        return AutomationUiRecentJob(
            jobId = job.jobId,
            topic = resolveTopicFromOutbox(),
            status = job.status,
            createdAtEpochMs = job.createdAtEpochMs
        )
    }

    private fun AutomationJobGraphSnapshot.resolveTopicFromOutbox(): String {
        val payload = outboxEvents.firstOrNull { it.eventType == "JOB_CREATED" }?.payloadJson ?: return ""
        return runCatching { JSONObject(payload).optString("topic") }.getOrDefault("")
    }

    private fun AutomationJobGraphSnapshot.resolvePublishModeFromOutbox(): String {
        val payload = outboxEvents.firstOrNull { it.eventType == "JOB_CREATED" }?.payloadJson ?: return ""
        return runCatching { JSONObject(payload).optString("publishMode") }.getOrDefault("")
    }

    companion object {
        const val MOCK_PROJECT_ID: String = "automation-ui-mvp"
        const val MOCK_WORKFLOW_ID: String = "automation-ui-mock-video-pipeline"
        const val MOCK_WORKFLOW_VERSION: Int = 1
        const val DEFAULT_RECENT_LIMIT: Int = 8
        const val MAX_TOPIC_LENGTH: Int = 280

        private val STEP_DISPLAY_ORDER = mapOf(
            "CONTENT" to 0,
            "VOICE" to 1,
            "VIDEO" to 2,
            "PUBLISH_DRAFT" to 3
        )

        fun create(
            repository: AutomationRepository,
            artifactStore: AutomationArtifactStore = AutomationArtifactStore.empty(),
            connectorRegistry: AutomationConnectorRegistry = AutomationConnectorRegistry.empty()
        ): AutomationFacade {
            val engine = AutomationEngine(
                repository = repository,
                artifactStore = artifactStore,
                connectorRegistry = connectorRegistry
            )
            return AutomationFacade(
                engine = engine,
                repository = repository
            )
        }

        fun createDefault(): AutomationFacade {
            return create(
                repository = AutomationRepository.empty(),
                artifactStore = AutomationArtifactStore.empty(),
                connectorRegistry = AutomationConnectorRegistry.empty()
            )
        }
    }
}
