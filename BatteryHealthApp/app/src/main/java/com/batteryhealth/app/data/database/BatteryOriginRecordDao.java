package com.batteryhealth.app.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.batteryhealth.app.data.model.BatteryOriginRecord;

import java.util.List;

@Dao
public interface BatteryOriginRecordDao {

    @Insert
    long insert(BatteryOriginRecord record);

    @Query("SELECT * FROM battery_origin_record ORDER BY timestamp DESC")
    List<BatteryOriginRecord> getAll();

    @Query("SELECT * FROM battery_origin_record ORDER BY timestamp DESC")
    LiveData<List<BatteryOriginRecord>> getAllLiveData();

    @Query("SELECT * FROM battery_origin_record ORDER BY timestamp DESC LIMIT :limit")
    List<BatteryOriginRecord> getRecent(int limit);

    @Query("SELECT * FROM battery_origin_record ORDER BY timestamp DESC LIMIT 1")
    BatteryOriginRecord getLatest();

    @Query("SELECT * FROM battery_origin_record WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<BatteryOriginRecord> getSince(long startTime);

    @Query("SELECT COUNT(*) FROM battery_origin_record")
    int getCount();

    @Query("DELETE FROM battery_origin_record WHERE timestamp < :timestamp")
    void deleteOlderThan(long timestamp);

    @Query("DELETE FROM battery_origin_record")
    void deleteAll();
}
