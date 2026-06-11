#include "sn_rules_config.h"
#include <algorithm>
#include <chrono>
#include <sstream>
#include <fstream>

namespace digiguide::core {

// ========== SNRulesManager 实现 ==========

SNRulesManager& SNRulesManager::getInstance() {
    static SNRulesManager instance;
    return instance;
}

SNRulesManager::SNRulesManager() : current_version_("3.1.0"), last_check_time_(0) {
    initializeDefaultRules();
}

SNRulesManager::~SNRulesManager() {}

void SNRulesManager::initializeDefaultRules() {
    // Apple规则
    SNRuleConfig apple;
    apple.brand = "APPLE";
    apple.format_description = "Apple SN: 12位，第4位=半年代码，第5位=周次";
    apple.sn_min_length = 12;
    apple.sn_max_length = 12;
    apple.year_pattern = "第4位";
    apple.warranty_months = 12;
    apple.official_api_url = "https://checkcoverage.apple.com";
    apple.requires_auth = false;
    apple.has_captcha = false;
    apple.priority = 100;
    apple.version = "3.1.0";
    apple.last_updated = std::chrono::system_clock::now().time_since_epoch().count();

    // Apple年份映射
    apple.year_map = {
        {'C', 2010}, {'D', 2010}, {'F', 2011}, {'G', 2011},
        {'H', 2012}, {'J', 2012}, {'K', 2013}, {'L', 2013},
        {'M', 2014}, {'N', 2014}, {'P', 2015}, {'Q', 2015},
        {'R', 2016}, {'S', 2016}, {'T', 2017}, {'V', 2017},
        {'W', 2018}, {'X', 2018}, {'Y', 2019}, {'Z', 2019},
        {'0', 2020}, {'1', 2020}, {'2', 2021}, {'3', 2021},
        {'4', 2022}, {'5', 2022}, {'6', 2023}, {'7', 2023},
        {'8', 2024}, {'9', 2024}, {'A', 2025}, {'B', 2025}
    };
    rules_["APPLE"] = apple;

    // Samsung规则
    SNRuleConfig samsung;
    samsung.brand = "SAMSUNG";
    samsung.format_description = "Samsung SN: 倒数第7位=年份，倒数第6位=月份";
    samsung.sn_min_length = 10;
    samsung.sn_max_length = 15;
    samsung.year_pattern = "倒数第7位";
    samsung.warranty_months = 12;
    samsung.official_api_url = "";
    samsung.requires_auth = false;
    samsung.has_captcha = false;
    samsung.priority = 90;
    samsung.year_map = {
        {'R', 2023}, {'S', 2024}, {'T', 2025}, {'U', 2026}, {'V', 2027}, {'W', 2028}
    };
    samsung.month_map = {
        {'1', 1}, {'2', 2}, {'3', 3}, {'4', 4}, {'5', 5}, {'6', 6},
        {'7', 7}, {'8', 8}, {'9', 9}, {'A', 10}, {'B', 11}, {'C', 12}
    };
    rules_["SAMSUNG"] = samsung;

    // Huawei规则
    SNRuleConfig huawei;
    huawei.brand = "HUAWEI";
    huawei.format_description = "Huawei SN: 第6-7位=年份后两位，第8-9位=周次";
    huawei.sn_min_length = 10;
    huawei.sn_max_length = 20;
    huawei.year_pattern = "第6-7位";
    huawei.warranty_months = 12;
    huawei.official_api_url = "https://consumer.huawei.com/cn/support/warranty-query";
    huawei.requires_auth = false;
    huawei.has_captcha = true;  // 华为官网可能有验证码
    huawei.priority = 80;
    rules_["HUAWEI"] = huawei;

    // Honor规则（与华为类似）
    SNRuleConfig honor;
    honor.brand = "HONOR";
    honor.format_description = "Honor SN: 与华为类似，第6-7位=年份，第8-9位=周次";
    honor.sn_min_length = 10;
    honor.sn_max_length = 20;
    honor.year_pattern = "第6-7位";
    honor.warranty_months = 12;
    honor.official_api_url = "https://www.hihonor.com/cn/support/warranty-query";
    honor.requires_auth = false;
    honor.has_captcha = true;
    honor.priority = 75;
    rules_["HONOR"] = honor;

    // Xiaomi规则
    SNRuleConfig xiaomi;
    xiaomi.brand = "XIAOMI";
    xiaomi.format_description = "Xiaomi SN: 多种格式，IMEI或自定义编码";
    xiaomi.sn_min_length = 8;
    xiaomi.sn_max_length = 15;
    xiaomi.warranty_months = 12;
    xiaomi.official_api_url = "https://www.mi.com/support/warranty";
    xiaomi.requires_auth = false;
    xiaomi.has_captcha = false;
    xiaomi.priority = 70;
    rules_["XIAOMI"] = xiaomi;

    // OPPO规则
    SNRuleConfig oppo;
    oppo.brand = "OPPO";
    oppo.format_description = "OPPO SN: 第4-5位含年份+月份编码";
    oppo.sn_min_length = 10;
    oppo.sn_max_length = 15;
    oppo.warranty_months = 12;
    oppo.official_api_url = "https://support.oppo.com/cn/warranty";
    oppo.requires_auth = false;
    oppo.has_captcha = false;
    oppo.priority = 65;
    rules_["OPPO"] = oppo;

    // vivo规则
    SNRuleConfig vivo;
    vivo.brand = "VIVO";
    vivo.format_description = "vivo SN: 第5-6位=年份，第7-8位=周次/月份";
    vivo.sn_min_length = 10;
    vivo.sn_max_length = 15;
    vivo.warranty_months = 12;
    vivo.official_api_url = "https://www.vivo.com.cn/service/warranty";
    vivo.requires_auth = false;
    vivo.has_captcha = false;
    vivo.priority = 60;
    rules_["VIVO"] = vivo;

    // Lenovo规则
    SNRuleConfig lenovo;
    lenovo.brand = "LENOVO";
    lenovo.format_description = "Lenovo SN: ThinkPad格式，前4位=机型，第5位=年份";
    lenovo.sn_min_length = 10;
    lenovo.sn_max_length = 15;
    lenovo.warranty_months = 12;
    lenovo.official_api_url = "https://support.lenovo.com/us/en/warrantylookup";
    lenovo.requires_auth = false;
    lenovo.has_captcha = false;
    lenovo.priority = 50;
    rules_["LENOVO"] = lenovo;

    // HP规则
    SNRuleConfig hp;
    hp.brand = "HP";
    hp.format_description = "HP SN: 第3-4位=年份和地区，后续=周次";
    hp.sn_min_length = 10;
    hp.sn_max_length = 15;
    hp.warranty_months = 12;
    hp.official_api_url = "https://support.hp.com/check-warranty";
    hp.requires_auth = false;
    hp.has_captcha = false;
    hp.priority = 45;
    rules_["HP"] = hp;

    // ASUS规则
    SNRuleConfig asus;
    asus.brand = "ASUS";
    asus.format_description = "ASUS SN: 第2位=年份代码，第3位=月份代码";
    asus.sn_min_length = 10;
    asus.sn_max_length = 15;
    asus.warranty_months = 12;
    asus.official_api_url = "https://www.asus.com/support/warranty";
    asus.requires_auth = false;
    asus.has_captcha = false;
    asus.priority = 40;
    rules_["ASUS"] = asus;

    // Dell规则
    SNRuleConfig dell;
    dell.brand = "DELL";
    dell.format_description = "Dell SN: 服务标签5-7位，需官方API查询";
    dell.sn_min_length = 5;
    dell.sn_max_length = 7;
    dell.warranty_months = 12;
    dell.official_api_url = "https://www.dell.com/support";
    dell.requires_auth = false;
    dell.has_captcha = false;
    dell.priority = 35;
    rules_["DELL"] = dell;
}

std::optional<SNRuleConfig> SNRulesManager::getRule(const std::string& brand) {
    auto it = rules_.find(brand);
    if (it != rules_.end()) {
        return it->second;
    }
    return std::nullopt;
}

std::vector<SNRuleConfig> SNRulesManager::getAllRules() {
    std::vector<SNRuleConfig> result;
    for (const auto& [brand, rule] : rules_) {
        result.push_back(rule);
    }
    // 按优先级排序
    std::sort(result.begin(), result.end(), [](const SNRuleConfig& a, const SNRuleConfig& b) {
        return a.priority > b.priority;
    });
    return result;
}

void SNRulesManager::updateRule(const SNRuleConfig& rule) {
    rules_[rule.brand] = rule;
    rules_[rule.brand].last_updated = std::chrono::system_clock::now().time_since_epoch().count();

    // 触发回调
    for (auto& callback : update_callbacks_) {
        callback(rule.brand);
    }
}

void SNRulesManager::updateRules(const std::vector<SNRuleConfig>& rules) {
    for (const auto& rule : rules) {
        updateRule(rule);
    }
}

void SNRulesManager::removeRule(const std::string& brand) {
    rules_.erase(brand);
}

bool SNRulesManager::loadFromJson(const std::string& json_content) {
    // TODO: 实现JSON解析
    // 使用第三方JSON库如nlohmann/json
    return true;
}

std::string SNRulesManager::exportToJson() {
    // TODO: 实现JSON导出
    std::stringstream ss;
    ss << "{\"rules\": [";
    bool first = true;
    for (const auto& [brand, rule] : rules_) {
        if (!first) ss << ",";
        first = false;
        ss << "{\"brand\":\"" << rule.brand << "\"}";
    }
    ss << "]}";
    return ss.str();
}

std::string SNRulesManager::getRulesVersion() {
    return current_version_;
}

bool SNRulesManager::needsUpdate() {
    // 检查是否需要更新（例如超过7天未更新）
    auto now = std::chrono::system_clock::now().time_since_epoch().count();
    auto diff = now - last_check_time_;
    return diff > 7 * 24 * 60 * 60 * 1000 * 1000 * 1000;  // 7天
}

void SNRulesManager::registerUpdateCallback(std::function<void(const std::string&)> callback) {
    update_callbacks_.push_back(callback);
}

std::vector<std::string> SNRulesManager::getBrandsRequiringAuth() {
    std::vector<std::string> result;
    for (const auto& [brand, rule] : rules_) {
        if (rule.requires_auth) {
            result.push_back(brand);
        }
    }
    return result;
}

std::vector<std::string> SNRulesManager::getBrandsWithCaptcha() {
    std::vector<std::string> result;
    for (const auto& [brand, rule] : rules_) {
        if (rule.has_captcha) {
            result.push_back(brand);
        }
    }
    return result;
}

// ========== APIMonitor 实现 ==========

void APIMonitor::recordResponse(const APIResponseRecord& record) {
    response_history_[record.brand].push_back(record);

    // 保持最多100条记录
    if (response_history_[record.brand].size() > 100) {
        response_history_[record.brand].erase(response_history_[record.brand].begin());
    }
}

APIMonitor::APIStatus APIMonitor::getBrandStatus(const std::string& brand) {
    auto it = response_history_.find(brand);
    if (it == response_history_.end() || it->second.empty()) {
        return APIStatus::OK;  // 默认正常
    }

    // 检查最近10次请求的状态
    auto& records = it->second;
    int failed_count = 0;
    int degraded_count = 0;

    int check_count = std::min(10, (int)records.size());
    for (int i = records.size() - check_count; i < records.size(); i++) {
        if (records[i].status == APIStatus::FAILED) failed_count++;
        if (records[i].status == APIStatus::DEGRADED) degraded_count++;
        if (records[i].structure_changed) degraded_count++;
    }

    if (failed_count >= check_count * 0.8) {
        return APIStatus::FAILED;
    }
    if (failed_count >= check_count * 0.5 || degraded_count >= check_count * 0.3) {
        return APIStatus::DEGRADED;
    }
    return APIStatus::OK;
}

std::vector<APIMonitor::APIResponseRecord> APIMonitor::getRecentRecords(
    const std::string& brand, int count) {
    auto it = response_history_.find(brand);
    if (it == response_history_.end()) {
        return {};
    }

    auto& records = it->second;
    int start = std::max(0, (int)records.size() - count);
    return std::vector<APIMonitor::APIResponseRecord>(records.begin() + start, records.end());
}

bool APIMonitor::detectStructureChange(const std::string& brand, const std::string& expected_structure) {
    // TODO: 实现响应结构检测
    return false;
}

std::string APIMonitor::getFallbackSuggestion(const std::string& brand) {
    auto status = getBrandStatus(brand);
    switch (status) {
        case APIStatus::FAILED:
            return "API服务不可用，建议使用本地解码或手动查询";
        case APIStatus::DEGRADED:
            return "API响应结构可能变化，建议验证结果准确性";
        case APIStatus::AUTH_REQUIRED:
            return "API需要认证，建议引导用户手动查询";
        case APIStatus::CAPTCHA:
            return "API需要验证码，建议引导用户手动查询";
        default:
            return "";
    }
}

void APIMonitor::cleanupOldRecords(int64_t max_age_ms) {
    auto now = std::chrono::system_clock::now().time_since_epoch().count() / 1000000;

    for (auto& [brand, records] : response_history_) {
        records.erase(
            std::remove_if(records.begin(), records.end(),
                [now, max_age_ms](const APIResponseRecord& r) {
                    return (now - r.timestamp / 1000000) > max_age_ms;
                }),
            records.end()
        );
    }
}

// ========== FallbackHandler 实现 ==========

FallbackHandler::FallbackStrategy FallbackHandler::getStrategy(
    const std::string& brand, APIMonitor::APIStatus status) {
    switch (status) {
        case APIMonitor::APIStatus::FAILED:
            return FallbackStrategy::LOCAL_DECODE;
        case APIMonitor::APIStatus::AUTH_REQUIRED:
        case APIMonitor::APIStatus::CAPTCHA:
            return FallbackStrategy::MANUAL_GUIDE;
        case APIMonitor::APIStatus::DEGRADED:
            return FallbackStrategy::PARTIAL_DECODE;
        default:
            return FallbackStrategy::LOCAL_DECODE;
    }
}

FallbackHandler::FallbackResult FallbackHandler::executeFallback(
    const std::string& brand, const std::string& sn) {
    APIMonitor monitor;
    auto status = monitor.getBrandStatus(brand);
    auto strategy = getStrategy(brand, status);

    FallbackResult result;
    result.strategy = strategy;
    result.official_url = getOfficialQueryUrl(brand);
    result.can_continue = (strategy != FallbackStrategy::MANUAL_GUIDE);

    switch (strategy) {
        case FallbackStrategy::LOCAL_DECODE:
            result.message = "使用本地解码算法分析SN";
            break;
        case FallbackStrategy::MANUAL_GUIDE:
            result.message = "请访问官方网站手动查询保修信息";
            break;
        case FallbackStrategy::PARTIAL_DECODE:
            result.message = "部分信息可能不准确，建议验证";
            break;
        case FallbackStrategy::OFFICIAL_LINK:
            result.message = "请通过官方渠道查询完整信息";
            break;
        default:
            result.message = "";
    }

    return result;
}

std::string FallbackHandler::getOfficialQueryUrl(const std::string& brand) {
    auto& manager = SNRulesManager::getInstance();
    auto rule = manager.getRule(brand);
    if (rule.has_value()) {
        return rule->official_api_url;
    }

    // 默认官方链接
    static const std::map<std::string, std::string> official_urls = {
        {"APPLE", "https://checkcoverage.apple.com"},
        {"SAMSUNG", "https://www.samsung.com/us/support/service"},
        {"HUAWEI", "https://consumer.huawei.com/cn/support/warranty-query"},
        {"HONOR", "https://www.hihonor.com/cn/support/warranty-query"},
        {"XIAOMI", "https://www.mi.com/support/warranty"},
        {"OPPO", "https://support.oppo.com/cn/warranty"},
        {"VIVO", "https://www.vivo.com.cn/service/warranty"},
        {"LENOVO", "https://support.lenovo.com/us/en/warrantylookup"},
        {"HP", "https://support.hp.com/check-warranty"},
        {"ASUS", "https://www.asus.com/support/warranty"},
        {"DELL", "https://www.dell.com/support"}
    };

    auto it = official_urls.find(brand);
    if (it != official_urls.end()) {
        return it->second;
    }
    return "";
}

std::string FallbackHandler::getManualQueryGuide(const std::string& brand) {
    std::stringstream guide;
    guide << "=== " << brand << " 手动查询指南 ===\n\n";
    guide << "1. 访问官方网站: " << getOfficialQueryUrl(brand) << "\n";
    guide << "2. 输入您的设备序列号\n";
    guide << "3. 查看保修状态和生产日期信息\n\n";
    guide << "提示: 部分品牌可能需要登录账户或输入验证码\n";
    return guide.str();
}

} // namespace digiguide::core