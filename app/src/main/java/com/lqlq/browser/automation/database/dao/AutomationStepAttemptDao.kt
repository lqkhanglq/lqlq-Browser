package com.lqlq.browser.automation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lqlq.browser.automation.database.entity.AutomationStepAttemptEntity

@Dao
interface AutomationStepAttemptDao {
    @Insert
    fun insert(attempt: AutomationStepAttemptEntity)

    @Query(
        "SELECT * FROM automation_step_attempts " +
            "WHERE stepId = :stepId ORDER BY attemptNumber ASC"
    )
    fun listByStep(stepId: String): List<AutomationStepAttemptEntity>
}
