package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import com.batteryhealth.app.data.model.HealthCheckResult;

/**
 * 网络连接检测：判断当前网络状态是否良好，用于辅助说明云端同步能力。
 */
public class NetworkHealthChecker implements IHealthChecker {

    @Override
    public String getName() { return "网络连接"; }

    @Override
    public String getCategory() { return HealthCheckResult.CATEGORY_SYSTEM; }

    @Override
    public int getPriority() { return 100; }

    @Override
    public HealthCheckResult check(Context context) {
        try {
            Context appCtx = context.getApplicationContext();
            ConnectivityManager cm = (ConnectivityManager) appCtx
                    .getSystemService(Context.CONNECTIVITY_SERVICE);

            HealthCheckResult.Builder builder = new HealthCheckResult.Builder()
                    .setId("network_health")
                    .setTitle(getName())
                    .setCategory(getCategory());

            if (cm == null) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_INFO)
                        .setStatus("无法读取")
                        .setValue("--")
                        .setUnit("")
                        .setDescription("无法访问系统连接服务。")
                        .setAdvice("请确认系统连接服务正常。")
                        .setItemScore(55)
                        .build();
            }

            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork == null) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_WARNING)
                        .setStatus("未连接")
                        .setValue("离线")
                        .setUnit("")
                        .setDescription("当前设备未连接到任何网络。")
                        .setAdvice("若需在线功能，请接入 Wi-Fi 或移动数据。")
                        .setItemScore(30)
                        .build();
            }

            NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
            if (caps == null) {
                return builder
                        .setSeverity(HealthCheckResult.SEVERITY_WARNING)
                        .setStatus("异常")
                        .setValue("未知")
                        .setUnit("")
                        .setDescription("无法获取当前网络能力信息。")
                        .setAdvice("建议切换网络后重试。")
                        .setItemScore(40)
                        .build();
            }

            boolean hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            boolean isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            boolean isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            boolean isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

            String typeName = isWifi ? "Wi-Fi" : (isCellular ? "移动网络" : "其他网络");
            String quality = hasInternet ? "正常" : "受限";

            int severity;
            String advice;
            int score;
            if (hasInternet) {
                severity = HealthCheckResult.SEVERITY_GOOD;
                advice = isMetered ? "当前网络为按流量计费，请留意移动数据用量。" : "当前网络状态良好。";
                score = 100;
            } else {
                severity = HealthCheckResult.SEVERITY_WARNING;
                advice = "网络已连接但无法访问互联网，请检查路由器或运营商信号。";
                score = 50;
            }

            return builder
                    .setSeverity(severity)
                    .setStatus(quality)
                    .setValue(typeName)
                    .setUnit("")
                    .setDescription("网络类型：" + typeName + "，连接质量：" + quality + "，按流量计费：" + (isMetered ? "是" : "否") + "。")
                    .setAdvice(advice)
                    .setItemScore(score)
                    .build();
        } catch (Exception e) {
            return new HealthCheckResult.Builder()
                    .setId("network_health")
                    .setTitle(getName())
                    .setCategory(getCategory())
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus("读取失败")
                    .setValue("--")
                    .setUnit("")
                    .setDescription("读取网络状态失败：" + e.getMessage())
                    .setAdvice("请稍后重试。")
                    .setItemScore(55)
                    .build();
        }
    }
}
