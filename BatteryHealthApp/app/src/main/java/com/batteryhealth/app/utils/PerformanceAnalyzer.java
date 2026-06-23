package com.batteryhealth.app.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PerformanceAnalyzer {

    private static final String TAG = "PerformanceAnalyzer";

    private final Context context;

    public PerformanceAnalyzer(Context context) {
        this.context = context;
    }

    public AnrAnalysisResult analyzeAnrLogs() {
        AnrAnalysisResult result = new AnrAnalysisResult();
        List<AnrRecord> records = new ArrayList<>();

        File anrDir = new File(Environment.getDataDirectory(), "system/anr");
        if (!anrDir.exists() || !anrDir.isDirectory()) {
            result.hasAnr = false;
            result.message = "未检测到ANR日志";
            return result;
        }

        File[] anrFiles = anrDir.listFiles((dir, name) -> name.startsWith("traces"));
        if (anrFiles == null || anrFiles.length == 0) {
            result.hasAnr = false;
            result.message = "未检测到ANR日志";
            return result;
        }

        int totalAnrs = 0;
        int ourAppAnrs = 0;

        for (File file : anrFiles) {
            try {
                AnrRecord record = parseAnrFile(file);
                if (record != null) {
                    records.add(record);
                    totalAnrs++;
                    if (record.isOurApp) {
                        ourAppAnrs++;
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Error reading ANR file: " + file.getName(), e);
            }
        }

        result.hasAnr = totalAnrs > 0;
        result.totalAnrs = totalAnrs;
        result.ourAppAnrs = ourAppAnrs;
        result.anrRecords = records;

        if (ourAppAnrs > 0) {
            result.message = "检测到 " + ourAppAnrs + " 次本应用ANR，" + (totalAnrs - ourAppAnrs) + " 次其他应用ANR";
            result.severity = ourAppAnrs > 5 ? "严重" : (ourAppAnrs > 2 ? "中等" : "轻微");
        } else {
            result.message = "本应用无ANR记录，系统共 " + totalAnrs + " 次ANR";
            result.severity = "正常";
        }

        return result;
    }

    private AnrRecord parseAnrFile(File file) throws IOException {
        AnrRecord record = new AnrRecord();
        record.fileName = file.getName();
        record.timestamp = file.lastModified();

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        String fullContent = content.toString();

        Matcher reasonMatcher = Pattern.compile("ANR in (\\S+)").matcher(fullContent);
        if (reasonMatcher.find()) {
            record.packageName = reasonMatcher.group(1);
            record.isOurApp = record.packageName != null && 
                record.packageName.contains("batteryhealth");
        }

        Matcher timeMatcher = Pattern.compile("Build fingerprint:.*\\n").matcher(fullContent);
        if (timeMatcher.find()) {
            int idx = fullContent.indexOf("ANR in");
            if (idx > 0) {
                String header = fullContent.substring(0, idx);
                Matcher dateMatcher = Pattern.compile("(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})").matcher(header);
                if (dateMatcher.find()) {
                    record.date = dateMatcher.group(1);
                }
            }
        }

        if (record.isOurApp) {
            Matcher cpuMatcher = Pattern.compile("CPU usage from (\\d+)ms to (\\d+)ms ago:\\s+([\\s\\S]*?)(?:\\n\\n|\\z)").matcher(fullContent);
            if (cpuMatcher.find()) {
                record.cpuUsage = cpuMatcher.group(3).trim();
            }

            Matcher stackMatcher = Pattern.compile("(\"main\".*?)(?:^\"|\\z)", Pattern.MULTILINE | Pattern.DOTALL).matcher(fullContent);
            if (stackMatcher.find()) {
                record.stackTrace = stackMatcher.group(1).trim();
            }
        }

        return record;
    }

    public AppStartupAnalysis analyzeAppStartup() {
        AppStartupAnalysis result = new AppStartupAnalysis();

        File tracesDir = new File(Environment.getDataDirectory(), "system/anr");
        File[] files = tracesDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().contains("traces")) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("batteryhealth") && line.contains("ANR")) {
                                result.anrCount++;
                            }
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        result.packageName = context.getPackageName();
        result.suggestion = generateStartupSuggestion(result);

        return result;
    }

    private String generateStartupSuggestion(AppStartupAnalysis analysis) {
        if (analysis.anrCount == 0) {
            return "应用启动正常，未检测到ANR问题";
        } else if (analysis.anrCount <= 3) {
            return "检测到少量ANR，建议优化主界面初始化逻辑，减少主线程阻塞操作";
        } else {
            return "检测到多次ANR，建议：\n1. 检查主线程是否有耗时操作\n2. 将网络请求、数据库查询移至子线程\n3. 使用异步加载优化UI渲染";
        }
    }

    public PerformanceInsights getPerformanceInsights() {
        PerformanceInsights insights = new PerformanceInsights();

        AnrAnalysisResult anrResult = analyzeAnrLogs();
        insights.anrCount = anrResult.ourAppAnrs;
        insights.anrSeverity = anrResult.severity;

        insights.suggestions = generatePerformanceSuggestions(insights);

        return insights;
    }

    private List<String> generatePerformanceSuggestions(PerformanceInsights insights) {
        List<String> suggestions = new ArrayList<>();

        if (insights.anrCount > 0) {
            suggestions.add("✓ 检测到ANR问题，建议优化主线程性能");
        }

        suggestions.add("✓ 避免在主线程执行耗时操作（网络请求、数据库查询等）");
        suggestions.add("✓ 使用RecyclerView优化列表渲染性能");
        suggestions.add("✓ 及时释放Bitmap和其他资源，避免内存泄漏");
        suggestions.add("✓ 考虑使用WorkManager处理后台任务");

        return suggestions;
    }

    /**
     * 读取当前系统 CPU 使用率（百分比，0-100）。
     * 通过 /proc/stat 差值计算，首次调用返回 0。
     */
    public int getCpuUsage() {
        try {
            try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
                String line = reader.readLine();
                if (line != null && line.startsWith("cpu ")) {
                    String[] parts = line.split("\\s+");
                    long user = Long.parseLong(parts[1]);
                    long nice = Long.parseLong(parts[2]);
                    long system = Long.parseLong(parts[3]);
                    long idle = Long.parseLong(parts[4]);
                    long iowait = parts.length > 5 ? Long.parseLong(parts[5]) : 0;
                    long irq = parts.length > 6 ? Long.parseLong(parts[6]) : 0;
                    long softirq = parts.length > 7 ? Long.parseLong(parts[7]) : 0;
                    long total = user + nice + system + idle + iowait + irq + softirq;

                    if (lastSysTotal > 0) {
                        long deltaTotal = total - lastSysTotal;
                        long deltaIdle = idle - lastSysIdle;
                        lastSysIdle = idle;
                        lastSysTotal = total;
                        if (deltaTotal > 0) {
                            return (int) ((deltaTotal - deltaIdle) * 100 / deltaTotal);
                        }
                    }
                    lastSysIdle = idle;
                    lastSysTotal = total;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to read CPU usage: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 读取当前系统内存使用率（百分比，0-100）。
     * 通过 {@link ActivityManager.MemoryInfo} 获取。
     */
    public int getMemoryUsage() {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                if (mi.totalMem > 0) {
                    return (int) ((mi.totalMem - mi.availMem) * 100 / mi.totalMem);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to read memory usage: " + e.getMessage());
        }
        return 0;
    }

    // 缓存上一次 CPU 统计值用于差值计算
    private long lastSysTotal = 0;
    private long lastSysIdle = 0;

    public static class AnrAnalysisResult {
        public boolean hasAnr;
        public int totalAnrs;
        public int ourAppAnrs;
        public String severity;
        public String message;
        public List<AnrRecord> anrRecords;
    }

    public static class AnrRecord {
        public String fileName;
        public long timestamp;
        public String date;
        public String packageName;
        public boolean isOurApp;
        public String cpuUsage;
        public String stackTrace;
    }

    public static class AppStartupAnalysis {
        public String packageName;
        public int anrCount;
        public String suggestion;
    }

    public static class PerformanceInsights {
        public int anrCount;
        public String anrSeverity;
        public List<String> suggestions;
    }
}