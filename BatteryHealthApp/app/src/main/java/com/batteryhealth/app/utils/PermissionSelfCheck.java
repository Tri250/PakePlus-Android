package com.batteryhealth.app.utils;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限自检与修复工具类
 *
 * 功能：
 * 1. 检查所有必需权限状态
 * 2. 自动引导用户修复缺失权限
 * 3. 检测 Android 16 特殊权限要求
 * 4. 电池优化白名单检查
 * 5. 精确闹钟权限检查（Android 12+）
 */
public class PermissionSelfCheck {

    private static final String TAG = "PermissionSelfCheck";

    // Android 16 必需权限
    private static final String[] REQUIRED_PERMISSIONS_API36 = {
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC,
            Manifest.permission.FOREGROUND_SERVICE_HEALTH
    };

    // Android 13-15 必需权限
    private static final String[] REQUIRED_PERMISSIONS_API33 = {
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE
    };

    // Android 12 必需权限
    private static final String[] REQUIRED_PERMISSIONS_API31 = {
            Manifest.permission.FOREGROUND_SERVICE
    };

    /**
     * 执行完整的权限自检
     */
    public static PermissionStatus checkAllPermissions(Context context) {
        PermissionStatus status = new PermissionStatus();

        // 检查运行时权限
        status.runtimePermissions = checkRuntimePermissions(context);

        // 检查通知权限（Android 13+）
        status.notificationPermission = checkNotificationPermission(context);

        // 检查电池优化状态
        status.batteryOptimization = checkBatteryOptimization(context);

        // 检查精确闹钟权限（Android 12+）
        status.exactAlarmPermission = checkExactAlarmPermission(context);

        // 检查后台启动限制（Android 14+）
        status.backgroundStartRestricted = checkBackgroundStartRestriction(context);

        // 检查全屏 intent 权限（Android 14+）
        status.fullScreenIntentPermission = checkFullScreenIntentPermission(context);

        status.allGranted = status.runtimePermissions.isEmpty()
                && status.notificationPermission
                && status.batteryOptimization
                && status.exactAlarmPermission
                && !status.backgroundStartRestricted
                && status.fullScreenIntentPermission;

        return status;
    }

    /**
     * 检查运行时权限
     */
    public static List<String> checkRuntimePermissions(Context context) {
        List<String> missingPermissions = new ArrayList<>();
        String[] permissions = getRequiredPermissionsForApiLevel();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        return missingPermissions;
    }

    /**
     * 检查通知权限（Android 13+）
     */
    public static boolean checkNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true; // Android 12 及以下不需要显式申请
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 检查电池优化状态
     */
    public static boolean checkBatteryOptimization(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    /**
     * 检查精确闹钟权限（Android 12+）
     */
    public static boolean checkExactAlarmPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    /**
     * 检查后台启动限制（Android 14+）。
     * <p>Android 14 引入 {@code ActivityManager.isBackgroundRestricted}，
     * 可用于判断应用是否被限制从后台启动前台服务。
     */
    public static boolean checkBackgroundStartRestriction(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false; // 无限制
        }
        try {
            android.app.ActivityManager am =
                    (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                return am.isBackgroundRestricted();
            }
        } catch (Throwable t) {
            LogHelper.e(TAG, "checkBackgroundStartRestriction failed: " + t.getMessage());
        }
        return false;
    }

