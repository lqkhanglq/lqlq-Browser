package com.lqlq.browser.automation.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "automation_step_dependencies",
    foreignKeys = [
        ForeignKey(
            entity = AutomationJobEntity::class,
            parentColumns = ["jobId"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AutomationStepEntity::class,
            parentColumns = ["stepId"],
            childColumns = ["fromStepId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AutomationStepEntity::class,
            parentColumns = ["stepId"],
            childColumns = ["toStepId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["jobId", "fromStepId", "toStepId"], unique = true),
        Index(value = ["fromStepId"]),
        Index(value = ["toStepId"]),
        Index(value = ["jobId"])
    ]
)
data class AutomationStepDependencyEntity(
    @PrimaryKey
    val dependencyId: String,
    val jobId: String,
    val fromStepId: String,
    val toStepId: String,
    val dependencyKind: String,
    val conditionType: String,
    val conditionParamJson: String?,
    val allowSkippedUpstream: Boolean
)
