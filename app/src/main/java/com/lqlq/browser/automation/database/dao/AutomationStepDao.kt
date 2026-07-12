package com.lqlq.browser.automation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lqlq.browser.automation.database.entity.AutomationStepEntity

@Dao
interface AutomationStepDao {
    @Insert
    fun insertAll(steps: List<AutomationStepEntity>)

    @Query("SELECT * FROM automation_steps WHERE stepId = :stepId LIMIT 1")
    fun getById(stepId: String): AutomationStepEntity?

    @Query(
        "SELECT * FROM automation_steps " +
            "WHERE jobId = :jobId ORDER BY stepKey ASC"
    )
    fun listByJob(jobId: String): List<AutomationStepEntity>

    @Query(
        "SELECT * FROM automation_steps " +
            "WHERE jobId = :jobId AND status = :status ORDER BY stepKey ASC"
    )
    fun listByJobAndStatus(
        jobId: String,
        status: String
    ): List<AutomationStepEntity>

    // Phai xoa TRUOC connector_bindings (steps.connectorBindingId la FK NO_ACTION
    // toi bindings.bindingId) va SAU artifacts (artifacts.producerStepId la FK
    // NO_ACTION toi steps.stepId) — xem RoomAutomationRepository.deleteJobGraph.
    @Query("DELETE FROM automation_steps WHERE jobId = :jobId")
    fun deleteByJob(jobId: String): Int
}
