package com.lqlq.browser.automation.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lqlq.browser.automation.database.dao.AutomationDatabaseMetadataDao
import com.lqlq.browser.automation.database.entity.AutomationDatabaseMetadataEntity

@Database(
    entities = [AutomationDatabaseMetadataEntity::class],
    version = AutomationDatabaseConstants.DATABASE_VERSION,
    exportSchema = true
)
abstract class AutomationDatabase : RoomDatabase() {
    abstract fun metadataDao(): AutomationDatabaseMetadataDao

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
