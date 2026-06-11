package service

import (
    "digiguide/backend/internal/model"
    "regexp"
    "strconv"
    "strings"
    "time"
)

// AnalyzeBugreportFile 分析bugreport文件
func AnalyzeBugreportFile(file interface{}) (model.BatteryAnalysisResponse, error) {
    // TODO: 实现文件解析
    return model.BatteryAnalysisResponse{}, nil
}

// AnalyzeBugreportText 分析bugreport文本内容
func AnalyzeBugreportText(content string) model.BatteryAnalysisResponse {
    rawData := parseBugreportText(content)
    healthResult := CalculateBatteryHealth(rawData)

    return model.BatteryAnalysisResponse{
        RawData:      rawData,
        HealthResult: healthResult,
        ReportID:     0, // TODO: 保存到数据库后返回ID
        AnalysisTime: time.Now(),
    }
}

// CalculateBatteryHealth 计算电池健康度
func CalculateBatteryHealth(rawData model.BatteryRawData) model.BatteryHealthResult {
    result := model.BatteryHealthResult{
        Grade:       "F",
        Suggestions: []string{},
    }

    var totalWeight float64
    var score float64

    // 容量保持率 (35%权重)
    if rawData.CurrentCapacityMah != nil && rawData.DesignCapacityMah != nil {
        retention := float64(*rawData.CurrentCapacityMah) / float64(*rawData.DesignCapacityMah)
        result.Factors.CapacityRetention = &retention
        score += retention * 0.35
        totalWeight += 0.35
        result.Factors.AvailableFactors++
    }

    // 循环衰减 (30%权重)
    if rawData.CycleCount != nil {
        cycles := *rawData.CycleCount
        var decay float64
        if cycles <= 200 {
            decay = 1.0 - float64(cycles)/100.0*0.032
        } else if cycles <= 400 {
            decay = 1.0 - 0.064 - float64(cycles-200)/100.0*0.04
        } else {
            decay = 1.0 - 0.064 - 0.08 - float64(cycles-400)/100.0*0.052
        }
        if decay < 0 {
            decay = 0
        }
        if decay > 1 {
            decay = 1
        }
        result.Factors.CycleDecay = &decay
        score += decay * 0.30
        totalWeight += 0.30
        result.Factors.AvailableFactors++
    }

    // 温度老化 (10%权重)
    if rawData.TemperatureCelsius != nil {
        temp := *rawData.TemperatureCelsius
        if temp <= 25 {
            result.Factors.ThermalAging = &[]float64{1.0}[0]
            score += 1.0 * 0.10
        } else {
            // 每高于25°C 10°C，老化因子下降
            aging := 1.0 - (temp - 25) / 100.0
            if aging < 0 {
                aging = 0
            }
            result.Factors.ThermalAging = &aging
            score += aging * 0.10
        }
        totalWeight += 0.10
        result.Factors.AvailableFactors++
    }

    // 计算综合健康度
    if totalWeight > 0 {
        result.HealthPercentage = (score / totalWeight) * 100
    }

    // 计算等级
    result.Grade = calculateGrade(result.HealthPercentage)

    // 设置置信度
    if result.Factors.AvailableFactors >= 4 {
        result.Confidence = model.ConfidenceHigh
    } else if result.Factors.AvailableFactors >= 2 {
        result.Confidence = model.ConfidenceMedium
    } else if result.Factors.AvailableFactors >= 1 {
        result.Confidence = model.ConfidenceLow
    } else {
        result.Confidence = model.ConfidenceNone
    }

    // 生成诊断文字
    result.DiagnosisText = generateDiagnosisText(rawData, result)

    // 生成建议
    result.Suggestions = generateSuggestions(result)

    return result
}

// GetReports 获取报告列表
func GetReports(page, limit string) []model.BatteryReport {
    // TODO: 从数据库查询
    return []model.BatteryReport{}
}

// GetReportDetail 获取报告详情
func GetReportDetail(id string) (model.BatteryReport, error) {
    // TODO: 从数据库查询
    return model.BatteryReport{}, nil
}

// DeleteReport 删除报告
func DeleteReport(id string) error {
    // TODO: 从数据库删除
    return nil
}

