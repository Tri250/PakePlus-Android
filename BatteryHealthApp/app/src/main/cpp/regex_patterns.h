#pragma once

#include <vector>
#include <string>
#include <unordered_map>
#include <algorithm>
#include <cctype>

namespace digiguide::core {

// 品牌枚举
enum class BrandType {
    UNKNOWN,
    XIAOMI,      // 小米/Redmi
    HUAWEI,      // 华为
    HONOR,       // 荣耀
    OPPO,        // OPPO
    VIVO,        // vivo/iQOO
    ONEPLUS,     // 一加
    SAMSUNG,     // 三星
    APPLE,       // 苹果
    REALME,      // Realme
    MEIZU,       // 魅族
    NUBIA        // 努比亚
};

// 品牌配置结构 - 每个品牌的专属解析规则
struct BrandConfig {
    BrandType type;
    std::string name;
    std::string name_cn;
    
    // 容量提取模式（按优先级排序）
    std::vector<std::string> capacityPatterns;
    
    // 设计容量提取模式
    std::vector<std::string> designCapacityPatterns;
    
    // 循环次数提取模式
    std::vector<std::string> cycleCountPatterns;
    
    // 温度提取模式
    std::vector<std::string> temperaturePatterns;
    
    // 电压提取模式
    std::vector<std::string> voltagePatterns;
    
    // 电池健康度代码提取模式（部分品牌）
    std::vector<std::string> healthCodePatterns;
};

// 正则模式库 - 支持 50+ 模式，覆盖主流品牌
struct RegexPatterns {

