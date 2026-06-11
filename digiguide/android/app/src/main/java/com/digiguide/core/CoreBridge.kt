package com.digiguide.core

import com.digiguide.model.BatteryHealthResult
import com.digiguide.model.BatteryRawData
import com.digiguide.model.SNDecodeResult
import com.digiguide.model.SNDecodeStatus
import com.digiguide.model.Brand
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
        if (NativeLib.isNativeLoaded()) {
            return nativeDecodeSN(sn)
        }
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

    // ========== Kotlin完整实现 ==========

    private fun kotlinDecodeSN(sn: String): SNDecodeResult {
        return try {
            val brand = identifyBrand(sn)
            kotlinDecodeSN(sn, brand)
        } catch (e: Exception) {
            SNDecodeResult(
                brand = Brand.UNKNOWN,
                rawSn = sn,
                status = SNDecodeStatus.FAILED,
                errorMessage = "解码异常: ${e.message}"
            )
        }
    }

    private fun kotlinDecodeSN(sn: String, brand: Brand): SNDecodeResult {
        val result = SNDecodeResult(
            brand = brand,
            rawSn = sn,
            status = SNDecodeStatus.PARTIAL
        )

        try {
            when (brand) {
                Brand.APPLE, Brand.APPLE_MAC -> decodeAppleSN(sn, result)
                Brand.SAMSUNG -> decodeSamsungSN(sn, result)
                Brand.HUAWEI -> decodeHuaweiSN(sn, result)
                Brand.HONOR -> decodeHuaweiSN(sn, result.apply { brand = Brand.HONOR })
                Brand.XIAOMI -> decodeXiaomiSN(sn, result)
                Brand.OPPO -> decodeOPPOSN(sn, result)
                Brand.VIVO -> decodeVivoSN(sn, result)
                Brand.LENOVO -> decodeLenovoSN(sn, result)
                Brand.HP -> decodeHPSN(sn, result)
                Brand.ASUS -> decodeASUSSN(sn, result)
                Brand.DELL -> decodeDellSN(sn, result)
                Brand.UNKNOWN -> {
                    result.status = SNDecodeStatus.FAILED
                    result.errorMessage = "无法识别品牌，请手动选择"
                }
            }
        } catch (e: Exception) {
            result.status = SNDecodeStatus.FAILED
            result.errorMessage = "解码异常: ${e.message}"
        }

        return result
    }

    // ========== 各品牌解码实现 ==========

    private fun decodeAppleSN(sn: String, result: SNDecodeResult) {
        if (sn.length != 12) {
            result.status = SNDecodeStatus.FAILED
            result.errorMessage = "Apple SN应为12位"
            return
        }

        val yearMap = mapOf(
            'C' to 2010, 'D' to 2010, 'F' to 2011, 'G' to 2011,
            'H' to 2012, 'J' to 2012, 'K' to 2013, 'L' to 2013,
            'M' to 2014, 'N' to 2014, 'P' to 2015, 'Q' to 2015,
            'R' to 2016, 'S' to 2016, 'T' to 2017, 'V' to 2017,
            'W' to 2018, 'X' to 2018, 'Y' to 2019, 'Z' to 2019,
            '0' to 2020, '1' to 2020, '2' to 2021, '3' to 2021,
            '4' to 2022, '5' to 2022, '6' to 2023, '7' to 2023,
            '8' to 2024, '9' to 2024, 'A' to 2025, 'B' to 2025,
            'C' to 2026, 'D' to 2026
        )

        val halfYearMap = mapOf(
            'C' to "上半年", 'D' to "下半年", 'F' to "上半年", 'G' to "下半年",
            'H' to "上半年", 'J' to "下半年", 'K' to "上半年", 'L' to "下半年",
            'M' to "上半年", 'N' to "下半年", 'P' to "上半年", 'Q' to "下半年",
            'R' to "上半年", 'S' to "下半年", 'T' to "上半年", 'V' to "下半年",
            'W' to "上半年", 'X' to "下半年", 'Y' to "上半年", 'Z' to "下半年",
            '0' to "上半年", '1' to "下半年", '2' to "上半年", '3' to "下半年",
            '4' to "上半年", '5' to "下半年", '6' to "上半年", '7' to "下半年",
            '8' to "上半年", '9' to "下半年", 'A' to "上半年", 'B' to "下半年"
        )

        val weekMap = mapOf(
            '1' to 1, '2' to 2, '3' to 3, '4' to 4, '5' to 5, '6' to 6, '7' to 7, '8' to 8, '9' to 9,
            'C' to 10, 'D' to 11, 'F' to 12, 'G' to 13, 'H' to 14, 'J' to 15, 'K' to 16,
            'L' to 17, 'M' to 18, 'N' to 19, 'P' to 20, 'Q' to 21, 'R' to 22, 'S' to 23,
            'T' to 24, 'V' to 25, 'W' to 26, 'X' to 27, 'Y' to 28
        )

        val yearChar = sn[3]
        val weekChar = sn[4]

        result.factoryYear = yearMap[yearChar]
        result.halfYear = halfYearMap[yearChar]
        result.factoryWeek = weekMap[weekChar]

        if (result.factoryWeek != null) {
            result.factoryMonth = ((result.factoryWeek!! - 1) / 4 + 1).coerceAtMost(12)
        }

        if (result.factoryYear != null) {
            result.status = SNDecodeStatus.SUCCESS
        } else {
            result.status = SNDecodeStatus.FAILED
            result.errorMessage = "无法识别Apple年份编码"
        }
    }

    private fun decodeSamsungSN(sn: String, result: SNDecodeResult) {
        if (sn.length < 10) {
            result.status = SNDecodeStatus.FAILED
            result.errorMessage = "Samsung SN长度不足"
            return
        }

        val yearMap = mapOf(
            'R' to 2023, 'S' to 2024, 'T' to 2025, 'U' to 2026, 'V' to 2027, 'W' to 2028
        )

        val monthMap = mapOf(
            '1' to 1, '2' to 2, '3' to 3, '4' to 4, '5' to 5, '6' to 6, '7' to 7, '8' to 8, '9' to 9,
            'A' to 10, 'B' to 11, 'C' to 12
        )

        val yearPos = sn.length - 7
        val monthPos = sn.length - 6

        if (yearPos >= 0 && yearPos < sn.length) {
            result.factoryYear = yearMap[sn[yearPos]]
        }

        if (monthPos >= 0 && monthPos < sn.length) {
            result.factoryMonth = monthMap[sn[monthPos]]
        }

        if (result.factoryYear != null) {
            result.status = SNDecodeStatus.SUCCESS
        } else {
            result.status = SNDecodeStatus.PARTIAL
            result.errorMessage = "Samsung SN年份编码不在支持范围"
        }
    }

    private fun decodeHuaweiSN(sn: String, result: SNDecodeResult) {
        if (sn.length < 9) {
            result.status = SNDecodeStatus.FAILED
            result.errorMessage = "Huawei SN长度不足"
            return
        }

        try {
            val yearPart = sn.substring(5, 7)
            val yearSuffix = yearPart.toIntOrNull()
            if (yearSuffix != null && yearSuffix >= 20 && yearSuffix <= 26) {
                result.factoryYear = 2000 + yearSuffix
            }

            val weekPart = sn.substring(7, 9)
            val week = weekPart.toIntOrNull()
            if (week != null && week >= 1 && week <= 52) {
                result.factoryWeek = week
                result.factoryMonth = ((week - 1) / 4 + 1).coerceAtMost(12)
            }

            if (result.factoryYear != null) {
                result.status = SNDecodeStatus.SUCCESS
            } else {
                result.status = SNDecodeStatus.PARTIAL
            }
        } catch (e: Exception) {
            result.status = SNDecodeStatus.FAILED
            result.errorMessage = "Huawei SN格式解析错误"
        }
    }

    private fun decodeXiaomiSN(sn: String, result: SNDecodeResult) {
        // IMEI格式无法直接解码
        if (sn.length == 15 && sn.all { it.isDigit() }) {
            result.status = SNDecodeStatus.PARTIAL
            result.errorMessage = "IMEI格式需通过官方API查询保修信息"
            return
        }

        // 尝试解析自定义格式
        if (sn.length >= 4) {
            try {
                val yearPart = sn.substring(2, 4)
                val yearSuffix = yearPart.toIntOrNull()
                if (yearSuffix != null && yearSuffix >= 20 && yearSuffix <= 26) {
                    result.factoryYear = 2000 + yearSuffix
                    result.status = SNDecodeStatus.PARTIAL
                    result.errorMessage = "小米SN格式多样，结果仅供参考"
                    return
                }
            } catch (e: Exception) {
                // 继续尝试其他格式
            }
        }

        result.status = SNDecodeStatus.PARTIAL
        result.errorMessage = "无法识别的小米SN格式，建议通过官方渠道查询"
    }

    private fun decodeOPPOSN(sn: String, result: SNDecodeResult) {
        if (sn.length < 10) {
            result.status = SNDecodeStatus.FAILED
            result.errorMessage = "OPPO SN长度不足"
            return
        }

        // OPPO SN格式复杂，需要更多样本分析
        result.status = SNDecodeStatus.PARTIAL
        result.errorMessage = "OPPO SN编码规则复杂，建议通过官方渠道查询"
    }

    private fun decodeVivoSN(sn: String, result: SNDecodeResult) {
        if (sn.length < 8) {
            result.status = SNDecodeStatus.FAILED
            result.errorMessage = "vivo SN长度不足"
            return
        }

        try {
            val yearPart = sn.substring(4, 6)
            val yearSuffix = yearPart.toIntOrNull()
            if (yearSuffix != null && yearSuffix >= 20 && yearSuffix <= 26) {
                result.factoryYear = 2000 + yearSuffix
            }

            val weekOrMonthPart = sn.substring(6, 8)
            val weekOrMonth = weekOrMonthPart.toIntOrNull()
            if (weekOrMonth != null) {
                if (weekOrMonth >= 1 && weekOrMonth <= 12) {
                    result.factoryMonth = weekOrMonth
                } else if (weekOrMonth >= 1 && weekOrMonth <= 52) {
                    result.factoryWeek = weekOrMonth
                    result.factoryMonth = ((weekOrMonth - 1) / 4 + 1).coerceAtMost(12)
                }
            }

            result.status = SNDecodeStatus.PARTIAL
            result.errorMessage = "vivo SN格式多样，结果仅供参考"
        } catch (e: Exception) {
            result.status = SNDecodeStatus.FAILED
            result.errorMessage = "vivo SN格式解析错误"
        }
    }

    private fun decodeLenovoSN(sn: String, result: SNDecodeResult) {
        if (sn.length < 10) {
            result.status = SNDecodeStatus.FAILED
            result.errorMessage = "Lenovo SN长度不足"
            return
        }

        // ThinkPad格式：前4位机型，第5位年份
        if (sn.length >= 5) {
            val yearChar = sn[4]
            val yearMap = mapOf(
                'A' to 2010, 'B' to 2011, 'C' to 2012, 'D' to 2013,
                'E' to 2014, 'F' to 2015, 'G' to 2016, 'H' to 2017,
                'J' to 2018, 'K' to 2019, 'L' to 2020, 'M' to 2021,
                'N' to 2022, 'P' to 2023, 'Q' to 2024, 'R' to 2025
            )
            result.factoryYear = yearMap[yearChar]
        }

        result.status = SNDecodeStatus.PARTIAL
        result.errorMessage = "Lenovo SN需通过官方保修查询验证"
    }

    private fun decodeHPSN(sn: String, result: SNDecodeResult) {
        if (sn.length < 10) {
            result.status = SNDecodeStatus.FAILED
            result.errorMessage = "HP SN长度不足"
            return
        }

        // HP SN格式：第3-4位含年份和地区信息
        result.status = SNDecodeStatus.PARTIAL
        result.errorMessage = "HP SN需通过官方保修查询验证"
    }

    private fun decodeASUSSN(sn: String, result: SNDecodeResult) {
        if (sn.length < 3) {
            result.status = SNDecodeStatus.FAILED
            result.errorMessage = "ASUS SN长度不足"
            return
        }

        // ASUS SN格式：第2位年份代码，第3位月份代码
        val yearMap = mapOf(
            'A' to 2010, 'B' to 2011, 'C' to 2012, 'D' to 2013,
            'E' to 2014, 'F' to 2015, 'G' to 2016, 'H' to 2017,
            'J' to 2018, 'K' to 2019, 'L' to 2020, 'M' to 2021,
            'N' to 2022, 'P' to 2023, 'Q' to 2024, 'R' to 2025
        )

        val monthMap = mapOf(
            '1' to 1, '2' to 2, '3' to 3, '4' to 4, '5' to 5, '6' to 6,
            '7' to 7, '8' to 8, '9' to 9, 'A' to 10, 'B' to 11, 'C' to 12
        )

        result.factoryYear = yearMap[sn[1]]
        result.factoryMonth = monthMap[sn[2]]

        result.status = SNDecodeStatus.PARTIAL
        result.errorMessage = "ASUS SN结果仅供参考，建议通过官方验证"
    }

    private fun decodeDellSN(sn: String, result: SNDecodeResult) {
        if (sn.length < 5 || sn.length > 7) {
            result.status = SNDecodeStatus.FAILED
            result.errorMessage = "Dell服务标签应为5-7位"
            return
        }

        // Dell服务标签无法本地解码
        result.status = SNDecodeStatus.PARTIAL
        result.errorMessage = "Dell服务标签需通过官方API查询"
    }

    // ========== 品牌识别 ==========

    private fun identifyBrand(sn: String): Brand {
        val cleanSn = sn.uppercase()

        // Apple: 12位，第4位是年份编码
        if (cleanSn.length == 12) {
            val yearChars = setOf('C', 'D', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N',
                                   'P', 'Q', 'R', 'S', 'T', 'V', 'W', 'X', 'Y', 'Z',
                                   '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B')
            if (cleanSn[3] in yearChars) {
                return Brand.APPLE
            }
        }

        // Samsung: 倒数第7位是年份编码
        if (cleanSn.length >= 10) {
            val yearPos = cleanSn.length - 7
            val yearChars = setOf('R', 'S', 'T', 'U', 'V', 'W')
            if (yearPos >= 0 && cleanSn[yearPos] in yearChars) {
                return Brand.SAMSUNG
            }
        }

        // Huawei/Honor: 第6-7位是年份后两位
        if (cleanSn.length >= 9) {
            try {
                val yearPart = cleanSn.substring(5, 7)
                val yearSuffix = yearPart.toIntOrNull()
                if (yearSuffix != null && yearSuffix >= 20 && yearSuffix <= 26) {
                    return Brand.HUAWEI
                }
            } catch (e: Exception) {
                // 继续尝试其他识别
            }
        }

        // Xiaomi: 15位纯数字IMEI
        if (cleanSn.length == 15 && cleanSn.all { it.isDigit() }) {
            return Brand.XIAOMI
        }

        // OPPO: 通常以特定前缀开始
        if (cleanSn.startsWith("OPPO") || cleanSn.length == 10 && cleanSn[0].isDigit()) {
            return Brand.OPPO
        }

        // vivo: 通常以特定前缀开始
        if (cleanSn.startsWith("VIVO") || cleanSn.length >= 10) {
            return Brand.VIVO
        }

        // Lenovo: ThinkPad格式
        if (cleanSn.length >= 10 && cleanSn.startsWith("PF") || cleanSn.startsWith("MP")) {
            return Brand.LENOVO
        }

        // Dell: 5-7位服务标签
        if (cleanSn.length >= 5 && cleanSn.length <= 7) {
            return Brand.DELL
        }

        return Brand.UNKNOWN
    }

    // ========== 格式验证 ==========

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

    // ========== 格式提示 ==========

    private fun kotlinGetFormatHint(brand: Brand): String {
        return when (brand) {
            Brand.APPLE -> "Apple SN: 12位，第4位=半年代码，第5位=周次（如C3LXK2XXXXX）"
            Brand.SAMSUNG -> "Samsung SN: 倒数第7位=年份（R=2023,S=2024...），倒数第6位=月份"
            Brand.HUAWEI -> "Huawei SN: 第6-7位=年份后两位（如23=2023），第8-9位=周次"
            Brand.HONOR -> "Honor SN: 与华为类似，第6-7位=年份，第8-9位=周次"
            Brand.XIAOMI -> "Xiaomi SN: IMEI格式（15位数字）或自定义格式"
            Brand.OPPO -> "OPPO SN: 第4-5位含年份+月份编码，格式多变"
            Brand.VIVO -> "vivo SN: 第5-6位=年份，第7-8位=周次或月份"
            Brand.LENOVO -> "Lenovo SN: ThinkPad格式，前4位机型，第5位年份"
            Brand.HP -> "HP SN: 第3-4位=年份和地区编码"
            Brand.ASUS -> "ASUS SN: 第2位=年份代码，第3位=月份代码"
            Brand.DELL -> "Dell SN: 服务标签5-7位字母数字组合"
            Brand.APPLE_MAC -> "Apple Mac SN: 与iPhone类似，12位格式"
            Brand.UNKNOWN -> "未知品牌，请手动选择品牌后查询"
        }
    }

    // ========== Bugreport解析 ==========

    private fun kotlinParseBugreport(text: String): BatteryRawData {
        val data = BatteryRawData()

        try {
            // 提取品牌
            Regex("""ro\.product\.brand=\s*([A-Za-z0-9_\- ]+)""")
                .find(text)?.let { data.brand = it.groupValues[1].trim() }

            // 提取型号
            Regex("""ro\.product\.model=\s*([A-Za-z0-9_\- ]+)""")
                .find(text)?.let { data.model = it.groupValues[1].trim() }

            // 提取制造商
            Regex("""ro\.product\.manufacturer=\s*([A-Za-z0-9_\- ]+)""")
                .find(text)?.let { data.manufacturer = it.groupValues[1].trim() }

        // 提取设计容量（多模式）
        val designPatterns = listOf(
            Regex("""DesignCapacity:\s*(\d+)"""),
            Regex("""design_capacity:\s*(\d+)"""),
            Regex("""battery_design_capacity:\s*(\d+)"""),
            Regex("""额定容量[:：]\s*(\d+)\s*mAh""")
        )
        for (pattern in designPatterns) {
            pattern.find(text)?.let {
                data.designCapacityMah = it.groupValues[1].toIntOrNull()
                break
            }
        }

        // 提取当前容量（多模式，按优先级）
        val capacityPatterns = listOf(
            Regex("""Min learned battery capacity:\s*(\d+)\s*mAh"""),
            Regex("""full charge capacity:\s*(\d+)\s*mAh"""),
            Regex("""learned capacity:\s*(\d+)\s*mAh"""),
            Regex("""CurrentCapacity:\s*(\d+)\s*mAh"""),
            Regex("""BatteryCapacity:\s*(\d+)\s*mAh"""),
            Regex("""last_full_charge_capacity:\s*(\d+)\s*mAh""")
        )
        for (pattern in capacityPatterns) {
            pattern.find(text)?.let {
                data.currentCapacityMah = it.groupValues[1].toIntOrNull()
                break
            }
        }

        // 提取循环次数（多模式）
        val cyclePatterns = listOf(
            Regex("""battery cycle count:\s*(\d+)"""),
            Regex("""cycle count:\s*(\d+)"""),
            Regex("""CycleCount:\s*(\d+)"""),
            Regex("""BatteryCycleCount:\s*(\d+)"""),
            Regex("""charge_cycle_count:\s*(\d+)"""),
            Regex("""循环次数[:：]\s*(\d+)"""),
            Regex("""充电循环[:：]\s*(\d+)""")
        )
        for (pattern in cyclePatterns) {
            pattern.find(text)?.let {
                val cycles = it.groupValues[1].toIntOrNull()
                if (cycles != null && cycles >= 0 && cycles <= 2000) {
                    data.cycleCount = cycles
                    break
                }
            }
        }

        // 提取制造日期（多格式）
        val datePatterns = listOf(
            Regex("""manufacturing_date:\s*(\d{4})-(\d{2})-(\d{2})"""),
            Regex("""mfg_date:\s*(\d{4})-(\d{2})-(\d{2})"""),
            Regex("""battery_produce_date:\s*(\d{4})-(\d{2})-(\d{2})"""),
            Regex("""生产日期[:：]\s*(\d{4})[年/-](\d{1,2})[月/-](\d{1,2})"""),
            Regex("""Battery\s+MFG\s+Date:\s*(\d{4})[.-](\d{2})[.-](\d{2})""")
        )
        for (pattern in datePatterns) {
            pattern.find(text)?.let { match ->
                val year = match.groupValues[1].toIntOrNull()
                val month = match.groupValues[2].toIntOrNull()
                val day = match.groupValues[3].toIntOrNull()
                if (year != null && month != null && day != null) {
                    if (year >= 2000 && year <= 2030 && month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                        data.manufacturingDate = "$year-$month-$day"
                        break
                    }
                }
            }
        }

        // 提取温度（多模式）
        val tempPatterns = listOf(
            Regex("""battery temperature:\s*(\d+\.?\d*)\s*°?C"""),
            Regex("""BatteryTemp:\s*(\d+\.?\d*)"""),
            Regex("""battery_temp:\s*(\d+\.?\d*)"""),
            Regex("""电池温度[:：]\s*(\d+\.?\d*)\s*°?C""")
        )
        for (pattern in tempPatterns) {
            pattern.find(text)?.let {
                val temp = it.groupValues[1].toFloatOrNull()
                if (temp != null && temp >= -20f && temp <= 60f) {
                    data.temperatureCelsius = temp
                    break
                }
            }
        }

        // 提取充电计数
        Regex("""charge_count:\s*(\d+)""")
            .find(text)?.let { data.chargeCount = it.groupValues[1].toIntOrNull() }

        // 提取亮屏时间
        Regex("""Screen on time:\s*(\d+\.?\d*)\s*h""")
            .find(text)?.let { data.screenOnTimeHours = it.groupValues[1].toFloatOrNull()?.toInt() }

        // 提取电压
        Regex("""battery voltage:\s*(\d+\.?\d*)\s*mV""")
            .find(text)?.let { data.lastVoltageMv = it.groupValues[1].toFloatOrNull() }

        // 提取电流
        Regex("""battery current:\s*(-?\d+\.?\d*)\s*mA""")
            .find(text)?.let { data.lastCurrentMa = it.groupValues[1].toFloatOrNull() }
        } catch (e: Exception) {
            // 解析失败，返回空数据
        }

        return data
    }

    // ========== 健康度计算 ==========

    private fun kotlinCalculateHealth(rawData: BatteryRawData): BatteryHealthResult {
        val result = BatteryHealthResult()
        var totalWeight = 0f
        var score = 0f

        try {
            // 容量保持率 (35%权重)
            if (rawData.currentCapacityMah != null && rawData.designCapacityMah != null) {
                val retention = rawData.currentCapacityMah.toFloat() / rawData.designCapacityMah.toFloat()
                result.capacityRetention = retention.coerceIn(0f, 1.5f)  // 新电池可能略高于设计容量
                score += result.capacityRetention!! * 0.35f
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

            // 循环次数专项分析
            result.cycleCount = cycles
            val ratedCycles = 500
            result.cyclePercentUsed = (cycles.toFloat() / ratedCycles) * 100f
            result.cycleGrade = when {
                cycles <= 100 -> "极佳"
                cycles <= 200 -> "良好"
                cycles <= 350 -> "一般"
                cycles <= 450 -> "警告"
                else -> "危险"
            }
            result.estimatedRemainingCycles = (ratedCycles - cycles).coerceAtLeast(0)
        }

        // 温度老化 (10%权重)
        if (rawData.temperatureCelsius != null) {
            val temp = rawData.temperatureCelsius!!
            val thermalAging = when {
                temp <= 25f -> 1.0f
                temp <= 35f -> 1.0f - (temp - 25f) / 50f
                else -> 1.0f - (temp - 25f) / 25f
            }
            result.thermalAging = thermalAging.coerceIn(0f, 1f)
            score += thermalAging * 0.10f
            totalWeight += 0.10f
        }

        // 内阻估算（从电压电流数据）
        if (rawData.lastVoltageMv != null && rawData.lastCurrentMa != null) {
            val voltage = rawData.lastVoltageMv!!
            val current = rawData.lastCurrentMa!!
            if (current != 0f) {
                // 简化内阻估算：R = ΔV / ΔI
                val resistance = kotlin.math.abs(voltage / current) * 1000  // mΩ
                if (resistance >= 50f && resistance <= 500f) {
                    result.estimatedResistanceMohm = resistance
                    // 内阻增长因子（假设基准内阻100mΩ）
                    val resistanceGrowth = 1.0f - (resistance - 100f) / 400f
                    result.resistanceGrowth = resistanceGrowth.coerceIn(0f, 1f)
                    score += resistanceGrowth.coerceIn(0f, 1f) * 0.15f
                    totalWeight += 0.15f
                }
            }
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

        // 生成诊断文字
        result.diagnosisText = generateDiagnosisText(rawData, result)

        // 生成建议
        result.suggestions = generateSuggestions(rawData, result)

        // 设置置信度
        val factorCount = result.getAvailableFactorsCount()
        result.confidence = when {
            factorCount >= 4 -> BatteryHealthResult.ConfidenceLevel.HIGH
            factorCount >= 2 -> BatteryHealthResult.ConfidenceLevel.MEDIUM
            factorCount >= 1 -> BatteryHealthResult.ConfidenceLevel.LOW
            else -> BatteryHealthResult.ConfidenceLevel.NONE
        }

        // 预估剩余寿命
        if (result.cycleCount != null && result.cycleCount!! > 0) {
            val remainingCycles = result.estimatedRemainingCycles ?: 0
            result.remainingLifespanMonths = (remainingCycles / 30).coerceAtLeast(0)  // 每月约30次循环
        }
        } catch (e: Exception) {
            // 计算失败，返回默认结果
            result.healthPercentage = 0f
            result.grade = "F"
            result.diagnosisText = "健康度计算失败: ${e.message}"
        }

        return result
    }

    private fun generateDiagnosisText(rawData: BatteryRawData, result: BatteryHealthResult): String {
        val sb = StringBuilder()

        sb.append("电池健康度分析结果：\n\n")

        if (rawData.brand != null) {
            sb.append("设备品牌：${rawData.brand}\n")
        }
        if (rawData.model != null) {
            sb.append("设备型号：${rawData.model}\n")
        }

        sb.append("\n核心指标：\n")

        if (result.capacityRetention != null) {
            val retentionPercent = (result.capacityRetention!! * 100).toInt()
            sb.append("容量保持率：${retentionPercent}%\n")
        }

        if (rawData.cycleCount != null) {
            sb.append("循环次数：${rawData.cycleCount}次\n")
            sb.append("循环状态：${result.cycleGrade}\n")
        }

        if (rawData.temperatureCelsius != null) {
            sb.append("当前温度：${rawData.temperatureCelsius}°C\n")
        }

        sb.append("\n综合健康度：${result.healthPercentage.toInt()}%（${result.grade}）\n")

        sb.append("\n分析置信度：${result.confidence.name}（${result.getAvailableFactorsCount()}个因子）\n")

        return sb.toString()
    }

    private fun generateSuggestions(rawData: BatteryRawData, result: BatteryHealthResult): MutableList<String> {
        val suggestions = mutableListOf<String>()

        if (result.capacityRetention != null && result.capacityRetention!! < 0.8f) {
            suggestions.add("电池容量明显衰减（${(result.capacityRetention!! * 100).toInt()}%），建议考虑更换电池")
        } else if (result.capacityRetention != null && result.capacityRetention!! < 0.9f) {
            suggestions.add("电池容量有所衰减，建议减少深度放电，保持电量在20%-80%区间")
        }

        if (rawData.cycleCount != null) {
            if (rawData.cycleCount!! > 400) {
                suggestions.add("循环次数较高（${rawData.cycleCount}次），电池已接近设计寿命，建议关注电池状态")
            } else if (rawData.cycleCount!! > 300) {
                suggestions.add("循环次数较多（${rawData.cycleCount}次），建议优化充电习惯以延长寿命")
            }
        }

        if (rawData.temperatureCelsius != null && rawData.temperatureCelsius!! > 40f) {
            suggestions.add("电池温度偏高（${rawData.temperatureCelsius}°C），建议避免高温环境使用和充电")
        }

        if (result.estimatedResistanceMohm != null && result.estimatedResistanceMohm!! > 200f) {
            suggestions.add("电池内阻较高（${result.estimatedResistanceMohm!!.toInt()}mΩ），可能影响充电效率和续航")
        }

        if (suggestions.isEmpty()) {
            suggestions.add("电池状态良好（${result.grade}），继续保持良好使用习惯")
            suggestions.add("建议保持电量在20%-80%区间，避免深度放电")
            suggestions.add("建议使用原装充电器，避免高温环境充电")
        }

        return suggestions
    }
}