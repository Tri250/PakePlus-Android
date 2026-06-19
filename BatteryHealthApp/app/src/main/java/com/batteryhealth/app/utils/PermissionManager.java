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
 * 权限管理工具类
 */
public class PermissionManager {

    public static final int PERMISSION_REQUEST_CODE = 100;

    /**
     * 检查并请求权限
     */
    public static void checkAndRequestPermissions(Activity activity, String[] permissions) {
        List<String> permissionsToRequest = new ArrayList<>();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(activity,
                    permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * 处理权限请求结果，对拒绝的权限显示引导对话框
     */
    public static void handlePermissionResult(Activity activity, String[] permissions,
                                               int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                String permission = permissions[i];
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                    showRationaleDialog(activity, permission);
                } else {
                    // 用户选择了“不再询问”，引导去设置页
                    showGoToSettingsDialog(activity);
                }
                break;
            }
        }
    }

    private static void showRationaleDialog(Activity activity, String permission) {
        String message = activity.getString(com.batteryhealth.app.R.string.dialog_permission_message);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && android.Manifest.permission.POST_NOTIFICATIONS.equals(permission)) {
            message = activity.getString(com.batteryhealth.app.R.string.dialog_permission_notification_message);
        }
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(com.batteryhealth.app.R.string.dialog_permission_title))
                .setMessage(message)
                .setPositiveButton(activity.getString(com.batteryhealth.app.R.string.dialog_permission_retry), (dialog, which) -> {
                    ActivityCompat.requestPermissions(activity,
                            new String[]{permission}, PERMISSION_REQUEST_CODE);
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