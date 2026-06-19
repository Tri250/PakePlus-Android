package com.batteryhealth.app.utils;

import android.os.Build;

import java.lang.reflect.Method;

/**
 * 反射读取系统属性的兼容封装。优先使用公开 API，无权限时回退反射。
 */
public final class SystemPropertiesCompat {

    private SystemPropertiesCompat() {}

    public static String get(String key) {
        if (key == null) return null;
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method getter = clazz.getMethod("get", String.class);
            Object value = getter.invoke(null, key);
            return value == null ? null : value.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : value;
    }

    public static String getSoC() {
        // 多个常见 sysprop 候选
        String[] keys = {
                "ro.boot.soc",
                "ro.boot.platform",
                "ro.board.platform",
                "ro.hardware.chipname",
                "ro.hardware",
                "ro.chipname",
                "ro.product.cpu.abi"
        };
        for (String key : keys) {
            String value = get(key);
            if (value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }

    public static String getDeviceMarketingName() {
        String[] keys = {
                "ro.product.marketname",
                "ro.product.model",
                "ro.product.brand"
        };
        for (String key : keys) {
            String value = get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return Build.MODEL;
    }
}
