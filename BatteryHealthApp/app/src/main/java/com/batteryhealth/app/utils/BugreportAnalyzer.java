package com.batteryhealth.app.utils;

import android.util.Log;

import java.util.List;

/**
 * Bugreport 电池/性能数据分析器。
 *
 * 在 {@link BugreportParser} 提取的原始字段基础上，进行置信度评估、异常检测
 * 与结构化输出，供 UI 层直接展示并持久化到 SharedPreferences。
 */
public class BugreportAnalyzer {

    private static final String TAG = "BugreportAnalyzer";

    /**
     * 对解析结果进行综合分析。
     *
     * @param parsed BugreportParser 输出的原始数据
     * @return 结构化分析结果
     */
    public AnalysisResult analyze(BugreportParser.ParsedResult parsed) {
        AnalysisResult result = new AnalysisResult();
        if (parsed == null) return result;

        result.designCapacity = parsed.designCapacityMah;
        result.fullCapacity = parsed.fullCapacityMah;
        result.cycleCount = parsed.cycleCount;
        result.voltage = parsed.voltageMv;
        result.temperature = parsed.temperatureC;
        result.technology = parsed.technology != null ? parsed.technology : "Li-ion";
        result.chargingPolicy = parsed.chargingPolicy != null ? parsed.chargingPolicy : "unknown";

        // 健康度：优先使用 bugreport 中已计算百分比，否则用 FCC / 设计容量
        if (parsed.batteryHealthPercent > 0 && parsed.batteryHealthPercent <= 100) {
            result.batteryHealth = parsed.batteryHealthPercent;
            result.healthConfidence = 0.9f;
        } else if (parsed.fullCapacityMah > 0 && parsed.designCapacityMah > 0) {
            result.batteryHealth = (int) (parsed.fullCapacityMah * 100.0 / parsed.designCapacityMah);
            result.healthConfidence = 0.75f;
        } else {
            result.batteryHealth = -1;
            result.healthConfidence = 0f;
        }

        // 基于快照统计平均温度和最高温度
        result.avgTemperature = computeAvgTemperature(parsed.batterySnapshots);
        result.maxTemperature = computeMaxTemperature(parsed.batterySnapshots);

        // 简单性能评估：温度高于 45°C 认为存在过热风险
        result.hasThermalRisk = result.maxTemperature > 45.0;

        Log.d(TAG, "Analyzed: health=" + result.batteryHealth
                + " cycles=" + result.cycleCount
                + " temp=" + result.temperature);
        return result;
    }

    private double computeAvgTemperature(List<BugreportParser.BatterySnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return 0;
        double sum = 0;
        int valid = 0;
        for (BugreportParser.BatterySnapshot s : snapshots) {
            if (s.temperatureDeciC > 0) {
                sum += s.temperatureDeciC / 10.0;
                valid++;
            }
        }
        return valid > 0 ? sum / valid : 0;
    }

    private double computeMaxTemperature(List<BugreportParser.BatterySnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return 0;
        double max = 0;
        for (BugreportParser.BatterySnapshot s : snapshots) {
            double t = s.temperatureDeciC / 10.0;
            if (t > max) max = t;
        }
        return max;
    }

    /**
     * 分析结果容器。
     */
    public static class AnalysisResult {
        public int batteryHealth = -1;        // 健康度百分比
        public int cycleCount = -1;           // 循环次数
        public int designCapacity;            // 设计容量 mAh
        public int fullCapacity;              // 满充容量 mAh
        public int voltage;                   // 电压 mV
        public double temperature;            // 当前温度 °C
        public double avgTemperature;         // 平均温度 °C
        public double maxTemperature;         // 最高温度 °C
        public String technology = "Li-ion";  // 电池技术
        public String chargingPolicy = "unknown"; // 充电策略
        public float healthConfidence;        // 健康度置信度 0-1
        public boolean hasThermalRisk;        // 是否存在过热风险
    }
}
