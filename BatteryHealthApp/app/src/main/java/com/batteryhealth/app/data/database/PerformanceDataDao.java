package com.batteryhealth.app.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.batteryhealth.app.data.model.PerformanceData;

import java.util.List;

/**
 * 性能数据访问对象
 */
@Dao
public interface PerformanceDataDao {
    
    @Insert
    long insert(PerformanceData data);
    
    @Update
    void update(PerformanceData data);
    
    @Delete
    void delete(PerformanceData data);
    
    @Query("SELECT * FROM performance_data ORDER BY timestamp DESC")
    List<PerformanceData> getAll();
    
    @Query("SELECT * FROM performance_data ORDER BY timestamp DESC")
    LiveData<List<PerformanceData>> getAllLiveData();
    
    @Query("SELECT * FROM performance_data WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<PerformanceData> getSince(long startTime);
    
    @Query("SELECT * FROM performance_data WHERE has_issue = 1 ORDER BY timestamp DESC")
    List<PerformanceData> getIssues();
    
    @Query("SELECT * FROM performance_data WHERE app_package = :packageName ORDER BY timestamp DESC LIMIT 1")
    PerformanceData getLatestByApp(String packageName);
    
    @Query("SELECT AVG(performance_score) FROM performance_data WHERE timestamp >= :startTime")
    float getAverageScoreSince(long startTime);
    
    @Query("SELECT COUNT(*) FROM performance_data WHERE has_issue = 1 AND timestamp >= :startTime")
    int getIssueCountSince(long startTime);
    
    @Query("DELETE FROM performance_data WHERE timestamp < :timestamp")
    void deleteOlderThan(long timestamp);
    
    @Query("DELETE FROM performance_data")
    void deleteAll();
}