    // ========== 品牌配置表 ==========
    static const std::unordered_map<BrandType, BrandConfig>& getBrandConfigs() {
        static const std::unordered_map<BrandType, BrandConfig> configs = {
            // 小米/Redmi 配置
            {BrandType::XIAOMI, {
                BrandType::XIAOMI, "Xiaomi", "小米",
                // 容量模式
                {
                    R"(Min learned battery capacity:\s*(\d+)\s*mAh)",
                    R"(MF_05[=:\s]+(\d+))",                    // 小米 MF 格式
                    R"(fc[=:\s]+(\d+))",                       // healthd 格式
                    R"(charge_capacity[=:\s]+(\d+))",
                    R"(last_full_capacity[=:\s]+(\d+))",
                    R"(full charge capacity:\s*(\d+)\s*mAh)",
                    R"(learned capacity:\s*(\d+)\s*mAh)",
                    R"(QG_01[=:\s]+(\d+))",                    // 小米 QG 格式
                    R"(FullCapacity:\s*(\d+))",
                    R"(battery capacity:\s*(\d+)\s*mAh)",
                    R"(Capacity:\s*(\d+)\s*mAh)"
                },
                // 设计容量模式
                {
                    R"(DesignCapacity:\s*(\d+))",
                    R"(MF_08[=:\s]+(\d+))",
                    R"(nominal_capacity[=:\s]+(\d+))",
                    R"(rated_capacity[=:\s]+(\d+))"
                },
                // 循环次数模式
                {
                    R"(MF_06[=:\s]+(\d+))",                    // 小米 MF 格式
                    R"(cc[=:\s]+(\d+))",                       // healthd 格式
                    R"(battery cycle count:\s*(\d+))",
                    R"(cycle count:\s*(\d+))",
                    R"(charge cycles:\s*(\d+))",
                    R"(battery cycles:\s*(\d+))",
                    R"(CycleCount:\s*(\d+))",
                    R"(BatteryCycleCount:\s*(\d+))",
                    R"(ChargingCycleCount:\s*(\d+))",
                    R"(battery_age_cycles:\s*(\d+))",
                    R"(cycle_count:\s*(\d+))"
                },
                // 温度模式
                {
                    R"(t[=:\s]+(\d+\.?\d*))",                  // healthd 格式
                    R"(battery temperature:\s*(\d+\.?\d*)\s*°?C)",
                    R"(BatteryTemp:\s*(\d+\.?\d*))",
                    R"(temp[=:\s]+(\d+\.?\d*))"
                },
                // 电压模式
                {
                    R"(v[=:\s]+(\d+))",                        // healthd 格式
                    R"(battery voltage:\s*(\d+\.?\d*)\s*mV)",
                    R"(voltage[=:\s]+(\d+))"
                },
                // 健康度代码
                {
                    R"(h[=:\s]+(\d+))"                         // healthd 格式
                }
            }},
            
            // 华为配置
            {BrandType::HUAWEI, {
                BrandType::HUAWEI, "Huawei", "华为",
                // 容量模式 - healthd 格式为主
                {
                    R"(fc[=:\s]+(\d+))",                       // healthd: fc=4562
                    R"(Min learned battery capacity:\s*(\d+)\s*mAh)",
                    R"(charge_capacity[=:\s]+(\d+))",
                    R"(last_full_capacity[=:\s]+(\d+))",
                    R"(full charge capacity:\s*(\d+)\s*mAh)",
                    R"(FullCapacity:\s*(\d+))",
                    R"(battery capacity:\s*(\d+)\s*mAh)"
                },
                // 设计容量模式
                {
                    R"(DesignCapacity:\s*(\d+))",
                    R"(nominal_capacity[=:\s]+(\d+))",
                    R"(rated_capacity[=:\s]+(\d+))"
                },
                // 循环次数模式
                {
                    R"(cc[=:\s]+(\d+))",                       // healthd: cc=1200
                    R"(battery cycle count:\s*(\d+))",
                    R"(cycle count:\s*(\d+))",
                    R"(charge cycles:\s*(\d+))",
                    R"(CycleCount:\s*(\d+))"
                },
                // 温度模式
                {
                    R"(t[=:\s]+(\d+\.?\d*))",                  // healthd: t=27.0
                    R"(battery temperature:\s*(\d+\.?\d*)\s*°?C)",
                    R"(BatteryTemp:\s*(\d+\.?\d*))"
                },
                // 电压模式
                {
                    R"(v[=:\s]+(\d+))",                        // healthd: v=4356
                    R"(battery voltage:\s*(\d+\.?\d*)\s*mV)"
                },
                // 健康度代码
                {
                    R"(h[=:\s]+(\d+))"                         // healthd: h=2
                }
            }},
            
            // 荣耀配置（继承华为格式）
            {BrandType::HONOR, {
                BrandType::HONOR, "Honor", "荣耀",
                // 容量模式 - 与华为相同，healthd 格式为主
                {
                    R"(fc[=:\s]+(\d+))",
                    R"(Min learned battery capacity:\s*(\d+)\s*mAh)",
                    R"(charge_capacity[=:\s]+(\d+))",
                    R"(last_full_capacity[=:\s]+(\d+))",
                    R"(full charge capacity:\s*(\d+)\s*mAh)",
                    R"(FullCapacity:\s*(\d+))",
                    R"(battery capacity:\s*(\d+)\s*mAh)"
                },
                // 设计容量模式
                {
                    R"(DesignCapacity:\s*(\d+))",
                    R"(nominal_capacity[=:\s]+(\d+))",
                    R"(rated_capacity[=:\s]+(\d+))"
                },
                // 循环次数模式
                {
                    R"(cc[=:\s]+(\d+))",
                    R"(battery cycle count:\s*(\d+))",
                    R"(cycle count:\s*(\d+))",
                    R"(charge cycles:\s*(\d+))",
                    R"(CycleCount:\s*(\d+))"
                },
                // 温度模式
                {
                    R"(t[=:\s]+(\d+\.?\d*))",
                    R"(battery temperature:\s*(\d+\.?\d*)\s*°?C)",
                    R"(BatteryTemp:\s*(\d+\.?\d*))"
                },
                // 电压模式
                {
                    R"(v[=:\s]+(\d+))",
                    R"(battery voltage:\s*(\d+\.?\d*)\s*mV)"
                },
                // 健康度代码
                {
                    R"(h[=:\s]+(\d+))"
                }
            }},
            
            // OPPO 配置
            {BrandType::OPPO, {
                BrandType::OPPO, "OPPO", "OPPO",
                // 容量模式
                {
                    R"(fc[=:\s]+(\d+))",
                    R"(Min learned battery capacity:\s*(\d+)\s*mAh)",
                    R"(QG_01[=:\s]+(\d+))",                    // OPPO QG 格式
                    R"(charge_capacity[=:\s]+(\d+))",
                    R"(last_full_capacity[=:\s]+(\d+))",
                    R"(full charge capacity:\s*(\d+)\s*mAh)",
                    R"(FullCapacity:\s*(\d+))",
                    R"(battery capacity:\s*(\d+)\s*mAh)"
                },
                // 设计容量模式
                {
                    R"(DesignCapacity:\s*(\d+))",
                    R"(QG_02[=:\s]+(\d+))",
                    R"(nominal_capacity[=:\s]+(\d+))"
                },
                // 循环次数模式
                {
                    R"(cc[=:\s]+(\d+))",
                    R"(QG_03[=:\s]+(\d+))",
                    R"(battery cycle count:\s*(\d+))",
                    R"(cycle count:\s*(\d+))",
                    R"(charge cycles:\s*(\d+))"
                },
                // 温度模式
                {
                    R"(t[=:\s]+(\d+\.?\d*))",
                    R"(battery temperature:\s*(\d+\.?\d*)\s*°?C)",
                    R"(BatteryTemp:\s*(\d+\.?\d*))"
                },
                // 电压模式
                {
                    R"(v[=:\s]+(\d+))",
                    R"(battery voltage:\s*(\d+\.?\d*)\s*mV)"
                },
                {}
            }},
            
            // vivo/iQOO 配置
            {BrandType::VIVO, {
                BrandType::VIVO, "vivo", "vivo",
                // 容量模式
                {
                    R"(fc[=:\s]+(\d+))",
                    R"(Min learned battery capacity:\s*(\d+)\s*mAh)",
                    R"(charge_capacity[=:\s]+(\d+))",
                    R"(last_full_capacity[=:\s]+(\d+))",
                    R"(full charge capacity:\s*(\d+)\s*mAh)",
                    R"(FullCapacity:\s*(\d+))",
                    R"(battery capacity:\s*(\d+)\s*mAh)",
                    R"(Capacity:\s*(\d+)\s*mAh)"
                },
                // 设计容量模式
                {
                    R"(DesignCapacity:\s*(\d+))",
                    R"(nominal_capacity[=:\s]+(\d+))",
                    R"(rated_capacity[=:\s]+(\d+))"
                },
                // 循环次数模式
                {
                    R"(cc[=:\s]+(\d+))",
                    R"(battery cycle count:\s*(\d+))",
                    R"(cycle count:\s*(\d+))",
                    R"(charge cycles:\s*(\d+))",
                    R"(CycleCount:\s*(\d+))"
                },
                // 温度模式
                {
                    R"(t[=:\s]+(\d+\.?\d*))",
                    R"(battery temperature:\s*(\d+\.?\d*)\s*°?C)",
                    R"(BatteryTemp:\s*(\d+\.?\d*))"
                },
                // 电压模式
                {
                    R"(v[=:\s]+(\d+))",
                    R"(battery voltage:\s*(\d+\.?\d*)\s*mV)"
                },
                {}
            }},
            
            // 一加配置
            {BrandType::ONEPLUS, {
                BrandType::ONEPLUS, "OnePlus", "一加",
                // 容量模式
                {
                    R"(fc[=:\s]+(\d+))",
                    R"(Min learned battery capacity:\s*(\d+)\s*mAh)",
                    R"(charge_capacity[=:\s]+(\d+))",
                    R"(last_full_capacity[=:\s]+(\d+))",
                    R"(full charge capacity:\s*(\d+)\s*mAh)",
                    R"(FullCapacity:\s*(\d+))",
                    R"(battery capacity:\s*(\d+)\s*mAh)"
                },
                // 设计容量模式
                {
                    R"(DesignCapacity:\s*(\d+))",
                    R"(nominal_capacity[=:\s]+(\d+))"
                },
                // 循环次数模式
                {
                    R"(cc[=:\s]+(\d+))",
                    R"(battery cycle count:\s*(\d+))",
                    R"(cycle count:\s*(\d+))",
                    R"(charge cycles:\s*(\d+))"
                },
                // 温度模式
                {
                    R"(t[=:\s]+(\d+\.?\d*))",
                    R"(battery temperature:\s*(\d+\.?\d*)\s*°?C)",
                    R"(BatteryTemp:\s*(\d+\.?\d*))"
                },
                // 电压模式
                {
                    R"(v[=:\s]+(\d+))",
                    R"(battery voltage:\s*(\d+\.?\d*)\s*mV)"
                },
                {}
            }},
            
            // 三星配置
            {BrandType::SAMSUNG, {
                BrandType::SAMSUNG, "Samsung", "三星",
                // 容量模式
                {
                    R"(fc[=:\s]+(\d+))",
                    R"(Min learned battery capacity:\s*(\d+)\s*mAh)",
                    R"(charge_capacity[=:\s]+(\d+))",
                    R"(last_full_capacity[=:\s]+(\d+))",
                    R"(full charge capacity:\s*(\d+)\s*mAh)",
                    R"(FullCapacity:\s*(\d+))",
                    R"(battery capacity:\s*(\d+)\s*mAh)"
                },
                // 设计容量模式
                {
                    R"(DesignCapacity:\s*(\d+))",
                    R"(nominal_capacity[=:\s]+(\d+))"
                },
                // 循环次数模式
                {
                    R"(cc[=:\s]+(\d+))",
                    R"(battery cycle count:\s*(\d+))",
                    R"(cycle count:\s*(\d+))",
                    R"(charge cycles:\s*(\d+))"
                },
                // 温度模式
                {
                    R"(t[=:\s]+(\d+\.?\d*))",
                    R"(battery temperature:\s*(\d+\.?\d*)\s*°?C)",
                    R"(BatteryTemp:\s*(\d+\.?\d*))"
                },
                // 电压模式
                {
                    R"(v[=:\s]+(\d+))",
                    R"(battery voltage:\s*(\d+\.?\d*)\s*mV)"
                },
                {}
            }}
        };
        return configs;
    }
    
