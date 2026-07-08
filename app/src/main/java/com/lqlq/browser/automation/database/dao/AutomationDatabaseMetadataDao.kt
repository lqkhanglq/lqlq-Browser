package com.lqlq.browser.automation.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lqlq.browser.automation.database.AutomationDatabaseConstants
import com.lqlq.browser.automation.database.entity.AutomationDatabaseMetadataEntity

@Dao
interface AutomationDatabaseMetadataDao {
    @Upsert
    fun upsert(metadata: AutomationDatabaseMetadataEntity)

    @Query(
        "SELECT * FROM ${AutomationDatabaseConstants.METADATA_TABLE_NAME} " +
            "WHERE `key` = :key LIMIT 1"
    )
    fun getByKey(key: String): AutomationDatabaseMetadataEntity?

    @Query(
        "DELETE FROM ${AutomationDatabaseConstants.METADATA_TABLE_NAME} " +
            "WHERE `key` = :key"
    )
    fun deleteByKey(key: String)
}
