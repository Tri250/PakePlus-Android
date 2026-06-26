package com.batteryhealth.app.data.model;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 自检模块单条检测结果。
 *
 * 本类为纯数据模型，不依赖 Android 运行环境，便于在测试与引擎之间传递。
 * 每个 {@code HealthCheckResult} 描述了一个可独立判定的检查项（如电池
 * 健康度、充电协议、通知权限等）的当前状态与建议。
 */
public class HealthCheckResult {

    /** 严重程度：数值越大越严重，0 为正常。 */
    public static final int SEVERITY_GOOD = 0;
    public static final int SEVERITY_INFO = 1;
    public static final int SEVERITY_WARNING = 2;
    public static final int SEVERITY_CRITICAL = 3;

    @IntDef({SEVERITY_GOOD, SEVERITY_INFO, SEVERITY_WARNING, SEVERITY_CRITICAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Severity {}

    /** 修复动作类型：供引擎在 {@code applyFix()} 中决策跳转目标。 */
    public static final int FIX_ACTION_NONE = 0;
    public static final int FIX_ACTION_NOTIFICATION_SETTINGS = 1;
    public static final int FIX_ACTION_BATTERY_OPTIMIZATION = 2;
    public static final int FIX_ACTION_POWER_USAGE_DETAILS = 3;
    public static final int FIX_ACTION_APPLICATION_DETAILS = 4;
    public static final int FIX_ACTION_CHARGING_LIMIT = 5;
    public static final int FIX_ACTION_BATTERY_SAVER = 6;
    public static final int FIX_ACTION_NETWORK_SETTINGS = 7;
    public static final int FIX_ACTION_ADVICE_ONLY = 8;
    public static final int FIX_ACTION_PERMISSION_SETTINGS = 9;

    @IntDef({FIX_ACTION_NONE, FIX_ACTION_NOTIFICATION_SETTINGS,
            FIX_ACTION_BATTERY_OPTIMIZATION, FIX_ACTION_POWER_USAGE_DETAILS,
            FIX_ACTION_APPLICATION_DETAILS, FIX_ACTION_CHARGING_LIMIT,
            FIX_ACTION_BATTERY_SAVER, FIX_ACTION_NETWORK_SETTINGS,
            FIX_ACTION_ADVICE_ONLY, FIX_ACTION_PERMISSION_SETTINGS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FixAction {}

    /** 分类：用于在卡片列表中分组展示（当前 UI 展示为一大列表，分类可用于统计）。 */
    public static final String CATEGORY_BATTERY = "battery";
    public static final String CATEGORY_CHARGING = "charging";
    public static final String CATEGORY_PERFORMANCE = "performance";
    public static final String CATEGORY_SYSTEM = "system";

    private final String id;
    private final String title;
    private final String category;
    @Severity private final int severity;
    private final String status;
    private final String value;
    private final String unit;
    private final String description;
    private final String advice;
    private final boolean repairable;
    @FixAction private final int fixAction;
    private final int itemScore; // 单项评分（0-100）
    private final long timestamp;

    private HealthCheckResult(Builder b) {
        this.id = b.id;
        this.title = b.title;
        this.category = b.category;
        this.severity = b.severity;
        this.status = b.status;
        this.value = b.value;
        this.unit = b.unit;
        this.description = b.description;
        this.advice = b.advice;
        this.repairable = b.repairable;
        this.fixAction = b.fixAction;
        this.itemScore = b.itemScore;
        this.timestamp = b.timestamp > 0 ? b.timestamp : System.currentTimeMillis();
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    @Severity public int getSeverity() { return severity; }
    public String getStatus() { return status; }
    public String getValue() { return value; }
    public String getUnit() { return unit; }
    public String getDescription() { return description; }
    public String getAdvice() { return advice; }
    public boolean isRepairable() { return repairable; }
    @FixAction public int getFixAction() { return fixAction; }
    public int getItemScore() { return itemScore; }
    public long getTimestamp() { return timestamp; }

    /** 根据严重程度映射颜色资源 ID（供 UI 使用）。 */
    public int toColorRes() {
        switch (severity) {
            case SEVERITY_CRITICAL: return com.batteryhealth.app.R.color.ios_red;
            case SEVERITY_WARNING:  return com.batteryhealth.app.R.color.ios_orange;
            case SEVERITY_INFO:     return com.batteryhealth.app.R.color.ios_blue;
            default:                return com.batteryhealth.app.R.color.ios_green;
        }
    }

    public static class Builder {
        private String id;
        private String title;
        private String category = CATEGORY_BATTERY;
        @Severity private int severity = SEVERITY_GOOD;
        private String status;
        private String value;
        private String unit;
        private String description;
        private String advice;
        private boolean repairable = false;
        @FixAction private int fixAction = FIX_ACTION_NONE;
        private int itemScore = 100;
        private long timestamp;

        public Builder setId(String id) { this.id = id; return this; }
        public Builder setTitle(String title) { this.title = title; return this; }
        public Builder setCategory(String category) { this.category = category; return this; }
        public Builder setSeverity(@Severity int severity) { this.severity = severity; return this; }
        public Builder setStatus(String status) { this.status = status; return this; }
        public Builder setValue(String value) { this.value = value; return this; }
        public Builder setUnit(String unit) { this.unit = unit; return this; }
        public Builder setDescription(String description) { this.description = description; return this; }
        public Builder setAdvice(String advice) { this.advice = advice; return this; }
        public Builder setRepairable(boolean repairable) { this.repairable = repairable; return this; }
        public Builder setFixAction(@FixAction int action) { this.fixAction = action; return this; }
        public Builder setItemScore(int score) { this.itemScore = Math.max(0, Math.min(100, score)); return this; }
        public Builder setTimestamp(long timestamp) { this.timestamp = timestamp; return this; }

        public HealthCheckResult build() {
            return new HealthCheckResult(this);
        }
    }
}
