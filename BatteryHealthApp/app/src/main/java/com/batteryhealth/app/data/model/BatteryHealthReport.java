package com.batteryhealth.app.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

/**
 * 远程电池健康分析报告
 * 对应 digiguide BatteryHealthAnalysis 结构。
 */
public class BatteryHealthReport {
    @SerializedName("brand") private String brand;
    @SerializedName("model") private String model;
    @SerializedName("battery_health_percentage") private float batteryHealthPercentage;
    @SerializedName("battery_health_level") private String batteryHealthLevel;
    @SerializedName("design_capacity_mah") private int designCapacityMah;
    @SerializedName("current_capacity_mah") private int currentCapacityMah;
    @SerializedName("cycle_count") private int cycleCount;
    @SerializedName("estimated_remaining_capacity_mah") private int estimatedRemainingCapacityMah;
    @SerializedName("voltage_now_uv") private long voltageNowUv;
    @SerializedName("current_now_ua") private long currentNowUa;
    @SerializedName("temperature_now_celsius") private float temperatureNowCelsius;
    @SerializedName("battery_source") private String batterySource;
    @SerializedName("recommendations") private List<Recommendation> recommendations;
    @SerializedName("app_consumption") private List<AppConsumption> appConsumption;

    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public float getBatteryHealthPercentage() { return batteryHealthPercentage; }
    public String getBatteryHealthLevel() { return batteryHealthLevel; }
    public int getDesignCapacityMah() { return designCapacityMah; }
    public int getCurrentCapacityMah() { return currentCapacityMah; }
    public int getCycleCount() { return cycleCount; }
    public int getEstimatedRemainingCapacityMah() { return estimatedRemainingCapacityMah; }
    public long getVoltageNowUv() { return voltageNowUv; }
    public long getCurrentNowUa() { return currentNowUa; }
    public float getTemperatureNowCelsius() { return temperatureNowCelsius; }
    public String getBatterySource() { return batterySource; }

    public List<Recommendation> getRecommendations() {
        return recommendations != null ? recommendations : Collections.emptyList();
    }

    public List<AppConsumption> getAppConsumption() {
        return appConsumption != null ? appConsumption : Collections.emptyList();
    }

    public static class Recommendation {
        @SerializedName("title") private String title;
        @SerializedName("content") private String content;
        @SerializedName("priority") private String priority;

        public Recommendation() {}

        public Recommendation(String title, String content, String priority) {
            this.title = title;
            this.content = content;
            this.priority = priority;
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
    }

    public static class AppConsumption {
        @SerializedName("package_name") private String packageName;
        @SerializedName("app_name") private String appName;
        @SerializedName("usage_minutes") private int usageMinutes;
        @SerializedName("consumption_percent") private float consumptionPercent;

        public AppConsumption() {}

        public AppConsumption(String packageName, String appName, int usageMinutes, float consumptionPercent) {
            this.packageName = packageName;
            this.appName = appName;
            this.usageMinutes = usageMinutes;
            this.consumptionPercent = consumptionPercent;
        }

        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public int getUsageMinutes() { return usageMinutes; }
        public void setUsageMinutes(int usageMinutes) { this.usageMinutes = usageMinutes; }
        public float getConsumptionPercent() { return consumptionPercent; }
        public void setConsumptionPercent(float consumptionPercent) { this.consumptionPercent = consumptionPercent; }
    }
}
