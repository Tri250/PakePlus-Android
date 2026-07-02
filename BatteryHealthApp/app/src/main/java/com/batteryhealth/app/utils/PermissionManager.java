package com.batteryhealth.app.utils;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限管理工具类。
 * 在请求权限前展示用途说明弹窗，避免用户在不理解权限用途时直接拒绝，
 * 导致 shouldShowRequestPermissionRationale 返回 false 后无法再次引导。
 */
public class PermissionManager {

    public static final int PERMISSION_REQUEST_CODE = 100;

    /**
     * 检查并请求权限。
     * 在请求前对每个未授予的权限展示用途说明弹窗，
     * 保证用户在任何 Android 版本下都能了解权限用途。
     */
    public static void checkAndRequestPermissions(Activity activity, String[] permissions) {
        List<String> permissionsToRequest = new ArrayList<>();
        List<String> permissionsGranted = new ArrayList<>();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    == PackageManager.PERMISSION_GRANTED) {
                permissionsGranted.add(permission);
            } else {
                permissionsToRequest.add(permission);
            }
        }

        if (permissionsToRequest.isEmpty()) {
            // 全部已授予，无需任何操作
            return;
        }

        // 展示权限用途说明，说明后再请求
        showPermissionsRationale(activity, permissionsToRequest.toArray(new String[0]));
    }

    /**
     * 批量展示权限用途说明弹窗，说明后再请求权限。
     */
    private static void showPermissionsRationale(Activity activity, String[] permissions) {
        StringBuilder sb = new StringBuilder();
        for (String permission : permissions) {
            if (android.Manifest.permission.POST_NOTIFICATIONS.equals(permission)) {
                sb.append("「通知权限」：用于接收充电完成、低电量提醒、电池健康预警等重要通知。\n\n");
            } else if ("android.permission.READ_PHONE_STATE".equals(permission)) {
                sb.append("「电话状态权限」：用于读取电池序列号等硬件信息以识别电池型号（非通话功能）。\n\n");
            } else if ("android.permission.PACKAGE_USAGE_STATS".equals(permission)) {
                sb.append("「使用情况访问权限」：用于查看各应用的耗电排行和前台运行时长，数据仅本地展示，不会上传。\n\n");
            } else {
                sb.append(activity.getString(com.batteryhealth.app.R.string.dialog_permission_message)).append("\n\n");
            }
        }
        // 去掉末尾多余换行
        String message = sb.toString().trim();
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(com.batteryhealth.app.R.string.dialog_permission_title))
                .setMessage(message)
                .setPositiveButton("继续", (dialog, which) -> {
                    ActivityCompat.requestPermissions(activity, permissions, PERMISSION_REQUEST_CODE);
                })
                .setNegativeButton(activity.getString(com.batteryhealth.app.R.string.dialog_permission_cancel), null)
                .show();
    }

    /**
     * 处理权限请求结果，对所有被拒绝的权限统一显示引导对话框。
     * 早期版本在第一个被拒绝权限处 break，导致多个权限被拒时只有一项被处理。
     */
    public static void handlePermissionResult(Activity activity, String[] permissions,
                                               int[] grantResults) {
        List<String> rationalePermissions = new ArrayList<>();
        List<String> permanentlyDeniedPermissions = new ArrayList<>();

        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                String permission = permissions[i];
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                    rationalePermissions.add(permission);
                } else {
                    permanentlyDeniedPermissions.add(permission);
                }
            }
        }

        if (!rationalePermissions.isEmpty()) {
            showRationaleDialog(activity, rationalePermissions);
        } else if (!permanentlyDeniedPermissions.isEmpty()) {
            showGoToSettingsDialog(activity);
        }
    }

    private static void showRationaleDialog(Activity activity, List<String> permissions) {
        String message = buildPermissionRationaleMessage(activity, permissions);
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(com.batteryhealth.app.R.string.dialog_permission_title))
                .setMessage(message)
                .setPositiveButton(activity.getString(com.batteryhealth.app.R.string.dialog_permission_retry), (dialog, which) -> {
                    ActivityCompat.requestPermissions(activity,
                            permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
                })
                .setNegativeButton(activity.getString(com.batteryhealth.app.R.string.dialog_permission_cancel), null)
                .show();
    }

    private static void showGoToSettingsDialog(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(com.batteryhealth.app.R.string.dialog_permission_denied_title))
                .setMessage(activity.getString(com.batteryhealth.app.R.string.dialog_permission_denied_message))
                .setPositiveButton(activity.getString(com.batteryhealth.app.R.string.action_go_settings), (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(intent);
                })
                .setNegativeButton(activity.getString(com.batteryhealth.app.R.string.dialog_permission_cancel), null)
                .show();
    }

    private static String buildPermissionRationaleMessage(Activity activity, List<String> permissions) {
        StringBuilder sb = new StringBuilder();
        for (String permission : permissions) {
            if (android.Manifest.permission.POST_NOTIFICATIONS.equals(permission)) {
                sb.append("• 通知权限：用于接收充电完成、低电量提醒、电池健康预警等重要通知。\n");
            } else if (android.Manifest.permission.READ_PHONE_STATE.equals(permission)) {
                sb.append("• 电话状态权限：用于读取电池序列号等硬件信息以识别电池型号（非通话功能）。\n");
            } else if ("android.permission.PACKAGE_USAGE_STATS".equals(permission)) {
                sb.append("• 使用情况访问权限：用于查看各应用耗电排行和前台运行时长，数据仅本地展示。\n");
            } else {
                sb.append("• ").append(activity.getString(com.batteryhealth.app.R.string.dialog_permission_message)).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 检查权限是否已授予
     */
    public static boolean hasPermission(Activity activity, String permission) {
        return ContextCompat.checkSelfPermission(activity, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 检查多个权限是否都已授予
     */
    public static boolean hasPermissions(Activity activity, String[] permissions) {
        for (String permission : permissions) {
            if (!hasPermission(activity, permission)) {
                return false;
            }
        }
        return true;
    }
}
