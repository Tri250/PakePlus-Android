package com.batteryhealth.app.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Ignore;
import androidx.room.TypeConverters;

import com.batteryhealth.app.data.database.Converters;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 电池健康报告实体类
 * 既作为 Room 数据库存储，也兼容 Gson 远程/本地解析。
 */
@Entity(tableName = "battery_health_report")
@TypeConverters({Converters.class})
public class BatteryHealthReport {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @SerializedName("brand")
    @ColumnInfo(name = "brand")
    private String brand;

    @SerializedName("model")
    @ColumnInfo(name = "model")
    private String model;

    @SerializedName("battery_health_percentage")
    @ColumnInfo(name = "health_percentage")
    private float batteryHealthPercentage;

    @SerializedName("battery_health_level")
    @ColumnInfo(name = "health_level")
    private String batteryHealthLevel;

    @SerializedName("health_grade")
    @ColumnInfo(name = "health_grade")
    private String healthGrade;

    @SerializedName("design_capacity_mah")
    @ColumnInfo(name = "design_capacity_mah")
    private int designCapacityMah;

    @SerializedName("current_capacity_mah")
    @ColumnInfo(name = "current_capacity_mah")
    private int currentCapacityMah;

    @SerializedName("cycle_count")
    @ColumnInfo(name = "cycle_count")
    private int cycleCount;

    @SerializedName("capacity_percent")
    @ColumnInfo(name = "capacity_percent")
    private int capacityPercent;

    @SerializedName("charge_counter_uah")
    @ColumnInfo(name = "charge_counter_uah")
    private int chargeCounter;

    @SerializedName("current_now_ua")
    @ColumnInfo(name = "current_now_ua")
    private int currentNow;

    @SerializedName("voltage_now_uv")
    @ColumnInfo(name = "voltage_now_uv")
    private long voltageNowUv;

    @SerializedName("temperature_now_celsius")
    @ColumnInfo(name = "temperature_now_celsius")
    private float temperatureNowCelsius;

    @SerializedName("technology")
    @ColumnInfo(name = "technology")
    private String technology;

    @SerializedName("battery_source")
    @ColumnInfo(name = "battery_source")
    private String batterySource;

    @SerializedName("battery_source_confidence")
    @ColumnInfo(name = "battery_source_confidence")
    private float batterySourceConfidence;

    @SerializedName("confidence")
    @ColumnInfo(name = "confidence")
    private float confidence;

    @SerializedName("raw_content_snippet")
    @ColumnInfo(name = "raw_content_snippet")
    private String rawContentSnippet;

    @SerializedName("app_usage_list")
    @ColumnInfo(name = "app_usage_list")
    private List<AppUsageInfo> appUsageList;

    @SerializedName("screen_on_time_minutes")
    @ColumnInfo(name = "screen_on_time_minutes")
    private long screenOnTimeMinutes;

    @SerializedName("parsed_at")
    @ColumnInfo(name = "parsed_at")
    private Date parsedAt;

    @SerializedName("recommendations")
    @ColumnInfo(name = "recommendations")
    private List<Recommendation> recommendations;

    @SerializedName("app_consumption")
    @ColumnInfo(name = "app_consumption")
    private List<AppConsumption> appConsumption;

    @SerializedName("estimated_remaining_capacity_mah")
    @ColumnInfo(name = "estimated_remaining_capacity_mah")
    private int estimatedRemainingCapacityMah;

    public BatteryHealthReport() {
        this.parsedAt = new Date();
        this.appUsageList = new ArrayList<>();
        this.recommendations = new ArrayList<>();
        this.appConsumption = new ArrayList<>();
    }

    @Ignore
    public BatteryHealthReport(String brand, String model) {
        this();
        this.brand = brand;
        this.model = model;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public float getBatteryHealthPercentage() { return batteryHealthPercentage; }
    public void setBatteryHealthPercentage(float batteryHealthPercentage) { this.batteryHealthPercentage = batteryHealthPercentage; }

    public String getBatteryHealthLevel() { return batteryHealthLevel; }
    public void setBatteryHealthLevel(String batteryHealthLevel) { this.batteryHealthLevel = batteryHealthLevel; }

    public String getHealthGrade() { return healthGrade; }
    public void setHealthGrade(String healthGrade) { this.healthGrade = healthGrade; }

    public int getDesignCapacityMah() { return designCapacityMah; }
    public void setDesignCapacityMah(int designCapacityMah) { this.designCapacityMah = designCapacityMah; }

    public int getCurrentCapacityMah() { return currentCapacityMah; }
    public void setCurrentCapacityMah(int currentCapacityMah) { this.currentCapacityMah = currentCapacityMah; }

    public int getCycleCount() { return cycleCount; }
    public void setCycleCount(int cycleCount) { this.cycleCount = cycleCount; }

    public int getCapacityPercent() { return capacityPercent; }
    public void setCapacityPercent(int capacityPercent) { this.capacityPercent = capacityPercent; }

    public int getChargeCounter() { return chargeCounter; }
    public void setChargeCounter(int chargeCounter) { this.chargeCounter = chargeCounter; }

    public int getCurrentNow() { return currentNow; }
    public void setCurrentNow(int currentNow) { this.currentNow = currentNow; }

    public long getVoltageNowUv() { return voltageNowUv; }
    public void setVoltageNowUv(long voltageNowUv) { this.voltageNowUv = voltageNowUv; }

    public float getTemperatureNowCelsius() { return temperatureNowCelsius; }
    public void setTemperatureNowCelsius(float temperatureNowCelsius) { this.temperatureNowCelsius = temperatureNowCelsius; }

    public String getTechnology() { return technology; }
    public void setTechnology(String technology) { this.technology = technology; }

    public String getBatterySource() { return batterySource; }
    public void setBatterySource(String batterySource) { this.batterySource = batterySource; }

    public float getBatterySourceConfidence() { return batterySourceConfidence; }
    public void setBatterySourceConfidence(float batterySourceConfidence) { this.batterySourceConfidence = batterySourceConfidence; }

    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }

    public String getRawContentSnippet() { return rawContentSnippet; }
    public void setRawContentSnippet(String rawContentSnippet) { this.rawContentSnippet = rawContentSnippet; }

    public List<AppUsageInfo> getAppUsageList() {
        return appUsageList != null ? appUsageList : Collections.emptyList();
    }
    public void setAppUsageList(List<AppUsageInfo> appUsageList) { this.appUsageList = appUsageList; }

    public long getScreenOnTimeMinutes() { return screenOnTimeMinutes; }
    public void setScreenOnTimeMinutes(long screenOnTimeMinutes) { this.screenOnTimeMinutes = screenOnTimeMinutes; }

    public Date getParsedAt() { return parsedAt; }
    public void setParsedAt(Date parsedAt) { this.parsedAt = parsedAt; }

    public List<Recommendation> getRecommendations() {
        return recommendations != null ? recommendations : Collections.emptyList();
    }
    public void setRecommendations(List<Recommendation> recommendations) { this.recommendations = recommendations; }

    public List<AppConsumption> getAppConsumption() {
        return appConsumption != null ? appConsumption : Collections.emptyList();
    }
    public void setAppConsumption(List<AppConsumption> appConsumption) { this.appConsumption = appConsumption; }

    public int getEstimatedRemainingCapacityMah() { return estimatedRemainingCapacityMah; }
    public void setEstimatedRemainingCapacityMah(int estimatedRemainingCapacityMah) { this.estimatedRemainingCapacityMah = estimatedRemainingCapacityMah; }

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
