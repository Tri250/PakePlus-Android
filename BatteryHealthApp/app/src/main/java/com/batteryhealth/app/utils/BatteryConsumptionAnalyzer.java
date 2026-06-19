package com.batteryhealth.app.utils;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
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
        public final double systemEstimatedScreenOnHours; // 屏幕亮屏续航
        public final List<AppConsumption> topConsumers; // TOP 5 耗电应用

        public Result(long capacity, double hours, double screenHours, List<AppConsumption> list) {
            this.batteryCapacityMah = capacity;
            this.systemEstimatedHours = hours;
            this.systemEstimatedScreenOnHours = screenHours;
            this.topConsumers = list;
        }
    }

    /**
     * 分析过去 N 毫秒内应用的耗电情况。
     * @param context Context
     * @param windowMs 统计窗口（默认 24 小时）
     */
    public static Result analyze(Context context, long windowMs) {
        long capacity = readBatteryCapacityMah(context);
        // 通过字符串 "batterystats" 获取隐藏的 BatteryStatsManager 服务
        Object bsm = null;
        try {
            bsm = context.getSystemService(BATTERY_STATS_SERVICE);
        } catch (Throwable ignored) {
            bsm = null;
        }
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (bsm == null) {
            return new Result(capacity, -1, -1, new ArrayList<>());
        }

        List<AppConsumption> consumers = new ArrayList<>();
        // 获取所有应用的耗电统计
        try {
            // 调用 BatteryUsageStatsManager（Android 12+ 推荐 API）
            double totalUah = 0;
            List<TempStat> temp = new ArrayList<>();
            try {
                java.lang.reflect.Method m = bsm.getClass().getMethod("getBatteryUsageStats");
                Object bus = m.invoke(bsm);
                if (bus != null) {
                    java.lang.reflect.Method getStats = bus.getClass().getMethod("getStats");
                    java.util.Map<?, ?> statsMap = (java.util.Map<?, ?>) getStats.invoke(bus);
                    if (statsMap != null) {
                        for (Object key : statsMap.keySet()) {
                            Object entry = statsMap.get(key);
                            if (entry == null) continue;
                            String pkg = keyToString(key);
                            long consumedUah = getLongField(entry, "getConsumedPower");
                            long foregroundMs = getLongField(entry, "getTimeInForeground");
                            if (consumedUah > 0) {
                                totalUah += consumedUah;
                                temp.add(new TempStat(pkg, consumedUah, foregroundMs));
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
                // 旧设备无此 API，回退到 usage list
            }

            // 用 usm 获取应用列表（保证显示名称可用）
            if (usm != null) {
                long end = System.currentTimeMillis();
                long start = end - windowMs;
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
            Collections.sort(temp, (a, b) -> Long.compare(b.consumedUah, a.consumedUah));
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
                long mah = t.consumedUah / 1000;
                consumers.add(new AppConsumption(t.pkg, display, percent, mah, t.foregroundMs));
            }
        } catch (Exception e) {
            Log.w(TAG, "analyze failed: " + e.getMessage());
        }

        // 系统预估续航（基于当前耗电速率）
        double hours = -1;
        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null && capacity > 0) {
                int currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
                if (currentAvg > 0 && capacity > 0) {
                    hours = (capacity * 1000.0) / currentAvg;
                }
            }
        } catch (Exception ignored) {}

        return new Result(capacity, hours, -1, consumers);
    }

    private static int readBatteryCapacityMah(Context context) {
        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int micro = bm.getIntProperty(25); // BATTERY_PROPERTY_CHARGE_FULL
                if (micro > 1000) return micro / 1000;
            }
        } catch (Exception ignored) {}
        return -1;
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

    private static class TempStat {
        final String pkg;
        final long consumedUah;
        final long foregroundMs;
        TempStat(String pkg, long consumedUah, long foregroundMs) {
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
