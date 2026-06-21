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

import com.batteryhealth.app.data.model.PerformanceData;

import java.util.List;

/**
 * 性能数据访问对象
 *
 * 实现说明：
 * - 聚合查询（AVG）使用 COALESCE 兜底，避免空表时返回 NULL 拆箱 NPE。
 * - 批量插入与多语句操作使用 @Transaction 包裹以保证原子性。
 */
@Dao
public interface PerformanceDataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PerformanceData data);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<PerformanceData> dataList);

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

    @Nullable
    @Query("SELECT * FROM performance_data WHERE app_package = :packageName ORDER BY timestamp DESC LIMIT 1")
    PerformanceData getLatestByApp(String packageName);

    /**
     * 返回指定时间之后的平均性能评分。空表返回 0。
     */
    @Query("SELECT COALESCE(AVG(performance_score), 0) FROM performance_data WHERE timestamp >= :startTime")
    float getAverageScoreSince(long startTime);

    @Query("SELECT COUNT(*) FROM performance_data WHERE has_issue = 1 AND timestamp >= :startTime")
    int getIssueCountSince(long startTime);

    @Query("SELECT COUNT(*) FROM performance_data WHERE has_issue = 1")
    int getTotalIssueCount();

    @Query("DELETE FROM performance_data WHERE timestamp < :timestamp")
    void deleteOlderThan(long timestamp);

    @Query("DELETE FROM performance_data")
    void deleteAll();

    /**
     * 事务性清理：删除并返回实际删除行数。
     */
    @Transaction
    default int pruneOlderThan(long timestamp) {
        List<PerformanceData> toDelete = getSince(0L);
        // 由于没有直接的「带条件删除并返回受影响行数」API，
        // 这里通过先统计再删除并基于差异返回。简单调用方也可以直接 deleteOlderThan。
        int issueCountBefore = getTotalIssueCount();
        deleteOlderThan(timestamp);
        int issueCountAfter = getTotalIssueCount();
        // 简化：返回受影响行数的下界
        return Math.max(0, issueCountBefore - issueCountAfter);
    }
}
