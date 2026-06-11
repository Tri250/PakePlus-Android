package com.digiguide.model

/**
 * SN解码状态
 */
enum class SNDecodeStatus {
    SUCCESS,    // 完全成功
    PARTIAL,    // 部分成功
    FAILED      // 完全失败
}

/**
 * SN解码结果
 */
data class SNDecodeResult(
    val brand: Brand = Brand.UNKNOWN,
    val rawSn: String = "",
    val factoryYear: Int? = null,
    val factoryMonth: Int? = null,
    val factoryWeek: Int? = null,
    val halfYear: String? = null,
    val status: SNDecodeStatus = SNDecodeStatus.FAILED,
    val errorMessage: String = ""
) {
    /**
     * 获取生产日期估算字符串
     */
    fun getProductionDateEstimate(): String {
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

    /**
     * 获取品牌名称
     */
    fun getBrandName(): String = brand.toChinese()

    /**
     * 是否成功解码
     */
    fun isSuccess(): Boolean = status == SNDecodeStatus.SUCCESS || status == SNDecodeStatus.PARTIAL

    /**
     * 获取保修状态估算
     */
    fun getWarrantyStatus(): String {
        if (factoryYear == null) return "无法估算"

        val currentYear = java.time.LocalDate.now().year
        val currentMonth = java.time.LocalDate.now().monthValue

        val productionMonth = factoryMonth ?: 1
        val warrantyMonths = getWarrantyMonths(brand)

        val totalMonths = (currentYear - factoryYear) * 12 + (currentMonth - productionMonth)

        return when {
            totalMonths < warrantyMonths -> "保修期内（剩余${warrantyMonths - totalMonths}个月）"
            totalMonths < warrantyMonths + 6 -> "保修已过期${totalMonths - warrantyMonths}个月"
            else -> "保修已过期"
        }
    }

    private fun getWarrantyMonths(brand: Brand): Int {
        return when (brand) {
            APPLE, APPLE_MAC, SAMSUNG, HUAWEI, HONOR, XIAOMI, OPPO, VIVO -> 12
            LENOVO, HP, ASUS, DELL -> 12
            UNKNOWN -> 12
        }
    }
}