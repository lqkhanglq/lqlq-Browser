package com.lqlq.browser.automation.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "automation_connector_bindings",
    foreignKeys = [
        ForeignKey(
            entity = AutomationProjectEntity::class,
            parentColumns = ["projectId"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = AutomationJobEntity::class,
            parentColumns = ["jobId"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["jobId"]),
        Index(value = ["connectorId"]),
        Index(value = ["jobId", "category"])
    ]
)
data class AutomationConnectorBindingEntity(
    @PrimaryKey
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
