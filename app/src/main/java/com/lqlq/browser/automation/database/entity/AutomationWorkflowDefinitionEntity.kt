package com.lqlq.browser.automation.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "automation_workflow_definitions",
    primaryKeys = ["workflowId", "workflowVersion"],
    indices = [
        Index(value = ["status"]),
        Index(value = ["minimumAppVersionCode"])
    ]
)
data class AutomationWorkflowDefinitionEntity(
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
