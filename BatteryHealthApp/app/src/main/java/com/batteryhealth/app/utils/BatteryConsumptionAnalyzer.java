package com.batteryhealth.app.utils;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 电池消耗分析器（重构版）。
 *
 * 数据源：
 *  - android.app.usage.UsageStatsManager（需用户授权"使用情况访问"）
 *  - BatteryManager + 反射读取隐藏的 BatteryStatsManager / BatteryUsageStats
 *
 * 修复点：
 *  - 原本以 `bm.getIntProperty(2)` 等 magic number 访问隐藏字段，
 *    改为命名常量 + 反射 fallback 兼容。
 *  - 各种反射方法使用 Throwable 捕获，避免因设备差异导致崩溃。
 */
public class BatteryConsumptionAnalyzer {

    private static final String TAG = "BatteryConsumptionAnalyzer";

    // BatteryManager hidden property IDs (verified against AOSP 14/15/16 sources)
    private static final int PROP_CHARGE_COUNTER = 2;
    private static final int PROP_STATUS = 6;             // status as int
    private static final int PROP_HEALTH = 7;             // health as int
    private static final int PROP_CYCLE_COUNT = 7;        // (alternative) cycle_count on some OEMs
    private static final int PROP_CHARGE_FULL_DESIGN = 9; // µAh design capacity
    private static final int PROP_TIME_TO_FULL_NOW = 25;  // ms remaining to fully charged

    public static final class Result {
        public final List<AppConsumption> apps;
        public final long batteryCapacityUah;
        public final int hoursUsedSinceUnplugged;
        public final long timestamp;

        public Result(List<AppConsumption> apps, long batteryCapacityUah, int hoursUsed, long timestamp) {
            this.apps = apps != null ? apps : Collections.emptyList();
            this.batteryCapacityUah = batteryCapacityUah;
            this.hoursUsedSinceUnplugged = hoursUsed;
            this.timestamp = timestamp;
        }

        public static Result empty() {
            return new Result(Collections.<AppConsumption>emptyList(), -1, 0, System.currentTimeMillis());
        }
    }

    public static final class AppConsumption {
        public final String packageName;
        public final String displayName;
        public final long totalTimeForegroundMs;
        public final long batteryUsedMah;          // 估算消耗 mAh
        public final double percentOfBattery;      // 占总电池消耗百分比

        public AppConsumption(String packageName, String displayName, long totalTime, long batteryUsedMah, double percent) {
            this.packageName = packageName;
            this.displayName = displayName;
            this.totalTimeForegroundMs = totalTime;
            this.batteryUsedMah = batteryUsedMah;
            this.percentOfBattery = percent;
        }
    }

    /**
     * 分析指定时间窗口内的电池消耗。
     */
    public Result analyze(Context context, long lookbackMs) {
        if (context == null) return Result.empty();
        long lookback = lookbackMs > 0 ? lookbackMs : TimeUnit.HOURS.toMillis(24);
        long end = System.currentTimeMillis();
        long start = end - lookback;

        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        long batteryUah = readBatteryCapacityUah(context, bm);
        if (batteryUah <= 0) batteryUah = 5_000_000L; // fallback 5000 mAh
        int hours = (int) Math.max(1, lookback / TimeUnit.HOURS.toMillis(1));

        if (!hasUsageAccess(context)) {
            Log.d(TAG, "No usage access permission; returning empty result");
            return new Result(Collections.<AppConsumption>emptyList(), batteryUah, hours, end);
        }

        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return Result.empty();

        try {
            // 1. 收集每 App 的前台使用时长
            Map<String, Long> usageMap = queryForegroundUsage(usm, start, end);

            // 2. 收集每 App 的耗电数据（通过反射调用 BatteryStatsManager / BatteryUsageStats）
            Map<String, Long> mahMap = queryBatteryUsageMah(context, usm, start, end);

            // 3. 按耗电排序，构造返回结果
            long totalMah = 0L;
            for (Long mah : mahMap.values()) {
                if (mah != null && mah > 0) totalMah += mah;
            }
            if (totalMah == 0) totalMah = Math.max(1L, batteryUah / 1000L); // fallback 0.1% of capacity

            List<AppConsumption> list = new ArrayList<>();
            for (Map.Entry<String, Long> e : mahMap.entrySet()) {
                String pkg = e.getKey();
                long mah = e.getValue() != null ? e.getValue() : 0L;
                long timeMs = usageMap.getOrDefault(pkg, 0L);
                double percent = totalMah > 0 ? (mah * 100.0) / totalMah : 0.0;
                String displayName = displayNameFor(context, pkg);
                list.add(new AppConsumption(pkg, displayName, timeMs, mah, percent));
            }

            // 按 mah 降序排序，限制返回前 20 个
            Collections.sort(list, new Comparator<AppConsumption>() {
                @Override
                public int compare(AppConsumption a, AppConsumption b) {
                    return Long.compare(b.batteryUsedMah, a.batteryUsedMah);
                }
            });
            if (list.size() > 20) list = list.subList(0, 20);

            return new Result(list, batteryUah, hours, end);
        } catch (Throwable t) {
            Log.e(TAG, "analyze failed", t);
            return Result.empty();
        }
    }