// parseBugreportText 解析bugreport文本
func parseBugreportText(text string) model.BatteryRawData {
    data := model.BatteryRawData{}

    // 提取品牌
    brandPattern := regexp.MustCompile(`ro\.product\.brand=\s*([A-Za-z0-9_\- ]+)`)
    if match := brandPattern.FindStringSubmatch(text); match != nil {
        data.Brand = &match[1]
    }

    // 提取型号
    modelPattern := regexp.MustCompile(`ro\.product\.model=\s*([A-Za-z0-9_\- ]+)`)
    if match := modelPattern.FindStringSubmatch(text); match != nil {
        data.Model = &match[1]
    }

    // 提取设计容量
    designPattern := regexp.MustCompile(`DesignCapacity:\s*(\d+)`)
    if match := designPattern.FindStringSubmatch(text); match != nil {
        val, _ := strconv.Atoi(match[1])
        data.DesignCapacityMah = &val
    }

    // 提取当前容量（按优先级）
    capacityPatterns := []string{
        `Min learned battery capacity:\s*(\d+)\s*mAh`,
        `full charge capacity:\s*(\d+)\s*mAh`,
        `learned capacity:\s*(\d+)\s*mAh`,
    }
    for _, pattern := range capacityPatterns {
        re := regexp.MustCompile(pattern)
        if match := re.FindStringSubmatch(text); match != nil {
            val, _ := strconv.Atoi(match[1])
            data.CurrentCapacityMah = &val
            break
        }
    }

    // 提取循环次数
    cyclePatterns := []string{
        `battery cycle count:\s*(\d+)`,
        `cycle count:\s*(\d+)`,
        `CycleCount:\s*(\d+)`,
    }
    for _, pattern := range cyclePatterns {
        re := regexp.MustCompile(pattern)
        if match := re.FindStringSubmatch(text); match != nil {
            val, _ := strconv.Atoi(match[1])
            data.CycleCount = &val
            break
        }
    }

    // 提取温度
    tempPattern := regexp.MustCompile(`battery temperature:\s*(\d+\.?\d*)\s*°?C`)
    if match := tempPattern.FindStringSubmatch(text); match != nil {
        val, _ := strconv.ParseFloat(match[1], 64)
        data.TemperatureCelsius = &val
    }

    return data
}

// calculateGrade 计算等级
func calculateGrade(percentage float64) string {
    if percentage >= 95 {
        return "A+"
    }
    if percentage >= 90 {
        return "A"
    }
    if percentage >= 80 {
        return "B"
    }
    if percentage >= 70 {
        return "C"
    }
    if percentage >= 60 {
        return "D"
    }
    return "F"
}

// generateDiagnosisText 生成诊断文字
func generateDiagnosisText(rawData model.BatteryRawData, result model.BatteryHealthResult) string {
    var sb strings.Builder

    sb.WriteString("电池健康度分析结果：\n")

    if result.Factors.CapacityRetention != nil {
        retention := *result.Factors.CapacityRetention * 100
        sb.WriteString("容量保持率：" + strconv.FormatFloat(retention, 'f', 1, 64) + "%\n")
    }

    if rawData.CycleCount != nil {
        sb.WriteString("循环次数：" + strconv.Itoa(*rawData.CycleCount) + "次\n")
    }

    if rawData.TemperatureCelsius != nil {
        sb.WriteString("当前温度：" + strconv.FormatFloat(*rawData.TemperatureCelsius, 'f', 1, 64) + "°C\n")
    }

    sb.WriteString("\n分析置信度：" + string(result.Confidence) + " (" + strconv.Itoa(result.Factors.AvailableFactors) + "个因子)\n")

    return sb.String()
}

// generateSuggestions 生成使用建议
func generateSuggestions(result model.BatteryHealthResult) []string {
    suggestions := []string{}

    if result.Factors.CapacityRetention != nil && *result.Factors.CapacityRetention < 0.8 {
        suggestions = append(suggestions, "电池容量明显衰减，建议考虑更换电池")
    } else if result.Factors.CapacityRetention != nil && *result.Factors.CapacityRetention < 0.9 {
        suggestions = append(suggestions, "电池容量有所衰减，建议减少深度放电")
    }

    if result.HealthPercentage < 80 {
        suggestions = append(suggestions, "建议使用原装充电器，避免快充过度")
        suggestions = append(suggestions, "建议电量保持在20%-80%区间，避免深度放电")
    }

    if len(suggestions) == 0 {
        suggestions = append(suggestions, "电池状态良好，继续保持良好使用习惯")
    }

    return suggestions
}