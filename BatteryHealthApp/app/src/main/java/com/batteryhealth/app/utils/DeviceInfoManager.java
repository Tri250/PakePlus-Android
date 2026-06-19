package com.batteryhealth.app.utils;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.batteryhealth.app.data.model.DeviceConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 设备信息收集器
 * 负责聚合 Build / ActivityManager / StatFs / sysfs / 本地机型数据库等信息。
 */
public class DeviceInfoManager {

    private static final String TAG = "DeviceInfoManager";

    private final Context context;
    private final DeviceDatabaseManager deviceDb;

    private DeviceConfig cachedConfig;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // GPU 渲染器 sysfs / 属性候选路径
    private static final String[] GPU_RENDERER_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/gpu_model",
            "/sys/class/kgsl/kgsl-3d0/device/driver/name",
            "/sys/class/misc/mali0/device/utgard/clock",
            "/sys/class/misc/mali0/device/clock",
            "/sys/class/gpu/clk_level",
            "/sys/class/devfreq/gpufreq/max_freq",
            "/sys/class/devfreq/gpufreq/min_freq",
            "/sys/class/devfreq/gpufreq/cur_freq",
            "/sys/kernel/gpu/gpu_model",
            "/sys/module/msm_kgsl/parameters/kgsl_3d0_pwrrail",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq"
    };

    public DeviceInfoManager(Context context) {
        this.context = context.getApplicationContext();
        this.deviceDb = DeviceDatabaseManager.getInstance(this.context);
    }

    /**
     * 获取完整设备配置（同步，首次调用可能阻塞，建议 UI 层使用异步接口）。
     */
    public DeviceConfig getDeviceConfig() {
        if (cachedConfig != null) {
            return cachedConfig;
        }
        cachedConfig = buildDeviceConfig();
        return cachedConfig;
    }

    /**
     * 异步获取完整设备配置，避免主线程阻塞。
     */
    public void getDeviceConfigAsync(DeviceConfigCallback callback) {
        if (cachedConfig != null) {
            callback.onConfigLoaded(cachedConfig);
            return;
        }
        executor.submit(() -> {
            try {
                DeviceConfig config = buildDeviceConfig();
                mainHandler.post(() -> callback.onConfigLoaded(config));
            } catch (Exception e) {
                Log.e(TAG, "Error building device config async", e);
                mainHandler.post(() -> callback.onConfigLoadFailed(e));
            }
        });
    }

    private DeviceConfig buildDeviceConfig() {
        DeviceConfig config = new DeviceConfig();

        // 1. CPU 信息
        collectCpuInfo(config);

        // 2. 内存信息
        collectMemoryInfo(config);

        // 3. 存储信息
        collectStorageInfo(config);

        // 4. 屏幕信息
        collectScreenInfo(config);

        // 5. 电池基础信息
        collectBatteryInfo(config);

        // 6. 网络信息
        collectNetworkInfo(config);

        // 7. 激活日期（优先电子保卡）
        ActivationInfo activation = collectActivationInfo();
        config.setActivationDate(activation.timestamp);
        config.setActivationDateStr(activation.dateStr);
        config.setUsageDays(activation.usageDays);
        config.setActivationSource(activation.source);
        config.setActivationConfidence(activation.confidence);

        // 8. GPU 信息（保留主板字段原始值）
        String gpuInfo = collectGpuInfo();

        // 9. 使用机型数据库覆盖营销名称/处理器/屏幕规格
        DeviceDatabaseManager.DeviceEntry entry = deviceDb.findDevice();
        if (entry != null) {
            if (entry.marketName != null && !entry.marketName.isEmpty()) {
                config.setModel(entry.marketName);
            }
            if (entry.processor != null && !entry.processor.isEmpty()) {
                config.setCpuInfo(entry.processor);
            }
            if (entry.batteryMah > 0) {
                config.setBatteryCapacity(entry.batteryMah);
            }
        }
        config.setGpuInfo(gpuInfo);

        return config;
    }

    /**
     * 设备配置加载回调。
     */
    public interface DeviceConfigCallback {
        void onConfigLoaded(DeviceConfig config);
        void onConfigLoadFailed(Exception e);
    }

    /**
     * 获取 GPU 信息。
     */
    public String getGpuInfo() {
        return collectGpuInfo();
    }

    /**
     * 获取营销型号名。
     */
    public String getMarketModelName() {
        return deviceDb.getMarketName();
    }

    /**
     * 获取处理器营销名。
     */
    public String getProcessorName() {
        return deviceDb.getProcessorName();
    }

    /**
     * 获取处理器详细信息（优先本地数据库，其次 CPU 硬件信息）。
     */
    public String getProcessorInfo() {
        String dbProcessor = deviceDb.getProcessorName();
        if (dbProcessor != null && !dbProcessor.isEmpty()) {
            return dbProcessor;
        }
        DeviceConfig config = getDeviceConfig();
        return config != null ? config.getCpuInfo() : "未识别";
    }

    /**
     * 获取激活日期来源文本。
     */
    public String getActivationSourceText() {
        DeviceConfig config = getDeviceConfig();
        return config != null ? config.getActivationSource() : "未知";
    }

    /**
     * 获取激活日期可信度。
     */
    public float getActivationConfidence() {
        DeviceConfig config = getDeviceConfig();
        return config != null ? config.getActivationConfidence() : 0.0f;
    }

    /**
     * 获取设备已使用天数。
     */
    public int getUsageDays() {
        DeviceConfig config = getDeviceConfig();
        return config != null ? config.getUsageDays() : -1;
    }

    // region CPU / Memory / Storage / Screen

    private void collectCpuInfo(DeviceConfig config) {
        config.setCpuCores(Runtime.getRuntime().availableProcessors());

        int maxFreq = 0;
        for (int i = 0; i < config.getCpuCores(); i++) {
            try {
                String path = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq";
                String value = readFile(path);
                if (value != null) {
                    int freq = Integer.parseInt(value);
                    if (freq > maxFreq) maxFreq = freq;
                }
            } catch (Exception ignored) {
            }
        }
        config.setCpuFreqMax(maxFreq / 1000); // kHz -> MHz

        StringBuilder cpuInfo = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("Hardware") || line.startsWith("model name") || line.startsWith("Processor")) {
                    cpuInfo.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read cpuinfo", e);
        }
        config.setCpuInfo(cpuInfo.toString().trim());
    }

    private void collectMemoryInfo(DeviceConfig config) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memInfo);
            config.setTotalMemory((int) (memInfo.totalMem / (1024 * 1024)));       // MB
            config.setAvailableMemory((int) (memInfo.availMem / (1024 * 1024)));   // MB
        }
    }

    private void collectStorageInfo(DeviceConfig config) {
        StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
        long blockSize = statFs.getBlockSizeLong();
        long totalBlocks = statFs.getBlockCountLong();
        long availableBlocks = statFs.getAvailableBlocksLong();
        config.setTotalStorage(totalBlocks * blockSize / (1024 * 1024 * 1024));         // GB
        config.setAvailableStorage(availableBlocks * blockSize / (1024 * 1024 * 1024)); // GB
    }

    private void collectScreenInfo(DeviceConfig config) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        config.setScreenWidth(metrics.widthPixels);
        config.setScreenHeight(metrics.heightPixels);
        config.setScreenDensity(metrics.density);
        config.setScreenDpi(metrics.densityDpi);

        double widthInches = metrics.widthPixels / (double) metrics.xdpi;
        double heightInches = metrics.heightPixels / (double) metrics.ydpi;
        double size = Math.sqrt(widthInches * widthInches + heightInches * heightInches);
        config.setScreenSize((float) size);
    }

    private void collectBatteryInfo(DeviceConfig config) {
        int dbCapacity = deviceDb.getDesignCapacity();
        if (dbCapacity > 0) {
            config.setBatteryCapacity(dbCapacity);
            config.setBatteryTechnology("锂离子");
            return;
        }

        try {
            String tech = readSysfsString(new String[]{
                    "/sys/class/power_supply/battery/technology",
                    "/sys/class/power_supply/bms/technology"
            }, "锂离子");
            config.setBatteryTechnology(tech);
        } catch (Exception ignored) {
            config.setBatteryTechnology("锂离子");
        }
    }

    private void collectNetworkInfo(DeviceConfig config) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;

            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork == null || !activeNetwork.isConnected()) {
                config.setNetworkType("无网络");
                return;
            }

            int type = activeNetwork.getType();
            if (type == android.net.ConnectivityManager.TYPE_WIFI) {
                config.setNetworkType("Wi-Fi");
            } else if (type == android.net.ConnectivityManager.TYPE_MOBILE) {
                int subtype = activeNetwork.getSubtype();
                config.setNetworkType(getMobileNetworkType(subtype));
            } else {
                config.setNetworkType(activeNetwork.getTypeName());
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to collect network info: " + e.getMessage());
        }
    }

    private String getMobileNetworkType(int subtype) {
        switch (subtype) {
            case android.telephony.TelephonyManager.NETWORK_TYPE_LTE:
                return "4G";
            case android.telephony.TelephonyManager.NETWORK_TYPE_NR:
                return "5G";
            case android.telephony.TelephonyManager.NETWORK_TYPE_UMTS:
            case android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA:
            case android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA:
            case android.telephony.TelephonyManager.NETWORK_TYPE_HSPA:
            case android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP:
                return "3G";
            case android.telephony.TelephonyManager.NETWORK_TYPE_GPRS:
            case android.telephony.TelephonyManager.NETWORK_TYPE_EDGE:
            case android.telephony.TelephonyManager.NETWORK_TYPE_CDMA:
            case android.telephony.TelephonyManager.NETWORK_TYPE_1xRTT:
            case android.telephony.TelephonyManager.NETWORK_TYPE_IDEN:
                return "2G";
            default:
                return "移动数据";
        }
    }

    // endregion

    // region 激活日期

    private ActivationInfo collectActivationInfo() {
        ActivationInfo info = new ActivationInfo();

        // 0. 优先读取各品牌系统电子保卡激活日期（准确度最高）
        long warrantyTime = readElectronicWarrantyActivation();
        if (warrantyTime > 0) {
            info.set(warrantyTime, "electronic_warranty_card", 0.98f);
            return info;
        }

        // 1. 系统 FIRST_BOOT_TIME（部分国产 ROM 如 MIUI/HarmonyOS 提供）
        try {
            long firstBoot = Settings.Global.getLong(context.getContentResolver(), "first_boot_time", -1);
            if (firstBoot > 0) {
                info.set(firstBoot, "system_first_boot_time", 0.95f);
                return info;
            }
        } catch (Exception ignored) {
        }

        // 2. DevicePolicyManager 设备激活时间（企业/MDM 场景）
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null) {
                long provisioningTime = invokeLongMethod(dpm, "getProvisioningTime");
                if (provisioningTime > 0) {
                    info.set(provisioningTime, "device_policy_manager", 0.90f);
                    return info;
                }
            }
        } catch (Exception ignored) {
        }

        // 3. Google Play 服务首次安装时间（较稳定的首次使用参考）
        long gmsInstall = getPackageFirstInstallTime("com.google.android.gms");
        if (gmsInstall > 0) {
            info.set(gmsInstall, "gms_first_install", 0.85f);
            return info;
        }

        // 4. 系统框架首次安装时间
        long systemPackageInstall = getPackageFirstInstallTime("android");
        if (systemPackageInstall > 0) {
            info.set(systemPackageInstall, "system_framework_install", 0.80f);
            return info;
        }

        // 5. 本应用首次安装时间
        long appInstall = getPackageFirstInstallTime(context.getPackageName());
        if (appInstall > 0) {
            info.set(appInstall, "app_first_install", 0.60f);
            return info;
        }

        // 6. 数据目录创建时间
        try {
            File dataDir = context.getDataDir();
            if (dataDir != null) {
                long lastModified = dataDir.lastModified();
                if (lastModified > 0) {
                    info.set(lastModified, "app_data_directory", 0.40f);
                    return info;
                }
            }
        } catch (Exception ignored) {
        }

        // 7. 无法推断，标记为未知
        info.setUnknown();
        return info;
    }

    /**
     * 读取手机系统电子保卡激活日期。各品牌实现不同，这里按品牌依次尝试常见 Setting/Property。
     */
    private long readElectronicWarrantyActivation() {
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase(Locale.ROOT) : "";
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(Locale.ROOT) : "";

        // 小米 / 红米
        if (brand.contains("xiaomi") || brand.contains("redmi") || manufacturer.contains("xiaomi")) {
            long t = getSettingsLong(Settings.Secure.getUriFor("miui_activated").getLastPathSegment(), 0);
            if (t > 0) return t;
            t = getSettingsLong("miui_activated", 0);
            if (t > 0) return t;
            t = getSystemPropertyLong("ro.miui.activated");
            if (t > 0) return t;
        }

        // OPPO / realme / OnePlus（ColorOS / OxygenOS / realme UI）
        if (brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus")
                || manufacturer.contains("oppo") || manufacturer.contains("oneplus")) {
            long t = getSettingsLong("oppo_activated", 0);
            if (t > 0) return t;
            t = getSettingsLong("coloros_activated", 0);
            if (t > 0) return t;
            t = getSystemPropertyLong("ro.oppo.activated");
            if (t > 0) return t;
        }

        // vivo / iQOO
        if (brand.contains("vivo") || brand.contains("iqoo") || manufacturer.contains("vivo")) {
            long t = getSettingsLong("vivo_activated", 0);
            if (t > 0) return t;
            t = getSettingsLong("vivo_warranty_time", 0);
            if (t > 0) return t;
            t = getSystemPropertyLong("ro.vivo.activated");
            if (t > 0) return t;
        }

        // 华为 / 荣耀
        if (brand.contains("huawei") || brand.contains("honor")
                || manufacturer.contains("huawei")) {
            long t = getSettingsLong("huawei_warranty_time", 0);
            if (t > 0) return t;
            t = parseDateString(getSettingsString("huawei_activation_date"));
            if (t > 0) return t;
            t = getSystemPropertyLong("ro.hw.oem.activated");
            if (t > 0) return t;
        }

        // 魅族
        if (brand.contains("meizu") || manufacturer.contains("meizu")) {
            long t = getSettingsLong("meizu_activated", 0);
            if (t > 0) return t;
        }

        // 三星
        if (brand.contains("samsung") || manufacturer.contains("samsung")) {
            long t = getSettingsLong("samsung_activated", 0);
            if (t > 0) return t;
        }

        // 通用电子保卡 key（部分 ROM 会写入）
        long t = getSettingsLong("electronic_warranty_activated", 0);
        if (t > 0) return t;
        t = getSettingsLong("device_activated_time", 0);
        if (t > 0) return t;
        t = getSystemPropertyLong("ro.runtime.firstboot");
        if (t > 0) return t;

        return -1;
    }

    private long getSettingsLong(String key, long defaultValue) {
        try {
            return Settings.Secure.getLong(context.getContentResolver(), key, defaultValue);
        } catch (Exception e) {
            try {
                return Settings.Global.getLong(context.getContentResolver(), key, defaultValue);
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
    }

    private String getSettingsString(String key) {
        try {
            String value = Settings.Secure.getString(context.getContentResolver(), key);
            if (value != null && !value.isEmpty()) return value;
            return Settings.Global.getString(context.getContentResolver(), key);
        } catch (Exception e) {
            return null;
        }
    }

    private long getSystemPropertyLong(String propertyName) {
        try {
            String value = getSystemProperty(propertyName);
            if (value != null && !value.isEmpty()) {
                return Long.parseLong(value.trim());
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private long parseDateString(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return -1;
        String[] patterns = {"yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd", "yyyy-MM-dd HH:mm:ss"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
                Date date = sdf.parse(dateStr.trim());
                if (date != null) return date.getTime();
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    private long getPackageFirstInstallTime(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            return info.firstInstallTime;
        } catch (Exception e) {
            return -1;
        }
    }

    private long invokeLongMethod(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object result = method.invoke(target);
            if (result instanceof Long) return (Long) result;
        } catch (Exception ignored) {
        }
        return -1;
    }

    // endregion

    // region GPU

    private String collectGpuInfo() {
        // 1. 尝试读取 sysfs
        for (String path : GPU_RENDERER_PATHS) {
            String value = readFile(path);
            if (value != null && !value.isEmpty()) {
                return value.trim();
            }
        }

        // 2. 通过系统属性读取
        String[] properties = {
                "ro.hardware.egl",
                "ro.hardware.vulkan",
                "ro.product.board",
                "ro.board.platform",
                "ro.hardware"
        };
        for (String prop : properties) {
            String value = getSystemProperty(prop);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        // 3. 从 /proc/cpuinfo 的 Hardware 字段推断 GPU 厂商
        String cpuHardware = getCpuHardware();
        if (cpuHardware != null) {
            String lower = cpuHardware.toLowerCase(Locale.ROOT);
            if (lower.contains("qcom") || lower.contains("qualcomm") || lower.contains("snapdragon")) {
                return "Adreno GPU（高通）";
            } else if (lower.contains("mtk") || lower.contains("mediatek")) {
                return "Mali GPU（联发科）";
            } else if (lower.contains("kirin") || lower.contains("hisilicon")) {
                return "Mali GPU（海思麒麟）";
            } else if (lower.contains("exynos")) {
                return "Mali/Xclipse GPU（三星）";
            }
        }

        return "未识别";
    }

    private String getCpuHardware() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("Hardware")) {
                    return line.split(":", 2)[1].trim();
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    // endregion

    // region 工具方法

    private String readFile(String path) {
        File file = new File(path);
        if (!file.exists() || !file.canRead()) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            return null;
        }
        return sb.toString().trim();
    }

    private String readSysfsString(String[] paths, String defaultValue) {
        for (String path : paths) {
            String value = readFile(path);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return defaultValue;
    }

    @android.annotation.SuppressLint("PrivateApi")
    private String getSystemProperty(String propertyName) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getMethod("get", String.class);
            Object value = get.invoke(null, propertyName);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // endregion

    private static class ActivationInfo {
        long timestamp;
        String dateStr;
        int usageDays;
        String source;
        float confidence;

        void set(long timestamp, String source, float confidence) {
            this.timestamp = timestamp;
            this.source = source;
            this.confidence = confidence;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            this.dateStr = sdf.format(new Date(timestamp));
            this.usageDays = (int) ((System.currentTimeMillis() - timestamp) / (24 * 60 * 60 * 1000L));
        }

        void setUnknown() {
            this.timestamp = -1;
            this.source = "unknown";
            this.confidence = 0.0f;
            this.dateStr = "--";
            this.usageDays = -1;
        }
    }
}
