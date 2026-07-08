package com.lqlq.browser.automation

data class AutomationStartRequest(
    val topic: String,
    val contentServiceId: String,
    val voiceServiceId: String,
    val videoServiceId: String,
    val publishServiceId: String,
    val publishMode: String
)

data class AutomationUiJobSnapshot(
    val jobId: String,
    val projectId: String,
    val workflowId: String,
    val workflowVersion: Int,
    val topic: String,
    val status: String,
    val createdAtEpochMs: Long,
    val publishMode: String,
    val steps: List<AutomationUiStepSnapshot>,
    val dependencies: List<AutomationUiDependencySnapshot>
)

data class AutomationUiStepSnapshot(
    val stepId: String,
    val stepKey: String,
    val stepType: String,
    val status: String,
    val connectorBindingId: String?,
    val waitingReason: String?
)

data class AutomationUiDependencySnapshot(
    val dependencyId: String,
    val fromStepId: String,
    val toStepId: String
)

data class AutomationUiRecentJob(
    val jobId: String,
    val topic: String,
    val status: String,
    val createdAtEpochMs: Long
)
