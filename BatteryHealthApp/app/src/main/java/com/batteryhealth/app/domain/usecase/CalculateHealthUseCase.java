package com.batteryhealth.app.domain.usecase;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.domain.repository.DeviceRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CalculateHealthUseCase {

    private static final int MEDIAN_WINDOW = 5;
    private final List<Float> healthBuffer = new ArrayList<>();

    private final BatteryRepository batteryRepository;
    private final DeviceRepository deviceRepository;

    public CalculateHealthUseCase(BatteryRepository batteryRepository, 
                                  DeviceRepository deviceRepository) {
        this.batteryRepository = batteryRepository;
        this.deviceRepository = deviceRepository;
    }

    public Result execute(int designCapacity, int currentCapacity, 
                          int cycleCount, int usageDays) {
        Result result = new Result();

        if (currentCapacity > 0 && designCapacity > 0) {
            float ratio = (currentCapacity / (float) designCapacity) * 100f;
            float clampedHealth = clampHealth(ratio);
            float filteredHealth = applyMedianFilter(clampedHealth);

            result.healthPercentage = filteredHealth;
            result.confidence = 0.95f;
            result.source = "fcc_ratio";
            result.cycleLossPercent = Math.max(0f, 100f - ratio);
            result.healthLevel = getHealthLevel(filteredHealth);
            result.healthStatus = getHealthStatus(result.healthLevel);
            return result;
        }

        if (usageDays > 0 && designCapacity > 0) {
            float daysLoss = usageDays * 0.026f;
            float estimatedHealth = clampHealth(100f - daysLoss);
            float filteredHealth = applyMedianFilter(estimatedHealth);

            result.healthPercentage = filteredHealth;
            result.confidence = 0.35f;
            result.source = "usage_days_estimate";
            result.usageLossPercent = daysLoss;
            result.healthLevel = getHealthLevel(filteredHealth);
            result.healthStatus = getHealthStatus(result.healthLevel);
            return result;
        }

        result.healthPercentage = -1;
        result.source = "no_data";
        result.healthLevel = "unknown";
        result.healthStatus = "无法获取电池健康数据";
        result.confidence = 0f;
        return result;
    }

    public Result execute(BatteryInfo info) {
        if (info == null) {
            Result result = new Result();
            result.healthPercentage = -1;
            result.source = "no_data";
            result.healthLevel = "unknown";
            result.healthStatus = "无法获取电池健康数据";
            result.confidence = 0f;
            return result;
        }

        int usageDays = deviceRepository.getUsageDays();
        return execute(info.getDesignCapacity(), info.getCurrentCapacity(), 
                       info.getCycleCount(), usageDays);
    }

    private float clampHealth(float v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    private float applyMedianFilter(float currentValue) {
        if (currentValue < 0) return currentValue;
        synchronized (healthBuffer) {
            healthBuffer.add(currentValue);
            if (healthBuffer.size() > MEDIAN_WINDOW) {
                healthBuffer.remove(0);
            }
            if (healthBuffer.size() < 3) return currentValue;
            List<Float> sorted = new ArrayList<>(healthBuffer);
            Collections.sort(sorted);
            int mid = sorted.size() / 2;
            if (sorted.size() % 2 == 0) {
                return (sorted.get(mid - 1) + sorted.get(mid)) / 2f;
            }
            return sorted.get(mid);
        }
    }

    private String getHealthLevel(float p) {
        if (p < 0) return "unknown";
        if (p >= 95) return "excellent";
        if (p >= 85) return "good";
        if (p >= 75) return "average";
        if (p >= 60) return "poor";
        return "very_poor";
    }

    private String getHealthStatus(String level) {
        switch (level) {
            case "excellent": return "电池状态极佳";
            case "good": return "电池状态良好";
            case "average": return "电池状态一般";
            case "poor": return "电池损耗明显";
            case "very_poor": return "建议尽快更换电池";
            default: return "健康状态未知";
        }
    }

    public String calculateGrade(float health) {
        if (health >= 95) return "A+";
        if (health >= 90) return "A";
        if (health >= 85) return "A-";
        if (health >= 80) return "B+";
        if (health >= 75) return "B";
        if (health >= 70) return "B-";
        if (health >= 60) return "C";
        return "D";
    }

    public static class Result {
        public float healthPercentage;
        public String healthLevel;
        public String healthStatus;
        public String source;
        public float confidence;
        public float factoryLossPercent;
        public float cycleLossPercent;
        public float usageLossPercent;
    }
}