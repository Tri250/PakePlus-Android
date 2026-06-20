package com.batteryhealth.app.utils.healthcheck;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import com.batteryhealth.app.data.model.HealthCheckResult;

/**
 * 通知权限检测：Android 13+ 需要运行时请求 POST_NOTIFICATIONS，
 * 若未授予，后台监测服务将无法发送实时状态通知。
 */
public class NotificationPermissionChecker implements IHealthChecker {

    @Override
    public String getName() { return "通知权限"; }

    @Override
    public String getCategory() { return HealthCheckResult.CATEGORY_SYSTEM; }

    @Override
    public int getPriority() { return 80; }

    @Override
    public HealthCheckResult check(Context context) {
        try {
            Context appCtx = context.getApplicationContext();

            // Android 12 及以下无需运行时请求通知权限
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return new HealthCheckResult.Builder()
                        .setId("notification_permission")
                        .setTitle(getName())
                        .setCategory(getCategory())
                        .setSeverity(HealthCheckResult.SEVERITY_GOOD)
                        .setStatus("已启用")
                        .setValue("N/A")
                        .setUnit("")
                        .setDescription("当前系统版本无需显式请求通知权限。")
                        .setAdvice("若仍无法看到通知，请检查系统设置中的通知开关。")
                        .setItemScore(100)
                        .build();
            }

            boolean granted = appCtx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;

            // 同时检查通知总开关是否被屏蔽
            NotificationManager nm = (NotificationManager) appCtx
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            boolean notifBlocked = nm != null && !nm.areNotificationsEnabled();

            boolean healthy = granted && !notifBlocked;

            HealthCheckResult.Builder builder = new HealthCheckResult.Builder()
                    .setId("notification_permission")
                    .setTitle(getName())
                    .setCategory(getCategory());

            if (healthy) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_GOOD)
                        .setStatus("已启用")
                        .setValue("已授权")
                        .setUnit("")
                        .setDescription("通知权限已授予，后台监测服务可正常发送状态通知。")
                        .setAdvice("如不想被打扰，可在应用的「设置-预警设置」中关闭特定类型的通知。")
                        .setItemScore(100)
                        .build();
            }

            // 检查是否有特定的通知渠道被屏蔽（Android 8+）
            return builder
                    .setSeverity(HealthCheckResult.SEVERITY_WARNING)
                    .setStatus("未启用")
                    .setValue("未授权")
                    .setUnit("")
                    .setDescription("通知权限被限制，将无法收到电池健康、充电状态等实时提醒。")
                    .setAdvice("在系统设置中开启本应用的通知权限，以便及时获得重要事件提醒。")
                    .setRepairable(true)
                    .setFixAction(HealthCheckResult.FIX_ACTION_NOTIFICATION_SETTINGS)
                    .setItemScore(45)
                    .build();
        } catch (Exception e) {
            return new HealthCheckResult.Builder()
                    .setId("notification_permission")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus("读取失败")
                    .setValue("--")
                    .setUnit("")
                    .setDescription("读取通知权限状态失败：" + e.getMessage())
                    .setAdvice("请稍后重试。")
                    .setItemScore(55)
                    .build();
        }
    }
}
