package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

import com.batteryhealth.app.data.model.HealthCheckResult;

import java.lang.reflect.Method;

/**
 * WiFi检测
 * 检测WiFi状态，未连接时开启会增加耗电
 */
public class WifiChecker implements IHealthChecker {

    private static final String NAME = "WiFi 状态";
    private static final String CATEGORY = HealthCheckResult.CATEGORY_SYSTEM;
    private static final int PRIORITY = 70;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public HealthCheckResult check(Context context) {
        HealthCheckResult.Builder builder = new HealthCheckResult.Builder()
                .setId("wifi_status")
                .setTitle(NAME)
                .setCategory(CATEGORY);

        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                builder.setStatus("无法检测");
                builder.setSeverity(HealthCheckResult.SEVERITY_INFO);
                builder.setItemScore(70);
                builder.setDescription("无法获取 WiFi 服务。");
                builder.setAdvice("您可以在系统设置中手动检查 WiFi 状态。");
                return builder.build();
            }

            boolean isWifiEnabled = wifiManager.isWifiEnabled();
            boolean isConnected = false;
            String ssid = "";

            try {
                ConnectivityManager cm = (ConnectivityManager)
                        context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                    if (activeNetwork != null && activeNetwork.isConnected()) {
                        isConnected = activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
                        if (isConnected) {
                            android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                            if (wifiInfo != null) {
                                ssid = wifiInfo.getSSID();
                                if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                                    ssid = ssid.substring(1, ssid.length() - 1);
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            if (isConnected) {
                builder.setStatus("已连接");
                builder.setSeverity(HealthCheckResult.SEVERITY_GOOD);
                builder.setItemScore(90);
                builder.setDescription("WiFi 已连接到 " + (ssid != null && !ssid.isEmpty() ? ssid : "未知网络") +
                        "。使用 WiFi 上网比移动数据更省电。");
                builder.setAdvice("使用 WiFi 上网可节省移动数据和电量。");
                builder.setValue("连接中");
            } else if (isWifiEnabled) {
                builder.setStatus("已开启(未连接)");
                builder.setSeverity(HealthCheckResult.SEVERITY_WARNING);
                builder.setItemScore(65);
                builder.setDescription("WiFi 已开启但未连接网络。未连接时 WiFi 仍会扫描网络，消耗一定电量。");
                builder.setAdvice("如果附近没有可用的 WiFi 网络，建议关闭 WiFi 以节省电量。");
                builder.setValue("未连接");
                builder.setRepairable(true);
                builder.setFixAction(HealthCheckResult.FIX_ACTION_WIFI_SETTINGS);
            } else {
                builder.setStatus("已关闭");
                builder.setSeverity(HealthCheckResult.SEVERITY_GOOD);
                builder.setItemScore(100);
                builder.setDescription("WiFi 当前处于关闭状态。");
                builder.setAdvice("不需要时保持 WiFi 关闭可延长续航。");
                builder.setValue("关闭");
            }
        } catch (Exception e) {
            builder.setStatus("无法检测");
            builder.setSeverity(HealthCheckResult.SEVERITY_INFO);
            builder.setItemScore(70);
            builder.setDescription("无法检测 WiFi 状态。");
            builder.setAdvice("您可以在系统设置中手动检查 WiFi 状态。");
        }

        return builder.build();
    }
}
