package com.digiguide.core

import com.digiguide.model.BatteryHealthResult
import com.digiguide.model.BatteryRawData
import com.digiguide.model.SNDecodeResult
import com.digiguide.model.SNDecodeStatus
import com.digiguide.model.Brand

/**
 * C++ Core引擎的JNI桥接层
 * 提供SN解码和电池健康度计算的接口
 */
object CoreBridge {

    // ========== SN解码接口 ==========

    /**
     * 自动识别品牌并解码SN
     */
    fun decodeSN(sn: String): SNDecodeResult {
        // 如果Native库已加载，使用JNI调用
        if (NativeLib.isNativeLoaded()) {
            return nativeDecodeSN(sn)
        }

        // 否则使用Kotlin实现
        return kotlinDecodeSN(sn)
    }

    /**
     * 指定品牌解码SN
     */
    fun decodeSN(sn: String, brand: Brand): SNDecodeResult {
        if (NativeLib.isNativeLoaded()) {
            return nativeDecodeSNWithBrand(sn, brand.ordinal)
        }
        return kotlinDecodeSN(sn, brand)
    }

    /**
     * 验证SN格式
     */
    fun validateSNFormat(sn: String, brand: Brand): Boolean {
        if (NativeLib.isNativeLoaded()) {
            return nativeValidateFormat(sn, brand.ordinal)
        }
        return kotlinValidateFormat(sn, brand)
    }

    /**
     * 获取品牌SN格式说明
     */
    fun getFormatHint(brand: Brand): String {
        if (NativeLib.isNativeLoaded()) {
            return nativeGetFormatHint(brand.ordinal)
        }
        return kotlinGetFormatHint(brand)
    }

    // ========== Bugreport解析接口 ==========

    /**
     * 从文本解析电池数据
     */
    fun parseBugreport(text: String): BatteryRawData {
        if (NativeLib.isNativeLoaded()) {
            return nativeParseBugreport(text)
        }
        return kotlinParseBugreport(text)
    }

    // ========== 健康度计算接口 ==========

    /**
     * 计算电池健康度
     */
    fun calculateBatteryHealth(rawData: BatteryRawData): BatteryHealthResult {
        if (NativeLib.isNativeLoaded()) {
            return nativeCalculateHealth(rawData)
        }
        return kotlinCalculateHealth(rawData)
    }

    // ========== Native方法声明 ==========

    @JvmStatic
    private external fun nativeDecodeSN(sn: String): SNDecodeResult

    @JvmStatic
    private external fun nativeDecodeSNWithBrand(sn: String, brandOrdinal: Int): SNDecodeResult

    @JvmStatic
    private external fun nativeValidateFormat(sn: String, brandOrdinal: Int): Boolean

    @JvmStatic
    private external fun nativeGetFormatHint(brandOrdinal: Int): String

    @JvmStatic
    private external fun nativeParseBugreport(text: String): BatteryRawData

    @JvmStatic
    private external fun nativeCalculateHealth(rawData: BatteryRawData): BatteryHealthResult

    // ========== Kotlin备用实现 ==========

    private fun kotlinDecodeSN(sn: String): SNDecodeResult {
        // 简化的Kotlin实现
        val brand = identifyBrand(sn)
        return kotlinDecodeSN(sn, brand)
    }

    private fun kotlinDecodeSN(sn: String, brand: Brand): SNDecodeResult {
        val result = SNDecodeResult(
            brand = brand,
            rawSn = sn,
            status = SNDecodeStatus.PARTIAL,
            errorMessage = "Kotlin fallback implementation"
        )

        // Apple解码
        if (brand == Brand.APPLE && sn.length == 12) {
            val yearChar = sn[3]
            val yearMap = mapOf(
                'C' to 2010, 'D' to 2010, 'F' to 2011, 'G' to 2011,
                'H' to 2012, 'J' to 2012, 'K' to 2013, 'L' to 2013,
                'M' to 2014, 'N' to 2014, 'P' to 2015, 'Q' to 2015,
                'R' to 2016, 'S' to 2016, 'T' to 2017, 'V' to 2017,
                'W' to 2018, 'X' to 2018, 'Y' to 2019, 'Z' to 2019,
                '0' to 2020, '1' to 2020, '2' to 2021, '3' to 2021,
                '4' to 2022, '5' to 2022, '6' to 2023, '7' to 2023,
                '8' to 2024, '9' to 2024, 'A' to 2025, 'B' to 2025
            )
            result.factoryYear = yearMap[yearChar]
            result.status = SNDecodeStatus.SUCCESS
            result.errorMessage = ""
        }

        return result
    }

    private fun identifyBrand(sn: String): Brand {
        val cleanSn = sn.uppercase()

        // Apple: 12位
        if (cleanSn.length == 12) {
            val yearChars = setOf('C', 'D', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N',
                                   'P', 'Q', 'R', 'S', 'T', 'V', 'W', 'X', 'Y', 'Z',
                                   '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B')
            if (cleanSn[3] in yearChars) {
                return Brand.APPLE
            }
        }

        // Xiaomi: 15位纯数字IMEI
        if (cleanSn.length == 15 && cleanSn.all { it.isDigit() }) {
            return Brand.XIAOMI
        }

        return Brand.UNKNOWN
    }

    private fun kotlinValidateFormat(sn: String, brand: Brand): Boolean {
        return when (brand) {
            Brand.APPLE, Brand.APPLE_MAC -> sn.length == 12
            Brand.SAMSUNG -> sn.length >= 10
            Brand.HUAWEI, Brand.HONOR -> sn.length >= 10
            Brand.XIAOMI -> sn.length == 15 || sn.length >= 8
            Brand.OPPO, Brand.VIVO -> sn.length >= 10
            Brand.LENOVO, Brand.HP, Brand.ASUS -> sn.length >= 8
            Brand.DELL -> sn.length >= 5 && sn.length <= 7
            Brand.UNKNOWN -> false
        }
    }

