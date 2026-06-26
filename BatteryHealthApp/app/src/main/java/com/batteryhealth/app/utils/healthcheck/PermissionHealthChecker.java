package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.os.Build;

import com.batteryhealth.app.data.model.HealthCheckResult;
import com.batteryhealth.app.utils.PermissionSelfCheck;

/**
 * 系统权限综合检测：检测 Android 13-16 各版本所需的运行时权限、
 * 电池优化白名单、精确闹钟、后台启动限制等关键系统权限。
 * <p>
 * 集成 PermissionSelfCheck 的完整权限自检逻辑，将其以健康检查结果形式呈现。
 */
public class PermissionHealthChecker implements IHealthChecker {

    @Override
    public String getName() { return "系统权限"; }

    @Override
    public String getCategory() { return HealthCheckResult.CATEGORY_SYSTEM; }

    @Override
    public int getPriority() { return 75; }

    @Override
    public HealthCheckResult check(Context context) {
        try {
            Context appCtx = context.getApplicationContext();
            PermissionSelfCheck.PermissionStatus status = PermissionSelfCheck.checkAllPermissions(appCtx);

            int issueCount = 0;
            StringBuilder descBuilder = new StringBuilder();
            StringBuilder adviceBuilder = new StringBuilder();

            // 运行时权限
            if (!status.runtimePermissions.isEmpty()) {
                issueCount += status.runtimePermissions.size();
                descBuilder.append("缺失运行时权限 ").append(status.runtimePermissions.size()).append(" 项；");
                adviceBuilder.append("请授予所有运行时权限；");
            }

            // 通知权限
            if (!status.notificationPermission) {
                issueCount++;
                descBuilder.append("通知权限未授予；");
                adviceBuilder.append("开启通知权限以接收重要提醒；");
            }

            // 电池优化
            if (!status.batteryOptimization) {
                issueCount++;
                descBuilder.append("未加入电池优化白名单；");
                adviceBuilder.append("加入白名单以确保后台监测稳定运行；");
            }

            // 精确闹钟
            if (!status.exactAlarmPermission) {
                issueCount++;
                descBuilder.append("精确闹钟权限未授予；");
                adviceBuilder.append("授予精确闹钟权限以确保定时任务准确执行；");
            }

            // 后台启动限制
            if (status.backgroundStartRestricted) {
                issueCount++;
                descBuilder.append("后台启动受限(Android 14+)；");
                adviceBuilder.append("在系统设置中关闭后台限制；");
            }

            // 全屏 Intent
            if (!status.fullScreenIntentPermission) {
                issueCount++;
                descBuilder.append("全屏 Intent 权限未授予；");
                adviceBuilder.append("授予全屏 Intent 权限以在重要时刻展示悬浮通知。");
            }

            String description = descBuilder.toString();
            String advice = adviceBuilder.toString();

            if (description.isEmpty()) {
                description = "所有关键系统权限已正确配置，应用可正常运行。";
                advice = "保持当前设置即可。";
            }

            int score;
            int severity;
            String statusText;
            if (issueCount == 0) {
                score = 100;
                severity = HealthCheckResult.SEVERITY_GOOD;
                statusText = "正常";
            } else if (issueCount <= 2) {
                score = 55;
                severity = HealthCheckResult.SEVERITY_WARNING;
                statusText = "部分受限";
            } else {
                score = 25;
                severity = HealthCheckResult.SEVERITY_CRITICAL;
                statusText = "多项受限";
            }

            return new HealthCheckResult.Builder()
                    .setId("permission_health")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(severity)
                    .setStatus(statusText)
                    .setValue(issueCount + " 项问题")
                    .setUnit("")
                    .setDescription(description)
                    .setAdvice(advice)
                    .setRepairable(issueCount > 0)
                    .setFixAction(HealthCheckResult.FIX_ACTION_PERMISSION_SETTINGS)
                    .setItemScore(score)
                    .build();

        } catch (Exception e) {
            return new HealthCheckResult.Builder()
                    .setId("permission_health")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus("读取失败")
                    .setValue("--")
                    .setUnit("")
                    .setDescription("读取系统权限状态失败：" + e.getMessage())
                    .setAdvice("请稍后重试。")
                    .setItemScore(50)
                    .build();
        }
    }
}
