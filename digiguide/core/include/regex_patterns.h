#pragma once

#include <vector>
#include <string>
#include <map>
#include <functional>
#include <memory>

namespace digiguide::core {

/**
 * 正则模式库 - 30+模式用于bugreport解析
 * 支持动态扩展和社区反馈
 */
struct RegexPatterns {

    // ========== 容量提取模式（按优先级排序）==========
    static const std::vector<std::string>& getCapacityPatterns() {
        static const std::vector<std::string> patterns = {
            R"(Min learned battery capacity:\s*(\d+)\s*mAh)",
            R"(full charge capacity:\s*(\d+)\s*mAh)",
            R"(learned capacity:\s*(\d+)\s*mAh)",
            R"(FullCapacity:\s*(\d+))",
            R"(battery capacity:\s*(\d+)\s*mAh)",
            R"(Capacity:\s*(\d+)\s*mAh)",
            // 新增模式（社区反馈）
            R"(CurrentCapacity:\s*(\d+)\s*mAh)",
            R"(battery_current_capacity:\s*(\d+))",
            R"(last_full_charge_capacity:\s*(\d+)\s*mAh)",
            R"(BatteryCapacity:\s*(\d+)\s*mAh)",
            R"(real capacity:\s*(\d+)\s*mAh)",
            R"(actual capacity:\s*(\d+)\s*mAh)"
        };
        return patterns;
    }

    // ========== 循环次数提取模式（9种厂商格式 + 扩展）==========
    static const std::vector<std::string>& getCycleCountPatterns() {
        static const std::vector<std::string> patterns = {
            R"(battery cycle count:\s*(\d+))",
            R"(cycle count:\s*(\d+))",
            R"(charge cycles:\s*(\d+))",
            R"(battery cycles:\s*(\d+))",
            R"(CycleCount:\s*(\d+))",
            R"(BatteryCycleCount:\s*(\d+))",
            R"(ChargingCycleCount:\s*(\d+))",
            R"(battery_age_cycles:\s*(\d+))",
            R"(cycle_count:\s*(\d+))",
            // 新增模式（社区反馈）
            R"(Battery Cycle Count:\s*(\d+))",
            R"(CYCLE_COUNT:\s*(\d+))",
            R"(battery_cycle:\s*(\d+))",
            R"(charge_cycle_count:\s*(\d+))",
            R"(cycle:\s*(\d+))",
            R"(循环次数[:：]\s*(\d+))",
            R"(充电循环[:：]\s*(\d+))",
            R"(battery_cycles_count:\s*(\d+))"
        };
        return patterns;
    }

    // ========== 制造日期提取模式（16种格式 + 扩展）==========
    static const std::vector<std::string>& getDatePatterns() {
        static const std::vector<std::string> patterns = {
            R"(manufacturing_date:\s*(\d{4})-(\d{2})-(\d{2}))",
            R"(mfg_date:\s*(\d{4})-(\d{2})-(\d{2}))",
            R"(battery.*?date:\s*(\d{4})-(\d{2})-(\d{2}))",
            R"(first_use_date:\s*(\d{4})-(\d{2})-(\d{2}))",
            R"(battery_make_date:\s*(\d{4})-(\d{2})-(\d{2}))",
            R"(mfg_date:\s*(\d{4})(\d{2})(\d{2}))",
            R"(manufacturing_date:\s*(\d{4})(\d{2})(\d{2}))",
            R"(mfgdate:\s*(\d{4})(\d{2})(\d{2}))",
            R"(battery_produce_date:\s*(\d{4})(\d{2})(\d{2}))",
            R"(生产日期[:：]\s*(\d{4})[年/-](\d{1,2})[月/-](\d{1,2}))",
            R"(出厂日期[:：]\s*(\d{4})[年/-](\d{1,2})[月/-](\d{1,2}))",
            R"(manufacturing_date:\s*(\d{4})[.\/](\d{2})[.\/](\d{2}))",
            R"(mfg_date:\s*(\d{2})[.\/](\d{2})[.\/](\d{4}))",
            R"(Battery\s+MFG\s+Date:\s*(\d{4})[.-](\d{2})[.-](\d{2}))",
            R"(battery_production_date:\s*(\d{4})-(\d{2})-(\d{2}))",
            R"(battery_manufacture_time:\s*(\d{4})(\d{2})(\d{2}))",
            // 新增模式（社区反馈）
            R"(MFG_DATE:\s*(\d{4})-(\d{2})-(\d{2}))",
            R"(BATTERY_MFG_DATE:\s*(\d{4})(\d{2})(\d{2}))",
            R"(manufactured:\s*(\d{4})-(\d{2})-(\d{2}))",
            R"(production_date:\s*(\d{4})-(\d{2})-(\d{2}))",
            R"(产日期[:：]\s*(\d{4})[年/-](\d{1,2})[月/-](\d{1,2}))",
            R"(制造日期[:：]\s*(\d{4})[年/-](\d{1,2})[月/-](\d{1,2}))",
            R"(battery_age_date:\s*(\d{4})-(\d{2})-(\d{2}))",
            R"(cell_manufacture_date:\s*(\d{4})-(\d{2})-(\d{2}))"
        };
        return patterns;
    }

