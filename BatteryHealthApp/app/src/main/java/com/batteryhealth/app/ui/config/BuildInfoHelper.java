package com.batteryhealth.app.ui.config;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.util.Locale;

/**
 * 设备信息静态后备方案
 * 
 * 当DeviceInfoManager不可用时，使用此工具直接获取设备信息
 * 不依赖任何需要权限的sysfs读取
 */
public class BuildInfoHelper {
    
    /**
     * 获取设备名称（品牌+型号）
     */
    public static String getDeviceName() {
        try {
            String brand = Build.BRAND;
            String model = Build.MODEL;
            if ((brand == null || brand.isEmpty()) && (model == null || model.isEmpty())) {
                return "Unknown Device";
            }
            StringBuilder sb = new StringBuilder();
            if (brand != null && !brand.isEmpty()) {
                sb.append(brand.substring(0, 1).toUpperCase());
                if (brand.length() > 1) {
                    sb.append(brand.substring(1));
                }
                sb.append(" ");
            }
            if (model != null && !model.isEmpty()) {
                sb.append(model);
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Unknown Device";
        }
    }
    
    /**
     * 获取Android版本
     */
    public static String getAndroidVersion() {
        try {
            int sdkInt = Build.VERSION.SDK_INT;
            String version = Build.VERSION.RELEASE;
            if (sdkInt >= 34) return "Android 14 (" + version + ")";
            if (sdkInt >= 33) return "Android 13 (" + version + ")";
            if (sdkInt >= 32) return "Android 12L (" + version + ")";
            if (sdkInt >= 31) return "Android 12 (" + version + ")";
            if (sdkInt >= 30) return "Android 11 (" + version + ")";
            if (sdkInt >= 29) return "Android 10 (" + version + ")";
            if (sdkInt >= 28) return "Android 9 (" + version + ")";
            if (sdkInt >= 27) return "Android 8.1 (" + version + ")";
            if (sdkInt >= 26) return "Android 8.0 (" + version + ")";
            return "Android " + version;
        } catch (Exception e) {
            return "Android";
        }
    }
    
    /**
     * 获取处理器信息
     */
    public static String getProcessorInfo() {
        try {
            int cores = Runtime.getRuntime().availableProcessors();
            String abi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 
                    ? Build.SUPPORTED_ABIS[0] : "Unknown";
            return cores + "核 " + abi;
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * 获取内存信息
     */
    public static String getMemoryInfo() {
        return getMemoryInfo(null);
    }
    
    public static String getMemoryInfo(Context context) {
        try {
            long totalMemory = 0;
            if (context != null) {
                ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (activityManager != null) {
                    ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                    activityManager.getMemoryInfo(memoryInfo);
                    totalMemory = memoryInfo.totalMem / (1024 * 1024);
                }
            }
            // fallback - 从/proc/meminfo读取
            if (totalMemory <= 0) {
                try {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.FileReader("/proc/meminfo"));
                    String line = reader.readLine();
                    reader.close();
                    if (line != null) {
                        // MemTotal:  8000000 kB
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 2) {
                            totalMemory = Long.parseLong(parts[1]) / 1024; // KB to MB
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (totalMemory > 0) {
                if (totalMemory >= 1024) {
                    return String.format(Locale.getDefault(), "%.1f GB", totalMemory / 1024.0);
                }
                return String.format(Locale.getDefault(), "%d MB", totalMemory);
            }
            return "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * 获取存储信息
     */
    public static String getStorageInfo() {
        try {
            StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
            long blockSize = statFs.getBlockSizeLong();
            long totalBlocks = statFs.getBlockCountLong();
            long totalStorage = (blockSize * totalBlocks) / (1024 * 1024 * 1024);
            return String.format(Locale.getDefault(), "%d GB", totalStorage);
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * 获取屏幕分辨率
     */
    public static String getScreenInfo() {
        return getScreenInfo(null);
    }
    
    public static String getScreenInfo(Context context) {
        try {
            if (context == null) {
                return "Unknown";
            }
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) {
                return "Unknown";
            }
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(metrics);
            return String.format(Locale.getDefault(), "%d x %d", 
                    metrics.widthPixels, metrics.heightPixels);
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * 获取完整设备信息（用于context方法）
     */
    public static String getFullDeviceInfo(Context context) {
        try {
            StringBuilder info = new StringBuilder();
            info.append("设备: ").append(getDeviceName()).append("\n");
            info.append("系统: ").append(getAndroidVersion()).append("\n");
            info.append("处理器: ").append(getProcessorInfo()).append("\n");
            info.append("存储: ").append(getStorageInfo());
            return info.toString();
        } catch (Exception e) {
            return "设备信息获取失败";
        }
    }
}