    private fun kotlinGetFormatHint(brand: Brand): String {
        return when (brand) {
            Brand.APPLE -> "Apple SN: 12位，第4位=半年代码，第5位=周次"
            Brand.SAMSUNG -> "Samsung SN: 倒数第7位=年份，倒数第6位=月份"
            Brand.HUAWEI -> "Huawei SN: 第6-7位=年份后两位，第8-9位=周次"
            Brand.HONOR -> "Honor SN: 与华为类似"
            Brand.XIAOMI -> "Xiaomi SN: IMEI或自定义格式"
            Brand.OPPO -> "OPPO SN: 第4-5位含年份+月份编码"
            Brand.VIVO -> "vivo SN: 第5-6位=年份，第7-8位=周次/月份"
            Brand.LENOVO -> "Lenovo SN: ThinkPad格式"
            Brand.HP -> "HP SN: 第3-4位=年份和地区"
            Brand.ASUS -> "ASUS SN: 第2位=年份代码，第3位=月份代码"
            Brand.DELL -> "Dell SN: 服务标签5-7位"
            Brand.APPLE_MAC -> "Apple Mac SN: 与iPhone类似"
            Brand.UNKNOWN -> "未知品牌SN格式"
        }
    }

    private fun kotlinParseBugreport(text: String): BatteryRawData {
        val data = BatteryRawData()

        // 提取品牌
        val brandPattern = Regex("""ro\.product\.brand=\s*([A-Za-z0-9_\- ]+)""")
        brandPattern.find(text)?.let { data.brand = it.groupValues[1] }

        // 提取型号
        val modelPattern = Regex("""ro\.product\.model=\s*([A-Za-z0-9_\- ]+)""")
        modelPattern.find(text)?.let { data.model = it.groupValues[1] }

        // 提取设计容量
        val designPattern = Regex("""DesignCapacity:\s*(\d+)""")
        designPattern.find(text)?.let { data.designCapacityMah = it.groupValues[1].toIntOrNull() }

        // 提取当前容量
        val capacityPatterns = listOf(
            Regex("""Min learned battery capacity:\s*(\d+)\s*mAh"""),
            Regex("""full charge capacity:\s*(\d+)\s*mAh"""),
            Regex("""learned capacity:\s*(\d+)\s*mAh""")
        )
        for (pattern in capacityPatterns) {
            pattern.find(text)?.let {
                data.currentCapacityMah = it.groupValues[1].toIntOrNull()
                break
            }
        }

        // 提取循环次数
        val cyclePatterns = listOf(
            Regex("""battery cycle count:\s*(\d+)"""),
            Regex("""cycle count:\s*(\d+)"""),
            Regex("""CycleCount:\s*(\d+)""")
        )
        for (pattern in cyclePatterns) {
            pattern.find(text)?.let {
                data.cycleCount = it.groupValues[1].toIntOrNull()
                break
            }
        }

        // 提取温度
        val tempPattern = Regex("""battery temperature:\s*(\d+\.?\d*)\s*°?C""")
        tempPattern.find(text)?.let { data.temperatureCelsius = it.groupValues[1].toFloatOrNull() }

        return data
    }

    private fun kotlinCalculateHealth(rawData: BatteryRawData): BatteryHealthResult {
        val result = BatteryHealthResult()
        var totalWeight = 0f
        var score = 0f

        // 容量保持率 (35%权重)
        if (rawData.currentCapacityMah != null && rawData.designCapacityMah != null) {
            val retention = rawData.currentCapacityMah.toFloat() / rawData.designCapacityMah.toFloat()
            result.capacityRetention = retention
            score += retention * 0.35f
            totalWeight += 0.35f
        }

        // 循环衰减 (30%权重)
        if (rawData.cycleCount != null) {
            val cycles = rawData.cycleCount!!
            val decay = when {
                cycles <= 200 -> 1.0f - (cycles / 100f) * 0.032f
                cycles <= 400 -> 1.0f - 0.064f - ((cycles - 200) / 100f) * 0.04f
                else -> 1.0f - 0.064f - 0.08f - ((cycles - 400) / 100f) * 0.052f
            }
            result.cycleDecay = decay.coerceIn(0f, 1f)
            score += result.cycleDecay!! * 0.30f
            totalWeight += 0.30f
        }

        // 计算综合健康度
        if (totalWeight > 0) {
            result.healthPercentage = (score / totalWeight) * 100f
        }

        // 计算等级
        result.grade = when {
            result.healthPercentage >= 95 -> "A+"
            result.healthPercentage >= 90 -> "A"
            result.healthPercentage >= 80 -> "B"
            result.healthPercentage >= 70 -> "C"
            result.healthPercentage >= 60 -> "D"
            else -> "F"
        }

        // 生成建议
        result.suggestions = mutableListOf()
        if (result.capacityRetention != null && result.capacityRetention!! < 0.8f) {
            result.suggestions.add("电池容量明显衰减，建议考虑更换电池")
        }
        if (rawData.cycleCount != null && rawData.cycleCount!! > 400) {
            result.suggestions.add("循环次数较高，电池已接近设计寿命")
        }
        if (result.suggestions.isEmpty()) {
            result.suggestions.add("电池状态良好，继续保持良好使用习惯")
        }

        return result
    }
}