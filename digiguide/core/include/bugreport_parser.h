#pragma once

#include "result_types.h"
#include <string>
#include <vector>
#include <optional>

namespace digiguide::core {

// 电池原始数据结构
struct BatteryRawData {
    // 基础信息
    std::optional<std::string> brand;
    std::optional<std::string> model;
    std::optional<std::string> sn;

    // 容量数据
    std::optional<int> design_capacity_mah;      // 设计容量
    std::optional<int> current_capacity_mah;     // 当前容量（Min learned）
    std::optional<int> charge_counter_mah;       // 当前电量计数器

    // 循环与寿命
    std::optional<int> cycle_count;
    std::optional<std::string> manufacturing_date;

    // 温度
    std::optional<float> temperature_celsius;

    // 使用统计
    std::optional<int> screen_on_time_hours;     // 亮屏时间
    std::optional<int> charge_count;             // 充电次数

    // 充电行为（从battery history解析）
    struct ChargingEvent {
        int64_t timestamp;
        int start_level;       // 开始充电电量
        int end_level;         // 结束充电电量
        int duration_minutes;  // 充电时长
        float avg_power_w;     // 平均充电功率
    };
    std::vector<ChargingEvent> charging_events;

    // 应用耗电（仅第三方APP）
    struct AppPowerUsage {
        std::string package_name;
        std::string display_name;
        float power_mah;        // 耗电mAh
        int wakeup_count;       // 唤醒次数
        bool is_system;
    };
    std::vector<AppPowerUsage> app_power_usages;

    // 电压/电流数据（用于内阻估算）
    std::vector<std::pair<float, float>> voltage_current_pairs;  // (电压mV, 电流mA)

    // 辅助方法
    bool hasCapacityData() const {
        return design_capacity_mah.has_value() || current_capacity_mah.has_value();
    }

    bool hasCycleData() const {
        return cycle_count.has_value();
    }

    bool hasTemperatureData() const {
        return temperature_celsius.has_value();
    }

    int getAvailableDataCount() const {
        int count = 0;
        if (brand.has_value()) count++;
        if (model.has_value()) count++;
        if (design_capacity_mah.has_value()) count++;
        if (current_capacity_mah.has_value()) count++;
        if (cycle_count.has_value()) count++;
        if (manufacturing_date.has_value()) count++;
        if (temperature_celsius.has_value()) count++;
        if (screen_on_time_hours.has_value()) count++;
        if (charge_count.has_value()) count++;
        if (!voltage_current_pairs.empty()) count++;
        if (!charging_events.empty()) count++;
        if (!app_power_usages.empty()) count++;
        return count;
    }
};

// Bugreport解析器类
class BugreportParser {
public:
    // 从文本内容解析
    static BatteryRawData parseFromText(const std::string& bugreport_text);

    // 从ZIP文件解析
    static BatteryRawData parseFromZip(const std::string& zip_path);

    // 获取解析详情（哪些字段提取成功/失败）
    static ParseDetail getParseDetail(const BatteryRawData& data);

    // 获取解析统计
    static std::string getParseSummary(const BatteryRawData& data);

private:
    // 各子解析器
    static void extractBrandModel(const std::string& text, BatteryRawData& data);
    static void extractCapacity(const std::string& text, BatteryRawData& data);
    static void extractCycleCount(const std::string& text, BatteryRawData& data);
    static void extractManufacturingDate(const std::string& text, BatteryRawData& data);
    static void extractTemperature(const std::string& text, BatteryRawData& data);
    static void extractScreenOnTime(const std::string& text, BatteryRawData& data);
    static void extractChargingEvents(const std::string& text, BatteryRawData& data);
    static void extractAppPowerUsage(const std::string& text, BatteryRawData& data);
    static void extractVoltageCurrent(const std::string& text, BatteryRawData& data);
    static void extractSN(const std::string& text, BatteryRawData& data);

    // 辅助方法
    static std::optional<int> extractIntWithPatterns(
        const std::string& text,
        const std::vector<std::string>& patterns);

    static std::optional<std::string> extractStringWithPattern(
        const std::string& text,
        const std::string& pattern);
};

} // namespace digiguide::core