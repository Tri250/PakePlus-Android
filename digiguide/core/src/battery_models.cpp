#include "battery_models.h"
#include "battery_health.h"
#include <cmath>

namespace digiguide::core {

// ========== 内阻估算实现 ==========

// 已在头文件中实现，此处补充额外方法

// ========== 老化模型实现 ==========

// 已在头文件中实现

// ========== 电池物理模型实现 ==========

// 已在头文件中实现

// ========== 辅助计算函数 ==========

namespace {

// 电池容量衰减曲线（经验公式）
float capacityDecayCurve(int cycles, float design_capacity) {
    // 锂电池容量衰减经验公式
    // Q(n) = Q0 * exp(-k * n)
    // 其中 k ≈ 0.0003（每循环衰减率）
    float k = 0.0003f;
    float decay = std::exp(-k * cycles);
    return decay;
}

// 温度对寿命的影响（阿伦尼乌斯方程简化）
float temperatureLifeFactor(float temperature_celsius) {
    // 参考温度25°C
    float T_ref = 25.0f;
    float T = temperature_celsius;

    // 活化能相关系数（简化）
    // 每升高10°C，老化速率翻倍
    float factor = std::pow(2.0f, (T - T_ref) / 10.0f);

    return factor;
}

// 内阻增长模型
float resistanceGrowthModel(int cycles, float initial_resistance) {
    // 内阻随循环次数增长
    // R(n) = R0 * (1 + a * sqrt(n))
    // 其中 a ≈ 0.1
    float a = 0.1f;
    float growth = 1.0f + a * std::sqrt(static_cast<float>(cycles));

    return initial_resistance * growth;
}

} // anonymous namespace

// ========== 电池状态评估 ==========

struct BatteryStateEvaluator {
    // 评估电池是否需要更换
    static bool needsReplacement(const BatteryHealthResult& result) {
        return result.health_percentage < 60.0f;
    }

    // 评估电池是否处于警告状态
    static bool isWarningState(const BatteryHealthResult& result) {
        return result.health_percentage < 80.0f;
    }

    // 评估电池是否处于良好状态
    static bool isGoodState(const BatteryHealthResult& result) {
        return result.health_percentage >= 90.0f;
    }

    // 评估电池是否处于极佳状态
    static bool isExcellentState(const BatteryHealthResult& result) {
        return result.health_percentage >= 95.0f;
    }
};

// ========== 电池寿命预测 ==========

struct BatteryLifespanPredictor {
    // 预测剩余循环次数
    static int predictRemainingCycles(
        int current_cycles,
        float capacity_retention,
        float target_retention = 0.8f) {

        // 基于当前衰减率预测
        if (capacity_retention >= target_retention) {
            // 计算衰减率
            float decay_rate = (1.0f - capacity_retention) / current_cycles;

            // 预测达到目标衰减率的循环次数
            float remaining_decay = capacity_retention - target_retention;
            int remaining_cycles = static_cast<int>(remaining_decay / decay_rate);

            return remaining_cycles;
        }

        return 0;  // 已低于目标
    }

    // 预测剩余使用时间（月）
    static int predictRemainingMonths(
        float health_percentage,
        float monthly_decay_rate = 0.5f) {

        // 假设每月健康度下降 monthly_decay_rate%
        float remaining_health = health_percentage - 60.0f;  // 60%为更换阈值

        if (remaining_health <= 0) return 0;

        int months = static_cast<int>(remaining_health / monthly_decay_rate);

        // 限制在0-36个月
        if (months < 0) months = 0;
        if (months > 36) months = 36;

        return months;
    }
};

} // namespace digiguide::core