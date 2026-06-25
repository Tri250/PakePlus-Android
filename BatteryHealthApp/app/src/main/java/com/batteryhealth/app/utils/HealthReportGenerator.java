package com.batteryhealth.app.utils;

import android.content.Context;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.HealthRadarData;
import com.batteryhealth.app.data.repository.BatteryRepositoryImpl;
import com.batteryhealth.app.domain.repository.BatteryRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 健康日报/周报生成器
 * 生成文字总结 + 关键指标变化
 */
public class HealthReportGenerator {

    private final Context context;
    private final BatteryRepository batteryRepository;

    public static class HealthReport {
        public String title;
        public String period;
        public String summaryText;
        public float startHealth;
        public float endHealth;
        public float healthChange;
        public float avgTemperature;
        public float maxTemperature;
        public float minTemperature;
        public int chargeCount;
        public int avgCycleCount;
        public String healthTrend;
        public List<String> keyFindings;
        public String recommendations;
        public long startDate;
        public long endDate;

        public HealthReport() {
            this.startHealth = -1;
            this.endHealth = -1;
            this.keyFindings = new ArrayList<>();
        }
    }

    public HealthReportGenerator(Context context) {
        this.context = context.getApplicationContext();
        this.batteryRepository = new BatteryRepositoryImpl(
                (BatteryHealthApplication) context.getApplicationContext());
    }

    public HealthReport generateDailyReport() {
        HealthReport report = new HealthReport();
        report.title = "电池健康日报";

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        report.endDate = System.currentTimeMillis();
        report.startDate = cal.getTimeInMillis();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        report.period = sdf.format(new Date(report.startDate));

        generateReportData(report, report.startDate, report.endDate);

        return report;
    }

    public HealthReport generateWeeklyReport() {
        HealthReport report = new HealthReport();
        report.title = "电池健康周报";

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        report.endDate = System.currentTimeMillis();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        report.startDate = cal.getTimeInMillis();

        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
        report.period = sdf.format(new Date(report.startDate)) + " ~ " +
                sdf.format(new Date(report.endDate));

        generateReportData(report, report.startDate, report.endDate);

        return report;
    }

    private void generateReportData(HealthReport report, long startDate, long endDate) {
        try {
            List<BatteryInfo> historyData = loadHistoryData(startDate, endDate);

            if (historyData == null || historyData.isEmpty()) {
                report.summaryText = "暂无足够数据生成报告，请继续使用以积累数据";
                report.recommendations = "建议保持 App 在后台运行，以便持续采集电池数据";
                return;
            }

            BatteryInfo first = historyData.get(0);
            BatteryInfo last = historyData.get(historyData.size() - 1);

            report.startHealth = first.getHealthPercentage();
            report.endHealth = last.getHealthPercentage();
            report.healthChange = report.endHealth - report.startHealth;

            float totalTemp = 0f;
            float maxTemp = Float.MIN_VALUE;
            float minTemp = Float.MAX_VALUE;
            int tempCount = 0;
            int chargeCount = 0;
            boolean wasCharging = false;

            for (BatteryInfo info : historyData) {
                float temp = info.getTemperature();
                if (temp > 0) {
                    totalTemp += temp;
                    tempCount++;
                    if (temp > maxTemp) maxTemp = temp;
                    if (temp < minTemp) minTemp = temp;
                }

                boolean isCharging = info.isCharging();
                if (isCharging && !wasCharging) {
                    chargeCount++;
                }
                wasCharging = isCharging;
            }

            if (tempCount > 0) {
                report.avgTemperature = totalTemp / tempCount;
                report.maxTemperature = maxTemp;
                report.minTemperature = minTemp;
            }

            report.chargeCount = chargeCount;
            report.avgCycleCount = last.getCycleCount();

            if (report.healthChange > 0.5f) {
                report.healthTrend = "上升";
            } else if (report.healthChange < -0.5f) {
                report.healthTrend = "下降";
            } else {
                report.healthTrend = "稳定";
            }

            report.keyFindings = generateKeyFindings(report);
            report.summaryText = buildSummaryText(report);
            report.recommendations = generateRecommendations(report);

        } catch (Exception e) {
            report.summaryText = "报告生成失败：" + e.getMessage();
            report.recommendations = "请稍后重试";
        }
    }

