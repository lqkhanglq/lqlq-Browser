package com.lqlq.browser.automation.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "automation_artifacts",
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
            childColumns = ["producerStepId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["jobId"]),
        Index(value = ["producerStepId"]),
        Index(value = ["storageClass"]),
        Index(value = ["checksumSha256"]),
        Index(value = ["integrityStatus"])
    ]
)
data class AutomationArtifactEntity(
    @PrimaryKey
    val artifactId: String,
    val jobId: String,
    val producerStepId: String?,
    val artifactType: String,
    val storageClass: String,
    val storageUri: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val checksumSha256: String?,
    val providerId: String?,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
    val sensitivityLevel: String,
    val integrityStatus: String,
    val retentionPolicy: String,
    val remoteReferenceId: String?,
    val exportedContentUri: String?,
    val finalizedAtEpochMs: Long?
)
