package com.batteryhealth.app.utils;

import android.content.Context;
import android.util.Log;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 周报 / 月报生成器
 * 基于 Room 数据库中最近 7 天 / 30 天的电池记录生成统计报告。
 */
public class ReportGenerator {
    private static final String TAG = "ReportGenerator";
    private final Context context;

    public ReportGenerator(Context context) {
        this.context = context.getApplicationContext();
    }

    public static class BatteryReport {
        public String title;
        public String period;
        public String summary;
        public String recommendation;
        public int recordCount;
    }

    public BatteryReport generateWeeklyReport() {
        return generateReport(7, "周报", "近7天");
    }

    public BatteryReport generateMonthlyReport() {
        return generateReport(30, "月报", "近30天");
    }

    private BatteryReport generateReport(int days, String title, String periodLabel) {
        BatteryReport report = new BatteryReport();
        report.title = title;
        report.period = periodLabel;

        AppDatabase db = BatteryHealthApplication.getInstance() != null
                ? BatteryHealthApplication.getInstance().getDatabase()
                : null;
        if (db == null) {
            report.summary = "暂无足够数据生成" + title;
            report.recommendation = "请稍后再试";
            report.recordCount = 0;
            return report;
        }

        long endTime = System.currentTimeMillis();
        long startTime = endTime - days * 24L * 60L * 60L * 1000L;
        List<BatteryInfo> records;
        try {
            records = db.batteryInfoDao().getBetween(startTime, endTime);
        } catch (Exception e) {
            Log.e(TAG, "查询数据库失败", e);
            report.summary = "暂无足够数据生成" + title;
            report.recommendation = "请稍后再试";
            report.recordCount = 0;
            return report;
        }

        report.recordCount = records == null ? 0 : records.size();
        if (records == null || records.isEmpty()) {
            report.summary = "暂无足够数据生成" + title;
            report.recommendation = "请保持应用运行，数据会自动记录。";
            return report;
        }

        float avgHealth = 0;
        float avgTemp = 0;
        float avgLevel = 0;
        float minLevel = 101;
        float maxLevel = -1;
        float firstHealth = -1;
        float lastHealth = -1;
        float maxTemp = -1;
        int highTempCount = 0;

        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

        for (int i = 0; i < records.size(); i++) {
            BatteryInfo info = records.get(i);
            avgHealth += info.getHealthPercentage();
            avgTemp += info.getTemperature();
            avgLevel += info.getLevel();
            if (info.getLevel() < minLevel) minLevel = info.getLevel();
            if (info.getLevel() > maxLevel) maxLevel = info.getLevel();
            if (info.getTemperature() > maxTemp) maxTemp = info.getTemperature();
            if (info.getTemperature() > 42) highTempCount++;
            if (i == 0) firstHealth = info.getHealthPercentage();
            if (i == records.size() - 1) lastHealth = info.getHealthPercentage();
        }

        int count = records.size();
        avgHealth /= count;
        avgTemp /= count;
        avgLevel /= count;
        float healthChange = (firstHealth > 0 && lastHealth > 0) ? lastHealth - firstHealth : 0;

        StringBuilder sb = new StringBuilder();
        sb.append(title).append("统计：\n");
        sb.append("• 平均健康度：").append(String.format(Locale.getDefault(), "%.1f%%", avgHealth)).append("\n");
        sb.append("• 平均温度：").append(String.format(Locale.getDefault(), "%.1f°C", avgTemp)).append("\n");
        sb.append("• 平均电量：").append(String.format(Locale.getDefault(), "%.1f%%", avgLevel)).append("\n");
        sb.append("• 最低电量：").append(String.format(Locale.getDefault(), "%d%%", (int) minLevel)).append("\n");
        sb.append("• 最高电量：").append(String.format(Locale.getDefault(), "%d%%", (int) maxLevel)).append("\n");
        sb.append("• 健康度变化：").append(String.format(Locale.getDefault(), "%+.1f%%", healthChange)).append("\n");
        sb.append("• 记录条数：").append(count).append("\n");
        sb.append("• 统计时段：").append(sdf.format(new Date(records.get(0).getTimestamp())))
                .append(" ~ ").append(sdf.format(new Date(records.get(count - 1).getTimestamp())));
        report.summary = sb.toString();

        StringBuilder rec = new StringBuilder();
        if (avgTemp > 40 || highTempCount > 0) {
            rec.append("电池温度过高，建议避免边充边玩，充电时保持通风。\n");
        }
        if (avgHealth < 80) {
            rec.append("健康度较低，建议前往官方售后检测电池。\n");
        } else if (healthChange < -1) {
            rec.append("健康度下降较快，注意减少高温和深度放电。\n");
        }
        if (minLevel < 20) {
            rec.append("检测到深度放电，建议保持电量在20%以上。\n");
        }
        if (rec.length() == 0) {
            rec.append("电池状态良好，继续保持当前使用习惯。");
        }
        report.recommendation = rec.toString().trim();

        return report;
    }
}
