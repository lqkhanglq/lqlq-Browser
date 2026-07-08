package com.lqlq.browser.automation.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lqlq.browser.automation.database.AutomationDatabaseConstants

@Entity(tableName = AutomationDatabaseConstants.METADATA_TABLE_NAME)
data class AutomationDatabaseMetadataEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAtEpochMs: Long
)
