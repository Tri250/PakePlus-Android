package com.batteryhealth.app.utils;

import android.content.Context;
import android.util.Log;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReportGenerator {
    private static final String TAG = "ReportGenerator";
    private final Context context;
    
    public ReportGenerator(Context context) {
        this.context = context;
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
        long startTime = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
        return generateReport("周报", "过去7天", startTime);
    }
    
    public BatteryReport generateMonthlyReport() {
        long startTime = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
        return generateReport("月报", "过去30天", startTime);
    }
    
    private BatteryReport generateReport(String title, String period, long startTime) {
        BatteryReport report = new BatteryReport();
        report.title = title;
        report.period = period;
        
        try {
            AppDatabase db = BatteryHealthApplication.getInstance().getDatabase();
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
            report.minLevel = 100;
            report.maxLevel = 0;
            
            for (BatteryInfo info : records) {
                if (info.hasValidHealthData()) {
                    totalHealth += info.getHealthPercentage();
                    healthCount++;
                }
                if (info.getTemperature() > -100) {
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
                float firstHealth = records.get(0).getHealthPercentage();
                float lastHealth = records.get(records.size() - 1).getHealthPercentage();
                report.healthChange = lastHealth - firstHealth;
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
            if (report.avgTemperature > 35) {
                recommendation.append("电池温度偏高，建议避免高温充电。\n");
            }
            if (report.avgHealth < 80) {
                recommendation.append("电池健康度较低，建议减少快充使用频率。\n");
            }
            if (report.healthChange < -1) {
                recommendation.append("健康度下降较快，建议联系售后检测。\n");
            }
            if (report.minLevel < 10) {
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

    /**
     * 按天聚合电池数据，生成每日统计列表（用于报告页 RecyclerView）。
     */
    public static List<com.batteryhealth.app.ui.battery.ReportAdapter.DailyStat> generateDailyStats(List<BatteryInfo> records) {
        List<com.batteryhealth.app.ui.battery.ReportAdapter.DailyStat> result = new ArrayList<>();
        if (records == null || records.isEmpty()) return result;

        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        Map<String, List<BatteryInfo>> dayMap = new HashMap<>();

        for (BatteryInfo info : records) {
            String dayKey = dayFormat.format(new Date(info.getTimestamp()));
            dayMap.computeIfAbsent(dayKey, k -> new ArrayList<>()).add(info);
        }

        List<String> sortedDays = new ArrayList<>(dayMap.keySet());
        Collections.sort(sortedDays);

        for (String dayKey : sortedDays) {
            List<BatteryInfo> dayRecords = dayMap.get(dayKey);
            if (dayRecords == null || dayRecords.isEmpty()) continue;

            float sumHealth = 0, sumTemp = 0;
            int healthCount = 0, tempCount = 0;
            int maxCycle = -1;
            long firstTimestamp = dayRecords.get(0).getTimestamp();

            for (BatteryInfo info : dayRecords) {
                if (info.hasValidHealthData()) {
                    sumHealth += info.getHealthPercentage();
                    healthCount++;
                }
                if (info.getTemperature() > -100) {
                    sumTemp += info.getTemperature();
                    tempCount++;
                }
                int cc = info.getCycleCount();
                if (cc > maxCycle) maxCycle = cc;
            }

            float avgHealth = healthCount > 0 ? sumHealth / healthCount : 0;
            float avgTemp = tempCount > 0 ? sumTemp / tempCount : 0;
            result.add(new com.batteryhealth.app.ui.battery.ReportAdapter.DailyStat(
                    firstTimestamp, avgHealth, avgTemp, maxCycle > 0 ? maxCycle : 0));
        }

        return result;
    }
}