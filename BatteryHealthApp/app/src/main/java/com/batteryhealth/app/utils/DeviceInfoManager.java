package com.batteryhealth.app.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.batteryhealth.app.data.model.DeviceConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 设备信息管理器
 *
 * 功能：
 * 1. 获取设备硬件信息
 * 2. 获取系统配置
 * 3. 查询设备激活日期
 * 4. 分析设备性能
 */
public class DeviceInfoManager {

    private static final String TAG = "DeviceInfoManager";
    private Context context;
    private DeviceConfig deviceConfig;
    
    public DeviceInfoManager(Context context) {
        this.context = context.getApplicationContext();
        this.deviceConfig = new DeviceConfig();
        loadDeviceInfo();
    }
    
    /**
     * 加载设备信息
     */
    private void loadDeviceInfo() {
        // 基本信息已在构造函数中初始化
        
        // 加载处理器信息
        loadCpuInfo();
        
        // 加载内存信息
        loadMemoryInfo();
        
        // 加载存储信息
        loadStorageInfo();
        
        // 加载显示信息
        loadDisplayInfo();
        
        // 加载电池信息
        loadBatteryInfo();
        
        // 加载网络信息
        loadNetworkInfo();
        
        // 加载激活信息
        loadActivationInfo();
    }
    
    /**
     * 加载处理器信息
     */
    private void loadCpuInfo() {
        BufferedReader reader = null;
        try {
            // 获取CPU核心数
            int cores = Runtime.getRuntime().availableProcessors();
            deviceConfig.setCpuCores(cores);

            // 读取CPU信息
            File cpuInfoFile = new File("/proc/cpuinfo");
            if (cpuInfoFile.exists()) {
                reader = new BufferedReader(new FileReader(cpuInfoFile));
                StringBuilder cpuInfo = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Hardware") || line.contains("Processor") ||
                        line.contains("model name")) {
                        cpuInfo.append(line).append("\n");
                    }
                }
                deviceConfig.setCpuInfo(cpuInfo.toString().trim());
            }

