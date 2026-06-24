package com.batteryhealth.app.domain.usecase;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.domain.repository.BatteryRepository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 趋势数据 UseCase（v5.0 - 对标国内同类系统完整版）
 *
 * 支持：
 * 1. 多时间范围（7天/30天/90天/180天）
 * 2. 每日聚合健康度
 * 3. 异常衰减检测（骤降标注）
 * 4. 电池寿命预测（基于月均衰减率推算剩余可用月数）
 * 5. 充电建议（基于温度趋势和衰减速率）
 * 6. 循环次数趋势
 * 7. 温度趋势
 */
public class GetTrendDataUseCase {

    private final BatteryRepository batteryRepository;

    public static final int RANGE_7D = 0;
    public static final int RANGE_30D = 1;
    public static final int RANGE_90D = 2;
    public static final int RANGE_180D = 3;

    private static final long[] RANGE_MS = {
            7L * 24 * 60 * 60 * 1000,
            30L * 24 * 60 * 60 * 1000,
            90L * 24 * 60 * 60 * 1000,
            180L * 24 * 60 * 60 * 1000
    };

    public GetTrendDataUseCase(BatteryRepository batteryRepository) {
        this.batteryRepository = batteryRepository;
    }

    public Result execute(int rangeIndex) {
        Result result = new Result();
        result.rangeIndex = rangeIndex;

        long now = System.currentTimeMillis();
        long startTime = now - RANGE_MS[rangeIndex];

        List<BatteryInfo> history = batteryRepository.getHistorySince(startTime);

        if (history == null || history.isEmpty()) {
            result.hasData = false;
            buildEmptyDailyPoints(result, rangeIndex);
            return result;
        }

        result.hasData = true;

        // 按日聚合健康度
        List<DailyPoint> dailyPoints = aggregateByDay(history, now, rangeIndex);
        result.dailyPoints = dailyPoints;

        // 统计计算
        calculateStats(result, history, dailyPoints);

        // 异常检测
        detectAnomalies(result, dailyPoints);

        // 电池寿命预测
        predictLifespan(result);

        // 充电建议
        generateChargingAdvice(result);

        return result;
    }

    private void buildEmptyDailyPoints(Result result, int rangeIndex) {
        List<DailyPoint> points = new ArrayList<>();
        result.dailyPoints = points;
        result.initialHealth = -1;
        result.currentHealth = -1;
        result.totalDecay = 0;
        result.monthlyDecay = 0;
        result.avgTemperature = -1;
        result.maxTemperature = -1;
        result.recordCount = 0;
        result.dataSpanDays = 0;
    }

