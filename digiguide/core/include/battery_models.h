#pragma once

#include "result_types.h"
#include <optional>
#include <vector>

namespace digiguide::core {

// 电池物理模型参数
struct BatteryPhysicsModel {
    // 基础参数
    float nominal_capacity_mah;     // 标称容量
    float nominal_voltage_v;        // 标称电压
    float max_voltage_v;            // 最大电压
    float min_voltage_v;            // 最小电压

    // 内阻参数
    float initial_resistance_mohm;  // 初始内阻（新电池）
    float max_resistance_mohm;      // 最大内阻（报废阈值）

    // 老化参数
    float cycle_life;               // 设计循环寿命
    float calendar_life_years;      // 设计日历寿命

    // 温度参数
    float optimal_temp_c;           // 最佳工作温度
    float max_temp_c;               // 最高工作温度
    float min_temp_c;               // 最低工作温度
};

// 不同电池类型的物理模型
struct BatteryModelFactory {
    // 手机常用锂聚合物电池模型
    static BatteryPhysicsModel getLiPoPhoneModel(float capacity_mah) {
        BatteryPhysicsModel model;
        model.nominal_capacity_mah = capacity_mah;
        model.nominal_voltage_v = 3.7f;
        model.max_voltage_v = 4.35f;
        model.min_voltage_v = 3.0f;

        // 内阻：容量越大内阻越小
        // 典型值：5000mAh电池初始内阻约50mΩ
        model.initial_resistance_mohm = 50.0f * (5000.0f / capacity_mah);
        model.max_resistance_mohm = model.initial_resistance_mohm * 3.0f;

        model.cycle_life = 500.0f;      // 500次循环后80%容量
        model.calendar_life_years = 3.0f;

        model.optimal_temp_c = 25.0f;
        model.max_temp_c = 45.0f;
        model.min_temp_c = 0.0f;

        return model;
    }

    // 锂离子电池模型（笔记本等）
    static BatteryPhysicsModel getLiIonLaptopModel(float capacity_mah) {
        BatteryPhysicsModel model;
        model.nominal_capacity_mah = capacity_mah;
        model.nominal_voltage_v = 3.6f;
        model.max_voltage_v = 4.2f;
        model.min_voltage_v = 2.75f;

        model.initial_resistance_mohm = 80.0f;
        model.max_resistance_mohm = 240.0f;

        model.cycle_life = 300.0f;
        model.calendar_life_years = 2.0f;

        model.optimal_temp_c = 20.0f;
        model.max_temp_c = 60.0f;
        model.min_temp_c = -10.0f;

        return model;
    }

    // 根据类型获取模型
    static BatteryPhysicsModel getModel(BatteryType type, float capacity_mah) {
        switch (type) {
            case BatteryType::LiPo:
                return getLiPoPhoneModel(capacity_mah);
            case BatteryType::LiIon:
                return getLiIonLaptopModel(capacity_mah);
            default:
                return getLiPoPhoneModel(capacity_mah);
        }
    }
};

// 内阻估算器
class ResistanceEstimator {
public:
    // 从电压-电流数据估算内阻
    // R = ΔV / ΔI
    static std::optional<float> estimateFromVoltageCurrent(
        const std::vector<std::pair<float, float>>& vc_pairs) {

        if (vc_pairs.size() < 2) {
            return std::nullopt;
        }

        // 计算电压变化和电流变化
        float total_voltage_change = 0;
        float total_current_change = 0;
        int valid_pairs = 0;

        for (size_t i = 1; i < vc_pairs.size(); ++i) {
            float v1 = vc_pairs[i-1].first;   // mV
            float v2 = vc_pairs[i].first;     // mV
            float i1 = vc_pairs[i-1].second;  // mA
            float i2 = vc_pairs[i].second;    // mA

            float delta_v = v2 - v1;
            float delta_i = i2 - i1;

            // 忽略电流变化太小的情况（测量误差）
            if (std::abs(delta_i) > 50.0f) {
                total_voltage_change += delta_v;
                total_current_change += delta_i;
                valid_pairs++;
            }
        }

        if (valid_pairs == 0 || std::abs(total_current_change) < 1.0f) {
            return std::nullopt;
        }

        // R = ΔV / ΔI (mV / mA = mΩ)
        float resistance = std::abs(total_voltage_change / total_current_change);

        // 验证合理性（手机电池内阻通常在30-200mΩ）
        if (resistance < 10.0f || resistance > 500.0f) {
            return std::nullopt;
        }

        return resistance;
    }

