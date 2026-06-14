package com.batteryhealth.app;

/**
 * 健康因子数据类
 */
public class HealthFactors {
    // 各因子百分比 (0-100)
    public float capacityRetention;    // 容量保持率
    public float cycleDecay;           // 循环衰减
    public float resistanceGrowth;     // 内阻增长
    public float thermalAging;         // 温度老化
    public float chargingDamage;       // 充电损伤

    // 可用因子数量
    public int availableFactors;

    public HealthFactors() {
        capacityRetention = 0f;
        cycleDecay = 0f;
        resistanceGrowth = 0f;
        thermalAging = 0f;
        chargingDamage = 0f;
        availableFactors = 0;
    }

    // 获取平均分
    public float getAverageScore() {
        if (availableFactors == 0) return 0f;
        float sum = 0f;
        int count = 0;
        if (capacityRetention > 0) { sum += capacityRetention; count++; }
        if (cycleDecay > 0) { sum += cycleDecay; count++; }
        if (resistanceGrowth > 0) { sum += resistanceGrowth; count++; }
        if (thermalAging > 0) { sum += thermalAging; count++; }
        if (chargingDamage > 0) { sum += chargingDamage; count++; }
        return count > 0 ? sum / count : 0f;
    }
}