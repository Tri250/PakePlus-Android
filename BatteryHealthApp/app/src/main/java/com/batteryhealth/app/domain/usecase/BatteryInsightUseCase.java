package com.batteryhealth.app.domain.usecase;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.domain.repository.DeviceRepository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class BatteryInsightUseCase {

    private static final long THREE_MONTHS_MS = 90L * 24 * 60 * 60 * 1000;
    private static final long ONE_WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;

    private final BatteryRepository batteryRepository;
    private final DeviceRepository deviceRepository;

    public BatteryInsightUseCase(BatteryRepository batteryRepository,
                                 DeviceRepository deviceRepository) {
        this.batteryRepository = batteryRepository;
        this.deviceRepository = deviceRepository;
    }

    public AgingPrediction predictAging() {
        AgingPrediction result = new AgingPrediction();
        try {
            long threeMonthsAgo = System.currentTimeMillis() - THREE_MONTHS_MS;
            List<BatteryInfo> history = batteryRepository.getHistorySince(threeMonthsAgo);

            if (history == null || history.size() < 7) {
                result.hasEnoughData = false;
                result.confidence = 0.3f;
                BatteryInfo current = batteryRepository.getCurrentBatteryInfo();
                result.currentHealth = current != null ? current.getHealthPercentage() : 85f;
                result.predictedHealth6Months = Math.max(60f, result.currentHealth - 5f);
                result.monthlyDecayRate = 0.8f;
                return result;
            }

            List<DataPoint> dailyHealth = aggregateDailyHealth(history);
            if (dailyHealth.size() < 7) {
                result.hasEnoughData = false;
                result.confidence = 0.4f;
                result.currentHealth = dailyHealth.isEmpty() ? 85f : dailyHealth.get(dailyHealth.size() - 1).value;
                result.predictedHealth6Months = Math.max(60f, result.currentHealth - 5f);
                result.monthlyDecayRate = 0.8f;
                return result;
            }

            float[] regression = linearRegression(dailyHealth);
            float slope = regression[0];
            float intercept = regression[1];

            float seasonalFactor = calculateSeasonalFactor(dailyHealth);

            float currentHealth = dailyHealth.get(dailyHealth.size() - 1).value;
            result.currentHealth = currentHealth;
            result.monthlyDecayRate = Math.abs(slope) * 30f;

            float[] predicted = new float[6];
            for (int i = 0; i < 6; i++) {
                float monthIndex = dailyHealth.size() + (i + 1) * 30f;
                float basePrediction = slope * monthIndex + intercept;
                float seasonalAdjustment = seasonalFactor * (float) Math.sin(2 * Math.PI * (monthIndex / 365f));
                predicted[i] = Math.max(50f, Math.min(100f, basePrediction + seasonalAdjustment));
            }
            result.predictedMonthlyHealth = predicted;
            result.predictedHealth6Months = predicted[5];

            result.confidence = Math.min(0.95f, 0.5f + (dailyHealth.size() / 180f) * 0.45f);
            result.hasEnoughData = true;

        } catch (Exception e) {
            result.hasEnoughData = false;
            result.confidence = 0.2f;
            result.currentHealth = 85f;
            result.predictedHealth6Months = 80f;
            result.monthlyDecayRate = 0.8f;
        }
        return result;
    }

    public AnomalyDetection detectAnomalies() {
        AnomalyDetection result = new AnomalyDetection();
        result.anomalies = new ArrayList<>();
        try {
            long oneWeekAgo = System.currentTimeMillis() - ONE_WEEK_MS;
            List<BatteryInfo> history = batteryRepository.getHistorySince(oneWeekAgo);

            if (history == null || history.size() < 24) {
                result.hasAnomalies = false;
                return result;
            }

            float avgDischargeRate = calculateAvgDischargeRate(history);
            float avgTemperature = calculateAvgTemperature(history);
            int deepDischargeCount = countDeepDischarges(history);

            float recentDischargeRate = calculateRecentDischargeRate(history);
            if (recentDischargeRate > avgDischargeRate * 1.5f && avgDischargeRate > 0) {
                Anomaly anomaly = new Anomaly();
                anomaly.type = Anomaly.TYPE_DISCHARGE_RATE;
                anomaly.severity = recentDischargeRate > avgDischargeRate * 2f ? Anomaly.SEVERITY_HIGH : Anomaly.SEVERITY_MEDIUM;
                anomaly.message = String.format(Locale.getDefault(),
                        "近期放电速率异常增加，平均提升 %.0f%%",
                        (recentDischargeRate / avgDischargeRate - 1) * 100);
                anomaly.suggestion = "检查是否有新增耗电应用，或屏幕亮度/后台活动增加";
                result.anomalies.add(anomaly);
            }

            float recentAvgTemp = calculateRecentAvgTemperature(history);
            if (recentAvgTemp > 35f) {
                Anomaly anomaly = new Anomaly();
                anomaly.type = Anomaly.TYPE_HIGH_TEMPERATURE;
                anomaly.severity = recentAvgTemp > 40f ? Anomaly.SEVERITY_HIGH : Anomaly.SEVERITY_MEDIUM;
                anomaly.message = String.format(Locale.getDefault(),
                        "电池持续高温，平均温度 %.1f°C", recentAvgTemp);
                anomaly.suggestion = "避免在高温环境下使用，充电时移除手机壳";
                result.anomalies.add(anomaly);
            }

            if (deepDischargeCount >= 2) {
                Anomaly anomaly = new Anomaly();
                anomaly.type = Anomaly.TYPE_DEEP_DISCHARGE;
                anomaly.severity = deepDischargeCount >= 4 ? Anomaly.SEVERITY_HIGH : Anomaly.SEVERITY_MEDIUM;
                anomaly.message = String.format(Locale.getDefault(),
                        "本周深度放电 %d 次，低于 20%% 的次数过多", deepDischargeCount);
                anomaly.suggestion = "尽量保持电量在 20%-80% 之间，避免深度放电";
                result.anomalies.add(anomaly);
            }

            result.hasAnomalies = !result.anomalies.isEmpty();

        } catch (Exception e) {
            result.hasAnomalies = false;
        }
        return result;
    }

    public ChargingSuggestion getChargingSuggestion() {
        ChargingSuggestion result = new ChargingSuggestion();
        try {
            long twoWeeksAgo = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000;
            List<BatteryInfo> history = batteryRepository.getHistorySince(twoWeeksAgo);

            if (history == null || history.size() < 48) {
                result.bestChargeStartHour = 22;
                result.bestChargeEndHour = 7;
                result.recommendedChargeCeiling = 80;
                result.suggestion = "建议夜间充电，充至 80% 最佳";
                return result;
            }

            int[] chargeStartHours = new int[24];
            int[] chargeEndHours = new int[24];
            int chargeSessions = 0;
            boolean wasCharging = false;
            long lastChargeStart = 0;

            for (BatteryInfo info : history) {
                boolean isCharging = info.isCharging();
                if (isCharging && !wasCharging) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(info.getTimestamp());
                    int hour = cal.get(Calendar.HOUR_OF_DAY);
                    chargeStartHours[hour]++;
                    lastChargeStart = info.getTimestamp();
                    chargeSessions++;
                } else if (!isCharging && wasCharging && lastChargeStart > 0) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(info.getTimestamp());
                    int hour = cal.get(Calendar.HOUR_OF_DAY);
                    chargeEndHours[hour]++;
                    lastChargeStart = 0;
                }
                wasCharging = isCharging;
            }

            int bestStartHour = 22;
            int maxStarts = 0;
            for (int i = 0; i < 24; i++) {
                if (chargeStartHours[i] > maxStarts) {
                    maxStarts = chargeStartHours[i];
                    bestStartHour = i;
                }
            }

            int bestEndHour = 7;
            int maxEnds = 0;
            for (int i = 0; i < 24; i++) {
                if (chargeEndHours[i] > maxEnds) {
                    maxEnds = chargeEndHours[i];
                    bestEndHour = i;
                }
            }

            result.bestChargeStartHour = bestStartHour;
            result.bestChargeEndHour = bestEndHour;

            float avgTemperature = calculateAvgTemperature(history);
            int cycleCount = 0;
            BatteryInfo current = batteryRepository.getCurrentBatteryInfo();
            if (current != null) {
                cycleCount = current.getCycleCount();
            }

            if (cycleCount > 500 || avgTemperature > 30f) {
                result.recommendedChargeCeiling = 80;
            } else if (cycleCount > 200) {
                result.recommendedChargeCeiling = 85;
            } else {
                result.recommendedChargeCeiling = 90;
            }

            result.suggestion = String.format(Locale.getDefault(),
                    "建议 %d:00 左右开始充电，充至 %d%% 最佳",
                    bestStartHour, result.recommendedChargeCeiling);

        } catch (Exception e) {
            result.bestChargeStartHour = 22;
            result.bestChargeEndHour = 7;
            result.recommendedChargeCeiling = 80;
            result.suggestion = "建议夜间充电，充至 80% 最佳";
        }
        return result;
    }

    public ReplacementSuggestion getReplacementSuggestion() {
        ReplacementSuggestion result = new ReplacementSuggestion();
        try {
            BatteryInfo current = batteryRepository.getCurrentBatteryInfo();
            if (current == null) {
                result.needsReplacement = false;
                result.estimatedMonthsLeft = 24;
                result.suggestion = "电池状态良好";
                return result;
            }

            float health = current.getHealthPercentage();
            int cycleCount = current.getCycleCount();
            int usageDays = deviceRepository.getUsageDays();

            AgingPrediction aging = predictAging();
            float monthlyDecay = aging.monthlyDecayRate > 0 ? aging.monthlyDecayRate : 0.8f;

            float healthThreshold = 80f;
            float monthsToThreshold = (health - healthThreshold) / monthlyDecay;
            result.estimatedMonthsLeft = Math.max(1, Math.round(monthsToThreshold));

            float usageIntensity = cycleCount > 0 ? (cycleCount / (float) Math.max(1, usageDays)) * 30f : 4f;

            if (health <= 80f || cycleCount >= 800) {
                result.needsReplacement = true;
                result.urgency = ReplacementSuggestion.URGENCY_HIGH;
                result.suggestion = "建议尽快更换电池，当前健康度已低于 80%";
            } else if (health <= 85f || cycleCount >= 500 || result.estimatedMonthsLeft <= 6) {
                result.needsReplacement = true;
                result.urgency = ReplacementSuggestion.URGENCY_MEDIUM;
                result.suggestion = "建议在未来 3-6 个月内考虑更换电池";
            } else if (health <= 90f || result.estimatedMonthsLeft <= 12) {
                result.needsReplacement = false;
                result.urgency = ReplacementSuggestion.URGENCY_LOW;
                result.suggestion = "电池状态良好，预计还可使用约 " + result.estimatedMonthsLeft + " 个月";
            } else {
                result.needsReplacement = false;
                result.urgency = ReplacementSuggestion.URGENCY_NONE;
                result.suggestion = "电池状态极佳，预计还可使用约 " + result.estimatedMonthsLeft + " 个月";
            }

            result.currentHealth = health;
            result.cycleCount = cycleCount;
            result.usageIntensity = usageIntensity;

        } catch (Exception e) {
            result.needsReplacement = false;
            result.estimatedMonthsLeft = 24;
            result.suggestion = "电池状态良好";
        }
        return result;
    }

    public WeeklyReport generateWeeklyReport() {
        WeeklyReport report = new WeeklyReport();
        try {
            long oneWeekAgo = System.currentTimeMillis() - ONE_WEEK_MS;
            List<BatteryInfo> history = batteryRepository.getHistorySince(oneWeekAgo);

            if (history == null || history.isEmpty()) {
                report.hasEnoughData = false;
                report.summary = "本周数据不足，无法生成报告";
                return report;
            }

            BatteryInfo current = batteryRepository.getCurrentBatteryInfo();
            float currentHealth = current != null ? current.getHealthPercentage() : 85f;

            List<DataPoint> dailyHealth = aggregateDailyHealth(history);
            if (dailyHealth.size() >= 2) {
                float startHealth = dailyHealth.get(0).value;
                float endHealth = dailyHealth.get(dailyHealth.size() - 1).value;
                report.weeklyHealthChange = endHealth - startHealth;
            } else {
                report.weeklyHealthChange = 0f;
            }

            report.avgTemperature = calculateAvgTemperature(history);
            report.avgDischargeRate = calculateAvgDischargeRate(history);
            report.deepDischargeCount = countDeepDischarges(history);
            report.chargeCycleCount = countChargeCycles(history);

            float avgTemp = report.avgTemperature;
            int deepDischarges = report.deepDischargeCount;
            float healthChange = report.weeklyHealthChange;

            StringBuilder summary = new StringBuilder();
            if (healthChange < -0.5f) {
                summary.append("本周健康度下降略快，");
            } else if (healthChange > 0.1f) {
                summary.append("本周健康度保持良好，");
            } else {
                summary.append("本周健康度基本稳定，");
            }

            if (avgTemp > 35f) {
                summary.append("电池温度偏高，");
            } else if (avgTemp < 20f) {
                summary.append("电池温度偏低，");
            }

            if (deepDischarges >= 2) {
                summary.append("深度放电次数较多。");
            } else {
                summary.append("使用习惯良好。");
            }

            report.summary = summary.toString();
            report.currentHealth = currentHealth;
            report.hasEnoughData = dailyHealth.size() >= 3;

            StringBuilder nextWeekPrediction = new StringBuilder();
            nextWeekPrediction.append("预计下周健康度");
            if (Math.abs(healthChange) < 0.2f) {
                nextWeekPrediction.append("保持稳定");
            } else if (healthChange < 0) {
                nextWeekPrediction.append(String.format(Locale.getDefault(), "下降约 %.1f%%", Math.abs(healthChange)));
            } else {
                nextWeekPrediction.append(String.format(Locale.getDefault(), "上升约 %.1f%%", healthChange));
            }
            report.nextWeekPrediction = nextWeekPrediction.toString();

        } catch (Exception e) {
            report.hasEnoughData = false;
            report.summary = "生成报告时发生错误";
        }
        return report;
    }

    public List<InsightItem> getDailyInsights() {
        List<InsightItem> insights = new ArrayList<>();

        try {
            AgingPrediction aging = predictAging();
            InsightItem agingInsight = new InsightItem();
            agingInsight.type = InsightItem.TYPE_CAPACITY_TREND;
            agingInsight.title = "容量趋势";
            agingInsight.shortMessage = String.format(Locale.getDefault(),
                    "每月健康度下降约 %.1f%%", aging.monthlyDecayRate);
            agingInsight.detailMessage = String.format(Locale.getDefault(),
                    "基于历史数据分析，电池每月健康度下降约 %.1f%%。预计 6 个月后健康度为 %.0f%%。",
                    aging.monthlyDecayRate, aging.predictedHealth6Months);
            agingInsight.priority = 2;
            insights.add(agingInsight);

            ChargingSuggestion charging = getChargingSuggestion();
            InsightItem chargingInsight = new InsightItem();
            chargingInsight.type = InsightItem.TYPE_CHARGING_HABIT;
            chargingInsight.title = "充电建议";
            chargingInsight.shortMessage = charging.suggestion;
            chargingInsight.detailMessage = String.format(Locale.getDefault(),
                    "根据您的充电习惯分析，最佳充电时间段为 %d:00 - %d:00。建议充电上限为 %d%%，可有效延长电池寿命。",
                    charging.bestChargeStartHour, charging.bestChargeEndHour, charging.recommendedChargeCeiling);
            chargingInsight.priority = 1;
            insights.add(chargingInsight);

            float avgTemp = 25f;
            try {
                long oneWeekAgo = System.currentTimeMillis() - ONE_WEEK_MS;
                List<BatteryInfo> history = batteryRepository.getHistorySince(oneWeekAgo);
                if (history != null && !history.isEmpty()) {
                    avgTemp = calculateAvgTemperature(history);
                }
            } catch (Exception ignored) {}

            InsightItem tempInsight = new InsightItem();
            tempInsight.type = InsightItem.TYPE_TEMPERATURE;
            tempInsight.title = "温度影响";
            if (avgTemp > 35f) {
                tempInsight.shortMessage = String.format(Locale.getDefault(), "平均温度 %.0f°C，注意散热", avgTemp);
                tempInsight.detailMessage = "电池在高温环境下老化速度会加快。建议避免在高温环境下长时间使用或充电，充电时可移除手机壳以帮助散热。";
                tempInsight.priority = 1;
            } else if (avgTemp < 10f) {
                tempInsight.shortMessage = String.format(Locale.getDefault(), "平均温度 %.0f°C，低温保护", avgTemp);
                tempInsight.detailMessage = "低温环境下电池活性会降低，可能出现续航变短的情况。这是正常现象，温度回升后会恢复。";
                tempInsight.priority = 3;
            } else {
                tempInsight.shortMessage = String.format(Locale.getDefault(), "平均温度 %.0f°C，温度适宜", avgTemp);
                tempInsight.detailMessage = "当前使用温度在适宜范围内，有助于保持电池健康。最佳工作温度为 16-22°C。";
                tempInsight.priority = 4;
            }
            insights.add(tempInsight);

            ReplacementSuggestion replacement = getReplacementSuggestion();
            InsightItem usageInsight = new InsightItem();
            usageInsight.type = InsightItem.TYPE_USAGE_SUGGESTION;
            usageInsight.title = "使用建议";
            usageInsight.shortMessage = replacement.suggestion;
            usageInsight.detailMessage = String.format(Locale.getDefault(),
                    "当前电池健康度 %.0f%%，循环次数 %d 次。%s",
                    replacement.currentHealth, replacement.cycleCount, replacement.suggestion);
            usageInsight.priority = 2;
            insights.add(usageInsight);

            AnomalyDetection anomalies = detectAnomalies();
            if (anomalies.hasAnomalies && !anomalies.anomalies.isEmpty()) {
                for (Anomaly anomaly : anomalies.anomalies) {
                    InsightItem anomalyInsight = new InsightItem();
                    anomalyInsight.type = InsightItem.TYPE_ANOMALY_ALERT;
                    anomalyInsight.title = "异常提醒";
                    anomalyInsight.shortMessage = anomaly.message;
                    anomalyInsight.detailMessage = anomaly.message + "\n建议：" + anomaly.suggestion;
                    anomalyInsight.priority = anomaly.severity == Anomaly.SEVERITY_HIGH ? 0 : 1;
                    anomalyInsight.isAlert = true;
                    insights.add(anomalyInsight);
                }
            }

            Collections.sort(insights, (a, b) -> Integer.compare(a.priority, b.priority));

        } catch (Exception e) {
            InsightItem fallback = new InsightItem();
            fallback.type = InsightItem.TYPE_USAGE_SUGGESTION;
            fallback.title = "使用建议";
            fallback.shortMessage = "保持良好充电习惯，延长电池寿命";
            fallback.detailMessage = "建议保持电量在 20%-80% 之间，避免深度放电和充满。夜间充电时可使用智能充电功能。";
            fallback.priority = 5;
            insights.add(fallback);
        }

        return insights;
    }

    private List<DataPoint> aggregateDailyHealth(List<BatteryInfo> history) {
        List<DataPoint> result = new ArrayList<>();
        if (history == null || history.isEmpty()) return result;

        Collections.sort(history, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

        long currentDay = 0;
        float daySum = 0;
        int dayCount = 0;

        for (BatteryInfo info : history) {
            if (info.getHealthPercentage() < 0) continue;

            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(info.getTimestamp());
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long dayStart = cal.getTimeInMillis();

            if (dayStart != currentDay) {
                if (dayCount > 0) {
                    DataPoint point = new DataPoint();
                    point.timestamp = currentDay;
                    point.value = daySum / dayCount;
                    result.add(point);
                }
                currentDay = dayStart;
                daySum = 0;
                dayCount = 0;
            }
            daySum += info.getHealthPercentage();
            dayCount++;
        }

        if (dayCount > 0) {
            DataPoint point = new DataPoint();
            point.timestamp = currentDay;
            point.value = daySum / dayCount;
            result.add(point);
        }

        return result;
    }

    private float[] linearRegression(List<DataPoint> points) {
        int n = points.size();
        if (n < 2) return new float[]{0, 85f};

        float sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            float x = i;
            float y = points.get(i).value;
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        float slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        float intercept = (sumY - slope * sumX) / n;

        return new float[]{slope, intercept};
    }

    private float calculateSeasonalFactor(List<DataPoint> points) {
        if (points.size() < 30) return 0.3f;
        float sumVariance = 0;
        float[] regression = linearRegression(points);
        for (int i = 0; i < points.size(); i++) {
            float predicted = regression[0] * i + regression[1];
            float actual = points.get(i).value;
            sumVariance += Math.abs(actual - predicted);
        }
        return Math.max(0.2f, Math.min(1f, sumVariance / points.size()));
    }

    private float calculateAvgDischargeRate(List<BatteryInfo> history) {
        if (history == null || history.size() < 2) return 0;

        float totalRate = 0;
        int count = 0;
        int lastLevel = -1;
        long lastTime = 0;

        for (BatteryInfo info : history) {
            if (info.isCharging()) {
                lastLevel = -1;
                continue;
            }
            int level = info.getLevel();
            long time = info.getTimestamp();

            if (lastLevel >= 0 && level < lastLevel && time > lastTime) {
                float hours = (time - lastTime) / (1000f * 60 * 60);
                if (hours > 0.1f && hours < 24f) {
                    float rate = (lastLevel - level) / hours;
                    if (rate > 0 && rate < 50) {
                        totalRate += rate;
                        count++;
                    }
                }
            }
            lastLevel = level;
            lastTime = time;
        }

        return count > 0 ? totalRate / count : 0;
    }

    private float calculateAvgTemperature(List<BatteryInfo> history) {
        if (history == null || history.isEmpty()) return 25f;
        float sum = 0;
        int count = 0;
        for (BatteryInfo info : history) {
            if (info.getTemperature() > 0) {
                sum += info.getTemperature();
                count++;
            }
        }
        return count > 0 ? sum / count : 25f;
    }

    private int countDeepDischarges(List<BatteryInfo> history) {
        if (history == null || history.isEmpty()) return 0;
        int count = 0;
        boolean wasLow = false;
        for (BatteryInfo info : history) {
            boolean isLow = info.getLevel() <= 20 && !info.isCharging();
            if (isLow && !wasLow) {
                count++;
            }
            wasLow = isLow;
        }
        return count;
    }

    private int countChargeCycles(List<BatteryInfo> history) {
        if (history == null || history.isEmpty()) return 0;
        int count = 0;
        boolean wasCharging = false;
        for (BatteryInfo info : history) {
            boolean isCharging = info.isCharging();
            if (isCharging && !wasCharging) {
                count++;
            }
            wasCharging = isCharging;
        }
        return count;
    }

    private float calculateRecentDischargeRate(List<BatteryInfo> history) {
        if (history == null || history.size() < 12) return 0;
        List<BatteryInfo> recent = history.subList(Math.max(0, history.size() - 12), history.size());
        return calculateAvgDischargeRate(recent);
    }

    private float calculateRecentAvgTemperature(List<BatteryInfo> history) {
        if (history == null || history.size() < 12) return 25f;
        List<BatteryInfo> recent = history.subList(Math.max(0, history.size() - 12), history.size());
        return calculateAvgTemperature(recent);
    }

    private static class DataPoint {
        long timestamp;
        float value;
    }

    public static class AgingPrediction {
        public boolean hasEnoughData;
        public float currentHealth;
        public float predictedHealth6Months;
        public float[] predictedMonthlyHealth;
        public float monthlyDecayRate;
        public float confidence;
    }

    public static class AnomalyDetection {
        public boolean hasAnomalies;
        public List<Anomaly> anomalies;
    }

    public static class Anomaly {
        public static final int TYPE_DISCHARGE_RATE = 1;
        public static final int TYPE_HIGH_TEMPERATURE = 2;
        public static final int TYPE_DEEP_DISCHARGE = 3;

        public static final int SEVERITY_LOW = 1;
        public static final int SEVERITY_MEDIUM = 2;
        public static final int SEVERITY_HIGH = 3;

        public int type;
        public int severity;
        public String message;
        public String suggestion;
    }

    public static class ChargingSuggestion {
        public int bestChargeStartHour;
        public int bestChargeEndHour;
        public int recommendedChargeCeiling;
        public String suggestion;
    }

    public static class ReplacementSuggestion {
        public static final int URGENCY_NONE = 0;
        public static final int URGENCY_LOW = 1;
        public static final int URGENCY_MEDIUM = 2;
        public static final int URGENCY_HIGH = 3;

        public boolean needsReplacement;
        public int urgency;
        public float currentHealth;
        public int cycleCount;
        public float usageIntensity;
        public int estimatedMonthsLeft;
        public String suggestion;
    }

    public static class WeeklyReport {
        public boolean hasEnoughData;
        public float currentHealth;
        public float weeklyHealthChange;
        public float avgTemperature;
        public float avgDischargeRate;
        public int deepDischargeCount;
        public int chargeCycleCount;
        public String summary;
        public String nextWeekPrediction;
    }

    public static class InsightItem {
        public static final int TYPE_CHARGING_HABIT = 1;
        public static final int TYPE_TEMPERATURE = 2;
        public static final int TYPE_CAPACITY_TREND = 3;
        public static final int TYPE_USAGE_SUGGESTION = 4;
        public static final int TYPE_ANOMALY_ALERT = 5;

        public int type;
        public String title;
        public String shortMessage;
        public String detailMessage;
        public int priority;
        public boolean isAlert;
    }
}
