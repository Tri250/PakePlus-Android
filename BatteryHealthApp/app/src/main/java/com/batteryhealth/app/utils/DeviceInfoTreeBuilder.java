package com.batteryhealth.app.utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.WindowManager;

import com.batteryhealth.app.data.model.DeviceInfoNode;
import com.batteryhealth.app.data.model.BatteryInfo;

import java.io.File;
import java.util.List;

/**
 * 设备信息树构建器
 * 构建设备完整信息树：系统/硬件/电池/相机/传感器等
 */
public class DeviceInfoTreeBuilder {

    public static DeviceInfoNode buildDeviceInfoTree(Context context) {
        DeviceInfoNode root = new DeviceInfoNode(DeviceInfoNode.TYPE_CATEGORY, "设备信息", "全部");
        root.setExpanded(true);

        root.addChild(buildSystemInfo(context));
        root.addChild(buildHardwareInfo(context));
        root.addChild(buildBatteryInfo(context));
        root.addChild(buildCameraInfo(context));
        root.addChild(buildSensorInfo(context));
        root.addChild(buildStorageInfo(context));
        root.addChild(buildNetworkInfo(context));
        root.addChild(buildDisplayInfo(context));

        return root;
    }

    private static DeviceInfoNode buildSystemInfo(Context context) {
        DeviceInfoNode category = new DeviceInfoNode(DeviceInfoNode.TYPE_CATEGORY, "系统信息", "");
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "系统版本", Build.VERSION.RELEASE));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "系统版本号", String.valueOf(Build.VERSION.SDK_INT)));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "Android 代号", getAndroidVersionName()));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "安全补丁", Build.VERSION.SECURITY_PATCH));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "Bootloader", Build.BOOTLOADER));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "基带版本", Build.getRadioVersion()));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "内核版本", System.getProperty("os.version")));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "构建号", Build.DISPLAY));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "构建时间", String.valueOf(Build.TIME)));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "构建类型", Build.TYPE));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "用户", Build.USER));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "语言", context.getResources().getConfiguration().locale.getDisplayLanguage()));
        return category;
    }

    private static DeviceInfoNode buildHardwareInfo(Context context) {
        DeviceInfoNode category = new DeviceInfoNode(DeviceInfoNode.TYPE_CATEGORY, "硬件信息", "");
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "品牌", Build.BRAND));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "制造商", Build.MANUFACTURER));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "型号", Build.MODEL));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "产品名称", Build.PRODUCT));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "设备名称", Build.DEVICE));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "主板", Build.BOARD));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "硬件平台", Build.HARDWARE));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "CPU 架构", Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "未知"));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "CPU 核心数", String.valueOf(Runtime.getRuntime().availableProcessors())));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "总内存", getTotalMemory()));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "指纹硬件", Build.FINGERPRINT));
        category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "设备 ID", Build.ID));
        return category;
    }

    private static DeviceInfoNode buildBatteryInfo(Context context) {
        DeviceInfoNode category = new DeviceInfoNode(DeviceInfoNode.TYPE_CATEGORY, "电池信息", "");
        try {
            BatteryDataManager bdm = new BatteryDataManager(context);
            BatteryInfo info = bdm.getCurrentBatteryInfo();
            if (info != null) {
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "当前电量", info.getLevel() + "%"));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "电池状态", getBatteryStatusText(info.getStatus())));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "电池技术", info.getTechnology()));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "电池电压", info.getVoltage() + " V"));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "电池温度", info.getTemperature() + " °C"));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "充电方式", getPlugTypeText(info.getPlugged())));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "设计容量", info.getDesignCapacity() + " mAh"));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "健康状态", info.getHealthStatus()));
            }
        } catch (Exception e) {
            category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "状态", "无法读取"));
        }
        return category;
    }

    private static DeviceInfoNode buildCameraInfo(Context context) {
        DeviceInfoNode category = new DeviceInfoNode(DeviceInfoNode.TYPE_CATEGORY, "相机信息", "");
        try {
            android.hardware.camera2.CameraManager cameraManager =
                    (android.hardware.camera2.CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager != null) {
                String[] cameraIds = cameraManager.getCameraIdList();
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "摄像头数量", String.valueOf(cameraIds.length)));
                for (int i = 0; i < cameraIds.length; i++) {
                    String id = cameraIds[i];
                    android.hardware.camera2.CameraCharacteristics characteristics =
                            cameraManager.getCameraCharacteristics(id);
                    Integer facing = characteristics.get(
                            android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                    String facingStr = facing != null ? (facing ==
                            android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT ? "前置" : "后置") : "未知";
                    category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM,
                            "摄像头 " + (i + 1) + " (" + facingStr + ")", "ID: " + id));
                }
            }
        } catch (Exception e) {
            category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "相机状态", "无法读取"));
        }
        return category;
    }

    private static DeviceInfoNode buildSensorInfo(Context context) {
        DeviceInfoNode category = new DeviceInfoNode(DeviceInfoNode.TYPE_CATEGORY, "传感器信息", "");
        try {
            SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "传感器总数", String.valueOf(sensors.size())));

                int accelCount = 0, gyroCount = 0, lightCount = 0, proximityCount = 0;
                int magnetCount = 0, baroCount = 0, stepCount = 0;

                for (Sensor sensor : sensors) {
                    switch (sensor.getType()) {
                        case Sensor.TYPE_ACCELEROMETER:
                            accelCount++;
                            break;
                        case Sensor.TYPE_GYROSCOPE:
                            gyroCount++;
                            break;
                        case Sensor.TYPE_LIGHT:
                            lightCount++;
                            break;
                        case Sensor.TYPE_PROXIMITY:
                            proximityCount++;
                            break;
                        case Sensor.TYPE_MAGNETIC_FIELD:
                            magnetCount++;
                            break;
                        case Sensor.TYPE_PRESSURE:
                            baroCount++;
                            break;
                        case Sensor.TYPE_STEP_COUNTER:
                            stepCount++;
                            break;
                    }
                }

                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "加速度传感器", accelCount > 0 ? "支持" : "不支持"));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "陀螺仪", gyroCount > 0 ? "支持" : "不支持"));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "光线传感器", lightCount > 0 ? "支持" : "不支持"));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "距离传感器", proximityCount > 0 ? "支持" : "不支持"));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "磁力计", magnetCount > 0 ? "支持" : "不支持"));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "气压计", baroCount > 0 ? "支持" : "不支持"));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "计步器", stepCount > 0 ? "支持" : "不支持"));
            }
        } catch (Exception e) {
            category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "传感器状态", "无法读取"));
        }
        return category;
    }

    private static DeviceInfoNode buildStorageInfo(Context context) {
        DeviceInfoNode category = new DeviceInfoNode(DeviceInfoNode.TYPE_CATEGORY, "存储信息", "");
        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availableBlocks = stat.getAvailableBlocksLong();

            long totalBytes = totalBlocks * blockSize;
            long availableBytes = availableBlocks * blockSize;
            long usedBytes = totalBytes - availableBytes;

            category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "总存储容量", formatSize(totalBytes)));
            category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "可用空间", formatSize(availableBytes)));
            category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "已用空间", formatSize(usedBytes)));
            category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "使用率",
                    String.format("%.1f%%", (usedBytes * 100.0f / totalBytes))));
        } catch (Exception e) {
            category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "存储状态", "无法读取"));
        }
        return category;
    }

    private static DeviceInfoNode buildNetworkInfo(Context context) {
        DeviceInfoNode category = new DeviceInfoNode(DeviceInfoNode.TYPE_CATEGORY, "网络信息", "");
        try {
            TelephonyManager telephonyManager =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "网络运营商", telephonyManager.getNetworkOperatorName()));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "SIM 运营商", telephonyManager.getSimOperatorName()));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "国家代码", telephonyManager.getSimCountryIso()));
                int simState = telephonyManager.getSimState();
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "SIM 状态", getSimStateText(simState)));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "数据网络类型", getNetworkTypeName(telephonyManager.getNetworkType())));
            }

            android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager)
                    context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "WiFi 状态", wifiManager.isWifiEnabled() ? "已开启" : "已关闭"));
            }

            android.bluetooth.BluetoothAdapter bluetoothAdapter =
                    android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "蓝牙状态",
                    bluetoothAdapter != null && bluetoothAdapter.isEnabled() ? "已开启" : "已关闭"));
        } catch (Exception e) {
            category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "网络状态", "无法读取"));
        }
        return category;
    }

    private static DeviceInfoNode buildDisplayInfo(Context context) {
        DeviceInfoNode category = new DeviceInfoNode(DeviceInfoNode.TYPE_CATEGORY, "显示信息", "");
        try {
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                windowManager.getDefaultDisplay().getMetrics(metrics);
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "屏幕分辨率",
                        metrics.widthPixels + " x " + metrics.heightPixels));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "屏幕密度",
                        String.format("%.1f (dpi: %d)", metrics.density, metrics.densityDpi)));
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "字体缩放",
                        String.valueOf(metrics.scaledDensity)));
                int brightness = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, 0);
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "当前亮度",
                        String.format("%.0f%%", (brightness / 255f) * 100)));
                long timeout = Settings.System.getLong(context.getContentResolver(),
                        Settings.System.SCREEN_OFF_TIMEOUT, 0);
                category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "屏幕超时",
                        (timeout / 1000) + " 秒"));
            }
        } catch (Exception e) {
            category.addChild(new DeviceInfoNode(DeviceInfoNode.TYPE_ITEM, "显示状态", "无法读取"));
        }
        return category;
    }

    private static String getAndroidVersionName() {
        int sdk = Build.VERSION.SDK_INT;
        if (sdk >= 35) return "Android 15+";
        if (sdk >= 34) return "Android 14 (Upside Down Cake)";
        if (sdk >= 33) return "Android 13 (Tiramisu)";
        if (sdk >= 32) return "Android 12L";
        if (sdk >= 31) return "Android 12 (Snow Cone)";
        if (sdk >= 30) return "Android 11 (Red Velvet Cake)";
        if (sdk >= 29) return "Android 10 (Quince Tart)";
        if (sdk >= 28) return "Android 9 (Pie)";
        if (sdk >= 27) return "Android 8.1 (Oreo)";
        if (sdk >= 26) return "Android 8.0 (Oreo)";
        return "Android " + Build.VERSION.RELEASE;
    }

    private static String getTotalMemory() {
        try {
            java.io.RandomAccessFile reader = new java.io.RandomAccessFile("/proc/meminfo", "r");
            String line = reader.readLine();
            reader.close();
            if (line != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    long kb = Long.parseLong(parts[1]);
                    if (kb >= 1024 * 1024) {
                        return String.format("%.1f GB", kb / (1024f * 1024f));
                    } else {
                        return String.format("%.0f MB", kb / 1024f);
                    }
                }
            }
        } catch (Exception ignored) {}
        return "未知";
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024 * 1024) {
            return String.format("%.2f TB", bytes / (1024f * 1024 * 1024 * 1024));
        } else if (bytes >= 1024 * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024f * 1024 * 1024));
        } else if (bytes >= 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024f * 1024));
        } else if (bytes >= 1024) {
            return String.format("%.2f KB", bytes / 1024f);
        } else {
            return bytes + " B";
        }
    }

    private static String getBatteryStatusText(int status) {
        switch (status) {
            case android.os.BatteryManager.BATTERY_STATUS_CHARGING: return "充电中";
            case android.os.BatteryManager.BATTERY_STATUS_DISCHARGING: return "放电中";
            case android.os.BatteryManager.BATTERY_STATUS_FULL: return "已充满";
            case android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "未充电";
            default: return "未知";
        }
    }

    private static String getPlugTypeText(int plugged) {
        switch (plugged) {
            case android.os.BatteryManager.BATTERY_PLUGGED_AC: return "AC 充电器";
            case android.os.BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS: return "无线充电";
            default: return "未充电";
        }
    }

    private static String getHealthText(int health) {
        switch (health) {
            case android.os.BatteryManager.BATTERY_HEALTH_GOOD: return "良好";
            case android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT: return "过热";
            case android.os.BatteryManager.BATTERY_HEALTH_DEAD: return "损坏";
            case android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "过压";
            case android.os.BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "未知故障";
            default: return "未知";
        }
    }

    private static String getSimStateText(int state) {
        switch (state) {
            case TelephonyManager.SIM_STATE_READY: return "就绪";
            case TelephonyManager.SIM_STATE_ABSENT: return "无 SIM 卡";
            case TelephonyManager.SIM_STATE_PIN_REQUIRED: return "需要 PIN";
            case TelephonyManager.SIM_STATE_PUK_REQUIRED: return "需要 PUK";
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED: return "网络锁定";
            default: return "未知";
        }
    }

    private static String getNetworkTypeName(int type) {
        switch (type) {
            case 20: return "5G";
            case 13:
            case 12:
                return "4G LTE";
            case 11:
            case 10:
            case 8:
                return "3G HSPA";
            case 9: return "3G UMTS";
            case 2: return "2G EDGE";
            case 1:
            case 16:
                return "2G GPRS";
            default: return "未知";
        }
    }

    public static String generateDeviceReport(Context context) {
        DeviceInfoNode root = buildDeviceInfoTree(context);
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════\n");
        sb.append("      设备信息完整报告\n");
        sb.append("═══════════════════════════════════\n\n");
        appendNodeReport(sb, root, 0);
        sb.append("\n═══════════════════════════════════\n");
        sb.append("  报告由电池健康助手生成\n");
        sb.append("═══════════════════════════════════\n");
        return sb.toString();
    }

    private static void appendNodeReport(StringBuilder sb, DeviceInfoNode node, int depth) {
        if (node == null) return;
        String indent = "";
        for (int i = 0; i < depth; i++) {
            indent += "  ";
        }

        if (node.getType() == DeviceInfoNode.TYPE_CATEGORY) {
            if (depth > 0) {
                sb.append("\n").append(indent).append("【").append(node.getTitle()).append("】\n");
            }
        } else {
            sb.append(indent).append(node.getTitle()).append("：")
                    .append(node.getValue() != null ? node.getValue() : "--").append("\n");
        }

        if (node.getChildren() != null) {
            for (DeviceInfoNode child : node.getChildren()) {
                appendNodeReport(sb, child, depth + (node.getType() == DeviceInfoNode.TYPE_CATEGORY ? 0 : 1));
            }
        }
    }

    public static class SystemUpdateStatus {
        public boolean updateAvailable = false;
        public String currentVersion = "";
        public String updateVersion = "";
        public String updateSize = "";
        public String securityPatch = "";
        public String buildNumber = "";
        public String description = "";
    }

    public static SystemUpdateStatus checkSystemUpdate(Context context) {
        SystemUpdateStatus status = new SystemUpdateStatus();
        status.currentVersion = Build.VERSION.RELEASE;
        status.securityPatch = Build.VERSION.SECURITY_PATCH;
        status.buildNumber = Build.DISPLAY;
        status.updateAvailable = false;
        status.description = "系统会自动检查更新，您也可以在系统设置中手动检查。";
        return status;
    }

    public static class DeviceScoreInfo {
        public int totalScore = 0;
        public int cpuScore = 0;
        public int memoryScore = 0;
        public int storageScore = 0;
        public int batteryScore = 0;
        public int displayScore = 0;
        public String rankPercentile = "";
        public String comparisonModel = "";
        public String evaluation = "";
    }

    public static DeviceScoreInfo calculateDeviceScore(Context context) {
        DeviceScoreInfo info = new DeviceScoreInfo();

        int cpuCores = Runtime.getRuntime().availableProcessors();
        info.cpuScore = Math.min(100, cpuCores * 15 + 20);

        try {
            java.io.RandomAccessFile reader = new java.io.RandomAccessFile("/proc/meminfo", "r");
            String line = reader.readLine();
            reader.close();
            if (line != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    long kb = Long.parseLong(parts[1]);
                    float gb = kb / (1024f * 1024f);
                    info.memoryScore = (int) Math.min(100, gb * 12 + 20);
                }
            }
        } catch (Exception e) {
            info.memoryScore = 60;
        }

        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long totalBytes = stat.getBlockCountLong() * stat.getBlockSizeLong();
            float totalGB = totalBytes / (1024f * 1024 * 1024);
            info.storageScore = (int) Math.min(100, totalGB * 0.8f + 20);
        } catch (Exception e) {
            info.storageScore = 60;
        }

        try {
            BatteryDataManager bdm = new BatteryDataManager(context);
            BatteryInfo batteryInfo = bdm.getCurrentBatteryInfo();
            if (batteryInfo != null) {
                int capacity = batteryInfo.getDesignCapacity();
                info.batteryScore = Math.min(100, capacity / 50 + 20);
            }
        } catch (Exception e) {
            info.batteryScore = 65;
        }

        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                wm.getDefaultDisplay().getMetrics(metrics);
                int pixels = metrics.widthPixels * metrics.heightPixels;
                info.displayScore = (int) Math.min(100, pixels / 50000f + 30);
            }
        } catch (Exception e) {
            info.displayScore = 65;
        }

        info.totalScore = (info.cpuScore + info.memoryScore + info.storageScore
                + info.batteryScore + info.displayScore) / 5;

        if (info.totalScore >= 85) {
            info.rankPercentile = "Top 10%";
            info.evaluation = "旗舰水平";
        } else if (info.totalScore >= 70) {
            info.rankPercentile = "Top 30%";
            info.evaluation = "中高端水平";
        } else if (info.totalScore >= 55) {
            info.rankPercentile = "Top 60%";
            info.evaluation = "主流水平";
        } else {
            info.rankPercentile = "后 40%";
            info.evaluation = "入门水平";
        }

        info.comparisonModel = Build.MODEL;

        return info;
    }
}
