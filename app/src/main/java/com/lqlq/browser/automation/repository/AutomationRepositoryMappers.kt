package com.lqlq.browser.automation.repository

import com.lqlq.browser.automation.database.entity.AutomationConnectorBindingEntity
import com.lqlq.browser.automation.database.entity.AutomationJobEntity
import com.lqlq.browser.automation.database.entity.AutomationOutboxEventEntity
import com.lqlq.browser.automation.database.entity.AutomationProjectEntity
import com.lqlq.browser.automation.database.entity.AutomationStepDependencyEntity
import com.lqlq.browser.automation.database.entity.AutomationStepEntity
import com.lqlq.browser.automation.database.entity.AutomationWorkflowDefinitionEntity

internal fun AutomationProjectEntity.toRecord(): AutomationProjectRecord = AutomationProjectRecord(
    projectId = projectId,
    name = name,
    topicTemplate = topicTemplate,
    contentType = contentType,
    approvalPolicy = approvalPolicy,
    enabled = enabled,
    configJson = configJson,
    configSchemaVersion = configSchemaVersion,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    deletedAtEpochMs = deletedAtEpochMs
)

internal fun AutomationProjectRecord.toEntity(): AutomationProjectEntity = AutomationProjectEntity(
    projectId = projectId,
    name = name,
    topicTemplate = topicTemplate,
    contentType = contentType,
    approvalPolicy = approvalPolicy,
    enabled = enabled,
    configJson = configJson,
    configSchemaVersion = configSchemaVersion,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    deletedAtEpochMs = deletedAtEpochMs
)

internal fun AutomationWorkflowDefinitionEntity.toRecord(): AutomationWorkflowDefinitionRecord =
    AutomationWorkflowDefinitionRecord(
        workflowId = workflowId,
        workflowVersion = workflowVersion,
        status = status,
        minimumAppVersionCode = minimumAppVersionCode,
        definitionSchemaVersion = definitionSchemaVersion,
        stepContractJson = stepContractJson,
        dependencyContractJson = dependencyContractJson,
        seededFromAppVersionCode = seededFromAppVersionCode,
        insertedAtEpochMs = insertedAtEpochMs
    )

internal fun AutomationWorkflowDefinitionRecord.toEntity(): AutomationWorkflowDefinitionEntity =
    AutomationWorkflowDefinitionEntity(
        workflowId = workflowId,
        workflowVersion = workflowVersion,
        status = status,
        minimumAppVersionCode = minimumAppVersionCode,
        definitionSchemaVersion = definitionSchemaVersion,
        stepContractJson = stepContractJson,
        dependencyContractJson = dependencyContractJson,
        seededFromAppVersionCode = seededFromAppVersionCode,
        insertedAtEpochMs = insertedAtEpochMs
    )

internal fun AutomationJobEntity.toRecord(): AutomationJobRecord = AutomationJobRecord(
    jobId = jobId,
    projectId = projectId,
    workflowId = workflowId,
    workflowVersion = workflowVersion,
    status = status,
    createdAtEpochMs = createdAtEpochMs,
    scheduledAtEpochMs = scheduledAtEpochMs,
    startedAtEpochMs = startedAtEpochMs,
    completedAtEpochMs = completedAtEpochMs,
    currentStepId = currentStepId,
    lastErrorCode = lastErrorCode,
    lastErrorSummary = lastErrorSummary,
    cancelRequested = cancelRequested,
    pauseRequested = pauseRequested,
    revision = revision,
    retryOfJobId = retryOfJobId,
    scheduleOccurrenceAtEpochMs = scheduleOccurrenceAtEpochMs
)

internal fun AutomationJobRecord.toEntity(): AutomationJobEntity = AutomationJobEntity(
    jobId = jobId,
    projectId = projectId,
    workflowId = workflowId,
    workflowVersion = workflowVersion,
    status = status,
    createdAtEpochMs = createdAtEpochMs,
    scheduledAtEpochMs = scheduledAtEpochMs,
    startedAtEpochMs = startedAtEpochMs,
    completedAtEpochMs = completedAtEpochMs,
    currentStepId = currentStepId,
    lastErrorCode = lastErrorCode,
    lastErrorSummary = lastErrorSummary,
    cancelRequested = cancelRequested,
    pauseRequested = pauseRequested,
    revision = revision,
    retryOfJobId = retryOfJobId,
    scheduleOccurrenceAtEpochMs = scheduleOccurrenceAtEpochMs
)

internal fun AutomationConnectorBindingEntity.toRecord(): AutomationConnectorBindingRecord =
    AutomationConnectorBindingRecord(
        bindingId = bindingId,
        bindingScope = bindingScope,
        projectId = projectId,
        jobId = jobId,
        connectorId = connectorId,
        connectorVersion = connectorVersion,
        category = category,
        configSchemaVersion = configSchemaVersion,
        configJson = configJson,
        capabilitySnapshotJson = capabilitySnapshotJson,
        enabled = enabled,
        createdAtEpochMs = createdAtEpochMs
    )