    /**
     * 按日聚合：每天取所有有效健康度记录的均值
     */
    private List<DailyPoint> aggregateByDay(List<BatteryInfo> history, long now, int rangeIndex) {
        List<DailyPoint> points = new ArrayList<>();
        Calendar cal = Calendar.getInstance();

        long dayStart = -1;
        float dayHealthSum = 0;
        int dayHealthCount = 0;
        float dayTempSum = 0;
        int dayTempCount = 0;
        float dayMaxTemp = Float.MIN_VALUE;
        int dayCycleSum = 0;
        int dayCycleCount = 0;
        long dayTimestamp = 0;

        for (BatteryInfo info : history) {
            float health = info.getHealthPercentage();
            if (health < 0) continue;

            cal.setTimeInMillis(info.getTimestamp());
            long currentDay = cal.get(Calendar.YEAR) * 10000L
                    + (cal.get(Calendar.MONTH) + 1) * 100L
                    + cal.get(Calendar.DAY_OF_MONTH);

            if (dayStart != currentDay && dayStart >= 0) {
                DailyPoint dp = new DailyPoint();
                dp.timestamp = dayTimestamp;
                dp.dayKey = dayStart;
                dp.health = dayHealthCount > 0 ? dayHealthSum / dayHealthCount : -1;
                dp.avgTemperature = dayTempCount > 0 ? dayTempSum / dayTempCount : -1;
                dp.maxTemperature = dayMaxTemp;
                dp.cycleCount = dayCycleCount > 0 ? dayCycleSum / dayCycleCount : -1;
                points.add(dp);

                dayHealthSum = 0;
                dayHealthCount = 0;
                dayTempSum = 0;
                dayTempCount = 0;
                dayMaxTemp = Float.MIN_VALUE;
                dayCycleSum = 0;
                dayCycleCount = 0;
            }

            dayStart = currentDay;
            dayTimestamp = info.getTimestamp();
            dayHealthSum += health;
            dayHealthCount++;
            float temp = info.getTemperature();
            if (temp > 0) {
                dayTempSum += temp;
                dayTempCount++;
                if (temp > dayMaxTemp) dayMaxTemp = temp;
            }
            if (info.getCycleCount() >= 0) {
                dayCycleSum += info.getCycleCount();
                dayCycleCount++;
            }
        }

        // 最后一天
        if (dayHealthCount > 0) {
            DailyPoint dp = new DailyPoint();
            dp.timestamp = dayTimestamp;
            dp.dayKey = dayStart;
            dp.health = dayHealthSum / dayHealthCount;
            dp.avgTemperature = dayTempCount > 0 ? dayTempSum / dayTempCount : -1;
            dp.maxTemperature = dayMaxTemp;
            dp.cycleCount = dayCycleCount > 0 ? dayCycleSum / dayCycleCount : -1;
            points.add(dp);
        }

        return points;
    }

    private void calculateStats(Result result, List<BatteryInfo> history, List<DailyPoint> dailyPoints) {
        if (dailyPoints.isEmpty()) {
            result.initialHealth = -1;
            result.currentHealth = -1;
            result.totalDecay = 0;
            result.monthlyDecay = 0;
            result.avgTemperature = -1;
            result.maxTemperature = -1;
            result.recordCount = 0;
            result.dataSpanDays = 0;
            return;
        }

        result.initialHealth = dailyPoints.get(0).health;
        result.currentHealth = dailyPoints.get(dailyPoints.size() - 1).health;

        if (result.initialHealth >= 0 && result.currentHealth >= 0) {
            result.totalDecay = result.initialHealth - result.currentHealth;
        }

        // 基于实际时间跨度计算月均衰减
        long earliestTs = dailyPoints.get(0).timestamp;
        long latestTs = dailyPoints.get(dailyPoints.size() - 1).timestamp;
        float daysSpan = (latestTs - earliestTs) / (1000f * 60 * 60 * 24);
        result.dataSpanDays = (int) Math.max(1, daysSpan);

        if (daysSpan > 0 && result.totalDecay > 0) {
            result.monthlyDecay = result.totalDecay / daysSpan * 30f;
        }

        // 温度统计
        float sumTemp = 0;
        float maxTemp = Float.MIN_VALUE;
        int validTempCount = 0;
        for (BatteryInfo info : history) {
            float temp = info.getTemperature();
            if (temp > 0) {
                sumTemp += temp;
                if (temp > maxTemp) maxTemp = temp;
                validTempCount++;
            }
        }
        result.avgTemperature = validTempCount > 0 ? sumTemp / validTempCount : -1;
        result.maxTemperature = maxTemp > Float.MIN_VALUE ? maxTemp : -1;
        result.recordCount = history.size();
    }

    /**
     * 异常检测：相邻日健康度骤降超过阈值
     */
    private void detectAnomalies(Result result, List<DailyPoint> dailyPoints) {
        result.anomalies = new ArrayList<>();
        if (dailyPoints.size() < 2) return;

        float anomalyThreshold = 1.5f; // 单日骤降1.5%视为异常

        for (int i = 1; i < dailyPoints.size(); i++) {
            float prev = dailyPoints.get(i - 1).health;
            float curr = dailyPoints.get(i).health;
            if (prev > 0 && curr > 0 && (prev - curr) >= anomalyThreshold) {
                Anomaly anomaly = new Anomaly();
                anomaly.timestamp = dailyPoints.get(i).timestamp;
                anomaly.healthDrop = prev - curr;
                anomaly.healthBefore = prev;
                anomaly.healthAfter = curr;
                result.anomalies.add(anomaly);
            }
        }
    }

