package com.batteryhealth.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URL;

/**
 * 网络配置
 * 管理后端服务地址。无默认地址，必须在使用前由用户或配置明确设置，避免模拟/空跑。
 */
public class NetworkConfig {
    private static final String PREFS_NAME = "network_config";
    private static final String KEY_BASE_URL = "base_url";

    /**
     * 获取当前配置的后端地址。若未配置则返回空字符串。
     */
    public static String getBaseUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String url = prefs.getString(KEY_BASE_URL, "");
        return url != null ? url : "";
    }

    /**
     * 设置后端地址。仅接受合法的 http / https URL；非法输入会被忽略（保持原值或空）。
     *
     * @return 是否成功保存
     */
    public static boolean setBaseUrl(Context context, String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_BASE_URL, "")
                    .apply();
            return true;
        }
        baseUrl = baseUrl.trim();
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        if (!isValidHttpUrl(baseUrl)) {
            return false;
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, baseUrl)
                .apply();
        return true;
    }

    /**
     * 后端地址是否已配置且合法。
     */
    public static boolean isBaseUrlConfigured(Context context) {
        String url = getBaseUrl(context);
        return !url.isEmpty() && isValidHttpUrl(url);
    }

    private static boolean isValidHttpUrl(String url) {
        try {
            URL u = new URL(url);
            String protocol = u.getProtocol();
            return ("http".equals(protocol) || "https".equals(protocol))
                    && u.getHost() != null && !u.getHost().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
