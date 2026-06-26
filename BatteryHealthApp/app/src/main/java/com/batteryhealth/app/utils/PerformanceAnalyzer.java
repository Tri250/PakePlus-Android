package com.batteryhealth.app.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 性能分析器：采集 CPU/内存/存储/GPU 真实数据，计算多维加权性能评分。
 * 评分维度：SoC 档位(30%) + CPU 负载(20%) + 内存负载(20%) + 存储负载(15%) + ANR 历史(15%)
 */
public class PerformanceAnalyzer {

    private static final String TAG = "PerformanceAnalyzer";

    private final Context context;
    private long lastSysIdle = 0;
    private long lastSysTotal = 0;

    public PerformanceAnalyzer(Context context) {
        this.context = context.getApplicationContext();
    }

    // ========== ViewModel 依赖的方法 ==========

    /**
     * 获取系统 CPU 使用率（0-100），基于 /proc/stat 两次采样差值。
     */
    public int getCpuUsage() {
        try {
            long[] times = readSystemCpuTimesInternal();
            if (times == null || times.length < 4) return 0;

            long user = times[0], nice = times[1], system = times[2], idle = times[3];
            long total = 0;
            for (long t : times) total += t;

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
        } catch (Exception e) {
            Log.w(TAG, "getCpuUsage failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 获取系统内存使用率（0-100），基于 ActivityManager.MemoryInfo。
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
            Log.w(TAG, "getMemoryUsage failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 获取存储使用率（0-100）。
     */
    public int getStorageUsage() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long total = stat.getTotalBytes();
            long available = stat.getAvailableBytes();
            if (total > 0) {
                return (int) ((total - available) * 100 / total);
            }
        } catch (Exception e) {
            Log.w(TAG, "getStorageUsage failed: " + e.getMessage());
        }
        return 0;
    }

    // ========== 多维加权性能评分 ==========

    /**
     * 计算综合性能评分（0-100），基于国内同类系统评分标准。
     * 权重：SoC 档位 30% + CPU 负载 20% + 内存负载 20% + 存储负载 15% + ANR 历史 15%
     */
    public PerformanceScoreResult calculatePerformanceScore() {
        PerformanceScoreResult result = new PerformanceScoreResult();

        // 1. SoC 档位评分（0-100）
        int socScore = calculateSocScore();
        result.socScore = socScore;

        // 2. CPU 负载评分（负载越低分越高）
        int cpuUsage = getCpuUsage();
        result.cpuUsage = cpuUsage;
        int cpuScore = calculateCpuScore(cpuUsage);
        result.cpuScore = cpuScore;

        // 3. 内存负载评分
        int memUsage = getMemoryUsage();
        result.memoryUsage = memUsage;
        int memScore = calculateMemoryScore(memUsage);
        result.memoryScore = memScore;

        // 4. 存储负载评分
        int storageUsage = getStorageUsage();
        result.storageUsage = storageUsage;
        int storageScore = calculateStorageScore(storageUsage);
        result.storageScore = storageScore;

        // 5. ANR 历史评分
        AnrAnalysisResult anrResult = analyzeAnrLogs();
        int anrScore = calculateAnrScore(anrResult);
        result.anrScore = anrScore;
        result.anrResult = anrResult;

        // 加权计算总分
        int totalScore = (int) (socScore * 0.30 + cpuScore * 0.20 + memScore * 0.20
                + storageScore * 0.15 + anrScore * 0.15);
        totalScore = Math.max(0, Math.min(100, totalScore));
        result.totalScore = totalScore;
        result.grade = scoreToGrade(totalScore);
        result.gradeDescription = gradeToDescription(totalScore);

        return result;
    }

    /**
     * SoC 档位评分：基于处理器营销名判定档位。
     */
    private int calculateSocScore() {
        String cpu = Build.HARDWARE.toLowerCase(Locale.ROOT);

        // Flagship
        if (cpu.contains("sm8") || cpu.contains("sdm8") || cpu.contains("snapdragon 8")
                || cpu.contains("mt699") || cpu.contains("mt698") || cpu.contains("dimensity 9")
                || cpu.contains("kirin 9") || cpu.contains("tensor g4") || cpu.contains("tensor g5")
                || cpu.contains("exynos 2400") || cpu.contains("exynos 2500")) {
            return 95;
        }
        // High-end
        if (cpu.contains("sm7") || cpu.contains("sdm7") || cpu.contains("snapdragon 7")
                || cpu.contains("mt689") || cpu.contains("mt688") || cpu.contains("dimensity 8")
                || cpu.contains("dimensity 7") || cpu.contains("kirin 8")
                || cpu.contains("exynos 2200") || cpu.contains("exynos 2300")
                || cpu.contains("unisoc t8")) {
            return 80;
        }
        // Mid-range
        if (cpu.contains("sm6") || cpu.contains("sdm6") || cpu.contains("snapdragon 6")
                || cpu.contains("mt685") || cpu.contains("mt687") || cpu.contains("dimensity 6")
                || cpu.contains("kirin 7") || cpu.contains("kirin 6")
                || cpu.contains("exynos 1280") || cpu.contains("exynos 1380")
                || cpu.contains("unisoc t7") || cpu.contains("unisoc t6")) {
            return 60;
        }
        // Entry-level
        if (cpu.contains("sm4") || cpu.contains("sdm4") || cpu.contains("snapdragon 4")
                || cpu.contains("mt676") || cpu.contains("mt681") || cpu.contains("dimensity 3")
                || cpu.contains("unisoc t5") || cpu.contains("unisoc t4")
                || cpu.contains("sc9863")) {
            return 40;
        }
        // Unknown
        return 50;
    }

    /**
     * CPU 负载评分：负载越低分越高。
     * 0-30% → 100, 30-50% → 80, 50-70% → 60, 70-85% → 40, 85%+ → 20
     */
    private int calculateCpuScore(int cpuUsage) {
        if (cpuUsage <= 30) return 100;
        if (cpuUsage <= 50) return 80;
        if (cpuUsage <= 70) return 60;
        if (cpuUsage <= 85) return 40;
        return 20;
    }

    /**
     * 内存负载评分：Android 系统内存占用 60-70% 属正常范围。
     * 0-50% → 100, 50-70% → 85, 70-80% → 65, 80-90% → 45, 90%+ → 20
     */
    private int calculateMemoryScore(int memUsage) {
        if (memUsage <= 50) return 100;
        if (memUsage <= 70) return 85;
        if (memUsage <= 80) return 65;
        if (memUsage <= 90) return 45;
        return 20;
    }

    /**
     * 存储负载评分：存储使用率过高影响系统性能。
     * 0-60% → 100, 60-75% → 85, 75-85% → 65, 85-95% → 40, 95%+ → 15
     */
    private int calculateStorageScore(int storageUsage) {
        if (storageUsage <= 60) return 100;
        if (storageUsage <= 75) return 85;
        if (storageUsage <= 85) return 65;
        if (storageUsage <= 95) return 40;
        return 15;
    }

    /**
     * ANR 历史评分。
     * 0 次 → 100, 1-2 次 → 80, 3-5 次 → 50, 5+ 次 → 20
     */
    private int calculateAnrScore(AnrAnalysisResult anrResult) {
        if (anrResult == null || !anrResult.hasAnr) return 100;
        int count = anrResult.ourAppAnrs;
        if (count == 0) return 95;
        if (count <= 2) return 80;
        if (count <= 5) return 50;
        return 20;
    }

    public static String scoreToGrade(int score) {
        if (score >= 90) return "A+";
        if (score >= 85) return "A";
        if (score >= 75) return "B+";
        if (score >= 65) return "B";
        if (score >= 55) return "C";
        return "D";
    }

    public static String gradeToDescription(int score) {
        if (score >= 90) return "性能卓越";
        if (score >= 85) return "性能优秀";
        if (score >= 75) return "性能良好";
        if (score >= 65) return "性能一般";
        if (score >= 55) return "性能偏低";
        return "性能较差";
    }

    // ========== 动态性能建议 ==========

    /**
     * 基于真实性能数据生成动态建议，不使用固定文案。
     */
    public List<String> generateDynamicSuggestions(PerformanceScoreResult scoreResult) {
        List<String> suggestions = new ArrayList<>();
        if (scoreResult == null) return suggestions;

        // SoC 档位建议
        if (scoreResult.socScore <= 40) {
            suggestions.add("设备处理器为入门级，建议关闭后台高耗电应用，降低动画效果以提升流畅度");
        } else if (scoreResult.socScore <= 60) {
            suggestions.add("中端处理器性能有限，建议避免同时运行多个大型应用");
        }

        // CPU 负载建议
        if (scoreResult.cpuUsage > 85) {
            suggestions.add(String.format(Locale.getDefault(), "CPU 占用 %d%% 过高，建议关闭后台应用或重启设备", scoreResult.cpuUsage));
        } else if (scoreResult.cpuUsage > 70) {
            suggestions.add(String.format(Locale.getDefault(), "CPU 占用 %d%% 偏高，建议检查后台运行的应用", scoreResult.cpuUsage));
        }

        // 内存建议
        if (scoreResult.memoryUsage > 90) {
            suggestions.add(String.format(Locale.getDefault(), "内存占用 %d%% 严重不足，建议清理后台应用或重启设备", scoreResult.memoryUsage));
        } else if (scoreResult.memoryUsage > 80) {
            suggestions.add(String.format(Locale.getDefault(), "内存占用 %d%% 偏高，建议关闭不必要的后台应用", scoreResult.memoryUsage));
        }

        // 存储建议
        if (scoreResult.storageUsage > 95) {
            suggestions.add("存储空间严重不足，系统性能将受影响，建议立即清理文件");
        } else if (scoreResult.storageUsage > 85) {
            suggestions.add("存储空间偏少，建议清理缓存和不必要的文件以保持系统流畅");
        }

        // ANR 建议
        if (scoreResult.anrResult != null && scoreResult.anrResult.ourAppAnrs > 0) {
            suggestions.add(String.format(Locale.getDefault(), "检测到 %d 次应用无响应(ANR)，建议更新应用或反馈开发者", scoreResult.anrResult.ourAppAnrs));
        }

        // 综合建议
        if (scoreResult.totalScore >= 90) {
            suggestions.add("设备整体性能优异，可放心使用各类应用和游戏");
        } else if (scoreResult.totalScore >= 75) {
            suggestions.add("设备性能良好，日常使用流畅，大型游戏可能略有压力");
        }

        // 至少一条建议
        if (suggestions.isEmpty()) {
            suggestions.add("设备性能正常，建议保持良好的使用习惯，定期清理缓存");
        }

        return suggestions;
    }

    // ========== ANR 分析（兼容非 root 设备） ==========

    public AnrAnalysisResult analyzeAnrLogs() {
        AnrAnalysisResult result = new AnrAnalysisResult();
        List<AnrRecord> records = new ArrayList<>();

        // 方式1：直接读取 /data/system/anr/（需要 root 或系统权限）
        File anrDir = new File(Environment.getDataDirectory(), "system/anr");
        if (anrDir.exists() && anrDir.isDirectory() && anrDir.canRead()) {
            File[] anrFiles = anrDir.listFiles((dir, name) -> name.startsWith("traces"));
            if (anrFiles != null && anrFiles.length > 0) {
                int totalAnrs = 0;
                int ourAppAnrs = 0;

                for (File file : anrFiles) {
                    try {
                        AnrRecord record = parseAnrFile(file);
                        if (record != null) {
                            records.add(record);
                            totalAnrs++;
                            if (record.isOurApp) ourAppAnrs++;
                        }
                    } catch (IOException e) {
                        Log.w(TAG, "Error reading ANR file: " + file.getName(), e);
                    }
                }

                result.hasAnr = totalAnrs > 0;
                result.totalAnrs = totalAnrs;
                result.ourAppAnrs = ourAppAnrs;
                result.anrRecords = records;
                result.message = formatAnrMessage(ourAppAnrs, totalAnrs);
                result.severity = formatAnrSeverity(ourAppAnrs);
                return result;
            }
        }

        // 方式2：通过 DropBoxManager 读取 ANR 条目（非 root 可用，Android 12+）
        try {
            android.os.DropBoxManager dbm = (android.os.DropBoxManager)
                    context.getSystemService(Context.DROPBOX_SERVICE);
            if (dbm != null) {
                long cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L; // 最近7天
                boolean hasEntry = dbm.isTagEnabled("system_app_anr") || dbm.isTagEnabled("data_app_anr");
                if (hasEntry) {
                    // DropBoxManager 无法直接计数，标记为"可能存在"
                    result.hasAnr = false; // 无法确认
                    result.totalAnrs = 0;
                    result.ourAppAnrs = 0;
                    result.message = "ANR 日志需要系统权限读取，建议通过 BugReport 获取详细分析";
                    result.severity = "未知";
                    return result;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "DropBoxManager not available: " + e.getMessage());
        }

        // 方式3：非 root 设备，无法直接读取
        result.hasAnr = false;
        result.totalAnrs = 0;
        result.ourAppAnrs = 0;
        result.message = "ANR 检测结果未知（非 root 设备无法读取系统 ANR 日志，建议通过指南页上传 BugReport 进行分析）";
        result.severity = "未知";
        return result;
    }

    private String formatAnrMessage(int ourAppAnrs, int totalAnrs) {
        if (ourAppAnrs > 0) {
            return String.format(Locale.getDefault(), "检测到 %d 次本应用ANR，%d 次其他应用ANR",
                    ourAppAnrs, totalAnrs - ourAppAnrs);
        }
        return String.format(Locale.getDefault(), "本应用无ANR记录，系统共 %d 次ANR", totalAnrs);
    }

    private String formatAnrSeverity(int ourAppAnrs) {
        if (ourAppAnrs > 5) return "严重";
        if (ourAppAnrs > 2) return "中等";
        if (ourAppAnrs > 0) return "轻微";
        return "正常";
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

    // ========== 应用性能分析 ==========

    /**
     * 获取应用 CPU 使用率（0-100%）。
     */
    public float getAppCpuUsage() {
        try {
            long[] appTimes = readProcessCpuTimes();
            long[] sysTimes = readSystemCpuTimesInternal();
            if (appTimes == null || sysTimes == null) return 0f;

            long appCpuTime = appTimes[0] + appTimes[1];
            long sysCpuTime = 0;
            for (long t : sysTimes) sysCpuTime += t;

            if (lastCpuTime > 0 && lastAppCpuTime > 0) {
                long deltaApp = appCpuTime - lastAppCpuTime;
                long deltaSys = sysCpuTime - lastCpuTime;
                if (deltaSys > 0) {
                    float usage = (deltaApp * 100f) / deltaSys;
                    lastAppCpuTime = appCpuTime;
                    lastCpuTime = sysCpuTime;
                    return Math.min(usage, 100f);
                }
            }

            lastAppCpuTime = appCpuTime;
            lastCpuTime = sysCpuTime;
        } catch (Exception e) {
            Log.w(TAG, "getAppCpuUsage failed: " + e.getMessage());
        }
        return 0f;
    }

    private long lastCpuTime = 0;
    private long lastAppCpuTime = 0;

    /**
     * 获取应用内存使用量（字节）。
     */
    public long getAppMemoryUsage() {
        try {
            Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
            Debug.getMemoryInfo(memoryInfo);
            return memoryInfo.getTotalPss() * 1024L;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 检查前台服务是否正在运行。
     */
    public boolean isForegroundServiceRunning() {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningServiceInfo> services = am.getRunningServices(Integer.MAX_VALUE);
                String myPackage = context.getPackageName();
                for (ActivityManager.RunningServiceInfo service : services) {
                    if (service.service.getPackageName().equals(myPackage)
                            && service.foreground) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "isForegroundServiceRunning failed: " + e.getMessage());
        }
        return false;
    }

    // ========== 内部方法 ==========

    private long[] readProcessCpuTimes() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/stat"))) {
            String line = reader.readLine();
            if (line != null) {
                String[] parts = line.split("\\s+");
                if (parts.length > 14) {
                    long utime = Long.parseLong(parts[13]);
                    long stime = Long.parseLong(parts[14]);
                    return new long[]{utime, stime};
                }
            }
        } catch (Exception e) {
            android.util.Log.d("PerformanceAnalyzer", "readProcessCpuTimes failed: " + e.getClass().getSimpleName());
        }
        return null;
    }

    private long[] readSystemCpuTimesInternal() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = reader.readLine();
            if (line != null && line.startsWith("cpu ")) {
                String[] parts = line.split("\\s+");
                long[] times = new long[parts.length - 1];
                for (int i = 1; i < parts.length; i++) {
                    times[i - 1] = Long.parseLong(parts[i]);
                }
                return times;
            }
        } catch (Exception e) {
            android.util.Log.d("PerformanceAnalyzer", "readSystemCpuTimesInternal failed: " + e.getClass().getSimpleName());
        }
        return null;
    }

    // ========== 数据类 ==========

    public static class PerformanceScoreResult {
        public int totalScore;
        public String grade;
        public String gradeDescription;
        public int socScore;
        public int cpuUsage;
        public int cpuScore;
        public int memoryUsage;
        public int memoryScore;
        public int storageUsage;
        public int storageScore;
        public int anrScore;
        public AnrAnalysisResult anrResult;
    }

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
}
