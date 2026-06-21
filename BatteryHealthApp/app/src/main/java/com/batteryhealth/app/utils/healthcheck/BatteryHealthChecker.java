package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.HealthCheckResult;
import com.batteryhealth.app.utils.BatteryDataManager;

/**
 * 电池健康度检测。
 *
 * <p>直接复用 {@link BatteryDataManager} 提供的实测数据：
 * <ul>
 * <li>若能读取到电池健康度百分比，则按区间给出 GOOD / WARNING / CRITICAL。</li>
 * <li>若无法读取，则给出 INFO 级别提示，供用户了解当前系统限制。</li>
 * </ul>
 */
public class BatteryHealthChecker implements IHealthChecker {

    @Override
    public String getName() { return "电池健康度"; }

    @Override
    public String getCategory() { return HealthCheckResult.CATEGORY_BATTERY; }

    @Override
    public int getPriority() { return 10; }

    @Override
    public HealthCheckResult check(Context context) {
        if (context == null) {
            return new HealthCheckResult.Builder()
                    .setId("battery_health")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus("读取失败")
                    .setValue("--")
                    .setUnit("")
                    .setDescription("读取电池健康数据时发生异常：context is null")
                    .setAdvice("可尝试重启设备或稍后再次检测。")
                    .setItemScore(50)
                    .build();
        }
        try {
            BatteryDataManager manager = new BatteryDataManager(context.getApplicationContext());
            com.batteryhealth.app.data.model.BatteryInfo info = manager.getBatteryInfo();
            float healthPct = info != null ? info.getHealthPercentage() : -1f;
            int cycleCount = info != null ? info.getCycleCount() : -1;

            HealthCheckResult.Builder builder = new HealthCheckResult.Builder()
                    .setId("battery_health")
                    .setTitle(getName())
                    .setCategory(getCategory());

            if (healthPct < 0) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_INFO)
                        .setStatus("无法读取")
                        .setValue("--")
                        .setUnit("%")
                        .setDescription("当前系统未开放电池健康度数据；仍可通过循环次数与温度趋势辅助判断。")
                        .setAdvice("保持 20%-80% 电量区间，避免长期高温充电，可显著延长电池寿命。")
                        .setItemScore(60)
                        .build();
            }

            int pct = Math.round(healthPct);
            int severity;
            String status;
            String advice;
            int score;
            if (healthPct >= 90) {
                severity = HealthCheckResult.SEVERITY_GOOD;
                status = "状态良好";
                advice = "电池状态极佳，保持当前使用习惯即可。";
                score = 100;
            } else if (healthPct >= 75) {
                severity = HealthCheckResult.SEVERITY_GOOD;
                status = "正常";
                advice = "电池健康度仍在正常区间，建议保持良好充电习惯。";
                score = 85;
            } else if (healthPct >= 60) {
                severity = HealthCheckResult.SEVERITY_WARNING;
                status = "存在衰减";
                advice = "电池已有明显衰减，建议开启智能充电限制，避免满电长充。";
                score = 60;
            } else {
                severity = HealthCheckResult.SEVERITY_CRITICAL;
                status = "建议更换";
                advice = "电池衰减严重，建议联系售后评估更换，更换后续航将获得显著提升。";
                score = 35;
            }

            StringBuilder desc = new StringBuilder();
            desc.append("实测电池健康度：").append(pct).append("%。");
            if (cycleCount > 0) {
                desc.append("累计循环次数：").append(cycleCount).append("次。");
            }
            if (info != null && info.getBatterySource() != null) {
                desc.append("电池来源判定：").append(mapBatterySource(info.getBatterySource())).append("。");
            }

            return builder
                    .setSeverity(severity)
                    .setStatus(status)
                    .setValue(String.valueOf(pct))
                    .setUnit("%")
                    .setDescription(desc.toString())
                    .setAdvice(advice)
                    .setItemScore(score)
                    .build();
        } catch (Exception e) {
            return new HealthCheckResult.Builder()
                    .setId("battery_health")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus("读取失败")
                    .setValue("--")
                    .setUnit("")
                    .setDescription("读取电池健康数据时发生异常：" + e.getMessage())
                    .setAdvice("可尝试重启设备或稍后再次检测。")
                    .setItemScore(50)
                    .build();
        }
    }

    private static String mapBatterySource(String source) {
        if (source == null) return "未知";
        switch (source) {
            case "original": return "原装";
            case "third_party": return "第三方";
            default: return "无法验证";
        }
    }
}
