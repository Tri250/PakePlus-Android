package com.batteryhealth.app.utils;

import android.content.Context;
import android.text.TextUtils;

import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 电池健康报告生成器（CSV 格式）。
 * 用于导出周报 / 月报等用户可读报告。
 */
public class ReportGenerator {

    private static final String TAG = "ReportGenerator";
    // 设备温度有意义的下限（°C）：低温保护与全功率正常工作的范围约为 -20~80°C
    private static final float MIN_VALID_TEMPERATURE = -20f;
    private static final float MAX_VALID_TEMPERATURE = 80f;

    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    public enum Period {
        WEEK, MONTH
    }

    public static final class BatteryReport {
        public final String period;
        public final long generatedAt;
        public final int recordCount;
        public final float averageTemperature;
        public final float averageHealth;
        public final float minHealth;
        public final float maxHealth;
        public final long exportedPath;

        public BatteryReport(String period, long generatedAt, int recordCount,
                            float averageTemperature, float averageHealth,
                            float minHealth, float maxHealth, long exportedPath) {
            this.period = period;
            this.generatedAt = generatedAt;
            this.recordCount = recordCount;
            this.averageTemperature = averageTemperature;
            this.averageHealth = averageHealth;
            this.minHealth = minHealth;
            this.maxHealth = maxHealth;
            this.exportedPath = exportedPath;
        }
    }

    private final Context context;
    private final AppDatabase database;

    public ReportGenerator(Context context, AppDatabase database) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.database = database;
    }

    public BatteryReport generateWeeklyReport() {
        return generateReport(Period.WEEK);
    }

    public BatteryReport generateMonthlyReport() {
        return generateReport(Period.MONTH);
    }

    public BatteryReport generateReport(Period period) {
        if (context == null || database == null) {
            return emptyReport(period);
        }
        long end = System.currentTimeMillis();
        long windowMs = period == Period.MONTH ? 30L * 24 * 60 * 60 * 1000
                : 7L * 24 * 60 * 60 * 1000;
        long start = end - windowMs;

        List<BatteryInfo> records = null;
        try {
            records = database.batteryInfoDao().getSince(start);
        } catch (Throwable t) {
            // DB error; produce empty report rather than crashing
        }
        if (records == null || records.isEmpty()) {
            return emptyReport(period);
        }

        float tempSum = 0f;
        int tempCount = 0;
        float healthSum = 0f;
        int healthCount = 0;
        float minHealth = Float.MAX_VALUE;
        float maxHealth = -Float.MAX_VALUE;

        for (BatteryInfo info : records) {
            if (info == null) continue;
            float temp = info.getTemperature();
            if (temp >= MIN_VALID_TEMPERATURE && temp <= MAX_VALID_TEMPERATURE) {
                tempSum += temp;
                tempCount++;
            }
            float health = info.getHealthPercentage();
            if (health > 0f && health <= 100f) {
                healthSum += health;
                healthCount++;
                if (health < minHealth) minHealth = health;
                if (health > maxHealth) maxHealth = health;
            }
        }

        float avgTemp = tempCount > 0 ? tempSum / tempCount : -1f;
        float avgHealth = healthCount > 0 ? healthSum / healthCount : -1f;
        if (healthCount == 0) {
            minHealth = -1f;
            maxHealth = -1f;
        }

        long exportedPath = writeCsv(period, records, avgHealth, avgTemp);

        return new BatteryReport(
                period == Period.MONTH ? "monthly" : "weekly",
                end,
                records.size(),
                avgTemp,
                avgHealth,
                minHealth,
                maxHealth,
                exportedPath
        );
    }

    private BatteryReport emptyReport(Period period) {
        return new BatteryReport(
                period == Period.MONTH ? "monthly" : "weekly",
                System.currentTimeMillis(), 0, -1f, -1f, -1f, -1f, -1L);
    }

    private long writeCsv(Period period, List<BatteryInfo> records,
                          float avgHealth, float avgTemp) {
        File exportDir = new File(context.getExternalFilesDir(null), "reports");
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            return -1L;
        }
        String fileName = (period == Period.MONTH ? "battery_monthly" : "battery_weekly")
                + "_" + System.currentTimeMillis() + ".csv";
        File outFile = new File(exportDir, fileName);

        SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN, Locale.getDefault());
        try (Writer w = new FileWriter(outFile)) {
            // Header
            w.write("timestamp,level,voltage_mV,current_mA,temperature_C,health_percent,status,plugged\n");

            for (BatteryInfo info : records) {
                if (info == null) continue;
                w.write(sdf.format(new Date(info.getTimestamp()))).write(',');
                w.write(safeInt(info.getLevel())).write(',');
                w.write(safeInt(info.getVoltage())).write(',');
                w.write(safeInt(info.getCurrentNow() / 1000)).write(',');
                w.write(String.format(Locale.US, "%.2f", info.getTemperature())).write(',');
                w.write(String.format(Locale.US, "%.2f", info.getHealthPercentage())).write(',');
                w.write(safeInt(info.getStatus())).write(',');
                w.write(safeInt(info.getPlugged())).write('\n');
            }

            w.write("\n# Summary\n");
            w.write("# average_health,").write(String.format(Locale.US, "%.2f", avgHealth)).write('\n');
            w.write("# average_temperature,").write(String.format(Locale.US, "%.2f", avgTemp)).write('\n');
            w.write("# record_count,").write(Integer.toString(records.size())).write('\n');
        } catch (IOException e) {
            return -1L;
        }
        return outFile.getAbsolutePath().hashCode();
    }

    private static String safeInt(int v) {
        return Integer.toString(v);
    }
}
