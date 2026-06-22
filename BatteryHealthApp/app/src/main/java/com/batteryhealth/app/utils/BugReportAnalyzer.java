package com.batteryhealth.app.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.batteryhealth.app.data.model.BugReportGuide;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName().toLowerCase();
                
                if (entryName.contains("battery") || entryName.contains("power") || 
                    entryName.contains("dumpsys") || entryName.endsWith(".txt")) {
                    
                    StringBuilder content = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                        parseLine(line, result);
                    }
                    reader.close();
                }
                
                zis.closeEntry();
            }
        }
    }

    private void parseTextBugReport(File textFile, BugReportGuide.AnalysisResult result) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(textFile)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseLine(line, result);
            }
        }
    }

    private void parseLine(String line, BugReportGuide.AnalysisResult result) {
        if (line == null) return;

        parseBatteryEvent(line, result);
        parseChargingSession(line, result);
        parseAnomaly(line, result);
        parseWakelock(line, result);
        parseDeviceInfo(line, result);
    }

    private void parseBatteryEvent(String line, BugReportGuide.AnalysisResult result) {
        if (line.contains("BatteryManager") || line.contains("battery")) {
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
            } else if (level >= 0 && line.contains("start") || line.contains("START")) {
                result.chargeSessions.add(new BugReportGuide.AnalysisResult.ChargeSession(
                        System.currentTimeMillis(), System.currentTimeMillis(),
                        level, -1, type, power, power
                ));
            }
        }
    }

    private void parseAnomaly(String line, BugReportGuide.AnalysisResult result) {
        if (line.contains("ANR") || line.contains("anr")) {
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    System.currentTimeMillis(), "CRITICAL", "ANR",
                    "检测到应用无响应: " + line,
                    "建议检查后台运行的应用，可能存在内存泄漏或CPU占用过高"
            ));
        }
        
        if (line.contains("Wakelock") || line.contains("wakelock")) {
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    System.currentTimeMillis(), "MEDIUM", "异常唤醒",
                    "检测到异常唤醒锁: " + line,
                    "建议检查耗电应用，可能存在过度唤醒问题"
            ));
        }

        if (line.contains("temperature") && line.contains("high")) {
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    System.currentTimeMillis(), "HIGH", "电池过热",
                    "检测到电池温度过高: " + line,
                    "建议停止使用手机，让电池冷却后再使用"
            ));
        }

        if (line.contains("battery") && line.contains("low") && line.contains("capacity")) {
            result.anomalies.add(new BugReportGuide.AnalysisResult.Anomaly(
                    System.currentTimeMillis(), "HIGH", "电池容量低",
                    "检测到电池容量偏低: " + line,
                    "建议考虑更换电池"
            ));
        }
    }

    private void parseWakelock(String line, BugReportGuide.AnalysisResult result) {
        if (line.contains("WakeLock") || line.contains("wakelock")) {
            String packageName = extractPackageName(line);
            String appName = extractAppName(line, packageName);
            
            Map<String, BugReportGuide.AnalysisResult.AppWakelock> wakelockMap = new HashMap<>();
            for (BugReportGuide.AnalysisResult.AppWakelock w : result.wakelocks) {
                wakelockMap.put(w.packageName, w);
            }
            
            BugReportGuide.AnalysisResult.AppWakelock existing = wakelockMap.get(packageName);
            if (existing != null) {
                existing.count++;
                existing.durationMs += 60000;
            } else {
                result.wakelocks.add(new BugReportGuide.AnalysisResult.AppWakelock(
                        packageName, appName, 60000, 1
                ));
            }
        }
    }

    private void parseDeviceInfo(String line, BugReportGuide.AnalysisResult result) {
        if (result.deviceInfo == null) {
            result.deviceInfo = new BugReportGuide.AnalysisResult.DeviceInfo(
                    Build.MODEL, Build.BRAND, Build.VERSION.RELEASE,
                    Build.DISPLAY, 0, 0, 0
            );
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
            String[] parts = line.split("[^0-9]");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    int value = Integer.parseInt(part);
                    if (value >= 0 && value <= 100) {
                        return value;
                    }
                }
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return -1;
    }

    private float extractPower(String line) {
        try {
            String[] parts = line.split("[^0-9.]");
            for (String part : parts) {
                if (!part.isEmpty() && part.contains(".")) {
                    float value = Float.parseFloat(part);
                    if (value > 0 && value < 200) {
                        return value;
                    }
                }
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