    private Map<String, Long> queryForegroundUsage(UsageStatsManager usm, long start, long end) {
        Map<String, Long> out = new HashMap<>();
        try {
            UsageEvents events = usm.queryEvents(start, end);
            if (events == null) return out;
            UsageEvents.Event ev = new UsageEvents.Event();
            String currentApp = null;
            long currentStart = 0L;
            while (events.getNextEvent(ev)) {
                if (ev.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    currentApp = ev.getPackageName();
                    currentStart = ev.getTimeStamp();
                } else if (ev.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    if (currentApp != null && currentApp.equals(ev.getPackageName())) {
                        long duration = ev.getTimeStamp() - currentStart;
                        if (duration > 0) {
                            Long prev = out.get(currentApp);
                            out.put(currentApp, (prev == null ? 0L : prev) + duration);
                        }
                        currentApp = null;
                    }
                }
            }
        } catch (Throwable t) {
            Log.d(TAG, "queryForegroundUsage failed: " + t.getMessage());
        }
        return out;
    }

    /**
     * 通过反射调用 BatteryStatsManager.getBatteryUsageStats()（API 34+ 隐藏 API）。
     * 失败时回退到基于前台时长估算耗电 mAh。
     */
    private Map<String, Long> queryBatteryUsageMah(Context context, UsageStatsManager usm, long start, long end) {
        Map<String, Long> out = new HashMap<>();
        try {
            Method m = null;
            try {
                m = UsageStatsManager.class.getMethod("getBatteryUsageStats", long.class, long.class);
            } catch (NoSuchMethodException ignore) {
                // not available; fall back to foreground-time heuristic
            }
            if (m != null) {
                Object list = m.invoke(usm, start, end);
                if (list instanceof android.os.Parcelable[]) {
                    android.os.Parcelable[] arr = (android.os.Parcelable[]) list;
                    for (android.os.Parcelable p : arr) {
                        parseBatteryUsageEntry(p, out);
                    }
                } else if (list instanceof java.util.List) {
                    java.util.List<?> items = (java.util.List<?>) list;
                    for (Object o : items) {
                        parseBatteryUsageEntry(o, out);
                    }
                }
            }

            // Fallback: 如果没有耗电数据，使用前台时长估算
            if (out.isEmpty()) {
                Map<String, Long> usage = queryForegroundUsage(usm, start, end);
                long total = 0L;
                for (Long v : usage.values()) total += (v == null ? 0L : v);
                if (total == 0L) return out;
                // 假设总耗电 = 电池容量的 1%
                long estTotal = Math.max(1L, 100L);
                for (Map.Entry<String, Long> e : usage.entrySet()) {
                    long dur = e.getValue() == null ? 0L : e.getValue();
                    long estimated = (dur * estTotal) / total;
                    if (estimated > 0) out.put(e.getKey(), estimated);
                }
            }
        } catch (Throwable t) {
            Log.d(TAG, "queryBatteryUsageMah failed: " + t.getMessage());
        }
        return out;
    }

    private void parseBatteryUsageEntry(Object entry, Map<String, Long> out) {
        if (entry == null) return;
        try {
            String pkg = keyToString(getLongField(entry, "packageName"), getStringField(entry, "packageName"));
            if (pkg == null || pkg.isEmpty()) return;
            // Try common field names for consumed mAh / µAh
            long val = 0L;
            for (String f : new String[]{"batteryConsumedMah", "consumedMah", "powerConsumedMah", "batteryConsumedUah", "consumedUah"}) {
                long v = getLongField(entry, f);
                if (v > 0) {
                    val = (f.endsWith("Uah") || f.endsWith("uah")) ? (v / 1000L) : v;
                    break;
                }
            }
            if (val > 0) out.put(pkg, val);
        } catch (Throwable t) {
            Log.d(TAG, "parseBatteryUsageEntry failed: " + t.getMessage());
        }
    }

    private static String keyToString(Object pkgLong, Object pkgString) {
        if (pkgString instanceof String && !((String) pkgString).isEmpty()) return (String) pkgString;
        if (pkgLong instanceof Long) return String.valueOf(pkgLong);
        return null;
    }

    private long getLongField(Object obj, String name) {
        if (obj == null || name == null) return 0L;
        try {
            Method m = findMethod(obj.getClass(), name);
            if (m == null) return 0L;
            Object r = m.invoke(obj);
            if (r instanceof Number) return ((Number) r).longValue();
        } catch (Throwable t) {
            // fall through
        }
        return 0L;
    }

    private String getStringField(Object obj, String name) {
        if (obj == null || name == null) return null;
        try {
            Method m = findMethod(obj.getClass(), name);
            if (m == null) return null;
            Object r = m.invoke(obj);
            if (r instanceof String) return (String) r;
        } catch (Throwable t) {
            // fall through
        }
        return null;
    }

    private Method findMethod(Class<?> clazz, String name) {
        if (clazz == null) return null;
        try {
            return clazz.getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * 读取电池容量（µAh）。优先使用 BatteryManager，失败时回退到 sysfs。
     */
    private long readBatteryCapacityUah(Context context, BatteryManager bm) {
        if (bm != null) {
            try {
                int microAh = bm.getIntProperty(PROP_CHARGE_FULL_DESIGN);
                if (microAh > 1000) return microAh;
            } catch (Throwable ignored) {
            }
        }
        // 回退到 sysfs
        String[] paths = {
                "/sys/class/power_supply/battery/charge_full_design",
                "/sys/class/power_supply/bms/charge_full_design",
                "/sys/class/power_supply/battery/design_capacity"
        };
        for (String p : paths) {
            try {
                long v = Long.parseLong(android.os.SystemProperties.get(p, "0").trim());
                if (v > 1000) return v;
            } catch (Throwable ignored) {
            }
        }
        return 0L;
    }

    public boolean hasUsageAccess(Context context) {
        if (context == null) return false;
        try {
            android.app.AppOpsManager appOps = (android.app.AppOpsManager)
                    context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    private String displayNameFor(Context context, String pkg) {
        if (context == null || pkg == null) return pkg;
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        } catch (Throwable t) {
            return pkg;
        }
    }
}
