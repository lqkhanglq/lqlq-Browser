package com.lqlq.browser.automation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lqlq.browser.automation.database.entity.AutomationProjectEntity

@Dao
interface AutomationProjectDao {
    @Insert
    fun insert(project: AutomationProjectEntity)

    @Update
    fun update(project: AutomationProjectEntity)

    @Query("SELECT * FROM automation_projects WHERE projectId = :projectId LIMIT 1")
    fun getById(projectId: String): AutomationProjectEntity?

    @Query(
        "SELECT * FROM automation_projects " +
            "WHERE enabled = 1 AND deletedAtEpochMs IS NULL " +
            "ORDER BY updatedAtEpochMs DESC"
    )
    fun listActive(): List<AutomationProjectEntity>
}
