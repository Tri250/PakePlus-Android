package com.batteryhealth.app.utils.healthcheck;

import android.app.ActivityManager;
import android.content.Context;

import com.batteryhealth.app.data.model.HealthCheckResult;

/**
 * 内存健康度：读取设备可用内存与总内存，判断是否存在频繁内存回收压力。
 */
public class MemoryHealthChecker implements IHealthChecker {

    @Override
    public String getName() { return "内存健康"; }

    @Override
    public String getCategory() { return HealthCheckResult.CATEGORY_PERFORMANCE; }

    @Override
    public int getPriority() { return 55; }

    @Override
    public HealthCheckResult check(Context context) {
        if (context == null) {
            return buildInfoResult("读取内存数据时发生异常：context is null", "请稍后重试。");
        }
        try {
            Context appCtx = context.getApplicationContext();
            ActivityManager am = (ActivityManager) appCtx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return buildInfoResult("无法访问 ActivityManager。", "请稍后重试。");
            }

            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);

            long totalMb = info.totalMem / (1024 * 1024);
            long availMb = info.availMem / (1024 * 1024);
            float usedPct = info.totalMem > 0 ? (1f - (float) info.availMem / info.totalMem) * 100f : 0f;
            boolean lowMemory = info.lowMemory;

            HealthCheckResult.Builder builder = new HealthCheckResult.Builder()
                    .setId("memory_health")
                    .setTitle(getName())
                    .setCategory(getCategory());

            int severity;
            String status;
            String advice;
            int score;

            if (usedPct < 60f && !lowMemory) {
                severity = HealthCheckResult.SEVERITY_GOOD;
                status = "充裕";
                advice = "当前内存充裕，无需调整。";
                score = 100;
            } else if (usedPct < 80f && !lowMemory) {
                severity = HealthCheckResult.SEVERITY_INFO;
                status = "正常";
                advice = "内存使用正常；如偶有卡顿，可清理近期未使用的应用。";
                score = 75;
            } else if (usedPct < 90f) {
                severity = HealthCheckResult.SEVERITY_WARNING;
                status = "偏高";
                advice = "内存占用较高，建议关闭后台应用或重启设备以释放内存。";
                score = 55;
            } else {
                severity = HealthCheckResult.SEVERITY_CRITICAL;
                status = "告急";
                advice = "内存占用极高，系统可能已处于频繁内存回收状态，建议立即关闭后台应用或重启。";
                score = 25;
            }

            String desc = String.format("总内存：%1$d MB，可用内存：%2$d MB，当前已占用：%3$.0f%%。",
                    totalMb, availMb, usedPct);

            return builder
                    .setSeverity(severity)
                    .setStatus(status)
                    .setValue(String.format("%.0f", usedPct))
                    .setUnit("%")
                    .setDescription(desc)
                    .setAdvice(advice)
                    .setItemScore(score)
                    .build();
        } catch (Exception e) {
            return buildInfoResult("读取内存数据失败：" + e.getMessage(), "请稍后重试。");
        }
    }

    private HealthCheckResult buildInfoResult(String description, String advice) {
        return new HealthCheckResult.Builder()
                .setId("memory_health")
                .setTitle(getName())
                .setCategory(getCategory())
                .setSeverity(HealthCheckResult.SEVERITY_INFO)
                .setStatus("读取失败")
                .setValue("--")
                .setUnit("")
                .setDescription(description)
                .setAdvice(advice)
                .setItemScore(55)
                .build();
    }
}
