package com.batteryhealth.app.data.database;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PerformanceData;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.data.model.BatteryOriginRecord;

/**
 * Room数据库主类
 *
 * 包含所有数据表的定义
 *
 * 版本历史：
 *  v1-v3：早期结构（健康度、性能、功率历史表）
 *  v4：新增 battery_origin_record 表
 *  v5：为所有时间序列表添加 timestamp 索引，并为高频查询字段
 *      (performance_data.app_package / has_issue, power_history.session_id)
 *      添加索引，避免全表扫描。
 */
@Database(
    entities = {
        BatteryInfo.class,
        PerformanceData.class,
        PowerHistory.class,
        BatteryOriginRecord.class
    },
    version = 5,
    exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract BatteryInfoDao batteryInfoDao();
    public abstract PerformanceDataDao performanceDataDao();
    public abstract PowerHistoryDao powerHistoryDao();
    public abstract BatteryOriginRecordDao batteryOriginRecordDao();

    /**
     * v4 → v5 迁移：为已有表补充索引。
     *
     * 索引名称与 Room 通过 @Index 自动生成的名称保持一致
     * （格式：index_<table>_<column>），确保 schema 校验通过。
     */
    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_battery_info_timestamp` ON `battery_info`(`timestamp`)");
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_performance_data_timestamp` ON `performance_data`(`timestamp`)");
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_performance_data_app_package` ON `performance_data`(`app_package`)");
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_performance_data_has_issue` ON `performance_data`(`has_issue`)");
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_power_history_timestamp` ON `power_history`(`timestamp`)");
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_power_history_session_id` ON `power_history`(`session_id`)");
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_battery_origin_record_timestamp` ON `battery_origin_record`(`timestamp`)");
        }
    };
}
