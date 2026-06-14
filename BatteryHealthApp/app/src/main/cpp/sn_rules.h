#pragma once

#include "result_types.h"
#include <map>
#include <string>

namespace digiguide::core {

// Apple年份-半年结构
struct AppleYearHalf {
    int year;
    int half;  // 1=上半年, 2=下半年
};

// Apple SN编码规则
struct AppleSNRules {
    // 半年代码映射表（确定性）
    static const std::map<char, AppleYearHalf>& getYearMap() {
        static const std::map<char, AppleYearHalf> YEAR_MAP = {
            {'C', {2010, 1}}, {'D', {2010, 2}},
            {'F', {2011, 1}}, {'G', {2011, 2}},
            {'H', {2012, 1}}, {'J', {2012, 2}},
            {'K', {2013, 1}}, {'L', {2013, 2}},
            {'M', {2014, 1}}, {'N', {2014, 2}},
            {'P', {2015, 1}}, {'Q', {2015, 2}},
            {'R', {2016, 1}}, {'S', {2016, 2}},
            {'T', {2017, 1}}, {'V', {2017, 2}},
            {'W', {2018, 1}}, {'X', {2018, 2}},
            {'Y', {2019, 1}}, {'Z', {2019, 2}},
            {'0', {2020, 1}}, {'1', {2020, 2}},
            {'2', {2021, 1}}, {'3', {2021, 2}},
            {'4', {2022, 1}}, {'5', {2022, 2}},
            {'6', {2023, 1}}, {'7', {2023, 2}},
            {'8', {2024, 1}}, {'9', {2024, 2}},
            {'A', {2025, 1}}, {'B', {2025, 2}},
            {'C', {2026, 1}}, {'D', {2026, 2}}
        };
        return YEAR_MAP;
    }

    // 周次编码映射表（确定性）
    static const std::map<char, int>& getWeekMap() {
        static const std::map<char, int> WEEK_MAP = {
            {'1', 1},  {'2', 2},  {'3', 3},  {'4', 4},  {'5', 5},
            {'6', 6},  {'7', 7},  {'8', 8},  {'9', 9},
            {'C', 12}, {'D', 13}, {'F', 15}, {'G', 16},
            {'H', 17}, {'J', 18}, {'K', 19}, {'L', 20},
            {'M', 21}, {'N', 22}, {'P', 23}, {'Q', 24},
            {'R', 25}, {'S', 26}, {'T', 27}, {'V', 28},
            {'W', 29}, {'X', 30}, {'Y', 31}
        };
        return WEEK_MAP;
    }

    // SN长度
    static constexpr int SN_LENGTH = 12;

    // 年份位置
    static constexpr int YEAR_POSITION = 3;  // 第4位（索引3）

    // 周次位置
    static constexpr int WEEK_POSITION = 4;  // 第5位（索引4）
};

// Samsung SN编码规则
struct SamsungSNRules {
    // 年份编码映射表
    static const std::map<char, int>& getYearMap() {
        static const std::map<char, int> YEAR_MAP = {
            {'R', 2023}, {'S', 2024}, {'T', 2025},
            {'U', 2026}, {'V', 2027}, {'W', 2028}
        };
        return YEAR_MAP;
    }

    // 月份编码映射表
    static const std::map<char, int>& getMonthMap() {
        static const std::map<char, int> MONTH_MAP = {
            {'1', 1},  {'2', 2},  {'3', 3},  {'4', 4},  {'5', 5},
            {'6', 6},  {'7', 7},  {'8', 8},  {'9', 9},
            {'A', 10}, {'B', 11}, {'C', 12}
        };
        return MONTH_MAP;
    }

    // 年份位置（倒数第7位）
    static constexpr int YEAR_OFFSET_FROM_END = 7;

    // 月份位置（倒数第6位）
    static constexpr int MONTH_OFFSET_FROM_END = 6;
};

// Huawei SN编码规则
struct HuaweiSNRules {
    // 年份位置（第6-7位，索引5-6）
    static constexpr int YEAR_START = 5;
    static constexpr int YEAR_LENGTH = 2;

    // 周次位置（第8-9位，索引7-8）
    static constexpr int WEEK_START = 7;
    static constexpr int WEEK_LENGTH = 2;
};

// Honor SN编码规则（继承华为）
struct HonorSNRules : HuaweiSNRules {};

// Xiaomi SN编码规则
struct XiaomiSNRules {
    // 多种格式，需根据长度判断
    // IMEI格式：15位
    static constexpr int IMEI_LENGTH = 15;

    // 自定义格式：第3-4位含年份信息
    static constexpr int YEAR_START = 2;
    static constexpr int YEAR_LENGTH = 2;
};

// OPPO SN编码规则
struct OPPOSNRules {
    // 年份+月份编码位置（第4-5位）
    static constexpr int CODE_START = 3;
    static constexpr int CODE_LENGTH = 2;
};

// Vivo SN编码规则
struct VivoSNRules {
    // 年份位置（第5-6位）
    static constexpr int YEAR_START = 4;
    static constexpr int YEAR_LENGTH = 2;

    // 周次/月份位置（第7-8位）
    static constexpr int WEEK_START = 6;
    static constexpr int WEEK_LENGTH = 2;
};

// 品牌SN规则汇总
struct BrandSNRules {
    static std::string getFormatDescription(Brand brand) {
        switch (brand) {
            case Brand::APPLE:
                return "Apple SN: 12位，第4位=半年代码，第5位=周次";
            case Brand::SAMSUNG:
                return "Samsung SN: 倒数第7位=年份，倒数第6位=月份";
            case Brand::HUAWEI:
                return "Huawei SN: 第6-7位=年份后两位，第8-9位=周次";
            case Brand::HONOR:
                return "Honor SN: 与华为类似，第6-7位=年份，第8-9位=周次";
            case Brand::XIAOMI:
                return "Xiaomi SN: 多种格式，IMEI或自定义编码";
            case Brand::OPPO:
                return "OPPO SN: 第4-5位含年份+月份编码";
            case Brand::VIVO:
                return "vivo SN: 第5-6位=年份，第7-8位=周次/月份";
            case Brand::LENOVO:
                return "Lenovo SN: ThinkPad格式，前4位=机型，第5位=年份";
            case Brand::HP:
                return "HP SN: 第3-4位=年份和地区，后续=周次";
            case Brand::ASUS:
                return "ASUS SN: 第2位=年份代码，第3位=月份代码";
            case Brand::DELL:
                return "Dell SN: 服务标签5-7位，需官方API查询";
            case Brand::APPLE_MAC:
                return "Apple Mac SN: 与iPhone类似，12位格式";
            default:
                return "未知品牌SN格式";
        }
    }

    static int getDefaultWarrantyMonths(Brand brand) {
        switch (brand) {
            case Brand::APPLE:
            case Brand::APPLE_MAC:
            case Brand::SAMSUNG:
            case Brand::HUAWEI:
            case Brand::HONOR:
            case Brand::XIAOMI:
            case Brand::OPPO:
            case Brand::VIVO:
                return 12;  // 1年
            case Brand::LENOVO:
            case Brand::HP:
            case Brand::ASUS:
            case Brand::DELL:
                return 12;  // 1年（商务机型可能更长）
            default:
                return 12;
        }
    }
};

} // namespace digiguide::core