package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;

import com.batteryhealth.app.data.model.HealthCheckResult;

/**
 * 移动网络检测
 * 检测移动网络类型和信号强度，弱信号会增加耗电
 */
public class MobileNetworkChecker implements IHealthChecker {

    private static final String NAME = "移动网络";
    private static final String CATEGORY = HealthCheckResult.CATEGORY_SYSTEM;
    private static final int PRIORITY = 75;

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
                .setId("mobile_network")
                .setTitle(NAME)
                .setCategory(CATEGORY);

        try {
            TelephonyManager telephonyManager = (TelephonyManager)
                    context.getSystemService(Context.TELEPHONY_SERVICE);
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (telephonyManager == null || cm == null) {
                builder.setStatus("无法检测");
                builder.setSeverity(HealthCheckResult.SEVERITY_INFO);
                builder.setItemScore(70);
                builder.setDescription("无法获取移动网络信息。");
                builder.setAdvice("您可以在系统设置中查看移动网络状态。");
                return builder.build();
            }

            NetworkInfo mobileInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            boolean isMobileConnected = mobileInfo != null && mobileInfo.isConnected();

            int networkType = telephonyManager.getNetworkType();
            String networkTypeName = getNetworkTypeName(networkType);

            int signalLevel = -1;
            try {
                signalLevel = telephonyManager.getSignalStrength().getLevel();
            } catch (Exception ignored) {}

            if (!isMobileConnected) {
                builder.setStatus("未连接");
                builder.setSeverity(HealthCheckResult.SEVERITY_GOOD);
                builder.setItemScore(85);
                builder.setDescription("移动数据未开启，使用 WiFi 或处于离线状态。");
                builder.setAdvice("使用 WiFi 上网比移动数据更省电。");
                builder.setValue("未使用");
            } else {
                builder.setValue(networkTypeName);

                if (signalLevel >= 3) {
                    builder.setStatus("信号良好");
                    builder.setSeverity(HealthCheckResult.SEVERITY_GOOD);
                    builder.setItemScore(90);
                    builder.setDescription(String.format("当前使用 %s 网络，信号强度良好。", networkTypeName));
                    builder.setAdvice("信号良好时移动网络耗电相对较低。");
                } else if (signalLevel >= 1) {
                    builder.setStatus("信号一般");
                    builder.setSeverity(HealthCheckResult.SEVERITY_INFO);
                    builder.setItemScore(75);
                    builder.setDescription(String.format("当前使用 %s 网络，信号强度一般。弱信号会增加手机搜索网络的耗电。",
                            networkTypeName));
                    builder.setAdvice("信号较弱时建议尽量使用 WiFi。");
                } else {
                    builder.setStatus("信号较弱");
                    builder.setSeverity(HealthCheckResult.SEVERITY_WARNING);
                    builder.setItemScore(60);
                    builder.setDescription(String.format("当前使用 %s 网络，信号强度较弱。弱信号下手机会频繁搜索基站，显著增加耗电。",
                            networkTypeName));
                    builder.setAdvice("建议在信号差的地方切换到 WiFi 或开启飞行模式。");
                    builder.setRepairable(true);
                    builder.setFixAction(HealthCheckResult.FIX_ACTION_NETWORK_SETTINGS);
                }
            }
        } catch (Exception e) {
            builder.setStatus("无法检测");
            builder.setSeverity(HealthCheckResult.SEVERITY_INFO);
            builder.setItemScore(70);
            builder.setDescription("无法检测移动网络状态。");
            builder.setAdvice("您可以在系统设置中手动检查移动网络状态。");
        }

        return builder.build();
    }

    private String getNetworkTypeName(int type) {
        switch (type) {
            case 20:
                return "5G";
            case 13:
                return "4G LTE";
            case 12:
                return "4G LTE";
            case 11:
            case 10:
            case 8:
                return "3G HSPA";
            case 9:
                return "3G UMTS";
            case 2:
                return "2G EDGE";
            case 1:
            case 16:
                return "2G GSM";
            default:
                return "未知";
        }
    }
}