    // ========== 品牌/型号提取模式 ==========
    static const std::string& getBrandPattern() {
        static const std::string pattern = R"(ro\.product\.brand=\s*([A-Za-z0-9_\- ]+))";
        return pattern;
    }

    static const std::string& getManufacturerPattern() {
        static const std::string pattern = R"(ro\.product\.manufacturer=\s*([A-Za-z0-9_\- ]+))";
        return pattern;
    }

    static const std::string& getModelPattern() {
        static const std::string pattern = R"(ro\.product\.model=\s*([A-Za-z0-9_\- ]+))";
        return pattern;
    }

    // ========== 温度提取模式 ==========
    static const std::vector<std::string>& getTemperaturePatterns() {
        static const std::vector<std::string> patterns = {
            R"(battery temperature:\s*(\d+\.?\d*)\s*°?C)",
            R"(BatteryTemp:\s*(\d+\.?\d*))",
            // 新增模式
            R"(battery_temp:\s*(\d+\.?\d*))",
            R"(BatteryTemperature:\s*(\d+\.?\d*)\s*°?C)",
            R"(temp:\s*(\d+\.?\d*)\s*C)",
            R"(temperature:\s*(\d+\.?\d*)\s*°C)",
            R"(电池温度[:：]\s*(\d+\.?\d*)\s*°?C)"
        };
        return patterns;
    }

    static const std::string& getTemperaturePattern() {
        static const std::string pattern = R"(battery temperature:\s*(\d+\.?\d*)\s*°?C)";
        return pattern;
    }

    static const std::string& getTemperaturePatternAlt() {
        static const std::string pattern = R"(BatteryTemp:\s*(\d+\.?\d*))";
        return pattern;
    }

    // ========== 设计容量提取模式 ==========
    static const std::vector<std::string>& getDesignCapacityPatterns() {
        static const std::vector<std::string> patterns = {
            R"(DesignCapacity:\s*(\d+))",
            // 新增模式
            R"(design_capacity:\s*(\d+))",
            R"(DesignCapacity:\s*(\d+)\s*mAh)",
            R"(battery_design_capacity:\s*(\d+))",
            R"(nominal_capacity:\s*(\d+)\s*mAh)",
            R"(额定容量[:：]\s*(\d+)\s*mAh)",
            R"(设计容量[:：]\s*(\d+)\s*mAh)"
        };
        return patterns;
    }

    static const std::string& getDesignCapacityPattern() {
        static const std::string pattern = R"(DesignCapacity:\s*(\d+))";
        return pattern;
    }

    // ========== 充电计数提取模式 ==========
    static const std::string& getChargeCountPattern() {
        static const std::string pattern = R"(charge_count:\s*(\d+))";
        return pattern;
    }

    // ========== 亮屏时间提取模式 ==========
    static const std::string& getScreenOnTimePattern() {
        static const std::string pattern = R"(Screen on time:\s*(\d+\.?\d*)\s*h)";
        return pattern;
    }

    // ========== 电压提取模式 ==========
    static const std::vector<std::string>& getVoltagePatterns() {
        static const std::vector<std::string> patterns = {
            R"(battery voltage:\s*(\d+\.?\d*)\s*mV)",
            R"(voltage:\s*(\d+\.?\d*)\s*mV)",
            R"(BatteryVoltage:\s*(\d+\.?\d*))",
            R"(current_voltage:\s*(\d+\.?\d*)\s*mV)"
        };
        return patterns;
    }

