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
                // Honor SN typically starts with 'HON' or 'HNR' after 2020
                // Huawei SN typically starts with 'HW' or numeric prefix
                // Check common Honor prefixes
                std::string prefix = clean_sn.substr(0, 3);
                std::transform(prefix.begin(), prefix.end(), prefix.begin(), ::toupper);
                if (prefix == "HON" || prefix == "HNR" || prefix == "HNR"
                    || clean_sn.find("HONOR") != std::string::npos) {
                    return Brand::HONOR;
                }
                // Default to Huawei; caller should use Build.BRAND to disambiguate
                return Brand::HUAWEI;
            }
        }
    }

    // Xiaomi: IMEI格式15位，或自定义格式
    if (clean_sn.length() == 15 && std::all_of(clean_sn.begin(), clean_sn.end(), ::isdigit)) {
        return Brand::XIAOMI;
    }

    // OPPO: SN usually starts with letter prefix (e.g. A0X, C0X, R0X)
    // Vivo: SN usually starts with letter prefix (e.g. B0X, D0X, V0X)
    // Distinguish by checking OPPO-specific prefixes first
    if (clean_sn.length() >= 10 && clean_sn.length() <= 15) {
        // OPPO SN typically starts with A, C, R, P, or contains "OPPO"
        if (clean_sn[0] == 'A' || clean_sn[0] == 'C' || clean_sn[0] == 'R'
            || clean_sn[0] == 'P' || clean_sn.find("OPPO") != std::string::npos) {
            return Brand::OPPO;
        }
        // Vivo SN typically starts with B, D, V, or contains "VIVO"
        if (clean_sn[0] == 'B' || clean_sn[0] == 'D' || clean_sn[0] == 'V'
            || clean_sn.find("VIVO") != std::string::npos) {
            return Brand::VIVO;
        }
        // 无明确 OPPO/vivo 前缀时，不做默认推断，避免把未知 SN 误判为 OPPO。
        return Brand::UNKNOWN;
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

    // Xiaomi SN/IMEI 生产规则未公开，无法从 SN 可靠推断生产日期。
    // 仅做品牌识别，避免给出伪精确的模拟年份。
    if (sn.length() == 15 && std::all_of(sn.begin(), sn.end(), ::isdigit)) {
        result.status = SNDecodeStatus::PARTIAL;
        result.error_message = "IMEI格式无法直接提取生产日期，请通过官方渠道查询";
        return result;
    }

    if (sn.length() >= 4) {
        result.status = SNDecodeStatus::PARTIAL;
        result.error_message = "小米SN生产规则未公开，无法可靠解析生产日期";
        return result;
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

    if (sn.length() < 8) {
        result.status = SNDecodeStatus::FAILED;
        result.error_message = "SN长度不足";
        return result;
    }

    // OPPO 官方 SN 生产规则未公开，任何按位置截取两位数字并映射为年份的做法
    // 都属于伪精确估算，可能严重误导用户。2026 正式版仅做品牌识别，不输出日期。
    result.status = SNDecodeStatus::PARTIAL;
    result.error_message = "OPPO SN生产规则未公开，无法可靠解析生产日期";
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

    // vivo 官方 SN 生产规则未公开，过去按固定位解析年份/周次属于伪精确实现。
    // 2026 正式版仅做品牌识别，不输出具体生产日期。
    result.status = SNDecodeStatus::PARTIAL;
    result.error_message = "vivo SN生产规则未公开，无法可靠解析生产日期";
    return result;
}

// ========== Lenovo解码 ==========

SNDecodeResult SNDecoder::decodeLenovo(const std::string& sn) {
    SNDecodeResult result;
    result.brand = Brand::LENOVO;
    result.raw_sn = sn;

    if (sn.length() < 8) {
        result.status = SNDecodeStatus::FAILED;
        result.error_message = "SN长度不足";
        return result;
    }

    // Lenovo/ThinkPad 官方生产日期规则未公开，按第 4 字符线性映射年份
    // 会忽略编码循环复用，导致 2026 年后溢出或年份错误。2026 正式版不再估算。
    result.status = SNDecodeStatus::PARTIAL;
    result.error_message = "Lenovo SN生产规则未公开，无法可靠解析生产日期";
    return result;
}

// ========== HP解码 ==========

SNDecodeResult SNDecoder::decodeHP(const std::string& sn) {
    SNDecodeResult result;
    result.brand = Brand::HP;
    result.raw_sn = sn;

    if (sn.length() < 6) {
        result.status = SNDecodeStatus::FAILED;
        result.error_message = "SN长度不足";
        return result;
    }

    // HP 序列号中普通数字极易被误识别为年份/周次，原实现属于高误报伪精确。
    // 2026 正式版仅做品牌识别，不输出生产日期。
    result.status = SNDecodeStatus::PARTIAL;
    result.error_message = "HP SN生产规则未公开，无法可靠解析生产日期";
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

    // ASUS 官方 SN 生产规则未公开，固定位置映射年份/月份属于伪精确实现。
    // 2026 正式版仅做品牌识别，不输出生产日期。
    result.status = SNDecodeStatus::PARTIAL;
    result.error_message = "ASUS SN生产规则未公开，无法可靠解析生产日期";
    return result;
}

// ========== Dell解码 ==========

SNDecodeResult SNDecoder::decodeDell(const std::string& sn) {
    SNDecodeResult result;
    result.brand = Brand::DELL;
    result.raw_sn = sn;

    if (sn.length() < 5) {
        result.status = SNDecodeStatus::FAILED;
        result.error_message = "SN长度不足";
        return result;
    }

    // Dell Service Tag / Express Service Code 不编码生产日期，
    // 原实现按数值大小猜测年份属于伪精确。2026 正式版不再估算。
    result.status = SNDecodeStatus::PARTIAL;
    result.error_message = "Dell SN不编码生产日期，请通过官方保修查询";
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

    if (status == SNDecodeStatus::PARTIAL && !factory_year.has_value()) {
        return "无法可靠推断生产日期";
    }

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

        if (status == SNDecodeStatus::PARTIAL) {
            oss << "（估算值，仅供参考）";
        }
    }

    return oss.str();
}

} // namespace digiguide::core