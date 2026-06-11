package com.digiguide.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 健康度等级颜色定义
 * 用于电池健康度分析和SN解码结果显示
 */
object HealthColors {

    // 健康度等级颜色（A+到F）
    val gradeAPlus = Color(0xFF4CAF50)    // 绿色 - 极佳
    val gradeA = Color(0xFF8BC34A)        // 浅绿 - 良好
    val gradeB = Color(0xFFFFC107)        // 黄色 - 一般
    val gradeC = Color(0xFFFF9800)        // 橙色 - 较差
    val gradeD = Color(0xFFF44336)        // 红色 - 很差
    val gradeF = Color(0xFF9C27B0)        // 紫色 - 极差
    val gradeUnknown = Color(0xFF9E9E9E)  // 灰色 - 未知

    /**
     * 根据健康度百分比获取对应颜色
     */
    fun getColorByPercentage(percentage: Float): Color {
        return when {
            percentage >= 95 -> gradeAPlus
            percentage >= 90 -> gradeA
            percentage >= 80 -> gradeB
            percentage >= 70 -> gradeC
            percentage >= 60 -> gradeD
            else -> gradeF
        }
    }

    /**
     * 根据等级字符串获取对应颜色
     */
    fun getColorByGrade(grade: String): Color {
        return when (grade) {
            "A+" -> gradeAPlus
            "A" -> gradeA
            "B" -> gradeB
            "C" -> gradeC
            "D" -> gradeD
            "F" -> gradeF
            else -> gradeUnknown
        }
    }

    /**
     * 根据循环次数状态获取对应颜色
     */
    fun getCycleGradeColor(cycleGrade: String): Color {
        return when (cycleGrade) {
            "极佳" -> gradeAPlus
            "良好" -> gradeA
            "一般" -> gradeB
            "警告" -> gradeC
            "危险" -> gradeD
            else -> gradeUnknown
        }
    }

    /**
     * 根据循环百分比获取对应颜色
     */
    fun getCyclePercentColor(percentUsed: Float): Color {
        return when {
            percentUsed <= 40 -> gradeAPlus
            percentUsed <= 80 -> gradeB
            else -> gradeD
        }
    }

    /**
     * 根据剩余循环次数获取对应颜色
     */
    fun getRemainingCyclesColor(remaining: Int): Color {
        return when {
            remaining > 200 -> gradeAPlus
            remaining > 50 -> gradeB
            else -> gradeD
        }
    }
}

/**
 * 状态颜色定义
 * 用于成功、警告、错误等状态显示
 */
object StatusColors {

    val success = Color(0xFF4CAF50)
    val warning = Color(0xFFFF9800)
    val error = Color(0xFFF44336)
    val info = Color(0xFF2196F3)

    // 状态容器背景色（浅色版本）
    val successContainer = Color(0xFFE8F5E9)
    val warningContainer = Color(0xFFFFF3E0)
    val errorContainer = Color(0xFFFFEBEE)
    val infoContainer = Color(0xFFE3F2FD)

    // 状态容器文字色
    val onSuccessContainer = Color(0xFF1B5E20)
    val onWarningContainer = Color(0xFFE65100)
    val onErrorContainer = Color(0xFFB71C1C)
    val onInfoContainer = Color(0xFF0D47A1)
}

/**
 * 品牌颜色定义
 * 用于不同品牌设备的区分显示
 */
object BrandColors {

    val apple = Color(0xFF007AFF)
    val samsung = Color(0xFF1428A0)
    val huawei = Color(0xFFCF0A2C)
    val honor = Color(0xFFCF0A2C)
    val xiaomi = Color(0xFFFF6700)
    val oppo = Color(0xFF1D953F)
    val vivo = Color(0xFF415FFF)
    val lenovo = Color(0xFFE2231A)
    val hp = Color(0xFF0096D6)
    val asus = Color(0xFF00539B)
    val dell = Color(0xFF007DB8)
    val unknown = Color(0xFF9E9E9E)
}