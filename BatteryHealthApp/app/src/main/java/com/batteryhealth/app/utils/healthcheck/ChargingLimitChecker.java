package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;

import com.batteryhealth.app.data.model.HealthCheckResult;
import com.batteryhealth.app.utils.BatteryDataManager;

/**
 * 智能充电限制检测：判断是否开启了 80%/85%/90% 的智能充电上限。
 * 若无开启且电池健康度已下降到一定水平，建议用户开启以延长电池寿命。
 */
public class ChargingLimitChecker implements IHealthChecker {

    @Override
    public String getName() { return "充电限制"; }

    @Override
    public String getCategory() { return HealthCheckResult.CATEGORY_CHARGING; }

    @Override
    public int getPriority() { return 35; }

    @Override
    public HealthCheckResult check(Context context) {
        if (context == null) {
            return new HealthCheckResult.Builder()
                    .setId("charging_limit")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus("读取失败")
                    .setValue("--")
                    .setUnit("")
                    .setDescription("读取充电限制状态时发生异常：context is null")
                    .setAdvice("请稍后重试。")
                    .setItemScore(55)
                    .build();
        }
        try {
            Context appCtx = context.getApplicationContext();
            BatteryDataManager manager = new BatteryDataManager(appCtx);
            int limit = manager.getChargingLimitPercent(); // 100 表示无限制
            float healthPct = 0;
            try {
                com.batteryhealth.app.data.model.BatteryInfo info = manager.getBatteryInfo();
                if (info != null) healthPct = info.getHealthPercentage();
            } catch (Exception ignored) {}

            HealthCheckResult.Builder builder = new HealthCheckResult.Builder()
                    .setId("charging_limit")
                    .setTitle(getName())
                    .setCategory(getCategory());

            // 正常化：若 limit > 100 视为无数据（系统未开放 API）
            if (limit > 100 || limit <= 0) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_INFO)
                        .setStatus("未检测到")
                        .setValue("--")
                        .setUnit("%")
                        .setDescription("当前 ROM 未提供统一的充电限制读取接口。")
                        .setAdvice("可在系统「电池」设置中查找「智能充电/优化充电」开关手动开启，保持 80% 充电上限能显著延长电池寿命。")
                        .setItemScore(75)
                        .build();
            }

            if (limit < 100) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_GOOD)
                        .setStatus("已启用")
                        .setValue(String.valueOf(limit))
                        .setUnit("%")
                        .setDescription("当前已启用智能充电限制（上限 " + limit + "%），有利于延长电池化学寿命。")
                        .setAdvice("保持当前设置即可；若即将外出需要更长续航，可临时取消限制。")
                        .setItemScore(100)
                        .build();
            }

            // limit == 100：未启用智能充电限制
            int severity;
            String status;
            String advice;
            int score;
            if (healthPct > 0 && healthPct < 80f) {
                severity = HealthCheckResult.SEVERITY_WARNING;
                status = "未启用";
                advice = "电池健康度已有下降，强烈建议开启智能充电限制至 80%。";
                score = 55;
            } else {
                severity = HealthCheckResult.SEVERITY_INFO;
                status = "未启用";
                advice = "建议开启智能充电限制至 80%，长期保持电池化学健康。";
                score = 70;
            }

            return builder
                    .setSeverity(severity)
                    .setStatus(status)
                    .setValue(String.valueOf(limit))
                    .setUnit("%")
                    .setDescription("系统未启用智能充电限制。长期满电充电会加速电解液分解。")
                    .setAdvice(advice)
                    .setRepairable(true)
                    .setFixAction(HealthCheckResult.FIX_ACTION_CHARGING_LIMIT)
                    .setItemScore(score)
                    .build();
        } catch (Exception e) {
            return new HealthCheckResult.Builder()
                    .setId("charging_limit")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus("读取失败")
                    .setValue("--")
                    .setUnit("")
                    .setDescription("读取充电限制状态失败：" + e.getMessage())
                    .setAdvice("请稍后重试。")
                    .setItemScore(55)
                    .build();
        }
    }
}
