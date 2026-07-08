package com.lqlq.browser.automation.repository

data class AutomationProjectRecord(
    val projectId: String,
    val name: String,
    val topicTemplate: String,
    val contentType: String,
    val approvalPolicy: String,
    val enabled: Boolean,
    val configJson: String,
    val configSchemaVersion: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?
)

data class AutomationWorkflowDefinitionRecord(
    val workflowId: String,
    val workflowVersion: Int,
    val status: String,
    val minimumAppVersionCode: Int,
    val definitionSchemaVersion: Int,
    val stepContractJson: String,
    val dependencyContractJson: String,
    val seededFromAppVersionCode: Int,
    val insertedAtEpochMs: Long
)

data class AutomationJobRecord(
    val jobId: String,
    val projectId: String,
    val workflowId: String,
    val workflowVersion: Int,
    val status: String,
    val createdAtEpochMs: Long,
    val scheduledAtEpochMs: Long?,
    val startedAtEpochMs: Long?,
    val completedAtEpochMs: Long?,
    val currentStepId: String?,
    val lastErrorCode: String?,
    val lastErrorSummary: String?,
    val cancelRequested: Boolean,
    val pauseRequested: Boolean,
    val revision: Long,
    val retryOfJobId: String?,
    val scheduleOccurrenceAtEpochMs: Long?
)

data class AutomationConnectorBindingRecord(
    val bindingId: String,
    val bindingScope: String,
    val projectId: String?,
    val jobId: String?,
    val connectorId: String,
    val connectorVersion: Int,
    val category: String,
    val configSchemaVersion: Int,
    val configJson: String,
    val capabilitySnapshotJson: String,
    val enabled: Boolean,
    val createdAtEpochMs: Long
)

data class AutomationStepRecord(
    val stepId: String,
    val jobId: String,
    val stepKey: String,
    val stepType: String,
    val connectorBindingId: String?,
    val status: String,
    val attemptCount: Int,
    val nextRetryAtEpochMs: Long?,
    val startedAtEpochMs: Long?,
    val completedAtEpochMs: Long?,
    val errorCategory: String?,
    val redactedErrorDetail: String?,
    val executionLeaseOwner: String?,
    val executionLeaseExpiresAtEpochMs: Long?,
    val executionLeaseBootMarker: String?,
    val revision: Long,
    val waitingReason: String?
)

data class AutomationStepDependencyRecord(
    val dependencyId: String,
    val jobId: String,
    val fromStepId: String,
    val toStepId: String,
    val dependencyKind: String,
    val conditionType: String,
    val conditionParamJson: String?,
    val allowSkippedUpstream: Boolean
)

data class AutomationOutboxEventRecord(
    val outboxEventId: String,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: String,
    val payloadJson: String,
    val payloadSchemaVersion: Int,
    val dedupeKey: String,
    val status: String,
    val availableAtEpochMs: Long,
    val claimedAtEpochMs: Long?,
    val claimOwner: String?,
    val claimExpiresAtEpochMs: Long?,
    val dispatchedWorkName: String?,
    val processedAtEpochMs: Long?,
    val attemptCount: Int,
    val lastError: String?,
    val deadLetteredAtEpochMs: Long?,
    val revision: Long
)

data class CreateAutomationJobGraphCommand(
    val job: AutomationJobRecord,
    val connectorBindings: List<AutomationConnectorBindingRecord>,
    val steps: List<AutomationStepRecord>,
    val dependencies: List<AutomationStepDependencyRecord>,
    val initialOutboxEvents: List<AutomationOutboxEventRecord> = emptyList()
)

data class AutomationJobGraphSnapshot(
    val job: AutomationJobRecord,
    val connectorBindings: List<AutomationConnectorBindingRecord>,
    val steps: List<AutomationStepRecord>,
    val dependencies: List<AutomationStepDependencyRecord>,
    val outboxEvents: List<AutomationOutboxEventRecord> = emptyList()
)
