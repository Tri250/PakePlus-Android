package service

import (
    "digiguide/backend/internal/model"
    "digiguide/backend/internal/client"
    "strings"
    "time"
)

// DecodeSN 解码SN序列号
func DecodeSN(sn string, brand model.Brand) model.SNDecodeResult {
    // 清理SN
    sn = strings.ToUpper(strings.TrimSpace(sn))

    // 如果未指定品牌，自动识别
    if brand == "" || brand == model.BrandUnknown {
        brand = identifyBrand(sn)
    }

    // 根据品牌调用对应解码器
    result := decodeByBrand(sn, brand)

    // 计算保修状态
    result.WarrantyStatus = calculateWarrantyStatus(result)

    return result
}

// ValidateSNFormat 验证SN格式
func ValidateSNFormat(sn string, brand model.Brand) bool {
    sn = strings.TrimSpace(sn)

    switch brand {
    case model.BrandApple, model.BrandAppleMac:
        return len(sn) == 12
    case model.BrandSamsung:
        return len(sn) >= 10
    case model.BrandHuawei, model.BrandHonor:
        return len(sn) >= 10
    case model.BrandXiaomi:
        return len(sn) == 15 || len(sn) >= 8
    case model.BrandOPPO, model.BrandVivo:
        return len(sn) >= 10
    case model.BrandLenovo, model.BrandHP, model.BrandASUS:
        return len(sn) >= 8
    case model.BrandDell:
        return len(sn) >= 5 && len(sn) <= 7
    default:
        return false
    }
}

// GetFormatHint 获取品牌SN格式说明
func GetFormatHint(brand model.Brand) string {
    hints := map[model.Brand]string{
        model.BrandApple:    "Apple SN: 12位，第4位=半年代码，第5位=周次",
        model.BrandSamsung:  "Samsung SN: 倒数第7位=年份，倒数第6位=月份",
        model.BrandHuawei:   "Huawei SN: 第6-7位=年份后两位，第8-9位=周次",
        model.BrandHonor:    "Honor SN: 与华为类似，第6-7位=年份，第8-9位=周次",
        model.BrandXiaomi:   "Xiaomi SN: 多种格式，IMEI或自定义编码",
        model.BrandOPPO:     "OPPO SN: 第4-5位含年份+月份编码",
        model.BrandVivo:     "vivo SN: 第5-6位=年份，第7-8位=周次/月份",
        model.BrandLenovo:   "Lenovo SN: ThinkPad格式，前4位=机型，第5位=年份",
        model.BrandHP:       "HP SN: 第3-4位=年份和地区，后续=周次",
        model.BrandASUS:     "ASUS SN: 第2位=年份代码，第3位=月份代码",
        model.BrandDell:     "Dell SN: 服务标签5-7位，需官方API查询",
        model.BrandAppleMac: "Apple Mac SN: 与iPhone类似，12位格式",
    }

    if hint, ok := hints[brand]; ok {
        return hint
    }
    return "未知品牌SN格式"
}

// GetWarrantyInfo 获取保修信息
func GetWarrantyInfo(sn string) model.WarrantyInfo {
    brand := identifyBrand(sn)
    result := decodeByBrand(sn, brand)

    info := model.WarrantyInfo{
        SN:            sn,
        Brand:         brand,
        WarrantyStatus: calculateWarrantyStatus(result),
    }

    // 如果有生产日期，计算保修结束日期
    if result.FactoryYear != nil {
        year := *result.FactoryYear
        month := 1
        if result.FactoryMonth != nil {
            month = *result.FactoryMonth
        }

        // 默认保修12个月
        warrantyMonths := GetWarrantyMonths(brand)
        endDate := time.Date(year, time.Month(month+warrantyMonths), 1, 0, 0, 0, 0, time.UTC)
        endDateStr := endDate.Format("2006-01-02")
        info.WarrantyEndDate = &endDateStr

        // 计算剩余天数
        now := time.Now()
        if endDate.After(now) {
            remaining := int(endDate.Sub(now).Hours() / 24)
            info.RemainingDays = &remaining
        }
    }

    return info
}

