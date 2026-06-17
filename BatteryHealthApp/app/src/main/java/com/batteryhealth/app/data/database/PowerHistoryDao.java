package com.batteryhealth.app.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.batteryhealth.app.data.model.PowerHistory;

import java.util.List;

/**
 * 充电功率历史数据访问对象
 */
@Dao
public interface PowerHistoryDao {
    
    @Insert
    long insert(PowerHistory history);
    
    @Update
    void update(PowerHistory history);
    
    @Delete
    void delete(PowerHistory history);
    
    @Query("SELECT * FROM power_history ORDER BY timestamp DESC")
    List<PowerHistory> getAll();
    
    @Query("SELECT * FROM power_history ORDER BY timestamp DESC")
    LiveData<List<PowerHistory>> getAllLiveData();
    
    @Query("SELECT * FROM power_history WHERE session_id = :sessionId ORDER BY timestamp ASC")
    List<PowerHistory> getBySession(String sessionId);
    
    @Query("SELECT * FROM power_history WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<PowerHistory> getSince(long startTime);
    
    @Query("SELECT AVG(power) FROM power_history WHERE session_id = :sessionId")
    float getAveragePowerBySession(String sessionId);
    
    @Query("SELECT MAX(power) FROM power_history WHERE session_id = :sessionId")
    float getMaxPowerBySession(String sessionId);
    
    @Query("SELECT * FROM power_history ORDER BY timestamp DESC LIMIT 1")
    PowerHistory getLatest();
    
    @Query("SELECT DISTINCT session_id FROM power_history ORDER BY session_id DESC")
    List<String> getAllSessions();
    
    @Query("DELETE FROM power_history WHERE timestamp < :timestamp")
    void deleteOlderThan(long timestamp);
    
    @Query("DELETE FROM power_history")
    void deleteAll();
}