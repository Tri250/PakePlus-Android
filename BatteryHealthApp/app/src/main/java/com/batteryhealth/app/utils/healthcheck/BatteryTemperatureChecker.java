package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.batteryhealth.app.data.model.HealthCheckResult;

/**
 * 电池温度检测。
 *
 * <p>读取 {@link BatteryManager#EXTRA_TEMPERATURE}，按区间判定正常/异常。
 * 锂离子电池的理想工作温度约为 15-35°C，高于 45°C 属于异常高温。
 */
public class BatteryTemperatureChecker implements IHealthChecker {

    private static final float TEMP_NORMAL_MAX = 35.0f;
    private static final float TEMP_WARNING_MAX = 45.0f;

    @Override
    public String getName() { return "电池温度"; }

    @Override
    public String getCategory() { return HealthCheckResult.CATEGORY_BATTERY; }

    @Override
    public int getPriority() { return 20; }

    @Override
    public HealthCheckResult check(Context context) {
        try {
            Intent battery;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                battery = ContextCompat.registerReceiver(
                        context.getApplicationContext(), null,
                        new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                        ContextCompat.RECEIVER_NOT_EXPORTED);
            } else {
                battery = context.getApplicationContext()
                        .registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            }
            int tempRaw = battery != null ? battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) : -1;
            float tempC = tempRaw > 0 ? tempRaw / 10.0f : Float.NaN;

            HealthCheckResult.Builder builder = new HealthCheckResult.Builder()
                    .setId("battery_temperature")
                    .setTitle(getName())
                    .setCategory(getCategory());

            if (Float.isNaN(tempC)) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_INFO)
                        .setStatus("无法读取")
                        .setValue("--")
                        .setUnit("°C")
                        .setDescription("系统未提供电池温度读数。")
                        .setAdvice("若设备体感明显发热，请关闭后台高耗电应用并散热。")
                        .setItemScore(60)
                        .build();
            }

            int severity;
            String status;
            String advice;
            int score;
            if (tempC <= TEMP_NORMAL_MAX) {
                severity = HealthCheckResult.SEVERITY_GOOD;
                status = "正常";
                advice = "温度处于理想区间，继续保持即可。";
                score = 100;
            } else if (tempC <= TEMP_WARNING_MAX) {
                severity = HealthCheckResult.SEVERITY_WARNING;
                status = "偏高";
                advice = "温度略高，建议关闭高耗电应用并移除充电线与保护壳以帮助散热。";
                score = 70;
            } else {
                severity = HealthCheckResult.SEVERITY_CRITICAL;
                status = "过热";
                advice = "温度过高会加速电池老化，请立即停止充电并关闭高耗电应用，待冷却后再使用。";
                score = 30;
            }

            return builder
                    .setSeverity(severity)
                    .setStatus(status)
                    .setValue(String.format("%.1f", tempC))
                    .setUnit("°C")
                    .setDescription("当前电池温度为 " + String.format("%.1f°C", tempC)
                            + "，锂离子电池的理想工作温度区间是 15-35°C。")
                    .setAdvice(advice)
                    .setItemScore(score)
                    .build();
        } catch (Exception e) {
            return new HealthCheckResult.Builder()
                    .setId("battery_temperature")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus("读取失败")
                    .setValue("--")
                    .setUnit("")
                    .setDescription("读取电池温度失败：" + e.getMessage())
                    .setAdvice("请稍后重试。")
                    .setItemScore(55)
                    .build();
        }
    }
}