    private List<BatteryInfo> loadHistoryData(long startDate, long endDate) {
        List<BatteryInfo> result = new ArrayList<>();
        try {
            List<BatteryInfo> all = batteryRepository.getHistorySince(startDate);
            if (all != null) {
                for (BatteryInfo info : all) {
                    if (info.getTimestamp() <= endDate) {
                        result.add(info);
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private List<String> generateKeyFindings(HealthReport report) {
        List<String> findings = new ArrayList<>();

        if (report.healthChange < -1f) {
            findings.add(String.format(Locale.getDefault(),
                    "本期健康度下降 %.1f%%，降幅较大，建议关注充电习惯", Math.abs(report.healthChange)));
        } else if (report.healthChange > 0.5f) {
            findings.add(String.format(Locale.getDefault(),
                    "本期健康度上升 %.1f%%，状态良好", report.healthChange));
        } else {
            findings.add("健康度保持稳定，继续保持良好的使用习惯");
        }

        if (report.maxTemperature > 40f) {
            findings.add(String.format(Locale.getDefault(),
                    "最高温度达到 %.1f°C，高温会加速电池老化", report.maxTemperature));
        }

        if (report.chargeCount > 7) {
            findings.add(String.format(Locale.getDefault(),
                    "本期充电 %d 次，频繁充放电可能影响电池寿命", report.chargeCount));
        }

        if (report.avgTemperature > 0 && report.avgTemperature < 35f) {
            findings.add(String.format(Locale.getDefault(),
                    "平均温度 %.1f°C，工作温度适宜", report.avgTemperature));
        }

        return findings;
    }

    private String buildSummaryText(HealthReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append(report.title).append("（").append(report.period).append("）\n\n");

        if (report.startHealth >= 0 && report.endHealth >= 0) {
            sb.append(String.format(Locale.getDefault(),
                    "健康度：%.1f%% → %.1f%%（%s %.1f%%）\n",
                    report.startHealth, report.endHealth,
                    report.healthChange >= 0 ? "上升" : "下降",
                    Math.abs(report.healthChange)));
        }

        if (report.avgTemperature > 0) {
            sb.append(String.format(Locale.getDefault(),
                    "平均温度：%.1f°C（最高 %.1f°C / 最低 %.1f°C）\n",
                    report.avgTemperature, report.maxTemperature, report.minTemperature));
        }

        sb.append("充电次数：").append(report.chargeCount).append(" 次\n");
        sb.append("健康趋势：").append(report.healthTrend).append("\n");

        return sb.toString();
    }

    private String generateRecommendations(HealthReport report) {
        List<String> tips = new ArrayList<>();

        if (report.maxTemperature > 40f) {
            tips.add("• 避免在高温环境下长时间使用或充电");
        }

        if (report.chargeCount > 7) {
            tips.add("• 尽量减少频繁充放电，保持电量在 20%-80% 区间");
        }

        if (report.endHealth >= 0 && report.endHealth < 80) {
            tips.add("• 健康度偏低，建议避免边充边玩和整夜充电");
        }

        if (report.healthChange < -0.5f) {
            tips.add("• 健康度有所下降，建议优化充电习惯");
        }

        if (tips.isEmpty()) {
            tips.add("• 电池状态良好，继续保持良好的使用习惯");
            tips.add("• 建议每月进行一次完整的充放电校准");
        }

        tips.add("• 避免使用非原装充电器和数据线");
        tips.add("• 长期存放时保持 50% 左右电量");

        StringBuilder sb = new StringBuilder();
        for (String tip : tips) {
            sb.append(tip).append("\n");
        }
        return sb.toString().trim();
    }

    public String formatReport(HealthReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════\n");
        sb.append("       ").append(report.title).append("\n");
        sb.append("═══════════════════════════════════\n\n");
        sb.append("统计周期：").append(report.period).append("\n\n");
        sb.append("【核心指标】\n");
        sb.append("健康度变化：");
        if (report.startHealth >= 0 && report.endHealth >= 0) {
            sb.append(String.format(Locale.getDefault(), "%.1f%% → %.1f%%\n",
                    report.startHealth, report.endHealth));
        } else {
            sb.append("--\n");
        }
        sb.append("健康趋势：").append(report.healthTrend != null ? report.healthTrend : "--").append("\n");
        sb.append("平均温度：").append(report.avgTemperature > 0
                ? String.format(Locale.getDefault(), "%.1f°C", report.avgTemperature) : "--").append("\n");
        sb.append("充电次数：").append(report.chargeCount).append(" 次\n\n");

        if (report.keyFindings != null && !report.keyFindings.isEmpty()) {
            sb.append("【关键发现】\n");
            for (String finding : report.keyFindings) {
                sb.append("• ").append(finding).append("\n");
            }
            sb.append("\n");
        }

        sb.append("【养护建议】\n").append(report.recommendations != null ? report.recommendations : "").append("\n");
        sb.append("\n═══════════════════════════════════\n");
        sb.append("由「电池健康」App 生成\n");

        return sb.toString();
    }
}
