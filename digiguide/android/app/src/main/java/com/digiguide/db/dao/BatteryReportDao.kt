package com.digiguide.db.dao

import androidx.room.*
import com.digiguide.db.entity.BatteryReportEntity
import kotlinx.coroutines.flow.Flow

/**
 * 电池报告DAO
 */
@Dao
interface BatteryReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: BatteryReportEntity): Long

    @Update
    suspend fun update(report: BatteryReportEntity)

    @Delete
    suspend fun delete(report: BatteryReportEntity)

    @Query("DELETE FROM battery_reports WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM battery_reports")
    suspend fun deleteAll()

    @Query("SELECT * FROM battery_reports ORDER BY reportTime DESC")
    fun getAllReports(): Flow<List<BatteryReportEntity>>

    @Query("SELECT * FROM battery_reports ORDER BY reportTime DESC LIMIT :limit")
    fun getRecentReports(limit: Int = 20): Flow<List<BatteryReportEntity>>

    @Query("SELECT * FROM battery_reports WHERE id = :id")
    suspend fun findById(id: Long): BatteryReportEntity?

    @Query("SELECT * FROM battery_reports WHERE brand = :brand ORDER BY reportTime DESC")
    fun findByBrand(brand: String): Flow<List<BatteryReportEntity>>

    @Query("SELECT * FROM battery_reports WHERE model = :model ORDER BY reportTime DESC")
    fun findByModel(model: String): Flow<List<BatteryReportEntity>>

    @Query("SELECT COUNT(*) FROM battery_reports")
    suspend fun getCount(): Int

    @Query("SELECT AVG(healthPercentage) FROM battery_reports")
    suspend fun getAverageHealth(): Float?

    @Query("SELECT * FROM battery_reports WHERE healthPercentage < :threshold ORDER BY healthPercentage ASC")
    fun getLowHealthReports(threshold: Float = 80f): Flow<List<BatteryReportEntity>>

    @Query("SELECT * FROM battery_reports WHERE reportTime >= :startTime AND reportTime <= :endTime ORDER BY reportTime DESC")
    fun getReportsByTimeRange(startTime: Long, endTime: Long): Flow<List<BatteryReportEntity>>
}