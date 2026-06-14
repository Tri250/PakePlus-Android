package com.batteryhealth.app;

import java.util.List;
import java.util.ArrayList;

/**
 * 电池健康度结果数据类
 */
public class BatteryHealthResult {
    // 基础结果
    public float healthPercentage;     // 健康度百分比 (0-100)
    public String grade;               // 等级: A+, A, B, C, D, F
    public String gradeColor;          // 等级颜色
    public String gradeDescription;    // 等级描述

    // 详细信息
    public String diagnosisText;       // 诊断文字
    public List<String> suggestions;   // 使用建议
    public HealthFactors factors;      // 健康因子

    // 置信度 (0=NONE, 1=LOW, 2=MEDIUM, 3=HIGH)
    public int confidence;

    // 辅助数据
    public float estimatedResistanceMohm;  // 估算内阻 (mΩ)
    public int remainingLifespanMonths;    // 预估剩余寿命 (月)

    public BatteryHealthResult() {
        healthPercentage = 0f;
        grade = "F";
        gradeColor = "#9E9E9E";
        gradeDescription = "未知状态";
        diagnosisText = "";
        suggestions = new ArrayList<>();
        factors = new HealthFactors();
        confidence = 0;
        estimatedResistanceMohm = 0f;
        remainingLifespanMonths = 0;
    }

    // 获取置信度描述
    public String getConfidenceText() {
        switch (confidence) {
            case 3: return "高";
            case 2: return "中";
            case 1: return "低";
            default: return "无";
        }
    }

    // 获取健康度百分比描述
    public String getHealthPercentageText() {
        return String.format("%.1f%%", healthPercentage);
    }

    // 获取等级完整描述
    public String getGradeFullText() {
        return grade + " - " + gradeDescription;
    }

    // 获取剩余寿命描述
    public String getRemainingLifespanText() {
        if (remainingLifespanMonths > 0) {
            return "约 " + remainingLifespanMonths + " 个月";
        }
        return "无法估算";
    }

    // 获取内阻描述
    public String getResistanceText() {
        if (estimatedResistanceMohm > 0) {
            return String.format("%.2f mΩ", estimatedResistanceMohm);
        }
        return "未估算";
    }

    // 是否有有效数据
    public boolean hasValidResult() {
        return healthPercentage > 0 || (factors != null && factors.availableFactors > 0);
    }

    // 获取因子详情描述
    public String getFactorsDetailText() {
        StringBuilder sb = new StringBuilder();
        if (factors != null) {
            if (factors.capacityRetention > 0) {
                sb.append("容量保持率: ").append(String.format("%.1f%%", factors.capacityRetention)).append("\n");
            }
            if (factors.cycleDecay > 0) {
                sb.append("循环衰减: ").append(String.format("%.1f%%", factors.cycleDecay)).append("\n");
            }
            if (factors.resistanceGrowth > 0) {
                sb.append("内阻增长: ").append(String.format("%.1f%%", factors.resistanceGrowth)).append("\n");
            }
            if (factors.thermalAging > 0) {
                sb.append("温度老化: ").append(String.format("%.1f%%", factors.thermalAging)).append("\n");
            }
            if (factors.chargingDamage > 0) {
                sb.append("充电损伤: ").append(String.format("%.1f%%", factors.chargingDamage)).append("\n");
            }
        }
        return sb.toString();
    }
}