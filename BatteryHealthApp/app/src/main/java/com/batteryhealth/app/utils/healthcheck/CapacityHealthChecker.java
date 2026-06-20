package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;

import com.batteryhealth.app.data.model.HealthCheckResult;
import com.batteryhealth.app.utils.BatteryDataManager;

/**
 * 容量衰减检测：对比设计容量与当前满充容量，判断电池化学状态。
 */
public class CapacityHealthChecker implements IHealthChecker {

    @Override
    public String getName() { return "容量衰减"; }

    @Override
    public String getCategory() { return HealthCheckResult.CATEGORY_BATTERY; }

    @Override
    public int getPriority() { return 25; }

    @Override
    public HealthCheckResult check(Context context) {
        try {
            BatteryDataManager manager = new BatteryDataManager(context.getApplicationContext());
            com.batteryhealth.app.data.model.BatteryInfo info = manager.getBatteryInfo();
            if (info == null) {
                return buildInfoResult("无法获取电池容量信息。", "请确保应用已获得所需权限。");
            }

            int design = info.getDesignCapacity();
            int current = info.getCurrentCapacity();

            HealthCheckResult.Builder builder = new HealthCheckResult.Builder()
                    .setId("capacity_health")
                    .setTitle(getName())
                    .setCategory(getCategory());

            if (design <= 0 || current <= 0) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_INFO)
                        .setStatus("无数据")
                        .setValue("--")
                        .setUnit("mAh")
                        .setDescription("系统未提供容量参数，无法进行容量衰减分析。")
                        .setAdvice("可尝试使用第三方 BatteryManager API 更完善的 ROM。")
                        .setItemScore(65)
                        .build();
            }

            float lossPct = (1f - (float) current / design) * 100f;
            float remainPct = (float) current / design * 100f;

            int severity;
            String status;
            String advice;
            int score;
            if (lossPct <= 10f) {
                severity = HealthCheckResult.SEVERITY_GOOD;
                status = "状态极佳";
                advice = "当前容量几乎与全新状态一致，保持良好使用习惯即可。";
                score = 100;
            } else if (lossPct <= 20f) {
                severity = HealthCheckResult.SEVERITY_GOOD;
                status = "正常";
                advice = "容量略有衰减，仍处于正常范围。";
                score = 85;
            } else if (lossPct <= 35f) {
                severity = HealthCheckResult.SEVERITY_WARNING;
                status = "明显衰减";
                advice = "容量衰减已较明显，建议开启智能充电限制并避免高温环境。";
                score = 65;
            } else {
                severity = HealthCheckResult.SEVERITY_CRITICAL;
                status = "严重衰减";
                advice = "容量衰减严重，建议联系售后进行电池更换评估。";
                score = 35;
            }

            return builder
                    .setSeverity(severity)
                    .setStatus(status)
                    .setValue(String.format("%.0f/%.0f", (float) current, (float) design))
                    .setUnit("mAh")
                    .setDescription("设计容量：" + design + " mAh，当前容量：" + current
                            + " mAh，当前保留比例约 " + String.format("%.1f", remainPct) + "%。")
                    .setAdvice(advice)
                    .setItemScore(score)
                    .build();
        } catch (Exception e) {
            return buildInfoResult("读取容量数据失败：" + e.getMessage(), "请稍后重试。");
        }
    }

    private HealthCheckResult buildInfoResult(String description, String advice) {
        return new HealthCheckResult.Builder()
                .setId("capacity_health")
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
