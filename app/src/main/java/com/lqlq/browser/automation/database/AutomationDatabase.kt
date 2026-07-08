package com.lqlq.browser.automation.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lqlq.browser.automation.database.dao.AutomationArtifactDao
import com.lqlq.browser.automation.database.dao.AutomationConnectorBindingDao
import com.lqlq.browser.automation.database.dao.AutomationDatabaseMetadataDao
import com.lqlq.browser.automation.database.dao.AutomationJobDao
import com.lqlq.browser.automation.database.dao.AutomationOutboxDao
import com.lqlq.browser.automation.database.dao.AutomationProjectDao
import com.lqlq.browser.automation.database.dao.AutomationStepArtifactDao
import com.lqlq.browser.automation.database.dao.AutomationStepAttemptDao
import com.lqlq.browser.automation.database.dao.AutomationStepDao
import com.lqlq.browser.automation.database.dao.AutomationStepDependencyDao
import com.lqlq.browser.automation.database.dao.AutomationWorkflowDefinitionDao
import com.lqlq.browser.automation.database.entity.AutomationArtifactEntity
import com.lqlq.browser.automation.database.entity.AutomationConnectorBindingEntity
import com.lqlq.browser.automation.database.entity.AutomationDatabaseMetadataEntity
import com.lqlq.browser.automation.database.entity.AutomationJobEntity
import com.lqlq.browser.automation.database.entity.AutomationOutboxEventEntity
import com.lqlq.browser.automation.database.entity.AutomationProjectEntity
import com.lqlq.browser.automation.database.entity.AutomationStepAttemptEntity
import com.lqlq.browser.automation.database.entity.AutomationStepDependencyEntity
import com.lqlq.browser.automation.database.entity.AutomationStepEntity
import com.lqlq.browser.automation.database.entity.AutomationStepInputEntity
import com.lqlq.browser.automation.database.entity.AutomationStepOutputEntity
import com.lqlq.browser.automation.database.entity.AutomationWorkflowDefinitionEntity

@Database(
    entities = [
        AutomationDatabaseMetadataEntity::class,
        AutomationProjectEntity::class,
        AutomationWorkflowDefinitionEntity::class,
        AutomationJobEntity::class,
        AutomationStepEntity::class,
        AutomationStepDependencyEntity::class,
        AutomationStepAttemptEntity::class,
        AutomationArtifactEntity::class,
        AutomationStepInputEntity::class,
        AutomationStepOutputEntity::class,
        AutomationConnectorBindingEntity::class,
        AutomationOutboxEventEntity::class
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true
)
abstract class AutomationDatabase : RoomDatabase() {
    abstract fun metadataDao(): AutomationDatabaseMetadataDao
    abstract fun projectDao(): AutomationProjectDao
    abstract fun workflowDefinitionDao(): AutomationWorkflowDefinitionDao
    abstract fun jobDao(): AutomationJobDao
    abstract fun stepDao(): AutomationStepDao
    abstract fun stepDependencyDao(): AutomationStepDependencyDao
    abstract fun stepAttemptDao(): AutomationStepAttemptDao
    abstract fun artifactDao(): AutomationArtifactDao
    abstract fun stepArtifactDao(): AutomationStepArtifactDao
    abstract fun connectorBindingDao(): AutomationConnectorBindingDao
    abstract fun outboxDao(): AutomationOutboxDao

    companion object {
        fun create(context: Context): AutomationDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AutomationDatabase::class.java,
                AutomationDatabaseConstants.DATABASE_NAME
            ).build()
        }
    }
}
