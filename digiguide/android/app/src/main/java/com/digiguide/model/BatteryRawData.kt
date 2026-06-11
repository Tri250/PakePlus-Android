package com.digiguide.model

/**
 * 电池原始数据
 */
data class BatteryRawData(
    // 基础信息
    var brand: String? = null,
    var model: String? = null,
    var manufacturer: String? = null,
    var sn: String? = null,

    // 容量数据
    var designCapacityMah: Int? = null,
    var currentCapacityMah: Int? = null,
    var chargeCounterMah: Int? = null,

    // 循环与寿命
    var cycleCount: Int? = null,
    var manufacturingDate: String? = null,

    // 温度
    var temperatureCelsius: Float? = null,

    // 使用统计
    var screenOnTimeHours: Int? = null,
    var chargeCount: Int? = null,

    // 电压/电流数据（用于内阻估算）
    var lastVoltageMv: Float? = null,
    var lastCurrentMa: Float? = null,
    var voltageCurrentPairs: List<Pair<Float, Float>> = emptyList(),

    // 充电行为
    var chargingEvents: List<ChargingEvent> = emptyList(),

    // 应用耗电
    var appPowerUsages: List<AppPowerUsage> = emptyList()
) {
    /**
     * 充电事件
     */
    data class ChargingEvent(
        val timestamp: Long,
        val startLevel: Int,
        val endLevel: Int,
        val durationMinutes: Int,
        val avgPowerW: Float
    )

    /**
     * 应用耗电
     */
    data class AppPowerUsage(
        val packageName: String,
        val displayName: String,
        val powerMah: Float,
        val wakeupCount: Int,
        val isSystem: Boolean
    )

    /**
     * 是否有容量数据
     */
    fun hasCapacityData(): Boolean = designCapacityMah != null || currentCapacityMah != null

    /**
     * 是否有循环数据
     */
    fun hasCycleData(): Boolean = cycleCount != null

    /**
     * 是否有温度数据
     */
    fun hasTemperatureData(): Boolean = temperatureCelsius != null

    /**
     * 获取可用数据数量
     */
    fun getAvailableDataCount(): Int {
        var count = 0
        if (brand != null) count++
        if (model != null) count++
        if (designCapacityMah != null) count++
        if (currentCapacityMah != null) count++
        if (cycleCount != null) count++
        if (manufacturingDate != null) count++
        if (temperatureCelsius != null) count++
        if (screenOnTimeHours != null) count++
        if (chargeCount != null) count++
        if (voltageCurrentPairs.isNotEmpty()) count++
        if (chargingEvents.isNotEmpty()) count++
        if (appPowerUsages.isNotEmpty()) count++
        return count
    }

    /**
     * 获取数据摘要
     */
    fun getSummary(): String {
        val sb = StringBuilder()
        sb.append("品牌: ${brand ?: "未知"}\n")
        sb.append("型号: ${model ?: "未知"}\n")
        sb.append("设计容量: ${designCapacityMah?.let { "$it mAh" } ?: "未知"}\n")
        sb.append("当前容量: ${currentCapacityMah?.let { "$it mAh" } ?: "未知"}\n")
        sb.append("循环次数: ${cycleCount?.let { "$it 次" } ?: "未知"}\n")
        sb.append("温度: ${temperatureCelsius?.let { "$it°C" } ?: "未知"}\n")
        return sb.toString()
    }
}