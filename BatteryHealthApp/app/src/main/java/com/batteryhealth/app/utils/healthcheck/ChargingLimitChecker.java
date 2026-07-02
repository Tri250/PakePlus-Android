package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;

import com.batteryhealth.app.R;
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
                        .setStatus(appCtx.getString(R.string.status_undetected))
                        .setValue("--")
                        .setUnit("%")
                        .setDescription(appCtx.getString(R.string.charging_limit_no_api))
                        .setAdvice(appCtx.getString(R.string.charging_limit_no_api_advice))
                        .setItemScore(75)
                        .build();
            }

            if (limit < 100) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_GOOD)
                        .setStatus(appCtx.getString(R.string.status_enabled))
                        .setValue(String.valueOf(limit))
                        .setUnit("%")
                        .setDescription(appCtx.getString(R.string.charging_limit_enabled_desc, limit))
                        .setAdvice(appCtx.getString(R.string.charging_limit_enabled_advice))
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
                status = appCtx.getString(R.string.status_disabled);
                advice = appCtx.getString(R.string.charging_limit_disabled_advice_low_health);
                score = 55;
            } else {
                severity = HealthCheckResult.SEVERITY_INFO;
                status = appCtx.getString(R.string.status_disabled);
                advice = appCtx.getString(R.string.charging_limit_disabled_advice);
                score = 70;
            }

            return builder
                    .setSeverity(severity)
                    .setStatus(status)
                    .setValue(String.valueOf(limit))
                    .setUnit("%")
                    .setDescription(appCtx.getString(R.string.charging_limit_disabled_desc))
                    .setAdvice(advice)
                    .setRepairable(true)
                    .setFixAction(HealthCheckResult.FIX_ACTION_CHARGING_LIMIT)
                    .setItemScore(score)
                    .build();
        } catch (Exception e) {
            Context appCtx = context.getApplicationContext();
            return new HealthCheckResult.Builder()
                    .setId("charging_limit")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus(appCtx.getString(R.string.status_read_failed))
                    .setValue("--")
                    .setUnit("")
                    .setDescription(appCtx.getString(R.string.charging_limit_error_desc, e.getMessage()))
                    .setAdvice(appCtx.getString(R.string.health_check_retry_later))
                    .setItemScore(55)
                    .build();
        }
    }
}
