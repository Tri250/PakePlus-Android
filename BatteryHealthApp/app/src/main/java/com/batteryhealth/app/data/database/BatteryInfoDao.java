package com.batteryhealth.app.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Index;
import androidx.room.Query;
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
 */
@Dao
public interface BatteryInfoDao {
    
    @Insert
    long insert(BatteryInfo batteryInfo);
    
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
    @Index(value = {"timestamp"})
    List<BatteryInfo> getSince(long startTime);
    
    @Query("SELECT * FROM battery_info WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    @Index(value = {"timestamp"})
    List<BatteryInfo> getBetween(long startTime, long endTime);
    
    @Query("SELECT AVG(health_percentage) FROM battery_info WHERE timestamp >= :startTime")
    @Index(value = {"timestamp", "health_percentage"})
    float getAverageHealthSince(long startTime);

    @Query("SELECT COUNT(*) FROM battery_info WHERE timestamp >= :startTime")
    @Index(value = {"timestamp"})
    int getCountSince(long startTime);

    @Query("SELECT * FROM battery_info ORDER BY timestamp DESC LIMIT 1")
    BatteryInfo getLatest();
    
    @Query("SELECT COUNT(*) FROM battery_info")
    int getCount();
    
    @Query("DELETE FROM battery_info WHERE timestamp < :timestamp")
    @Index(value = {"timestamp"})
    void deleteOlderThan(long timestamp);
    
    @Query("DELETE FROM battery_info")
    void deleteAll();
}