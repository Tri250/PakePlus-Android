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

import com.batteryhealth.app.data.model.PowerHistory;

import java.util.List;

/**
 * 充电功率历史数据访问对象
 *
 * 实现说明：
 * - 聚合查询（AVG/MAX）使用 COALESCE 兜底，避免空表时返回 NULL 拆箱 NPE。
 * - 批量插入与多语句操作使用 @Transaction 包裹以保证原子性。
 */
@Dao
public interface PowerHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PowerHistory history);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<PowerHistory> histories);

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

    @Query("SELECT * FROM power_history WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    List<PowerHistory> getBetween(long startTime, long endTime);

    /**
     * 指定会话的平均功率。无记录时返回 0。
     */
    @Query("SELECT COALESCE(AVG(power), 0) FROM power_history WHERE session_id = :sessionId")
    float getAveragePowerBySession(String sessionId);

    /**
     * 指定会话的最大功率。无记录时返回 0。
     */
    @Query("SELECT COALESCE(MAX(power), 0) FROM power_history WHERE session_id = :sessionId")
    float getMaxPowerBySession(String sessionId);

    @Nullable
    @Query("SELECT * FROM power_history ORDER BY timestamp DESC LIMIT 1")
    PowerHistory getLatest();

    @Query("SELECT DISTINCT session_id FROM power_history WHERE session_id IS NOT NULL ORDER BY session_id DESC")
    List<String> getAllSessions();

    @Query("SELECT COUNT(*) FROM power_history")
    int getCount();

    @Query("DELETE FROM power_history WHERE timestamp < :timestamp")
    void deleteOlderThan(long timestamp);

    @Query("DELETE FROM power_history")
    void deleteAll();

    /**
     * 事务性清理：先删除再返回剩余行数。
     */
    @Transaction
    default int pruneOlderThan(long timestamp) {
        int before = getCount();
        deleteOlderThan(timestamp);
        return before - getCount();
    }
}
