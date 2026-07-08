package com.lqlq.browser.automation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lqlq.browser.automation.database.entity.AutomationArtifactEntity

@Dao
interface AutomationArtifactDao {
    @Insert
    fun insert(artifact: AutomationArtifactEntity)

    @Update
    fun update(artifact: AutomationArtifactEntity)

    @Query("SELECT * FROM automation_artifacts WHERE artifactId = :artifactId LIMIT 1")
    fun getById(artifactId: String): AutomationArtifactEntity?

    @Query(
        "SELECT * FROM automation_artifacts " +
            "WHERE jobId = :jobId ORDER BY createdAtEpochMs ASC"
    )
    fun listByJob(jobId: String): List<AutomationArtifactEntity>
}
