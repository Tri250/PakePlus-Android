#pragma once

#include <string>
#include <optional>
#include <vector>

namespace digiguide::core {

// 置信度级别
enum class ConfidenceLevel {
    HIGH,    // 高置信度（>=4个因子）
    MEDIUM,  // 中置信度（>=2个因子）
    LOW,     // 低置信度（>=1个因子）
    NONE     // 无置信度（无可用因子）
};

// SN解码状态
enum class SNDecodeStatus {
    SUCCESS,   // 完全成功
    PARTIAL,   // 部分成功
    FAILED     // 完全失败
};

// 电池类型
enum class BatteryType {
    LiPo,      // 锂聚合物（手机常用）
    LiIon,     // 锂离子
    LiFePO4,   // 磷酸铁锂
    UNKNOWN
};

// 品牌
enum class Brand {
    XIAOMI,
    HUAWEI,
    OPPO,
    VIVO,
    APPLE,
    HONOR,
    SAMSUNG,
    LENOVO,
    HP,
    ASUS,
    DELL,
    APPLE_MAC,
    UNKNOWN
};

// 品牌中文名映射
inline std::string brandToChinese(Brand brand) {
    switch (brand) {
        case Brand::XIAOMI: return "小米";
        case Brand::HUAWEI: return "华为";
        case Brand::OPPO: return "OPPO";
        case Brand::VIVO: return "vivo";
        case Brand::APPLE: return "苹果";
        case Brand::HONOR: return "荣耀";
        case Brand::SAMSUNG: return "三星";
        case Brand::LENOVO: return "联想";
        case Brand::HP: return "惠普";
        case Brand::ASUS: return "华硕";
        case Brand::DELL: return "戴尔";
        case Brand::APPLE_MAC: return "苹果电脑";
        default: return "未知";
    }
}

// 品牌英文名映射
inline std::string brandToString(Brand brand) {
    switch (brand) {
        case Brand::XIAOMI: return "XIAOMI";
        case Brand::HUAWEI: return "HUAWEI";
        case Brand::OPPO: return "OPPO";
        case Brand::VIVO: return "VIVO";
        case Brand::APPLE: return "APPLE";
        case Brand::HONOR: return "HONOR";
        case Brand::SAMSUNG: return "SAMSUNG";
        case Brand::LENOVO: return "LENOVO";
        case Brand::HP: return "HP";
        case Brand::ASUS: return "ASUS";
        case Brand::DELL: return "DELL";
        case Brand::APPLE_MAC: return "APPLE_MAC";
        default: return "UNKNOWN";
    }
}

// 解析详情
struct ParseDetail {
    std::vector<std::string> extracted_fields;  // 成功提取的字段
    std::vector<std::string> missing_fields;    // 缺失的字段
    std::vector<std::string> parse_warnings;    // 解析警告
};

} // namespace digiguide::core