package com.batteryhealth.app;

/**
 * BuildConfig 包装类，避免业务代码直接依赖 BuildConfig.DEBUG。
 */
public class BuildConfigHelper {

    public static boolean isDebugMode() {
        return BuildConfig.DEBUG;
    }
}
