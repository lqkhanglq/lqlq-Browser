package com.lqlq.browser.automation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lqlq.browser.automation.database.entity.AutomationStepDependencyEntity

@Dao
interface AutomationStepDependencyDao {
    @Insert
    fun insertAll(dependencies: List<AutomationStepDependencyEntity>)

    @Query(
        "SELECT * FROM automation_step_dependencies " +
            "WHERE jobId = :jobId ORDER BY dependencyId ASC"
    )
    fun listByJob(jobId: String): List<AutomationStepDependencyEntity>

    @Query(
        "SELECT * FROM automation_step_dependencies " +
            "WHERE toStepId = :stepId ORDER BY dependencyId ASC"
    )
    fun listIncoming(stepId: String): List<AutomationStepDependencyEntity>

    @Query(
        "SELECT * FROM automation_step_dependencies " +
            "WHERE fromStepId = :stepId ORDER BY dependencyId ASC"
    )
    fun listOutgoing(stepId: String): List<AutomationStepDependencyEntity>
}
