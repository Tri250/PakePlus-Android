package com.batteryhealth.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.batteryhealth.app.data.model.BugReportGuide.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BugReportHistoryManager {

    private static final String PREFS_NAME = "bugreport_analysis_history";
    private static final String KEY_RECORDS = "analysis_records";
    private static final int MAX_RECORDS = 20;

    private final SharedPreferences prefs;

    public BugReportHistoryManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Save an analysis result as a new history record.
     */
    public void saveRecord(AnalysisResult result) {
        if (result == null || result.summary == null) return;

        HistoryRecord record = new HistoryRecord(
            result.analysisTimestamp > 0 ? result.analysisTimestamp : System.currentTimeMillis(),
            result.rawFileName != null ? result.rawFileName : "未知文件",
            result.deviceInfo != null ? result.deviceInfo.model : "未知设备",
            result.summary.overallHealth,
            result.summary.anomalyCount,
            result.summary.criticalAnomalyCount,
            result.deviceInfo != null ? result.deviceInfo.healthPercentage : 0,
            result.deviceInfo != null ? result.deviceInfo.cycleCount : 0,
            result.deviceInfo != null ? result.deviceInfo.designCapacityMah : 0,
            result.deviceInfo != null ? result.deviceInfo.currentCapacityMah : 0
        );

        List<HistoryRecord> records = getRecords();
        records.add(0, record); // Add to front (newest first)

        // Trim to max size
        if (records.size() > MAX_RECORDS) {
            records = records.subList(0, MAX_RECORDS);
        }

        saveRecords(records);
    }

    /**
     * Get all history records, newest first.
     */
    public List<HistoryRecord> getRecords() {
        String data = prefs.getString(KEY_RECORDS, "");
        if (data.isEmpty()) return new ArrayList<>();

        String[] entries = data.split(";;;");
        List<HistoryRecord> records = new ArrayList<>();
        for (String entry : entries) {
            HistoryRecord record = HistoryRecord.deserialize(entry);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    /**
     * Delete a specific history record by timestamp.
     */
    public void deleteRecord(long timestamp) {
        List<HistoryRecord> records = getRecords();
        records.removeIf(r -> r.timestamp == timestamp);
        saveRecords(records);
    }

    /**
     * Clear all history records.
     */
    public void clearAll() {
        prefs.edit().remove(KEY_RECORDS).apply();
    }

    /**
     * Get the count of history records.
     */
    public int getRecordCount() {
        return getRecords().size();
    }

    /**
     * Compare two history records and return a diff summary.
     */
    public static String compareRecords(HistoryRecord older, HistoryRecord newer) {
        if (older == null || newer == null) return "无法对比";

        StringBuilder sb = new StringBuilder();
        if (older.healthPercentage > 0 && newer.healthPercentage > 0) {
            float diff = newer.healthPercentage - older.healthPercentage;
            sb.append(String.format("健康度变化: %.1f%% → %.1f%% (%s%.1f%%)\n",
                older.healthPercentage, newer.healthPercentage,
                diff >= 0 ? "+" : "", diff));
        }
        if (older.cycleCount > 0 && newer.cycleCount > 0) {
            int diff = newer.cycleCount - older.cycleCount;
            sb.append(String.format("循环次数: %d → %d (+%d)\n",
                older.cycleCount, newer.cycleCount, diff));
        }
        if (older.designCapacity > 0 && newer.designCapacity > 0) {
            int diff = newer.designCapacity - older.designCapacity;
            sb.append(String.format("设计容量: %d → %d mAh (%s%d)\n",
                older.designCapacity, newer.designCapacity,
                diff >= 0 ? "+" : "", diff));
        }
        if (older.currentCapacity > 0 && newer.currentCapacity > 0) {
            int diff = newer.currentCapacity - older.currentCapacity;
            sb.append(String.format("当前容量: %d → %d mAh (%s%d)\n",
                older.currentCapacity, newer.currentCapacity,
                diff >= 0 ? "+" : "", diff));
        }
        if (older.anomalyCount != newer.anomalyCount) {
            sb.append(String.format("异常数量: %d → %d\n", older.anomalyCount, newer.anomalyCount));
        }

        return sb.length() > 0 ? sb.toString() : "两次分析结果无显著变化";
    }

    private void saveRecords(List<HistoryRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < records.size(); i++) {
            if (i > 0) sb.append(";;;");
            sb.append(records.get(i).serialize());
        }
        prefs.edit().putString(KEY_RECORDS, sb.toString()).apply();
    }
}
