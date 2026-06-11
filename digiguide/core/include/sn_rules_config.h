#pragma once

#include <string>
#include <vector>
#include <map>
#include <optional>
#include <functional>
#include <memory>

namespace digiguide::core {

/**
 * SN规则配置项
 * 支持热更新和可扩展配置
 */
struct SNRuleConfig {
    std::string brand;              // 品牌名称
    std::string format_description; // 格式说明
    int sn_min_length;              // SN最小长度
    int sn_max_length;              // SN最大长度
    std::string year_pattern;       // 年份提取正则
    std::string month_pattern;      // 月份提取正则
    std::string week_pattern;       // 周次提取正则
    std::map<char, int> year_map;   // 年份字符映射
    std::map<char, int> month_map;  // 月份字符映射
    int warranty_months;            // 保修月数
    std::string official_api_url;   // 官方API地址
    bool requires_auth;             // 是否需要认证
    bool has_captcha;               // 是否有CAPTCHA
    int priority;                   // 解码优先级
    std::string version;            // 规则版本
    int64_t last_updated;           // 最后更新时间戳
};

/**
 * SN规则管理器
 * 支持动态加载、热更新、规则扩展
 */
class SNRulesManager {
public:
    // 单例模式
    static SNRulesManager& getInstance();

    // 获取品牌规则
    std::optional<SNRuleConfig> getRule(const std::string& brand);

    // 获取所有规则
    std::vector<SNRuleConfig> getAllRules();

    // 添加/更新规则（热更新）
    void updateRule(const SNRuleConfig& rule);

    // 批量更新规则
    void updateRules(const std::vector<SNRuleConfig>& rules);

    // 删除规则
    void removeRule(const std::string& brand);

    // 从JSON加载规则
    bool loadFromJson(const std::string& json_content);

    // 导出规则为JSON
    std::string exportToJson();

    // 获取规则版本
    std::string getRulesVersion();

    // 检查规则是否需要更新
    bool needsUpdate();

    // 注册规则变更回调
    void registerUpdateCallback(std::function<void(const std::string&)> callback);

    // 获取需要认证的品牌列表
    std::vector<std::string> getBrandsRequiringAuth();

    // 获取有CAPTCHA的品牌列表
    std::vector<std::string> getBrandsWithCaptcha();

private:
    SNRulesManager();
    ~SNRulesManager();

    // 内置默认规则
    void initializeDefaultRules();

    // 规则存储
    std::map<std::string, SNRuleConfig> rules_;

    // 更新回调
    std::vector<std::function<void(const std::string&)>> update_callbacks_;

    // 当前规则版本
    std::string current_version_;

    // 最后更新检查时间
    int64_t last_check_time_;
};

/**
 * API响应监控器
 * 监控品牌API响应结构变化
 */
class APIMonitor {
public:
    // API响应状态
    enum class APIStatus {
        OK,             // 正常
        DEGRADED,       // 降级（响应结构变化）
        FAILED,         // 失败
        AUTH_REQUIRED,  // 需要认证
        CAPTCHA,        // 需要CAPTCHA
        TIMEOUT         // 超时
    };

    // API响应记录
    struct APIResponseRecord {
        std::string brand;
        std::string endpoint;
        APIStatus status;
        int response_code;
        std::string error_message;
        int64_t response_time_ms;
        int64_t timestamp;
        bool structure_changed;  // 响应结构是否变化
    };

    // 记录API响应
    void recordResponse(const APIResponseRecord& record);

    // 获取品牌API状态
    APIStatus getBrandStatus(const std::string& brand);

    // 获取最近的响应记录
    std::vector<APIResponseRecord> getRecentRecords(const std::string& brand, int count = 10);

    // 检查API结构变化
    bool detectStructureChange(const std::string& brand, const std::string& expected_structure);

    // 获取降级建议
    std::string getFallbackSuggestion(const std::string& brand);

    // 清除过期记录
    void cleanupOldRecords(int64_t max_age_ms = 7 * 24 * 60 * 60 * 1000);  // 7天

private:
    std::map<std::string, std::vector<APIResponseRecord>> response_history_;
};

/**
 * 降级处理器
 * 处理API不可用时的降级策略
 */
class FallbackHandler {
public:
    // 降级策略
    enum class FallbackStrategy {
        LOCAL_DECODE,       // 本地解码
        MANUAL_GUIDE,       // 引导用户手动查询
        CACHED_RESULT,      // 使用缓存结果
        PARTIAL_DECODE,     // 部分解码
        OFFICIAL_LINK       // 提供官方链接
    };

    // 获取降级策略
    FallbackStrategy getStrategy(const std::string& brand, APIMonitor::APIStatus status);

    // 执行降级处理
    struct FallbackResult {
        FallbackStrategy strategy;
        std::string message;
        std::string official_url;
        bool can_continue;
    };
    FallbackResult executeFallback(const std::string& brand, const std::string& sn);

    // 获取官方查询链接
    std::string getOfficialQueryUrl(const std::string& brand);

    // 获取手动查询指南
    std::string getManualQueryGuide(const std::string& brand);
};

} // namespace digiguide::core