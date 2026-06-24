package com.batteryhealth.app.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.content.FileProvider;
import com.batteryhealth.app.data.model.BugReportGuide.AnalysisResult;
import static com.batteryhealth.app.data.model.BugReportGuide.AnalysisResult.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BugReportExportUtil {

    /**
     * Export analysis result to a text file and return the file.
     * The file is saved to app's external cache directory.
     */
    public static File exportToTextFile(Context context, AnalysisResult result) {
        // Generate filename with timestamp
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "bugreport_analysis_" + timestamp + ".txt";
        File exportDir = new File(context.getExternalCacheDir(), "exports");
        if (!exportDir.exists()) exportDir.mkdirs();
        File exportFile = new File(exportDir, fileName);

        // Build the full report text
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("  电池健康 - Bugreport 分析报告\n");
        sb.append("═══════════════════════════════════════\n\n");

        // Report metadata
        sb.append("生成时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n");
        sb.append("分析文件: ").append(result.rawFileName != null ? result.rawFileName : "未知").append("\n");
        if (result.parseDetail != null) {
            sb.append("解析详情: ").append(result.parseDetail).append("\n");
        }
        sb.append("\n");

        // Summary section
        if (result.summary != null) {
            sb.append("── 概览 ──────────────────────────────\n");
            sb.append("设备健康度: ").append(result.summary.overallHealth).append("\n");
            sb.append("充电次数: ").append(result.summary.totalChargeSessions).append(" 次\n");
            sb.append(String.format(Locale.getDefault(), "平均充电功率: %.1f W\n", result.summary.avgChargePower));
            sb.append("异常数量: ").append(result.summary.anomalyCount);
            sb.append(" (严重: ").append(result.summary.criticalAnomalyCount).append(")\n");

            if (!result.summary.extractedFields.isEmpty()) {
                sb.append("成功提取字段: ").append(String.join(", ", result.summary.extractedFields)).append("\n");
            }
            if (!result.summary.missingFields.isEmpty()) {
                sb.append("缺失字段: ").append(String.join(", ", result.summary.missingFields)).append("\n");
            }
            sb.append("\n");
        }

        // Device info section
        if (result.deviceInfo != null) {
            sb.append("── 设备信息 ──────────────────────────\n");
            DeviceInfo di = result.deviceInfo;
            appendIfNotEmpty(sb, "设备型号", di.model);
            appendIfNotEmpty(sb, "品牌", di.brand);
            appendIfNotEmpty(sb, "Android版本", di.androidVersion);
            appendIfNotEmpty(sb, "Build号", di.buildNumber);
            appendIfNotEmpty(sb, "序列号", di.serialNumber);
            appendIfNotEmpty(sb, "制造日期", di.manufacturingDate);
            if (di.designCapacityMah > 0) sb.append("设计容量: ").append(di.designCapacityMah).append(" mAh\n");
            if (di.currentCapacityMah > 0) sb.append("当前容量: ").append(di.currentCapacityMah).append(" mAh\n");
            if (di.batteryCapacity > 0) sb.append("电池容量: ").append(di.batteryCapacity).append(" mAh\n");
            if (di.cycleCount > 0) sb.append("循环次数: ").append(di.cycleCount).append(" 次\n");
            if (di.healthPercentage > 0) sb.append(String.format(Locale.getDefault(), "健康度: %.1f%%\n", di.healthPercentage));
            if (di.temperatureCelsius > 0) sb.append(String.format(Locale.getDefault(), "温度: %.1f°C\n", di.temperatureCelsius));
            if (di.screenOnTimeHours > 0) sb.append("亮屏时间: ").append(di.screenOnTimeHours).append(" 小时\n");
            sb.append("\n");
        }

        // Anomalies section
        if (result.anomalies != null && !result.anomalies.isEmpty()) {
            sb.append("── 异常检测结果 ──────────────────────\n");
            for (Anomaly anomaly : result.anomalies) {
                sb.append("[").append(anomaly.severity).append("] ").append(anomaly.type).append("\n");
                sb.append("  描述: ").append(anomaly.description).append("\n");
                sb.append("  建议: ").append(anomaly.suggestion).append("\n\n");
            }
        }

        // Battery events section
        if (result.batteryEvents != null && !result.batteryEvents.isEmpty()) {
            sb.append("── Bugreport 关键指标 ────────────────\n");
            for (BatteryEvent event : result.batteryEvents) {
                String time = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(event.timestamp));
                sb.append("[").append(time).append("] ").append(event.type);
                if (event.detail != null && !event.detail.isEmpty()) {
                    sb.append(" - ").append(event.detail);
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // Charge sessions section
        if (result.chargeSessions != null && !result.chargeSessions.isEmpty()) {
            sb.append("── 充电会话统计 ──────────────────────\n");
            for (ChargeSession session : result.chargeSessions) {
                long durationMs = session.endTime - session.startTime;
                sb.append(String.format(Locale.getDefault(),
                    "电量: %d%%→%d%%, 时长: %s, 功率: %.1fW (最高: %.1fW)\n",
                    session.startLevel, session.endLevel, formatDuration(durationMs),
                    session.avgPower, session.maxPower));
            }
            sb.append("\n");
        }

        // Wakelocks section
        if (result.wakelocks != null && !result.wakelocks.isEmpty()) {
            sb.append("── 耗电应用排行 ──────────────────────\n");
            for (AppWakelock wakelock : result.wakelocks) {
                sb.append(String.format(Locale.getDefault(),
                    "%s (%s) - 唤醒 %d 次, 持续 %s\n",
                    wakelock.appName, wakelock.packageName, wakelock.count,
                    formatDuration(wakelock.durationMs)));
            }
            sb.append("\n");
        }

        // Health checks section
        if (result.healthChecks != null && !result.healthChecks.isEmpty()) {
            sb.append("── 健康检查 ──────────────────────────\n");
            for (HealthCheck check : result.healthChecks) {
                sb.append("[").append(check.status).append("] ").append(check.checkType);
                if (check.detail != null && !check.detail.isEmpty()) {
                    sb.append(" - ").append(check.detail);
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("═══════════════════════════════════════\n");
        sb.append("  报告由「电池健康」App 本地生成\n");
        sb.append("  数据仅在本地处理，未上传任何服务器\n");
        sb.append("═══════════════════════════════════════\n");

        // Write to file
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(exportFile), StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
        } catch (IOException e) {
            return null;
        }

        return exportFile;
    }

    /**
     * Share the exported report file via system share sheet.
     */
    public static boolean shareReport(Context context, AnalysisResult result) {
        File file = exportToTextFile(context, result);
        if (file == null || !file.exists()) return false;

        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "电池健康 - Bugreport 分析报告");

            // Use FileProvider for Android 7.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                android.net.Uri uri = FileProvider.getUriForFile(
                    context, context.getPackageName() + ".fileprovider", file);
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                shareIntent.putExtra(Intent.EXTRA_STREAM, android.net.Uri.fromFile(file));
            }

            context.startActivity(Intent.createChooser(shareIntent, "分享分析报告"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void appendIfNotEmpty(StringBuilder sb, String label, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }

    private static String formatDuration(long ms) {
        if (ms < 0) ms = 0;
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d小时%d分钟", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format(Locale.getDefault(), "%d分钟", minutes);
        } else {
            return String.format(Locale.getDefault(), "%d秒", seconds);
        }
    }
}
