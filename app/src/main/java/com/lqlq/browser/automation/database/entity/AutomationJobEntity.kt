package com.lqlq.browser.automation.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "automation_jobs",
    foreignKeys = [
        ForeignKey(
            entity = AutomationProjectEntity::class,
            parentColumns = ["projectId"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = AutomationWorkflowDefinitionEntity::class,
            parentColumns = ["workflowId", "workflowVersion"],
            childColumns = ["workflowId", "workflowVersion"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["status"]),
        Index(value = ["projectId", "createdAtEpochMs"]),
        Index(value = ["scheduledAtEpochMs"]),
        Index(value = ["retryOfJobId"]),
        Index(value = ["workflowId", "workflowVersion"])
    ]
)
data class AutomationJobEntity(
    @PrimaryKey
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
