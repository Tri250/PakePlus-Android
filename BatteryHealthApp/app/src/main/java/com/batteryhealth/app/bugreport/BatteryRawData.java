package com.batteryhealth.app.bugreport;

/**
 * BugReport 解析后的核心数据结构（等价于 digiguide C++ BatteryRawData）。
 *
 * <p>每条信息均采用 {@code String} / {@code Integer} / {@code Float} 包装，保证单一字段缺失时整
 * 个对象仍然可序列化。{@code hasXxx()} 系列方法在 UI 与其他模块需要"是否有数据"时使用。</p>
 */
public class BatteryRawData {

    // ===== 基础信息 =====
    private String brand;
    private String model;
    private String sn;

    // ===== 容量 =====
    /** 设计容量 (mAh)。ColorOS / MIUI bugreport 写法为 charge_full_design。 */
    private Integer designCapacityMah;
    /** 当前满充容量 / Min learned (mAh)。 */
    private Integer currentCapacityMah;
    /** 当前 charge counter (mAh)。 */
    private Integer chargeCounterMah;

    // ===== 循环与寿命 =====
    private Integer cycleCount;
    private String manufacturingDate;

    // ===== 实时 =====
    /** 摄氏度。 */
    private Float temperatureCelsius;
    private Integer screenOnTimeHours;
    private Integer chargeCount;

    // ===== Battery History 充电事件 =====
    private final java.util.List<ChargingEvent> chargingEvents = new java.util.ArrayList<>();

    // ===== 第三方应用耗电 =====
    private final java.util.List<AppPowerUsage> appPowerUsages = new java.util.ArrayList<>();

    // ===== 电压电流样本 =====
    private final java.util.List<float[]> voltageCurrentPairs = new java.util.ArrayList<>();

    // ===== 解析结果统计 =====
    private int extractedFieldCount;

    public static class ChargingEvent {
        public long timestamp;
        public int startLevel;
        public int endLevel;
        public int durationMinutes;
        public float avgPowerW;
    }

    public static class AppPowerUsage {
        public String packageName;
        public String displayName;
        public float powerMah;
        public int wakeupCount;
        public boolean isSystem;
    }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getSn() { return sn; }
    public void setSn(String sn) { this.sn = sn; }

    public Integer getDesignCapacityMah() { return designCapacityMah; }
    public void setDesignCapacityMah(Integer v) { this.designCapacityMah = v; }

    public Integer getCurrentCapacityMah() { return currentCapacityMah; }
    public void setCurrentCapacityMah(Integer v) { this.currentCapacityMah = v; }

    public Integer getChargeCounterMah() { return chargeCounterMah; }
    public void setChargeCounterMah(Integer v) { this.chargeCounterMah = v; }

    public Integer getCycleCount() { return cycleCount; }
    public void setCycleCount(Integer v) { this.cycleCount = v; }

    public String getManufacturingDate() { return manufacturingDate; }
    public void setManufacturingDate(String v) { this.manufacturingDate = v; }

    public Float getTemperatureCelsius() { return temperatureCelsius; }
    public void setTemperatureCelsius(Float v) { this.temperatureCelsius = v; }

    public Integer getScreenOnTimeHours() { return screenOnTimeHours; }
    public void setScreenOnTimeHours(Integer v) { this.screenOnTimeHours = v; }

    public Integer getChargeCount() { return chargeCount; }
    public void setChargeCount(Integer v) { this.chargeCount = v; }

    public java.util.List<ChargingEvent> getChargingEvents() { return chargingEvents; }
    public java.util.List<AppPowerUsage> getAppPowerUsages() { return appPowerUsages; }
    public java.util.List<float[]> getVoltageCurrentPairs() { return voltageCurrentPairs; }

    public int getExtractedFieldCount() { return extractedFieldCount; }
    public void setExtractedFieldCount(int v) { this.extractedFieldCount = v; }

    public boolean hasCapacityData() {
        return designCapacityMah != null || currentCapacityMah != null;
    }

    public boolean hasCycleData() { return cycleCount != null; }
    public boolean hasTemperatureData() { return temperatureCelsius != null; }

    /** 用于 BatteryHealthCalculator：按可获取字段加权。 */
    public int getAvailableDataCount() {
        int c = 0;
        if (brand != null) c++;
        if (model != null) c++;
        if (designCapacityMah != null) c++;
        if (currentCapacityMah != null) c++;
        if (cycleCount != null) c++;
        if (manufacturingDate != null) c++;
        if (temperatureCelsius != null) c++;
        if (screenOnTimeHours != null) c++;
        if (chargeCount != null) c++;
        if (!voltageCurrentPairs.isEmpty()) c++;
        if (!chargingEvents.isEmpty()) c++;
        if (!appPowerUsages.isEmpty()) c++;
        return c;
    }
}
