#include "bugreport_parser.h"
#include "regex_patterns.h"
#include "zip_parser.h"
#include <regex>
#include <sstream>
#include <algorithm>

namespace digiguide::core {

// ========== 主解析入口 ==========

BatteryRawData BugreportParser::parseFromText(const std::string& bugreport_text) {
    BatteryRawData data;

    // 按顺序提取各字段
    extractBrandModel(bugreport_text, data);
    extractSN(bugreport_text, data);
    extractCapacity(bugreport_text, data);
    extractCycleCount(bugreport_text, data);
    extractManufacturingDate(bugreport_text, data);
    extractTemperature(bugreport_text, data);
    extractScreenOnTime(bugreport_text, data);
    extractVoltageCurrent(bugreport_text, data);
    extractChargingEvents(bugreport_text, data);
    extractAppPowerUsage(bugreport_text, data);

    return data;
}

BatteryRawData BugreportParser::parseFromZip(const std::string& zip_path) {
    ZipParseResult zip_result = ZipParser::parseFromFile(zip_path);

    if (!zip_result.success || !zip_result.main_bugreport_content.has_value()) {
        BatteryRawData empty_data;
        return empty_data;
    }

    return parseFromText(zip_result.main_bugreport_content.value());
}

// ========== 解析详情 ==========

ParseDetail BugreportParser::getParseDetail(const BatteryRawData& data) {
    ParseDetail detail;

    // 成功提取的字段
    if (data.brand.has_value()) detail.extracted_fields.push_back("品牌");
    if (data.model.has_value()) detail.extracted_fields.push_back("型号");
    if (data.sn.has_value()) detail.extracted_fields.push_back("SN");
    if (data.design_capacity_mah.has_value()) detail.extracted_fields.push_back("设计容量");
    if (data.current_capacity_mah.has_value()) detail.extracted_fields.push_back("当前容量");
    if (data.cycle_count.has_value()) detail.extracted_fields.push_back("循环次数");
    if (data.manufacturing_date.has_value()) detail.extracted_fields.push_back("制造日期");
    if (data.temperature_celsius.has_value()) detail.extracted_fields.push_back("温度");
    if (data.screen_on_time_hours.has_value()) detail.extracted_fields.push_back("亮屏时间");
    if (data.charge_count.has_value()) detail.extracted_fields.push_back("充电次数");
    if (!data.voltage_current_pairs.empty()) detail.extracted_fields.push_back("电压电流数据");
    if (!data.charging_events.empty()) detail.extracted_fields.push_back("充电事件");
    if (!data.app_power_usages.empty()) detail.extracted_fields.push_back("应用耗电");

    // 缺失的字段
    if (!data.brand.has_value()) detail.missing_fields.push_back("品牌");
    if (!data.model.has_value()) detail.missing_fields.push_back("型号");
    if (!data.design_capacity_mah.has_value()) detail.missing_fields.push_back("设计容量");
    if (!data.current_capacity_mah.has_value()) detail.missing_fields.push_back("当前容量");
    if (!data.cycle_count.has_value()) detail.missing_fields.push_back("循环次数");
    if (!data.manufacturing_date.has_value()) detail.missing_fields.push_back("制造日期");
    if (!data.temperature_celsius.has_value()) detail.missing_fields.push_back("温度");

    return detail;
}

std::string BugreportParser::getParseSummary(const BatteryRawData& data) {
    std::ostringstream oss;
    oss << "解析结果统计:\n";
    oss << "  成功提取字段: " << data.getAvailableDataCount() << " 个\n";
    oss << "  品牌: " << (data.brand.has_value() ? data.brand.value() : "未提取") << "\n";
    oss << "  型号: " << (data.model.has_value() ? data.model.value() : "未提取") << "\n";
    oss << "  设计容量: " << (data.design_capacity_mah.has_value() ?
                              std::to_string(data.design_capacity_mah.value()) + "mAh" : "未提取") << "\n";
    oss << "  当前容量: " << (data.current_capacity_mah.has_value() ?
                              std::to_string(data.current_capacity_mah.value()) + "mAh" : "未提取") << "\n";
    oss << "  循环次数: " << (data.cycle_count.has_value() ?
                              std::to_string(data.cycle_count.value()) : "未提取") << "\n";
    oss << "  温度: " << (data.temperature_celsius.has_value() ?
                           std::to_string(data.temperature_celsius.value()) + "°C" : "未提取") << "\n";
    oss << "  电压电流数据点: " << data.voltage_current_pairs.size() << " 个\n";
    oss << "  充电事件: " << data.charging_events.size() << " 个\n";
    oss << "  应用耗电记录: " << data.app_power_usages.size() << " 个\n";

    return oss.str();
}

// ========== 子解析器实现 ==========

void BugreportParser::extractBrandModel(const std::string& text, BatteryRawData& data) {
    // 提取品牌
    std::regex brand_regex(RegexPatterns::getBrandPattern());
    std::smatch match;
    if (std::regex_search(text, match, brand_regex)) {
        data.brand = match[1].str();
    }

    // 提取制造商（备用）
    if (!data.brand.has_value()) {
        std::regex manufacturer_regex(RegexPatterns::getManufacturerPattern());
        if (std::regex_search(text, match, manufacturer_regex)) {
            data.brand = match[1].str();
        }
    }

    // 提取型号
    std::regex model_regex(RegexPatterns::getModelPattern());
    if (std::regex_search(text, match, model_regex)) {
        data.model = match[1].str();
    }
}

void BugreportParser::extractCapacity(const std::string& text, BatteryRawData& data) {
    // 提取设计容量
    std::regex design_regex(RegexPatterns::getDesignCapacityPattern());
    std::smatch match;
    if (std::regex_search(text, match, design_regex)) {
        try {
            data.design_capacity_mah = std::stoi(match[1].str());
        } catch (...) {}
    }

    // 提取当前容量（按优先级尝试多个模式）
    for (const auto& pattern : RegexPatterns::getCapacityPatterns()) {
        std::regex capacity_regex(pattern);
        if (std::regex_search(text, match, capacity_regex)) {
            try {
                data.current_capacity_mah = std::stoi(match[1].str());
                break;  // 成功提取后停止
            } catch (...) {}
        }
    }
}

void BugreportParser::extractCycleCount(const std::string& text, BatteryRawData& data) {
    // 按优先级尝试多个模式
    for (const auto& pattern : RegexPatterns::getCycleCountPatterns()) {
        std::regex cycle_regex(pattern);
        std::smatch match;
        if (std::regex_search(text, match, cycle_regex)) {
            try {
                data.cycle_count = std::stoi(match[1].str());
                break;
            } catch (...) {}
        }
    }
}

void BugreportParser::extractManufacturingDate(const std::string& text, BatteryRawData& data) {
    // 按优先级尝试多个模式
    for (const auto& pattern : RegexPatterns::getDatePatterns()) {
        std::regex date_regex(pattern);
        std::smatch match;
        if (std::regex_search(text, match, date_regex)) {
            try {
                int year = std::stoi(match[1].str());
                int month = std::stoi(match[2].str());
                int day = std::stoi(match[3].str());

                // 验证日期有效性
                if (RegexPatterns::isValidDate(year, month, day)) {
                    std::ostringstream oss;
                    oss << year << "-" << month << "-" << day;
                    data.manufacturing_date = oss.str();
                    break;
                }
            } catch (...) {}
        }
    }
}

void BugreportParser::extractTemperature(const std::string& text, BatteryRawData& data) {
    std::regex temp_regex(RegexPatterns::getTemperaturePattern());
    std::smatch match;

    if (std::regex_search(text, match, temp_regex)) {
        try {
            data.temperature_celsius = std::stof(match[1].str());
        } catch (...) {}
    }

    // 备用模式
    if (!data.temperature_celsius.has_value()) {
        std::regex temp_alt_regex(RegexPatterns::getTemperaturePatternAlt());
        if (std::regex_search(text, match, temp_alt_regex)) {
            try {
                data.temperature_celsius = std::stof(match[1].str());
            } catch (...) {}
        }
    }
}

void BugreportParser::extractScreenOnTime(const std::string& text, BatteryRawData& data) {
    std::regex screen_regex(RegexPatterns::getScreenOnTimePattern());
    std::smatch match;

    if (std::regex_search(text, match, screen_regex)) {
        try {
            data.screen_on_time_hours = static_cast<int>(std::stof(match[1].str()));
        } catch (...) {}
    }
}

void BugreportParser::extractVoltageCurrent(const std::string& text, BatteryRawData& data) {
    // 提取电压数据
    std::regex voltage_regex(RegexPatterns::getVoltagePattern());
    std::regex current_regex(RegexPatterns::getCurrentPattern());

    std::sregex_iterator voltage_it(text.begin(), text.end(), voltage_regex);
    std::sregex_iterator current_it(text.begin(), text.end(), current_regex);

    std::vector<float> voltages;
    std::vector<float> currents;

    for (auto it = voltage_it; it != std::sregex_iterator(); ++it) {
        try {
            voltages.push_back(std::stof(it->str(1)));
        } catch (...) {}
    }

    for (auto it = current_it; it != std::sregex_iterator(); ++it) {
        try {
            currents.push_back(std::stof(it->str(1)));
        } catch (...) {}
    }

    // 将电压和电流配对（假设它们在文本中交替出现）
    size_t min_size = std::min(voltages.size(), currents.size());
    for (size_t i = 0; i < min_size; ++i) {
        data.voltage_current_pairs.emplace_back(voltages[i], currents[i]);
    }
}

void BugreportParser::extractChargingEvents(const std::string& text, BatteryRawData& data) {
    // 从Battery History解析充电事件
    std::regex history_regex(RegexPatterns::getBatteryHistoryPattern());
    std::sregex_iterator it(text.begin(), text.end(), history_regex);

    std::vector<std::tuple<int64_t, int, std::string>> history_entries;

    for (auto iter = it; iter != std::sregex_iterator(); ++iter) {
        std::smatch match = *iter;
        try {
            // 解析时间戳、电量、状态
            std::string time_str = match[1].str();
            int level = std::stoi(match[2].str());
            std::string status = match[3].str();

            // 简化的时间戳解析（实际需要更复杂的处理）
            int64_t timestamp = 0;  // TODO: 完整时间戳解析

            history_entries.emplace_back(timestamp, level, status);
        } catch (...) {}
    }

    // 分析充电事件（简化实现）
    // 实际需要更复杂的逻辑来识别充电开始/结束
    data.charging_events.clear();  // 暂不实现完整解析
}

void BugreportParser::extractAppPowerUsage(const std::string& text, BatteryRawData& data) {
    // 提取应用耗电数据
    std::regex app_regex(RegexPatterns::getAppPowerPattern());
    std::sregex_iterator it(text.begin(), text.end(), app_regex);

    for (auto iter = it; iter != std::sregex_iterator(); ++iter) {
        std::smatch match = *iter;
        BatteryRawData::AppPowerUsage usage;
        usage.package_name = match[1].str();
        usage.display_name = usage.package_name;  // 简化处理
        try {
            usage.power_mah = std::stof(match[2].str());
        } catch (...) {
            usage.power_mah = 0;
        }
        usage.wakeup_count = 0;
        usage.is_system = false;  // 默认第三方应用

        data.app_power_usages.push_back(usage);
    }

    // 按耗电量排序，取前10个
    std::sort(data.app_power_usages.begin(), data.app_power_usages.end(),
              [](const auto& a, const auto& b) { return a.power_mah > b.power_mah; });

    if (data.app_power_usages.size() > 10) {
        data.app_power_usages.resize(10);
    }
}

void BugreportParser::extractSN(const std::string& text, BatteryRawData& data) {
    std::regex sn_regex(RegexPatterns::getSNPattern());
    std::smatch match;

    if (std::regex_search(text, match, sn_regex)) {
        data.sn = match[1].str();
    }

    // 备用：IMEI
    if (!data.sn.has_value()) {
        std::regex imei_regex(RegexPatterns::getIMEIPattern());
        if (std::regex_search(text, match, imei_regex)) {
            data.sn = match[1].str();
        }
    }
}

// ========== 辅助方法 ==========

std::optional<int> BugreportParser::extractIntWithPatterns(
    const std::string& text,
    const std::vector<std::string>& patterns) {

    for (const auto& pattern : patterns) {
        std::regex regex(pattern);
        std::smatch match;
        if (std::regex_search(text, match, regex)) {
            try {
                return std::stoi(match[1].str());
            } catch (...) {}
        }
    }

    return std::nullopt;
}

std::optional<std::string> BugreportParser::extractStringWithPattern(
    const std::string& text,
    const std::string& pattern) {

    std::regex regex(pattern);
    std::smatch match;
    if (std::regex_search(text, match, regex)) {
        return match[1].str();
    }

    return std::nullopt;
}

} // namespace digiguide::core