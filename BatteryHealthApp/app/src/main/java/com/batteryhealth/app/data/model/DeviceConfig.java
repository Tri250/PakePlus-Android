package com.batteryhealth.app.data.model;

import android.os.Build;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.TypeConverters;

import com.batteryhealth.app.data.database.Converters;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 设备配置信息实体类
 * 存储设备硬件配置、系统信息等，同时作为 Room 实体持久化。
 */
@Entity(tableName = "device_config")
@TypeConverters({Converters.class})
public class DeviceConfig {

    @PrimaryKey(autoGenerate = true)
    private long id;

    // 基本信息
    @ColumnInfo(name = "brand")
    private String brand; // 品牌

    @ColumnInfo(name = "manufacturer")
    private String manufacturer; // 制造商

    @ColumnInfo(name = "model")
    private String model; // 型号

    @ColumnInfo(name = "device")
    private String device; // 设备代号

    @ColumnInfo(name = "product")
    private String product; // 产品名

    @ColumnInfo(name = "board")
    private String board; // 主板

    @ColumnInfo(name = "hardware")
    private String hardware; // 硬件

    @ColumnInfo(name = "gpu_info")
    private String gpuInfo; // GPU 信息

    // 系统信息
    @ColumnInfo(name = "android_version")
    private String androidVersion; // Android版本

    @ColumnInfo(name = "sdk_version")
    private int sdkVersion; // SDK版本

    @ColumnInfo(name = "security_patch")
    private String securityPatch; // 安全补丁级别

    @ColumnInfo(name = "build_id")
    private String buildId; // 构建ID

    @ColumnInfo(name = "fingerprint")
    private String fingerprint; // 指纹信息

    // 处理器信息
    @ColumnInfo(name = "cpu_abi")
    private String cpuAbi; // CPU架构

    @ColumnInfo(name = "cpu_abi2")
    private String cpuAbi2; // 第二CPU架构

    @ColumnInfo(name = "supported_abis")
    private String[] supportedAbis; // 支持的架构列表

    @ColumnInfo(name = "cpu_cores")
    private int cpuCores; // CPU核心数

    @ColumnInfo(name = "cpu_freq_max")
    private int cpuFreqMax; // CPU最大频率 (MHz)

    @ColumnInfo(name = "cpu_info")
    private String cpuInfo; // CPU详细信息

    @ColumnInfo(name = "processor")
    private String processor; // 处理器营销名/档次

    // 内存信息
    @ColumnInfo(name = "total_memory")
    private long totalMemory; // 总内存 (MB)

    @ColumnInfo(name = "available_memory")
    private long availableMemory; // 可用内存 (MB)

    @ColumnInfo(name = "total_storage")
    private long totalStorage; // 总存储 (GB)

    @ColumnInfo(name = "available_storage")
    private long availableStorage; // 可用存储 (GB)

    @ColumnInfo(name = "ram_gb")
    private int ramGB; // 内存 GB（取整）

    @ColumnInfo(name = "storage_gb")
    private int storageGB; // 存储 GB（取整）

    // 显示信息
    @ColumnInfo(name = "screen_width")
    private int screenWidth; // 屏幕宽度

    @ColumnInfo(name = "screen_height")
    private int screenHeight; // 屏幕高度

    @ColumnInfo(name = "screen_density")
    private float screenDensity; // 屏幕密度

    @ColumnInfo(name = "screen_dpi")
    private int screenDpi; // DPI

    @ColumnInfo(name = "screen_size")
    private float screenSize; // 屏幕尺寸 (英寸)

    @ColumnInfo(name = "screen_resolution")
    private String screenResolution; // 屏幕分辨率字符串

    // 电池信息
    @ColumnInfo(name = "battery_technology")
    private String batteryTechnology; // 电池技术

    @ColumnInfo(name = "battery_capacity")
    private int batteryCapacity; // 电池容量

    // 网络信息
    @ColumnInfo(name = "network_type")
    private String networkType; // 网络类型

    @ColumnInfo(name = "ip_address")
    private String ipAddress; // IP地址

    @ColumnInfo(name = "mac_address")
    private String macAddress; // MAC地址

