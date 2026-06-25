package com.batteryhealth.app.data.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.batteryhealth.app.data.model.HealthCheckHistory;

import java.util.List;

@Dao
public interface HealthCheckHistoryDao {

    @Insert
    long insert(HealthCheckHistory history);

    @Update
    void update(HealthCheckHistory history);

    @Delete
    void delete(HealthCheckHistory history);

    @Query("SELECT * FROM health_check_history ORDER BY timestamp DESC LIMIT :count")
    List<HealthCheckHistory> getRecent(int count);

    @Query("SELECT * FROM health_check_history ORDER BY timestamp DESC")
    List<HealthCheckHistory> getAll();

    @Query("DELETE FROM health_check_history WHERE id NOT IN (SELECT id FROM health_check_history ORDER BY timestamp DESC LIMIT 10)")
    void deleteOldRecords();

    @Query("DELETE FROM health_check_history")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM health_check_history")
    int getCount();
}