    // ========== 通用容量提取模式（所有品牌兜底）==========
    static const std::vector<std::string>& getCapacityPatterns() {
        static const std::vector<std::string> patterns = {
            // healthd 格式（荣耀/华为/小米等）
            R"(fc[=:\s]+(\d+))",
            // 小米 MF 格式
            R"(MF_05[=:\s]+(\d+))",
            // 通用格式
            R"(Min learned battery capacity:\s*(\d+)\s*mAh)",
            R"(charge_capacity[=:\s]+(\d+))",
            R"(last_full_capacity[=:\s]+(\d+))",
            R"(full charge capacity:\s*(\d+)\s*mAh)",
            R"(learned capacity:\s*(\d+)\s*mAh)",
            // QG 格式（OPPO/小米）
            R"(QG_01[=:\s]+(\d+))",
            // 其他格式
            R"(FullCapacity:\s*(\d+))",
            R"(battery capacity:\s*(\d+)\s*mAh)",
            R"(Capacity:\s*(\d+)\s*mAh)",
            R"(current_capacity[=:\s]+(\d+))",
            R"(actual_capacity[=:\s]+(\d+))"
        };
        return patterns;
    }

    // ========== 通用循环次数提取模式（所有品牌兜底）==========
    static const std::vector<std::string>& getCycleCountPatterns() {
        static const std::vector<std::string> patterns = {
            // healthd 格式（荣耀/华为）
            R"(cc[=:\s]+(\d+))",
            // 小米 MF 格式
            R"(MF_06[=:\s]+(\d+))",
            // QG 格式（OPPO）
            R"(QG_03[=:\s]+(\d+))",
            // 通用格式
            R"(battery cycle count:\s*(\d+))",
            R"(cycle count:\s*(\d+))",
            R"(charge cycles:\s*(\d+))",
            R"(battery cycles:\s*(\d+))",
            R"(CycleCount:\s*(\d+))",
            R"(BatteryCycleCount:\s*(\d+))",
            R"(ChargingCycleCount:\s*(\d+))",
            R"(battery_age_cycles:\s*(\d+))",
            R"(cycle_count:\s*(\d+))",
            R"(charge_cycle_count[=:\s]+(\d+))"
        };
        return patterns;
    }
    
