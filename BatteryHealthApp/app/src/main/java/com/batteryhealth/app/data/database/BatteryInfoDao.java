package com.batteryhealth.app.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.batteryhealth.app.data.model.BatteryInfo;

import java.util.List;

/**
 * 电池信息数据访问对象
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
    List<BatteryInfo> getSince(long startTime);
    
    @Query("SELECT * FROM battery_info WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    List<BatteryInfo> getBetween(long startTime, long endTime);
    
    @Query("SELECT AVG(health_percentage) FROM battery_info WHERE timestamp >= :startTime")
    float getAverageHealthSince(long startTime);
    
    @Query("SELECT * FROM battery_info ORDER BY timestamp DESC LIMIT 1")
    BatteryInfo getLatest();
    
    @Query("SELECT COUNT(*) FROM battery_info")
    int getCount();
    
    @Query("DELETE FROM battery_info WHERE timestamp < :timestamp")
    void deleteOlderThan(long timestamp);
    
    @Query("DELETE FROM battery_info")
    void deleteAll();
}