package com.lqlq.browser.automation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lqlq.browser.automation.database.entity.AutomationWorkflowDefinitionEntity

@Dao
interface AutomationWorkflowDefinitionDao {
    @Insert
    fun insert(definition: AutomationWorkflowDefinitionEntity)

    @Query(
        "SELECT * FROM automation_workflow_definitions " +
            "WHERE workflowId = :workflowId AND workflowVersion = :workflowVersion LIMIT 1"
    )
    fun getByIdAndVersion(
        workflowId: String,
        workflowVersion: Int
    ): AutomationWorkflowDefinitionEntity?

    @Query(
        "SELECT * FROM automation_workflow_definitions " +
            "WHERE workflowId = :workflowId AND status = :status " +
            "ORDER BY workflowVersion DESC LIMIT 1"
    )
    fun getLatestActiveVersion(
        workflowId: String,
        status: String = "ACTIVE"
    ): AutomationWorkflowDefinitionEntity?
}