    // ========== 通用设计容量提取模式 ==========
    static const std::vector<std::string>& getDesignCapacityPatterns() {
        static const std::vector<std::string> patterns = {
            R"(DesignCapacity:\s*(\d+))",
            R"(MF_08[=:\s]+(\d+))",
            R"(QG_02[=:\s]+(\d+))",
            R"(nominal_capacity[=:\s]+(\d+))",
            R"(rated_capacity[=:\s]+(\d+))",
            R"(design_capacity[=:\s]+(\d+))",
            R"(max_capacity[=:\s]+(\d+))"
        };
        return patterns;
    }

    // ========== 通用温度提取模式 ==========
    static const std::vector<std::string>& getTemperaturePatterns() {
        static const std::vector<std::string> patterns = {
            // healthd 格式
            R"(t[=:\s]+(\d+\.?\d*))",
            // 通用格式
            R"(battery temperature:\s*(\d+\.?\d*)\s*°?C)",
            R"(BatteryTemp:\s*(\d+\.?\d*))",
            R"(temp[=:\s]+(\d+\.?\d*))",
            R"(temperature[=:\s]+(\d+\.?\d*))",
            R"(battery_temp[=:\s]+(\d+\.?\d*))"
        };
        return patterns;
    }
    
    // ========== 通用电压提取模式 ==========
    static const std::vector<std::string>& getVoltagePatterns() {
        static const std::vector<std::string> patterns = {
            // healthd 格式
            R"(v[=:\s]+(\d+))",
            // 通用格式
            R"(battery voltage:\s*(\d+\.?\d*)\s*mV)",
            R"(voltage[=:\s]+(\d+))",
            R"(voltage_mv[=:\s]+(\d+))"
        };
        return patterns;
    }
    