    /**
     * 电池寿命预测：基于月均衰减率推算健康度降至60%的剩余月数
     */
    private void predictLifespan(Result result) {
        if (result.monthlyDecay <= 0 || result.currentHealth < 0) {
            result.remainingMonths = -1;
            result.lifespanPrediction = "";
            return;
        }

        float targetHealth = 60f;
        float remainingHealth = result.currentHealth - targetHealth;
        if (remainingHealth <= 0) {
            result.remainingMonths = 0;
            result.lifespanPrediction = "电池健康度已低于60%，建议尽快更换";
            return;
        }

        result.remainingMonths = remainingHealth / result.monthlyDecay;

        if (result.remainingMonths > 24) {
            result.lifespanPrediction = String.format(Locale.getDefault(),
                    "预计可用约%.0f个月，电池状态良好", result.remainingMonths);
        } else if (result.remainingMonths > 12) {
            result.lifespanPrediction = String.format(Locale.getDefault(),
                    "预计可用约%.0f个月，建议关注电池状态", result.remainingMonths);
        } else if (result.remainingMonths > 6) {
            result.lifespanPrediction = String.format(Locale.getDefault(),
                    "预计可用约%.0f个月，建议规划更换", result.remainingMonths);
        } else {
            result.lifespanPrediction = String.format(Locale.getDefault(),
                    "预计可用约%.0f个月，建议尽快更换", result.remainingMonths);
        }
    }

    /**
     * 充电建议：基于温度趋势和衰减速率
     */
    private void generateChargingAdvice(Result result) {
        List<String> tips = new ArrayList<>();

        if (result.avgTemperature > 0) {
            if (result.avgTemperature > 40) {
                tips.add("电池平均温度偏高，建议充电时取下手机壳散热");
            } else if (result.avgTemperature > 35) {
                tips.add("充电时注意散热，避免在高温环境下快充");
            }
        }

        if (result.monthlyDecay > 0) {
            if (result.monthlyDecay > 3) {
                tips.add("衰减速率较快，建议避免深度放电（低于20%再充电）");
            }
            if (result.monthlyDecay > 1.5) {
                tips.add("建议保持电量在20%-80%之间，减少完整充放电循环");
            }
        }

        if (result.maxTemperature > 0 && result.maxTemperature > 45) {
            tips.add("检测到高温充电记录，建议使用慢充或旁路充电模式");
        }

        if (tips.isEmpty()) {
            tips.add("电池状态良好，保持日常使用习惯即可");
        }

        result.chargingAdvice = tips;
    }

    // ======================== 内部数据类 ========================

    public static class Result {
        public boolean hasData;
        public int rangeIndex;
        public List<DailyPoint> dailyPoints;
        public float initialHealth = -1;
        public float currentHealth = -1;
        public float totalDecay;
        public float monthlyDecay;
        public float avgTemperature = -1;
        public float maxTemperature = -1;
        public int recordCount;
        public int dataSpanDays;
        public List<Anomaly> anomalies;
        public float remainingMonths = -1;
        public String lifespanPrediction = "";
        public List<String> chargingAdvice;
    }

    /**
     * 每日聚合数据点
     */
    public static class DailyPoint {
        public long timestamp;
        public long dayKey;
        public float health;
        public float avgTemperature;
        public float maxTemperature;
        public float cycleCount;
    }

    /**
     * 异常事件
     */
    public static class Anomaly {
        public long timestamp;
        public float healthDrop;
        public float healthBefore;
        public float healthAfter;
    }
}
