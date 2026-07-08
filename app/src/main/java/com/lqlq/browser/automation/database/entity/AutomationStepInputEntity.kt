package com.lqlq.browser.automation.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "automation_step_inputs",
    foreignKeys = [
        ForeignKey(
            entity = AutomationStepEntity::class,
            parentColumns = ["stepId"],
            childColumns = ["stepId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AutomationArtifactEntity::class,
            parentColumns = ["artifactId"],
            childColumns = ["artifactId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["stepId", "role", "ordinal"], unique = true),
        Index(value = ["stepId"]),
        Index(value = ["artifactId"])
    ]
)
data class AutomationStepInputEntity(
    @PrimaryKey
    val stepInputId: String,
    val stepId: String,
    val artifactId: String,
    val role: String,
    val ordinal: Int,
    val required: Boolean
)
