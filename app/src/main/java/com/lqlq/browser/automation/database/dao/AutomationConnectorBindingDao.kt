package com.lqlq.browser.automation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lqlq.browser.automation.database.entity.AutomationConnectorBindingEntity

@Dao
interface AutomationConnectorBindingDao {
    @Insert
    fun insertAll(bindings: List<AutomationConnectorBindingEntity>)

    @Query(
        "SELECT * FROM automation_connector_bindings " +
            "WHERE bindingId = :bindingId LIMIT 1"
    )
    fun getById(bindingId: String): AutomationConnectorBindingEntity?

    @Query(
        "SELECT * FROM automation_connector_bindings " +
            "WHERE jobId = :jobId ORDER BY createdAtEpochMs ASC"
    )
    fun listByJob(jobId: String): List<AutomationConnectorBindingEntity>

    @Query(
        "SELECT * FROM automation_connector_bindings " +
            "WHERE projectId = :projectId AND bindingScope = 'PROJECT' ORDER BY createdAtEpochMs ASC"
    )
    fun listByProject(projectId: String): List<AutomationConnectorBindingEntity>
}
