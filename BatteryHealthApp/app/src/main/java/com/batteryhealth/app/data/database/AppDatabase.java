package com.batteryhealth.app.data.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PerformanceData;
import com.batteryhealth.app.data.model.PowerHistory;

/**
 * Room数据库主类
 * 
 * 包含所有数据表的定义
 */
@Database(
    entities = {
        BatteryInfo.class,
        PerformanceData.class,
        PowerHistory.class
    },
    version = 1,
    exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    
    public abstract BatteryInfoDao batteryInfoDao();
    public abstract PerformanceDataDao performanceDataDao();
    public abstract PowerHistoryDao powerHistoryDao();
}