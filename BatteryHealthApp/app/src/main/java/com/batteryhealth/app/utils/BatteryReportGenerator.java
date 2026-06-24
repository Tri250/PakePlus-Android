package com.batteryhealth.app.utils;

import android.content.Context;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.database.BatteryInfoDao;
import com.batteryhealth.app.data.model.BatteryInfo;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import androidx.annotation.WorkerThread;

public class BatteryReportGenerator {

    public static class Report {
        public String period;
        public String periodLabel;
        public float startHealth;
        public float endHealth;
        public float healthDecay;
        public float avgTemperature;
        public float maxTemperature;
        public int chargeCount;
        public long totalChargeDurationMs;
        public float avgChargePower;
        public int maxCycleCount;
        public int minCycleCount;
        public int avgLevel;
        public int lowBatteryCount;
        public String healthTrend;
        public String recommendation;
        public List<DailySummary> dailySummaries;

        public static class DailySummary {
            public long date;
            public float health;
            public int avgLevel;
            public float temperature;
            public boolean charged;

            public DailySummary(long date, float health, int avgLevel, float temperature, boolean charged) {
                this.date = date;
                this.health = health;
                this.avgLevel = avgLevel;
                this.temperature = temperature;
                this.charged = charged;
            }
        }
    }

    private final Context context;

    public BatteryReportGenerator(Context context) {
        this.context = context.getApplicationContext();
    }

    public Report generateWeeklyReport() {
        Calendar cal = Calendar.getInstance();
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        long startTime = cal.getTimeInMillis();
        return generateReport(startTime, endTime, "本周");
    }

