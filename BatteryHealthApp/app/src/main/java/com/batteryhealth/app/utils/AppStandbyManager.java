package com.batteryhealth.app.utils;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import com.batteryhealth.app.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppStandbyManager {

    public static final int BUCKET_ACTIVE = 10;
    public static final int BUCKET_WORKING_SET = 20;
    public static final int BUCKET_FREQUENT = 30;
    public static final int BUCKET_RARE = 40;
    public static final int BUCKET_RESTRICTED = 45;
    public static final int BUCKET_NEVER = 50;
    public static final int BUCKET_UNKNOWN = -1;

    private final Context context;

    public AppStandbyManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public int getAppStandbyBucket(String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return BUCKET_UNKNOWN;
        }
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return BUCKET_UNKNOWN;
            return usm.getAppStandbyBucket();
        } catch (Exception e) {
            return BUCKET_UNKNOWN;
        }
    }

    public int getCurrentAppStandbyBucket() {
        return getAppStandbyBucket(context.getPackageName());
    }

    public String getBucketName(int bucket) {
        switch (bucket) {
            case BUCKET_ACTIVE:
                return context.getString(R.string.standby_bucket_active);
            case BUCKET_WORKING_SET:
                return context.getString(R.string.standby_bucket_working_set);
            case BUCKET_FREQUENT:
                return context.getString(R.string.standby_bucket_frequent);
            case BUCKET_RARE:
                return context.getString(R.string.standby_bucket_rare);
            case BUCKET_RESTRICTED:
                return context.getString(R.string.standby_bucket_restricted);
            case BUCKET_NEVER:
                return context.getString(R.string.standby_bucket_never);
            default:
                return context.getString(R.string.standby_bucket_unknown);
        }
    }

    public String getBucketDescription(int bucket) {
        switch (bucket) {
            case BUCKET_ACTIVE:
                return context.getString(R.string.standby_bucket_desc_active);
            case BUCKET_WORKING_SET:
                return context.getString(R.string.standby_bucket_desc_working_set);
            case BUCKET_FREQUENT:
                return context.getString(R.string.standby_bucket_desc_frequent);
            case BUCKET_RARE:
                return context.getString(R.string.standby_bucket_desc_rare);
            case BUCKET_RESTRICTED:
                return context.getString(R.string.standby_bucket_desc_restricted);
            case BUCKET_NEVER:
                return context.getString(R.string.standby_bucket_desc_never);
            default:
                return "";
        }
    }

    public String getImpactLevel(int bucket) {
        switch (bucket) {
            case BUCKET_ACTIVE:
                return context.getString(R.string.standby_impact_minimal);
            case BUCKET_WORKING_SET:
                return context.getString(R.string.standby_impact_low);
            case BUCKET_FREQUENT:
                return context.getString(R.string.standby_impact_medium);
            case BUCKET_RARE:
            case BUCKET_RESTRICTED:
            case BUCKET_NEVER:
                return context.getString(R.string.standby_impact_high);
            default:
                return context.getString(R.string.standby_impact_low);
        }
    }

    public int getBucketColor(int bucket) {
        switch (bucket) {
            case BUCKET_ACTIVE:
                return 0xFF4CAF50;
            case BUCKET_WORKING_SET:
                return 0xFF8BC34A;
            case BUCKET_FREQUENT:
                return 0xFFFFC107;
            case BUCKET_RARE:
                return 0xFFFF9800;
            case BUCKET_RESTRICTED:
            case BUCKET_NEVER:
                return 0xFFF44336;
            default:
                return 0xFF9E9E9E;
        }
    }

    public List<AppStandbyInfo> getTopAppsWithStandbyInfo(int limit) {
        List<AppStandbyInfo> result = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return result;
        }

        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            PackageManager pm = context.getPackageManager();
            if (usm == null || pm == null) return result;

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -7);
            long startTime = cal.getTimeInMillis();
            long endTime = System.currentTimeMillis();

            List<UsageStats> statsList = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

            if (statsList == null || statsList.isEmpty()) {
                return result;
            }

            java.util.Map<String, Long> totalTimeMap = new java.util.HashMap<>();
            for (UsageStats stats : statsList) {
                String pkg = stats.getPackageName();
                long time = stats.getTotalTimeInForeground();
                Long existing = totalTimeMap.get(pkg);
                if (existing == null) {
                    totalTimeMap.put(pkg, time);
                } else {
                    totalTimeMap.put(pkg, existing + time);
                }
            }

            List<java.util.Map.Entry<String, Long>> sortedEntries = new ArrayList<>(totalTimeMap.entrySet());
            Collections.sort(sortedEntries, new Comparator<java.util.Map.Entry<String, Long>>() {
                @Override
                public int compare(java.util.Map.Entry<String, Long> a, java.util.Map.Entry<String, Long> b) {
                    return Long.compare(b.getValue(), a.getValue());
                }
            });

            int count = 0;
            for (java.util.Map.Entry<String, Long> entry : sortedEntries) {
                if (count >= limit) break;
                String packageName = entry.getKey();
                long totalTime = entry.getValue();

                if (packageName.equals(context.getPackageName())) continue;

                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    CharSequence label = pm.getApplicationLabel(appInfo);

                    AppStandbyInfo info = new AppStandbyInfo();
                    info.packageName = packageName;
                    info.appName = label != null ? label.toString() : packageName;
                    info.standbyBucket = getAppStandbyBucket(packageName);
                    info.totalForegroundTimeMs = totalTime;
                    result.add(info);
                    count++;
                } catch (Exception e) {
                    // Skip apps we can't get info for
                }
            }

        } catch (Exception e) {
            // Return empty list if we can't get usage stats
        }

        return result;
    }

    public boolean hasUsageStatsPermission() {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    public void openUsageAccessSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ex) {
                // Ignore
            }
        }
    }

    public static class AppStandbyInfo {
        public String packageName;
        public String appName;
        public int standbyBucket;
        public long totalForegroundTimeMs;
    }
}
