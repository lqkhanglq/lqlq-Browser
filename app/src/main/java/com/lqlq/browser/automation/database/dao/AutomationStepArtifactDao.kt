package com.lqlq.browser.automation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lqlq.browser.automation.database.entity.AutomationStepInputEntity
import com.lqlq.browser.automation.database.entity.AutomationStepOutputEntity

@Dao
interface AutomationStepArtifactDao {
    @Insert
    fun insertInputs(inputs: List<AutomationStepInputEntity>)

    @Insert
    fun insertOutputs(outputs: List<AutomationStepOutputEntity>)

    @Query(
        "SELECT * FROM automation_step_inputs " +
            "WHERE stepId = :stepId ORDER BY ordinal ASC"
    )
    fun listInputs(stepId: String): List<AutomationStepInputEntity>

    @Query(
        "SELECT * FROM automation_step_outputs " +
            "WHERE stepId = :stepId ORDER BY ordinal ASC"
    )
    fun listOutputs(stepId: String): List<AutomationStepOutputEntity>
}
