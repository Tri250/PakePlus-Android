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
 * 充电协议检测：判断当前充电方式是否为快充以及充电功率。
 */
public class ChargingProtocolChecker implements IHealthChecker {

    @Override
    public String getName() { return "充电协议"; }

    @Override
    public String getCategory() { return HealthCheckResult.CATEGORY_CHARGING; }

    @Override
    public int getPriority() { return 30; }

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
            int plugged = battery != null ? battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) : -1;
            BatteryDataManager manager = new BatteryDataManager(appCtx);
            com.batteryhealth.app.data.model.BatteryInfo info = manager.getBatteryInfo();
            float powerW = info != null ? info.getChargingPower() : 0f;

            HealthCheckResult.Builder builder = new HealthCheckResult.Builder()
                    .setId("charging_protocol")
                    .setTitle(getName())
                    .setCategory(getCategory());

            String plugType;
            switch (plugged) {
                case BatteryManager.BATTERY_PLUGGED_AC: plugType = "交流电源"; break;
                case BatteryManager.BATTERY_PLUGGED_USB: plugType = "USB 充电"; break;
                case BatteryManager.BATTERY_PLUGGED_WIRELESS: plugType = "无线充电"; break;
                default: plugType = "未充电";
            }

            if (plugged <= 0 || powerW <= 0.1f) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_INFO)
                        .setStatus("未充电")
                        .setValue("--")
                        .setUnit("W")
                        .setDescription("当前未接入充电器，无法判断充电协议。")
                        .setAdvice("使用原装充电器 + 原装数据线可获得最佳充电速度与安全性。")
                        .setItemScore(75)
                        .build();
            }

            // 按功率分级：>=25W 认为是快充，>=65W 视为超快充，<10W 为慢速
            int severity;
            String status;
            String advice;
            int score;
            if (powerW >= 65f) {
                severity = HealthCheckResult.SEVERITY_GOOD;
                status = "超快充";
                advice = "当前处于超快充模式，功率表现优秀。";
                score = 100;
            } else if (powerW >= 25f) {
                severity = HealthCheckResult.SEVERITY_GOOD;
                status = "快充";
                advice = "当前为快充模式，若需更快速度请使用支持更高功率的原装充电组合。";
                score = 90;
            } else if (powerW >= 10f) {
                severity = HealthCheckResult.SEVERITY_WARNING;
                status = "普通充电";
                advice = "当前充电功率偏低，可能是数据线或充电器非原装导致。";
                score = 60;
            } else {
                severity = HealthCheckResult.SEVERITY_WARNING;
                status = "慢速充电";
                advice = "当前充电功率较低，建议更换更高功率的原装充电器。";
                score = 45;
            }

            return builder
                    .setSeverity(severity)
                    .setStatus(status)
                    .setValue(String.format("%.1f", powerW))
                    .setUnit("W")
                    .setDescription("当前接入：" + plugType + "，功率约 " + String.format("%.1f", powerW) + " W。")
                    .setAdvice(advice)
                    .setItemScore(score)
                    .build();
        } catch (Exception e) {
            return new HealthCheckResult.Builder()
                    .setId("charging_protocol")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus("读取失败")
                    .setValue("--")
                    .setUnit("")
                    .setDescription("读取充电数据失败：" + e.getMessage())
                    .setAdvice("请稍后重试。")
                    .setItemScore(55)
                    .build();
        }
    }
}
