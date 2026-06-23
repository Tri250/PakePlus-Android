#include "battery_health.h"
#include "battery_models.h"
#include <sstream>
#include <algorithm>
#include <cmath>

namespace digiguide::core {

// ========== 主计算入口 ==========

BatteryHealthResult BatteryHealthCalculator::calculate(const BatteryRawData& raw) {
    BatteryHealthResult result;
    HealthFactors& f = result.factors;

    // 1. 容量保持率（最基础，优先级最高）
    f.capacity_retention = calcCapacityRetention(
        raw.current_capacity_mah, raw.design_capacity_mah);

    // 2. 循环衰减
    f.cycle_decay = calcCycleDecay(raw.cycle_count, BatteryType::LiPo);

    // 3. 内阻估算（从电压降/电流数据真实计算）
    f.resistance_growth = calcResistanceGrowth(
        raw.voltage_current_pairs,
        raw.design_capacity_mah.value_or(5000));

    // 4. 温度老化（阿伦尼乌斯模型）
    f.thermal_aging = calcThermalAging(raw.temperature_celsius);

    // 5. 充电行为损伤
    f.charging_damage = calcChargingDamage(raw.charging_events);

    // 统计可用因子数量
    f.available_factors = 0;
    if (f.capacity_retention.has_value()) f.available_factors++;
    if (f.cycle_decay.has_value()) f.available_factors++;
    if (f.resistance_growth.has_value()) f.available_factors++;
    if (f.thermal_aging.has_value()) f.available_factors++;
    if (f.charging_damage.has_value()) f.available_factors++;

    // 计算综合健康度
    result.health_percentage = computeWeightedScore(f);
    result.grade = computeGrade(result.health_percentage);
    result.suggestions = generateSuggestions(f, raw);
    result.diagnosis_text = generateDiagnosisText(f, raw);

    // 估算内阻
    result.estimated_resistance_mohm = estimateResistance(
        raw.voltage_current_pairs,
        raw.design_capacity_mah.value_or(5000));

    // 估算剩余寿命
    result.remaining_lifespan_months = estimateRemainingLifespan(f, raw);

    // 置信度判定
    if (f.available_factors >= 4) result.confidence = ConfidenceLevel::HIGH;
    else if (f.available_factors >= 2) result.confidence = ConfidenceLevel::MEDIUM;
    else if (f.available_factors >= 1) result.confidence = ConfidenceLevel::LOW;
    else result.confidence = ConfidenceLevel::NONE;

    return result;
}

// ========== 各因子计算 ==========

std::optional<float> BatteryHealthCalculator::calcCapacityRetention(
    const std::optional<int>& current,
    const std::optional<int>& design) {

    if (!current.has_value() || !design.has_value()) {
        return std::nullopt;
    }

    if (design.value() <= 0) {
        return std::nullopt;
    }

    float retention = static_cast<float>(current.value()) / static_cast<float>(design.value());

    // 限制在合理范围（0-1.1，新电池可能略高于设计容量）
    if (retention < 0.0f) retention = 0.0f;
    if (retention > 1.1f) retention = 1.1f;

    return retention;
}

std::optional<float> BatteryHealthCalculator::calcCycleDecay(
    const std::optional<int>& cycles,
    BatteryType type) {

    if (!cycles.has_value()) {
        return std::nullopt;
    }

    // 使用老化模型计算
    float decay = AgingModelCalculator::calcCycleAging(cycles.value(), type);

    return decay;
}

std::optional<float> BatteryHealthCalculator::calcResistanceGrowth(
    const std::vector<std::pair<float, float>>& vc_pairs,
    float capacity_mah) {

    if (vc_pairs.size() < 2) {
        return std::nullopt;
    }

    // 估算内阻
    auto resistance = ResistanceEstimator::estimateFromVoltageCurrent(vc_pairs);

    if (!resistance.has_value()) {
        return std::nullopt;
    }

    // 获取典型初始内阻
    float typical_initial = ResistanceEstimator::estimateTypicalResistance(capacity_mah);

    // 内阻增长因子 = 1 - (当前内阻 / 最大内阻)
    // 当内阻达到初始值的3倍时，电池基本报废
    float max_resistance = typical_initial * 3.0f;
    float growth_factor = 1.0f - (resistance.value() / max_resistance);

    // 限制在合理范围
    if (growth_factor < 0.0f) growth_factor = 0.0f;
    if (growth_factor > 1.0f) growth_factor = 1.0f;

    return growth_factor;
}

std::optional<float> BatteryHealthCalculator::calcThermalAging(
    const std::optional<float>& temperature) {

    if (!temperature.has_value()) {
        return std::nullopt;
    }

    // 使用温度老化模型
    float aging = AgingModelCalculator::calcThermalAging(temperature.value());

    return aging;
}

std::optional<float> BatteryHealthCalculator::calcChargingDamage(
    const std::vector<BatteryRawData::ChargingEvent>& events) {

    if (events.empty()) {
        return std::nullopt;
    }

    // 使用充电损伤模型
    float damage = AgingModelCalculator::calcChargingDamage(events);

    return damage;
}

// ========== 综合评分 ==========

float BatteryHealthCalculator::computeWeightedScore(const HealthFactors& f) {
    // 动态权重：缺失因子不参与，权重按比例重新分配
    float total_weight = 0;
    float score = 0;

    // 容量保持率权重35%
    if (f.capacity_retention.has_value()) {
        score += f.capacity_retention.value() * 0.35f;
        total_weight += 0.35f;
    }

    // 循环衰减权重30%
    if (f.cycle_decay.has_value()) {
        score += f.cycle_decay.value() * 0.30f;
        total_weight += 0.30f;
    }

    // 内阻增长权重15%
    if (f.resistance_growth.has_value()) {
        score += f.resistance_growth.value() * 0.15f;
        total_weight += 0.15f;
    }

    // 温度老化权重10%
    if (f.thermal_aging.has_value()) {
        score += f.thermal_aging.value() * 0.10f;
        total_weight += 0.10f;
    }

    // 充电损伤权重10%
    if (f.charging_damage.has_value()) {
        score += f.charging_damage.value() * 0.10f;
        total_weight += 0.10f;
    }

    if (total_weight == 0) return 0;

    // 按权重比例归一化
    return (score / total_weight) * 100.0f;
}

std::string BatteryHealthCalculator::computeGrade(float score) {
    if (score >= 95) return "A+";
    if (score >= 90) return "A";
    if (score >= 80) return "B";
    if (score >= 70) return "C";
    if (score >= 60) return "D";
    return "F";
}

// ========== 使用建议生成 ==========

std::vector<std::string> BatteryHealthCalculator::generateSuggestions(
    const HealthFactors& factors,
    const BatteryRawData& data) {

    std::vector<std::string> suggestions;

    // 容量保持率建议
    if (factors.capacity_retention.has_value()) {
        float retention = factors.capacity_retention.value();
        if (retention < 0.8f) {
            suggestions.push_back("电池容量明显衰减，建议考虑更换电池");
        } else if (retention < 0.9f) {
            suggestions.push_back("电池容量有所衰减，建议减少深度放电");
        } else {
            suggestions.push_back("电池容量保持良好，继续保持良好使用习惯");
        }
    }

    // 循环次数建议
    if (factors.cycle_decay.has_value() && data.cycle_count.has_value()) {
        int cycles = data.cycle_count.value();
        if (cycles > 400) {
            suggestions.push_back("循环次数较高，电池已接近设计寿命");
        } else if (cycles > 200) {
            suggestions.push_back("建议避免频繁充电，保持20%-80%电量区间");
        }
    }

    // 温度建议
    if (data.temperature_celsius.has_value()) {
        float temp = data.temperature_celsius.value();
        if (temp > 40.0f) {
            suggestions.push_back("电池温度偏高，建议避免高温环境使用");
        } else if (temp > 35.0f) {
            suggestions.push_back("建议减少高负载应用使用，降低发热");
        }
    }

    // 充电习惯建议
    if (!suggestions.empty()) {
        suggestions.push_back("建议使用原装充电器，避免快充过度");
        suggestions.push_back("建议电量保持在20%-80%区间，避免深度放电");
    }

    return suggestions;
}

// ========== 诊断文字生成 ==========

std::string BatteryHealthCalculator::generateDiagnosisText(
    const HealthFactors& factors,
    const BatteryRawData& data) {

    std::ostringstream oss;

    oss << "电池健康度分析结果：\n";

    // 容量分析
    if (factors.capacity_retention.has_value()) {
        float retention = factors.capacity_retention.value() * 100;
        oss << "容量保持率：" << retention << "%\n";

        if (data.current_capacity_mah.has_value() && data.design_capacity_mah.has_value()) {
            oss << "当前容量：" << data.current_capacity_mah.value() << "mAh\n";
            oss << "设计容量：" << data.design_capacity_mah.value() << "mAh\n";
        }
    }

    // 循环分析
    if (data.cycle_count.has_value()) {
        oss << "循环次数：" << data.cycle_count.value() << "次\n";
    }

    // 温度分析
    if (data.temperature_celsius.has_value()) {
        oss << "当前温度：" << data.temperature_celsius.value() << "°C\n";
    }

    // 置信度说明
    oss << "\n分析置信度：";
    switch (factors.available_factors) {
        case 5: oss << "高（5个因子）\n"; break;
        case 4: oss << "高（4个因子）\n"; break;
        case 3: oss << "中（3个因子）\n"; break;
        case 2: oss << "中（2个因子）\n"; break;
        case 1: oss << "低（1个因子）\n"; break;
        default: oss << "无（缺少数据）\n"; break;
    }

    return oss.str();
}

// ========== 剩余寿命估算 ==========

std::optional<int> BatteryHealthCalculator::estimateRemainingLifespan(
    const HealthFactors& factors,
    const BatteryRawData& data) {

    if (factors.available_factors < 2) {
        return std::nullopt;
    }

    // Base monthly degradation rate: 0.5% per month
    float base_monthly_decay = 0.005f;

    // Adjust decay rate based on computed factors
    float adjusted_monthly_decay = base_monthly_decay;

    // Factor 1: Capacity retention — lower retention means faster future degradation
    if (factors.capacity_retention.has_value()) {
        float retention = factors.capacity_retention.value();
        if (retention < 0.7f) {
            // Below 70% retention, degradation accelerates (non-linear aging)
            adjusted_monthly_decay *= 1.0f + (0.7f - retention) * 3.0f;
        } else if (retention > 0.9f) {
            // Above 90% retention, degradation is slower
            adjusted_monthly_decay *= 0.7f;
        }
    }

    // Factor 2: Cycle decay — high cycle count means faster future degradation
    if (factors.cycle_decay.has_value()) {
        float cycle_score = factors.cycle_decay.value();
        if (cycle_score < 0.6f) {
            adjusted_monthly_decay *= 1.0f + (0.6f - cycle_score) * 1.5f;
        }
    }

    // Factor 3: Resistance growth — higher resistance means faster degradation
    if (factors.resistance_growth.has_value()) {
        float resistance_score = factors.resistance_growth.value();
        if (resistance_score < 0.5f) {
            adjusted_monthly_decay *= 1.0f + (0.5f - resistance_score) * 2.0f;
        }
    }

    // Factor 4: Temperature aging — chronic heat exposure accelerates degradation
    if (factors.thermal_aging.has_value()) {
        float temp_score = factors.thermal_aging.value();
        if (temp_score < 0.6f) {
            adjusted_monthly_decay *= 1.0f + (0.6f - temp_score) * 1.0f;
        }
    }

    // Factor 5: Charging damage — poor charging habits accelerate degradation
    if (factors.charging_damage.has_value()) {
        float charge_score = factors.charging_damage.value();
        if (charge_score < 0.6f) {
            adjusted_monthly_decay *= 1.0f + (0.6f - charge_score) * 0.8f;
        }
    }

    // Cap adjusted rate to reasonable bounds (0.2% - 3% per month)
    if (adjusted_monthly_decay < 0.002f) adjusted_monthly_decay = 0.002f;
    if (adjusted_monthly_decay > 0.03f) adjusted_monthly_decay = 0.03f;

    // Calculate remaining lifespan based on current health
    float health = factors.getAverageScore();
    float remaining_health = health - 0.6f; // 60% threshold for replacement
    if (remaining_health <= 0) {
        return 0;  // Already needs replacement
    }

    int months = static_cast<int>(remaining_health / adjusted_monthly_decay);

    // Limit to reasonable range (0-48 months)
    if (months < 0) months = 0;
    if (months > 48) months = 48;

    return months;
}

// ========== 内阻估算 ==========

std::optional<float> BatteryHealthCalculator::estimateResistance(
    const std::vector<std::pair<float, float>>& vc_pairs,
    float capacity_mah) {

    return ResistanceEstimator::estimateFromVoltageCurrent(vc_pairs);
}

// ========== 公共计算接口 ==========

std::optional<float> BatteryHealthCalculator::calculateCapacityRetention(
    const std::optional<int>& current,
    const std::optional<int>& design) {

    return calcCapacityRetention(current, design);
}

std::optional<float> BatteryHealthCalculator::calculateCycleDecay(
    const std::optional<int>& cycles,
    BatteryType type) {

    return calcCycleDecay(cycles, type);
}

// ========== HealthFactors 辅助方法 ==========

float HealthFactors::getAverageScore() const {
    float sum = 0;
    int count = 0;

    if (capacity_retention.has_value()) {
        sum += capacity_retention.value();
        count++;
    }
    if (cycle_decay.has_value()) {
        sum += cycle_decay.value();
        count++;
    }
    if (resistance_growth.has_value()) {
        sum += resistance_growth.value();
        count++;
    }
    if (thermal_aging.has_value()) {
        sum += thermal_aging.value();
        count++;
    }
    if (charging_damage.has_value()) {
        sum += charging_damage.value();
        count++;
    }

    if (count == 0) return 0;
    return sum / count;
}

// ========== BatteryHealthResult 辅助方法 ==========

std::string BatteryHealthResult::getGradeColor() const {
    return HealthGradeDescriptions::getColor(grade);
}

std::string BatteryHealthResult::getGradeDescription() const {
    return HealthGradeDescriptions::getDescription(grade);
}

} // namespace digiguide::core