package com.batteryhealth.app.data.database;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.batteryhealth.app.data.model.BatteryInfo;

import java.util.List;

/**
 * 电池信息数据访问对象
 *
 * 注意：
 * 1. timestamp 字段建立了索引，以加速按时间范围查询（getSince、getBetween、
 *    getAverageHealthSince、getCountSince、deleteOlderThan）。
 * 2. health_percentage 字段建立了索引，以加速按健康度排序或筛选的查询。
 * 3. 默认主键 id 已有 Room 隐式索引。
 *
 * 实现说明：
 * - 聚合查询（AVG）使用 COALESCE 兜底，避免空表时返回 NULL 进而导致 Java 端
 *   拆箱 NPE。
 * - 批量插入（insertAll）和多语句操作（pruneOlderThan）使用 @Transaction 包裹
 *   以保证原子性。
 */
@Dao
public interface BatteryInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(BatteryInfo batteryInfo);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<BatteryInfo> batteryInfos);

    @Update
    void update(BatteryInfo batteryInfo);

    @Delete
    void delete(BatteryInfo batteryInfo);

    @Query("SELECT * FROM battery_info ORDER BY timestamp DESC")
    List<BatteryInfo> getAll();

    @Query("SELECT * FROM battery_info ORDER BY timestamp DESC")
    LiveData<List<BatteryInfo>> getAllLiveData();

    @Query("SELECT * FROM battery_info ORDER BY timestamp DESC LIMIT :limit")
    List<BatteryInfo> getRecent(int limit);

    @Query("SELECT * FROM battery_info WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<BatteryInfo> getSince(long startTime);

    @Query("SELECT * FROM battery_info WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    List<BatteryInfo> getBetween(long startTime, long endTime);

    /**
     * 返回指定时间之后的平均健康度。空表返回 0，避免 NULL 拆箱。
     */
    @Query("SELECT COALESCE(AVG(health_percentage), 0) FROM battery_info WHERE timestamp >= :startTime")
    float getAverageHealthSince(long startTime);

    @Query("SELECT COUNT(*) FROM battery_info WHERE timestamp >= :startTime")
    int getCountSince(long startTime);

    @Nullable
    @Query("SELECT * FROM battery_info ORDER BY timestamp DESC LIMIT 1")
    BatteryInfo getLatest();

    @Query("SELECT COUNT(*) FROM battery_info")
    int getCount();

    @Query("DELETE FROM battery_info WHERE timestamp < :timestamp")
    void deleteOlderThan(long timestamp);

    @Query("DELETE FROM battery_info")
    void deleteAll();

    /**
     * 事务性清理：先删除再统计，保证外部读取到的 count 与实际一致。
     */
    @Transaction
    default int pruneOlderThan(long timestamp) {
        int before = getCount();
        deleteOlderThan(timestamp);
        return before - getCount();
    }
}
