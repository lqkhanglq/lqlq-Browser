package com.lqlq.browser.automation.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lqlq.browser.automation.database.entity.AutomationOutboxEventEntity

@Dao
interface AutomationOutboxDao {
    @Insert
    fun insert(event: AutomationOutboxEventEntity)

    @Query(
        "SELECT * FROM automation_outbox_events " +
            "WHERE outboxEventId = :outboxEventId LIMIT 1"
    )
    fun getById(outboxEventId: String): AutomationOutboxEventEntity?

    @Query(
        "SELECT * FROM automation_outbox_events " +
            "WHERE status = :status AND availableAtEpochMs <= :availableAtOrBeforeEpochMs " +
            "ORDER BY availableAtEpochMs ASC"
    )
    fun listPendingAvailable(
        status: String = "PENDING",
        availableAtOrBeforeEpochMs: Long
    ): List<AutomationOutboxEventEntity>
}
