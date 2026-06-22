package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.batteryhealth.app.data.model.HealthCheckResult;

/**
 * 续航预测检测：基于当前电量与放电速率估算剩余可用时间，并
 * 根据预计续航时长与用户历史数据评分。
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
            Intent battery = appCtx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
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

            BatteryManager bm = (BatteryManager) appCtx.getSystemService(Context.BATTERY_SERVICE);
            int currentMa = readCurrentMa(bm);
            int fullCapacityMah = readFullCapacityMah(bm);
            int chargeCounterMah = readChargeCounterMah(bm);

            // 无真实电流或容量数据时不使用固定放电速率模拟
            if (currentMa <= 0 || fullCapacityMah <= 0) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_INFO)
                        .setStatus("计算中")
                        .setValue("--")
                        .setUnit("小时")
                        .setDescription("暂无法获取实时电流或容量数据，无法估算续航。")
                        .setAdvice("请确保设备支持 BatteryManager 电流/容量读取，或稍后重试。")
                        .setItemScore(50)
                        .build();
            }

            float hours;
            int severity;
            String statusText;
            String advice;
            int score;

            if (isCharging) {
                int remainingMah = fullCapacityMah - chargeCounterMah;
                if (remainingMah <= 0) {
                    remainingMah = (int) (fullCapacityMah * (100 - pct) / 100f);
                }
                hours = remainingMah / (float) currentMa;

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
            } else {
                int remainingMah = chargeCounterMah > 0
                        ? chargeCounterMah
                        : (int) (fullCapacityMah * pct / 100f);
                hours = remainingMah / (float) currentMa;

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
            }

            String desc = String.format("当前电量：%1$d%%，%2$s状态下基于实时电流/容量估算约 %3$.1f 小时",
                    pct, isCharging ? "充电" : "放电", hours);

            return builder
                    .setSeverity(severity)
                    .setStatus(statusText)
                    .setValue(String.format("%.1f", hours))
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

    /**
     * 读取当前电流绝对值（mA）。优先 CURRENT_AVERAGE，回退 CURRENT_NOW。
     */
    private int readCurrentMa(BatteryManager bm) {
        if (bm == null) return 0;
        int current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
        if (current == 0 || current == Integer.MIN_VALUE) {
            current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        }
        if (current == Integer.MIN_VALUE) return 0;
        int abs = Math.abs(current);
        // BatteryManager 通常返回 µA，需转换为 mA
        if (abs > 100000) return abs / 1000;
        return abs;
    }

    /**
     * 读取满充容量（mAh）。优先 CHARGE_FULL，回退 CHARGE_FULL_DESIGN。
     */
    private int readFullCapacityMah(BatteryManager bm) {
        if (bm == null) return -1;
        int full = bm.getIntProperty(24); // BATTERY_PROPERTY_CHARGE_FULL
        if (full <= 0 || full == Integer.MIN_VALUE) {
            full = bm.getIntProperty(9); // BATTERY_PROPERTY_CHARGE_FULL_DESIGN
        }
        if (full <= 0 || full == Integer.MIN_VALUE) return -1;
        if (full > 100000) return full / 1000;
        return full;
    }

    /**
     * 读取当前电荷计数容量（mAh），即剩余可用容量。
     */
    private int readChargeCounterMah(BatteryManager bm) {
        if (bm == null) return -1;
        int counter = bm.getIntProperty(6); // BATTERY_PROPERTY_CHARGE_COUNTER
        if (counter == Integer.MIN_VALUE || counter == 0) return -1;
        int abs = Math.abs(counter);
        if (abs > 100000) return abs / 1000;
        return abs;
    }
}