internal fun AutomationConnectorBindingRecord.toEntity(): AutomationConnectorBindingEntity =
    AutomationConnectorBindingEntity(
        bindingId = bindingId,
        bindingScope = bindingScope,
        projectId = projectId,
        jobId = jobId,
        connectorId = connectorId,
        connectorVersion = connectorVersion,
        category = category,
        configSchemaVersion = configSchemaVersion,
        configJson = configJson,
        capabilitySnapshotJson = capabilitySnapshotJson,
        enabled = enabled,
        createdAtEpochMs = createdAtEpochMs
    )

internal fun AutomationStepEntity.toRecord(): AutomationStepRecord = AutomationStepRecord(
    stepId = stepId,
    jobId = jobId,
    stepKey = stepKey,
    stepType = stepType,
    connectorBindingId = connectorBindingId,
    status = status,
    attemptCount = attemptCount,
    nextRetryAtEpochMs = nextRetryAtEpochMs,
    startedAtEpochMs = startedAtEpochMs,
    completedAtEpochMs = completedAtEpochMs,
    errorCategory = errorCategory,
    redactedErrorDetail = redactedErrorDetail,
    executionLeaseOwner = executionLeaseOwner,
    executionLeaseExpiresAtEpochMs = executionLeaseExpiresAtEpochMs,
    executionLeaseBootMarker = executionLeaseBootMarker,
    revision = revision,
    waitingReason = waitingReason
)

internal fun AutomationStepRecord.toEntity(): AutomationStepEntity = AutomationStepEntity(
    stepId = stepId,
    jobId = jobId,
    stepKey = stepKey,
    stepType = stepType,
    connectorBindingId = connectorBindingId,
    status = status,
    attemptCount = attemptCount,
    nextRetryAtEpochMs = nextRetryAtEpochMs,
    startedAtEpochMs = startedAtEpochMs,
    completedAtEpochMs = completedAtEpochMs,
    errorCategory = errorCategory,
    redactedErrorDetail = redactedErrorDetail,
    executionLeaseOwner = executionLeaseOwner,
    executionLeaseExpiresAtEpochMs = executionLeaseExpiresAtEpochMs,
    executionLeaseBootMarker = executionLeaseBootMarker,
    revision = revision,
    waitingReason = waitingReason
)

internal fun AutomationStepDependencyEntity.toRecord(): AutomationStepDependencyRecord =
    AutomationStepDependencyRecord(
        dependencyId = dependencyId,
        jobId = jobId,
        fromStepId = fromStepId,
        toStepId = toStepId,
        dependencyKind = dependencyKind,
        conditionType = conditionType,
        conditionParamJson = conditionParamJson,
        allowSkippedUpstream = allowSkippedUpstream
    )

internal fun AutomationStepDependencyRecord.toEntity(): AutomationStepDependencyEntity =
    AutomationStepDependencyEntity(
        dependencyId = dependencyId,
        jobId = jobId,
        fromStepId = fromStepId,
        toStepId = toStepId,
        dependencyKind = dependencyKind,
        conditionType = conditionType,
        conditionParamJson = conditionParamJson,
        allowSkippedUpstream = allowSkippedUpstream
    )

internal fun AutomationOutboxEventEntity.toRecord(): AutomationOutboxEventRecord =
    AutomationOutboxEventRecord(
        outboxEventId = outboxEventId,
        eventType = eventType,
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        payloadJson = payloadJson,
        payloadSchemaVersion = payloadSchemaVersion,
        dedupeKey = dedupeKey,
        status = status,
        availableAtEpochMs = availableAtEpochMs,
        claimedAtEpochMs = claimedAtEpochMs,
        claimOwner = claimOwner,
        claimExpiresAtEpochMs = claimExpiresAtEpochMs,
        dispatchedWorkName = dispatchedWorkName,
        processedAtEpochMs = processedAtEpochMs,
        attemptCount = attemptCount,
        lastError = lastError,
        deadLetteredAtEpochMs = deadLetteredAtEpochMs,
        revision = revision
    )

internal fun AutomationOutboxEventRecord.toEntity(): AutomationOutboxEventEntity =
    AutomationOutboxEventEntity(
        outboxEventId = outboxEventId,
        eventType = eventType,
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        payloadJson = payloadJson,
        payloadSchemaVersion = payloadSchemaVersion,
        dedupeKey = dedupeKey,
        status = status,
        availableAtEpochMs = availableAtEpochMs,
        claimedAtEpochMs = claimedAtEpochMs,
        claimOwner = claimOwner,
        claimExpiresAtEpochMs = claimExpiresAtEpochMs,
        dispatchedWorkName = dispatchedWorkName,
        processedAtEpochMs = processedAtEpochMs,
        attemptCount = attemptCount,
        lastError = lastError,
        deadLetteredAtEpochMs = deadLetteredAtEpochMs,
        revision = revision
    )
