package com.lqlq.browser.automation.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "automation_projects",
    indices = [
        Index(value = ["enabled"]),
        Index(value = ["updatedAtEpochMs"])
    ]
)
data class AutomationProjectEntity(
    @PrimaryKey
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