    // ========== healthd 完整行匹配模式 ==========
    static const std::string& getHealthdPattern() {
        static const std::string pattern = 
            R"(healthd:\s*battery\s+l[=:\s]+(\d+)\s+v[=:\s]+(\d+)\s*t[=:\s]+(\d+\.?\d*)\s*h[=:\s]+(\d+)\s*st[=:\s]+(\d+)\s*c[=:\s]+(-?\d+)\s*fc[=:\s]+(\d+)\s*cc[=:\s]+(\d+))";
        return pattern;
    }
    
    // ========== healthd 简化匹配模式（更宽松）==========
    static const std::string& getHealthdSimplePattern() {
        static const std::string pattern = R"(healthd:.*?fc[=:\s]+(\d+).*?cc[=:\s]+(\d+))";
        return pattern;
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

    // ========== 旧版温度提取模式（兼容）==========
    static const std::string& getTemperaturePattern() {
        static const std::string pattern = R"(battery temperature:\s*(\d+\.?\d*)\s*°?C)";
        return pattern;
    }

    static const std::string& getTemperaturePatternAlt() {
        static const std::string pattern = R"(BatteryTemp:\s*(\d+\.?\d*))";
        return pattern;
    }

    // ========== 旧版设计容量提取模式（兼容）==========
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

    // ========== 旧版电压提取模式（兼容）==========
    static const std::string& getVoltagePattern() {
        static const std::string pattern = R"(battery voltage:\s*(\d+\.?\d*)\s*mV)";
        return pattern;
    }

    // ========== 旧版电流提取模式（兼容）==========
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
    
    // ========== 辅助方法：从品牌字符串识别品牌类型 ==========
    static BrandType detectBrandFromString(const std::string& brand_str) {
        std::string lower_brand = brand_str;
        std::transform(lower_brand.begin(), lower_brand.end(), lower_brand.begin(), ::tolower);
        
        if (lower_brand.find("xiaomi") != std::string::npos || 
            lower_brand.find("redmi") != std::string::npos ||
            lower_brand.find("mi") != std::string::npos) {
            return BrandType::XIAOMI;
        }
        if (lower_brand.find("huawei") != std::string::npos) {
            return BrandType::HUAWEI;
        }
        if (lower_brand.find("honor") != std::string::npos) {
            return BrandType::HONOR;
        }
        if (lower_brand.find("oppo") != std::string::npos) {
            return BrandType::OPPO;
        }
        if (lower_brand.find("vivo") != std::string::npos || 
            lower_brand.find("iqoo") != std::string::npos) {
            return BrandType::VIVO;
        }
        if (lower_brand.find("oneplus") != std::string::npos || 
            lower_brand.find("one plus") != std::string::npos) {
            return BrandType::ONEPLUS;
        }
        if (lower_brand.find("samsung") != std::string::npos) {
            return BrandType::SAMSUNG;
        }
        if (lower_brand.find("apple") != std::string::npos) {
            return BrandType::APPLE;
        }
        if (lower_brand.find("realme") != std::string::npos) {
            return BrandType::REALME;
        }
        if (lower_brand.find("meizu") != std::string::npos) {
            return BrandType::MEIZU;
        }
        if (lower_brand.find("nubia") != std::string::npos) {
            return BrandType::NUBIA;
        }
        
        return BrandType::UNKNOWN;
    }
};

} // namespace digiguide::core