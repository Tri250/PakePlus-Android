package com.batteryhealth.app.utils.healthcheck;

import android.app.ActivityManager;
import android.content.Context;
import android.os.StatFs;
import android.os.Environment;

import com.batteryhealth.app.data.model.HealthCheckResult;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * 设备性能健康度检测：综合 CPU 瞬时负载、内存使用率、存储使用率进行评分。
 *
 * <p>本检测使用的三个数据源均为系统原生 API 或标准 Linux 伪文件：
 * <ul>
 * <li>CPU：{@code /proc/stat} 读取 total/idle 次数，计算瞬时使用率。</li>
 * <li>内存：{@link ActivityManager.MemoryInfo}，读取 total / avail / threshold。</li>
 * <li>存储：{@link StatFs}，计算内部存储可用百分比。</li>
 * </ul>
 */
public class PerformanceHealthChecker implements IHealthChecker {

    @Override
    public String getName() { return "性能健康度"; }

    @Override
    public String getCategory() { return HealthCheckResult.CATEGORY_PERFORMANCE; }

    @Override
    public int getPriority() { return 50; }

    @Override
    public HealthCheckResult check(Context context) {
        boolean cpuFailed = false;
        boolean memoryFailed = false;
        boolean storageFailed = false;

        float cpuPct = readCpuUsage();
        if (cpuPct < 0) { cpuFailed = true; cpuPct = 0f; }
        float memoryPct = readMemoryUsage(context);
        if (memoryPct < 0) { memoryFailed = true; memoryPct = 0f; }
        float storagePct = readStorageUsage();
        if (storagePct < 0) { storageFailed = true; storagePct = 0f; }

        boolean anyFailed = cpuFailed || memoryFailed || storageFailed;

        // 三项加权综合评分，越高越好；读取失败的项目贡献为 0
        float weighted = 0.4f * (cpuFailed ? 0f : (100f - cpuPct))
                       + 0.3f * (memoryFailed ? 0f : (100f - memoryPct))
                       + 0.3f * (storageFailed ? 0f : (100f - storagePct));
        if (anyFailed) {
            // 有数据缺失时降低评分基准
            weighted *= 0.8f;
        }

        int severity;
        String status;
        String advice;
        int score = Math.round(weighted);
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
        if (!cpuFailed) {
            desc.append("CPU 瞬时负载：").append(String.format("%.0f%%", cpuPct)).append("；");
        }
        if (!memoryFailed) {
            desc.append("内存使用率：").append(String.format("%.0f%%", memoryPct)).append("；");
        }
        if (!storageFailed) {
            desc.append("存储使用率：").append(String.format("%.0f%%", storagePct)).append("。");
        }
        if (anyFailed) {
            desc.append("（部分数据读取失败，分数仅供参考）");
        }

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

    private static float readCpuUsage() {
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/stat"));
            String line = br.readLine();
            br.close();
            if (line == null) return -1f;
            String[] parts = line.split("\\s+");
            if (parts.length < 5) return -1f;
            long user = Long.parseLong(parts[1]);
            long nice = Long.parseLong(parts[2]);
            long system = Long.parseLong(parts[3]);
            long idle = Long.parseLong(parts[4]);
            long iowait = parts.length > 5 ? Long.parseLong(parts[5]) : 0L;
            long total = user + nice + system + idle + iowait;
            long nonIdle = user + nice + system;
            if (total <= 0) return -1f;
            return Math.min(100f, nonIdle * 100f / total);
        } catch (Exception e) {
            return -1f; // 读取失败返回 -1，由调用方标记并调整评分
        }
    }

    private static float readMemoryUsage(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getApplicationContext()
                    .getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return -1f;
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            if (info.totalMem <= 0) return -1f;
            float usedPct = (1f - (float) info.availMem / info.totalMem) * 100f;
            return Math.min(100f, Math.max(0f, usedPct));
        } catch (Exception e) {
            return -1f;
        }
    }

    private static float readStorageUsage() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long totalBytes = stat.getTotalBytes();
            long availBytes = stat.getAvailableBytes();
            if (totalBytes <= 0) return -1f;
            float usedPct = (1f - (float) availBytes / totalBytes) * 100f;
            return Math.min(100f, Math.max(0f, usedPct));
        } catch (Exception e) {
            return -1f;
        }
    }
}
