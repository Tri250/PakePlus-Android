#pragma once

#include "result_types.h"
#include "bugreport_parser.h"
#include <string>
#include <vector>
#include <optional>

namespace digiguide::core {

// 健康度因子结构
struct HealthFactors {
    std::optional<float> capacity_retention;    // 容量保持率 (0-1)
    std::optional<float> cycle_decay;           // 循环衰减 (0-1)
    std::optional<float> resistance_growth;     // 内阻增长 (0-1)
    std::optional<float> thermal_aging;         // 温度老化 (0-1)
    std::optional<float> charging_damage;       // 充电损伤 (0-1)

    int available_factors;                      // 可用的因子数量

    // 辅助方法
    float getAverageScore() const;
};

// 电池健康度结果
struct BatteryHealthResult {
    float health_percentage;                    // 综合健康度 0-100
    std::string grade;                          // A+ / A / B / C / D / F
    HealthFactors factors;
    std::string diagnosis_text;                 // 诊断文字
    std::vector<std::string> suggestions;       // 使用建议

    // 辅助数据
    std::optional<float> estimated_resistance_mohm;  // 估算内阻
    std::optional<int> remaining_lifespan_months;    // 预估剩余寿命

    ConfidenceLevel confidence;                 // 置信度

    // 辅助方法
    std::string getGradeColor() const;
    std::string getGradeDescription() const;
};

// 电池健康度计算器类
class BatteryHealthCalculator {
public:
    // 主计算入口
    static BatteryHealthResult calculate(const BatteryRawData& raw_data);

    // 仅计算容量保持率
    static std::optional<float> calculateCapacityRetention(
        const std::optional<int>& current,
        const std::optional<int>& design);

    // 仅计算循环衰减
    static std::optional<float> calculateCycleDecay(
        const std::optional<int>& cycles,
        BatteryType type);

private:
    // 各因子计算
    static std::optional<float> calcCapacityRetention(
        const std::optional<int>& current,
        const std::optional<int>& design);

    static std::optional<float> calcCycleDecay(
        const std::optional<int>& cycles,
        BatteryType type);

    static std::optional<float> calcResistanceGrowth(
        const std::vector<std::pair<float, float>>& vc_pairs,
        float capacity_mah);

    static std::optional<float> calcThermalAging(
        const std::optional<float>& temperature);

    static std::optional<float> calcChargingDamage(
        const std::vector<BatteryRawData::ChargingEvent>& events);

    // 综合评分与等级
    static float computeWeightedScore(const HealthFactors& factors);
    static std::string computeGrade(float score);
    static std::vector<std::string> generateSuggestions(
        const HealthFactors& factors,
        const BatteryRawData& data);

    // 诊断文字生成
    static std::string generateDiagnosisText(
        const HealthFactors& factors,
        const BatteryRawData& data);

    // 剩余寿命估算
    static std::optional<int> estimateRemainingLifespan(
        const HealthFactors& factors,
        const BatteryRawData& data);

    // 内阻估算
    static std::optional<float> estimateResistance(
        const std::vector<std::pair<float, float>>& vc_pairs,
        float capacity_mah);
};

// 健康度等级描述
struct HealthGradeDescriptions {
    static std::string getDescription(const std::string& grade) {
        if (grade == "A+") return "电池状态极佳，几乎无老化";
        if (grade == "A")  return "电池状态良好，轻微老化";
        if (grade == "B")  return "电池状态一般，中度老化";
        if (grade == "C")  return "电池状态较差，明显老化";
        if (grade == "D")  return "电池状态很差，严重老化";
        if (grade == "F")  return "电池状态极差，建议更换";
        return "未知状态";
    }

    static std::string getColor(const std::string& grade) {
        if (grade == "A+") return "#4CAF50";  // 绿色
        if (grade == "A")  return "#8BC34A";  // 浅绿
        if (grade == "B")  return "#FFC107";  // 黄色
        if (grade == "C")  return "#FF9800";  // 橙色
        if (grade == "D")  return "#F44336";  // 红色
        if (grade == "F")  return "#9C27B0";  // 紫色
        return "#9E9E9E";  // 灰色
    }

    static int getMinPercentage(const std::string& grade) {
        if (grade == "A+") return 95;
        if (grade == "A")  return 90;
        if (grade == "B")  return 80;
        if (grade == "C")  return 70;
        if (grade == "D")  return 60;
        return 0;
    }
};

} // namespace digiguide::core