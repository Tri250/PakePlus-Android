package com.batteryhealth.app.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.batteryhealth.app.data.model.DeviceConfig;

import java.util.List;

/**
 * 设备配置信息数据访问对象
 */
@Dao
public interface DeviceConfigDao {

    @Insert
    long insert(DeviceConfig config);

    @Update
    void update(DeviceConfig config);

    @Delete
    void delete(DeviceConfig config);

    @Query("SELECT * FROM device_config ORDER BY id DESC")
    List<DeviceConfig> getAll();

    @Query("SELECT * FROM device_config ORDER BY id DESC")
    LiveData<List<DeviceConfig>> getAllLiveData();

    @Query("SELECT * FROM device_config ORDER BY id DESC LIMIT 1")
    DeviceConfig getLatest();

    @Query("SELECT * FROM device_config WHERE id = :id")
    DeviceConfig getById(long id);

    @Query("SELECT COUNT(*) FROM device_config")
    int getCount();

    @Query("DELETE FROM device_config")
    void deleteAll();
}
