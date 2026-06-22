package com.batteryhealth.app.utils;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 电池消耗排行：通过 BatteryStatsManager 获取真实耗电数据，
 * 不需要 root 权限（自 Android 8+ 起对应用自身数据开放）。
 *
 * 注意：android.app.usage.BatteryStatsManager 与 android.os.BatteryStatsManager
 * 均被 SDK 标记为 hide，需通过 Context.getSystemService("batterystats") 字符串形式获取
 * 并通过反射调用其方法。
 */
public class BatteryConsumptionAnalyzer {

    private static final String TAG = "BatteryConsumptionAnalyzer";
    /** 对应隐藏常量 Context.BATTERY_STATS_SERVICE = "batterystats" */
    private static final String BATTERY_STATS_SERVICE = "batterystats";

    public static final class AppConsumption {
        public final String packageName;
        public final String displayName;
        public final double percent;        // 占总耗电百分比
        public final long totalMahConsumed;  // 估算耗电 mAh
        public final long foregroundTimeMs; // 前台时长

        public AppConsumption(String packageName, String displayName, double percent,
                              long totalMahConsumed, long foregroundTimeMs) {
            this.packageName = packageName;
            this.displayName = displayName;
            this.percent = percent;
            this.totalMahConsumed = totalMahConsumed;
            this.foregroundTimeMs = foregroundTimeMs;
        }
    }

    public static final class Result {
        public final long batteryCapacityMah;
        public final double systemEstimatedHours;     // 设备预估续航（小时）
        public final double systemEstimatedScreenOnHours; // 屏幕亮屏续航（小时）
        public final List<AppConsumption> topConsumers; // TOP 5 耗电应用
        public final boolean hasUsageAccessPermission;  // 是否拥有 USAGE_STATS 权限
        public final long screenOnTimeMs;               // 统计窗口内真实亮屏时间
        public final float screenPowerPercent;          // 屏幕耗电占比估算
        public final float systemPowerPercent;          // 系统耗电占比估算
        public final float appsPowerPercent;            // 应用耗电占比估算

        public Result(long capacity, double hours, double screenHours, List<AppConsumption> list,
                      boolean hasUsageAccessPermission, long screenOnTimeMs,
                      float screenPowerPercent, float systemPowerPercent, float appsPowerPercent) {
            this.batteryCapacityMah = capacity;
            this.systemEstimatedHours = hours;
            this.systemEstimatedScreenOnHours = screenHours;
            this.topConsumers = list;
            this.hasUsageAccessPermission = hasUsageAccessPermission;
            this.screenOnTimeMs = screenOnTimeMs;
            this.screenPowerPercent = screenPowerPercent;
            this.systemPowerPercent = systemPowerPercent;
            this.appsPowerPercent = appsPowerPercent;
        }
    }

