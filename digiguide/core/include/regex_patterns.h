#pragma once

#include <vector>
#include <string>

namespace digiguide::core {

// 正则模式库 - 30+模式用于bugreport解析
struct RegexPatterns {

    // ========== 容量提取模式（按优先级排序）==========
    static const std::vector<std::string>& getCapacityPatterns() {
        static const std::vector<std::string> patterns = {
            R"(Min learned battery capacity:\s*(\d+)\s*mAh)",
            R"(full charge capacity:\s*(\d+)\s*mAh)",
            R"(learned capacity:\s*(\d+)\s*mAh)",
            R"(FullCapacity:\s*(\d+))",
            R"(battery capacity:\s*(\d+)\s*mAh)",
            R"(Capacity:\s*(\d+)\s*mAh)"
        };
        return patterns;
    }

    // ========== 循环次数提取模式（9种厂商格式）==========
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
            R"(cycle_count:\s*(\d+))"
        };
        return patterns;
    }

    // ========== 制造日期提取模式（16种格式）==========
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
            R"(battery_manufacture_time:\s*(\d{4})(\d{2})(\d{2}))"
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
    static const std::string& getTemperaturePattern() {
        static const std::string pattern = R"(battery temperature:\s*(\d+\.?\d*)\s*°?C)";
        return pattern;
    }

    static const std::string& getTemperaturePatternAlt() {
        static const std::string pattern = R"(BatteryTemp:\s*(\d+\.?\d*))";
        return pattern;
    }

    // ========== 设计容量提取模式 ==========
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
    static const std::string& getVoltagePattern() {
        static const std::string pattern = R"(battery voltage:\s*(\d+\.?\d*)\s*mV)";
        return pattern;
    }

    // ========== 电流提取模式 ==========
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
};

} // namespace digiguide::core