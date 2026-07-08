package com.lqlq.browser.automation.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "automation_steps",
    foreignKeys = [
        ForeignKey(
            entity = AutomationJobEntity::class,
            parentColumns = ["jobId"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AutomationConnectorBindingEntity::class,
            parentColumns = ["bindingId"],
            childColumns = ["connectorBindingId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["jobId", "stepKey"], unique = true),
        Index(value = ["jobId", "status"]),
        Index(value = ["status", "nextRetryAtEpochMs"]),
        Index(value = ["executionLeaseExpiresAtEpochMs"]),
        Index(value = ["connectorBindingId"])
    ]
)
data class AutomationStepEntity(
    @PrimaryKey
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