    /**
     * 分析过去 N 毫秒内应用的耗电情况。
     * @param context Context
     * @param windowMs 统计窗口（默认 24 小时）
     */
    public static Result analyze(Context context, long windowMs) {
        long capacity = readBatteryCapacityMah(context);
        long end = System.currentTimeMillis();
        long start = end - windowMs;

        // 真实权限与亮屏时间（不依赖 BatteryStats）
        boolean hasUsageAccess = hasUsageAccess(context);
        long screenOnTimeMs = queryScreenOnTimeMs(context, start, end, hasUsageAccess);

        // 通过字符串 "batterystats" 获取隐藏的 BatteryStatsManager 服务
        Object bsm = null;
        try {
            bsm = context.getSystemService(BATTERY_STATS_SERVICE);
        } catch (Throwable ignored) {
            bsm = null;
        }
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

        double totalUah = 0;
        List<TempStat> temp = new ArrayList<>();
        List<AppConsumption> consumers = new ArrayList<>();

        if (bsm != null) {
            try {
                Object bus = null;

                // 首先尝试现有的 getBatteryUsageStats() 无参方法
                try {
                    java.lang.reflect.Method m = bsm.getClass().getMethod("getBatteryUsageStats");
                    bus = m.invoke(bsm);
                } catch (Throwable ignored) {}

                // Android 16: 尝试 getBatteryUsageStats(int) 带 USER_WORKSPACE 参数
                if (bus == null) {
                    try {
                        java.lang.reflect.Method m = bsm.getClass().getMethod("getBatteryUsageStats", int.class);
                        bus = m.invoke(bsm, 0); // USER_WORKSPACE = 0
                    } catch (Throwable ignored) {}
                }

                // Android 16: 尝试 getBatteryUsageStatsForUsers()
                if (bus == null) {
                    try {
                        java.lang.reflect.Method m = bsm.getClass().getMethod("getBatteryUsageStatsForUsers");
                        bus = m.invoke(bsm);
                    } catch (Throwable ignored) {}
                }

                if (bus != null) {
                    try {
                        java.lang.reflect.Method getStats = bus.getClass().getMethod("getStats");
                        java.util.Map<?, ?> statsMap = (java.util.Map<?, ?>) getStats.invoke(bus);
                        if (statsMap != null) {
                            for (Object key : statsMap.keySet()) {
                                Object entry = statsMap.get(key);
                                if (entry == null) continue;
                                String pkg = keyToString(key);
                                double consumedUah = getDoubleField(entry, "getConsumedPower");
                                long foregroundMs = getLongField(entry, "getTimeInForeground");
                                if (consumedUah > 0) {
                                    totalUah += consumedUah;
                                    temp.add(new TempStat(pkg, consumedUah, foregroundMs));
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                // 用 usm 获取应用列表（保证显示名称可用），仅在真正拥有权限时执行
                if (usm != null && hasUsageAccess) {
                    try {
                        android.app.usage.UsageEvents events = usm.queryEvents(start, end);
                        java.util.Set<String> activePkgs = new java.util.HashSet<>();
                        while (events.hasNextEvent()) {
                            android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
                            events.getNextEvent(e);
                            if (e.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                                activePkgs.add(e.getPackageName());
                            }
                        }
                        for (String p : activePkgs) {
                            boolean found = false;
                            for (TempStat t : temp) if (t.pkg.equals(p)) { found = true; break; }
                            if (!found) {
                                temp.add(new TempStat(p, 0, 0));
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // 排序、计算百分比
                Collections.sort(temp, (a, b) -> Double.compare(b.consumedUah, a.consumedUah));
                int top = Math.min(5, temp.size());
                PackageManager pm = context.getPackageManager();
                for (int i = 0; i < top; i++) {
                    TempStat t = temp.get(i);
                    String display = t.pkg;
                    try {
                        ApplicationInfo info = pm.getApplicationInfo(t.pkg, 0);
                        display = pm.getApplicationLabel(info).toString();
                    } catch (Exception ignored) {}
                    double percent = totalUah > 0 ? (t.consumedUah / totalUah) * 100.0 : 0;
                    long mah = (long) (t.consumedUah / 1000.0);
                    consumers.add(new AppConsumption(t.pkg, display, percent, mah, t.foregroundMs));
                }
            } catch (Exception e) {
                Log.w(TAG, "analyze failed: " + e.getMessage());
            }
        }

        // 系统预估续航（基于当前耗电速率）
        double hours = estimateRemainingHours(context, capacity);

        // 亮屏续航：按统计窗口内亮屏比例折算
        double screenOnHours = -1;
        if (hours > 0 && screenOnTimeMs > 0 && windowMs > 0) {
            double screenRatio = (double) screenOnTimeMs / windowMs;
            screenOnHours = hours / Math.max(0.01, screenRatio);
        }

        // 耗电分布：应用部分来自真实 BatteryStats 采样，屏幕部分来自真实亮屏时间比例
        float appsPowerPercent = 0f;
        float screenPowerPercent = 0f;
        float systemPowerPercent = 0f;
        if (totalUah > 0) {
            double appsUah = 0;
            for (TempStat t : temp) {
                appsUah += t.consumedUah;
            }
            appsPowerPercent = (float) Math.min(100.0, appsUah * 100.0 / totalUah);

            if (screenOnTimeMs > 0 && windowMs > 0) {
                double screenRatio = (double) screenOnTimeMs / windowMs;
                // 屏幕在亮屏期间约占整机功耗的 35%~55%，按亮屏比例折算到整个窗口
                screenPowerPercent = (float) Math.min(100.0 - appsPowerPercent, screenRatio * 45.0);
            }
            systemPowerPercent = Math.max(0f, 100f - appsPowerPercent - screenPowerPercent);
        } else if (screenOnTimeMs > 0 && windowMs > 0) {
            // 无 BatteryStats 时，仅按真实亮屏比例估算屏幕与系统占比
            double screenRatio = (double) screenOnTimeMs / windowMs;
            screenPowerPercent = (float) Math.min(100.0, screenRatio * 45.0);
            systemPowerPercent = Math.max(0f, 100f - screenPowerPercent);
        }

        return new Result(capacity, hours, screenOnHours, consumers, hasUsageAccess,
                screenOnTimeMs, screenPowerPercent, systemPowerPercent, appsPowerPercent);
    }

    /**
     * 基于当前电流、电压与容量估算剩余续航时间（小时）。
     */
    private static double estimateRemainingHours(Context context, long capacityMah) {
        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm == null || capacityMah <= 0) return -1;

            // Android 16+: 优先使用 OEM 暴露的预估剩余时间
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                try {
                    long remainingMs = Settings.Global.getLong(
                            context.getContentResolver(), "battery_estimated_remaining_time_ms", -1);
                    if (remainingMs > 0) return remainingMs / 3600000.0;
                } catch (Throwable ignored) {}
            }

            int currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
            if (currentAvg == 0 || currentAvg == Integer.MIN_VALUE) {
                currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            }
            int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            int voltageMicroV = 0;
            try {
                voltageMicroV = bm.getIntProperty(2); // BATTERY_PROPERTY_VOLTAGE
            } catch (Throwable ignored) {}

            if (currentAvg != 0 && currentAvg != Integer.MIN_VALUE && level >= 0) {
                double voltageV = voltageMicroV > 0 ? voltageMicroV / 1_000_000.0 : 3.8;
                double currentMa = Math.abs(currentAvg / 1000.0);
                double powerMw = currentMa * voltageV;
                double energyMwh = capacityMah * voltageV * (level / 100.0);
                if (powerMw > 0) return energyMwh / powerMw;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    /**
     * 通过 UsageStatsManager 查询真实屏幕亮屏时间。
     * 需要 PACKAGE_USAGE_STATS 权限，无权限时返回 0。
     */
    private static long queryScreenOnTimeMs(Context context, long start, long end, boolean hasPermission) {
        if (!hasPermission) return 0;
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return 0;
            long total = 0;
            java.util.Map<String, android.app.usage.UsageStats> stats = usm.queryAndAggregateUsageStats(start, end);
            if (stats != null) {
                for (android.app.usage.UsageStats s : stats.values()) {
                    total += s.getTotalTimeInForeground();
                }
            }
            return total;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int readBatteryCapacityMah(Context context) {
        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int micro = bm.getIntProperty(25); // BATTERY_PROPERTY_CHARGE_FULL
                if (micro > 1000) return micro / 1000;
                // API 36+: 尝试 BATTERY_PROPERTY_CHARGE_FULL_DESIGN (9) 作为回退
                try {
                    int designMicro = bm.getIntProperty(9);
                    if (designMicro > 1000) return designMicro / 1000;
                } catch (Throwable ignored) {}
            }
        } catch (Exception ignored) {}
        return -1;
    }

    /**
     * 检查应用是否拥有 PACKAGE_USAGE_STATS 权限。
     * UI 可据此提示用户授权以获取更准确的耗电排行。
     */
    public static boolean hasUsageAccess(Context context) {
        try {
            android.app.AppOpsManager appOps = (android.app.AppOpsManager)
                    context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps != null) {
                int mode = appOps.checkOpNoThrow(
                        "android:get_usage_stats",
                        android.os.Process.myUid(),
                        context.getPackageName());
                return mode == android.app.AppOpsManager.MODE_ALLOWED;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static String keyToString(Object key) {
        if (key == null) return "";
        try {
            java.lang.reflect.Method m = key.getClass().getMethod("getPackageName");
            Object v = m.invoke(key);
            return v != null ? v.toString() : key.toString();
        } catch (Exception ignored) {
            return key.toString();
        }
    }

    private static long getLongField(Object o, String methodName) {
        try {
            java.lang.reflect.Method m = o.getClass().getMethod(methodName);
            Object v = m.invoke(o);
            if (v instanceof Number) return ((Number) v).longValue();
        } catch (Exception ignored) {}
        return 0;
    }

    private static double getDoubleField(Object o, String methodName) {
        try {
            java.lang.reflect.Method m = o.getClass().getMethod(methodName);
            Object v = m.invoke(o);
            if (v instanceof Number) return ((Number) v).doubleValue();
        } catch (Exception ignored) {}
        return 0;
    }

    private static class TempStat {
        final String pkg;
        final double consumedUah;
        final long foregroundMs;
        TempStat(String pkg, double consumedUah, long foregroundMs) {
            this.pkg = pkg;
            this.consumedUah = consumedUah;
            this.foregroundMs = foregroundMs;
        }
    }

    public static String formatConsumption(List<AppConsumption> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            AppConsumption c = list.get(i);
            sb.append(String.format(Locale.getDefault(),
                    "%d. %s %.1f%% · %d mAh", i + 1, c.displayName, c.percent, c.totalMahConsumed));
            if (i < list.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }
}
