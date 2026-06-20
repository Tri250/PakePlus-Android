package com.batteryhealth.app.data.model;

/**
 * 应用使用/耗电/卡顿信息
 * 用于 Bugreport 解析后的续航与性能分析展示。
 */
public class AppUsageInfo {

    private String packageName;
    private String appName;
    private long usageTimeMs;
    private float powerMah;
    private int jankCount;

    public AppUsageInfo() {
    }

    public AppUsageInfo(String packageName, String appName, long usageTimeMs, float powerMah, int jankCount) {
        this.packageName = packageName;
        this.appName = appName;
        this.usageTimeMs = usageTimeMs;
        this.powerMah = powerMah;
        this.jankCount = jankCount;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public long getUsageTimeMs() {
        return usageTimeMs;
    }

    public void setUsageTimeMs(long usageTimeMs) {
        this.usageTimeMs = usageTimeMs;
    }

    public float getPowerMah() {
        return powerMah;
    }

    public void setPowerMah(float powerMah) {
        this.powerMah = powerMah;
    }

    public int getJankCount() {
        return jankCount;
    }

    public void setJankCount(int jankCount) {
        this.jankCount = jankCount;
    }
}
