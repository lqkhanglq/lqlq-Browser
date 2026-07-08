package com.lqlq.browser.automation.model

enum class AutomationJobStatus {
    DRAFT,
    QUEUED,
    RUNNING,
    WAITING_PROVIDER,
    WAITING_USER,
    PAUSED,
    RETRYING,
    FAILED,
    COMPLETED,
    CANCELLED
}

enum class AutomationStepStatus {
    PENDING,
    READY,
    RUNNING,
    WAITING_REMOTE,
    WAITING_USER,
    DOWNLOADING,
    UPLOADING,
    RETRYING,
    FAILED,
    COMPLETED,
    SKIPPED,
    CANCELLED
}

data class AutomationFoundationStatus(
    val initialized: Boolean,
    val repositoryReady: Boolean,
    val artifactStoreReady: Boolean,
    val connectorRegistryReady: Boolean,
    val registeredConnectorCount: Int
)
