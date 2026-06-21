package com.batteryhealth.app.utils;

import android.content.Context;
import android.util.Log;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportGenerator {
    private static final String TAG = "ReportGenerator";
    private static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;
    private static final int WEEKLY_DAYS = 7;
    private static final int MONTHLY_DAYS = 30;
    private static final float TEMPERATURE_THRESHOLD = 35f;
    private static final float HEALTH_THRESHOLD = 80f;
    private static final float HEALTH_CHANGE_THRESHOLD = -1f;
    private static final int MIN_LEVEL_THRESHOLD = 10;
    private static final int DEFAULT_MIN_LEVEL = 100;
    private static final int DEFAULT_MAX_LEVEL = 0;
    private static final float MIN_VALID_TEMPERATURE = -100f;

    private final Context context;

    public ReportGenerator(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        this.context = context.getApplicationContext();
    }

    public static class BatteryReport {
        public String title;
        public String period;
        public int recordCount;
        public float avgHealth;
        public float avgTemperature;
        public float avgLevel;
        public int minLevel;
        public int maxLevel;
        public float healthChange;
        public String summary;
        public String recommendation;
    }

    public BatteryReport generateWeeklyReport() {
        long startTime = System.currentTimeMillis() - WEEKLY_DAYS * MILLIS_PER_DAY;
        return generateReport("周报", "过去7天", startTime);
    }

    public BatteryReport generateMonthlyReport() {
        long startTime = System.currentTimeMillis() - MONTHLY_DAYS * MILLIS_PER_DAY;
        return generateReport("月报", "过去30天", startTime);
    }

    private BatteryReport generateReport(String title, String period, long startTime) {
        BatteryReport report = new BatteryReport();
        report.title = title;
        report.period = period;

        try {
            BatteryHealthApplication app = BatteryHealthApplication.getInstance();
            if (app == null) {
                report.summary = "生成报告时出错";
                return report;
            }
            AppDatabase db = app.getDatabase();
            if (db == null) return report;

            List<BatteryInfo> records = db.batteryInfoDao().getSince(startTime);
            if (records == null || records.isEmpty()) {
                report.summary = "暂无足够数据生成" + title;
                return report;
            }

            report.recordCount = records.size();
            Collections.sort(records, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

            float totalHealth = 0, totalTemp = 0, totalLevel = 0;
            int healthCount = 0, tempCount = 0, levelCount = 0;
            report.minLevel = DEFAULT_MIN_LEVEL;
            report.maxLevel = DEFAULT_MAX_LEVEL;

            for (BatteryInfo info : records) {
                if (info == null) continue;
                if (info.hasValidHealthData()) {
                    totalHealth += info.getHealthPercentage();
                    healthCount++;
                }
                if (info.getTemperature() > MIN_VALID_TEMPERATURE) {
                    totalTemp += info.getTemperature();
                    tempCount++;
                }
                totalLevel += info.getLevel();
                levelCount++;
                if (info.getLevel() < report.minLevel) report.minLevel = info.getLevel();
                if (info.getLevel() > report.maxLevel) report.maxLevel = info.getLevel();
            }

            report.avgHealth = healthCount > 0 ? totalHealth / healthCount : 0;
            report.avgTemperature = tempCount > 0 ? totalTemp / tempCount : 0;
            report.avgLevel = levelCount > 0 ? totalLevel / levelCount : 0;

            // 健康度变化
            if (records.size() >= 2) {
                BatteryInfo first = records.get(0);
                BatteryInfo last = records.get(records.size() - 1);
                if (first != null && last != null) {
                    report.healthChange = last.getHealthPercentage() - first.getHealthPercentage();
                }
            }

            // 生成摘要和建议
            StringBuilder summary = new StringBuilder();
            summary.append(String.format(Locale.getDefault(), "平均健康度: %.1f%%", report.avgHealth));
            summary.append(String.format(Locale.getDefault(), "\n平均温度: %.1f°C", report.avgTemperature));
            summary.append(String.format(Locale.getDefault(), "\n电量范围: %d%% - %d%%", report.minLevel, report.maxLevel));

            if (report.healthChange < 0) {
                summary.append(String.format(Locale.getDefault(), "\n健康度下降: %.1f%%", Math.abs(report.healthChange)));
            }

            report.summary = summary.toString();

            // 生成建议
            StringBuilder recommendation = new StringBuilder();
            if (report.avgTemperature > TEMPERATURE_THRESHOLD) {
                recommendation.append("电池温度偏高，建议避免高温充电。\n");
            }
            if (report.avgHealth < HEALTH_THRESHOLD) {
                recommendation.append("电池健康度较低，建议减少快充使用频率。\n");
            }
            if (report.healthChange < HEALTH_CHANGE_THRESHOLD) {
                recommendation.append("健康度下降较快，建议联系售后检测。\n");
            }
            if (report.minLevel < MIN_LEVEL_THRESHOLD) {
                recommendation.append("检测到深度放电，建议避免电池完全耗尽。\n");
            }
            if (recommendation.length() == 0) {
                recommendation.append("电池状态良好，继续保持！");
            }
            report.recommendation = recommendation.toString().trim();

        } catch (Exception e) {
            Log.e(TAG, "Error generating report: " + e.getMessage());
            report.summary = "生成报告时出错";
        }

        return report;
    }
}