    // 激活信息
    @ColumnInfo(name = "activation_date")
    private long activationDate; // 激活日期 (时间戳)

    @ColumnInfo(name = "activation_date_str")
    private String activationDateStr; // 激活日期字符串

    @ColumnInfo(name = "usage_days")
    private int usageDays; // 使用天数

    @ColumnInfo(name = "used_days")
    private int usedDays; // 使用天数（别名，与任务字段对齐）

    @ColumnInfo(name = "activation_source")
    private String activationSource; // 激活日期来源

    @ColumnInfo(name = "activation_confidence")
    private float activationConfidence; // 激活日期可信度 0-1

    // 配置评分与供应商
    @ColumnInfo(name = "config_score")
    private float configScore; // 配置评分 0-10

    @ColumnInfo(name = "component_suppliers")
    private Map<String, String> componentSuppliers; // 零部件供应商映射

    public DeviceConfig() {
    }

    @Ignore
    public DeviceConfig(boolean initBuildInfo) {
        if (initBuildInfo) {
            this.brand = Build.BRAND;
            this.manufacturer = Build.MANUFACTURER;
            this.model = Build.MODEL;
            this.device = Build.DEVICE;
            this.product = Build.PRODUCT;
            this.board = Build.BOARD;
            this.hardware = Build.HARDWARE;
            this.androidVersion = Build.VERSION.RELEASE;
            this.sdkVersion = Build.VERSION.SDK_INT;
            this.securityPatch = Build.VERSION.SECURITY_PATCH;
            this.buildId = Build.ID;
            this.fingerprint = Build.FINGERPRINT;
            this.cpuAbi = Build.CPU_ABI;
            this.cpuAbi2 = Build.CPU_ABI2;
            this.supportedAbis = Build.SUPPORTED_ABIS;
        }
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getDevice() { return device; }
    public void setDevice(String device) { this.device = device; }

    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }

    public String getBoard() { return board; }
    public void setBoard(String board) { this.board = board; }

    public String getHardware() { return hardware; }
    public void setHardware(String hardware) { this.hardware = hardware; }

    public String getGpuInfo() { return gpuInfo; }
    public void setGpuInfo(String gpuInfo) { this.gpuInfo = gpuInfo; }

    public String getAndroidVersion() { return androidVersion; }
    public void setAndroidVersion(String androidVersion) { this.androidVersion = androidVersion; }

    public int getSdkVersion() { return sdkVersion; }
    public void setSdkVersion(int sdkVersion) { this.sdkVersion = sdkVersion; }

    public String getSecurityPatch() { return securityPatch; }
    public void setSecurityPatch(String securityPatch) { this.securityPatch = securityPatch; }

