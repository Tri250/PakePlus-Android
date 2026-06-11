package com.digiguide.model

/**
 * 电池健康度结果
 */
data class BatteryHealthResult(
    var healthPercentage: Float = 0f,
    var grade: String = "F",
    var capacityRetention: Float? = null,
    var cycleDecay: Float? = null,
    var resistanceGrowth: Float? = null,
    var thermalAging: Float? = null,
    var chargingDamage: Float? = null,
    var diagnosisText: String = "",
    var suggestions: List<String> = emptyList(),
    var estimatedResistanceMohm: Float? = null,
    var remainingLifespanMonths: Int? = null,
    var confidence: ConfidenceLevel = ConfidenceLevel.NONE,
    // 循环次数专项分析
    var cycleCount: Int? = null,
    var cycleGrade: String = "未知",
    var cyclePercentUsed: Float = 0f,
    var estimatedRemainingCycles: Int? = null
) {
    /**
     * 置信度级别
     */
    enum class ConfidenceLevel {
        HIGH,    // 高置信度（>=4个因子）
        MEDIUM,  // 中置信度（>=2个因子）
        LOW,     // 低置信度（>=1个因子）
        NONE     // 无置信度（无可用因子）
    }

    /**
     * 获取等级颜色
     */
    fun getGradeColor(): String {
        return when (grade) {
            "A+" -> "#4CAF50"
            "A" -> "#8BC34A"
            "B" -> "#FFC107"
            "C" -> "#FF9800"
            "D" -> "#F44336"
            "F" -> "#9C27B0"
            else -> "#9E9E9E"
        }
    }

    /**
     * 获取等级描述
     */
    fun getGradeDescription(): String {
        return when (grade) {
            "A+" -> "电池状态极佳，几乎无老化"
            "A" -> "电池状态良好，轻微老化"
            "B" -> "电池状态一般，中度老化"
            "C" -> "电池状态较差，明显老化"
            "D" -> "电池状态很差，严重老化"
            "F" -> "电池状态极差，建议更换"
            else -> "未知状态"
        }
    }

    /**
     * 是否需要更换电池
     */
    fun needsReplacement(): Boolean = healthPercentage < 60f

    /**
     * 是否处于警告状态
     */
    fun isWarningState(): Boolean = healthPercentage < 80f

    /**
     * 是否处于良好状态
     */
    fun isGoodState(): Boolean = healthPercentage >= 90f

    /**
     * 获取可用因子数量
     */
    fun getAvailableFactorsCount(): Int {
        var count = 0
        if (capacityRetention != null) count++
        if (cycleDecay != null) count++
        if (resistanceGrowth != null) count++
        if (thermalAging != null) count++
        if (chargingDamage != null) count++
        return count
    }

    /**
     * 获取详细诊断报告
     */
    fun getDetailedReport(): String {
        val sb = StringBuilder()
        sb.append("=== 电池健康度分析报告 ===\n\n")
        sb.append("综合健康度: ${healthPercentage.toInt()}% ($grade)\n")
        sb.append("状态描述: ${getGradeDescription()}\n")
        sb.append("置信度: ${confidence.name} (${getAvailableFactorsCount()}个因子)\n\n")

        sb.append("=== 详细分析 ===\n")
        if (capacityRetention != null) {
            sb.append("容量保持率: ${(capacityRetention!! * 100).toInt()}%\n")
        }
        if (cycleDecay != null) {
            sb.append("循环衰减因子: ${(cycleDecay!! * 100).toInt()}%\n")
        }
        if (resistanceGrowth != null) {
            sb.append("内阻增长因子: ${(resistanceGrowth!! * 100).toInt()}%\n")
        }
        if (thermalAging != null) {
            sb.append("温度老化因子: ${(thermalAging!! * 100).toInt()}%\n")
        }
        if (chargingDamage != null) {
            sb.append("充电损伤因子: ${(chargingDamage!! * 100).toInt()}%\n")
        }

        if (estimatedResistanceMohm != null) {
            sb.append("\n估算内阻: ${estimatedResistanceMohm!!.toInt()}mΩ\n")
        }
        if (remainingLifespanMonths != null) {
            sb.append("预估剩余寿命: ${remainingLifespanMonths!!}个月\n")
        }

        if (suggestions.isNotEmpty()) {
            sb.append("\n=== 使用建议 ===\n")
            for (suggestion in suggestions) {
                sb.append("- $suggestion\n")
            }
        }

        return sb.toString()
    }
}