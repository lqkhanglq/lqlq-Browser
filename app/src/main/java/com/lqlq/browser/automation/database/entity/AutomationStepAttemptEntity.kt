package com.lqlq.browser.automation.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "automation_step_attempts",
    foreignKeys = [
        ForeignKey(
            entity = AutomationStepEntity::class,
            parentColumns = ["stepId"],
            childColumns = ["stepId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["stepId", "attemptNumber"], unique = true),
        Index(value = ["stepId"]),
        Index(value = ["idempotencyKey"])
    ]
)
data class AutomationStepAttemptEntity(
    @PrimaryKey
    val attemptId: String,
    val stepId: String,
    val attemptNumber: Int,
    val leaseOwner: String?,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long?,
    val outcome: String,
    val errorCategory: String?,
    val redactedErrorDetail: String?,
    val idempotencyKey: String
)
