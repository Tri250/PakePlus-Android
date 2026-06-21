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
 * 包含所有数据表的定义。
 *
 * 注意：
 * - schema 文件建议导出（{@code exportSchema = true}）以便版本管理与回归测试，
 *   当前为简化部署关闭该选项。
 * - 数据库 schema 变更必须提供 {@code Migration} 并在 {@code Room.databaseBuilder}
 *   中显式注册；当前 v3 假设在生产端已通过单独的迁移实现接入。
 */
@Database(
    entities = {
        BatteryInfo.class,
        PerformanceData.class,
        PowerHistory.class
    },
    version = 3,
    exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract BatteryInfoDao batteryInfoDao();
    public abstract PerformanceDataDao performanceDataDao();
    public abstract PowerHistoryDao powerHistoryDao();
}
