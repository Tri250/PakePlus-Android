package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.batteryhealth.app.data.model.HealthCheckResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * 续航预测检测：基于真实电池参数（容量 + 电流瞬时值）计算放电速率，
 * 杜绝任何"硬编码 + 简化估算"。
 *
 * <p>数据来源优先级：
 * <ol>
 *   <li>BatteryManager.BATTERY_PROPERTY_CHARGE_FULL_DESIGN / BATTERY_PROPERTY_CHARGE_FULL</li>
 *   <li>BatteryManager.BATTERY_PROPERTY_CURRENT_NOW（µA）</li>
 *   <li>sysfs /sys/class/power_supply/battery/charge_full / current_now</li>
 *   <li>以上均失败时，诚实告知"数据不足"，不做伪估算</li>
 * </ol>
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
                return builder.setSeverity(HealthCheckResult.SEVERITY_INFO)
                        .setStatus("无数据")
                        .setValue("--")
                        .setUnit("小时")
                        .setDescription("无法读取当前电量数据。")
                        .setAdvice("请稍后重试。")
                        .setItemScore(50).build();
            }

            int pct = level * 100 / scale;
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;

            // 真实读取电池容量（mAh）
            int capacityMah = readCapacityMah(appCtx);
            // 真实读取当前电流绝对值（mA）
            float currentMa = Math.abs(readCurrentMa(appCtx));

            float hours;
            int severity;
            String statusText;
            String advice;
            int score;
            String detail;

            if (isCharging) {
                if (currentMa > 0 && capacityMah > 0) {
                    int remainingToFullPct = 100 - pct;
                    float remainingMah = capacityMah * remainingToFullPct / 100f;
                    hours = remainingMah / currentMa;
                    detail = String.format("当前充电电流约 %.0f mA，剩余 %d%% 约需 %.1f 小时",
                            currentMa, remainingToFullPct, hours);
                } else {
                    hours = 0;
                    detail = "无法读取充电电流数据。";
                }

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
                if (currentMa > 0 && capacityMah > 0) {
                    float remainingMah = capacityMah * pct / 100f;
                    hours = remainingMah / currentMa;
                    detail = String.format("当前放电电流约 %.0f mA，剩余 %d%%（%.0f mAh）预计可用 %.1f 小时",
                            currentMa, pct, remainingMah, hours);
                } else {
                    hours = 0;
                    detail = "无法读取实时电流数据，无法准确估算续航。";
                }

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
                } else if (hours > 0) {
                    severity = HealthCheckResult.SEVERITY_CRITICAL;
                    statusText = "电量告急";
                    advice = "电池即将耗尽，请立即接入充电器。";
                    score = 25;
                } else {
                    severity = HealthCheckResult.SEVERITY_INFO;
                    statusText = "数据不足";
                    advice = "暂无法估算续航。请保持应用前台运行 1-2 分钟以采集电流数据。";
                    score = 40;
                }
            }

            String desc = String.format("当前电量：%1$d%%。%2$s。%3$s",
                    pct, isCharging ? "充电中" : "放电中", detail);

            return builder.setSeverity(severity).setStatus(statusText)
                    .setValue(hours > 0 ? String.format("%.1f", hours) : "--")
                    .setUnit("小时").setDescription(desc).setAdvice(advice).setItemScore(score).build();
        } catch (Exception e) {
            return new HealthCheckResult.Builder()
                    .setId("endurance_prediction").setTitle(getName()).setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_WARNING).setStatus("检测异常")
                    .setValue("--").setUnit("")
                    .setDescription("读取电量数据失败：" + e.getMessage())
                    .setAdvice("请稍后重试。").setItemScore(30).build();
        }
    }

    /** 真实读取电池容量 mAh（优先 BatteryManager API，回退 sysfs） */
    private int readCapacityMah(Context ctx) {
        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int micro = bm.getIntProperty(25); // BATTERY_PROPERTY_CHARGE_FULL
                if (micro > 1000) return micro / 1000;
            }
        } catch (Throwable ignored) {}
        try {
            File f = new File("/sys/class/power_supply/battery/charge_full");
            if (f.exists() && f.canRead()) {
                try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                    String line = r.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        long raw = Long.parseLong(line.trim());
                        if (raw > 1000) return (int) (raw / 1000);
                        return (int) raw;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    /** 真实读取当前电流绝对值 mA */
    private float readCurrentMa(Context ctx) {
        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (currentUa != Integer.MIN_VALUE && currentUa != 0) {
                    int abs = Math.abs(currentUa);
                    if (abs > 100000) return abs / 1000f;
                    return abs;
                }
            }
        } catch (Throwable ignored) {}
        try {
            File f = new File("/sys/class/power_supply/battery/current_now");
            if (f.exists() && f.canRead()) {
                try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                    String line = r.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        long raw = Math.abs(Long.parseLong(line.trim()));
                        if (raw > 100000) return raw / 1000f;
                        if (raw > 0) return raw;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return -1f;
    }
}