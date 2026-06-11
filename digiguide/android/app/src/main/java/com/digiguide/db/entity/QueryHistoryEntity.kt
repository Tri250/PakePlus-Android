package com.digiguide.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SN查询历史记录
 */
@Entity(tableName = "query_history")
data class QueryHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sn: String,
    val brand: String,
    val factoryYear: Int?,
    val factoryMonth: Int?,
    val factoryWeek: Int?,
    val halfYear: String?,
    val status: String,
    val errorMessage: String?,
    val queryTime: Long = System.currentTimeMillis(),
    val deviceModel: String?
) {
    /**
     * 获取生产日期字符串
     */
    fun getProductionDateString(): String {
        if (factoryYear == null) return "未知"

        val sb = StringBuilder()
        sb.append(factoryYear)

        if (factoryMonth != null) {
            sb.append("-").append(factoryMonth)
        } else if (factoryWeek != null) {
            val month = ((factoryWeek - 1) / 4 + 1).coerceAtMost(12)
            sb.append("-").append(month).append(" (第${factoryWeek}周)")
        } else if (halfYear != null) {
            sb.append(" ").append(halfYear)
        }

        return sb.toString()
    }
}