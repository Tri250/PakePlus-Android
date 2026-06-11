package com.digiguide.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.digiguide.db.dao.QueryHistoryDao
import com.digiguide.db.dao.BatteryReportDao
import com.digiguide.db.entity.QueryHistoryEntity
import com.digiguide.db.entity.BatteryReportEntity

/**
 * 应用数据库
 */
@Database(
    entities = [
        QueryHistoryEntity::class,
        BatteryReportEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun queryHistoryDao(): QueryHistoryDao
    abstract fun batteryReportDao(): BatteryReportDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun init(context: Context) {
            INSTANCE = getDatabase(context)
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "digiguide_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}