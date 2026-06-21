package com.batteryhealth.app.utils.healthcheck;

import android.app.ActivityManager;
import android.content.Context;
import android.os.StatFs;
import android.os.Environment;
import android.util.Log;

import com.batteryhealth.app.data.model.HealthCheckResult;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * 设备性能健康度检测：综合 CPU 瞬时负载、内存使用率、存储使用率进行评分。
 *
 * <p>本检测使用的三个数据源均为系统原生 API 或标准 Linux 伪文件：
 * <ul>
 * <li>CPU：{@code /proc/stat} 读取 total/idle 次数，两次采样间隔计算瞬时使用率。</li>
 * <li>内存：{@link ActivityManager.MemoryInfo}，读取 total / avail / threshold。</li>
 * <li>存储：{@link StatFs}，计算内部存储可用百分比。</li>
 * </ul>
 */
public class PerformanceHealthChecker implements IHealthChecker {

    private static final String TAG = "PerformanceHealthChecker";

    /** CPU 采样间隔（毫秒），用于计算两次 /proc/stat 之间的增量。 */
    private static final long CPU_SAMPLE_INTERVAL_MS = 200L;

    @Override
    public String getName() { return "性能健康度"; }

    @Override
    public String getCategory() { return HealthCheckResult.CATEGORY_PERFORMANCE; }

    @Override
    public int getPriority() { return 50; }

    @Override
    public HealthCheckResult check(Context context) {
        if (context == null) {
            return new HealthCheckResult.Builder()
                    .setId("performance_health")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus("读取失败")
                    .setValue("--")
                    .setUnit("分")
                    .setDescription("读取性能数据时发生异常：context is null")
                    .setAdvice("请稍后重试。")
                    .setItemScore(55)
                    .build();
        }
        float cpuPct = readCpuUsage();
        float memoryPct = readMemoryUsage(context);
        float storagePct = readStorageUsage();

        // 三项加权综合评分，越高越好
        float weighted = 0.4f * (100f - cpuPct) + 0.3f * (100f - memoryPct) + 0.3f * (100f - storagePct);

        int severity;
        String status;
        String advice;
        int score = (int) weighted;
        if (weighted >= 70f) {
            severity = HealthCheckResult.SEVERITY_GOOD;
            status = "良好";
            advice = "当前设备运行流畅，无需调整。";
        } else if (weighted >= 50f) {
            severity = HealthCheckResult.SEVERITY_INFO;
            status = "一般";
            advice = "可尝试清理近期不使用的应用以提升响应速度。";
        } else if (weighted >= 30f) {
            severity = HealthCheckResult.SEVERITY_WARNING;
            status = "较重";
            advice = "CPU/内存负载偏高，建议关闭后台高耗电应用并释放存储空间。";
        } else {
            severity = HealthCheckResult.SEVERITY_CRITICAL;
            status = "异常";
            advice = "系统资源占用严重，请排查异常进程或进行系统清理。";
        }

        StringBuilder desc = new StringBuilder();
        desc.append("CPU 瞬时负载：").append(String.format("%.0f%%", cpuPct)).append("；");
        desc.append("内存使用率：").append(String.format("%.0f%%", memoryPct)).append("；");
        desc.append("存储使用率：").append(String.format("%.0f%%", storagePct)).append("。");

        return new HealthCheckResult.Builder()
                .setId("performance_health")
                .setTitle(getName())
                .setCategory(getCategory())
                .setSeverity(severity)
                .setStatus(status)
                .setValue(String.valueOf(score))
                .setUnit("分")
                .setDescription(desc.toString())
                .setAdvice(advice)
                .setItemScore(score)
                .build();
    }

    /**
     * 读取 CPU 使用率：通过两次采样 /proc/stat 计算增量比值，
     * 而非单次读取的累计平均值。
     */
    private static float readCpuUsage() {
        long[] first = readCpuStat();
        if (first == null) return 20f; // 读取失败时返回保守值

        try {
            Thread.sleep(CPU_SAMPLE_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 20f;
        }

        long[] second = readCpuStat();
        if (second == null) return 20f;

        long totalDelta = second[0] - first[0];
        long idleDelta = second[1] - first[1];

        if (totalDelta <= 0) return 0f;
        float usage = (1f - (float) idleDelta / totalDelta) * 100f;
        return Math.min(100f, Math.max(0f, usage));
    }

    /**
     * 读取 /proc/stat 的 total 和 idle 计数。
     * @return [total, idle] 或 null（读取失败时）
     */
    private static long[] readCpuStat() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = br.readLine();
            if (line == null) return null;
            String[] parts = line.split("\\s+");
            if (parts.length < 5) return null;
            long user = Long.parseLong(parts[1]);
            long nice = Long.parseLong(parts[2]);
            long system = Long.parseLong(parts[3]);
            long idle = Long.parseLong(parts[4]);
            long iowait = parts.length > 5 ? Long.parseLong(parts[5]) : 0L;
            long irq = parts.length > 6 ? Long.parseLong(parts[6]) : 0L;
            long softirq = parts.length > 7 ? Long.parseLong(parts[7]) : 0L;
            long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0L;
            long total = user + nice + system + idle + iowait + irq + softirq + steal;
            return new long[]{total, idle + iowait};
        } catch (IOException | NumberFormatException e) {
            Log.d(TAG, "readCpuStat failed: " + e.getMessage());
            return null;
        }
    }

    private static float readMemoryUsage(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getApplicationContext()
                    .getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return 40f;
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            if (info.totalMem <= 0) return 40f;
            float usedPct = (1f - (float) info.availMem / info.totalMem) * 100f;
            return Math.min(100f, Math.max(0f, usedPct));
        } catch (Exception e) {
            return 40f;
        }
    }

    private static float readStorageUsage() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long totalBytes = stat.getTotalBytes();
            long availBytes = stat.getAvailableBytes();
            if (totalBytes <= 0) return 40f;
            float usedPct = (1f - (float) availBytes / totalBytes) * 100f;
            return Math.min(100f, Math.max(0f, usedPct));
        } catch (Exception e) {
            return 40f;
        }
    }
}
