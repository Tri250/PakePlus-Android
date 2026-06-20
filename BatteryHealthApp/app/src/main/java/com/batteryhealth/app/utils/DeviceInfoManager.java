package com.batteryhealth.app.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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

        return "未识别";
    }

    private String readCpuInfoFromProc() {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String lower = line.toLowerCase(Locale.ROOT);
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
        return v;
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

    /**
     * 根据设备品牌/型号返回常见零部件供应商参考信息。
     * 数据基于公开拆机资料与行业常见组合，仅作参考。
     */
    public Map<String, String> getComponentSuppliers(DeviceConfig config) {
        Map<String, String> suppliers = new HashMap<>();
        if (config == null) {
            return suppliers;
        }
        String brand = config.getBrand();
        String brandLower = brand != null ? brand.toLowerCase(Locale.ROOT) : "";
        String model = config.getModel();
        String modelLower = model != null ? model.toLowerCase(Locale.ROOT) : "";

        if (brandLower.contains("xiaomi") || brandLower.contains("redmi")) {
            suppliers.put("屏幕", "华星光电 / 三星 / 天马");
            suppliers.put("后置摄像头", "索尼 / 三星 / OmniVision");
            suppliers.put("电池", "ATL / 飞毛腿");
            suppliers.put("硬盘/闪存", "三星 / 海力士 / 铠侠");
        } else if (brandLower.contains("huawei") || brandLower.contains("honor")) {
            suppliers.put("屏幕", "京东方 / 维信诺 / 天马");
            suppliers.put("后置摄像头", "索尼 / 豪威");
            suppliers.put("电池", "ATL / 欣旺达");
            suppliers.put("硬盘/闪存", "长江存储 / 三星");
        } else if (brandLower.contains("oppo") || brandLower.contains("oneplus") || brandLower.contains("realme")) {
            suppliers.put("屏幕", "三星 / 京东方 / 天马");
            suppliers.put("后置摄像头", "索尼 / 三星");
            suppliers.put("电池", "ATL / 欣旺达");
            suppliers.put("硬盘/闪存", "三星 / 海力士");
        } else if (brandLower.contains("vivo") || brandLower.contains("iqoo")) {
            suppliers.put("屏幕", "三星 / 京东方 / 维信诺");
            suppliers.put("后置摄像头", "索尼 / 三星");
            suppliers.put("电池", "ATL / 飞毛腿");
            suppliers.put("硬盘/闪存", "三星 / 海力士");
        } else if (brandLower.contains("meizu")) {
            suppliers.put("屏幕", "三星 / 京东方");
            suppliers.put("后置摄像头", "索尼 / 三星");
            suppliers.put("电池", "ATL / 欣旺达");
            suppliers.put("硬盘/闪存", "三星 / 海力士");
        } else if (brandLower.contains("nubia") || brandLower.contains("redmagic")) {
            suppliers.put("屏幕", "京东方 / 维信诺");
            suppliers.put("后置摄像头", "索尼 / 三星");
            suppliers.put("电池", "ATL / 欣旺达");
            suppliers.put("硬盘/闪存", "三星 / 海力士");
        } else {
            suppliers.put("屏幕", "参考数据");
            suppliers.put("后置摄像头", "参考数据");
            suppliers.put("电池", "ATL / 宁德时代");
            suppliers.put("硬盘/闪存", "三星 / 海力士 / 铠侠");
        }

        // 根据型号关键词做少量细化
        if (modelLower.contains("ultra") || modelLower.contains("pro+") || modelLower.contains("ultimate")) {
            suppliers.put("屏幕", "三星 / 顶级国产屏");
        }
        return suppliers;
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
                String lower = line.toLowerCase(Locale.ROOT);
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
            String soc = SystemPropertiesCompat.getSoC();
            if (soc != null && !soc.isEmpty()) {
                cpuResult = soc;
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
