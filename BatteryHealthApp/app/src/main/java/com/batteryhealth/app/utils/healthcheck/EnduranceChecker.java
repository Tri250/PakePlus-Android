package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.batteryhealth.app.data.model.HealthCheckResult;
import com.batteryhealth.app.utils.BatteryConsumptionAnalyzer;

/**
 * 续航预测检测：基于当前电量与真实放电速率估算剩余可用时间。
 * 优先使用 BatteryConsumptionAnalyzer 的系统预估，回退到电流/容量计算。
 */
public class EnduranceChecker implements IHealthChecker {

    @Override
    public String getName() { return "续航预测"; }

    @Override
    public String getCategory() { return HealthCheckResult.CATEGORY_BATTERY; }

    @Override
    public int getPriority() { return 40; }

    @Override
    public HealthCheckResult check(Context context) {
        try {
            Context appCtx = context.getApplicationContext();
            Intent battery;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                battery = ContextCompat.registerReceiver(
                        appCtx, null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                        ContextCompat.RECEIVER_NOT_EXPORTED);
            } else {
                battery = appCtx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            }
            int level = battery != null ? battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
            int scale = battery != null ? battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;
            int status = battery != null ? battery.getIntExtra(BatteryManager.EXTRA_STATUS, 0) : 0;

            HealthCheckResult.Builder builder = new HealthCheckResult.Builder()
                    .setId("endurance_prediction")
                    .setTitle(getName())
                    .setCategory(getCategory());

            if (level <= 0 || scale <= 0) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_INFO)
                        .setStatus("无数据")
                        .setValue("--")
                        .setUnit("小时")
                        .setDescription("无法读取当前电量数据。")
                        .setAdvice("请稍后重试。")
                        .setItemScore(50)
                        .build();
            }

            int pct = level * 100 / scale;
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;

            // 使用 BatteryConsumptionAnalyzer 获取真实续航预估
            float hours = -1;
            float dischargeRate = 0;
            try {
                BatteryConsumptionAnalyzer.Result analysis =
                        BatteryConsumptionAnalyzer.analyze(appCtx, 24 * 60 * 60 * 1000L);
                if (analysis != null && analysis.systemEstimatedHours > 0) {
                    hours = (float) analysis.systemEstimatedHours;
                    if (pct > 0) {
                        dischargeRate = pct / hours;
                    }
                }
            } catch (Exception ignored) {}

            // 回退：通过电流和容量计算真实放电速率
            if (hours <= 0) {
                try {
                    BatteryManager bm = (BatteryManager) appCtx.getSystemService(Context.BATTERY_SERVICE);
                    if (bm != null) {
                        int currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
                        if (currentAvg == 0 || currentAvg == Integer.MIN_VALUE) {
                            currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                        }
                        int voltageMv = battery != null ? battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) : 0;
                        int capacityMicroAh = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                        if (capacityMicroAh == Integer.MIN_VALUE || capacityMicroAh == 0) {
                            capacityMicroAh = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_FULL);
                        }

                        if (currentAvg != 0 && currentAvg != Integer.MIN_VALUE && pct > 0) {
                            int capacityMah = -1;
                            if (capacityMicroAh > 100000) capacityMah = capacityMicroAh / 1000;
                            else if (capacityMicroAh > 100) capacityMah = capacityMicroAh;

                            if (capacityMah > 0) {
                                float remainingMah = capacityMah * (pct / 100f);
                                float absCurrentMa = Math.abs(currentAvg / 1000f);
                                if (absCurrentMa > 0) {
                                    if (isCharging) {
                                        // 充电时：剩余容量 / 充电电流
                                        float remainingToFull = capacityMah * ((100 - pct) / 100f);
                                        hours = remainingToFull / absCurrentMa;
                                    } else {
                                        // 放电时：剩余容量 / 放电电流
                                        hours = remainingMah / absCurrentMa;
                                    }
                                    dischargeRate = pct / hours;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            int severity;
            String statusText;
            String advice;
            int score;

            if (isCharging) {
                if (pct >= 90) {
                    severity = HealthCheckResult.SEVERITY_GOOD;
                    statusText = "即将充满";
                    advice = "电池接近充满，若长期保持充电，建议开启智能充电限制至 80%。";
                    score = 90;
                } else if (pct >= 50) {
                    severity = HealthCheckResult.SEVERITY_GOOD;
                    statusText = "充电中";
                    advice = "当前电量充足，可正常使用。";
                    score = 95;
                } else {
                    severity = HealthCheckResult.SEVERITY_INFO;
                    statusText = "充电中";
                    advice = "当前电量偏低，保持充电直到达到 80% 以上。";
                    score = 65;
                }
            } else if (hours > 0) {
                if (hours >= 6f) {
                    severity = HealthCheckResult.SEVERITY_GOOD;
                    statusText = "充裕";
                    advice = "预计续航充足，可放心使用。";
                    score = 100;
                } else if (hours >= 3f) {
                    severity = HealthCheckResult.SEVERITY_INFO;
                    statusText = "正常";
                    advice = "续航时间一般，建议关闭非必要后台应用以延长使用时间。";
                    score = 70;
                } else if (hours >= 1f) {
                    severity = HealthCheckResult.SEVERITY_WARNING;
                    statusText = "偏低";
                    advice = "预计续航时间较短，建议开启低电模式或及时充电。";
                    score = 50;
                } else {
                    severity = HealthCheckResult.SEVERITY_CRITICAL;
                    statusText = "电量告急";
                    advice = "电池即将耗尽，请立即接入充电器。";
                    score = 25;
                }
            } else {
                // 无法计算续航，仅基于电量判断
                if (pct >= 50) {
                    severity = HealthCheckResult.SEVERITY_GOOD;
                    statusText = "电量充足";
                    advice = "当前电量可正常使用。";
                    score = 80;
                } else if (pct >= 20) {
                    severity = HealthCheckResult.SEVERITY_INFO;
                    statusText = "电量一般";
                    advice = "建议关注电量变化，适时充电。";
                    score = 60;
                } else {
                    severity = HealthCheckResult.SEVERITY_WARNING;
                    statusText = "电量偏低";
                    advice = "建议尽快充电，避免深度放电损伤电池。";
                    score = 35;
                }
            }

            String desc;
            if (hours > 0) {
                desc = String.format("当前电量：%1$d%%。%2$s状态下预计可用约 %3$.1f 小时（放电速率 %4$.1f%%/h）",
                        pct, isCharging ? "充电" : "放电", hours, dischargeRate);
            } else {
                desc = String.format("当前电量：%1$d%%。%2$s状态，暂无法精确估算续航时间。",
                        pct, isCharging ? "充电" : "放电");
            }

            return builder
                    .setSeverity(severity)
                    .setStatus(statusText)
                    .setValue(hours > 0 ? String.format("%.1f", hours) : "--")
                    .setUnit("小时")
                    .setDescription(desc)
                    .setAdvice(advice)
                    .setItemScore(score)
                    .build();
        } catch (Exception e) {
            return new HealthCheckResult.Builder()
                    .setId("endurance_prediction")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus("读取失败")
                    .setValue("--")
                    .setUnit("")
                    .setDescription("读取电量数据失败：" + e.getMessage())
                    .setAdvice("请稍后重试。")
                    .setItemScore(55)
                    .build();
        }
    }
}
