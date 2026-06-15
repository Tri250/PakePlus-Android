#include "bugreport_parser.h"
#include "regex_patterns.h"
#include "zip_parser.h"
#include <regex>
#include <sstream>
#include <algorithm>
#include <cctype>

namespace digiguide::core {

// ========== 主解析入口 ==========

BatteryRawData BugreportParser::parseFromText(const std::string& bugreport_text) {
    BatteryRawData data;

    // 首先提取品牌和型号，用于确定解析策略
    extractBrandModel(bugreport_text, data);
    extractSN(bugreport_text, data);
    
    // 检测品牌类型
    BrandType brand_type = BrandType::UNKNOWN;
    if (data.brand.has_value()) {
        brand_type = RegexPatterns::detectBrandFromString(data.brand.value());
    }
    
    // 尝试从 healthd 格式一次性提取所有电池信息（荣耀/华为/小米等）
    if (tryExtractHealthd(bugreport_text, data)) {
        // healthd 成功提取了核心信息，继续提取其他辅助信息
    } else {
        // 使用品牌专属或通用模式逐项提取
        if (brand_type != BrandType::UNKNOWN) {
            // 使用品牌专属配置
            extractWithBrandConfig(bugreport_text, data, brand_type);
        } else {
            // 使用通用模式
            extractCapacity(bugreport_text, data);
            extractCycleCount(bugreport_text, data);
        }
    }
    
    // 提取其他辅助信息
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

    if (!zip_result.success) {
        BatteryRawData empty_data;
        return empty_data;
    }

    // 尝试使用主 bugreport 内容
    if (zip_result.main_bugreport_content.has_value()) {
        return parseFromText(zip_result.main_bugreport_content.value());
    }

    // 如果没有主内容，遍历所有文件寻找电池信息
    BatteryRawData combined_data;
    for (const auto& [name, content] : zip_result.files) {
        if (name.find(".txt") != std::string::npos ||
            name.find("bugreport") != std::string::npos ||
            name.find("dumpstate") != std::string::npos ||
            content.find("healthd:") != std::string::npos ||
            content.find("fc=") != std::string::npos) {
            
            BatteryRawData file_data = parseFromText(content);
            
            // 合并数据（优先保留已有数据）
            if (!file_data.brand.has_value() && combined_data.brand.has_value()) {
                file_data.brand = combined_data.brand;
            }
            if (!file_data.model.has_value() && combined_data.model.has_value()) {
                file_data.model = combined_data.model;
            }
            if (!file_data.current_capacity_mah.has_value() && combined_data.current_capacity_mah.has_value()) {
                file_data.current_capacity_mah = combined_data.current_capacity_mah;
            }
            if (!file_data.design_capacity_mah.has_value() && combined_data.design_capacity_mah.has_value()) {
                file_data.design_capacity_mah = combined_data.design_capacity_mah;
            }
            if (!file_data.cycle_count.has_value() && combined_data.cycle_count.has_value()) {
                file_data.cycle_count = combined_data.cycle_count;
            }
            
            combined_data = file_data;
            
            // 如果已经提取到核心数据，可以停止
            if (combined_data.current_capacity_mah.has_value() && combined_data.cycle_count.has_value()) {
                break;
            }
        }
    }

    return combined_data;
}

// ========== healthd 格式一次性提取（荣耀/华为/小米核心格式）==========

bool BugreportParser::tryExtractHealthd(const std::string& text, BatteryRawData& data) {
    // 尝试完整 healthd 格式匹配
    std::regex healthd_full_regex(RegexPatterns::getHealthdPattern());
    std::smatch match;
    
    if (std::regex_search(text, match, healthd_full_regex)) {
        try {
            // healthd: battery l=100 v=4356 t=27.0 h=2 st=2 c=265 fc=4562 cc=1200
            // match[1] = l (电量)
            // match[2] = v (电压)
            // match[3] = t (温度)
            // match[4] = h (健康度代码)
            // match[5] = st (状态)
            // match[6] = c (电流)
            // match[7] = fc (实际容量)
            // match[8] = cc (循环次数)
            
            data.current_capacity_mah = std::stoi(match[7].str());
            data.cycle_count = std::stoi(match[8].str());
            data.temperature_celsius = std::stof(match[3].str());
            
            // 电压电流配对
            float voltage = std::stof(match[2].str());
            float current = std::stof(match[6].str());
            data.voltage_current_pairs.emplace_back(voltage, current);
            
            return true;
        } catch (...) {
            // 解析失败，继续尝试其他方法
        }
    }
    
    // 尝试简化 healthd 格式匹配
    std::regex healthd_simple_regex(RegexPatterns::getHealthdSimplePattern());
    if (std::regex_search(text, match, healthd_simple_regex)) {
        try {
            // healthd: ... fc=4562 ... cc=1200
            data.current_capacity_mah = std::stoi(match[1].str());
            data.cycle_count = std::stoi(match[2].str());
            return true;
        } catch (...) {}
    }
    
    // 尝试单独提取 fc 和 cc（带词边界，避免误匹配）
    std::regex fc_regex(R"(\bfc[=:\s]+(\d+))");
    std::regex cc_regex(R"(\bcc[=:\s]+(\d+))");
    
    if (std::regex_search(text, match, fc_regex)) {
        try {
            data.current_capacity_mah = std::stoi(match[1].str());
        } catch (...) {}
    }
    
    if (std::regex_search(text, match, cc_regex)) {
        try {
            data.cycle_count = std::stoi(match[1].str());
        } catch (...) {}
    }
    
    return data.current_capacity_mah.has_value() || data.cycle_count.has_value();
}

// ========== 使用品牌专属配置提取 ==========

void BugreportParser::extractWithBrandConfig(const std::string& text, BatteryRawData& data, BrandType brand_type) {
    const auto& configs = RegexPatterns::getBrandConfigs();
    
    if (configs.find(brand_type) == configs.end()) {
        // 品牌配置不存在，使用通用模式
        extractCapacity(text, data);
        extractCycleCount(text, data);
        return;
    }
    
    const BrandConfig& config = configs.at(brand_type);
    
    // 使用品牌专属容量模式
    for (const auto& pattern : config.capacityPatterns) {
        std::regex regex(pattern);
        std::smatch match;
        if (std::regex_search(text, match, regex)) {
            try {
                data.current_capacity_mah = std::stoi(match[1].str());
                break;
            } catch (...) {}
        }
    }
    
    // 使用品牌专属设计容量模式
    for (const auto& pattern : config.designCapacityPatterns) {
        std::regex regex(pattern);
        std::smatch match;
        if (std::regex_search(text, match, regex)) {
            try {
                data.design_capacity_mah = std::stoi(match[1].str());
                break;
            } catch (...) {}
        }
    }
    
    // 使用品牌专属循环次数模式
    for (const auto& pattern : config.cycleCountPatterns) {
        std::regex regex(pattern);
        std::smatch match;
        if (std::regex_search(text, match, regex)) {
            try {
                data.cycle_count = std::stoi(match[1].str());
                break;
            } catch (...) {}
        }
    }
    
    // 使用品牌专属温度模式
    for (const auto& pattern : config.temperaturePatterns) {
        std::regex regex(pattern);
        std::smatch match;
        if (std::regex_search(text, match, regex)) {
            try {
                data.temperature_celsius = std::stof(match[1].str());
                break;
            } catch (...) {}
        }
    }
    
    // 使用品牌专属电压模式
    for (const auto& pattern : config.voltagePatterns) {
        std::regex regex(pattern);
        std::sregex_iterator it(text.begin(), text.end(), regex);
        for (auto iter = it; iter != std::sregex_iterator(); ++iter) {
            try {
                float voltage = std::stof(iter->str(1));
                data.voltage_current_pairs.emplace_back(voltage, 0);
            } catch (...) {}
        }
        if (!data.voltage_current_pairs.empty()) break;
    }
    
    // 如果品牌专属模式没有提取到数据，使用通用模式兜底
    if (!data.current_capacity_mah.has_value()) {
        extractCapacity(text, data);
    }
    if (!data.cycle_count.has_value()) {
        extractCycleCount(text, data);
    }
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
    // 提取设计容量（使用新的多模式）
    for (const auto& pattern : RegexPatterns::getDesignCapacityPatterns()) {
        std::regex design_regex(pattern);
        std::smatch match;
        if (std::regex_search(text, match, design_regex)) {
            try {
                data.design_capacity_mah = std::stoi(match[1].str());
                break;
            } catch (...) {}
        }
    }

    // 提取当前容量（使用新的多模式）
    for (const auto& pattern : RegexPatterns::getCapacityPatterns()) {
        std::regex capacity_regex(pattern);
        std::smatch match;
        if (std::regex_search(text, match, capacity_regex)) {
            try {
                data.current_capacity_mah = std::stoi(match[1].str());
                break;
            } catch (...) {}
        }
    }
}

void BugreportParser::extractCycleCount(const std::string& text, BatteryRawData& data) {
    // 使用新的多模式循环次数提取
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
    // 使用新的多模式温度提取
    for (const auto& pattern : RegexPatterns::getTemperaturePatterns()) {
        std::regex temp_regex(pattern);
        std::smatch match;
        if (std::regex_search(text, match, temp_regex)) {
            try {
                data.temperature_celsius = std::stof(match[1].str());
                break;
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
    // 使用新的多模式电压提取
    for (const auto& pattern : RegexPatterns::getVoltagePatterns()) {
        std::regex voltage_regex(pattern);
        std::sregex_iterator it(text.begin(), text.end(), voltage_regex);
        
        std::vector<float> voltages;
        for (auto iter = it; iter != std::sregex_iterator(); ++iter) {
            try {
                voltages.push_back(std::stof(iter->str(1)));
            } catch (...) {}
        }
        
        if (!voltages.empty()) {
            // 尝试匹配电流
            std::regex current_regex(RegexPatterns::getCurrentPattern());
            std::sregex_iterator current_it(text.begin(), text.end(), current_regex);
            
            std::vector<float> currents;
            for (auto iter = current_it; iter != std::sregex_iterator(); ++iter) {
                try {
                    currents.push_back(std::stof(iter->str(1)));
                } catch (...) {}
            }
            
            // 将电压和电流配对
            size_t min_size = std::min(voltages.size(), currents.size());
            for (size_t i = 0; i < min_size; ++i) {
                data.voltage_current_pairs.emplace_back(voltages[i], currents[i]);
            }
            
            // 如果没有电流，只保存电压
            if (currents.empty()) {
                for (float v : voltages) {
                    data.voltage_current_pairs.emplace_back(v, 0);
                }
            }
            break;
        }
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