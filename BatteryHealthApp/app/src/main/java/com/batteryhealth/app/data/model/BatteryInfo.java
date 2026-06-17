package com.batteryhealth.app.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

/**
 * 电池信息实体类
 * 
 * 存储电池健康度、容量、温度、循环次数等核心指标
 */
@Entity(tableName = "battery_info")
public class BatteryInfo {
    
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    @ColumnInfo(name = "timestamp")
    private long timestamp;
    
    // 电池容量信息
    @ColumnInfo(name = "design_capacity")
    private int designCapacity; // 设计容量 (mAh)
    
    @ColumnInfo(name = "current_capacity")
    private int currentCapacity; // 当前容量 (mAh)
    
    @ColumnInfo(name = "charge_counter")
    private int chargeCounter; // 充电计数器 (uAh)
    
    // 健康度
    @ColumnInfo(name = "health_percentage")
    private float healthPercentage; // 健康度百分比
    
    @ColumnInfo(name = "health_status")
    private String healthStatus; // 健康状态: good, normal, warning, poor
    
    // 循环次数
    @ColumnInfo(name = "cycle_count")
    private int cycleCount; // 充电循环次数
    
    // 温度
    @ColumnInfo(name = "temperature")
    private float temperature; // 电池温度 (°C)
    
    // 电压
    @ColumnInfo(name = "voltage")
    private float voltage; // 电压 (mV)
    
    // 电流
    @ColumnInfo(name = "current_now")
    private int currentNow; // 当前电流 (uA)
    
    // 充电状态
    @ColumnInfo(name = "status")
    private int status; // 充电状态
    
    @ColumnInfo(name = "plugged")
    private int plugged; // 充电方式
    
    @ColumnInfo(name = "level")
    private int level; // 电量百分比
    
    // 电池技术
    @ColumnInfo(name = "technology")
    private String technology; // 电池技术 (Li-ion等)
    
    // 电池溯源
    @ColumnInfo(name = "battery_source")
    private String batterySource; // 电池来源: original, third_party, unknown
    
    @ColumnInfo(name = "battery_serial")
    private String batterySerial; // 电池序列号
    
    // 充电功率
    @ColumnInfo(name = "charging_power")
    private float chargingPower; // 充电功率 (W)
    
    @ColumnInfo(name = "charging_voltage")
    private float chargingVoltage; // 充电电压 (V)
    
    @ColumnInfo(name = "charging_current")
    private float chargingCurrent; // 充电电流 (A)
    
    // 设备信息
    @ColumnInfo(name = "device_model")
    private String deviceModel; // 设备型号
    
    @ColumnInfo(name = "device_brand")
    private String deviceBrand; // 设备品牌

    // 数据来源标记
    @ColumnInfo(name = "cycle_count_estimated")
    private boolean cycleCountEstimated; // 循环次数是否为估算值

    public BatteryInfo() {
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters and Setters
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getDesignCapacity() {
        return designCapacity;
    }
    
    public void setDesignCapacity(int designCapacity) {
        this.designCapacity = designCapacity;
    }
    
    public int getCurrentCapacity() {
        return currentCapacity;
    }
    
    public void setCurrentCapacity(int currentCapacity) {
        this.currentCapacity = currentCapacity;
    }
    
    public int getChargeCounter() {
        return chargeCounter;
    }
    
    public void setChargeCounter(int chargeCounter) {
        this.chargeCounter = chargeCounter;
    }
    
    public float getHealthPercentage() {
        return healthPercentage;
    }
    
    public void setHealthPercentage(float healthPercentage) {
        this.healthPercentage = healthPercentage;
    }
    
    public String getHealthStatus() {
        return healthStatus;
    }
    
    public void setHealthStatus(String healthStatus) {
        this.healthStatus = healthStatus;
    }
    
    public int getCycleCount() {
        return cycleCount;
    }
    
    public void setCycleCount(int cycleCount) {
        this.cycleCount = cycleCount;
    }
    
    public float getTemperature() {
        return temperature;
    }
    
    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }
    
    public float getVoltage() {
        return voltage;
    }
    
    public void setVoltage(float voltage) {
        this.voltage = voltage;
    }
    
    public int getCurrentNow() {
        return currentNow;
    }
    
    public void setCurrentNow(int currentNow) {
        this.currentNow = currentNow;
    }
    
    public int getStatus() {
        return status;
    }
    
    public void setStatus(int status) {
        this.status = status;
    }
    
    public int getPlugged() {
        return plugged;
    }
    
    public void setPlugged(int plugged) {
        this.plugged = plugged;
    }
    
    public int getLevel() {
        return level;
    }
    
    public void setLevel(int level) {
        this.level = level;
    }
    
    public String getTechnology() {
        return technology;
    }
    
    public void setTechnology(String technology) {
        this.technology = technology;
    }
    
    public String getBatterySource() {
        return batterySource;
    }
    
    public void setBatterySource(String batterySource) {
        this.batterySource = batterySource;
    }
    
    public String getBatterySerial() {
        return batterySerial;
    }
    
    public void setBatterySerial(String batterySerial) {
        this.batterySerial = batterySerial;
    }
    
    public float getChargingPower() {
        return chargingPower;
    }
    
    public void setChargingPower(float chargingPower) {
        this.chargingPower = chargingPower;
    }
    
    public float getChargingVoltage() {
        return chargingVoltage;
    }
    
    public void setChargingVoltage(float chargingVoltage) {
        this.chargingVoltage = chargingVoltage;
    }
    
    public float getChargingCurrent() {
        return chargingCurrent;
    }
    
    public void setChargingCurrent(float chargingCurrent) {
        this.chargingCurrent = chargingCurrent;
    }
    
    public String getDeviceModel() {
        return deviceModel;
    }
    
    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }
    
    public String getDeviceBrand() {
        return deviceBrand;
    }
    
    public void setDeviceBrand(String deviceBrand) {
        this.deviceBrand = deviceBrand;
    }

    public boolean isCycleCountEstimated() {
        return cycleCountEstimated;
    }

    public void setCycleCountEstimated(boolean cycleCountEstimated) {
        this.cycleCountEstimated = cycleCountEstimated;
    }

    /**
     * 计算健康度
     */
    public void calculateHealthPercentage() {
        if (designCapacity > 0 && currentCapacity > 0) {
            this.healthPercentage = (currentCapacity * 100.0f) / designCapacity;
        }
    }
    
    /**
     * 计算充电功率
     */
    public void calculateChargingPower() {
        if (chargingVoltage > 0 && chargingCurrent > 0) {
            this.chargingPower = chargingVoltage * chargingCurrent;
        }
    }
    
    /**
     * 获取健康等级
     */
    public String getHealthGrade() {
        if (healthPercentage >= 90) {
            return "A+";
        } else if (healthPercentage >= 85) {
            return "A";
        } else if (healthPercentage >= 80) {
            return "B+";
        } else if (healthPercentage >= 75) {
            return "B";
        } else if (healthPercentage >= 70) {
            return "C";
        } else if (healthPercentage >= 60) {
            return "D";
        } else {
            return "E";
        }
    }
    
    /**
     * 获取健康描述
     */
    public String getHealthDescription() {
        if (healthPercentage >= 90) {
            return "电池状态极佳";
        } else if (healthPercentage >= 80) {
            return "电池状态良好";
        } else if (healthPercentage >= 70) {
            return "电池状态一般";
        } else {
            return "建议更换电池";
        }
    }
}