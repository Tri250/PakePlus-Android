package com.batteryhealth.app.utils;

import android.app.ActivityManager;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.batteryhealth.app.R;
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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 设备信息收集器
 * 负责聚合 Build / ActivityManager / StatFs / sysfs / 本地机型数据库等信息。
 */
public class DeviceInfoManager {

    private static final String TAG = "DeviceInfoManager";

    private final Context context;
    private final DeviceDatabaseManager deviceDb;

    private DeviceConfig cachedConfig;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("config-loader"));
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
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/drm/card0/device/pp_dpm_sclk",
            "/sys/class/drm/renderD128/device/pp_dpm_sclk",
            // Android 16 新增 GPU 检测路径
            "/sys/class/devfreq/gpufreq/gpu_model",
            "/sys/class/gpu/gpu_model"
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
     * 检测设备是否支持旁路充电（Bypass Charging）。
     * ColorOS 16 特性：充电时绕过电池直接供电，减少充电发热。
     * 通过检测 /sys/class/power_supply/battery/bypass_charging 节点是否可读来判断。
     *
     * @return true 表示设备支持旁路充电
     */
    public boolean isBypassChargingSupported() {
        String path = "/sys/class/power_supply/battery/bypass_charging";
        File file = new File(path);
        return file.exists() && file.canRead();
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
     * 获取处理器详细信息（多路 fallback 直至给出可见名称）。
     */
    public String getProcessorInfo() {
        // 1. 本地机型数据库（最准确，含营销名与型号）
        String dbProcessor = deviceDb.getProcessorName();
        if (dbProcessor != null && !dbProcessor.isEmpty()) {
            return dbProcessor;
        }

        // 2. sysprop SoC 标识
        String soc = SystemPropertiesCompat.getSoC();
        if (soc != null && !soc.isEmpty()) {
            String normalized = normalizeProcessorName(soc);
            if (normalized != null) return normalized;
        }

        // 3. /proc/cpuinfo Hardware / model name / Processor 行
        String cpuInfo = readCpuInfoFromProc();
        if (cpuInfo != null && !cpuInfo.isEmpty()) {
            return cpuInfo;
        }

        // 4. 设备配置中已经收集的 CPU 字段
        DeviceConfig config = getDeviceConfig();
        if (config != null) {
            String cfgCpu = config.getCpuInfo();
            if (cfgCpu != null && !cfgCpu.isEmpty()) return cfgCpu;
        }

        return context.getString(R.string.status_not_recognized);
    }

    private String readCpuInfoFromProc() {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String lower = line.toLowerCase();
                if (lower.startsWith("hardware") || lower.startsWith("model name")
                        || lower.startsWith("processor") || lower.startsWith("chip name")) {
                    int idx = line.indexOf(':');
                    if (idx >= 0 && idx < line.length() - 1) {
                        String value = line.substring(idx + 1).trim();
                        if (!value.isEmpty() && !value.equalsIgnoreCase("unknown")) {
                            if (sb.length() > 0) sb.append(" · ");
                            sb.append(value);
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.d(TAG, "Failed to read /proc/cpuinfo: " + e.getMessage());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String normalizeProcessorName(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        // 移除 arch 前缀
        String[] archPrefixes = {"arm64-v8a,", "armeabi-v7a,", "x86_64,", "x86,"};
        for (String p : archPrefixes) {
            if (v.startsWith(p)) {
                v = v.substring(p.length()).trim();
            }
        }
        // 处理 2026 芯片组营销后缀
        String lower = v.toLowerCase(Locale.ROOT);
        // Snapdragon 8 Gen 4 / Gen 5 / Elite / Supreme
        if (lower.contains("gen4") || lower.contains("gen 4")) {
            v = v.replaceAll("(?i)gen\\s*4", "Gen 4");
        }
        if (lower.contains("gen5") || lower.contains("gen 5")) {
            v = v.replaceAll("(?i)gen\\s*5", "Gen 5");
        }
        if (lower.contains("elite")) {
            v = v.replaceAll("(?i)elite", "Elite");
        }
        if (lower.contains("supreme")) {
            v = v.replaceAll("(?i)supreme", "Supreme");
        }
        // Dimensity 9500 / 9400
        if (lower.contains("dimensity")) {
            if (lower.contains("9500")) {
                return "MediaTek Dimensity 9500";
            }
            if (lower.contains("9400")) {
                return "MediaTek Dimensity 9400";
            }
        }
        // Tensor G5
        if (lower.contains("tensor") && lower.contains("g5")) {
            return "Google Tensor G5";
        }
        return v;
    }

    /**
     * 获取激活日期来源文本。
     */
    public String getActivationSourceText() {
        DeviceConfig config = getDeviceConfig();
        return config != null ? config.getActivationSource() : context.getString(R.string.status_unknown);
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
                String lower = line.toLowerCase();
                // 扩展匹配关键词：覆盖 ARM 设备常见的 SoC 标识字段
                if (lower.startsWith("hardware") || lower.startsWith("model name")
                        || lower.startsWith("processor") || lower.startsWith("chip name")
                        || lower.startsWith("cpu part") || lower.startsWith("cpu implementer")
                        || lower.startsWith("soc name") || lower.startsWith("platform")) {
                    int idx = line.indexOf(':');
                    if (idx >= 0 && idx < line.length() - 1) {
                        String value = line.substring(idx + 1).trim();
                        if (!value.isEmpty() && !value.equalsIgnoreCase("unknown")) {
                            if (cpuInfo.length() > 0) cpuInfo.append(" · ");
                            cpuInfo.append(value);
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read cpuinfo", e);
        }

        // 如果 /proc/cpuinfo 没有拿到有效信息，尝试 sysprop
        String cpuResult = cpuInfo.toString().trim();
        if (cpuResult.isEmpty()) {
            // 优先使用 Android 16 新增的 SoC 属性（更准确的芯片型号名称）
            String[] newSocProps = {
                    "ro.boot.soc_model",
                    "ro.soc.model",
                    "ro.product.soc_model"
            };
            for (String prop : newSocProps) {
                String value = SystemPropertiesCompat.get(prop);
                if (value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value)) {
                    cpuResult = value;
                    break;
                }
            }
            // 回退到原有 SoC 检测
            if (cpuResult.isEmpty()) {
                String soc = SystemPropertiesCompat.getSoC();
                if (soc != null && !soc.isEmpty()) {
                    cpuResult = soc;
                }
            }
        }

        // 最后兜底：使用 Build.HARDWARE（通常包含 qcom/mtk/exynos 等平台标识）
        if (cpuResult.isEmpty() && Build.HARDWARE != null && !Build.HARDWARE.isEmpty()
                && !Build.HARDWARE.equalsIgnoreCase("unknown")) {
            cpuResult = formatHardwareName(Build.HARDWARE);
        }

        config.setCpuInfo(cpuResult);
    }

    /**
     * 格式化 Build.HARDWARE 为更可读的处理器名称。
     * 例如 "qcom" → "Qualcomm Snapdragon", "mt6789" → "MediaTek MT6789"
     */
    private String formatHardwareName(String hw) {
        if (hw == null || hw.isEmpty()) return "";
        String lower = hw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("qcom") || lower.contains("snapdragon")) {
            return "Qualcomm Snapdragon (" + hw + ")";
        }
        if (lower.startsWith("mt") || lower.startsWith("mtk")) {
            return "MediaTek " + hw.toUpperCase(Locale.ROOT);
        }
        if (lower.startsWith("exynos")) {
            return "Samsung Exynos (" + hw + ")";
        }
        if (lower.startsWith("kirin")) {
            return "HiSilicon Kirin (" + hw + ")";
        }
        if (lower.contains("unisoc") || lower.startsWith("ud7") || lower.startsWith("t7")
                || lower.startsWith("s8") || lower.startsWith("t3")) {
            return "UNISOC (" + hw + ")";
        }
        if (lower.startsWith("google")) {
            return "Google Tensor (" + hw + ")";
        }
        return hw;
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
        long totalBytes = -1;
        long availableBytes = -1;

        // Android 8+ 优先使用 StorageStatsManager，返回的是整机存储（与系统设置一致）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                StorageStatsManager ssm = (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
                StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                if (ssm != null && sm != null) {
                    // Android 16+ 使用 StorageManager.UUID_DEFAULT 处理 UUID，避免空指针
                    java.util.UUID uuid;
                    try {
                        String uuidStr = sm.getPrimaryStorageVolume().getUuid();
                        uuid = uuidStr != null ? java.util.UUID.fromString(uuidStr) : StorageManager.UUID_DEFAULT;
                    } catch (SecurityException se) {
                        // Android 16 可能因存储权限限制抛出 SecurityException，回退到 UUID_DEFAULT
                        Log.d(TAG, "StorageVolume UUID access denied on Android 16+, using UUID_DEFAULT: " + se.getMessage());
                        uuid = StorageManager.UUID_DEFAULT;
                    }
                    totalBytes = ssm.getTotalBytes(uuid);
                    availableBytes = ssm.getFreeBytes(uuid);
                }
            } catch (SecurityException e) {
                // Android 16+ 存储权限受限时的安全异常
                Log.d(TAG, "StorageStatsManager access denied, fallback to StatFs: " + e.getMessage());
            } catch (Exception e) {
                Log.d(TAG, "StorageStatsManager failed, fallback to StatFs: " + e.getMessage());
            }
        }

        // Android 16+ 检测存储加密状态
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            try {
                StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    android.os.storage.StorageVolume primaryVolume = sm.getPrimaryStorageVolume();
                    // 通过反射调用 isDirectoryEncrypted()（Android 16 新增 API）
                    try {
                        java.lang.reflect.Method isEncryptedMethod = primaryVolume.getClass()
                                .getMethod("isDirectoryEncrypted");
                        Object result = isEncryptedMethod.invoke(primaryVolume);
                        if (result instanceof Boolean) {
                            Log.d(TAG, "Primary storage encrypted: " + result);
                        }
                    } catch (NoSuchMethodException nsme) {
                        Log.d(TAG, "isDirectoryEncrypted() not available on this device");
                    }
                }
            } catch (SecurityException e) {
                Log.d(TAG, "Storage encryption check denied: " + e.getMessage());
            } catch (Exception e) {
                Log.d(TAG, "Storage encryption check failed: " + e.getMessage());
            }
        }

        // 回退：使用外部存储目录 StatFs
        if (totalBytes <= 0 || availableBytes <= 0) {
            try {
                File path = Environment.getExternalStorageDirectory();
                if (path != null) {
                    StatFs statFs = new StatFs(path.getPath());
                    long blockSize = statFs.getBlockSizeLong();
                    totalBytes = statFs.getBlockCountLong() * blockSize;
                    availableBytes = statFs.getAvailableBlocksLong() * blockSize;
                }
            } catch (Exception e) {
                Log.d(TAG, "External storage StatFs failed: " + e.getMessage());
            }
        }

        // 最后兜底：/data 分区
        if (totalBytes <= 0 || availableBytes <= 0) {
            try {
                StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
                long blockSize = statFs.getBlockSizeLong();
                totalBytes = statFs.getBlockCountLong() * blockSize;
                availableBytes = statFs.getAvailableBlocksLong() * blockSize;
            } catch (Exception e) {
                Log.d(TAG, "Data directory StatFs failed: " + e.getMessage());
            }
        }

        if (totalBytes > 0) {
            config.setTotalStorage(totalBytes / (1024 * 1024 * 1024));         // GB
        }
        if (availableBytes > 0) {
            config.setAvailableStorage(availableBytes / (1024 * 1024 * 1024)); // GB
        }
    }

    private void collectScreenInfo(DeviceConfig config) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        // Android 11+ 优先使用 WindowMetrics API（getRealMetrics 在 Android 16 已废弃）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                android.view.WindowMetrics windowMetrics = wm.getCurrentWindowMetrics();
                android.graphics.Rect bounds = windowMetrics.getBounds();
                config.setScreenWidth(bounds.width());
                config.setScreenHeight(bounds.height());
                // 从 WindowMetrics 获取密度
                float density = windowMetrics.getDensity();
                config.setScreenDensity(density);
                config.setScreenDpi((int) (density * 160f));
            } catch (Exception e) {
                Log.d(TAG, "WindowMetrics API failed, fallback to getRealMetrics: " + e.getMessage());
                // 回退到旧 API
                DisplayMetrics metrics = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(metrics);
                config.setScreenWidth(metrics.widthPixels);
                config.setScreenHeight(metrics.heightPixels);
                config.setScreenDensity(metrics.density);
                config.setScreenDpi(metrics.densityDpi);
            }
        } else {
            // 旧版 API 回退
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            config.setScreenWidth(metrics.widthPixels);
            config.setScreenHeight(metrics.heightPixels);
            config.setScreenDensity(metrics.density);
            config.setScreenDpi(metrics.densityDpi);
        }

        // 使用 densityDpi 计算对角线，xdpi/ydpi 在很多设备上不准确
        int dpi = config.getScreenDpi() > 0 ? config.getScreenDpi() : 160;
        double widthInches = config.getScreenWidth() / (double) dpi;
        double heightInches = config.getScreenHeight() / (double) dpi;
        double size = Math.sqrt(widthInches * widthInches + heightInches * heightInches);
        config.setScreenSize((float) size);
    }

    private void collectBatteryInfo(DeviceConfig config) {
        int dbCapacity = deviceDb.getDesignCapacity();
        if (dbCapacity > 0) {
            config.setBatteryCapacity(dbCapacity);
            config.setBatteryTechnology(context.getString(R.string.battery_technology_default));
            return;
        }

        try {
            String tech = readSysfsString(new String[]{
                    "/sys/class/power_supply/battery/technology",
                    "/sys/class/power_supply/bms/technology"
            }, context.getString(R.string.battery_technology_default));
            config.setBatteryTechnology(tech);
        } catch (Exception ignored) {
            config.setBatteryTechnology(context.getString(R.string.battery_technology_default));
        }
    }

    private void collectNetworkInfo(DeviceConfig config) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;

            // Android 10+ 优先使用 NetworkCapabilities API（getActiveNetworkInfo 已废弃）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.net.Network activeNetwork = cm.getActiveNetwork();
                android.net.NetworkCapabilities caps = activeNetwork != null
                        ? cm.getNetworkCapabilities(activeNetwork) : null;
                if (caps == null) {
                    config.setNetworkType(context.getString(R.string.status_no_network));
                    return;
                }
                if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                    config.setNetworkType("Wi-Fi");
                } else if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    // 通过 TelephonyManager 获取移动网络子类型
                    android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager)
                            context.getSystemService(Context.TELEPHONY_SERVICE);
                    int subtype = tm != null ? tm.getDataNetworkType() : android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN;
                    config.setNetworkType(getMobileNetworkType(subtype));
                } else {
                    config.setNetworkType(context.getString(R.string.status_mobile_data));
                }
            } else {
                // 旧版 API 回退
                android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if (activeNetwork == null || !activeNetwork.isConnected()) {
                    config.setNetworkType(context.getString(R.string.status_no_network));
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
                return context.getString(R.string.status_mobile_data);
        }
    }

    // endregion

    // region 激活日期

    private ActivationInfo collectActivationInfo() {
        ActivationInfo info = new ActivationInfo();
        ActivationDateHelper.Result result = ActivationDateHelper.detect(context);
        if (result.isValid()) {
            info.set(result.timestamp, result.source, result.confidence);
        } else {
            info.setUnknown();
        }
        return info;
    }

    /**
     * 公开接口：获取激活时间检测结果（含使用天数）。供其他模块（如 BatteryDataManager）使用。
     */
    public ActivationDateHelper.Result getActivationInfo() {
        return ActivationDateHelper.detect(context);
    }

    /**
     * 读取手机系统电子保卡激活日期。各品牌实现不同，这里按品牌依次尝试常见 Setting/Property。
     * 兼容保留：实际逻辑已迁到 ActivationDateHelper。
     */
    @Deprecated
    private long readElectronicWarrantyActivation() {
        ActivationDateHelper.Result result = ActivationDateHelper.detect(context);
        return result.isValid() ? result.timestamp : -1;
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

        // 2. 通过系统属性读取（增加 ro.opengles.version）
        String[] properties = {
                "ro.hardware.egl",
                "ro.hardware.vulkan",
                "ro.opengles.version",
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

        // 2b. Android 16 新增 GPU 检测属性（通过 SystemPropertiesCompat）
        String[] newGpuProps = {
                "ro.hardware.gpu",
                "ro.opengles.version"
        };
        for (String prop : newGpuProps) {
            String value = SystemPropertiesCompat.get(prop);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        // 3. 通过反射调用 GLES20.glGetString(GL_RENDERER)
        String glRenderer = getGlRendererViaReflection();
        if (glRenderer != null && !glRenderer.isEmpty()) {
            return glRenderer;
        }

        // 4. 从 /proc/gpuinfo 读取（部分设备存在）
        String procGpu = readFile("/proc/gpuinfo");
        if (procGpu != null && !procGpu.isEmpty()) {
            return procGpu.trim();
        }

        // 5. 从 /proc/cpuinfo 的 Hardware 字段推断 GPU 厂商
        String cpuHardware = getCpuHardware();
        if (cpuHardware != null) {
            String lower = cpuHardware.toLowerCase(Locale.ROOT);
            if (lower.contains("qcom") || lower.contains("qualcomm") || lower.contains("snapdragon")) {
                return context.getString(R.string.gpu_adreno);
            } else if (lower.contains("mtk") || lower.contains("mediatek")) {
                return context.getString(R.string.gpu_mali_mediatek);
            } else if (lower.contains("kirin") || lower.contains("hisilicon")) {
                return context.getString(R.string.gpu_mali_hisilicon);
            } else if (lower.contains("exynos")) {
                return context.getString(R.string.gpu_mali_samsung);
            }
        }

        return context.getString(R.string.status_not_recognized);
    }

    private String getGlRendererViaReflection() {
        try {
            Class<?> gles20Class = Class.forName("android.opengl.GLES20");
            java.lang.reflect.Method glGetStringMethod = gles20Class.getMethod("glGetString", int.class);
            // GL_RENDERER = 0x1F01
            Object result = glGetStringMethod.invoke(null, 0x1F01);
            if (result != null) {
                String renderer = result.toString();
                if (!renderer.isEmpty() && !renderer.contains("Emulator")) {
                    return renderer;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
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

    /**
     * 命名线程工厂，用于为线程池中的线程设置可读名称与未捕获异常处理器。
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            t.setUncaughtExceptionHandler((thread, ex) -> {
                Log.e("NamedThreadFactory", "Uncaught exception in thread " + thread.getName(), ex);
            });
            return t;
        }
    }
}
