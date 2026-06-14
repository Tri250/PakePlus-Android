package com.batteryhealth.app;

/**
 * 电池解析结果数据类
 */
public class BatteryParseResult {
    // 基础信息
    public String brand;
    public String model;
    public String sn;

    // 容量数据
    public int designCapacityMah;      // 设计容量 (mAh)
    public int currentCapacityMah;     // 当前容量 (mAh)
    public int chargeCounterMah;       // 电量计数器 (mAh)

    // 循环与寿命
    public int cycleCount;             // 循环次数
    public String manufacturingDate;   // 制造日期

    // 温度
    public float temperatureCelsius;   // 温度 (°C)

    // 使用统计
    public int screenOnTimeHours;      // 亮屏时间 (小时)
    public int chargeCount;            // 充电次数

    // 状态
    public boolean hasData;            // 是否有有效数据

    // 默认构造函数
    public BatteryParseResult() {
        designCapacityMah = 0;
        currentCapacityMah = 0;
        chargeCounterMah = 0;
        cycleCount = 0;
        temperatureCelsius = 0f;
        screenOnTimeHours = 0;
        chargeCount = 0;
        hasData = false;
    }

    // 获取容量保持率
    public float getCapacityRetention() {
        if (designCapacityMah > 0 && currentCapacityMah > 0) {
            return (currentCapacityMah * 100f) / designCapacityMah;
        }
        return 0f;
    }

    // 获取容量保持率描述
    public String getCapacityRetentionText() {
        float retention = getCapacityRetention();
        if (retention > 0) {
            return String.format("%.1f%%", retention);
        }
        return "未知";
    }

    // 获取设计容量描述
    public String getDesignCapacityText() {
        if (designCapacityMah > 0) {
            return designCapacityMah + " mAh";
        }
        return "未知";
    }

    // 获取当前容量描述
    public String getCurrentCapacityText() {
        if (currentCapacityMah > 0) {
            return currentCapacityMah + " mAh";
        }
        return "未知";
    }

    // 获取循环次数描述
    public String getCycleCountText() {
        if (cycleCount > 0) {
            return cycleCount + " 次";
        }
        return "未知";
    }

    // 获取温度描述
    public String getTemperatureText() {
        if (temperatureCelsius > 0) {
            return String.format("%.1f°C", temperatureCelsius);
        }
        return "未知";
    }

    // 获取品牌描述
    public String getBrandText() {
        if (brand != null && !brand.isEmpty()) {
            return brand;
        }
        return "未知";
    }

    // 获取型号描述
    public String getModelText() {
        if (model != null && !model.isEmpty()) {
            return model;
        }
        return "未知";
    }

    // 获取数据完整性描述
    public String getDataCompletenessText() {
        int count = 0;
        if (brand != null && !brand.isEmpty()) count++;
        if (model != null && !model.isEmpty()) count++;
        if (designCapacityMah > 0) count++;
        if (currentCapacityMah > 0) count++;
        if (cycleCount > 0) count++;
        if (temperatureCelsius > 0) count++;

        return count + "/6 项数据";
    }
}