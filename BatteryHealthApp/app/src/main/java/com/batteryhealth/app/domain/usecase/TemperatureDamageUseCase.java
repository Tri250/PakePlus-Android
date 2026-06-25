package com.batteryhealth.app.domain.usecase;

import com.batteryhealth.app.data.model.BatteryInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 温度累积伤害评估 UseCase
 *
 * 功能：
 * 1. 基于历史温度数据计算 Arrhenius 方程的累积伤害
 * 2. 高温区间分级：35-40℃(轻度)、40-45℃(中度)、45-50℃(重度)、>50℃(极重)
 * 3. 计算各温度区间累计时长
 * 4. 输出温度伤害评分和建议
 * 5. 关联容量衰减预测
 */
public class TemperatureDamageUseCase {

    private static final float REF_TEMP_K = 298.15f;
    private static final float ACTIVATION_ENERGY = 50000f;
    private static final float GAS_CONSTANT = 8.314f;

    private static final float TEMP_MILD_LOW = 35f;
    private static final float TEMP_MILD_HIGH = 40f;
    private static final float TEMP_MODERATE_LOW = 40f;
    private static final float TEMP_MODERATE_HIGH = 45f;
    private static final float TEMP_SEVERE_LOW = 45f;
    private static final float TEMP_SEVERE_HIGH = 50f;
    private static final float TEMP_CRITICAL_LOW = 50f;

    public Result execute(List<BatteryInfo> history) {
        Result result = new Result();

        if (history == null || history.isEmpty()) {
            result.hasData = false;
            return result;
        }

        result.hasData = true;

        List<BatteryInfo> sortedList = new ArrayList<>(history);
        sortedList.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

        calculateTemperatureDurations(result, sortedList);
        calculateArrheniusDamage(result, sortedList);
        calculateDamageScore(result);
        generateAdvice(result);
        predictCapacityDecay(result, sortedList);

        return result;
    }

    private void calculateTemperatureDurations(Result result, List<BatteryInfo> sortedList) {
        long mildDurationMs = 0;
        long moderateDurationMs = 0;
        long severeDurationMs = 0;
        long criticalDurationMs = 0;
        long totalValidDurationMs = 0;

        long prevTimestamp = -1;
        float prevTemp = -1;

        for (int i = 0; i < sortedList.size(); i++) {
            BatteryInfo info = sortedList.get(i);
            float temp = info.getTemperature();
            long timestamp = info.getTimestamp();

            if (temp <= 0) {
                prevTimestamp = -1;
                prevTemp = -1;
                continue;
            }

            if (prevTimestamp > 0 && prevTemp > 0) {
                long intervalMs = timestamp - prevTimestamp;
                if (intervalMs > 0 && intervalMs < 2 * 60 * 60 * 1000) {
                    float avgTemp = (prevTemp + temp) / 2f;
                    totalValidDurationMs += intervalMs;

                    if (avgTemp >= TEMP_CRITICAL_LOW) {
                        criticalDurationMs += intervalMs;
                    } else if (avgTemp >= TEMP_SEVERE_LOW && avgTemp < TEMP_SEVERE_HIGH) {
                        severeDurationMs += intervalMs;
                    } else if (avgTemp >= TEMP_MODERATE_LOW && avgTemp < TEMP_MODERATE_HIGH) {
                        moderateDurationMs += intervalMs;
                    } else if (avgTemp >= TEMP_MILD_LOW && avgTemp < TEMP_MILD_HIGH) {
                        mildDurationMs += intervalMs;
                    }
                }
            }

            prevTimestamp = timestamp;
            prevTemp = temp;
        }

        result.mildDurationMs = mildDurationMs;
        result.moderateDurationMs = moderateDurationMs;
        result.severeDurationMs = severeDurationMs;
        result.criticalDurationMs = criticalDurationMs;
        result.totalHighTempDurationMs = mildDurationMs + moderateDurationMs + severeDurationMs + criticalDurationMs;
        result.totalValidDurationMs = totalValidDurationMs;

        result.mildDurationHours = mildDurationMs / (1000f * 60 * 60);
        result.moderateDurationHours = moderateDurationMs / (1000f * 60 * 60);
        result.severeDurationHours = severeDurationMs / (1000f * 60 * 60);
        result.criticalDurationHours = criticalDurationMs / (1000f * 60 * 60);
        result.totalHighTempDurationHours = result.totalHighTempDurationMs / (1000f * 60 * 60);
    }

    private void calculateArrheniusDamage(Result result, List<BatteryInfo> sortedList) {
        double cumulativeDamage = 0;
        double totalReferenceDamage = 0;

        long prevTimestamp = -1;
        float prevTemp = -1;

        for (int i = 0; i < sortedList.size(); i++) {
            BatteryInfo info = sortedList.get(i);
            float temp = info.getTemperature();
            long timestamp = info.getTimestamp();

            if (temp <= 0) {
                prevTimestamp = -1;
                prevTemp = -1;
                continue;
            }

            if (prevTimestamp > 0 && prevTemp > 0) {
                long intervalMs = timestamp - prevTimestamp;
                if (intervalMs > 0 && intervalMs < 2 * 60 * 60 * 1000) {
                    float avgTemp = (prevTemp + temp) / 2f;
                    float tempK = avgTemp + 273.15f;

                    double arrheniusFactor = Math.exp(
                            ACTIVATION_ENERGY / GAS_CONSTANT * (1 / REF_TEMP_K - 1 / tempK)
                    );

                    cumulativeDamage += arrheniusFactor * intervalMs;
                    totalReferenceDamage += intervalMs;
                }
            }

            prevTimestamp = timestamp;
            prevTemp = temp;
        }

        result.cumulativeDamage = cumulativeDamage;
        result.avgDamageRatio = totalReferenceDamage > 0 ? (float) (cumulativeDamage / totalReferenceDamage) : 1f;
    }

