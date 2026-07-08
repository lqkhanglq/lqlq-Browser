package com.lqlq.browser.automation.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "automation_outbox_events",
    indices = [
        Index(value = ["dedupeKey"], unique = true),
        Index(value = ["status", "availableAtEpochMs"]),
        Index(value = ["claimExpiresAtEpochMs"]),
        Index(value = ["aggregateType", "aggregateId"]),
        Index(value = ["dispatchedWorkName"])
    ]
)
data class AutomationOutboxEventEntity(
    @PrimaryKey
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
