package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import com.batteryhealth.app.data.model.HealthCheckResult;

/**
 * 存储健康度：内部存储空间使用率判断。
 *
 * <p>存储接近满容时会显著影响系统性能与 SQLite/缓存写入。
 */
public class StorageHealthChecker implements IHealthChecker {

    @Override
    public String getName() { return "存储健康"; }

    @Override
    public String getCategory() { return HealthCheckResult.CATEGORY_PERFORMANCE; }

    @Override
    public int getPriority() { return 60; }

    @Override
    public HealthCheckResult check(Context context) {
        if (context == null) {
            return buildInfoResult("读取存储数据时发生异常：context is null", "请稍后重试。");
        }
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long totalBytes = stat.getTotalBytes();
            long availBytes = stat.getAvailableBytes();

            if (totalBytes <= 0) {
                return buildInfoResult("无法读取存储参数。", "请稍后重试。");
            }

            float usedPct = (1f - (float) availBytes / totalBytes) * 100f;
            long availGb = availBytes / (1024 * 1024 * 1024);
            long totalGb = totalBytes / (1024 * 1024 * 1024);

            int severity;
            String status;
            String advice;
            int score;

            if (usedPct < 70f) {
                severity = HealthCheckResult.SEVERITY_GOOD;
                status = "良好";
                advice = "存储空间充裕，无需调整。";
                score = 100;
            } else if (usedPct < 85f) {
                severity = HealthCheckResult.SEVERITY_INFO;
                status = "正常";
                advice = "存储空间使用正常，可适时清理不再需要的照片/视频。";
                score = 75;
            } else if (usedPct < 95f) {
                severity = HealthCheckResult.SEVERITY_WARNING;
                status = "偏高";
                advice = "存储占用偏高，建议清理大文件/应用缓存/已下载内容。";
                score = 50;
            } else {
                severity = HealthCheckResult.SEVERITY_CRITICAL;
                status = "告急";
                advice = "存储空间即将用尽，系统性能会受影响，请立即清理大文件或卸载不常用应用。";
                score = 20;
            }

            String desc = String.format("总空间：%1$d GB，可用空间：%2$d GB，当前已占用：%3$.0f%%。",
                    totalGb, availGb, usedPct);

            return new HealthCheckResult.Builder()
                    .setId("storage_health")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(severity)
                    .setStatus(status)
                    .setValue(String.format("%.0f", usedPct))
                    .setUnit("%")
                    .setDescription(desc)
                    .setAdvice(advice)
                    .setItemScore(score)
                    .build();
        } catch (Exception e) {
            return buildInfoResult("读取存储数据失败：" + e.getMessage(), "请稍后重试。");
        }
    }

    private HealthCheckResult buildInfoResult(String description, String advice) {
        return new HealthCheckResult.Builder()
                .setId("storage_health")
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
