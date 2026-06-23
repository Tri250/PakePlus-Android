package com.batteryhealth.app.utils;

import android.util.Log;

import com.batteryhealth.app.BuildConfig;

public final class LogHelper {

    private static final String TAG_PREFIX = "BatteryHealth_";
    private static final boolean DEBUG_ENABLED = BuildConfig.DEBUG;

    private LogHelper() {}

    public static void d(String tag, String message) {
        if (DEBUG_ENABLED) {
            Log.d(TAG_PREFIX + tag, message);
        }
    }

    public static void d(String tag, String message, Throwable throwable) {
        if (DEBUG_ENABLED) {
            Log.d(TAG_PREFIX + tag, message, throwable);
        }
    }

    public static void i(String tag, String message) {
        Log.i(TAG_PREFIX + tag, message);
    }

    public static void i(String tag, String message, Throwable throwable) {
        Log.i(TAG_PREFIX + tag, message, throwable);
    }

    public static void w(String tag, String message) {
        Log.w(TAG_PREFIX + tag, message);
    }

    public static void w(String tag, String message, Throwable throwable) {
        Log.w(TAG_PREFIX + tag, message, throwable);
    }

    public static void e(String tag, String message) {
        Log.e(TAG_PREFIX + tag, message);
    }

    public static void e(String tag, String message, Throwable throwable) {
        Log.e(TAG_PREFIX + tag, message, throwable);
    }

    public static void wtf(String tag, String message) {
        Log.wtf(TAG_PREFIX + tag, message);
    }

    public static void wtf(String tag, String message, Throwable throwable) {
        Log.wtf(TAG_PREFIX + tag, message, throwable);
    }
}