// BatchDecodeSN 批量解码SN
func BatchDecodeSN(snList []string) []model.SNDecodeResult {
    results := make([]model.SNDecodeResult, len(snList))
    for i, sn := range snList {
        results[i] = DecodeSN(sn, model.BrandUnknown)
    }
    return results
}

// GetBrandChinese 获取品牌中文名
func GetBrandChinese(brand model.Brand) string {
    names := map[model.Brand]string{
        model.BrandXiaomi:   "小米",
        model.BrandHuawei:   "华为",
        model.BrandOPPO:     "OPPO",
        model.BrandVivo:     "vivo",
        model.BrandApple:    "苹果",
        model.BrandHonor:    "荣耀",
        model.BrandSamsung:  "三星",
        model.BrandLenovo:   "联想",
        model.BrandHP:       "惠普",
        model.BrandASUS:     "华硕",
        model.BrandDell:     "戴尔",
        model.BrandAppleMac: "苹果电脑",
        model.BrandUnknown:  "未知",
    }

    if name, ok := names[brand]; ok {
        return name
    }
    return "未知"
}

// GetWarrantyMonths 获取保修月数
func GetWarrantyMonths(brand model.Brand) int {
    // 默认12个月保修
    return 12
}

// identifyBrand 自动识别品牌
func identifyBrand(sn string) model.Brand {
    sn = strings.ToUpper(sn)

    // Apple: 12位
    if len(sn) == 12 {
        yearChars := map[byte]bool{
            'C': true, 'D': true, 'F': true, 'G': true, 'H': true, 'J': true,
            'K': true, 'L': true, 'M': true, 'N': true, 'P': true, 'Q': true,
            'R': true, 'S': true, 'T': true, 'V': true, 'W': true, 'X': true,
            'Y': true, 'Z': true, '0': true, '1': true, '2': true, '3': true,
            '4': true, '5': true, '6': true, '7': true, '8': true, '9': true,
            'A': true, 'B': true,
        }
        if yearChars[sn[3]] {
            return model.BrandApple
        }
    }

    // Xiaomi: 15位纯数字IMEI
    if len(sn) == 15 && isAllDigits(sn) {
        return model.BrandXiaomi
    }

    // Samsung: 倒数第7位是年份编码
    if len(sn) >= 10 {
        yearPos := len(sn) - 7
        if yearPos >= 0 && yearPos < len(sn) {
            yearChars := map[byte]bool{'R': true, 'S': true, 'T': true, 'U': true, 'V': true, 'W': true}
            if yearChars[sn[yearPos]] {
                return model.BrandSamsung
            }
        }
    }

    // Huawei/Honor: 第6-7位是年份后两位
    if len(sn) >= 9 {
        yearPart := sn[5:7]
        if yearPart >= "20" && yearPart <= "26" {
            return model.BrandHuawei // 默认华为
        }
    }

    return model.BrandUnknown
}

// decodeByBrand 根据品牌解码
func decodeByBrand(sn string, brand model.Brand) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand: brand,
        RawSN: sn,
        Status: model.StatusFailed,
    }

    switch brand {
    case model.BrandApple, model.BrandAppleMac:
        return decodeAppleSN(sn)
    case model.BrandSamsung:
        return decodeSamsungSN(sn)
    case model.BrandHuawei:
        return decodeHuaweiSN(sn)
    case model.BrandHonor:
        r := decodeHuaweiSN(sn)
        r.Brand = model.BrandHonor
        return r
    case model.BrandXiaomi:
        return decodeXiaomiSN(sn)
    case model.BrandOPPO:
        return client.QueryOPPO(sn)
    case model.BrandVivo:
        return decodeVivoSN(sn)
    case model.BrandLenovo:
        return client.QueryLenovo(sn)
    case model.BrandHP:
        return client.QueryHP(sn)
    case model.BrandASUS:
        return decodeASUSSN(sn)
    case model.BrandDell:
        return client.QueryDell(sn)
    default:
        result.ErrorMessage = "无法识别的品牌"
        return result
    }
}

