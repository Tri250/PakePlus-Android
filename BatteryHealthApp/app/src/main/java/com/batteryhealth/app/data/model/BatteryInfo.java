package com.batteryhealth.app.data.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

/**
 * 电池信息实体类
 *
 * 存储电池健康度、容量、温度、循环次数等核心指标
 *
 * 注意：
 * - timestamp 字段建立了索引，加速按时间范围查询（DAO 中所有时间相关查询）。
 * - health_percentage 字段建立了索引，加速按健康度排序/筛选查询。
 */
@Entity(tableName = "battery_info",
        indices = {
                @Index(value = {"timestamp"}),
                @Index(value = {"health_percentage"})
        })
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

    @ColumnInfo(name = "cycle_count_source")
    private String cycleCountSource; // 循环次数来源

    @ColumnInfo(name = "design_capacity_source")
    private String designCapacitySource; // 设计容量来源

    @ColumnInfo(name = "current_capacity_source")
    private String currentCapacitySource; // 当前容量来源

    @ColumnInfo(name = "health_data_source")
    private String healthDataSource; // 健康度数据来源

    @ColumnInfo(name = "health_confidence")
    private float healthConfidence; // 健康度置信度 0-1

    @ColumnInfo(name = "system_health")
    private int systemHealth; // 系统 BATTERY_HEALTH 状态

    @ColumnInfo(name = "energy_counter")
    private int energyCounter; // 能量计数器 (uWh)

    @ColumnInfo(name = "battery_source_confidence")
    private float batterySourceConfidence; // 电池来源置信度 0-1

    @ColumnInfo(name = "factory_loss_percent")
    private float factoryLossPercent; // 出厂损耗百分比

    @ColumnInfo(name = "cycle_loss_percent")
    private float cycleLossPercent; // 循环损耗百分比

    @ColumnInfo(name = "usage_loss_percent")
    private float usageLossPercent; // 使用时长损耗百分比

    @ColumnInfo(name = "battery_source_reason")
    private String batterySourceReason; // 电池来源判定原因

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

    public String getCycleCountSource() {
        return cycleCountSource;
    }

    public void setCycleCountSource(String cycleCountSource) {
        this.cycleCountSource = cycleCountSource;
    }

    public String getDesignCapacitySource() {
        return designCapacitySource;
    }

    public void setDesignCapacitySource(String designCapacitySource) {
        this.designCapacitySource = designCapacitySource;
    }

    public String getCurrentCapacitySource() {
        return currentCapacitySource;
    }

    public void setCurrentCapacitySource(String currentCapacitySource) {
        this.currentCapacitySource = currentCapacitySource;
    }

    public String getHealthDataSource() {
        return healthDataSource;
    }

    public void setHealthDataSource(String healthDataSource) {
        this.healthDataSource = healthDataSource;
    }

    public float getHealthConfidence() {
        return healthConfidence;
    }

    public void setHealthConfidence(float healthConfidence) {
        this.healthConfidence = healthConfidence;
    }

    public int getSystemHealth() {
        return systemHealth;
    }

    public void setSystemHealth(int systemHealth) {
        this.systemHealth = systemHealth;
    }

    public int getEnergyCounter() {
        return energyCounter;
    }

    public void setEnergyCounter(int energyCounter) {
        this.energyCounter = energyCounter;
    }

    public float getBatterySourceConfidence() {
        return batterySourceConfidence;
    }

    public void setBatterySourceConfidence(float batterySourceConfidence) {
        this.batterySourceConfidence = batterySourceConfidence;
    }

    public float getFactoryLossPercent() {
        return factoryLossPercent;
    }

    public void setFactoryLossPercent(float factoryLossPercent) {
        this.factoryLossPercent = factoryLossPercent;
    }

    public float getCycleLossPercent() {
        return cycleLossPercent;
    }

    public void setCycleLossPercent(float cycleLossPercent) {
        this.cycleLossPercent = cycleLossPercent;
    }

    public float getUsageLossPercent() {
        return usageLossPercent;
    }

    public void setUsageLossPercent(float usageLossPercent) {
        this.usageLossPercent = usageLossPercent;
    }

    public String getBatterySourceReason() {
        return batterySourceReason;
    }

    public void setBatterySourceReason(String batterySourceReason) {
        this.batterySourceReason = batterySourceReason;
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
     *
     * 阈值与 BatteryDataManager / getHealthDescription() 保持一致：
     * 95+ A+，85-94 A，75-84 B，60-74 C，<60 D。
     */
    public String getHealthGrade() {
        if (healthPercentage < 0) {
            return "--";
        } else if (healthPercentage >= 95) {
            return "A+";
        } else if (healthPercentage >= 85) {
            return "A";
        } else if (healthPercentage >= 75) {
            return "B";
        } else if (healthPercentage >= 60) {
            return "C";
        } else {
            return "D";
        }
    }
    
    /**
     * 获取健康描述
     *
     * 阈值与 BatteryDataManager 的计算口径保持一致：
     * 95+ 极佳，85+ 良好，75+ 一般，60+ 较差，<60 极差。
     */
    public String getHealthDescription() {
        // 注意：此处返回硬编码字符串仅作为数据模型默认值，实际展示文本由 UI 层通过 strings.xml 控制
        if (healthPercentage < 0) {
            return "无法获取电池健康数据";
        } else if (healthPercentage >= 95) {
            return "电池状态极佳";
        } else if (healthPercentage >= 85) {
            return "电池状态良好";
        } else if (healthPercentage >= 75) {
            return "电池状态一般";
        } else if (healthPercentage >= 60) {
            return "电池损耗明显";
        } else {
            return "建议尽快更换电池";
        }
    }
    
    /**
     * 是否有有效的健康度数据
     */
    public boolean hasValidHealthData() {
        return healthPercentage >= 0;
    }
    
    /**
     * 是否有有效的循环次数数据
     */
    public boolean hasValidCycleCount() {
        return cycleCount >= 0;
    }

    /**
     * 当前是否处于充电状态（status=2 充电中，5 已充满）。
     */
    public boolean isCharging() {
        return status == 2 || status == 5;
    }

    /**
     * 创建当前对象的深拷贝，避免 Gson 序列化/反序列化的性能开销。
     */
    public BatteryInfo copy() {
        BatteryInfo snapshot = new BatteryInfo();
        snapshot.id = this.id;
        snapshot.timestamp = this.timestamp;
        snapshot.designCapacity = this.designCapacity;
        snapshot.currentCapacity = this.currentCapacity;
        snapshot.chargeCounter = this.chargeCounter;
        snapshot.healthPercentage = this.healthPercentage;
        snapshot.healthStatus = this.healthStatus;
        snapshot.cycleCount = this.cycleCount;
        snapshot.temperature = this.temperature;
        snapshot.voltage = this.voltage;
        snapshot.currentNow = this.currentNow;
        snapshot.status = this.status;
        snapshot.plugged = this.plugged;
        snapshot.level = this.level;
        snapshot.technology = this.technology;
        snapshot.batterySource = this.batterySource;
        snapshot.batterySerial = this.batterySerial;
        snapshot.chargingPower = this.chargingPower;
        snapshot.chargingVoltage = this.chargingVoltage;
        snapshot.chargingCurrent = this.chargingCurrent;
        snapshot.deviceModel = this.deviceModel;
        snapshot.deviceBrand = this.deviceBrand;
        snapshot.cycleCountEstimated = this.cycleCountEstimated;
        snapshot.cycleCountSource = this.cycleCountSource;
        snapshot.designCapacitySource = this.designCapacitySource;
        snapshot.currentCapacitySource = this.currentCapacitySource;
        snapshot.healthDataSource = this.healthDataSource;
        snapshot.healthConfidence = this.healthConfidence;
        snapshot.systemHealth = this.systemHealth;
        snapshot.energyCounter = this.energyCounter;
        snapshot.batterySourceConfidence = this.batterySourceConfidence;
        snapshot.factoryLossPercent = this.factoryLossPercent;
        snapshot.cycleLossPercent = this.cycleLossPercent;
        snapshot.usageLossPercent = this.usageLossPercent;
        snapshot.batterySourceReason = this.batterySourceReason;
        return snapshot;
    }
}