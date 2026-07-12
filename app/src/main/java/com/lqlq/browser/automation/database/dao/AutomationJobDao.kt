package com.lqlq.browser.automation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lqlq.browser.automation.database.entity.AutomationJobEntity

@Dao
interface AutomationJobDao {
    @Insert
    fun insert(job: AutomationJobEntity)

    @Query("SELECT * FROM automation_jobs WHERE jobId = :jobId LIMIT 1")
    fun getById(jobId: String): AutomationJobEntity?

    @Query(
        "SELECT * FROM automation_jobs " +
            "WHERE status = :status ORDER BY createdAtEpochMs DESC"
    )
    fun listByStatus(status: String): List<AutomationJobEntity>

    @Query(
        "SELECT * FROM automation_jobs " +
            "WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC LIMIT :limit"
    )
    fun listRecentByProject(
        projectId: String,
        limit: Int
    ): List<AutomationJobEntity>

    // steps/artifacts co onDelete = CASCADE tren jobId nen tu dong bi xoa theo;
    // connector_bindings/outbox khong co FK cascade nen phai xoa rieng (xem
    // AutomationConnectorBindingDao/AutomationOutboxDao).
    @Query("DELETE FROM automation_jobs WHERE jobId = :jobId")
    fun deleteById(jobId: String): Int
}
