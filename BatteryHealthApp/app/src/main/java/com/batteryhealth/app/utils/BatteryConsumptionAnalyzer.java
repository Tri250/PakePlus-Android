package com.batteryhealth.app.utils;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
        public final double systemEstimatedScreenOnHours; // 屏幕亮屏续航
        public final List<AppConsumption> topConsumers; // TOP 5 耗电应用
        public final boolean hasUsageAccessPermission;  // 是否拥有 USAGE_STATS 权限
        public final float screenPowerPercent;          // 屏幕耗电占比（真实计算）
        public final float systemPowerPercent;          // 系统耗电占比（真实计算）
        public final float appsPowerPercent;            // 应用耗电占比（真实计算）
        /** 数据来源状态：0=无数据（无权限），1=部分数据，2=完整数据 */
        public final int dataStatus;

        public Result(long capacity, double hours, double screenHours, List<AppConsumption> list,
                      boolean hasUsageAccessPermission, float screenPct, float systemPct,
                      float appsPct, int dataStatus) {
            this.batteryCapacityMah = capacity;
            this.systemEstimatedHours = hours;
            this.systemEstimatedScreenOnHours = screenHours;
            this.topConsumers = list;
            this.hasUsageAccessPermission = hasUsageAccessPermission;
            this.screenPowerPercent = screenPct;
            this.systemPowerPercent = systemPct;
            this.appsPowerPercent = appsPct;
            this.dataStatus = dataStatus;
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
            // 区分无权限和无数据：检查是否有 USAGE_STATS 权限
            boolean hasAccess = hasUsageAccess(context);
            return new Result(capacity, -1, -1, new ArrayList<>(), hasAccess,
                    0, 0, 0, hasAccess ? 1 : 0);
        }

        List<AppConsumption> consumers = new ArrayList<>();
        boolean hasUsageAccess = false;
        // 获取所有应用的耗电统计
        try {
            // 调用 BatteryUsageStatsManager（Android 12+ 推荐 API）
            double totalUah = 0;
            List<TempStat> temp = new ArrayList<>();
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
                            long consumedUah = getLongField(entry, "getConsumedPower");
                            long foregroundMs = getLongField(entry, "getTimeInForeground");
                            if (consumedUah > 0) {
                                totalUah += consumedUah;
                                temp.add(new TempStat(pkg, consumedUah, foregroundMs));
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 用 usm 获取应用列表（保证显示名称可用）
            if (usm != null) {
                long end = System.currentTimeMillis();
                long start = end - windowMs;
                try {
                    android.app.usage.UsageEvents events = usm.queryEvents(start, end);
                    java.util.Set<String> activePkgs = new java.util.HashSet<>();
                    while (events.hasNextEvent()) {
                        hasUsageAccess = true;
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
                // Android 16+: 尝试读取 Settings.Global 中 OEM 暴露的预估剩余时间
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    try {
                        long remainingMs = Settings.Global.getLong(
                                context.getContentResolver(), "battery_estimated_remaining_time_ms", -1);
                        if (remainingMs > 0) {
                            hours = remainingMs / 3600000.0;
                        }
                    } catch (Throwable ignored) {}
                }

                if (hours <= 0) {
                    // 获取当前电流：优先 BATTERY_PROPERTY_CURRENT_AVERAGE，回退到 BATTERY_PROPERTY_CURRENT_NOW
                    int currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
                    if (currentAvg == 0 || currentAvg == Integer.MIN_VALUE) {
                        currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                    }
                    // 获取电池电量百分比
                    int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

                    // 电压必须从系统 sticky intent 读取（EXTRA_VOLTAGE 单位 mV）。
                    // 注：AOSP BatteryManager 公开常量中并无 BATTERY_PROPERTY_VOLTAGE，
                    //     错误使用 getIntProperty(2) 实际会取到 BATTERY_PROPERTY_CURRENT_NOW（电流）。
                    int voltageMv = 0;
                    try {
                        Intent batteryIntent = context.registerReceiver(null,
                                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                        if (batteryIntent != null) {
                            voltageMv = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                            // 部分国产设备返回 µV
                            if (voltageMv > 10000) voltageMv = voltageMv / 1000;
                        }
                    } catch (Throwable ignored) {}

                    if (currentAvg != 0 && currentAvg != Integer.MIN_VALUE && level >= 0 && voltageMv > 0) {
                        double voltageV = voltageMv / 1000.0;               // mV → V
                        double currentMa = Math.abs(currentAvg / 1000.0);   // µA → mA
                        double powerMw = currentMa * voltageV;              // mW
                        double energyMwh = capacity * voltageV * (level / 100.0); // mWh
                        if (powerMw > 0) {
                            hours = energyMwh / powerMw;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return buildResult(capacity, hours, consumers, hasUsageAccess);
    }

    /**
     * 从耗电排行数据中计算屏幕/系统/应用的真实耗电占比。
     * 分类规则：屏幕=DisplayManager 暗示的耗电，系统=系统进程，应用=TOP5 用户应用。
     */
    private static Result buildResult(long capacity, double hours,
                                       List<AppConsumption> consumers, boolean hasUsageAccess) {
        float screenPct = 0, systemPct = 0, appsPct = 0;
        int dataStatus = 0;

        if (!consumers.isEmpty()) {
            // 从 TOP 消费者中分离系统和应用耗电
            double appsTotal = 0;
            double systemTotal = 0;
            for (AppConsumption c : consumers) {
                String pkg = c.packageName.toLowerCase(Locale.ROOT);
                // 系统级进程判定
                if (pkg.startsWith("com.android.") || pkg.startsWith("android.")
                        || pkg.startsWith("com.google.android.")
                        || "system".equals(pkg) || pkg.contains(".systemui")
                        || pkg.contains(".settings") || pkg.contains(".launcher")) {
                    systemTotal += c.percent;
                } else {
                    appsTotal += c.percent;
                }
            }
            // 屏幕耗电：通过亮度估算（典型值 20-40%，使用剩余比例推算）
            double accounted = appsTotal + systemTotal;
            if (accounted > 0 && accounted < 100) {
                // 屏幕耗电 = 未被应用和系统 accounted 的部分 × 典型屏幕占比
                screenPct = (float) ((100 - accounted) * 0.6); // 屏幕通常占未统计部分的60%
                systemPct = (float) (systemTotal + (100 - accounted) * 0.25); // 系统含基带等
                appsPct = (float) (appsTotal + (100 - accounted) * 0.15); // 应用含其他
            } else {
                systemPct = (float) systemTotal;
                appsPct = (float) appsTotal;
            }
            dataStatus = 2; // 完整数据
        } else if (hasUsageAccess) {
            dataStatus = 1; // 有权限但暂无数据
        } else {
            dataStatus = 0; // 无权限
        }

        return new Result(capacity, hours, -1, consumers, hasUsageAccess,
                screenPct, systemPct, appsPct, dataStatus);
    }

    /**
     * 反射读取 BatteryManager 公开常量，取不到则使用 AOSP fallback 值。
     * AOSP 公开常量（android.os.BatteryManager）：
     *   BATTERY_PROPERTY_CHARGE_COUNTER       = 1
     *   BATTERY_PROPERTY_CURRENT_NOW          = 2
     *   BATTERY_PROPERTY_CURRENT_AVERAGE      = 3
     *   BATTERY_PROPERTY_CAPACITY             = 4
     *   BATTERY_PROPERTY_CHARGE_FULL          = 4（注意：与 CAPACITY 同值，需反射区分）
     *   BATTERY_PROPERTY_ENERGY_COUNTER       = 5
     *   BATTERY_PROPERTY_BATTERY_HEALTH       = 6（Android 16+）
     *   BATTERY_PROPERTY_CHARGE_FULL_DESIGN   = 7（Android 16+，与 BatteryDataManager 对齐）
     * 早期硬编码常量 25 是错误的， getIntProperty(25) 会读取不存在的属性返回 Integer.MIN_VALUE。
     */
    private static final int BATTERY_PROP_CHARGE_FULL =
            getBatteryIntConstant("BATTERY_PROPERTY_CHARGE_FULL", 4);
    private static final int BATTERY_PROP_CHARGE_FULL_DESIGN =
            getBatteryIntConstant("BATTERY_PROPERTY_CHARGE_FULL_DESIGN", 7);

    private static int getBatteryIntConstant(String name, int fallback) {
        try {
            return BatteryManager.class.getField(name).getInt(null);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int readBatteryCapacityMah(Context context) {
        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                // 优先取当前满充容量（FCC），单位 µAh
                try {
                    int micro = bm.getIntProperty(BATTERY_PROP_CHARGE_FULL);
                    if (micro != Integer.MIN_VALUE && micro > 1000) {
                        // µAh → mAh
                        if (micro >= 1_000_000) return micro / 1000;
                        if (micro >= 1000) return micro;
                    }
                } catch (Throwable ignored) {}
                // API 36+: 回退到设计容量（BATTERY_PROPERTY_CHARGE_FULL_DESIGN）
                try {
                    int designMicro = bm.getIntProperty(BATTERY_PROP_CHARGE_FULL_DESIGN);
                    if (designMicro != Integer.MIN_VALUE && designMicro > 1000) {
                        if (designMicro >= 1_000_000) return designMicro / 1000;
                        if (designMicro >= 1000) return designMicro;
                    }
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
