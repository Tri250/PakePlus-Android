package com.batteryhealth.app.utils;

import android.os.Build;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反射读取系统属性的兼容封装。优先使用公开 API，无权限时回退反射。
 *
 * 优化：Class.forName 与 getMethod 的结果在运行期不变，缓存为静态常量，
 * 避免每次调用都重复反射查找（BatteryOriginDetector 等会调用数十次）。
 */
public final class SystemPropertiesCompat {

    private SystemPropertiesCompat() {}

    /** 缓存反射获取的 SystemProperties 类与 get(String) 方法，查找失败则置 null。 */
    private static final Class<?> SYS_PROP_CLAZZ;
    private static final Method SYS_PROP_GET;
    static {
        Class<?> clazz = null;
        Method getter = null;
        try {
            clazz = Class.forName("android.os.SystemProperties");
            getter = clazz.getMethod("get", String.class);
        } catch (Throwable ignored) {
            // 某些受限环境无法反射 SystemProperties，后续 get() 直接返回 null
        }
        SYS_PROP_CLAZZ = clazz;
        SYS_PROP_GET = getter;
    }

    /** 已读取属性值的进程级缓存，避免对同一 key 重复反射调用。 */
    private static final ConcurrentHashMap<String, String> VALUE_CACHE = new ConcurrentHashMap<>();

    public static String get(String key) {
        if (key == null) return null;
        // 系统属性在运行期基本不变，命中缓存直接返回
        String cached = VALUE_CACHE.get(key);
        if (cached != null) return cached;

        String value = getUncached(key);
        if (value != null) {
            VALUE_CACHE.put(key, value);
        }
        return value;
    }

    private static String getUncached(String key) {
        if (SYS_PROP_GET == null) return null;
        try {
            Object result = SYS_PROP_GET.invoke(null, key);
            return result == null ? null : result.toString();
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