    // 从容量估算典型内阻
    static float estimateTypicalResistance(float capacity_mah) {
        // 经验公式：容量越大内阻越小
        // 5000mAh → 50mΩ
        // 3000mAh → 80mΩ
        return 50.0f * (5000.0f / capacity_mah);
    }
};

// 老化模型计算器
class AgingModelCalculator {
public:
    // 循环老化模型
    // 每次循环损失约 0.02% - 0.05% 容量
    static float calcCycleAging(int cycle_count, BatteryType type) {
        float decay_rate = 0.0003f;  // 每循环损失0.03%

        if (type == BatteryType::LiIon) {
            decay_rate = 0.0005f;  // 锂离子老化更快
        }

        // 循环衰减 = 1 - (循环次数 * 衰减率)
        float decay = 1.0f - (cycle_count * decay_rate);

        // 限制在合理范围
        if (decay < 0.0f) decay = 0.0f;
        if (decay > 1.0f) decay = 1.0f;

        return decay;
    }

    // 温度老化模型（阿伦尼乌斯方程简化）
    // 每高于25°C 10°C，老化速率翻倍
    static float calcThermalAging(float temperature_celsius) {
        const float optimal_temp = 25.0f;

        if (temperature_celsius <= optimal_temp) {
            return 1.0f;  // 最佳温度，无额外老化
        }

        // 温度差
        float temp_diff = temperature_celsius - optimal_temp;

        // 老化因子 = 2^(温度差/10)
        float aging_factor = std::pow(2.0f, temp_diff / 10.0f);

        // 转换为健康度因子（1 - 老化因子/10）
        float health_factor = 1.0f - (aging_factor - 1.0f) / 10.0f;

        // 限制在合理范围
        if (health_factor < 0.0f) health_factor = 0.0f;
        if (health_factor > 1.0f) health_factor = 1.0f;

        return health_factor;
    }

    // 日历老化模型
    // 每年自然损失约 2-4% 容量
    static float calcCalendarAging(int months_since_production) {
        float decay_rate = 0.003f;  // 每月损失0.3%

        float decay = 1.0f - (months_since_production * decay_rate);

        if (decay < 0.0f) decay = 0.0f;
        if (decay > 1.0f) decay = 1.0f;

        return decay;
    }

    // 充电损伤模型
    // 高功率充电、过充、深度放电都会造成损伤
    static float calcChargingDamage(
        const std::vector<BatteryRawData::ChargingEvent>& events) {

        if (events.empty()) {
            return std::nullopt;
        }

        float damage_score = 0.0f;
        int analyzed_events = 0;

        for (const auto& event : events) {
            // 高功率充电损伤（>1C）
            float capacity_mah = 5000.0f;  // 默认容量
            float power_rate = event.avg_power_w / (capacity_mah / 1000.0f);

            if (power_rate > 1.0f) {
                damage_score += 0.01f;  // 快充损伤
            }

            // 深度放电损伤（<20%开始充电）
            if (event.start_level < 20) {
                damage_score += 0.02f;
            }

            // 过充损伤（>95%结束充电）
            if (event.end_level > 95) {
                damage_score += 0.01f;
            }

            analyzed_events++;
        }

        if (analyzed_events == 0) {
            return 1.0f;  // 无损伤数据，假设无损伤
        }

        // 平均损伤
        float avg_damage = damage_score / analyzed_events;

        // 转换为健康因子
        float health_factor = 1.0f - avg_damage;

        if (health_factor < 0.0f) health_factor = 0.0f;
        if (health_factor > 1.0f) health_factor = 1.0f;

        return health_factor;
    }
};

} // namespace digiguide::core