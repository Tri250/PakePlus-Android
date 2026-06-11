package com.digiguide.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 电池报告记录
 */
@Entity(tableName = "battery_reports")
data class BatteryReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val brand: String?,
    val model: String?,
    val sn: String?,
    val designCapacityMah: Int?,
    val currentCapacityMah: Int?,
    val cycleCount: Int?,
    val manufacturingDate: String?,
    val temperatureCelsius: Float?,
    val healthPercentage: Float,
    val grade: String,
    val capacityRetention: Float?,
    val cycleDecay: Float?,
    val diagnosisText: String?,
    val suggestions: String?,  // JSON格式的建议列表
    val reportTime: Long = System.currentTimeMillis(),
    val rawBugreportPath: String?
) {
    /**
     * 获取健康度描述
     */
    fun getHealthDescription(): String {
        return when (grade) {
            "A+" -> "极佳"
            "A" -> "良好"
            "B" -> "一般"
            "C" -> "较差"
            "D" -> "很差"
            "F" -> "极差"
            else -> "未知"
        }
    }

    /**
     * 获取容量保持率百分比
     */
    fun getCapacityRetentionPercent(): Int? {
        return capacityRetention?.let { (it * 100).toInt() }
    }
}