    public String getBuildId() { return buildId; }
    public void setBuildId(String buildId) { this.buildId = buildId; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    public String getCpuAbi() { return cpuAbi; }
    public void setCpuAbi(String cpuAbi) { this.cpuAbi = cpuAbi; }

    public String getCpuAbi2() { return cpuAbi2; }
    public void setCpuAbi2(String cpuAbi2) { this.cpuAbi2 = cpuAbi2; }

    public String[] getSupportedAbis() { return supportedAbis; }
    public void setSupportedAbis(String[] supportedAbis) { this.supportedAbis = supportedAbis; }

    public int getCpuCores() { return cpuCores; }
    public void setCpuCores(int cpuCores) { this.cpuCores = cpuCores; }

    public int getCpuFreqMax() { return cpuFreqMax; }
    public void setCpuFreqMax(int cpuFreqMax) { this.cpuFreqMax = cpuFreqMax; }

    public String getCpuInfo() { return cpuInfo; }
    public void setCpuInfo(String cpuInfo) { this.cpuInfo = cpuInfo; }

    public String getProcessor() { return processor; }
    public void setProcessor(String processor) { this.processor = processor; }

    public long getTotalMemory() { return totalMemory; }
    public void setTotalMemory(long totalMemory) { this.totalMemory = totalMemory; }

    public long getAvailableMemory() { return availableMemory; }
    public void setAvailableMemory(long availableMemory) { this.availableMemory = availableMemory; }

    public long getTotalStorage() { return totalStorage; }
    public void setTotalStorage(long totalStorage) { this.totalStorage = totalStorage; }

    public long getAvailableStorage() { return availableStorage; }
    public void setAvailableStorage(long availableStorage) { this.availableStorage = availableStorage; }

    public int getRamGB() { return ramGB; }
    public void setRamGB(int ramGB) { this.ramGB = ramGB; }

    public int getStorageGB() { return storageGB; }
    public void setStorageGB(int storageGB) { this.storageGB = storageGB; }

    public int getScreenWidth() { return screenWidth; }
    public void setScreenWidth(int screenWidth) { this.screenWidth = screenWidth; }

    public int getScreenHeight() { return screenHeight; }
    public void setScreenHeight(int screenHeight) { this.screenHeight = screenHeight; }

    public float getScreenDensity() { return screenDensity; }
    public void setScreenDensity(float screenDensity) { this.screenDensity = screenDensity; }

    public int getScreenDpi() { return screenDpi; }
    public void setScreenDpi(int screenDpi) { this.screenDpi = screenDpi; }

    public float getScreenSize() { return screenSize; }
    public void setScreenSize(float screenSize) { this.screenSize = screenSize; }

    public String getScreenResolution() {
        if (screenResolution == null && screenWidth > 0 && screenHeight > 0) {
            return String.format(Locale.getDefault(), "%d x %d", screenWidth, screenHeight);
        }
        return screenResolution;
    }
    public void setScreenResolution(String screenResolution) { this.screenResolution = screenResolution; }

    public String getBatteryTechnology() { return batteryTechnology; }
    public void setBatteryTechnology(String batteryTechnology) { this.batteryTechnology = batteryTechnology; }

    public int getBatteryCapacity() { return batteryCapacity; }
    public void setBatteryCapacity(int batteryCapacity) { this.batteryCapacity = batteryCapacity; }

    public String getNetworkType() { return networkType; }
    public void setNetworkType(String networkType) { this.networkType = networkType; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }

    public long getActivationDate() { return activationDate; }
    public void setActivationDate(long activationDate) { this.activationDate = activationDate; }

    public String getActivationDateStr() { return activationDateStr; }
    public void setActivationDateStr(String activationDateStr) { this.activationDateStr = activationDateStr; }

    public int getUsageDays() { return usageDays; }
    public void setUsageDays(int usageDays) { this.usageDays = usageDays; }

    public int getUsedDays() { return usedDays; }
    public void setUsedDays(int usedDays) { this.usedDays = usedDays; }

    public String getActivationSource() { return activationSource; }
    public void setActivationSource(String activationSource) { this.activationSource = activationSource; }

    public float getActivationConfidence() { return activationConfidence; }
    public void setActivationConfidence(float activationConfidence) { this.activationConfidence = activationConfidence; }

    public float getConfigScore() { return configScore; }
    public void setConfigScore(float configScore) { this.configScore = configScore; }

    public Map<String, String> getComponentSuppliers() {
        return componentSuppliers != null ? componentSuppliers : new HashMap<>();
    }
    public void setComponentSuppliers(Map<String, String> componentSuppliers) { this.componentSuppliers = componentSuppliers; }

    public String getFormattedBrand() {
        if (brand == null) return "Unknown";
        return brand.substring(0, 1).toUpperCase(Locale.ROOT) + brand.substring(1).toLowerCase(Locale.ROOT);
    }

    public String getFullModelName() {
        return String.format("%s %s", getFormattedBrand(), model);
    }

    public String getFormattedMemory() {
        if (totalMemory <= 0) return "Unknown";
        if (totalMemory >= 1024) {
            return String.format(Locale.getDefault(), "%.1f GB", totalMemory / 1024.0);
        }
        return String.format(Locale.getDefault(), "%d MB", totalMemory);
    }

    public String getFormattedStorage() {
        if (totalStorage <= 0) return "Unknown";
        return String.format(Locale.getDefault(), "%d GB", totalStorage);
    }

    public String getFormattedScreenSize() {
        if (screenSize <= 0) return "Unknown";
        return String.format(Locale.getDefault(), "%.1f\"", screenSize);
    }

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
