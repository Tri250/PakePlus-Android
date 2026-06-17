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
        try {
            // 获取CPU核心数
            int cores = Runtime.getRuntime().availableProcessors();
            deviceConfig.setCpuCores(cores);
            
            // 读取CPU信息
            File cpuInfoFile = new File("/proc/cpuinfo");
            if (cpuInfoFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(cpuInfoFile));
                StringBuilder cpuInfo = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Hardware") || line.contains("Processor") || 
                        line.contains("model name")) {
                        cpuInfo.append(line).append("\n");
                    }
                }
                reader.close();
                deviceConfig.setCpuInfo(cpuInfo.toString().trim());
            }
            
            // 读取CPU频率
            File maxFreqFile = new File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            if (maxFreqFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(maxFreqFile));
                String line = reader.readLine();
                reader.close();
                if (line != null) {
                    int maxFreq = Integer.parseInt(line.trim()) / 1000; // 转换为MHz
                    deviceConfig.setCpuFreqMax(maxFreq);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 加载内存信息
     */
    private void loadMemoryInfo() {
        try {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            
            // 总内存 (MB)
            long totalMemory = memoryInfo.totalMem / (1024 * 1024);
            deviceConfig.setTotalMemory(totalMemory);
            
            // 可用内存 (MB)
            long availableMemory = memoryInfo.availMem / (1024 * 1024);
            deviceConfig.setAvailableMemory(availableMemory);
            
        } catch (Exception e) {
            e.printStackTrace();
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
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(metrics);
            
            deviceConfig.setScreenWidth(metrics.widthPixels);
            deviceConfig.setScreenHeight(metrics.heightPixels);
            deviceConfig.setScreenDensity(metrics.density);
            deviceConfig.setScreenDpi(metrics.densityDpi);
            
            // 计算屏幕尺寸 (英寸)
            float widthInches = metrics.widthPixels / metrics.xdpi;
            float heightInches = metrics.heightPixels / metrics.ydpi;
            double screenSize = Math.sqrt(widthInches * widthInches + heightInches * heightInches);
            deviceConfig.setScreenSize((float) screenSize);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 加载电池信息
     */
    private void loadBatteryInfo() {
        try {
            // 读取电池技术
            File techFile = new File("/sys/class/power_supply/battery/technology");
            if (techFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(techFile));
                String technology = reader.readLine();
                reader.close();
                if (technology != null) {
                    deviceConfig.setBatteryTechnology(technology);
                }
            }
            
            // 读取电池容量
            File capacityFile = new File("/sys/class/power_supply/battery/charge_full_design");
            if (capacityFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(capacityFile));
                String line = reader.readLine();
                reader.close();
                if (line != null) {
                    int capacity = Integer.parseInt(line.trim()) / 1000; // 转换为mAh
                    deviceConfig.setBatteryCapacity(capacity);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 加载网络信息
     */
    private void loadNetworkInfo() {
        try {
            // 获取网络类型
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork != null) {
                deviceConfig.setNetworkType(getNetworkTypeString(activeNetwork.getType()));
            }
            
            // 获取IP地址
            deviceConfig.setIpAddress(getIPAddress());
            
            // 获取MAC地址
            deviceConfig.setMacAddress(getMacAddress());
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 加载激活信息
     */
    private void loadActivationInfo() {
        try {
            // 尝试从系统设置获取首次启动时间
            long firstBootTime = Settings.Global.getLong(context.getContentResolver(), 
                    Settings.Global.BOOT_COUNT, 0);
            
            // 使用设备构建时间作为激活时间的估算
            long buildTime = Build.TIME;
            deviceConfig.setActivationDate(buildTime);
            
            // 计算使用天数
            long currentTime = System.currentTimeMillis();
            long usageDays = (currentTime - buildTime) / (1000 * 60 * 60 * 24);
            deviceConfig.setUsageDays((int) usageDays);
            
            // 格式化日期
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            deviceConfig.setActivationDateStr(sdf.format(new java.util.Date(buildTime)));
            
        } catch (Exception e) {
            e.printStackTrace();
        }
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