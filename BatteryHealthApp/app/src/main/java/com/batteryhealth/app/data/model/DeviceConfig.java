package com.batteryhealth.app.data.model;

import android.os.Build;

import java.util.Locale;

/**
 * 设备配置信息类
 * 
 * 存储设备硬件配置、系统信息等
 */
public class DeviceConfig {
    
    // 基本信息
    private String brand; // 品牌
    private String manufacturer; // 制造商
    private String model; // 型号
    private String device; // 设备代号
    private String product; // 产品名
    private String board; // 主板
    private String hardware; // 硬件
    private String gpuInfo; // GPU 信息
    
    // 系统信息
    private String androidVersion; // Android版本
    private int sdkVersion; // SDK版本
    private String securityPatch; // 安全补丁级别
    private String buildId; // 构建ID
    private String fingerprint; // 指纹信息
    
    // 处理器信息
    private String cpuAbi; // CPU架构
    private String cpuAbi2; // 第二CPU架构
    private String[] supportedAbis; // 支持的架构列表
    private int cpuCores; // CPU核心数
    private int cpuFreqMax; // CPU最大频率 (MHz)
    private String cpuInfo; // CPU详细信息
    
    // 内存信息
    private long totalMemory; // 总内存 (MB)
    private long availableMemory; // 可用内存 (MB)
    private long totalStorage; // 总存储 (GB)
    private long availableStorage; // 可用存储 (GB)
    
    // 显示信息
    private int screenWidth; // 屏幕宽度
    private int screenHeight; // 屏幕高度
    private float screenDensity; // 屏幕密度
    private int screenDpi; // DPI
    private float screenSize; // 屏幕尺寸 (英寸)
    
    // 电池信息
    private String batteryTechnology; // 电池技术
    private int batteryCapacity; // 电池容量
    
    // 网络信息
    private String networkType; // 网络类型
    private String ipAddress; // IP地址
    private String macAddress; // MAC地址
    
    // 存储信息
    private String storageEncryption; // 存储加密状态
    
    // 激活信息
    private long activationDate; // 激活日期 (时间戳)
    private String activationDateStr; // 激活日期字符串
    private int usageDays; // 使用天数
    private String activationSource; // 激活日期来源
    private float activationConfidence; // 激活日期可信度 0-1
    
    public DeviceConfig() {
        // 初始化基本信息
        this.brand = Build.BRAND;
        this.manufacturer = Build.MANUFACTURER;
        this.model = Build.MODEL;
        this.device = Build.DEVICE;
        this.product = Build.PRODUCT;
        this.board = Build.BOARD;
        this.hardware = Build.HARDWARE;
        
        // 初始化系统信息
        this.androidVersion = Build.VERSION.RELEASE;
        this.sdkVersion = Build.VERSION.SDK_INT;
        this.securityPatch = Build.VERSION.SECURITY_PATCH;
        this.buildId = Build.ID;
        this.fingerprint = Build.FINGERPRINT;
        
        // 初始化处理器信息
        this.cpuAbi = Build.CPU_ABI;
        this.cpuAbi2 = Build.CPU_ABI2;
        this.supportedAbis = Build.SUPPORTED_ABIS;
    }
    
    // Getters and Setters
    public String getBrand() {
        return brand;
    }
    
    public void setBrand(String brand) {
        this.brand = brand;
    }
    
    public String getManufacturer() {
        return manufacturer;
    }
    
    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public String getDevice() {
        return device;
    }
    
    public void setDevice(String device) {
        this.device = device;
    }
    
    public String getProduct() {
        return product;
    }
    
    public void setProduct(String product) {
        this.product = product;
    }
    
    public String getBoard() {
        return board;
    }
    
    public void setBoard(String board) {
        this.board = board;
    }
    
    public String getHardware() {
        return hardware;
    }
    
    public void setHardware(String hardware) {
        this.hardware = hardware;
    }

    public String getGpuInfo() {
        return gpuInfo;
    }

    public void setGpuInfo(String gpuInfo) {
        this.gpuInfo = gpuInfo;
    }

    public String getAndroidVersion() {
        return androidVersion;
    }
    
    public void setAndroidVersion(String androidVersion) {
        this.androidVersion = androidVersion;
    }
    
    public int getSdkVersion() {
        return sdkVersion;
    }
    
    public void setSdkVersion(int sdkVersion) {
        this.sdkVersion = sdkVersion;
    }
    
    public String getSecurityPatch() {
        return securityPatch;
    }
    
