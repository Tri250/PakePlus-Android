package com.batteryhealth.app.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.BugReportGuide;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BugReportAnalyzer {

    private static final String TAG = "BugReportAnalyzer";

    private Context context;
    private BatteryDataManager batteryDataManager;

    public BugReportAnalyzer(Context context) {
        this.context = context;
        this.batteryDataManager = new BatteryDataManager(context);
    }

    public BugReportGuide.AnalysisResult analyze(File bugReportFile) {
        BugReportGuide.AnalysisResult result = new BugReportGuide.AnalysisResult();
        result.anomalies = new ArrayList<>();
        result.chargeSessions = new ArrayList<>();
        result.wakelocks = new ArrayList<>();
        result.batteryEvents = new ArrayList<>();

        try {
            if (bugReportFile.getName().endsWith(".zip")) {
                parseZipBugReport(bugReportFile, result);
            } else {
                parseTextBugReport(bugReportFile, result);
            }

            analyzeBatteryEvents(result);
            crossReferenceWithLiveData(result);
            generateSummary(result);

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

    private void parseZipBugReport(File zipFile, BugReportGuide.AnalysisResult result) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName().toLowerCase();

                if (entryName.contains("battery") || entryName.contains("power") ||
                    entryName.contains("dumpsys") || entryName.endsWith(".txt") ||
                    entryName.contains("thermalservice") || entryName.contains("activity") ||
                    entryName.contains("proc")) {

                    StringBuilder content = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                        parseLine(line, result);
                    }
                    reader.close();

                    // Parse the full content for structured sections
                    String fullContent = content.toString();
                    parseDumpsysBatterySection(fullContent, result);
                    parseDumpsysBatterystatsSection(fullContent, result);
                }

                zis.closeEntry();
            }
        }
    }

    private void parseTextBugReport(File textFile, BugReportGuide.AnalysisResult result) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(textFile)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
                parseLine(line, result);
            }
        }
        String fullContent = content.toString();
        parseDumpsysBatterySection(fullContent, result);
        parseDumpsysBatterystatsSection(fullContent, result);
    }

    private void parseLine(String line, BugReportGuide.AnalysisResult result) {
        if (line == null) return;

        parseBatteryEvent(line, result);
        parseChargingSession(line, result);
        parseAnomaly(line, result);
        parseWakelock(line, result);
        parseDeviceInfo(line, result);
        parseThermalEvent(line, result);
        parseMemoryPressure(line, result);
    }

    /**
     * Parse dumpsys battery section for structured battery data.
     */
    private void parseDumpsysBatterySection(String content, BugReportGuide.AnalysisResult result) {
        // Extract from "DUMP OF SERVICE battery:" section
        Pattern batterySection = Pattern.compile(
                "DUMP OF SERVICE battery:(.*?)(?:DUMP OF SERVICE|$)",
                Pattern.DOTALL);
        Matcher matcher = batterySection.matcher(content);
        if (!matcher.find()) return;

        String section = matcher.group(1);

        // Parse health percentage
        Pattern healthPattern = Pattern.compile("health:\\s*(\\d+)");
        Matcher healthMatcher = healthPattern.matcher(section);
        if (healthMatcher.find()) {
            try {
                int health = Integer.parseInt(healthMatcher.group(1));
                if (health >= 0 && health <= 100) {
                    result.batteryEvents.add(new BugReportGuide.AnalysisResult.BatteryEvent(
                            System.currentTimeMillis(), "健康度", "Bugreport 记录健康度: " + health + "%"));
                }
            } catch (NumberFormatException ignored) {}
        }

        // Parse cycle count
        Pattern cyclePattern = Pattern.compile("cycle_count:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher cycleMatcher = cyclePattern.matcher(section);
        if (cycleMatcher.find()) {
            try {
                int cycles = Integer.parseInt(cycleMatcher.group(1));
                result.batteryEvents.add(new BugReportGuide.AnalysisResult.BatteryEvent(
                        System.currentTimeMillis(), "循环次数", "Bugreport 记录循环次数: " + cycles));
            } catch (NumberFormatException ignored) {}
        }

        // Parse design capacity and current capacity
        Pattern designCapPattern = Pattern.compile("(?:design_capacity|charge_full_design):\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher designCapMatcher = designCapPattern.matcher(section);
        if (designCapMatcher.find()) {
            try {
                int designCap = Integer.parseInt(designCapMatcher.group(1));
                result.batteryEvents.add(new BugReportGuide.AnalysisResult.BatteryEvent(
                        System.currentTimeMillis(), "设计容量", "Bugreport 记录设计容量: " + designCap + " mAh"));
            } catch (NumberFormatException ignored) {}
        }

        Pattern currentCapPattern = Pattern.compile("(?:charge_full|learned_capacity|full_charge_capacity):\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher currentCapMatcher = currentCapPattern.matcher(section);
        if (currentCapMatcher.find()) {
            try {
                int currentCap = Integer.parseInt(currentCapMatcher.group(1));
                result.batteryEvents.add(new BugReportGuide.AnalysisResult.BatteryEvent(
                        System.currentTimeMillis(), "满充容量", "Bugreport 记录满充容量: " + currentCap + " mAh"));
            } catch (NumberFormatException ignored) {}
        }
    }

    /**
     * Parse dumpsys batterystats section for charging session data.
     */
    private void parseDumpsysBatterystatsSection(String content, BugReportGuide.AnalysisResult result) {
        Pattern statsSection = Pattern.compile(
                "DUMP OF SERVICE batterystats:(.*?)(?:DUMP OF SERVICE|$)",
                Pattern.DOTALL);
        Matcher matcher = statsSection.matcher(content);
        if (!matcher.find()) return;

        String section = matcher.group(1);

        // Parse charging sessions from batterystats
        Pattern chargePattern = Pattern.compile(
                "Charge\\s+(\\d+)\\s+->\\s+(\\d+)\\s+.*?(?:power=|watt=)\\s*([\\d.]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher chargeMatcher = chargePattern.matcher(section);
        while (chargeMatcher.find()) {
            try {
                int startLevel = Integer.parseInt(chargeMatcher.group(1));
                int endLevel = Integer.parseInt(chargeMatcher.group(2));
                float power = Float.parseFloat(chargeMatcher.group(3));

                result.chargeSessions.add(new BugReportGuide.AnalysisResult.ChargeSession(
                        System.currentTimeMillis(), System.currentTimeMillis(),
                        startLevel, endLevel, extractChargeTypeFromPower(power),
                        power, power
                ));
            } catch (NumberFormatException ignored) {}
        }

        // Parse wakelocks from batterystats
        Pattern wakelockPattern = Pattern.compile(
                "Wake lock\\s+(\\S+)\\s+.*?(?:held|time)\\s*=\\s*(\\d+)(?:ms|s)?",
                Pattern.CASE_INSENSITIVE);
        Matcher wakelockMatcher = wakelockPattern.matcher(section);
        Map<String, BugReportGuide.AnalysisResult.AppWakelock> wakelockMap = new HashMap<>();
        for (BugReportGuide.AnalysisResult.AppWakelock w : result.wakelocks) {
            wakelockMap.put(w.packageName, w);
        }

        while (wakelockMatcher.find()) {
            String lockName = wakelockMatcher.group(1);
            long duration = 0;
            try {
                duration = Long.parseLong(wakelockMatcher.group(2));
            } catch (NumberFormatException ignored) {}

            String pkg = extractPackageName(lockName);
            String appName = extractAppName(lockName, pkg);

            BugReportGuide.AnalysisResult.AppWakelock existing = wakelockMap.get(pkg);
            if (existing != null) {
                existing.count++;
                existing.durationMs += duration > 0 ? duration : 60000;
            } else {
                result.wakelocks.add(new BugReportGuide.AnalysisResult.AppWakelock(
                        pkg, appName, duration > 0 ? duration : 60000, 1
                ));
                wakelockMap.put(pkg, result.wakelocks.get(result.wakelocks.size() - 1));
            }
        }
    }

    private void parseBatteryEvent(String line, BugReportGuide.AnalysisResult result) {
        if (!line.contains("BatteryManager") && !line.contains("battery") && !line.contains("Battery")) return;

        String eventType = extractEventType(line);
        String detail = extractDetail(line);
        if (eventType != null && !eventType.isEmpty() && detail != null && !detail.isEmpty()) {
            result.batteryEvents.add(new BugReportGuide.AnalysisResult.BatteryEvent(
                    extractTimestamp(line), eventType, detail
            ));
        }
    }

    private void parseChargingSession(String line, BugReportGuide.AnalysisResult result) {
        if (!line.contains("charging") && !line.contains("Charging") && !line.contains("charge")) return;

        int level = extractLevel(line);
        float power = extractPower(line);
        String type = extractChargeType(line);

        // Fix: proper operator precedence
        if (level >= 0 && (line.contains("start") || line.contains("START"))) {
            result.chargeSessions.add(new BugReportGuide.AnalysisResult.ChargeSession(
                    extractTimestamp(line), System.currentTimeMillis(),
                    level, -1, type, power, power
            ));
        } else if (level >= 0 && !result.chargeSessions.isEmpty()) {
            BugReportGuide.AnalysisResult.ChargeSession lastSession =
                    result.chargeSessions.get(result.chargeSessions.size() - 1);
            if (lastSession.endLevel == -1) {
                lastSession.endLevel = level;
                lastSession.endTime = extractTimestamp(line);
                if (power > lastSession.maxPower) {
                    lastSession.maxPower = power;
                }
            }
        }
    }

    private void parseAnomaly(String line, BugReportGuide.AnalysisResult result) {
        // ANR detection - more precise pattern
        if (line.matches(".*ANR in\\s+\\S+.*") || line.contains("ANR in com.")) {
            String pkg = extractPackageName(line);
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    extractTimestamp(line), "CRITICAL", "ANR",
                    "检测到应用无响应: " + pkg,
                    "建议检查 " + pkg + " 是否存在主线程阻塞问题"
            ));
        }

        // Battery temperature anomaly - more precise
        if (line.contains("temperature") && (line.contains("overheat") || line.contains("OVERHEAT")
                || line.matches(".*temp\\s*=\\s*[4-9]\\d{2}.*"))) {
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    extractTimestamp(line), "HIGH", "电池过热",
                    "检测到电池温度过高: " + line.trim(),
                    "建议停止使用手机，让电池冷却后再使用"
            ));
        }

        // Low capacity anomaly — only when line explicitly mentions battery capacity/health percentage
        if ((line.contains("capacity") || line.contains("health")) && line.contains("%")) {
            // Extract percentage values and check if they represent a low battery health
            Pattern pctPattern = Pattern.compile("(\\d+)%");
            Matcher pctMatcher = pctPattern.matcher(line);
            while (pctMatcher.find()) {
                try {
                    int val = Integer.parseInt(pctMatcher.group(1));
                    // Only flag values 0-59 that are clearly battery health/capacity percentages
                    // Skip common false positives: version numbers, API levels, charge levels
                    if (val >= 0 && val < 60) {
                        String lowerLine = line.toLowerCase();
                        // Confirm this is about battery health/capacity, not charge level or version
                        boolean isHealthOrCapacity = (lowerLine.contains("health") && !lowerLine.contains("charge level"))
                                || (lowerLine.contains("capacity") && !lowerLine.contains("charge level")
                                    && !lowerLine.contains("level") && !lowerLine.contains("version"));
                        if (isHealthOrCapacity) {
                            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                                    extractTimestamp(line), "HIGH", "电池容量低",
                                    "检测到电池容量/健康度偏低: " + val + "%",
                                    "建议考虑更换电池"
                            ));
                            break;
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // Crash detection
        if (line.contains("FATAL EXCEPTION") || line.contains("Process crashed")) {
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    extractTimestamp(line), "HIGH", "应用崩溃",
                    "检测到应用崩溃: " + line.trim(),
                    "建议检查应用版本是否为最新，或联系开发者"
            ));
        }
    }

    private void parseWakelock(String line, BugReportGuide.AnalysisResult result) {
        if (!line.contains("WakeLock") && !line.contains("wakelock") && !line.contains("wake_lock")) return;

        String packageName = extractPackageName(line);
        String appName = extractAppName(line, packageName);
        long duration = extractDuration(line);

        // Find existing entry efficiently
        BugReportGuide.AnalysisResult.AppWakelock existing = null;
        for (BugReportGuide.AnalysisResult.AppWakelock w : result.wakelocks) {
            if (w.packageName.equals(packageName)) {
                existing = w;
                break;
            }
        }

        if (existing != null) {
            existing.count++;
            if (duration > 0) {
                existing.durationMs += duration;
            }
        } else {
            BugReportGuide.AnalysisResult.AppWakelock newLock =
                    new BugReportGuide.AnalysisResult.AppWakelock(
                            packageName, appName, duration > 0 ? duration : 0, 1
                    );
            result.wakelocks.add(newLock);
        }
    }

    private void parseDeviceInfo(String line, BugReportGuide.AnalysisResult result) {
        if (result.deviceInfo != null) return; // Already parsed

        // Parse device info from bugreport header
        if (line.contains("Build fingerprint:")) {
            String fingerprint = line.substring(line.indexOf(":") + 1).trim();
            if (result.deviceInfo == null) {
                result.deviceInfo = new BugReportGuide.AnalysisResult.DeviceInfo(
                        Build.MODEL, Build.BRAND, Build.VERSION.RELEASE,
                        fingerprint, 0, 0, 0
                );
            }
        }
    }

    /**
     * Parse thermal throttling events.
     */
    private void parseThermalEvent(String line, BugReportGuide.AnalysisResult result) {
        if (line.contains("thermal") && (line.contains("throttle") || line.contains("THROTTLE"))) {
            result.batteryEvents.add(new BugReportGuide.AnalysisResult.BatteryEvent(
                    extractTimestamp(line), "温度节流", "检测到温度节流事件: " + line.trim()
            ));
        }

        // High temperature detection from battery stats
        if (line.matches(".*temp\\s*=\\s*[4-9]\\d{2}.*") || line.matches(".*temperature\\s*=\\s*[4-9]\\d{2}.*")) {
            result.batteryEvents.add(new BugReportGuide.AnalysisResult.BatteryEvent(
                    extractTimestamp(line), "高温警告", "检测到设备高温: " + line.trim()
            ));
        }
    }

    /**
     * Parse memory pressure events.
     */
    private void parseMemoryPressure(String line, BugReportGuide.AnalysisResult result) {
        if (line.contains("low_memory") || line.contains("Low Memory Killer") ||
                (line.contains("oom") && line.contains("kill"))) {
            result.batteryEvents.add(new BugReportGuide.AnalysisResult.BatteryEvent(
                    extractTimestamp(line), "内存压力", "检测到低内存事件: " + line.trim()
            ));
        }
    }

    /**
     * Cross-reference bugreport data with live BatteryDataManager data for more accurate analysis.
     */
    private void crossReferenceWithLiveData(BugReportGuide.AnalysisResult result) {
        try {
            batteryDataManager.refreshFromStickyIntent();
            BatteryInfo liveInfo = batteryDataManager.getCurrentBatteryInfo();
            if (liveInfo == null) return;

            // Compare live health with bugreport health
            float liveHealth = liveInfo.getHealthPercentage();
            if (liveHealth > 0) {
                result.batteryEvents.add(new BugReportGuide.AnalysisResult.BatteryEvent(
                        System.currentTimeMillis(), "实时健康度",
                        String.format("当前设备实时健康度: %.1f%%（置信度: %.0f%%）",
                                liveHealth, liveInfo.getHealthConfidence() * 100)
                ));
            }

            // Compare live cycle count
            int liveCycles = liveInfo.getCycleCount();
            if (liveCycles > 0) {
                result.batteryEvents.add(new BugReportGuide.AnalysisResult.BatteryEvent(
                        System.currentTimeMillis(), "实时循环次数",
                        String.format("当前设备实时循环次数: %d", liveCycles)
                ));
            }

            // Compare live design capacity
            int liveDesignCap = liveInfo.getDesignCapacity();
            if (liveDesignCap > 0) {
                result.batteryEvents.add(new BugReportGuide.AnalysisResult.BatteryEvent(
                        System.currentTimeMillis(), "实时设计容量",
                        String.format("当前设备实时设计容量: %d mAh", liveDesignCap)
                ));
            }

            // Compare live current capacity
            int liveCurrentCap = liveInfo.getCurrentCapacity();
            if (liveCurrentCap > 0) {
                result.batteryEvents.add(new BugReportGuide.AnalysisResult.BatteryEvent(
                        System.currentTimeMillis(), "实时满充容量",
                        String.format("当前设备实时满充容量: %d mAh", liveCurrentCap)
                ));
            }

            // Battery source verification
            String source = liveInfo.getBatterySource();
            if ("third_party".equals(source)) {
                result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                        System.currentTimeMillis(), "MEDIUM", "电池来源",
                        "实时检测发现电池可能非原装",
                        "建议前往官方售后验证电池来源"
                ));
            }

            // Update device info with live data
            if (result.deviceInfo == null) {
                result.deviceInfo = new BugReportGuide.AnalysisResult.DeviceInfo(
                        Build.MODEL, Build.BRAND, Build.VERSION.RELEASE,
                        Build.DISPLAY, liveDesignCap, liveCycles, liveHealth
                );
            } else {
                // Fill in missing data from live info
                if (result.deviceInfo.batteryCapacity <= 0 && liveDesignCap > 0) {
                    result.deviceInfo.batteryCapacity = liveDesignCap;
                }
                if (result.deviceInfo.cycleCount <= 0 && liveCycles > 0) {
                    result.deviceInfo.cycleCount = liveCycles;
                }
                if (result.deviceInfo.healthPercentage <= 0 && liveHealth > 0) {
                    result.deviceInfo.healthPercentage = liveHealth;
                }
            }

        } catch (Exception e) {
            Log.d(TAG, "Cross-reference with live data failed: " + e.getMessage());
        }
    }

    private void analyzeBatteryEvents(BugReportGuide.AnalysisResult result) {
        int chargingCount = 0;
        int dischargingCount = 0;
        int temperatureWarnings = 0;
        int thermalThrottles = 0;
        int memoryPressureEvents = 0;

        for (BugReportGuide.AnalysisResult.BatteryEvent event : result.batteryEvents) {
            String type = event.type != null ? event.type.toLowerCase() : "";
            if (type.contains("充电") || type.contains("charging")) {
                chargingCount++;
            } else if (type.contains("放电") || type.contains("discharging")) {
                dischargingCount++;
            } else if (type.contains("温度") || type.contains("temperature")) {
                temperatureWarnings++;
            } else if (type.contains("节流") || type.contains("throttle")) {
                thermalThrottles++;
            } else if (type.contains("内存") || type.contains("memory")) {
                memoryPressureEvents++;
            }
        }

        if (temperatureWarnings > 5) {
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    System.currentTimeMillis(), "HIGH", "温度异常",
                    "检测到多次温度警告，共 " + temperatureWarnings + " 次",
                    "建议检查手机散热情况，避免长时间高负载使用"
            ));
        }

        if (thermalThrottles > 3) {
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    System.currentTimeMillis(), "MEDIUM", "频繁温度节流",
                    "检测到 " + thermalThrottles + " 次温度节流事件，设备可能散热不佳",
                    "建议避免在高温环境下长时间使用，检查保护壳是否影响散热"
            ));
        }

        if (dischargingCount > chargingCount * 2) {
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    System.currentTimeMillis(), "MEDIUM", "耗电过快",
                    "放电次数远大于充电次数，可能存在耗电异常",
                    "建议检查后台应用和定位服务"
            ));
        }

        if (memoryPressureEvents > 5) {
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    System.currentTimeMillis(), "MEDIUM", "内存不足",
                    "检测到 " + memoryPressureEvents + " 次低内存事件",
                    "建议关闭不必要的后台应用，或考虑清理存储空间"
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

        // Factor in live data for health assessment
        float liveHealth = -1;
        try {
            BatteryInfo liveInfo = batteryDataManager.getCurrentBatteryInfo();
            if (liveInfo != null) liveHealth = liveInfo.getHealthPercentage();
        } catch (Exception ignored) {}

        String overallHealth = calculateOverallHealth(avgChargePower, anomalyCount, criticalAnomalyCount, liveHealth);

        result.summary = new BugReportGuide.AnalysisResult.Summary(
                totalChargeSessions, totalChargeDurationMs, avgChargePower,
                anomalyCount, criticalAnomalyCount, overallHealth
        );

        result.wakelocks.sort(Comparator.comparingLong(w -> -w.durationMs));
        if (result.wakelocks.size() > 10) {
            result.wakelocks = result.wakelocks.subList(0, 10);
        }
    }

    private String calculateOverallHealth(float avgPower, int anomalyCount, int criticalCount, float liveHealth) {
        float score = 100;

        // Factor in live health
        if (liveHealth > 0) {
            score = liveHealth;
        }

        if (avgPower < 5) {
            score -= 15;
        } else if (avgPower < 15) {
            score -= 5;
        }

        score -= anomalyCount * 3;
        score -= criticalCount * 8;

        if (score >= 85) return "优秀";
        if (score >= 70) return "良好";
        if (score >= 50) return "一般";
        return "较差";
    }

    // region Helper methods

    private long extractTimestamp(String line) {
        // Try to parse timestamp from line
        Pattern timePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2})");
        Matcher matcher = timePattern.matcher(line);
        if (matcher.find()) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                java.util.Date date = sdf.parse(matcher.group(1).replace("T", " "));
                if (date != null) return date.getTime();
            } catch (Exception ignored) {}
        }

        // Try epoch timestamp
        Pattern epochPattern = Pattern.compile("(?:timestamp|time|ts)[=:\\s]+(\\d{10,13})");
        Matcher epochMatcher = epochPattern.matcher(line);
        if (epochMatcher.find()) {
            try {
                long ts = Long.parseLong(epochMatcher.group(1));
                return ts > 1_000_000_000_000L ? ts : ts * 1000;
            } catch (NumberFormatException ignored) {}
        }

        return System.currentTimeMillis();
    }

    private String extractEventType(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("level") && (lower.contains("change") || lower.contains("="))) return "电量变化";
        if (lower.contains("temperature") || lower.contains("temp")) return "温度变化";
        if (lower.contains("voltage")) return "电压变化";
        if (lower.contains("current")) return "电流变化";
        if (lower.contains("charging") || lower.contains("status")) return "充电状态";
        if (lower.contains("health")) return "健康状态";
        return "电池事件";
    }

    private String extractDetail(String line) {
        // Try to extract key=value pairs
        int start = line.indexOf("[");
        int end = line.indexOf("]");
        if (start >= 0 && end > start) {
            return line.substring(start + 1, end);
        }
        // Try to extract from key=value patterns
        Pattern kvPattern = Pattern.compile("((?:level|temp|voltage|current|health|status|capacity)[=:]\\s*[^,\\s]+)");
        Matcher m = kvPattern.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(m.group(1));
        }
        if (sb.length() > 0) return sb.toString();

        return line.length() > 80 ? line.substring(0, 80) + "..." : line.trim();
    }

    private int extractLevel(String line) {
        // Try "level=X" pattern first
        Pattern levelPattern = Pattern.compile("level[=:]\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher m = levelPattern.matcher(line);
        if (m.find()) {
            try {
                int value = Integer.parseInt(m.group(1));
                if (value >= 0 && value <= 100) return value;
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    private float extractPower(String line) {
        Pattern powerPattern = Pattern.compile("(?:power|watt|w)[=:]\\s*([\\d.]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = powerPattern.matcher(line);
        if (m.find()) {
            try {
                float value = Float.parseFloat(m.group(1));
                if (value > 0 && value < 500) return value;
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private String extractChargeType(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("super") || lower.contains("ultra")) return "超级快充";
        if (lower.contains("fast") || lower.contains("quick")) return "快充";
        if (lower.contains("wireless") || lower.contains("qi")) return "无线充电";
        if (lower.contains("usb")) return "USB充电";
        if (lower.contains("ac")) return "交流充电";
        return "普通充电";
    }

    private String extractChargeTypeFromPower(float power) {
        if (power >= 100) return "超级快充";
        if (power >= 30) return "快充";
        if (power >= 10) return "普通充电";
        if (power > 0) return "慢充";
        return "未知";
    }

    private long extractDuration(String line) {
        Pattern durationPattern = Pattern.compile("(?:duration|time|held)[=:]\\s*(\\d+)(ms|s|m)?", Pattern.CASE_INSENSITIVE);
        Matcher m = durationPattern.matcher(line);
        if (m.find()) {
            try {
                long value = Long.parseLong(m.group(1));
                String unit = m.group(2);
                if (unit != null) {
                    switch (unit.toLowerCase()) {
                        case "s": return value * 1000;
                        case "m": return value * 60 * 1000;
                        default: return value; // ms or no unit
                    }
                }
                // If value > 100000, likely milliseconds
                return value > 100000 ? value : value * 1000;
            } catch (NumberFormatException ignored) {}
        }
        return 0; // Unknown duration — do not fabricate a value
    }

    private String extractPackageName(String line) {
        // Try package= pattern
        int start = line.indexOf("package=");
        if (start >= 0) {
            int end = line.indexOf(" ", start + 8);
            if (end > start) return line.substring(start + 8, end);
            return line.substring(start + 8);
        }

        // Try com.xxx.xxx pattern
        Pattern pkgPattern = Pattern.compile("(com\\.[a-z0-9_.]+)");
        Matcher m = pkgPattern.matcher(line);
        if (m.find()) return m.group(1);

        return "未知应用";
    }

    private String extractAppName(String line, String packageName) {
        if (packageName == null) return "未知应用";
        if (packageName.contains("com.android")) return "系统应用";
        if (packageName.contains("com.google")) return "Google 应用";
        if (packageName.contains("weixin") || packageName.contains("tencent.mm")) return "微信";
        if (packageName.contains("taobao") || packageName.contains("tmall")) return "淘宝/天猫";
        if (packageName.contains("douyin") || packageName.contains("bytedance")) return "抖音";
        if (packageName.contains("baidu")) return "百度";
        if (packageName.contains("jd") || packageName.contains("jingdong")) return "京东";
        if (packageName.contains("alipay")) return "支付宝";
        if (packageName.contains("meizu")) return "魅族应用";
        if (packageName.contains("xiaomi") || packageName.contains("miui")) return "小米应用";
        if (packageName.contains("oppo") || packageName.contains("coloros")) return "OPPO应用";
        if (packageName.contains("vivo") || packageName.contains("bbk")) return "vivo应用";
        if (packageName.contains("huawei") || packageName.contains("emui")) return "华为应用";
        if (packageName.contains("samsung") || packageName.contains("sec")) return "三星应用";

        int lastDot = packageName.lastIndexOf(".");
        if (lastDot >= 0 && lastDot < packageName.length() - 1) {
            String name = packageName.substring(lastDot + 1);
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }

        return packageName;
    }

    // endregion
}