    public Report generateMonthlyReport() {
        Calendar cal = Calendar.getInstance();
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.MONTH, -1);
        long startTime = cal.getTimeInMillis();
        return generateReport(startTime, endTime, "本月");
    }

    /**
     * 生成指定时间段的电池报告。
     * <p>同步访问数据库，必须在后台线程调用（推荐通过 {@code ThreadExecutor.execute()} 或
     * ViewModel 的后台协程调用），否则在数据量较大时可能触发 ANR。
     */
    @WorkerThread
    public Report generateReport(long startTime, long endTime, String periodLabel) {
        Report report = new Report();
        report.periodLabel = periodLabel;
        report.dailySummaries = new ArrayList<>();

        try {
            BatteryHealthApplication app = BatteryHealthApplication.getInstance();
            if (app == null) return report;

            AppDatabase db = app.getDatabase();
            if (db == null) return report;

            BatteryInfoDao dao = db.batteryInfoDao();
            List<BatteryInfo> records = dao.getSince(startTime);

            if (records == null || records.isEmpty()) {
                return report;
            }

            List<BatteryInfo> validRecords = new ArrayList<>();
            for (BatteryInfo info : records) {
                if (info.getHealthPercentage() >= 0) {
                    validRecords.add(info);
                }
            }

            if (validRecords.isEmpty()) {
                return report;
            }

            float totalHealth = 0;
            float totalTemperature = 0;
            float maxTemp = Float.MIN_VALUE;
            int totalLevel = 0;
            int lowBatteryCount = 0;
            int chargeCount = 0;
            long totalChargeDuration = 0;
            float totalChargePower = 0;
            int chargePowerCount = 0;
            int maxCycle = Integer.MIN_VALUE;
            int minCycle = Integer.MAX_VALUE;

            boolean inChargeSession = false;
            long chargeStart = 0;

            for (BatteryInfo info : validRecords) {
                float health = info.getHealthPercentage();
                float temp = info.getTemperature();
                int level = info.getLevel();
                boolean charging = info.isCharging();
                int cycle = info.getCycleCount();

                totalHealth += health;
                totalTemperature += temp;
                totalLevel += level;

                if (temp > maxTemp) maxTemp = temp;
                if (level <= 20) lowBatteryCount++;
                if (cycle > 0) {
                    if (cycle > maxCycle) maxCycle = cycle;
                    if (cycle < minCycle) minCycle = cycle;
                }

                if (charging && !inChargeSession) {
                    inChargeSession = true;
                    chargeStart = info.getTimestamp();
                    chargeCount++;
                } else if (!charging && inChargeSession) {
                    inChargeSession = false;
                    totalChargeDuration += (info.getTimestamp() - chargeStart);
                }

                float power = info.getChargingPower();
                if (power > 0) {
                    totalChargePower += power;
                    chargePowerCount++;
                }
            }

            if (inChargeSession) {
                totalChargeDuration += (System.currentTimeMillis() - chargeStart);
            }

            int recordCount = validRecords.size();
            report.startHealth = validRecords.get(0).getHealthPercentage();
            report.endHealth = validRecords.get(validRecords.size() - 1).getHealthPercentage();
            report.healthDecay = report.startHealth - report.endHealth;
            report.avgTemperature = totalTemperature / recordCount;
            report.maxTemperature = maxTemp;
            report.avgLevel = totalLevel / recordCount;
            report.lowBatteryCount = lowBatteryCount;
            report.chargeCount = chargeCount;
            report.totalChargeDurationMs = totalChargeDuration;
            report.avgChargePower = chargePowerCount > 0 ? totalChargePower / chargePowerCount : 0;
            report.maxCycleCount = maxCycle > Integer.MIN_VALUE ? maxCycle : 0;
            report.minCycleCount = minCycle < Integer.MAX_VALUE ? minCycle : 0;

            report.healthTrend = determineTrend(report.healthDecay);
            report.recommendation = generateRecommendation(report);

            generateDailySummaries(validRecords, report);

        } catch (Exception e) {
            android.util.Log.e("BatteryReportGenerator", "Error generating report: " + e.getMessage(), e);
        }

        return report;
    }

    private void generateDailySummaries(List<BatteryInfo> records, Report report) {
        if (records.isEmpty()) return;

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(records.get(0).getTimestamp());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long currentDayStart = cal.getTimeInMillis();

        float dayHealth = 0;
        int dayLevel = 0;
        float dayTemp = 0;
        boolean dayCharged = false;
        int dayCount = 0;

        for (BatteryInfo info : records) {
            cal.setTimeInMillis(info.getTimestamp());
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long recordDayStart = cal.getTimeInMillis();

            if (recordDayStart > currentDayStart) {
                if (dayCount > 0) {
                    report.dailySummaries.add(new Report.DailySummary(
                            currentDayStart,
                            dayHealth / dayCount,
                            dayLevel / dayCount,
                            dayTemp / dayCount,
                            dayCharged
                    ));
                }

                currentDayStart = recordDayStart;
                dayHealth = 0;
                dayLevel = 0;
                dayTemp = 0;
                dayCharged = false;
                dayCount = 0;
            }

            dayHealth += info.getHealthPercentage();
            dayLevel += info.getLevel();
            dayTemp += info.getTemperature();
            if (info.isCharging()) dayCharged = true;
            dayCount++;
        }

        if (dayCount > 0) {
            report.dailySummaries.add(new Report.DailySummary(
                    currentDayStart,
                    dayHealth / dayCount,
                    dayLevel / dayCount,
                    dayTemp / dayCount,
                    dayCharged
            ));
        }
    }

    private String determineTrend(float decay) {
        if (decay > 1) return "下降较快";
        if (decay > 0) return "略有下降";
        return "稳定";
    }

    private String generateRecommendation(Report report) {
        StringBuilder sb = new StringBuilder();

        if (report.healthDecay > 1) {
            sb.append("本周电池健康度下降较快，建议：\n");
            sb.append("• 避免高温环境使用和充电\n");
            sb.append("• 保持电量在20%-80%区间\n");
            sb.append("• 减少快充次数，使用标准充电\n");
        }

        if (report.maxTemperature > 40) {
            sb.append("\n检测到高温记录，建议：\n");
            sb.append("• 充电时取下手机壳\n");
            sb.append("• 避免边充边玩大型游戏\n");
            sb.append("• 在空调房或通风良好的地方使用\n");
        }

        if (report.lowBatteryCount > 3) {
            sb.append("\n多次出现低电量（≤20%），建议：\n");
            sb.append("• 及时充电，避免深度放电\n");
            sb.append("• 开启省电模式\n");
            sb.append("• 减少后台运行的应用数量\n");
        }

        if (report.chargeCount > 7) {
            sb.append("\n充电次数较多，建议：\n");
            sb.append("• 尝试延长单次使用时间\n");
            sb.append("• 使用充电宝补充电量，减少充电次数\n");
        }

        if (report.endHealth < 80) {
            sb.append("\n电池健康度低于80%，建议：\n");
            sb.append("• 定期校准电池\n");
            sb.append("• 考虑更换电池\n");
        }

        if (sb.length() == 0) {
            sb.append("本周电池使用情况良好，继续保持！\n");
            sb.append("建议：保持电量在20%-80%区间，避免高温环境。");
        }

        return sb.toString();
    }

    public String formatReport(Report report) {
        if (report.startHealth < 0) {
            return "暂无数据";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【").append(report.periodLabel).append("电池健康报告】\n\n");

        sb.append("📊 健康度变化：\n");
        sb.append(String.format(Locale.getDefault(), "  初始：%.1f%% → 当前：%.1f%%\n", report.startHealth, report.endHealth));
        sb.append(String.format(Locale.getDefault(), "  衰减：%.2f%%\n", report.healthDecay));
        sb.append("  趋势：").append(report.healthTrend).append("\n\n");

        sb.append("🌡️ 温度统计：\n");
        sb.append(String.format(Locale.getDefault(), "  平均：%.1f°C\n", report.avgTemperature));
        sb.append(String.format(Locale.getDefault(), "  最高：%.1f°C\n\n", report.maxTemperature));

        sb.append("🔋 充电统计：\n");
        sb.append(String.format("  充电次数：%d 次\n", report.chargeCount));
        sb.append(String.format("  总时长：%s\n", formatDuration(report.totalChargeDurationMs)));
        sb.append(String.format(Locale.getDefault(), "  平均功率：%.1f W\n\n", report.avgChargePower));

        sb.append("📈 使用情况：\n");
        sb.append(String.format("  平均电量：%d%%\n", report.avgLevel));
        sb.append(String.format("  低电量次数：%d 次\n", report.lowBatteryCount));

        if (report.maxCycleCount > 0) {
            sb.append(String.format("  循环次数：%d-%d 次\n", report.minCycleCount, report.maxCycleCount));
        }

        sb.append("\n💡 建议：\n");
        sb.append(report.recommendation);

        return sb.toString();
    }

    private String formatDuration(long ms) {
        if (ms <= 0) return "--";
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d分钟", minutes);
        } else {
            return String.format("%d秒", seconds);
        }
    }
}
