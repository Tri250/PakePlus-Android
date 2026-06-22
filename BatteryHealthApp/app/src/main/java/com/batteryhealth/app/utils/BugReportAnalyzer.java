package com.batteryhealth.app.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.batteryhealth.app.data.model.BugReportGuide;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BugReportAnalyzer {

    private static final String TAG = "BugReportAnalyzer";

    /** 限制列表大小，防止大文件 OOM */
    private static final int MAX_BATTERY_EVENTS = 500;
    private static final int MAX_ANOMALIES = 100;
    private static final int MAX_CHARGE_SESSIONS = 200;
    private static final int MAX_WAKELOCKS = 50;

    private final Context context;

    public BugReportAnalyzer(Context context) {
        this.context = context;
    }

    public BugReportGuide.AnalysisResult analyze(File bugReportFile) {
        BugReportGuide.AnalysisResult result = new BugReportGuide.AnalysisResult();
        result.anomalies = new ArrayList<>();
        result.chargeSessions = new ArrayList<>();
        result.wakelocks = new ArrayList<>();
        result.batteryEvents = new ArrayList<>();

        try {
            String fileName = bugReportFile.getName().toLowerCase();
            if (fileName.endsWith(".zip")) {
                parseZipBugReport(bugReportFile, result);
            } else {
                parseTextBugReport(bugReportFile, result);
            }

            analyzeBatteryEvents(result);
            generateSummary(result);

        } catch (OutOfMemoryError e) {
            Log.e(TAG, "OOM analyzing bug report", e);
            result.anomalies.clear();
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    System.currentTimeMillis(), "HIGH", "内存不足",
                    "bugreport 文件过大，无法完整解析",
                    "请尝试使用更小的 bugreport 文件"
            ));
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing bug report: " + e.getMessage(), e);
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    System.currentTimeMillis(), "HIGH", "分析错误",
                    "无法解析 bugreport 文件: " + e.getMessage(),
                    "请尝试重新生成 bugreport 文件"
            ));
        }

        return result;
    }

    /**
     * 解析 ZIP 格式 bugreport。
     * 关键修复：不调用 reader.close()（会关闭底层 ZipInputStream），改用不关闭底层流的读取方式。
     */
    private void parseZipBugReport(File zipFile, BugReportGuide.AnalysisResult result) throws IOException {
        boolean parsed = false;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName == null) continue;
                String lowerName = entryName.toLowerCase();

                // 优先查找主 bugreport 文件（bugreport-*.txt）
                if (lowerName.contains("bugreport") && lowerName.endsWith(".txt")) {
                    // 流式逐行解析，不全部读入内存
                    parseStream(zis, result);
                    parsed = true;
                    break;
                }
                zis.closeEntry();
            }
        }

        // 回退：如果没找到 bugreport*.txt，尝试解析所有 .txt 条目
        if (!parsed) {
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (entryName == null) continue;
                    String lowerName = entryName.toLowerCase();
                    if (lowerName.endsWith(".txt") || lowerName.contains("battery")
                            || lowerName.contains("dumpsys") || lowerName.contains("power")) {
                        parseStream(zis, result);
                        parsed = true;
                        break;
                    }
                    zis.closeEntry();
                }
            }
        }

        if (!parsed) {
            throw new IOException("ZIP 中未找到可解析的 bugreport 文本文件");
        }
    }

    /**
     * 解析纯文本 bugreport。流式逐行读取，避免 OOM。
     */
    private void parseTextBugReport(File textFile, BugReportGuide.AnalysisResult result) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(textFile), StandardCharsets.UTF_8))) {
            parseStream(reader, result);
        }
    }

    /**
     * 流式逐行解析 bugreport 内容。
     * 从 InputStream（ZIP 条目）或 BufferedReader（纯文本）读取。
     */
    private void parseStream(InputStream is, BugReportGuide.AnalysisResult result) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        parseStream(reader, result);
        // 不关闭 reader，因为可能包装着 ZipInputStream（由外层 try-with-resources 管理）
    }

    private void parseStream(BufferedReader reader, BugReportGuide.AnalysisResult result) throws IOException {
        String line;
        String currentSection = "";

        while ((line = reader.readLine()) != null) {
            // 识别 DUMP OF SERVICE 段落起始
            if (line.contains("DUMP OF SERVICE")) {
                currentSection = line;
                continue;
            }
            // 仅当遇到下一个 DUMP OF SERVICE 时才切换段落，分隔线不清空段落
            // （bugreport 中大量含 ------ 的行会误清空段落）

            parseLine(line, result, currentSection);

            // 在 batterystats / battery 段落中提取电池统计
            if (currentSection.contains("batterystats") || currentSection.contains("battery")) {
                parseBatteryStatsLine(line, result);
            }
        }
    }

    /**
     * 解析 batterystats/battery 段落中的关键字段。
     * 修复：使用正则提取第一个数字，避免 replaceAll 拼接多个数字。
     */
    private void parseBatteryStatsLine(String line, BugReportGuide.AnalysisResult result) {
        // 提取设计容量: "Estimated battery capacity: 4000 mAh" 或 "Capacity: 4000"
        if (line.contains("apacity:") && (line.contains("mAh") || line.contains("mah"))) {
            try {
                Matcher m = Pattern.compile("(\\d+)\\s*mAh", Pattern.CASE_INSENSITIVE).matcher(line);
                if (m.find()) {
                    int mah = Integer.parseInt(m.group(1));
                    if (mah > 100 && mah < 20000) {
                        updateDeviceInfo(result, mah, -1);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 提取循环次数: "charge cycles: 42" 或 "Charge full cycles: 42"
        String lowerLine = line.toLowerCase();
        if (lowerLine.contains("cycle") && lowerLine.contains(":")) {
            try {
                Matcher m = Pattern.compile("(\\d+)").matcher(line.substring(line.indexOf(':') + 1));
                if (m.find()) {
                    int cycles = Integer.parseInt(m.group(1));
                    if (cycles >= 0 && cycles < 100000) {
                        updateDeviceInfo(result, -1, cycles);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 提取 charge_full（当前满充容量）: "Charge full: 3800 mAh"
        if (line.contains("harge") && line.contains("full") && line.contains("mAh")) {
            try {
                Matcher m = Pattern.compile("(\\d+)\\s*mAh", Pattern.CASE_INSENSITIVE).matcher(line);
                if (m.find()) {
                    int mah = Integer.parseInt(m.group(1));
                    if (mah > 100 && mah < 20000 && result.deviceInfo != null && result.deviceInfo.batteryCapacity > 0) {
                        // 计算健康度
                        float health = (mah * 100f) / result.deviceInfo.batteryCapacity;
                        result.deviceInfo.healthPercentage = Math.max(0f, Math.min(100f, health));
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 更新 DeviceInfo，保留已有字段。
     */
    private void updateDeviceInfo(BugReportGuide.AnalysisResult result, int batteryMah, int cycleCount) {
        String model = result.deviceInfo != null ? result.deviceInfo.model : Build.MODEL;
        String brand = result.deviceInfo != null ? result.deviceInfo.brand : Build.BRAND;
        String androidVer = result.deviceInfo != null ? result.deviceInfo.androidVersion : Build.VERSION.RELEASE;
        String buildNum = result.deviceInfo != null ? result.deviceInfo.buildNumber : Build.DISPLAY;
        int mah = batteryMah > 0 ? batteryMah : (result.deviceInfo != null ? result.deviceInfo.batteryCapacity : 0);
        int cycles = cycleCount >= 0 ? cycleCount : (result.deviceInfo != null ? result.deviceInfo.cycleCount : 0);
        float health = result.deviceInfo != null ? result.deviceInfo.healthPercentage : 0f;

        result.deviceInfo = new BugReportGuide.AnalysisResult.DeviceInfo(
                model, brand, androidVer, buildNum, mah, cycles, health
        );
    }

    private void parseLine(String line, BugReportGuide.AnalysisResult result, String currentSection) {
        if (line == null) return;

        // 从 bugreport 头部解析设备信息
        parseDeviceInfo(line, result);

        // 仅在非 batterystats 段落解析事件/会话/异常（避免 batterystats 段落海量匹配）
        if (!currentSection.contains("batterystats")) {
            parseBatteryEvent(line, result);
            parseChargingSession(line, result);
            parseAnomaly(line, result);
            parseWakelock(line, result);
        }
    }

    private void parseBatteryEvent(String line, BugReportGuide.AnalysisResult result) {
        if (result.batteryEvents.size() >= MAX_BATTERY_EVENTS) return;
        if (line.contains("BatteryManager") || (line.contains("battery") && line.contains("level"))) {
            String eventType = extractEventType(line);
            String detail = extractDetail(line);
            if (eventType != null && !eventType.isEmpty()) {
                result.batteryEvents.add(new BugReportGuide.AnalysisResult.BatteryEvent(
                        System.currentTimeMillis(), eventType, detail
                ));
            }
        }
    }

    private void parseChargingSession(String line, BugReportGuide.AnalysisResult result) {
        if (result.chargeSessions.size() >= MAX_CHARGE_SESSIONS) return;
        if (line.contains("charging") || line.contains("Charging")) {
            int level = extractLevel(line);
            float power = extractPower(line);
            String type = extractChargeType(line);

            if (level >= 0 && !result.chargeSessions.isEmpty()) {
                BugReportGuide.AnalysisResult.ChargeSession lastSession = result.chargeSessions.get(result.chargeSessions.size() - 1);
                if (lastSession.endLevel == -1) {
                    lastSession.endLevel = level;
                    lastSession.endTime = System.currentTimeMillis();
                    if (power > lastSession.maxPower) {
                        lastSession.maxPower = power;
                    }
                }
            } else if (level >= 0 && (line.contains("start") || line.contains("START"))) {
                result.chargeSessions.add(new BugReportGuide.AnalysisResult.ChargeSession(
                        System.currentTimeMillis(), System.currentTimeMillis(),
                        level, -1, type, power, power
                ));
            }
        }
    }

    private void parseAnomaly(String line, BugReportGuide.AnalysisResult result) {
        if (result.anomalies.size() >= MAX_ANOMALIES) return;
        if (line.contains("ANR ") || line.contains(" ANR ")) {
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    System.currentTimeMillis(), "CRITICAL", "ANR",
                    "检测到应用无响应: " + line.trim(),
                    "建议检查后台运行的应用，可能存在内存泄漏或CPU占用过高"
            ));
        }

        if (line.contains("temperature") && line.contains("high")) {
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    System.currentTimeMillis(), "HIGH", "电池过热",
                    "检测到电池温度过高: " + line.trim(),
                    "建议停止使用手机，让电池冷却后再使用"
            ));
        }
    }

    private void parseWakelock(String line, BugReportGuide.AnalysisResult result) {
        if (result.wakelocks.size() >= MAX_WAKELOCKS) return;
        if ((line.contains("WakeLock") || line.contains("wakelock")) && line.contains("partial")) {
            String packageName = extractPackageName(line);
            String appName = extractAppName(line, packageName);

            BugReportGuide.AnalysisResult.AppWakelock existing = null;
            for (BugReportGuide.AnalysisResult.AppWakelock w : result.wakelocks) {
                if (w.packageName.equals(packageName)) {
                    existing = w;
                    break;
                }
            }

            if (existing != null) {
                existing.count++;
            } else {
                result.wakelocks.add(new BugReportGuide.AnalysisResult.AppWakelock(
                        packageName, appName, 60000, 1
                ));
            }
        }
    }

    /**
     * 从 bugreport 头部解析设备信息（Build: brand/model/device）。
     */
    private void parseDeviceInfo(String line, BugReportGuide.AnalysisResult result) {
        if (result.deviceInfo != null) return; // 已设置
        if (line.startsWith("Build: ") || line.contains("Build: ")) {
            try {
                // bugreport 头部格式: "Build: brand/model/device: ..."
                Matcher m = Pattern.compile("Build:\\s*(\\S+)/(\\S+)/(\\S+)").matcher(line);
                if (m.find()) {
                    String brand = m.group(1);
                    String model = m.group(2);
                    result.deviceInfo = new BugReportGuide.AnalysisResult.DeviceInfo(
                            model, brand, Build.VERSION.RELEASE, Build.DISPLAY, 0, 0, 0f
                    );
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void analyzeBatteryEvents(BugReportGuide.AnalysisResult result) {
        int chargingCount = 0;
        int dischargingCount = 0;
        int temperatureWarnings = 0;

        for (BugReportGuide.AnalysisResult.BatteryEvent event : result.batteryEvents) {
            if (event.type.contains("charging")) {
                chargingCount++;
            } else if (event.type.contains("discharging")) {
                dischargingCount++;
            } else if (event.type.contains("temperature")) {
                temperatureWarnings++;
            }
        }

        if (temperatureWarnings > 5) {
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    System.currentTimeMillis(), "HIGH", "温度异常",
                    "检测到多次温度警告，共 " + temperatureWarnings + " 次",
                    "建议检查手机散热情况，避免长时间高负载使用"
            ));
        }

        if (dischargingCount > chargingCount * 2) {
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    System.currentTimeMillis(), "MEDIUM", "耗电过快",
                    "放电次数远大于充电次数，可能存在耗电异常",
                    "建议检查后台应用和定位服务"
            ));
        }
    }

    private void generateSummary(BugReportGuide.AnalysisResult result) {
        int totalChargeSessions = result.chargeSessions.size();
        long totalChargeDurationMs = 0;
        float totalPower = 0;
        int anomalyCount = result.anomalies.size();
        int criticalAnomalyCount = 0;

        for (BugReportGuide.AnalysisResult.ChargeSession session : result.chargeSessions) {
            totalChargeDurationMs += (session.endTime - session.startTime);
            totalPower += session.avgPower;
        }

        float avgChargePower = totalChargeSessions > 0 ? totalPower / totalChargeSessions : 0;

        for (BugReportGuide.AnalysisResult.Anomaly anomaly : result.anomalies) {
            if ("CRITICAL".equals(anomaly.severity)) {
                criticalAnomalyCount++;
            }
        }

        String overallHealth = calculateOverallHealth(avgChargePower, anomalyCount, criticalAnomalyCount);

        result.summary = new BugReportGuide.AnalysisResult.Summary(
                totalChargeSessions, totalChargeDurationMs, avgChargePower,
                anomalyCount, criticalAnomalyCount, overallHealth
        );

        result.wakelocks.sort(Comparator.comparingLong(w -> -w.durationMs));
        if (result.wakelocks.size() > 10) {
            result.wakelocks = result.wakelocks.subList(0, 10);
        }
    }

    private String calculateOverallHealth(float avgPower, int anomalyCount, int criticalCount) {
        float score = 100;

        if (avgPower < 5) {
            score -= 15;
        } else if (avgPower < 15) {
            score -= 5;
        }

        score -= anomalyCount * 5;
        score -= criticalCount * 10;

        if (score >= 85) return "优秀";
        if (score >= 70) return "良好";
        if (score >= 50) return "一般";
        return "较差";
    }

    private String extractEventType(String line) {
        if (line.contains("level")) return "电量变化";
        if (line.contains("temperature")) return "温度变化";
        if (line.contains("voltage")) return "电压变化";
        if (line.contains("current")) return "电流变化";
        if (line.contains("charging")) return "充电状态";
        return "电池事件";
    }

    private String extractDetail(String line) {
        int start = line.indexOf("[");
        int end = line.indexOf("]");
        if (start >= 0 && end > start) {
            return line.substring(start + 1, end);
        }
        return line.length() > 50 ? line.substring(0, 50) : line;
    }

    private int extractLevel(String line) {
        try {
            Matcher m = Pattern.compile("level[:\\s]+(\\d+)").matcher(line);
            if (m.find()) {
                int value = Integer.parseInt(m.group(1));
                if (value >= 0 && value <= 100) return value;
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return -1;
    }

    private float extractPower(String line) {
        try {
            Matcher m = Pattern.compile("power[:\\s]+([\\d.]+)").matcher(line);
            if (m.find()) {
                float value = Float.parseFloat(m.group(1));
                if (value > 0 && value < 200) return value;
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return 0;
    }

    private String extractChargeType(String line) {
        if (line.contains("fast") || line.contains("Fast")) return "快充";
        if (line.contains("wireless") || line.contains("Wireless")) return "无线充电";
        if (line.contains("usb") || line.contains("USB")) return "USB充电";
        return "普通充电";
    }

    private String extractPackageName(String line) {
        int start = line.indexOf("package=");
        if (start >= 0) {
            int end = line.indexOf(" ", start);
            if (end > start) {
                return line.substring(start + 8, end);
            }
            return line.substring(start + 8);
        }

        start = line.indexOf("/");
        if (start >= 0) {
            int end = line.indexOf(" ", start);
            if (end > start) {
                return line.substring(0, end);
            }
            return line.substring(0, Math.min(start + 50, line.length()));
        }

        return "未知应用";
    }

    private String extractAppName(String line, String packageName) {
        if (packageName.contains("com.android")) return "系统应用";
        if (packageName.contains("com.google")) return "Google 应用";
        if (packageName.contains("weixin") || packageName.contains("tencent")) return "微信";
        if (packageName.contains("taobao")) return "淘宝";
        if (packageName.contains("douyin") || packageName.contains("bytedance")) return "抖音";
        if (packageName.contains("baidu")) return "百度";
        if (packageName.contains("jd")) return "京东";

        int lastDot = packageName.lastIndexOf(".");
        if (lastDot >= 0) {
            String name = packageName.substring(lastDot + 1);
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }

        return packageName;
    }
}