// decodeAppleSN Apple SN解码
func decodeAppleSN(sn string) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand: model.BrandApple,
        RawSN: sn,
    }

    if len(sn) < 5 {
        result.Status = model.StatusFailed
        result.ErrorMessage = "SN长度不足"
        return result
    }

    // Apple年份编码映射
    yearMap := map[byte]int{
        'C': 2010, 'D': 2010, 'F': 2011, 'G': 2011,
        'H': 2012, 'J': 2012, 'K': 2013, 'L': 2013,
        'M': 2014, 'N': 2014, 'P': 2015, 'Q': 2015,
        'R': 2016, 'S': 2016, 'T': 2017, 'V': 2017,
        'W': 2018, 'X': 2018, 'Y': 2019, 'Z': 2019,
        '0': 2020, '1': 2020, '2': 2021, '3': 2021,
        '4': 2022, '5': 2022, '6': 2023, '7': 2023,
        '8': 2024, '9': 2024, 'A': 2025, 'B': 2025,
    }

    // 半年映射
    halfYearMap := map[byte]string{
        'C': "上半年", 'D': "下半年", 'F': "上半年", 'G': "下半年",
        'H': "上半年", 'J': "下半年", 'K': "上半年", 'L': "下半年",
        'M': "上半年", 'N': "下半年", 'P': "上半年", 'Q': "下半年",
        'R': "上半年", 'S': "下半年", 'T': "上半年", 'V': "下半年",
        'W': "上半年", 'X': "下半年", 'Y': "上半年", 'Z': "下半年",
        '0': "上半年", '1': "下半年", '2': "上半年", '3': "下半年",
        '4': "上半年", '5': "下半年", '6': "上半年", '7': "下半年",
        '8': "上半年", '9': "下半年", 'A': "上半年", 'B': "下半年",
    }

    yearChar := sn[3]
    if year, ok := yearMap[yearChar]; ok {
        result.FactoryYear = &year
        if half, ok := halfYearMap[yearChar]; ok {
            result.HalfYear = &half
        }
        result.Status = model.StatusSuccess
    } else {
        result.Status = model.StatusFailed
        result.ErrorMessage = "无法识别年份编码"
        return result
    }

    // 周次解码（简化）
    weekChar := sn[4]
    if weekChar >= '1' && weekChar <= '9' {
        week := int(weekChar - '0')
        result.FactoryWeek = &week
        month := (week - 1) / 4 + 1
        if month > 12 {
            month = 12
        }
        result.FactoryMonth = &month
    }

    return result
}

// decodeSamsungSN Samsung SN解码
func decodeSamsungSN(sn string) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand: model.BrandSamsung,
        RawSN: sn,
    }

    if len(sn) < 7 {
        result.Status = model.StatusFailed
        result.ErrorMessage = "SN长度不足"
        return result
    }

    // Samsung年份编码
    yearMap := map[byte]int{
        'R': 2023, 'S': 2024, 'T': 2025, 'U': 2026, 'V': 2027, 'W': 2028,
    }

    // 月份编码
    monthMap := map[byte]int{
        '1': 1, '2': 2, '3': 3, '4': 4, '5': 5, '6': 6, '7': 7, '8': 8, '9': 9,
        'A': 10, 'B': 11, 'C': 12,
    }

    yearPos := len(sn) - 7
    monthPos := len(sn) - 6

    if year, ok := yearMap[sn[yearPos]]; ok {
        result.FactoryYear = &year
    }

    if month, ok := monthMap[sn[monthPos]]; ok {
        result.FactoryMonth = &month
    }

    if result.FactoryYear != nil {
        result.Status = model.StatusSuccess
    } else {
        result.Status = model.StatusPartial
        result.ErrorMessage = "无法完全解码"
    }

    return result
}

// decodeHuaweiSN Huawei SN解码
func decodeHuaweiSN(sn string) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand: model.BrandHuawei,
        RawSN: sn,
    }

    if len(sn) < 9 {
        result.Status = model.StatusFailed
        result.ErrorMessage = "SN长度不足"
        return result
    }

    // 提取年份（第6-7位）
    yearPart := sn[5:7]
    yearSuffix := parseInt(yearPart)
    if yearSuffix >= 20 && yearSuffix <= 26 {
        year := 2000 + yearSuffix
        result.FactoryYear = &year
    }

    // 提取周次（第8-9位）
    weekPart := sn[7:9]
    week := parseInt(weekPart)
    if week >= 1 && week <= 52 {
        result.FactoryWeek = &week
        month := (week - 1) / 4 + 1
        if month > 12 {
            month = 12
        }
        result.FactoryMonth = &month
    }

    if result.FactoryYear != nil {
        result.Status = model.StatusSuccess
    } else {
        result.Status = model.StatusFailed
        result.ErrorMessage = "无法解码"
    }

    return result
}