    /**
     * 检查全屏 intent 权限（Android 14+）
     */
    public static boolean checkFullScreenIntentPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true;
        }
        return context.checkSelfPermission(Manifest.permission.USE_FULL_SCREEN_INTENT)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 获取当前 API 级别所需的权限列表
     */
    private static String[] getRequiredPermissionsForApiLevel() {
        if (Build.VERSION.SDK_INT >= 36) {
            return REQUIRED_PERMISSIONS_API36;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return REQUIRED_PERMISSIONS_API33;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return REQUIRED_PERMISSIONS_API31;
        }
        return new String[0];
    }

    /**
     * 显示权限修复对话框
     */
    public static void showPermissionFixDialog(Activity activity, PermissionStatus status) {
        if (status.allGranted) {
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("检测到以下权限问题需要修复：\n\n");

        if (!status.runtimePermissions.isEmpty()) {
            message.append("缺失的运行时权限：\n");
            for (String perm : status.runtimePermissions) {
                message.append("• ").append(getPermissionDisplayName(perm)).append("\n");
            }
            message.append("\n");
        }

        if (!status.notificationPermission) {
            message.append("• 通知权限未授予（影响前台服务通知显示）\n");
        }

        if (!status.batteryOptimization) {
            message.append("• 电池优化未关闭（可能导致后台服务被杀死）\n");
        }

        if (!status.exactAlarmPermission) {
            message.append("• 精确闹钟权限未授予（影响服务自动重启）\n");
        }

        if (status.backgroundStartRestricted) {
            message.append("• 后台启动受限（Android 14+ 限制）\n");
        }

        if (!status.fullScreenIntentPermission) {
            message.append("• 全屏 Intent 权限未授予\n");
        }

        message.append("\n是否立即修复？");

        new AlertDialog.Builder(activity)
                .setTitle("权限自检")
                .setMessage(message.toString())
                .setPositiveButton("立即修复", (dialog, which) -> {
                    fixPermissions(activity, status);
                })
                .setNegativeButton("稍后", null)
                .setCancelable(false)
                .show();
    }

    /**
     * 修复权限问题
     */
    public static void fixPermissions(Activity activity, PermissionStatus status) {
        // 1. 申请运行时权限
        if (!status.runtimePermissions.isEmpty()) {
            ActivityCompat.requestPermissions(activity,
                    status.runtimePermissions.toArray(new String[0]),
                    PermissionManager.PERMISSION_REQUEST_CODE);
        }

        // 2. 引导关闭电池优化
        if (!status.batteryOptimization) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            try {
                activity.startActivity(intent);
            } catch (Exception e) {
                LogHelper.e(TAG, "Failed to open battery optimization settings: " + e.getMessage());
            }
        }

        // 3. 引导授予精确闹钟权限
        if (!status.exactAlarmPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            try {
                activity.startActivity(intent);
            } catch (Exception e) {
                LogHelper.e(TAG, "Failed to open exact alarm settings: " + e.getMessage());
            }
        }

        // 4. 引导到应用设置页面
        if (!status.notificationPermission || status.backgroundStartRestricted || !status.fullScreenIntentPermission) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            try {
                activity.startActivity(intent);
            } catch (Exception e) {
                LogHelper.e(TAG, "Failed to open app settings: " + e.getMessage());
            }
        }
    }

    /**
     * 获取权限显示名称
     */
    private static String getPermissionDisplayName(String permission) {
        switch (permission) {
            case Manifest.permission.POST_NOTIFICATIONS:
                return "通知权限";
            case Manifest.permission.FOREGROUND_SERVICE:
                return "前台服务权限";
            case Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC:
                return "前台服务数据同步权限";
            case Manifest.permission.FOREGROUND_SERVICE_HEALTH:
                return "前台服务健康权限";
            case Manifest.permission.USE_FULL_SCREEN_INTENT:
                return "全屏 Intent 权限";
            default:
                return permission;
        }
    }

    /**
     * 权限状态数据类
     */
    public static class PermissionStatus {
        public List<String> runtimePermissions = new ArrayList<>();
        public boolean notificationPermission = true;
        public boolean batteryOptimization = true;
        public boolean exactAlarmPermission = true;
        public boolean backgroundStartRestricted = false;
        public boolean fullScreenIntentPermission = true;
        public boolean allGranted = true;

        @Override
        public String toString() {
            return "PermissionStatus{" +
                    "runtimePermissions=" + runtimePermissions +
                    ", notificationPermission=" + notificationPermission +
                    ", batteryOptimization=" + batteryOptimization +
                    ", exactAlarmPermission=" + exactAlarmPermission +
                    ", backgroundStartRestricted=" + backgroundStartRestricted +
                    ", fullScreenIntentPermission=" + fullScreenIntentPermission +
                    ", allGranted=" + allGranted +
                    '}';
        }
    }
}
