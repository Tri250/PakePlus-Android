#include "sn_decoder.h"
#include "sn_rules.h"
#include <regex>
#include <algorithm>
#include <sstream>

namespace digiguide::core {

// ========== SN解码主入口 ==========

SNDecodeResult SNDecoder::decode(const std::string& sn) {
    Brand brand = identifyBrand(sn);
    return decode(sn, brand);
}

SNDecodeResult SNDecoder::decode(const std::string& sn, Brand brand) {
    switch (brand) {
        case Brand::APPLE:
            return decodeApple(sn);
        case Brand::APPLE_MAC:
            return decodeApple(sn);  // Mac使用相同规则
        case Brand::SAMSUNG:
            return decodeSamsung(sn);
        case Brand::HUAWEI:
            return decodeHuawei(sn);
        case Brand::HONOR:
            return decodeHonor(sn);
        case Brand::XIAOMI:
            return decodeXiaomi(sn);
        case Brand::OPPO:
            return decodeOPPO(sn);
        case Brand::VIVO:
            return decodeVivo(sn);
        case Brand::LENOVO:
            return decodeLenovo(sn);
        case Brand::HP:
            return decodeHP(sn);
        case Brand::ASUS:
            return decodeASUS(sn);
        case Brand::DELL:
            return decodeDell(sn);
        default:
            SNDecodeResult result;
            result.brand = Brand::UNKNOWN;
            result.raw_sn = sn;
            result.status = SNDecodeStatus::FAILED;
            result.error_message = "无法识别的品牌";
            return result;
    }
}

// ========== 品牌识别 ==========

Brand SNDecoder::identifyBrand(const std::string& sn) {
    // 清理SN（去除空格、特殊字符）
    std::string clean_sn = sn;
    std::transform(clean_sn.begin(), clean_sn.end(), clean_sn.begin(),
                   [](char c) { return std::toupper(c); });

    // Apple: 12位字母数字，特定格式
    if (clean_sn.length() == 12) {
        // 检查是否符合Apple格式（第4位是年份编码）
        const auto& yearMap = AppleSNRules::getYearMap();
        if (yearMap.find(clean_sn[3]) != yearMap.end()) {
            return Brand::APPLE;
        }
    }

    // Samsung: 倒数第7位是年份编码
    if (clean_sn.length() >= 10) {
        const auto& yearMap = SamsungSNRules::getYearMap();
        size_t year_pos = clean_sn.length() - SamsungSNRules::YEAR_OFFSET_FROM_END;
        if (year_pos < clean_sn.length() && yearMap.find(clean_sn[year_pos]) != yearMap.end()) {
            return Brand::SAMSUNG;
        }
    }

    // Huawei/Honor: 第6-7位是年份后两位
    if (clean_sn.length() >= 10 && clean_sn.length() <= 20) {
        if (clean_sn.length() >= 9) {
            std::string year_part = clean_sn.substr(HuaweiSNRules::YEAR_START, 2);
            // 检查是否是年份（20-26）
            if (year_part >= "20" && year_part <= "26") {
                // 尝试区分华为和荣耀（需要更多信息）
                return Brand::HUAWEI;  // 默认华为
            }
        }
    }

    // Xiaomi: IMEI格式15位，或自定义格式
    if (clean_sn.length() == 15 && std::all_of(clean_sn.begin(), clean_sn.end(), ::isdigit)) {
        return Brand::XIAOMI;
    }

    // OPPO: 特定格式
    if (clean_sn.length() >= 10 && clean_sn.length() <= 15) {
        // OPPO SN通常以特定前缀开始
        return Brand::OPPO;  // 需要更多特征
    }

    // Vivo: 特定格式
    if (clean_sn.length() >= 10 && clean_sn.length() <= 15) {
        return Brand::VIVO;  // 需要更多特征
    }

    // PC品牌识别
    if (clean_sn.length() >= 8) {
        // Lenovo ThinkPad
        if (clean_sn.substr(0, 4) == "TPAD" || clean_sn.find("LENOVO") != std::string::npos) {
            return Brand::LENOVO;
        }
        // HP
        if (clean_sn.substr(0, 3) == "HP" || clean_sn.find("HP") != std::string::npos) {
            return Brand::HP;
        }
        // ASUS
        if (clean_sn.substr(0, 4) == "ASUS" || clean_sn.find("ASUS") != std::string::npos) {
            return Brand::ASUS;
        }
        // Dell
        if (clean_sn.length() >= 5 && clean_sn.length() <= 7) {
            return Brand::DELL;
        }
    }

    return Brand::UNKNOWN;
}

// ========== Apple解码 ==========

SNDecodeResult SNDecoder::decodeApple(const std::string& sn) {
    SNDecodeResult result;
    result.brand = Brand::APPLE;
    result.raw_sn = sn;

    std::string clean_sn = sn;
    std::transform(clean_sn.begin(), clean_sn.end(), clean_sn.begin(), ::toupper);

    if (clean_sn.length() < 5) {
        result.status = SNDecodeStatus::FAILED;
        result.error_message = "SN长度不足";
        return result;
    }

    const auto& yearMap = AppleSNRules::getYearMap();
    const auto& weekMap = AppleSNRules::getWeekMap();

    char yearChar = clean_sn[AppleSNRules::YEAR_POSITION];
    char weekChar = clean_sn[AppleSNRules::WEEK_POSITION];

    auto yearIt = yearMap.find(yearChar);
    if (yearIt == yearMap.end()) {
        result.status = SNDecodeStatus::FAILED;
        result.error_message = "无法识别年份编码: " + std::string(1, yearChar);
        return result;
    }

    auto weekIt = weekMap.find(weekChar);
    if (weekIt == weekMap.end()) {
        result.status = SNDecodeStatus::PARTIAL;
        result.error_message = "无法识别周次编码: " + std::string(1, weekChar);
        result.factory_year = yearIt->second.year;
        result.half_year = yearIt->second.half == 1 ? "上半年" : "下半年";
        return result;
    }

    result.factory_year = yearIt->second.year;
    result.factory_week = weekIt->second;
    result.half_year = yearIt->second.half == 1 ? "上半年" : "下半年";

    // 由周次推算月份
    result.factory_month = std::min(12, (weekIt->second - 1) / 4 + 1);

    result.status = SNDecodeStatus::SUCCESS;
    return result;
}

// ========== Samsung解码 ==========

SNDecodeResult SNDecoder::decodeSamsung(const std::string& sn) {
    SNDecodeResult result;
    result.brand = Brand::SAMSUNG;
    result.raw_sn = sn;

    std::string clean_sn = sn;
    std::transform(clean_sn.begin(), clean_sn.end(), clean_sn.begin(), ::toupper);

    if (clean_sn.length() < 7) {
        result.status = SNDecodeStatus::FAILED;
        result.error_message = "SN长度不足";
        return result;
    }

    const auto& yearMap = SamsungSNRules::getYearMap();
    const auto& monthMap = SamsungSNRules::getMonthMap();

    size_t year_pos = clean_sn.length() - SamsungSNRules::YEAR_OFFSET_FROM_END;
    size_t month_pos = clean_sn.length() - SamsungSNRules::MONTH_OFFSET_FROM_END;

    char yearChar = clean_sn[year_pos];
    char monthChar = clean_sn[month_pos];

    auto yearIt = yearMap.find(yearChar);
    if (yearIt == yearMap.end()) {
        result.status = SNDecodeStatus::FAILED;
        result.error_message = "无法识别年份编码: " + std::string(1, yearChar);
        return result;
    }

    auto monthIt = monthMap.find(monthChar);
    if (monthIt == monthMap.end()) {
        result.status = SNDecodeStatus::PARTIAL;
        result.error_message = "无法识别月份编码: " + std::string(1, monthChar);
        result.factory_year = yearIt->second;
        return result;
    }

    result.factory_year = yearIt->second;
    result.factory_month = monthIt->second;

    result.status = SNDecodeStatus::SUCCESS;
    return result;
}

// ========== Huawei解码 ==========

SNDecodeResult SNDecoder::decodeHuawei(const std::string& sn) {
    SNDecodeResult result;
    result.brand = Brand::HUAWEI;
    result.raw_sn = sn;

    if (sn.length() < 9) {
        result.status = SNDecodeStatus::FAILED;
        result.error_message = "SN长度不足";
        return result;
    }

    try {
        // 提取年份（第6-7位）
        std::string year_str = sn.substr(HuaweiSNRules::YEAR_START, HuaweiSNRules::YEAR_LENGTH);
        int year_suffix = std::stoi(year_str);
        result.factory_year = 2000 + year_suffix;

        // 提取周次（第8-9位）
        std::string week_str = sn.substr(HuaweiSNRules::WEEK_START, HuaweiSNRules::WEEK_LENGTH);
        result.factory_week = std::stoi(week_str);

        // 由周次推算月份
        result.factory_month = std::min(12, (result.factory_week.value() - 1) / 4 + 1);

        result.status = SNDecodeStatus::SUCCESS;
    } catch (const std::exception& e) {
        result.status = SNDecodeStatus::FAILED;
        result.error_message = "解析失败: " + std::string(e.what());
    }

    return result;
}

// ========== Honor解码 ==========

SNDecodeResult SNDecoder::decodeHonor(const std::string& sn) {
    // Honor使用与华为相同的编码规则
    SNDecodeResult result = decodeHuawei(sn);
    result.brand = Brand::HONOR;
    return result;
}

// ========== Xiaomi解码 ==========

SNDecodeResult SNDecoder::decodeXiaomi(const std::string& sn) {
    SNDecodeResult result;
    result.brand = Brand::XIAOMI;
    result.raw_sn = sn;

    // Xiaomi有多种SN格式，需要更复杂的识别逻辑
    // IMEI格式：15位纯数字
    if (sn.length() == 15 && std::all_of(sn.begin(), sn.end(), ::isdigit)) {
        // IMEI格式无法直接提取生产日期
        result.status = SNDecodeStatus::PARTIAL;
        result.error_message = "IMEI格式需要官方API查询";
        return result;
    }

    // 自定义格式：尝试解析
    if (sn.length() >= 4) {
        try {
            std::string year_part = sn.substr(XiaomiSNRules::YEAR_START, XiaomiSNRules::YEAR_LENGTH);
            int year_suffix = std::stoi(year_part);
            if (year_suffix >= 20 && year_suffix <= 26) {
                result.factory_year = 2000 + year_suffix;
                result.status = SNDecodeStatus::PARTIAL;
                result.error_message = "小米SN格式多样，结果仅供参考";
                return result;
            }
        } catch (...) {
            // 解析失败
        }
    }

    result.status = SNDecodeStatus::FAILED;
    result.error_message = "无法识别的小米SN格式";
    return result;
}

// ========== OPPO解码 ==========

SNDecodeResult SNDecoder::decodeOPPO(const std::string& sn) {
    SNDecodeResult result;
    result.brand = Brand::OPPO;
    result.raw_sn = sn;

    // OPPO SN格式需要查表解码
    result.status = SNDecodeStatus::PARTIAL;
    result.error_message = "OPPO SN需要官方API查询";
    return result;
}

// ========== Vivo解码 ==========

SNDecodeResult SNDecoder::decodeVivo(const std::string& sn) {
    SNDecodeResult result;
    result.brand = Brand::VIVO;
    result.raw_sn = sn;

    if (sn.length() < 8) {
        result.status = SNDecodeStatus::FAILED;
        result.error_message = "SN长度不足";
        return result;
    }

    try {
        // 提取年份（第5-6位）
        std::string year_str = sn.substr(VivoSNRules::YEAR_START, VivoSNRules::YEAR_LENGTH);
        int year_suffix = std::stoi(year_str);
        if (year_suffix >= 20 && year_suffix <= 26) {
            result.factory_year = 2000 + year_suffix;
        }

        // 提取周次/月份（第7-8位）
        std::string week_str = sn.substr(VivoSNRules::WEEK_START, VivoSNRules::WEEK_LENGTH);
        int week_or_month = std::stoi(week_str);
        if (week_or_month >= 1 && week_or_month <= 12) {
            result.factory_month = week_or_month;
        } else if (week_or_month >= 1 && week_or_month <= 52) {
            result.factory_week = week_or_month;
            result.factory_month = std::min(12, (week_or_month - 1) / 4 + 1);
        }

        result.status = SNDecodeStatus::PARTIAL;
        result.error_message = "vivo SN格式多样，结果仅供参考";
    } catch (const std::exception& e) {
        result.status = SNDecodeStatus::FAILED;
        result.error_message = "解析失败: " + std::string(e.what());
    }

    return result;
}

// ========== Lenovo解码 ==========

SNDecodeResult SNDecoder::decodeLenovo(const std::string& sn) {
    SNDecodeResult result;
    result.brand = Brand::LENOVO;
    result.raw_sn = sn;

    // ThinkPad格式需要查表
    result.status = SNDecodeStatus::PARTIAL;
    result.error_message = "Lenovo SN需要官方API查询";
    return result;
}

// ========== HP解码 ==========

SNDecodeResult SNDecoder::decodeHP(const std::string& sn) {
    SNDecodeResult result;
    result.brand = Brand::HP;
    result.raw_sn = sn;

    result.status = SNDecodeStatus::PARTIAL;
    result.error_message = "HP SN需要官方API查询";
    return result;
}

// ========== ASUS解码 ==========

SNDecodeResult SNDecoder::decodeASUS(const std::string& sn) {
    SNDecodeResult result;
    result.brand = Brand::ASUS;
    result.raw_sn = sn;

    if (sn.length() < 3) {
        result.status = SNDecodeStatus::FAILED;
        result.error_message = "SN长度不足";
        return result;
    }

    // ASUS: 第2位=年份代码，第3位=月份代码
    // 需要查表解码
    result.status = SNDecodeStatus::PARTIAL;
    result.error_message = "ASUS SN需要官方API查询";
    return result;
}

// ========== Dell解码 ==========

SNDecodeResult SNDecoder::decodeDell(const std::string& sn) {
    SNDecodeResult result;
    result.brand = Brand::DELL;
    result.raw_sn = sn;

    // Dell服务标签需要官方API查询
    result.status = SNDecodeStatus::PARTIAL;
    result.error_message = "Dell SN需要官方API查询";
    return result;
}

// ========== 格式验证 ==========

bool SNDecoder::validateFormat(const std::string& sn, Brand brand) {
    std::string clean_sn = sn;
    std::transform(clean_sn.begin(), clean_sn.end(), clean_sn.begin(), ::toupper);

    switch (brand) {
        case Brand::APPLE:
        case Brand::APPLE_MAC:
            return clean_sn.length() == 12;
        case Brand::SAMSUNG:
            return clean_sn.length() >= 10;
        case Brand::HUAWEI:
        case Brand::HONOR:
            return clean_sn.length() >= 10;
        case Brand::XIAOMI:
            return clean_sn.length() == 15 || clean_sn.length() >= 8;
        case Brand::OPPO:
        case Brand::VIVO:
            return clean_sn.length() >= 10;
        case Brand::LENOVO:
        case Brand::HP:
        case Brand::ASUS:
            return clean_sn.length() >= 8;
        case Brand::DELL:
            return clean_sn.length() >= 5 && clean_sn.length() <= 7;
        default:
            return false;
    }
}

// ========== 格式说明 ==========

std::string SNDecoder::getFormatHint(Brand brand) {
    return BrandSNRules::getFormatDescription(brand);
}

// ========== 生产日期估算 ==========

std::string SNDecodeResult::getProductionDateEstimate() const {
    std::ostringstream oss;

    if (factory_year.has_value()) {
        oss << factory_year.value();

        if (factory_month.has_value()) {
            oss << "-" << factory_month.value();
        } else if (factory_week.has_value()) {
            // 由周次推算月份
            int month = std::min(12, (factory_week.value() - 1) / 4 + 1);
            oss << "-" << month << " (第" << factory_week.value() << "周)";
        } else if (half_year.has_value()) {
            oss << " " << half_year.value();
        }
    }

    return oss.str();
}

} // namespace digiguide::core