// decodeXiaomiSN Xiaomi SN解码
func decodeXiaomiSN(sn string) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand: model.BrandXiaomi,
        RawSN: sn,
    }

    // IMEI格式无法直接解码
    if len(sn) == 15 && isAllDigits(sn) {
        result.Status = model.StatusPartial
        result.ErrorMessage = "IMEI格式需要官方API查询"
        return result
    }

    // 尝试解析自定义格式
    if len(sn) >= 4 {
        yearPart := sn[2:4]
        yearSuffix := parseInt(yearPart)
        if yearSuffix >= 20 && yearSuffix <= 26 {
            year := 2000 + yearSuffix
            result.FactoryYear = &year
            result.Status = model.StatusPartial
            result.ErrorMessage = "小米SN格式多样，结果仅供参考"
            return result
        }
    }

    result.Status = model.StatusFailed
    result.ErrorMessage = "无法识别的小米SN格式"
    return result
}

// decodeVivoSN vivo SN解码
func decodeVivoSN(sn string) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand: model.BrandVivo,
        RawSN: sn,
    }

    if len(sn) < 8 {
        result.Status = model.StatusFailed
        result.ErrorMessage = "SN长度不足"
        return result
    }

    // 提取年份（第5-6位）
    yearPart := sn[4:6]
    yearSuffix := parseInt(yearPart)
    if yearSuffix >= 20 && yearSuffix <= 26 {
        year := 2000 + yearSuffix
        result.FactoryYear = &year
    }

    // 提取周次/月份（第7-8位）
    weekPart := sn[6:8]
    weekOrMonth := parseInt(weekPart)
    if weekOrMonth >= 1 && weekOrMonth <= 12 {
        result.FactoryMonth = &weekOrMonth
    } else if weekOrMonth >= 1 && weekOrMonth <= 52 {
        result.FactoryWeek = &weekOrMonth
        month := (weekOrMonth - 1) / 4 + 1
        if month > 12 {
            month = 12
        }
        result.FactoryMonth = &month
    }

    result.Status = model.StatusPartial
    result.ErrorMessage = "vivo SN格式多样，结果仅供参考"
    return result
}

// decodeASUSSN ASUS SN解码
func decodeASUSSN(sn string) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand: model.BrandASUS,
        RawSN: sn,
    }

    if len(sn) < 3 {
        result.Status = model.StatusFailed
        result.ErrorMessage = "SN长度不足"
        return result
    }

    result.Status = model.StatusPartial
    result.ErrorMessage = "ASUS SN需要官方API查询"
    return result
}

// calculateWarrantyStatus 计算保修状态
func calculateWarrantyStatus(result model.SNDecodeResult) string {
    if result.FactoryYear == nil {
        return "无法估算"
    }

    now := time.Now()
    year := *result.FactoryYear
    month := 1
    if result.FactoryMonth != nil {
        month = *result.FactoryMonth
    }

    warrantyMonths := GetWarrantyMonths(result.Brand)
    totalMonths := (now.Year() - year) * 12 + (int(now.Month()) - month)

    if totalMonths < warrantyMonths {
        remaining := warrantyMonths - totalMonths
        return "保修期内（剩余" + intToStr(remaining) + "个月）"
    } else if totalMonths < warrantyMonths + 6 {
        expired := totalMonths - warrantyMonths
        return "保修已过期" + intToStr(expired) + "个月"
    }

    return "保修已过期"
}

// 辅助函数
func isAllDigits(s string) bool {
    for _, c := range s {
        if c < '0' || c > '9' {
            return false
        }
    }
    return true
}

func parseInt(s string) int {
    result := 0
    for _, c := range s {
        if c >= '0' && c <= '9' {
            result = result * 10 + int(c - '0')
        }
    }
    return result
}

func intToStr(n int) string {
    if n == 0 {
        return "0"
    }
    result := ""
    for n > 0 {
        result = string(byte('0'+n%10)) + result
        n /= 10
    }
    return result
}