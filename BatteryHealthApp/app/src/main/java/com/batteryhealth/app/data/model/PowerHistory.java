package com.batteryhealth.app.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;

/**
 * 充电功率历史记录实体类
 *
 * 存储充电过程中的功率变化数据
 */
@Entity(tableName = "power_history",
        indices = {
                @Index(value = {"session_id"}),
                @Index(value = {"timestamp"})
        })
public class PowerHistory {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    // 功率信息
    @ColumnInfo(name = "power")
    private float power; // 充电功率 (W)

    @ColumnInfo(name = "voltage")
    private float voltage; // 电压 (V)

    @ColumnInfo(name = "current")
    private float current; // 电流 (A)

    // 电池状态
    @ColumnInfo(name = "battery_level")
    private int batteryLevel; // 电池电量百分比

    @ColumnInfo(name = "battery_temp")
    private float batteryTemp; // 电池温度 (°C)

    // 充电阶段
    @ColumnInfo(name = "charging_phase")
    private String chargingPhase; // 充电阶段: trickle, constant_current, constant_voltage, full

    // 充电类型
    @ColumnInfo(name = "charge_type")
    private String chargeType; // 充电类型: normal, fast, super, wireless

    // 会话ID (用于区分不同充电会话)
    @ColumnInfo(name = "session_id")
    private String sessionId;

    public PowerHistory() {
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

    public float getPower() {
        return power;
    }

    public void setPower(float power) {
        this.power = power;
    }

    public float getVoltage() {
        return voltage;
    }

    public void setVoltage(float voltage) {
        this.voltage = voltage;
    }

    public float getCurrent() {
        return current;
    }

    public void setCurrent(float current) {
        this.current = current;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public float getBatteryTemp() {
        return batteryTemp;
    }

    public void setBatteryTemp(float batteryTemp) {
        this.batteryTemp = batteryTemp;
    }

    public String getChargingPhase() {
        return chargingPhase;
    }

    public void setChargingPhase(String chargingPhase) {
        this.chargingPhase = chargingPhase;
    }

    public String getChargeType() {
        return chargeType;
    }

    public void setChargeType(String chargeType) {
        this.chargeType = chargeType;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 计算功率瓦数。
     *
     * 注意：voltage 单位为 V，current 单位为 A。
     * 若外部传入的是 mV/mA，需先除以 1000 再调用本方法。
     */
    public void calculatePower() {
        if (voltage > 0 && current > 0) {
            // 功率 = 电压(V) × 电流(A)，结果单位为 W
            this.power = voltage * current;
        } else {
            this.power = 0;
        }
    }

    /**
     * 判断是否为快充
     */
    public boolean isFastCharge() {
        return power >= 18; // 18W以上认为是快充
    }

    /**
     * 判断是否为超级快充
     */
    public boolean isSuperCharge() {
        return power >= 40; // 40W以上认为是超级快充
    }

    /**
     * 获取充电类型描述
     */
    public String getChargeTypeDescription() {
        if (power >= 100) {
            return "超快闪充";
        } else if (power >= 60) {
            return "超级快充";
        } else if (power >= 40) {
            return "快速充电";
        } else if (power >= 18) {
            return "普通快充";
        } else if (power >= 10) {
            return "标准充电";
        } else {
            return "慢速充电";
        }
    }

    /**
     * 获取充电阶段描述
     */
    public String getChargingPhaseDescription() {
        if (chargingPhase == null) return "未知";

        switch (chargingPhase) {
            case "trickle":
                return "涓流充电";
            case "constant_current":
                return "恒流充电";
            case "constant_voltage":
                return "恒压充电";
            case "full":
                return "充电完成";
            default:
                return "充电中";
        }
    }
}
