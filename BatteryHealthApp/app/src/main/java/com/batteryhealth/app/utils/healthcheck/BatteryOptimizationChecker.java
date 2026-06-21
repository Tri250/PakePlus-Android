package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import com.batteryhealth.app.data.model.HealthCheckResult;

/**
 * 电池优化白名单检测：若应用未加入白名单，在 Doze/待机省电模式下
 * 后台服务可能被强行停止或延迟执行。
 */
public class BatteryOptimizationChecker implements IHealthChecker {

    @Override
    public String getName() { return "后台运行"; }

    @Override
    public String getCategory() { return HealthCheckResult.CATEGORY_SYSTEM; }

    @Override
    public int getPriority() { return 85; }

    @Override
    public HealthCheckResult check(Context context) {
        if (context == null) {
            return new HealthCheckResult.Builder()
                    .setId("battery_optimization")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus("读取失败")
                    .setValue("--")
                    .setUnit("")
                    .setDescription("读取电池优化状态时发生异常：context is null")
                    .setAdvice("请稍后重试。")
                    .setItemScore(60)
                    .build();
        }
        try {
            Context appCtx = context.getApplicationContext();

            // Android M 以下不存在电池优化机制
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return new HealthCheckResult.Builder()
                        .setId("battery_optimization")
                        .setTitle(getName())
                        .setCategory(getCategory())
                        .setSeverity(HealthCheckResult.SEVERITY_GOOD)
                        .setStatus("不适用")
                        .setValue("N/A")
                        .setUnit("")
                        .setDescription("当前系统版本无需电池优化白名单设置。")
                        .setAdvice("保持当前设置即可。")
                        .setItemScore(100)
                        .build();
            }

            PowerManager pm = (PowerManager) appCtx.getSystemService(Context.POWER_SERVICE);
            boolean ignoring = pm != null && pm.isIgnoringBatteryOptimizations(appCtx.getPackageName());

            HealthCheckResult.Builder builder = new HealthCheckResult.Builder()
                    .setId("battery_optimization")
                    .setTitle(getName())
                    .setCategory(getCategory());

            if (ignoring) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_GOOD)
                        .setStatus("已加入白名单")
                        .setValue("已启用")
                        .setUnit("")
                        .setDescription("应用已加入电池优化白名单，可在后台稳定运行监测服务。")
                        .setAdvice("保持当前设置即可。")
                        .setItemScore(100)
                        .build();
            }

            return builder
                    .setSeverity(HealthCheckResult.SEVERITY_WARNING)
                    .setStatus("受限")
                    .setValue("未加入")
                    .setUnit("")
                    .setDescription("应用未加入电池优化白名单，在深度休眠/待机场景下后台监测服务可能被暂停，" +
                            "导致数据记录中断、预警延迟。")
                    .setAdvice("点击「去设置」将本应用加入电池优化白名单，以获得最佳监测体验。")
                    .setRepairable(true)
                    .setFixAction(HealthCheckResult.FIX_ACTION_BATTERY_OPTIMIZATION)
                    .setItemScore(55)
                    .build();
        } catch (Exception e) {
            return new HealthCheckResult.Builder()
                    .setId("battery_optimization")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus("读取失败")
                    .setValue("--")
                    .setUnit("")
                    .setDescription("读取电池优化状态失败：" + e.getMessage())
                    .setAdvice("请稍后重试。")
                    .setItemScore(60)
                    .build();
        }
    }
}