            // 读取CPU频率
            File maxFreqFile = new File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            if (maxFreqFile.exists()) {
                try {
                    reader = new BufferedReader(new FileReader(maxFreqFile));
                    String line = reader.readLine();
                    if (line != null) {
                        int maxFreq = Integer.parseInt(line.trim()) / 1000; // 转换为MHz
                        deviceConfig.setCpuFreqMax(maxFreq);
                    }
                } finally {
                    if (reader != null) {
                        try { reader.close(); } catch (Exception ignored) {}
                        reader = null;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading CPU info: " + e.getMessage());
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
    }
    
    /**
     * 加载内存信息
     */
    private void loadMemoryInfo() {
        try {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) {
                Log.w(TAG, "ActivityManager is null");
                return;
            }
            
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            
            // 总内存 (MB)
            long totalMemory = memoryInfo.totalMem / (1024 * 1024);
            deviceConfig.setTotalMemory(totalMemory);
            
            // 可用内存 (MB)
            long availableMemory = memoryInfo.availMem / (1024 * 1024);
            deviceConfig.setAvailableMemory(availableMemory);
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading memory info: " + e.getMessage());
        }
    }
    
    /**
     * 加载存储信息
     */
    private void loadStorageInfo() {
        try {
            StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
            
            long blockSize = statFs.getBlockSizeLong();
            long totalBlocks = statFs.getBlockCountLong();
            long availableBlocks = statFs.getAvailableBlocksLong();
            
            // 总存储 (GB)
            long totalStorage = (blockSize * totalBlocks) / (1024 * 1024 * 1024);
            deviceConfig.setTotalStorage(totalStorage);
            
            // 可用存储 (GB)
            long availableStorage = (blockSize * availableBlocks) / (1024 * 1024 * 1024);
            deviceConfig.setAvailableStorage(availableStorage);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 加载显示信息
     */
    private void loadDisplayInfo() {
        try {
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) {
                Log.w(TAG, "WindowManager is null");
                return;
            }
            
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(metrics);
            
            deviceConfig.setScreenWidth(metrics.widthPixels);
            deviceConfig.setScreenHeight(metrics.heightPixels);
            deviceConfig.setScreenDensity(metrics.density);
            deviceConfig.setScreenDpi(metrics.densityDpi);
            
            // 计算屏幕尺寸 (英寸)
            if (metrics.xdpi > 0 && metrics.ydpi > 0) {
                float widthInches = metrics.widthPixels / metrics.xdpi;
                float heightInches = metrics.heightPixels / metrics.ydpi;
                double screenSize = Math.sqrt(widthInches * widthInches + heightInches * heightInches);
                deviceConfig.setScreenSize((float) screenSize);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading display info: " + e.getMessage());
        }
    }
    
    /**
     * 加载电池信息
     */
    private void loadBatteryInfo() {
        BufferedReader reader = null;
        try {
            // 读取电池技术
            File techFile = new File("/sys/class/power_supply/battery/technology");
            if (techFile.exists()) {
                reader = new BufferedReader(new FileReader(techFile));
                String technology = reader.readLine();
                if (technology != null) {
                    deviceConfig.setBatteryTechnology(technology);
                }
            }

            // 读取电池容量
            File capacityFile = new File("/sys/class/power_supply/battery/charge_full_design");
            if (capacityFile.exists()) {
                try {
                    reader = new BufferedReader(new FileReader(capacityFile));
                    String line = reader.readLine();
                    if (line != null) {
                        int capacity = Integer.parseInt(line.trim()) / 1000; // 转换为mAh
                        deviceConfig.setBatteryCapacity(capacity);
                    }
                } finally {
                    if (reader != null) {
                        try { reader.close(); } catch (Exception ignored) {}
                        reader = null;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading battery info: " + e.getMessage());
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
    }
    
    /**
     * 加载网络信息
     */
    private void loadNetworkInfo() {
        try {
            // 获取网络类型
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if (activeNetwork != null) {
                    deviceConfig.setNetworkType(getNetworkTypeString(activeNetwork.getType()));
                }
            }
            
            // 获取IP地址
            deviceConfig.setIpAddress(getIPAddress());
            
            // 获取MAC地址
            deviceConfig.setMacAddress(getMacAddress());
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading network info: " + e.getMessage());
        }
    }
    
    /**
     * 加载激活信息（2026 旗舰版）
     *
     * 尝试多种方式获取设备首次激活日期，并按可信度排序：
     * 1. 系统设置 first_boot_time（最可信，但极少 ROM 提供）
     * 2. 应用首次安装时间 PackageInfo.firstInstallTime（较可信）
     * 3. 应用数据目录创建时间（较可信，但可能被清除数据影响）
     * 4. Android ID + Build.TIME 估算（粗略估算）
     * 5. 设备构建时间（最后备选）
     *
     * 每次结果都会标注来源与可信度，UI 可据此向用户说明是否为估算值。
     */
    private void loadActivationInfo() {
        long activationTime = 0;
        String source = "unknown";
        float confidence = 0.0f;

        try {
            // 方法1: 系统设置中的首次启动时间（最可信）
            try {
                String firstBoot = Settings.Global.getString(context.getContentResolver(), "first_boot_time");
                if (firstBoot != null && !firstBoot.isEmpty()) {
                    activationTime = Long.parseLong(firstBoot);
                    source = "system";
                    confidence = 0.95f;
                }
            } catch (Exception ignored) {}

            // 方法2: 应用首次安装时间
            if (activationTime == 0) {
                try {
                    android.content.pm.PackageInfo packageInfo = context.getPackageManager()
                            .getPackageInfo(context.getPackageName(), 0);
                    if (packageInfo != null && packageInfo.firstInstallTime > 0) {
                        activationTime = packageInfo.firstInstallTime;
                        source = "package_install";
                        confidence = 0.75f;
                    }
                } catch (Exception ignored) {}
            }

            // 方法3: 从应用数据目录的修改时间推断
            if (activationTime == 0) {
                try {
                    File dataDir = context.getDataDir();
                    if (dataDir != null && dataDir.exists()) {
                        activationTime = dataDir.lastModified();
                        if (activationTime > 0) {
                            source = "datadir";
                            confidence = 0.55f;
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 方法4: 从Android ID的生成时间推断（粗略估算）
            if (activationTime == 0) {
                try {
                    String androidId = Settings.Secure.getString(context.getContentResolver(),
                            Settings.Secure.ANDROID_ID);
                    if (androidId != null && !androidId.isEmpty()) {
                        long buildTime = Build.TIME;
                        int idHash = androidId.hashCode();
                        long offset = Math.abs(idHash % (30L * 24 * 60 * 60 * 1000));
                        activationTime = buildTime + offset;
                        source = "android_id";
                        confidence = 0.30f;
                    }
                } catch (Exception ignored) {}
            }

            // 方法5: 使用设备构建时间作为最后备选
            if (activationTime == 0) {
                activationTime = Build.TIME;
                source = "build_time";
                confidence = 0.15f;
            }

            deviceConfig.setActivationDate(activationTime);
            deviceConfig.setActivationSource(source);
            deviceConfig.setActivationConfidence(confidence);

            // 计算使用天数
            long currentTime = System.currentTimeMillis();
            long usageDays = (currentTime - activationTime) / (1000 * 60 * 60 * 24);
            deviceConfig.setUsageDays((int) Math.max(0, usageDays));

            // 格式化日期
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            deviceConfig.setActivationDateStr(sdf.format(new java.util.Date(activationTime)));

        } catch (Exception e) {
            e.printStackTrace();
            deviceConfig.setActivationDate(Build.TIME);
            deviceConfig.setUsageDays(0);
            deviceConfig.setActivationDateStr("未知");
            deviceConfig.setActivationSource("unknown");
            deviceConfig.setActivationConfidence(0.0f);
        }
    }

    /**
     * 获取激活日期来源文本
     */
    public String getActivationSourceText() {
        String source = deviceConfig.getActivationSource();
        if ("system".equals(source)) return "系统记录";
        if ("package_install".equals(source)) return "应用安装时间";
        if ("datadir".equals(source)) return "应用数据目录";
        if ("android_id".equals(source)) return "Android ID 估算";
        if ("build_time".equals(source)) return "设备构建时间估算";
        return "未知";
    }

    /**
     * 获取激活日期可信度 0-1
     */
    public float getActivationConfidence() {
        return deviceConfig.getActivationConfidence();
    }
    
    /**
     * 获取网络类型字符串
     */
    private String getNetworkTypeString(int type) {
        switch (type) {
            case ConnectivityManager.TYPE_WIFI:
                return "WiFi";
            case ConnectivityManager.TYPE_MOBILE:
                return "移动数据";
            case ConnectivityManager.TYPE_ETHERNET:
                return "以太网";
            default:
                return "未知";
        }
    }
    
    /**
     * 获取IP地址
     */
    private String getIPAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        if (sAddr.indexOf(':') < 0) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Unknown";
    }
    
    /**
     * 获取MAC地址
     */
    private String getMacAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (intf.getName().equalsIgnoreCase("wlan0")) {
                    byte[] mac = intf.getHardwareAddress();
                    if (mac != null) {
                        StringBuilder buf = new StringBuilder();
                        for (byte b : mac) {
                            buf.append(String.format("%02X:", b));
                        }
                        if (buf.length() > 0) {
                            buf.deleteCharAt(buf.length() - 1);
                        }
                        return buf.toString();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Unknown";
    }
    
    /**
     * 获取设备配置
     */
    public DeviceConfig getDeviceConfig() {
        return deviceConfig;
    }
    
    /**
     * 获取设备品牌
     */
    public String getDeviceBrand() {
        return deviceConfig.getFormattedBrand();
    }
    
    /**
     * 获取设备型号
     */
    public String getDeviceModel() {
        return deviceConfig.getFullModelName();
    }
    
    /**
     * 获取Android版本
     */
    public String getAndroidVersion() {
        return deviceConfig.getAndroidCodename();
    }
    
    /**
     * 获取处理器信息
     */
    public String getProcessorInfo() {
        StringBuilder info = new StringBuilder();
        info.append(deviceConfig.getCpuCores()).append("核 ");
        if (deviceConfig.getCpuFreqMax() > 0) {
            info.append(deviceConfig.getCpuFreqMax()).append("MHz");
        }
        return info.toString();
    }
    
    /**
     * 获取内存信息
     */
    public String getMemoryInfo() {
        return deviceConfig.getFormattedMemory();
    }
    
    /**
     * 获取存储信息
     */
    public String getStorageInfo() {
        return deviceConfig.getFormattedStorage();
    }
    
    /**
     * 获取屏幕信息
     */
    public String getScreenInfo() {
        return deviceConfig.getScreenResolution() + " " + deviceConfig.getFormattedScreenSize();
    }
    
    /**
     * 获取激活日期
     */
    public String getActivationDate() {
        return deviceConfig.getActivationDateStr();
    }
    
    /**
     * 获取使用天数
     */
    public int getUsageDays() {
        return deviceConfig.getUsageDays();
    }
}