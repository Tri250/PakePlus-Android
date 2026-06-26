package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.batteryhealth.app.data.model.HealthCheckResult;
import com.batteryhealth.app.utils.BatteryDataManager;

/**
 * 充电保护检测：监测高温充电、过充、异常电流等危险状态。
 *
 * <p>检测项：
 * <ul>
 *   <li>高温充电：充电时电池温度 > 45°C</li>
 *   <li>过充风险：长时间满电状态（100%且仍充电）</li>
 *   <li>异常电流：充电电流异常偏低或偏高</li>
 * </ul>
 */
public class ChargingProtectionChecker implements IHealthChecker {

    private static final float TEMP_CHARGING_WARNING = 42.0f;  // 充电时温度警告阈值
    private static final float TEMP_CHARGING_CRITICAL = 45.0f; // 充电时温度危险阈值
    private static final float CURRENT_LOW_THRESHOLD = 0.1f;   // 异常低电流（A）
    private static final float POWER_FAST_CHARGE_MIN = 18.0f;   // 快充最低功率

    @Override
    public String getName() { return "充电保护"; }

    @Override
    public String getCategory() { return HealthCheckResult.CATEGORY_CHARGING; }

    @Override
    public int getPriority() { return 25; }

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
            if (battery == null) {
                return buildNoDataResult();
            }

            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int batteryPct = (level >= 0 && scale > 0) ? (int) ((level / (float) scale) * 100) : -1;
            float tempC = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0f;

            BatteryDataManager manager = new BatteryDataManager(appCtx);
            com.batteryhealth.app.data.model.BatteryInfo info = manager.getBatteryInfo();
            float powerW = info != null ? info.getChargingPower() : 0f;

            HealthCheckResult.Builder builder = new HealthCheckResult.Builder()
                    .setId("charging_protection")
                    .setTitle(getName())
                    .setCategory(getCategory());

            // 未充电状态
            if (!isCharging) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_GOOD)
                        .setStatus("正常")
                        .setValue("--")
                        .setUnit("")
                        .setDescription("当前未充电，无充电保护风险。")
                        .setAdvice("充电时建议使用原装充电器和数据线，避免高温环境下充电。")
                        .setItemScore(100)
                        .build();
            }

            // 综合评估充电安全状态
            int worstSeverity = HealthCheckResult.SEVERITY_GOOD;
            int score = 100;
            StringBuilder descBuilder = new StringBuilder();
            StringBuilder adviceBuilder = new StringBuilder();
            String statusText = "安全";

            // 1. 高温充电检测
            if (tempC > 0) {
                if (tempC >= TEMP_CHARGING_CRITICAL) {
                    worstSeverity = HealthCheckResult.SEVERITY_CRITICAL;
                    score = Math.min(score, 25);
                    descBuilder.append(String.format("电池温度 %.1f°C 严重过高，继续充电可能导致电池膨胀或损坏。", tempC));
                    adviceBuilder.append("请立即停止充电，关闭高耗电应用，取下保护壳散热，待温度降至 35°C 以下再继续充电。");
                    statusText = "危险";
                } else if (tempC >= TEMP_CHARGING_WARNING) {
                    if (worstSeverity < HealthCheckResult.SEVERITY_WARNING) {
                        worstSeverity = HealthCheckResult.SEVERITY_WARNING;
                    }
                    score = Math.min(score, 55);
                    descBuilder.append(String.format("充电时电池温度 %.1f°C 偏高，长期高温充电会加速电池老化。", tempC));
                    adviceBuilder.append("建议取下保护壳散热，关闭后台高耗电应用，或暂时降低充电功率（如切换到普通充电模式）。");
                    statusText = "偏高";
                } else {
                    descBuilder.append(String.format("充电温度 %.1f°C 正常。", tempC));
                }
            }

            // 2. 过充检测：100% 仍充电
            if (batteryPct >= 100 && status == BatteryManager.BATTERY_STATUS_CHARGING) {
                if (worstSeverity < HealthCheckResult.SEVERITY_WARNING) {
                    worstSeverity = HealthCheckResult.SEVERITY_WARNING;
                }
                score = Math.min(score, 60);
                if (descBuilder.length() > 0) descBuilder.append(" ");
                descBuilder.append("电池已充满但仍在充电，长期满电状态会加速电解液分解。");
                if (adviceBuilder.length() > 0) adviceBuilder.append(" ");
                adviceBuilder.append("建议开启智能充电限制（80%上限），或充满后及时拔掉充电器。");
                statusText = "过充风险";
            }

            // 3. 异常电流检测
            if (powerW > 0 && powerW < 2.0f && batteryPct < 90) {
                // 电量低但功率极低，可能是充电器/数据线问题
                if (worstSeverity < HealthCheckResult.SEVERITY_INFO) {
                    worstSeverity = HealthCheckResult.SEVERITY_INFO;
                }
                score = Math.min(score, 65);
                if (descBuilder.length() > 0) descBuilder.append(" ");
                descBuilder.append(String.format("充电功率仅 %.1f W，远低于正常水平。", powerW));
                if (adviceBuilder.length() > 0) adviceBuilder.append(" ");
                adviceBuilder.append("请检查充电器和数据线是否为原装，接口是否接触良好。");
                if (statusText.equals("安全")) statusText = "功率偏低";
            }

            // 一切正常
            if (worstSeverity == HealthCheckResult.SEVERITY_GOOD) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_GOOD)
                        .setStatus("安全")
                        .setValue(String.format("%.1f", tempC > 0 ? tempC : 0))
                        .setUnit("°C")
                        .setDescription("充电状态安全，温度正常，功率稳定。")
                        .setAdvice("保持当前充电习惯即可，建议开启智能充电限制以延长电池寿命。")
                        .setItemScore(100)
                        .build();
            }

            return builder
                    .setSeverity(worstSeverity)
                    .setStatus(statusText)
                    .setValue(String.format("%.1f", tempC > 0 ? tempC : 0))
                    .setUnit("°C")
                    .setDescription(descBuilder.toString())
                    .setAdvice(adviceBuilder.toString())
                    .setRepairable(worstSeverity >= HealthCheckResult.SEVERITY_WARNING)
                    .setFixAction(HealthCheckResult.FIX_ACTION_CHARGING_LIMIT)
                    .setItemScore(score)
                    .build();

        } catch (Exception e) {
            return new HealthCheckResult.Builder()
                    .setId("charging_protection")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus("读取失败")
                    .setValue("--")
                    .setUnit("")
                    .setDescription("读取充电保护数据失败：" + e.getMessage())
                    .setAdvice("请稍后重试。")
                    .setItemScore(55)
                    .build();
        }
    }

    private HealthCheckResult buildNoDataResult() {
        return new HealthCheckResult.Builder()
                .setId("charging_protection")
                .setTitle(getName())
                .setCategory(getCategory())
                .setSeverity(HealthCheckResult.SEVERITY_INFO)
                .setStatus("无数据")
                .setValue("--")
                .setUnit("")
                .setDescription("无法读取电池状态信息。")
                .setAdvice("请确保应用具有读取电池信息的权限。")
                .setItemScore(60)
                .build();
    }
}