    public void setSecurityPatch(String securityPatch) {
        this.securityPatch = securityPatch;
    }
    
    public String getBuildId() {
        return buildId;
    }
    
    public void setBuildId(String buildId) {
        this.buildId = buildId;
    }
    
    public String getFingerprint() {
        return fingerprint;
    }
    
    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }
    
    public String getCpuAbi() {
        return cpuAbi;
    }
    
    public void setCpuAbi(String cpuAbi) {
        this.cpuAbi = cpuAbi;
    }
    
    public String getCpuAbi2() {
        return cpuAbi2;
    }
    
    public void setCpuAbi2(String cpuAbi2) {
        this.cpuAbi2 = cpuAbi2;
    }
    
    public String[] getSupportedAbis() {
        return supportedAbis;
    }
    
    public void setSupportedAbis(String[] supportedAbis) {
        this.supportedAbis = supportedAbis;
    }
    
    public int getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(int cpuCores) {
        this.cpuCores = cpuCores;
    }

    public int getCpuFreqMax() {
        return cpuFreqMax;
    }

    public void setCpuFreqMax(int cpuFreqMax) {
        this.cpuFreqMax = cpuFreqMax;
    }

    public String getCpuInfo() {
        return cpuInfo;
    }

    public void setCpuInfo(String cpuInfo) {
        this.cpuInfo = cpuInfo;
    }
    
    public long getTotalMemory() {
        return totalMemory;
    }
    
    public void setTotalMemory(long totalMemory) {
        this.totalMemory = totalMemory;
    }
    
    public long getAvailableMemory() {
        return availableMemory;
    }
    
    public void setAvailableMemory(long availableMemory) {
        this.availableMemory = availableMemory;
    }
    
    public long getTotalStorage() {
        return totalStorage;
    }
    
    public void setTotalStorage(long totalStorage) {
        this.totalStorage = totalStorage;
    }
    
    public long getAvailableStorage() {
        return availableStorage;
    }
    
    public void setAvailableStorage(long availableStorage) {
        this.availableStorage = availableStorage;
    }
    
    public int getScreenWidth() {
        return screenWidth;
    }
    
    public void setScreenWidth(int screenWidth) {
        this.screenWidth = screenWidth;
    }
    
    public int getScreenHeight() {
        return screenHeight;
    }
    
    public void setScreenHeight(int screenHeight) {
        this.screenHeight = screenHeight;
    }
    
    public float getScreenDensity() {
        return screenDensity;
    }
    
    public void setScreenDensity(float screenDensity) {
        this.screenDensity = screenDensity;
    }
    
    public int getScreenDpi() {
        return screenDpi;
    }
    
    public void setScreenDpi(int screenDpi) {
        this.screenDpi = screenDpi;
    }
    
    public float getScreenSize() {
        return screenSize;
    }
    
    public void setScreenSize(float screenSize) {
        this.screenSize = screenSize;
    }
    
    public String getBatteryTechnology() {
        return batteryTechnology;
    }
    
    public void setBatteryTechnology(String batteryTechnology) {
        this.batteryTechnology = batteryTechnology;
    }
    
    public int getBatteryCapacity() {
        return batteryCapacity;
    }
    
    public void setBatteryCapacity(int batteryCapacity) {
        this.batteryCapacity = batteryCapacity;
    }
    
    public String getNetworkType() {
        return networkType;
    }
    
    public void setNetworkType(String networkType) {
        this.networkType = networkType;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public String getMacAddress() {
        return macAddress;
    }
    
    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getStorageEncryption() {
        return storageEncryption;
    }

    public void setStorageEncryption(String storageEncryption) {
        this.storageEncryption = storageEncryption;
    }
    
    public long getActivationDate() {
        return activationDate;
    }
    
    public void setActivationDate(long activationDate) {
        this.activationDate = activationDate;
    }
    
    public String getActivationDateStr() {
        return activationDateStr;
    }
    
    public void setActivationDateStr(String activationDateStr) {
        this.activationDateStr = activationDateStr;
    }
    
    public int getUsageDays() {
        return usageDays;
    }
    
    public void setUsageDays(int usageDays) {
        this.usageDays = usageDays;
    }

    public String getActivationSource() {
        return activationSource;
    }

    public void setActivationSource(String activationSource) {
        this.activationSource = activationSource;
    }

    public float getActivationConfidence() {
        return activationConfidence;
    }

    public void setActivationConfidence(float activationConfidence) {
        this.activationConfidence = activationConfidence;
    }

    /**
     * 获取格式化的品牌名
     */
    public String getFormattedBrand() {
        if (brand == null) return "Unknown";
        return brand.substring(0, 1).toUpperCase(Locale.ROOT) + brand.substring(1).toLowerCase(Locale.ROOT);
    }
    
    /**
     * 获取完整型号名
     */
    public String getFullModelName() {
        return String.format("%s %s", getFormattedBrand(), model);
    }
    
    /**
     * 获取格式化的内存大小（按营销规格取整，如 11.7 GB 显示为 12 GB）。
     */
    public String getFormattedMemory() {
        int gb = getMarketingTotalMemoryGb();
        if (gb > 0) {
            return String.format(Locale.getDefault(), "%d GB", gb);
        }
        if (totalMemory > 0) {
            return totalMemory >= 1024
                    ? String.format(Locale.getDefault(), "%.1f GB", totalMemory / 1024.0)
                    : String.format(Locale.getDefault(), "%d MB", totalMemory);
        }
        return "Unknown";
    }

    /**
     * 按标准 RAM 营销规格取整：根据实际总内存字节数匹配 1/2/3/4/6/8/12/16/18/24/32/48/64 GB。
     */
    public int getMarketingTotalMemoryGb() {
        if (totalMemory <= 0) return 0;
        double actualGb = totalMemory / 1024.0;
        int[] standards = {1, 2, 3, 4, 6, 8, 12, 16, 18, 24, 32, 48, 64};
        int best = standards[standards.length - 1];
        double minDiff = Double.MAX_VALUE;
        for (int size : standards) {
            double diff = Math.abs(actualGb - size);
            if (diff < minDiff) {
                minDiff = diff;
                best = size;
            }
        }
        // 偏差超过 3GB 时放弃匹配，保持原始值
        if (minDiff > 3.0) return 0;
        return best;
    }

    /**
     * 获取格式化的存储大小（保留一位小数，更贴近系统设置显示）。
     */
    public String getFormattedStorage() {
        if (totalStorage <= 0) return "Unknown";
        return totalStorage >= 100
                ? String.format(Locale.getDefault(), "%d GB", totalStorage)
                : String.format(Locale.getDefault(), "%.1f GB", totalStorage / 1.0);
    }
    
    /**
     * 获取屏幕分辨率
     */
    public String getScreenResolution() {
        return String.format(Locale.getDefault(), "%d x %d", screenWidth, screenHeight);
    }
    
    /**
     * 获取格式化的屏幕尺寸
     */
    public String getFormattedScreenSize() {
        if (screenSize <= 0) return "Unknown";
        return String.format(Locale.getDefault(), "%.1f\"", screenSize);
    }
    
    /**
     * 获取Android版本代号
     */
    public String getAndroidCodename() {
        switch (sdkVersion) {
            case Build.VERSION_CODES.BAKLAVA: return "Android 16";
            case Build.VERSION_CODES.VANILLA_ICE_CREAM: return "Android 15";
            case Build.VERSION_CODES.UPSIDE_DOWN_CAKE: return "Android 14";
            case Build.VERSION_CODES.TIRAMISU: return "Android 13";
            case Build.VERSION_CODES.S_V2: return "Android 12L";
            case Build.VERSION_CODES.S: return "Android 12";
            case Build.VERSION_CODES.R: return "Android 11";
            case Build.VERSION_CODES.Q: return "Android 10";
            case Build.VERSION_CODES.P: return "Android 9";
            case Build.VERSION_CODES.O_MR1: return "Android 8.1";
            case Build.VERSION_CODES.O: return "Android 8.0";
            default: return "Android " + androidVersion;
        }
    }
    
    /**
     * 判断是否为国内品牌
     */
    public boolean isDomesticBrand() {
        if (brand == null) return false;
        String lowerBrand = brand.toLowerCase(Locale.ROOT);
        return lowerBrand.contains("xiaomi") || lowerBrand.contains("redmi") ||
               lowerBrand.contains("huawei") || lowerBrand.contains("honor") ||
               lowerBrand.contains("oppo") || lowerBrand.contains("realme") ||
               lowerBrand.contains("vivo") || lowerBrand.contains("iqoo") ||
               lowerBrand.contains("oneplus") || lowerBrand.contains("meizu") ||
               lowerBrand.contains("nubia") || lowerBrand.contains("redmagic") ||
               lowerBrand.contains("zte") || lowerBrand.contains("lenovo");
    }
}