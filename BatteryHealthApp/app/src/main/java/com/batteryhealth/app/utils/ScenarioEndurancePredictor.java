package com.batteryhealth.app.utils;

import android.content.Context;
import android.os.BatteryManager;

import java.util.HashMap;
import java.util.Map;

/**
 * 多场景续航预测工具
 * 基于当前电量和不同场景的典型功耗预估续航时间
 */
public class ScenarioEndurancePredictor {

    public static final String SCENARIO_CALL = "通话";
    public static final String SCENARIO_GAME = "游戏";
    public static final String SCENARIO_VIDEO = "视频";
    public static final String SCENARIO_NAVIGATION = "导航";
    public static final String SCENARIO_READING = "阅读";

    public static class ScenarioPrediction {
        public String scenarioName;
        public float enduranceHours;
        public String formattedTime;
        public float powerConsumptionRate;
        public String description;
    }

    private static final Map<String, Float> SCENARIO_POWER_FACTORS = new HashMap<>();

    static {
        SCENARIO_POWER_FACTORS.put(SCENARIO_CALL, 0.6f);
        SCENARIO_POWER_FACTORS.put(SCENARIO_GAME, 2.5f);
        SCENARIO_POWER_FACTORS.put(SCENARIO_VIDEO, 1.2f);
        SCENARIO_POWER_FACTORS.put(SCENARIO_NAVIGATION, 1.8f);
        SCENARIO_POWER_FACTORS.put(SCENARIO_READING, 0.3f);
    }

    private static final Map<String, String> SCENARIO_DESCRIPTIONS = new HashMap<>();

    static {
        SCENARIO_DESCRIPTIONS.put(SCENARIO_CALL, "持续通话状态，屏幕常亮+蜂窝网络通话");
        SCENARIO_DESCRIPTIONS.put(SCENARIO_GAME, "高性能游戏状态，CPU/GPU满载运行");
        SCENARIO_DESCRIPTIONS.put(SCENARIO_VIDEO, "在线视频播放，屏幕常亮+网络流媒体");
        SCENARIO_DESCRIPTIONS.put(SCENARIO_NAVIGATION, "GPS导航状态，屏幕常亮+GPS+网络");
        SCENARIO_DESCRIPTIONS.put(SCENARIO_READING, "阅读状态，低亮度+静态画面");
    }

    public static Map<String, ScenarioPrediction> predictAll(Context context, int batteryLevel) {
        Map<String, ScenarioPrediction> predictions = new HashMap<>();

        float baselineRate = getBaselineDischargeRate(context);
        if (baselineRate <= 0) {
            baselineRate = 8f;
        }

        for (Map.Entry<String, Float> entry : SCENARIO_POWER_FACTORS.entrySet()) {
            String scenario = entry.getKey();
            float factor = entry.getValue();
            float scenarioRate = baselineRate * factor;

            ScenarioPrediction prediction = new ScenarioPrediction();
            prediction.scenarioName = scenario;
            prediction.powerConsumptionRate = scenarioRate;
            prediction.description = SCENARIO_DESCRIPTIONS.get(scenario);

            if (scenarioRate > 0 && batteryLevel > 0) {
                prediction.enduranceHours = batteryLevel / scenarioRate;
                prediction.formattedTime = formatHours(prediction.enduranceHours);
            } else {
                prediction.enduranceHours = 0;
                prediction.formattedTime = "--";
            }

            predictions.put(scenario, prediction);
        }

        return predictions;
    }

    private static float getBaselineDischargeRate(Context context) {
        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
                if (currentAvg == 0 || currentAvg == Integer.MIN_VALUE) {
                    currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                }
                int capacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                if (capacity == Integer.MIN_VALUE || capacity == 0) {
                    capacity = bm.getIntProperty(24);
                }

                int capacityMah = -1;
                if (capacity > 100000) capacityMah = capacity / 1000;
                else if (capacity > 100) capacityMah = capacity;

                float currentMa = Math.abs(currentAvg / 1000f);
                if (capacityMah > 0 && currentMa > 0) {
                    float totalHours = capacityMah / currentMa;
                    if (totalHours > 0) {
                        return 100f / totalHours;
                    }
                }
            }
        } catch (Exception ignored) {}

        return 8f;
    }

    public static String formatHours(float hours) {
        if (hours <= 0) return "--";
        int h = (int) hours;
        int m = (int) ((hours - h) * 60);
        if (h > 0) {
            return h + "小时" + m + "分";
        }
        return m + "分钟";
    }
}
