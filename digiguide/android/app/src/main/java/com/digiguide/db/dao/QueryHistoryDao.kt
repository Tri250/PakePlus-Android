package com.digiguide.db.dao

import androidx.room.*
import com.digiguide.db.entity.QueryHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * SN查询历史DAO
 */
@Dao
interface QueryHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: QueryHistoryEntity): Long

    @Update
    suspend fun update(history: QueryHistoryEntity)

    @Delete
    suspend fun delete(history: QueryHistoryEntity)

    @Query("DELETE FROM query_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM query_history")
    suspend fun deleteAll()

    @Query("SELECT * FROM query_history ORDER BY queryTime DESC")
    fun getAllHistory(): Flow<List<QueryHistoryEntity>>

    @Query("SELECT * FROM query_history ORDER BY queryTime DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 20): Flow<List<QueryHistoryEntity>>

    @Query("SELECT * FROM query_history WHERE sn = :sn ORDER BY queryTime DESC LIMIT 1")
    suspend fun findBySn(sn: String): QueryHistoryEntity?

    @Query("SELECT * FROM query_history WHERE brand = :brand ORDER BY queryTime DESC")
    fun findByBrand(brand: String): Flow<List<QueryHistoryEntity>>

    @Query("SELECT COUNT(*) FROM query_history")
    suspend fun getCount(): Int

    @Query("SELECT * FROM query_history WHERE queryTime >= :startTime AND queryTime <= :endTime ORDER BY queryTime DESC")
    fun getHistoryByTimeRange(startTime: Long, endTime: Long): Flow<List<QueryHistoryEntity>>

    // 非Flow版本，用于导出等场景
    @Query("SELECT * FROM query_history ORDER BY queryTime DESC")
    suspend fun getAllOnce(): List<QueryHistoryEntity>
}