package com.batteryhealth.app.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.BugReportGuide;
import com.batteryhealth.app.data.model.BugReportGuide.AnalysisResult;
import com.batteryhealth.app.data.model.BugReportGuide.AnalysisResult.Anomaly;
import com.batteryhealth.app.data.model.BugReportGuide.AnalysisResult.AppWakelock;
import com.batteryhealth.app.data.model.BugReportGuide.AnalysisResult.BatteryEvent;
import com.batteryhealth.app.data.model.BugReportGuide.AnalysisResult.ChargeSession;
import com.batteryhealth.app.data.model.BugReportGuide.AnalysisResult.DeviceInfo;
import com.batteryhealth.app.data.model.BugReportGuide.AnalysisResult.HealthCheck;
import com.batteryhealth.app.data.model.BugReportGuide.AnalysisResult.Summary;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BugReportAnalyzer {

    private static final String TAG = "BugReportAnalyzer";
    private static final int TOTAL_ANALYSIS_STEPS = 6;

    // ==================== 预编译正则 ====================
    // bugreport 可达数万行，所有 Pattern 必须预编译为静态常量，
    // 避免在 parseLine 逐行调用路径上重复 Pattern.compile 造成 CPU 与 GC 压力。

    // dumpsys battery 段落与字段
    private static final Pattern RE_BATTERY_SECTION = Pattern.compile(
            "DUMP OF SERVICE battery:(.*?)(?:DUMP OF SERVICE|$)", Pattern.DOTALL);
    private static final Pattern RE_HEALTH = Pattern.compile("health:\\s*(\\d+)");
    private static final Pattern RE_CYCLE = Pattern.compile("cycle_count:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_DESIGN_CAP = Pattern.compile(
            "(?:design_capacity|charge_full_design):\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_CURRENT_CAP = Pattern.compile(
            "(?:charge_full|learned_capacity|full_charge_capacity):\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_SERIAL_BATTERY = Pattern.compile(
            "(?:serial_number|serialno):\\s*(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_TEMP = Pattern.compile("temperature:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_VOLTAGE = Pattern.compile("voltage:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_TECH = Pattern.compile("technology:\\s*(\\S+)", Pattern.CASE_INSENSITIVE);

    // dumpsys batterystats 段落与字段
    private static final Pattern RE_STATS_SECTION = Pattern.compile(
            "DUMP OF SERVICE batterystats:(.*?)(?:DUMP OF SERVICE|$)", Pattern.DOTALL);
    private static final Pattern RE_CHARGE_SESSION = Pattern.compile(
            "Charge\\s+(\\d+)\\s*->\\s*(\\d+)\\s+.*?(?:power=|watt=)\\s*([\\d.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_WAKELOCK_STATS = Pattern.compile(
            "Wake lock\\s+(\\S+)\\s+.*?(?:held|time)\\s*=\\s*(\\d+)(?:ms|s)?", Pattern.CASE_INSENSITIVE);

    // 逐行解析字段
    private static final Pattern RE_PERCENT = Pattern.compile("(\\d+)%");
    private static final Pattern RE_TEMP_HIGH = Pattern.compile(".*temp\\s*=\\s*[4-9]\\d{2}.*");
    private static final Pattern RE_TEMPERATURE_HIGH = Pattern.compile(".*temperature\\s*=\\s*[4-9]\\d{2}.*");
    private static final Pattern RE_SERIALNO = Pattern.compile("ro\\.serialno=\\s*(\\S+)");
    private static final Pattern RE_SERIAL_NUM = Pattern.compile("serial_number:\\s*(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_IMEI = Pattern.compile("IMEI:\\s*(\\d{15})");
    private static final Pattern RE_BATTERY_VOLTAGE = Pattern.compile(
            "battery voltage:\\s*(\\d+\\.?\\d*)\\s*mV", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_BATTERY_CURRENT = Pattern.compile(
            "battery current:\\s*(-?\\d+\\.?\\d*)\\s*mA", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_SCREEN_ON = Pattern.compile(
            "Screen on time:\\s*(\\d+\\.?\\d*)\\s*h", Pattern.CASE_INSENSITIVE);

    // 提取辅助
    private static final Pattern RE_TIMESTAMP = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2})");
    private static final Pattern RE_EPOCH = Pattern.compile(
            "(?:timestamp|time|ts)[=:\\s]+(\\d{10,13})");
    private static final Pattern RE_KV = Pattern.compile(
            "((?:level|temp|voltage|current|health|status|capacity)[=:]\\s*[^,\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_LEVEL = Pattern.compile("level[=:]\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_POWER = Pattern.compile(
            "(?:power|watt|w)[=:]\\s*([\\d.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_DURATION = Pattern.compile(
            "(?:duration|time|held)[=:]\\s*(\\d+)(ms|s|m)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_PKG = Pattern.compile("(com\\.[a-z0-9_.]+)");

    // 制造日期：预编译模式数组，避免逐行重复编译 16 个 Pattern
    private static final Pattern[] RE_MFG_DATE = new Pattern[16];
    private static final String[] MFG_DATE_SEP = new String[16];
    static {
        String[][] raw = {
                {"manufacturing_date:\\s*(\\d{4})-(\\d{2})-(\\d{2})", "-"},
                {"mfg_date:\\s*(\\d{4})-(\\d{2})-(\\d{2})", "-"},
                {"battery.*?date:\\s*(\\d{4})-(\\d{2})-(\\d{2})", "-"},
                {"first_use_date:\\s*(\\d{4})-(\\d{2})-(\\d{2})", "-"},
                {"battery_make_date:\\s*(\\d{4})-(\\d{2})-(\\d{2})", "-"},
                {"mfg_date:\\s*(\\d{4})(\\d{2})(\\d{2})", ""},
                {"manufacturing_date:\\s*(\\d{4})(\\d{2})(\\d{2})", ""},
                {"mfgdate:\\s*(\\d{4})(\\d{2})(\\d{2})", ""},
                {"battery_produce_date:\\s*(\\d{4})(\\d{2})(\\d{2})", ""},
                {"生产日期[:：]\\s*(\\d{4})[年/-](\\d{1,2})[月/-](\\d{1,2})", "-"},
                {"出厂日期[:：]\\s*(\\d{4})[年/-](\\d{1,2})[月/-](\\d{1,2})", "-"},
                {"manufacturing_date:\\s*(\\d{4})[./](\\d{2})[./](\\d{2})", "-"},
                {"mfg_date:\\s*(\\d{2})[./](\\d{2})[./](\\d{4})", "-"},
                {"Battery\\s+MFG\\s+Date:\\s*(\\d{4})[.-](\\d{2})[.-](\\d{2})", "-"},
                {"battery_production_date:\\s*(\\d{4})-(\\d{2})-(\\d{2})", "-"},
                {"battery_manufacture_time:\\s*(\\d{4})(\\d{2})(\\d{2})", ""}
        };
        for (int i = 0; i < raw.length; i++) {
            RE_MFG_DATE[i] = Pattern.compile(raw[i][0], Pattern.CASE_INSENSITIVE);
            MFG_DATE_SEP[i] = raw[i][1];
        }
    }

    private final Context context;
    private final BatteryDataManager batteryDataManager;
    // SimpleDateFormat 非线程安全，但本类实例仅在单一后台线程使用，复用即可
    private final SimpleDateFormat timestampSdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public interface AnalysisProgressCallback {
        void onProgress(int step, int totalSteps, String description);
        void onFileValidated(boolean valid, String reason);
        void onSectionParsed(String sectionName, int itemsFound);
    }

    public BugReportAnalyzer(Context context) {
        this.context = context;
        this.batteryDataManager = new BatteryDataManager(context);
    }

    public AnalysisResult analyze(File bugReportFile) {
        return analyze(bugReportFile, null);
    }

    public AnalysisResult analyze(File bugReportFile, AnalysisProgressCallback callback) {
        AnalysisResult result = new AnalysisResult();
        result.rawFileName = bugReportFile != null ? bugReportFile.getName() : "unknown";
        result.analysisTimestamp = System.currentTimeMillis();

        try {
            // Step 1: Validate file
            if (callback != null) callback.onProgress(1, TOTAL_ANALYSIS_STEPS, "验证文件格式");
            boolean valid = validateBugReportFile(bugReportFile);
            if (callback != null) {
                if (valid) {
                    callback.onFileValidated(true, "文件格式验证通过");
                } else {
                    callback.onFileValidated(false, "文件格式验证失败");
                }
            }
            if (!valid) {
                result.anomalies.add(new Anomaly(
                        System.currentTimeMillis(), "CRITICAL", "文件无效",
                        "bugreport 文件格式验证失败",
                        "请确保文件为有效的 Android bugreport（.zip 或 .txt）"
                ));
                generateSummary(result);
                return result;
            }

            // Step 2: Parse content
            if (callback != null) callback.onProgress(2, TOTAL_ANALYSIS_STEPS, "解析文件内容");
            if (bugReportFile.getName().endsWith(".zip")) {
                parseZipBugReport(bugReportFile, result, callback);
            } else {
                parseTextBugReport(bugReportFile, result, callback);
            }

            // Step 3: Cross-reference with live data
            if (callback != null) callback.onProgress(3, TOTAL_ANALYSIS_STEPS, "交叉比对实时数据");
            crossReferenceWithLiveData(result);

            // Step 4: Analyze battery events
            if (callback != null) callback.onProgress(4, TOTAL_ANALYSIS_STEPS, "分析电池事件");
            analyzeBatteryEvents(result);

            // Step 5: Generate summary
            if (callback != null) callback.onProgress(5, TOTAL_ANALYSIS_STEPS, "生成分析报告");
            generateSummary(result);

            // Step 6: Done
            if (callback != null) callback.onProgress(6, TOTAL_ANALYSIS_STEPS, "分析完成");

        } catch (Exception e) {
            Log.e(TAG, "Error analyzing bug report: " + e.getMessage(), e);
            result.anomalies.add(new Anomaly(
                    System.currentTimeMillis(), "HIGH", "分析错误",
                    "无法解析 bugreport 文件: " + e.getMessage(),
                    "请尝试重新生成 bugreport 文件"
            ));
        }

        return result;
    }

    public boolean validateBugReportFile(File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            Log.d(TAG, "File does not exist or is not readable");
            return false;
        }

        String name = file.getName().toLowerCase();
        if (!name.endsWith(".zip") && !name.endsWith(".txt")) {
            Log.d(TAG, "File extension is not .zip or .txt: " + name);
            return false;
        }

        long sizeKB = file.length() / 1024;
        if (sizeKB < 1 || file.length() > 2L * 1024 * 1024 * 1024) {
            Log.d(TAG, "File size out of range: " + sizeKB + " KB");
            return false;
        }

        if (name.endsWith(".txt")) {
            return validateTextBugReport(file);
        } else {
            return validateZipBugReport(file);
        }
    }

    private boolean validateTextBugReport(File file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 50) {
                lineCount++;
                if (line.contains("bugreport") || line.contains("dumpsys") || line.contains("Build fingerprint")) {
                    return true;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error validating text bugreport: " + e.getMessage());
        }
        return false;
    }

    private boolean validateZipBugReport(File file) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().toLowerCase().contains("bugreport")) {
                    return true;
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error validating zip bugreport: " + e.getMessage());
        }
        return false;
    }

    private void parseZipBugReport(File zipFile, AnalysisResult result, AnalysisProgressCallback callback) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
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

                    String fullContent = content.toString();
                    int itemsBefore = result.batteryEvents.size() + result.chargeSessions.size()
                            + result.anomalies.size() + result.wakelocks.size();
                    parseDumpsysBatterySection(fullContent, result);
                    parseDumpsysBatterystatsSection(fullContent, result);
                    int itemsAfter = result.batteryEvents.size() + result.chargeSessions.size()
                            + result.anomalies.size() + result.wakelocks.size();
                    if (callback != null) {
                        callback.onSectionParsed(entry.getName(), itemsAfter - itemsBefore);
                    }
                }

                zis.closeEntry();
            }
        }
    }

    private void parseTextBugReport(File textFile, AnalysisResult result, AnalysisProgressCallback callback) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(textFile)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
                parseLine(line, result);
            }
        }
        String fullContent = content.toString();
        int itemsBefore = result.batteryEvents.size() + result.chargeSessions.size()
                + result.anomalies.size() + result.wakelocks.size();
        parseDumpsysBatterySection(fullContent, result);
        parseDumpsysBatterystatsSection(fullContent, result);
        int itemsAfter = result.batteryEvents.size() + result.chargeSessions.size()
                + result.anomalies.size() + result.wakelocks.size();
        if (callback != null) {
            callback.onSectionParsed("text_bugreport", itemsAfter - itemsBefore);
        }
    }

    private void parseLine(String line, AnalysisResult result) {
        if (line == null || line.trim().isEmpty()) return;

        parseBatteryEvent(line, result);
        parseChargingSession(line, result);
        parseAnomaly(line, result);
        parseWakelock(line, result);
        parseDeviceInfo(line, result);
        parseThermalEvent(line, result);
        parseMemoryPressure(line, result);
        parseManufacturingDate(line, result);
        parseSerialNumber(line, result);
        parseVoltageCurrent(line, result);
        parseScreenOnTime(line, result);
    }

    private void parseDumpsysBatterySection(String content, AnalysisResult result) {
        Matcher matcher = RE_BATTERY_SECTION.matcher(content);
        if (!matcher.find()) return;

        String section = matcher.group(1);
        if (section == null) return;

        // Parse health percentage
        Matcher healthMatcher = RE_HEALTH.matcher(section);
        if (healthMatcher.find()) {
            try {
                int health = Integer.parseInt(healthMatcher.group(1));
                if (health >= 0 && health <= 100) {
                    result.batteryEvents.add(new BatteryEvent(
                            System.currentTimeMillis(), "健康度", "Bugreport 记录健康度: " + health + "%"));
                    if (result.deviceInfo != null) {
                        result.deviceInfo.healthPercentage = health;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        // Parse cycle count
        Matcher cycleMatcher = RE_CYCLE.matcher(section);
        if (cycleMatcher.find()) {
            try {
                int cycles = Integer.parseInt(cycleMatcher.group(1));
                result.batteryEvents.add(new BatteryEvent(
                        System.currentTimeMillis(), "循环次数", "Bugreport 记录循环次数: " + cycles));
                if (result.deviceInfo != null) {
                    result.deviceInfo.cycleCount = cycles;
                }
            } catch (NumberFormatException ignored) {}
        }

        // Parse design capacity
        Matcher designCapMatcher = RE_DESIGN_CAP.matcher(section);
        if (designCapMatcher.find()) {
            try {
                int designCap = Integer.parseInt(designCapMatcher.group(1));
                result.batteryEvents.add(new BatteryEvent(
                        System.currentTimeMillis(), "设计容量", "Bugreport 记录设计容量: " + designCap + " mAh"));
                if (result.deviceInfo != null) {
                    result.deviceInfo.designCapacityMah = designCap;
                }
            } catch (NumberFormatException ignored) {}
        }

        // Parse current capacity
        Matcher currentCapMatcher = RE_CURRENT_CAP.matcher(section);
        if (currentCapMatcher.find()) {
            try {
                int currentCap = Integer.parseInt(currentCapMatcher.group(1));
                result.batteryEvents.add(new BatteryEvent(
                        System.currentTimeMillis(), "满充容量", "Bugreport 记录满充容量: " + currentCap + " mAh"));
                if (result.deviceInfo != null) {
                    result.deviceInfo.currentCapacityMah = currentCap;
                }
            } catch (NumberFormatException ignored) {}
        }

        // Parse serial number from battery section
        Matcher serialMatcher = RE_SERIAL_BATTERY.matcher(section);
        if (serialMatcher.find()) {
            String serial = serialMatcher.group(1);
            if (result.deviceInfo != null) {
                result.deviceInfo.serialNumber = serial;
            }
            result.batteryEvents.add(new BatteryEvent(
                    System.currentTimeMillis(), "序列号", "Bugreport 记录序列号: " + serial));
        }

        // Parse manufacturing date from battery section
        parseManufacturingDateFromSection(section, result);

        // Parse temperature
        Matcher tempMatcher = RE_TEMP.matcher(section);
        if (tempMatcher.find()) {
            try {
                int tempRaw = Integer.parseInt(tempMatcher.group(1));
                float tempC = tempRaw / 10.0f;
                result.batteryEvents.add(new BatteryEvent(
                        System.currentTimeMillis(), "温度", "Bugreport 记录温度: " + tempC + "°C"));
                if (result.deviceInfo != null) {
                    result.deviceInfo.temperatureCelsius = tempC;
                }
            } catch (NumberFormatException ignored) {}
        }

        // Parse voltage
        Matcher voltageMatcher = RE_VOLTAGE.matcher(section);
        if (voltageMatcher.find()) {
            try {
                int voltage = Integer.parseInt(voltageMatcher.group(1));
                result.batteryEvents.add(new BatteryEvent(
                        System.currentTimeMillis(), "电压", "Bugreport 记录电压: " + voltage + " mV"));
            } catch (NumberFormatException ignored) {}
        }

        // Parse technology
        Matcher techMatcher = RE_TECH.matcher(section);
        if (techMatcher.find()) {
            String tech = techMatcher.group(1);
            result.batteryEvents.add(new BatteryEvent(
                    System.currentTimeMillis(), "电池技术", "Bugreport 记录技术: " + tech));
        }
    }

    private void parseDumpsysBatterystatsSection(String content, AnalysisResult result) {
        Matcher matcher = RE_STATS_SECTION.matcher(content);
        if (!matcher.find()) return;

        String section = matcher.group(1);
        if (section == null) return;

        // Parse charging sessions from batterystats
        Matcher chargeMatcher = RE_CHARGE_SESSION.matcher(section);
        while (chargeMatcher.find()) {
            try {
                int startLevel = Integer.parseInt(chargeMatcher.group(1));
                int endLevel = Integer.parseInt(chargeMatcher.group(2));
                float power = Float.parseFloat(chargeMatcher.group(3));

                result.chargeSessions.add(new ChargeSession(
                        System.currentTimeMillis(), System.currentTimeMillis(),
                        startLevel, endLevel, extractChargeTypeFromPower(power),
                        power, power
                ));
            } catch (NumberFormatException ignored) {}
        }

        // Parse wakelocks from batterystats
        Matcher wakelockMatcher = RE_WAKELOCK_STATS.matcher(section);
        Map<String, AppWakelock> wakelockMap = new HashMap<>();
        for (AppWakelock w : result.wakelocks) {
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

            AppWakelock existing = wakelockMap.get(pkg);
            if (existing != null) {
                existing.count++;
                existing.durationMs += duration > 0 ? duration : 60000;
            } else {
                AppWakelock newLock = new AppWakelock(
                        pkg, appName, duration > 0 ? duration : 60000, 1
                );
                result.wakelocks.add(newLock);
                wakelockMap.put(pkg, newLock);
            }
        }
    }

    private void parseBatteryEvent(String line, AnalysisResult result) {
        if (!line.contains("BatteryManager") && !line.contains("battery") && !line.contains("Battery")) return;

        String eventType = extractEventType(line);
        String detail = extractDetail(line);
        if (eventType != null && !eventType.isEmpty() && detail != null && !detail.isEmpty()) {
            result.batteryEvents.add(new BatteryEvent(
                    extractTimestamp(line), eventType, detail
            ));
        }
    }

    private void parseChargingSession(String line, AnalysisResult result) {
        if (!line.contains("charging") && !line.contains("Charging") && !line.contains("charge")) return;

        int level = extractLevel(line);
        float power = extractPower(line);
        String type = extractChargeType(line);

        if (level >= 0 && (line.contains("start") || line.contains("START"))) {
            result.chargeSessions.add(new ChargeSession(
                    extractTimestamp(line), System.currentTimeMillis(),
                    level, -1, type, power, power
            ));
        } else if (level >= 0 && !result.chargeSessions.isEmpty()) {
            ChargeSession lastSession = result.chargeSessions.get(result.chargeSessions.size() - 1);
            if (lastSession.endLevel == -1) {
                lastSession.endLevel = level;
                lastSession.endTime = extractTimestamp(line);
                if (power > lastSession.maxPower) {
                    lastSession.maxPower = power;
                }
            }
        }
    }

    private void parseAnomaly(String line, AnalysisResult result) {
        // ANR detection
        if (line.contains("ANR in")) {
            String pkg = extractPackageName(line);
            result.anomalies.add(new Anomaly(
                    extractTimestamp(line), "CRITICAL", "ANR",
                    "检测到应用无响应: " + pkg,
                    "建议检查 " + pkg + " 是否存在主线程阻塞问题"
            ));
        }

        // Battery temperature anomaly
        if (line.contains("temperature") && (line.contains("overheat") || line.contains("OVERHEAT")
                || RE_TEMP_HIGH.matcher(line).matches())) {
            result.anomalies.add(new Anomaly(
                    extractTimestamp(line), "HIGH", "电池过热",
                    "检测到电池温度过高: " + line.trim(),
                    "建议停止使用手机，让电池冷却后再使用"
            ));
        }

        // Low capacity anomaly — only when line explicitly mentions battery capacity/health percentage
        if ((line.contains("capacity") || line.contains("health")) && line.contains("%")) {
            Matcher pctMatcher = RE_PERCENT.matcher(line);
            while (pctMatcher.find()) {
                try {
                    int val = Integer.parseInt(pctMatcher.group(1));
                    if (val >= 0 && val < 60) {
                        String lowerLine = line.toLowerCase();
                        boolean isHealthOrCapacity = (lowerLine.contains("health") && !lowerLine.contains("charge level"))
                                || (lowerLine.contains("capacity") && !lowerLine.contains("charge level")
                                    && !lowerLine.contains("level") && !lowerLine.contains("version"));
                        if (isHealthOrCapacity) {
                            result.anomalies.add(new Anomaly(
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
            result.anomalies.add(new Anomaly(
                    extractTimestamp(line), "HIGH", "应用崩溃",
                    "检测到应用崩溃: " + line.trim(),
                    "建议检查应用版本是否为最新，或联系开发者"
            ));
        }
    }

    private void parseWakelock(String line, AnalysisResult result) {
        if (!line.contains("WakeLock") && !line.contains("wakelock") && !line.contains("wake_lock")) return;

        String packageName = extractPackageName(line);
        String appName = extractAppName(line, packageName);
        long duration = extractDuration(line);

        AppWakelock existing = null;
        for (AppWakelock w : result.wakelocks) {
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
            result.wakelocks.add(new AppWakelock(
                    packageName, appName, duration > 0 ? duration : 0, 1
            ));
        }
    }

    private void parseDeviceInfo(String line, AnalysisResult result) {
        if (result.deviceInfo != null) return;

        if (line.contains("Build fingerprint:")) {
            String fingerprint = line.substring(line.indexOf(":") + 1).trim();
            result.deviceInfo = new DeviceInfo(
                    Build.MODEL, Build.BRAND, Build.VERSION.RELEASE,
                    fingerprint, 0, 0, 0
            );
        }
    }

    private void parseThermalEvent(String line, AnalysisResult result) {
        if (line.contains("thermal") && (line.contains("throttle") || line.contains("THROTTLE"))) {
            result.batteryEvents.add(new BatteryEvent(
                    extractTimestamp(line), "温度节流", "检测到温度节流事件: " + line.trim()
            ));
        }

        if (RE_TEMP_HIGH.matcher(line).matches() || RE_TEMPERATURE_HIGH.matcher(line).matches()) {
            result.batteryEvents.add(new BatteryEvent(
                    extractTimestamp(line), "高温警告", "检测到设备高温: " + line.trim()
            ));
        }
    }

    private void parseMemoryPressure(String line, AnalysisResult result) {
        if (line.contains("low_memory") || line.contains("Low Memory Killer") ||
                (line.contains("oom") && line.contains("kill"))) {
            result.batteryEvents.add(new BatteryEvent(
                    extractTimestamp(line), "内存压力", "检测到低内存事件: " + line.trim()
            ));
        }
    }

    private void parseManufacturingDate(String line, AnalysisResult result) {
        if (line == null || line.trim().isEmpty()) return;

        for (int i = 0; i < RE_MFG_DATE.length; i++) {
            try {
                Matcher m = RE_MFG_DATE[i].matcher(line);
                if (m.find()) {
                    int year, month, day;
                    if (MFG_DATE_SEP[i].equals("-") && RE_MFG_DATE[i].pattern().contains("(\\d{2})[./](\\d{2})[./](\\d{4})")) {
                        // MM/DD/YYYY format
                        month = Integer.parseInt(m.group(1));
                        day = Integer.parseInt(m.group(2));
                        year = Integer.parseInt(m.group(3));
                    } else {
                        year = Integer.parseInt(m.group(1));
                        month = Integer.parseInt(m.group(2));
                        day = Integer.parseInt(m.group(3));
                    }

                    if (isValidDate(year, month, day)) {
                        String dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month, day);
                        result.batteryEvents.add(new BatteryEvent(
                                extractTimestamp(line), "制造日期", "Bugreport 记录制造日期: " + dateStr));
                        if (result.deviceInfo != null) {
                            result.deviceInfo.manufacturingDate = dateStr;
                        }
                        return;
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Manufacturing date pattern match error: " + e.getMessage());
            }
        }
    }

    private void parseManufacturingDateFromSection(String section, AnalysisResult result) {
        if (section == null || section.isEmpty()) return;

        String[] lines = section.split("\n");
        for (String line : lines) {
            parseManufacturingDate(line, result);
            if (result.deviceInfo != null && result.deviceInfo.manufacturingDate != null) {
                return;
            }
        }
    }

    private void parseSerialNumber(String line, AnalysisResult result) {
        if (line == null || line.trim().isEmpty()) return;

        // ro.serialno=
        Matcher serialnoMatcher = RE_SERIALNO.matcher(line);
        if (serialnoMatcher.find()) {
            String sn = serialnoMatcher.group(1);
            if (sn != null && !sn.isEmpty() && !"unknown".equalsIgnoreCase(sn) && !"0".equals(sn)) {
                result.batteryEvents.add(new BatteryEvent(
                        extractTimestamp(line), "设备序列号", "Bugreport 记录序列号: " + sn));
                if (result.deviceInfo != null && (result.deviceInfo.serialNumber == null || result.deviceInfo.serialNumber.isEmpty())) {
                    result.deviceInfo.serialNumber = sn;
                }
            }
            return;
        }

        // serial_number:
        Matcher serialMatcher = RE_SERIAL_NUM.matcher(line);
        if (serialMatcher.find()) {
            String sn = serialMatcher.group(1);
            if (sn != null && !sn.isEmpty() && !"unknown".equalsIgnoreCase(sn) && !"0".equals(sn)) {
                result.batteryEvents.add(new BatteryEvent(
                        extractTimestamp(line), "设备序列号", "Bugreport 记录序列号: " + sn));
                if (result.deviceInfo != null && (result.deviceInfo.serialNumber == null || result.deviceInfo.serialNumber.isEmpty())) {
                    result.deviceInfo.serialNumber = sn;
                }
            }
            return;
        }

        // IMEI:
        Matcher imeiMatcher = RE_IMEI.matcher(line);
        if (imeiMatcher.find()) {
            String imei = imeiMatcher.group(1);
            result.batteryEvents.add(new BatteryEvent(
                    extractTimestamp(line), "IMEI", "Bugreport 记录 IMEI: " + imei));
            if (result.deviceInfo != null && (result.deviceInfo.serialNumber == null || result.deviceInfo.serialNumber.isEmpty())) {
                result.deviceInfo.serialNumber = imei;
            }
        }
    }

    private void parseVoltageCurrent(String line, AnalysisResult result) {
        if (line == null || line.trim().isEmpty()) return;

        // battery voltage:
        Matcher voltageMatcher = RE_BATTERY_VOLTAGE.matcher(line);
        if (voltageMatcher.find()) {
            try {
                float voltage = Float.parseFloat(voltageMatcher.group(1));
                result.batteryEvents.add(new BatteryEvent(
                        extractTimestamp(line), "电压", "Bugreport 记录电压: " + voltage + " mV"));
            } catch (NumberFormatException ignored) {}
        }

        // battery current:
        Matcher currentMatcher = RE_BATTERY_CURRENT.matcher(line);
        if (currentMatcher.find()) {
            try {
                float current = Float.parseFloat(currentMatcher.group(1));
                result.batteryEvents.add(new BatteryEvent(
                        extractTimestamp(line), "电流", "Bugreport 记录电流: " + current + " mA"));
            } catch (NumberFormatException ignored) {}
        }
    }

    private void parseScreenOnTime(String line, AnalysisResult result) {
        if (line == null || line.trim().isEmpty()) return;

        Matcher screenMatcher = RE_SCREEN_ON.matcher(line);
        if (screenMatcher.find()) {
            try {
                float hours = Float.parseFloat(screenMatcher.group(1));
                result.batteryEvents.add(new BatteryEvent(
                        extractTimestamp(line), "屏幕使用时间", "Bugreport 记录屏幕使用时间: " + hours + " 小时"));
                if (result.deviceInfo != null) {
                    result.deviceInfo.screenOnTimeHours = (int) hours;
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    private void crossReferenceWithLiveData(AnalysisResult result) {
        try {
            batteryDataManager.refreshFromStickyIntent();
            BatteryInfo liveInfo = batteryDataManager.getCurrentBatteryInfo();
            if (liveInfo == null) return;

            // Compare live health with bugreport health
            float liveHealth = liveInfo.getHealthPercentage();
            if (liveHealth > 0) {
                result.batteryEvents.add(new BatteryEvent(
                        System.currentTimeMillis(), "实时健康度",
                        String.format(Locale.getDefault(), "当前设备实时健康度: %.1f%%（置信度: %.0f%%）",
                                liveHealth, liveInfo.getHealthConfidence() * 100)
                ));
            }

            // Compare live cycle count
            int liveCycles = liveInfo.getCycleCount();
            if (liveCycles > 0) {
                result.batteryEvents.add(new BatteryEvent(
                        System.currentTimeMillis(), "实时循环次数",
                        String.format(Locale.getDefault(), "当前设备实时循环次数: %d", liveCycles)
                ));
            }

            // Compare live design capacity
            int liveDesignCap = liveInfo.getDesignCapacity();
            if (liveDesignCap > 0) {
                result.batteryEvents.add(new BatteryEvent(
                        System.currentTimeMillis(), "实时设计容量",
                        String.format(Locale.getDefault(), "当前设备实时设计容量: %d mAh", liveDesignCap)
                ));
            }

            // Compare live current capacity
            int liveCurrentCap = liveInfo.getCurrentCapacity();
            if (liveCurrentCap > 0) {
                result.batteryEvents.add(new BatteryEvent(
                        System.currentTimeMillis(), "实时满充容量",
                        String.format(Locale.getDefault(), "当前设备实时满充容量: %d mAh", liveCurrentCap)
                ));
            }

            // Battery source verification
            String source = liveInfo.getBatterySource();
            if ("third_party".equals(source)) {
                result.anomalies.add(new Anomaly(
                        System.currentTimeMillis(), "MEDIUM", "电池来源",
                        "实时检测发现电池可能非原装",
                        "建议前往官方售后验证电池来源"
                ));
            }

            // Update device info with live data
            if (result.deviceInfo == null) {
                result.deviceInfo = new DeviceInfo(
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

            // Set DeviceInfo fields from live data
            if (result.deviceInfo != null) {
                if (result.deviceInfo.serialNumber == null || result.deviceInfo.serialNumber.isEmpty()) {
                    String liveSerial = liveInfo.getBatterySerial();
                    if (liveSerial != null && !liveSerial.isEmpty()
                            && !"unknown".equalsIgnoreCase(liveSerial) && !"0".equals(liveSerial)) {
                        result.deviceInfo.serialNumber = liveSerial;
                    }
                }
                if (result.deviceInfo.designCapacityMah <= 0 && liveDesignCap > 0) {
                    result.deviceInfo.designCapacityMah = liveDesignCap;
                }
                if (result.deviceInfo.currentCapacityMah <= 0 && liveCurrentCap > 0) {
                    result.deviceInfo.currentCapacityMah = liveCurrentCap;
                }
                if (result.deviceInfo.temperatureCelsius <= 0 && liveInfo.getTemperature() > 0) {
                    result.deviceInfo.temperatureCelsius = liveInfo.getTemperature();
                }
            }

        } catch (Exception e) {
            Log.d(TAG, "Cross-reference with live data failed: " + e.getMessage());
        }
    }

    private void analyzeBatteryEvents(AnalysisResult result) {
        int chargingCount = 0;
        int dischargingCount = 0;
        int temperatureWarnings = 0;
        int thermalThrottles = 0;
        int memoryPressureEvents = 0;

        for (BatteryEvent event : result.batteryEvents) {
            String type = event.type != null ? event.type.toLowerCase() : "";
            if (type.contains("充电") || type.contains("charging")) {
                chargingCount++;
            } else if (type.contains("放电") || type.contains("discharging")) {
                dischargingCount++;
            } else if (type.contains("温度") || type.contains("temperature") || type.contains("高温")) {
                temperatureWarnings++;
            } else if (type.contains("节流") || type.contains("throttle")) {
                thermalThrottles++;
            } else if (type.contains("内存") || type.contains("memory")) {
                memoryPressureEvents++;
            }
        }

        if (temperatureWarnings > 5) {
            result.anomalies.add(new Anomaly(
                    System.currentTimeMillis(), "HIGH", "温度异常",
                    "检测到多次温度警告，共 " + temperatureWarnings + " 次",
                    "建议检查手机散热情况，避免长时间高负载使用"
            ));
        }

        if (thermalThrottles > 3) {
            result.anomalies.add(new Anomaly(
                    System.currentTimeMillis(), "MEDIUM", "频繁温度节流",
                    "检测到 " + thermalThrottles + " 次温度节流事件，设备可能散热不佳",
                    "建议避免在高温环境下长时间使用，检查保护壳是否影响散热"
            ));
        }

        if (dischargingCount > chargingCount * 2) {
            result.anomalies.add(new Anomaly(
                    System.currentTimeMillis(), "MEDIUM", "耗电过快",
                    "放电次数远大于充电次数，可能存在耗电异常",
                    "建议检查后台应用和定位服务"
            ));
        }

        if (memoryPressureEvents > 5) {
            result.anomalies.add(new Anomaly(
                    System.currentTimeMillis(), "MEDIUM", "内存不足",
                    "检测到 " + memoryPressureEvents + " 次低内存事件",
                    "建议关闭不必要的后台应用，或考虑清理存储空间"
            ));
        }
    }

    private void generateSummary(AnalysisResult result) {
        int totalChargeSessions = result.chargeSessions.size();
        long totalChargeDurationMs = 0;
        float totalPower = 0;
        int anomalyCount = result.anomalies.size();
        int criticalAnomalyCount = 0;

        for (ChargeSession session : result.chargeSessions) {
            totalChargeDurationMs += (session.endTime - session.startTime);
            totalPower += session.avgPower;
        }

        float avgChargePower = totalChargeSessions > 0 ? totalPower / totalChargeSessions : 0;

        for (Anomaly anomaly : result.anomalies) {
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

        result.summary = new Summary(
                totalChargeSessions, totalChargeDurationMs, avgChargePower,
                anomalyCount, criticalAnomalyCount, overallHealth
        );

        // Track extracted and missing fields
        List<String> extractedFields = new ArrayList<>();
        List<String> missingFields = new ArrayList<>();

        if (result.deviceInfo != null) {
            if (result.deviceInfo.model != null && !result.deviceInfo.model.isEmpty()) extractedFields.add("设备型号");
            else missingFields.add("设备型号");

            if (result.deviceInfo.brand != null && !result.deviceInfo.brand.isEmpty()) extractedFields.add("设备品牌");
            else missingFields.add("设备品牌");

            if (result.deviceInfo.androidVersion != null && !result.deviceInfo.androidVersion.isEmpty()) extractedFields.add("Android 版本");
            else missingFields.add("Android 版本");

            if (result.deviceInfo.buildNumber != null && !result.deviceInfo.buildNumber.isEmpty()) extractedFields.add("Build 编号");
            else missingFields.add("Build 编号");

            if (result.deviceInfo.batteryCapacity > 0) extractedFields.add("电池容量");
            else missingFields.add("电池容量");

            if (result.deviceInfo.cycleCount > 0) extractedFields.add("循环次数");
            else missingFields.add("循环次数");

            if (result.deviceInfo.healthPercentage > 0) extractedFields.add("健康度");
            else missingFields.add("健康度");

            if (result.deviceInfo.serialNumber != null && !result.deviceInfo.serialNumber.isEmpty()) extractedFields.add("序列号");
            else missingFields.add("序列号");

            if (result.deviceInfo.manufacturingDate != null && !result.deviceInfo.manufacturingDate.isEmpty()) extractedFields.add("制造日期");
            else missingFields.add("制造日期");

            if (result.deviceInfo.designCapacityMah > 0) extractedFields.add("设计容量");
            else missingFields.add("设计容量");

            if (result.deviceInfo.currentCapacityMah > 0) extractedFields.add("满充容量");
            else missingFields.add("满充容量");

            if (result.deviceInfo.temperatureCelsius > 0) extractedFields.add("温度");
            else missingFields.add("温度");

            if (result.deviceInfo.screenOnTimeHours > 0) extractedFields.add("屏幕使用时间");
            else missingFields.add("屏幕使用时间");
        } else {
            missingFields.add("设备型号");
            missingFields.add("设备品牌");
            missingFields.add("Android 版本");
            missingFields.add("Build 编号");
            missingFields.add("电池容量");
            missingFields.add("循环次数");
            missingFields.add("健康度");
            missingFields.add("序列号");
            missingFields.add("制造日期");
            missingFields.add("设计容量");
            missingFields.add("满充容量");
            missingFields.add("温度");
            missingFields.add("屏幕使用时间");
        }

        result.summary.extractedFieldCount = extractedFields.size();
        result.summary.missingFieldCount = missingFields.size();
        result.summary.extractedFields = extractedFields;
        result.summary.missingFields = missingFields;

        // Build parse detail string
        StringBuilder parseDetail = new StringBuilder();
        parseDetail.append("已提取 ").append(extractedFields.size()).append(" 个字段");
        if (!extractedFields.isEmpty()) {
            parseDetail.append("（").append(String.join("、", extractedFields)).append("）");
        }
        if (!missingFields.isEmpty()) {
            parseDetail.append("，缺少 ").append(missingFields.size()).append(" 个字段");
            parseDetail.append("（").append(String.join("、", missingFields)).append("）");
        }
        result.parseDetail = parseDetail.toString();

        // Sort wakelocks by durationMs descending, limit to top 10
        Collections.sort(result.wakelocks, new Comparator<AppWakelock>() {
            @Override
            public int compare(AppWakelock o1, AppWakelock o2) {
                return Long.compare(o2.durationMs, o1.durationMs);
            }
        });
        if (result.wakelocks.size() > 10) {
            result.wakelocks = new ArrayList<>(result.wakelocks.subList(0, 10));
        }
    }

    private String calculateOverallHealth(float avgPower, int anomalyCount, int criticalCount, float liveHealth) {
        float score = 100;

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
        // Try yyyy-MM-dd HH:mm:ss
        Matcher matcher = RE_TIMESTAMP.matcher(line);
        if (matcher.find()) {
            try {
                Date date = timestampSdf.parse(matcher.group(1).replace("T", " "));
                if (date != null) return date.getTime();
            } catch (Exception ignored) {}
        }

        // Try epoch timestamp
        Matcher epochMatcher = RE_EPOCH.matcher(line);
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
        // Try to extract bracket content
        int start = line.indexOf("[");
        int end = line.indexOf("]");
        if (start >= 0 && end > start) {
            return line.substring(start + 1, end);
        }
        // Try to extract from key=value patterns
        Matcher m = RE_KV.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(m.group(1));
        }
        if (sb.length() > 0) return sb.toString();

        return line.length() > 80 ? line.substring(0, 80) + "..." : line.trim();
    }

    private int extractLevel(String line) {
        Matcher m = RE_LEVEL.matcher(line);
        if (m.find()) {
            try {
                int value = Integer.parseInt(m.group(1));
                if (value >= 0 && value <= 100) return value;
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    private float extractPower(String line) {
        Matcher m = RE_POWER.matcher(line);
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
        if (lower.contains("超级快充") || lower.contains("super") || lower.contains("ultra")) return "超级快充";
        if (lower.contains("快充") || lower.contains("fast") || lower.contains("quick")) return "快充";
        if (lower.contains("无线") || lower.contains("wireless") || lower.contains("qi")) return "无线充电";
        if (lower.contains("usb")) return "USB充电";
        if (lower.contains("ac") || lower.contains("交流")) return "交流充电";
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
        Matcher m = RE_DURATION.matcher(line);
        if (m.find()) {
            try {
                long value = Long.parseLong(m.group(1));
                String unit = m.group(2);
                if (unit != null) {
                    switch (unit.toLowerCase()) {
                        case "s": return value * 1000;
                        case "m": return value * 60 * 1000;
                        default: return value;
                    }
                }
                return value > 100000 ? value : value * 1000;
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private String extractPackageName(String line) {
        int start = line.indexOf("package=");
        if (start >= 0) {
            int end = line.indexOf(" ", start + 8);
            if (end > start) return line.substring(start + 8, end);
            return line.substring(start + 8);
        }

        Matcher m = RE_PKG.matcher(line);
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
        if (packageName.contains("xiaomi") || packageName.contains("miui")) return "小米应用";
        if (packageName.contains("oppo") || packageName.contains("coloros")) return "OPPO应用";
        if (packageName.contains("vivo") || packageName.contains("bbk")) return "vivo应用";
        if (packageName.contains("huawei") || packageName.contains("emui") || packageName.contains("harmonyos")) return "华为应用";
        if (packageName.contains("samsung") || packageName.contains("sec")) return "三星应用";
        if (packageName.contains("honor") || packageName.contains("magicos")) return "荣耀应用";
        if (packageName.contains("meizu") || packageName.contains("flyme")) return "魅族应用";
        if (packageName.contains("oneplus")) return "一加应用";
        if (packageName.contains("lenovo") || packageName.contains("zuk")) return "联想应用";
        if (packageName.contains("zte") || packageName.contains("nubia")) return "中兴应用";
        if (packageName.contains("realme")) return "realme应用";

        int lastDot = packageName.lastIndexOf(".");
        if (lastDot >= 0 && lastDot < packageName.length() - 1) {
            String name = packageName.substring(lastDot + 1);
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }

        return packageName;
    }

    private boolean isValidDate(int year, int month, int day) {
        if (year < 2000 || year > 2030) return false;
        if (month < 1 || month > 12) return false;
        if (day < 1 || day > 31) return false;

        // More precise day validation per month
        int maxDay;
        switch (month) {
            case 2:
                boolean isLeapYear = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
                maxDay = isLeapYear ? 29 : 28;
                break;
            case 4: case 6: case 9: case 11:
                maxDay = 30;
                break;
            default:
                maxDay = 31;
        }
        return day <= maxDay;
    }

    // endregion
}
