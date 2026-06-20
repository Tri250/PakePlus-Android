package com.batteryhealth.app.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.batteryhealth.app.data.model.BatteryHealthReport;

import java.util.List;

/**
 * 电池健康报告数据访问对象
 */
@Dao
public interface BatteryHealthReportDao {

    @Insert
    long insert(BatteryHealthReport report);

    @Update
    void update(BatteryHealthReport report);

    @Delete
    void delete(BatteryHealthReport report);

    @Query("SELECT * FROM battery_health_report ORDER BY parsed_at DESC")
    List<BatteryHealthReport> getAll();

    @Query("SELECT * FROM battery_health_report ORDER BY parsed_at DESC")
    LiveData<List<BatteryHealthReport>> getAllLiveData();

    @Query("SELECT * FROM battery_health_report ORDER BY parsed_at DESC LIMIT :limit")
    List<BatteryHealthReport> getRecent(int limit);

    @Query("SELECT * FROM battery_health_report WHERE parsed_at >= :startTime ORDER BY parsed_at ASC")
    List<BatteryHealthReport> getSince(long startTime);

    @Query("SELECT * FROM battery_health_report ORDER BY parsed_at DESC LIMIT 1")
    BatteryHealthReport getLatest();

    @Query("SELECT COUNT(*) FROM battery_health_report")
    int getCount();

    @Query("DELETE FROM battery_health_report WHERE parsed_at < :timestamp")
    void deleteOlderThan(long timestamp);

    @Query("DELETE FROM battery_health_report")
    void deleteAll();
}