    static const std::string& getVoltagePattern() {
        static const std::string pattern = R"(battery voltage:\s*(\d+\.?\d*)\s*mV)";
        return pattern;
    }

    // ========== 电流提取模式 ==========
    static const std::vector<std::string>& getCurrentPatterns() {
        static const std::vector<std::string> patterns = {
            R"(battery current:\s*(-?\d+\.?\d*)\s*mA)",
            R"(current:\s*(-?\d+\.?\d*)\s*mA)",
            R"(BatteryCurrent:\s*(-?\d+\.?\d*))",
            R"(current_now:\s*(-?\d+\.?\d*)\s*mA)"
        };
        return patterns;
    }

    static const std::string& getCurrentPattern() {
        static const std::string pattern = R"(battery current:\s*(-?\d+\.?\d*)\s*mA)";
        return pattern;
    }

    // ========== 应用耗电提取模式 ==========
    static const std::string& getAppPowerPattern() {
        static const std::string pattern =
            R"(App power usage:.*?Package:\s*([^\n]+).*?Power:\s*(\d+\.?\d*)\s*mAh)";
        return pattern;
    }

    // ========== Battery History 充电事件模式 ==========
    static const std::string& getBatteryHistoryPattern() {
        static const std::string pattern =
            R"((\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}).*?battery level:\s*(\d+).*?status:\s*(\w+))";
        return pattern;
    }

    // ========== SN序列号提取模式 ==========
    static const std::string& getSNPattern() {
        static const std::string pattern = R"(ro\.serialno=\s*([A-Za-z0-9]+))";
        return pattern;
    }

    // ========== IMEI提取模式 ==========
    static const std::string& getIMEIPattern() {
        static const std::string pattern = R"(IMEI:\s*(\d{15}))";
        return pattern;
    }

    // ========== 辅助方法：验证日期有效性 ==========
    static bool isValidDate(int year, int month, int day) {
        if (year < 2000 || year > 2030) return false;
        if (month < 1 || month > 12) return false;
        if (day < 1 || day > 31) return false;

        // 简单的月份天数验证
        if (month == 2 && day > 29) return false;
        if ((month == 4 || month == 6 || month == 9 || month == 11) && day > 30) return false;

        return true;
    }

    // ========== 辅助方法：验证容量有效性 ==========
    static bool isValidCapacity(int capacity_mah) {
        // 手机电池容量通常在1000-6000mAh
        // 笔记本电池容量通常在20000-100000mAh
        return capacity_mah > 0 && capacity_mah < 100000;
    }

    // ========== 辅助方法：验证循环次数有效性 ==========
    static bool isValidCycleCount(int cycles) {
        // 循环次数通常在0-2000次
        return cycles >= 0 && cycles <= 2000;
    }

    // ========== 辅助方法：验证温度有效性 ==========
    static bool isValidTemperature(float temp_celsius) {
        // 电池温度通常在-20°C到60°C
        return temp_celsius >= -20.0f && temp_celsius <= 60.0f;
    }
};

/**
 * 正则模式管理器
 * 支持动态添加模式和社区反馈
 */
class RegexPatternManager {
public:
    static RegexPatternManager& getInstance();

    // 添加新模式
    void addCapacityPattern(const std::string& pattern);
    void addCycleCountPattern(const std::string& pattern);
    void addDatePattern(const std::string& pattern);
    void addTemperaturePattern(const std::string& pattern);
    void addCustomPattern(const std::string& category, const std::string& pattern);

    // 获取所有模式
    std::vector<std::string> getAllCapacityPatterns();
    std::vector<std::string> getAllCycleCountPatterns();
    std::vector<std::string> getAllDatePatterns();
    std::vector<std::string> getAllTemperaturePatterns();

    // 从社区反馈加载新模式
    bool loadFromCommunityFeedback(const std::string& json_content);

    // 导出模式配置
    std::string exportToJson();

    // 记录模式匹配成功/失败统计
    void recordPatternMatch(const std::string& pattern, bool success);

    // 获取模式效率统计
    std::map<std::string, std::pair<int, int>> getPatternStats();

private:
    RegexPatternManager();
    std::map<std::string, std::vector<std::string>> custom_patterns_;
    std::map<std::string, std::pair<int, int>> pattern_stats_;  // <success_count, fail_count>
};

} // namespace digiguide::core