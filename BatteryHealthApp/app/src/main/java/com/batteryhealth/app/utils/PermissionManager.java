package com.batteryhealth.app.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.batteryhealth.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行时权限管理工具。
 * 修复问题：原版本 handlePermissionResult 缺少对 grantResults 长度和空数组的检查，
 * 可能在用户拒绝授权时抛出 IndexOutOfBoundsException。
 */
public class PermissionManager {

    public static final int REQUEST_CODE_PERMISSIONS = 1001;
    public static final int REQUEST_CODE_SETTINGS = 1002;

    private final Activity activity;

    public PermissionManager(Activity activity) {
        this.activity = activity;
    }

    /**
     * 检查并申请运行时权限。
     *
     * @return true if all requested permissions are already granted
     */
    public boolean checkAndRequestPermissions(@NonNull String[] permissions) {
        if (permissions == null || permissions.length == 0) return true;
        List<String> toRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (permission == null) continue;
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(permission);
            }
        }
        if (toRequest.isEmpty()) return true;
        String[] arr = toRequest.toArray(new String[0]);
        ActivityCompat.requestPermissions(activity, arr, REQUEST_CODE_PERMISSIONS);
        return false;
    }

    /**
     * 处理权限申请结果。
     *
     * @return true 表示全部授予
     */
    public boolean handlePermissionResult(int requestCode, @NonNull String[] permissions, @Nullable int[] grantResults) {
        if (requestCode != REQUEST_CODE_PERMISSIONS) return false;
        if (permissions == null || permissions.length == 0) return true;
        if (grantResults == null || grantResults.length < permissions.length) {
            return false;
        }
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                if (i < permissions.length && ActivityCompat.shouldShowRequestPermissionRationale(activity, permissions[i])) {
                    showRationaleDialog(permissions);
                } else {
                    showGoToSettingsDialog();
                }
                return false;
            }
        }
        return true;
    }

    public void showRationaleDialog(@NonNull final String[] permissions) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.permission_rationale_title)
                .setMessage(R.string.permission_rationale_message)
                .setPositiveButton(R.string.permission_retry, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(activity, permissions, REQUEST_CODE_PERMISSIONS);
                    }
                })
                .setNegativeButton(R.string.permission_cancel, null)
                .show();
    }

    public void showGoToSettingsDialog() {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.permission_settings_title)
                .setMessage(R.string.permission_settings_message)
                .setPositiveButton(R.string.permission_open_settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openAppSettings();
                    }
                })
                .setNegativeButton(R.string.permission_cancel, null)
                .show();
    }

    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivityForResult(intent, REQUEST_CODE_SETTINGS);
        } catch (Exception e) {
            // Best-effort: fall back to system settings
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 是否有指定权限。
     */
    public static boolean hasPermission(@NonNull Context context, @NonNull String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 是否拥有所有指定权限。
     */
    public static boolean hasPermissions(@NonNull Context context, @NonNull String[] permissions) {
        if (permissions == null) return true;
        for (String p : permissions) {
            if (p == null) continue;
            if (ContextCompat.checkSelfPermission(context, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取应用所需的所有运行时权限。
     * 包含 Android 12+ 蓝牙权限、Android 13+ 通知权限等。
     */
    public static String[] getRequiredPermissions() {
        List<String> list = new ArrayList<>();
        // Android 13+ requires POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        // Optional usage access - declared in manifest as queries
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Usage access is not a runtime permission; user must enable from settings
        }
        return list.toArray(new String[0]);
    }
}
