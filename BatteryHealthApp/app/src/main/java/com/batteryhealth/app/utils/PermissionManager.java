package com.batteryhealth.app.utils;

import android.app.Activity;
import android.content.pm.PackageManager;

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