    private void calculateDamageScore(Result result) {
        float damageRatio = result.avgDamageRatio;

        float score = 100f;

        if (damageRatio <= 1.0f) {
            score = 95f;
        } else if (damageRatio <= 1.5f) {
            score = 90f - (damageRatio - 1.0f) * 20f;
        } else if (damageRatio <= 2.0f) {
            score = 80f - (damageRatio - 1.5f) * 40f;
        } else if (damageRatio <= 3.0f) {
            score = 60f - (damageRatio - 2.0f) * 30f;
        } else {
            score = Math.max(10f, 30f - (damageRatio - 3.0f) * 10f);
        }

        if (result.criticalDurationHours > 1) {
            score -= 15f;
        }
        if (result.severeDurationHours > 5) {
            score -= 10f;
        }
        if (result.moderateDurationHours > 20) {
            score -= 5f;
        }

        result.damageScore = Math.max(0, Math.min(100, score));

        if (result.damageScore >= 85) {
            result.damageGrade = "A";
            result.damageLevel = "温度伤害极低";
        } else if (result.damageScore >= 70) {
            result.damageGrade = "B";
            result.damageLevel = "温度伤害较低";
        } else if (result.damageScore >= 55) {
            result.damageGrade = "C";
            result.damageLevel = "温度伤害中等";
        } else if (result.damageScore >= 40) {
            result.damageGrade = "D";
            result.damageLevel = "温度伤害较高";
        } else {
            result.damageGrade = "E";
            result.damageLevel = "温度伤害严重";
        }
    }

    private void generateAdvice(Result result) {
        List<String> tips = new ArrayList<>();

        if (result.criticalDurationHours > 0.5) {
            tips.add("检测到极高温（>50℃）使用记录，严重影响电池寿命，建议避免在极端高温环境下使用手机");
        }

        if (result.severeDurationHours > 2) {
            tips.add("重度高温（45-50℃）时长较长，建议充电时取下手机壳，避免边充边玩");
        }

        if (result.moderateDurationHours > 10) {
            tips.add("中度高温（40-45℃）时长较多，建议避免在阳光直射下使用和充电");
        }

        if (result.mildDurationHours > 30) {
            tips.add("轻度高温（35-40℃）时长较多，注意保持手机通风散热");
        }

        if (result.totalHighTempDurationHours > 50) {
            tips.add("总体高温暴露时间较长，建议尽量在室温环境下使用和充电");
        }

        if (result.damageScore >= 80) {
            tips.add("温度控制良好，继续保持良好的使用习惯");
        }

        if (tips.isEmpty()) {
            tips.add("温度控制良好，电池处于健康温度范围");
        }

        result.adviceList = tips;
    }

    private void predictCapacityDecay(Result result, List<BatteryInfo> sortedList) {
        if (sortedList.size() < 2) {
            result.predictedMonthlyDecayFromTemp = -1;
            return;
        }

        float damageRatio = result.avgDamageRatio;
        float baseDecayPerCycle = 0.0025f;

        float predictedDecay = baseDecayPerCycle * damageRatio * 30f;

        result.predictedMonthlyDecayFromTemp = Math.max(0.5f, Math.min(10f, predictedDecay));
    }

    public static class Result {
        public boolean hasData;
        public float damageScore;
        public String damageGrade;
        public String damageLevel;
        public float avgDamageRatio;
        public double cumulativeDamage;

        public long mildDurationMs;
        public long moderateDurationMs;
        public long severeDurationMs;
        public long criticalDurationMs;
        public long totalHighTempDurationMs;
        public long totalValidDurationMs;

        public float mildDurationHours;
        public float moderateDurationHours;
        public float severeDurationHours;
        public float criticalDurationHours;
        public float totalHighTempDurationHours;

        public float predictedMonthlyDecayFromTemp;
        public List<String> adviceList;

        public Result() {
            hasData = false;
            damageScore = 0;
            damageGrade = "--";
            damageLevel = "";
            avgDamageRatio = 1f;
            cumulativeDamage = 0;
            predictedMonthlyDecayFromTemp = -1;
            adviceList = new ArrayList<>();
        }

        public String getTotalHighTempDurationText() {
            float hours = totalHighTempDurationHours;
            if (hours < 1) {
                return String.format(Locale.getDefault(), "%.0f 分钟", hours * 60);
            } else if (hours < 24) {
                return String.format(Locale.getDefault(), "%.1f 小时", hours);
            } else {
                float days = hours / 24f;
                return String.format(Locale.getDefault(), "%.1f 天", days);
            }
        }
    }
}
