package com.batteryhealth.app.utils;

import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * android.os.SystemProperties 反射访问工具（Android 内部隐藏 API）。
 * 主要用于读取 ro.* 类的只读系统属性。反射调用使用 Throwable 捕获以兼容所有错误场景。
 */
public final class SystemPropertiesCompat {

    private static final String TAG = "SystemPropertiesCompat";
    private static final String SYSTEM_PROPERTIES_CLASS = "android.os.SystemProperties";

    private SystemPropertiesCompat() {
        // utility class
    }

    private static final class ReflectHolder {
        static final Method GET = lookup("get", String.class, String.class);
        static final Method GET_INT = lookup("getInt", String.class, int.class);
        static final Method GET_LONG = lookup("getLong", String.class, long.class);
        static final Method GET_BOOLEAN = lookup("getBoolean", String.class, boolean.class);

        private static Method lookup(String name, Class<?>... paramTypes) {
            try {
                return Class.forName(SYSTEM_PROPERTIES_CLASS).getMethod(name, paramTypes);
            } catch (Throwable t) {
                Log.d(TAG, "SystemProperties." + name + " not available: " + t.getMessage());
                return null;
            }
        }
    }

    public static String get(String key) {
        return get(key, "");
    }

    public static String get(String key, String defaultValue) {
        if (key == null) return defaultValue;
        try {
            Method m = ReflectHolder.GET;
            if (m == null) return defaultValue;
            Object r = m.invoke(null, key, defaultValue);
            return r instanceof String ? (String) r : defaultValue;
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    public static int getInt(String key, int defaultValue) {
        if (key == null) return defaultValue;
        try {
            Method m = ReflectHolder.GET_INT;
            if (m == null) return defaultValue;
            Object r = m.invoke(null, key, defaultValue);
            return r instanceof Integer ? (Integer) r : defaultValue;
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    public static long getLong(String key, long defaultValue) {
        if (key == null) return defaultValue;
        try {
            Method m = ReflectHolder.GET_LONG;
            if (m == null) return defaultValue;
            Object r = m.invoke(null, key, defaultValue);
            return r instanceof Long ? (Long) r : defaultValue;
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        if (key == null) return defaultValue;
        try {
            Method m = ReflectHolder.GET_BOOLEAN;
            if (m == null) return defaultValue;
            Object r = m.invoke(null, key, defaultValue);
            return r instanceof Boolean ? (Boolean) r : defaultValue;
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    /**
     * 读取 SoC 厂商。Build.SOC_MANUFACTURER 在 API 31+ 才可访问。
     * 早期版本会回退到 ro.board.platform / ro.soc.manufacturer / ro.hardware.chipname。
     */
    public static String getSoC() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                String s = Build.SOC_MANUFACTURER;
                if (s != null && !s.isEmpty()) return s;
            } catch (Throwable t) {
                // ignore and fall back
            }
        }
        String[] candidates = {
                "ro.soc.manufacturer",
                "ro.board.platform",
                "ro.hardware.chipname",
                "ro.boot.platform",
                "ro.boot.soc_id",
                "ro.boot.chipname"
        };
        for (String key : candidates) {
            String v = get(key);
            if (v != null && !v.isEmpty() && !"unknown".equalsIgnoreCase(v)) {
                return v;
            }
        }
        return null;
    }
}
