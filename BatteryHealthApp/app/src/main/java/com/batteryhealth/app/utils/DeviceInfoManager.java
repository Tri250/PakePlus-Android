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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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

    // Map common SoC identifiers to Chinese marketing names
    private static final Map<String, String> PROCESSOR_CN_NAMES = new HashMap<>();
    static {
        // Qualcomm Snapdragon series
        PROCESSOR_CN_NAMES.put("sm8950", "骁龙 8 Gen 1");
        PROCESSOR_CN_NAMES.put("sm8550", "骁龙 8 Gen 2");
        PROCESSOR_CN_NAMES.put("sm8650", "骁龙 8 Gen 3");
        PROCESSOR_CN_NAMES.put("sm8750", "骁龙 8 Elite");
        PROCESSOR_CN_NAMES.put("sm7675", "骁龙 7+ Gen 3");
        PROCESSOR_CN_NAMES.put("sm7550", "骁龙 7 Gen 1");
        PROCESSOR_CN_NAMES.put("sm7475", "骁龙 7+ Gen 2");
        PROCESSOR_CN_NAMES.put("sm7435", "骁龙 7 Gen 3");
        PROCESSOR_CN_NAMES.put("sm7635", "骁龙 7s Gen 3");
        PROCESSOR_CN_NAMES.put("sm7325", "骁龙 7 Gen 1");
        PROCESSOR_CN_NAMES.put("sm6450", "骁龙 6 Gen 1");
        PROCESSOR_CN_NAMES.put("sm6375", "骁龙 6 Gen 1");
        PROCESSOR_CN_NAMES.put("sm4450", "骁龙 4 Gen 2");
        PROCESSOR_CN_NAMES.put("sm4350", "骁龙 4 Gen 1");
        // MediaTek Dimensity series
        PROCESSOR_CN_NAMES.put("mt6893", "天玑 1200");
        PROCESSOR_CN_NAMES.put("mt6877", "天玑 920");
        PROCESSOR_CN_NAMES.put("mt6895", "天玑 9200");
        PROCESSOR_CN_NAMES.put("mt6983", "天玑 9200+");
        PROCESSOR_CN_NAMES.put("mt6985", "天玑 9300");
        PROCESSOR_CN_NAMES.put("mt6989", "天玑 9300+");
        PROCESSOR_CN_NAMES.put("mt6991", "天玑 9400");
        PROCESSOR_CN_NAMES.put("mt6995", "天玑 9400+");
        PROCESSOR_CN_NAMES.put("mt6878", "天玑 8250");
        PROCESSOR_CN_NAMES.put("mt6886", "天玑 8300");
        PROCESSOR_CN_NAMES.put("mt6896", "天玑 8400");
        PROCESSOR_CN_NAMES.put("mt6897", "天玑 8400-Ultra");
        PROCESSOR_CN_NAMES.put("mt6879", "天玑 8200");
        PROCESSOR_CN_NAMES.put("mt6882", "天玑 8350");
        PROCESSOR_CN_NAMES.put("mt6885", "天玑 7200");
        PROCESSOR_CN_NAMES.put("mt6889", "天玑 7200-Ultra");
        PROCESSOR_CN_NAMES.put("mt6855", "天玑 7300");
        PROCESSOR_CN_NAMES.put("mt6858", "天玑 7300-Ultra");
        PROCESSOR_CN_NAMES.put("mt6835", "天玑 7050");
        PROCESSOR_CN_NAMES.put("mt6833", "天玑 7000");
        // HiSilicon Kirin
        PROCESSOR_CN_NAMES.put("kirin9000s", "麒麟 9000S");
        PROCESSOR_CN_NAMES.put("kirin9010", "麒麟 9010");
        PROCESSOR_CN_NAMES.put("kirin9020", "麒麟 9020");
        // Samsung Exynos
        PROCESSOR_CN_NAMES.put("exynos2400", "Exynos 2400");
        PROCESSOR_CN_NAMES.put("exynos2200", "Exynos 2200");
        PROCESSOR_CN_NAMES.put("exynos2100", "Exynos 2100");
        // Google Tensor
        PROCESSOR_CN_NAMES.put("tensor_g5", "Tensor G5");
        PROCESSOR_CN_NAMES.put("tensor_g4", "Tensor G4");
        // UNISOC
        PROCESSOR_CN_NAMES.put("t820", "虎贲 T820");
        PROCESSOR_CN_NAMES.put("t760", "虎贲 T760");
        PROCESSOR_CN_NAMES.put("t750", "虎贲 T750");
        // Xiaomi custom
        PROCESSOR_CN_NAMES.put("xring_o1", "玄戒 O1");
    }

    private final Context context;
    private final DeviceDatabaseManager deviceDb;

    private DeviceConfig cachedConfig;
    private volatile String cachedGpuInfo;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("config-loader"));
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // GPU 渲染器 sysfs / 属性候选路径（仅包含型号名称路径，不包含频率路径）
    private static final String[] GPU_RENDERER_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/gpu_model",
            "/sys/class/kgsl/kgsl-3d0/device/driver/name",
            "/sys/kernel/gpu/gpu_model",
            "/sys/module/msm_kgsl/parameters/kgsl_3d0_pwrrail",
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
        if (cachedGpuInfo == null) {
            cachedGpuInfo = collectGpuInfo();
        }
        return cachedGpuInfo;
    }

    /**
     * 强制刷新 GPU 信息缓存（用于 UI 主动重新检测的场景）。
     */
    public void refreshGpuInfo() {
        cachedGpuInfo = collectGpuInfo();
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
            if (normalized != null) {
                String cnName = mapToChineseProcessorName(normalized);
                return cnName != null ? cnName : normalized;
            }
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

    private String mapToChineseProcessorName(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String lower = raw.toLowerCase(Locale.ROOT);

        // 1. Direct lookup in map
        for (Map.Entry<String, String> entry : PROCESSOR_CN_NAMES.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 2. Pattern matching for common SoC naming conventions
        // Qualcomm: smXXXX pattern
        if (lower.startsWith("sm")) {
            String numStr = lower.replace("sm", "").split("[^0-9]")[0];
            if (numStr.length() >= 4) {
                int num = Integer.parseInt(numStr.substring(0, 4));
                if (num >= 8700) return "骁龙 8 Elite";
                if (num >= 8600) return "骁龙 8 Gen 3";
                if (num >= 8500) return "骁龙 8 Gen 2";
                if (num >= 8400) return "骁龙 8+ Gen 1";
                if (num >= 8300) return "骁龙 8 Gen 1";
                if (num >= 7600) return "骁龙 7+ Gen 3";
                if (num >= 7500) return "骁龙 7 Gen 3";
                if (num >= 7400) return "骁龙 7+ Gen 2";
                if (num >= 7300) return "骁龙 7 Gen 1";
                if (num >= 6400) return "骁龙 6 Gen 1";
                if (num >= 6300) return "骁龙 6s Gen 1";
                if (num >= 4400) return "骁龙 4 Gen 2";
                if (num >= 4300) return "骁龙 4 Gen 1";
            }
        }

        // MediaTek: mtXXXX pattern
        if (lower.startsWith("mt") || lower.contains("dimensity")) {
            if (lower.contains("9400")) return "天玑 9400";
            if (lower.contains("9300")) return "天玑 9300";
            if (lower.contains("9200")) return "天玑 9200";
            if (lower.contains("8400")) return "天玑 8400";
            if (lower.contains("8350")) return "天玑 8350";
            if (lower.contains("8300")) return "天玑 8300";
            if (lower.contains("8250")) return "天玑 8250";
            if (lower.contains("8200")) return "天玑 8200";
            if (lower.contains("7300")) return "天玑 7300";
            if (lower.contains("7200")) return "天玑 7200";
            if (lower.contains("7050")) return "天玑 7050";
            if (lower.contains("7000")) return "天玑 7000";
        }

        // Kirin
        if (lower.contains("kirin") || lower.contains("hisilicon")) {
            if (lower.contains("9020")) return "麒麟 9020";
            if (lower.contains("9010")) return "麒麟 9010";
            if (lower.contains("9000s")) return "麒麟 9000S";
            if (lower.contains("9000")) return "麒麟 9000";
        }

        // UNISOC
        if (lower.contains("unisoc") || lower.startsWith("ud7") || lower.startsWith("t7") || lower.startsWith("t8")) {
            if (lower.contains("t820")) return "虎贲 T820";
            if (lower.contains("t760")) return "虎贲 T760";
            if (lower.contains("t750")) return "虎贲 T750";
        }

        return null;
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

        // Map raw SoC identifier to Chinese marketing name
        String cnName = mapToChineseProcessorName(cpuResult);
        if (cnName != null) {
            cpuResult = cnName;
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
                    try {
                        java.lang.reflect.Method isEncryptedMethod = primaryVolume.getClass()
                                .getMethod("isDirectoryEncrypted");
                        Object encResult = isEncryptedMethod.invoke(primaryVolume);
                        if (encResult instanceof Boolean && (Boolean) encResult) {
                            info.storageEncryption = "文件级加密（FBE）";
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
                // 从 WindowMetrics 获取密度（API 34+）
                float density;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    density = windowMetrics.getDensity();
                } else {
                    DisplayMetrics metrics = new DisplayMetrics();
                    wm.getDefaultDisplay().getRealMetrics(metrics);
                    density = metrics.density;
                }
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
     * 关闭 ExecutorService，释放资源。
     * 在 Application.onTerminate() 中调用（仅在模拟器中生效，真机上 Application
     * 不保证调用 onTerminate，因此线程池会在进程退出时由系统自动回收）。
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
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
        // 1. Try reading GPU model from sysfs (only model name paths, NOT frequency paths)
        String[] gpuModelPaths = {
                "/sys/class/kgsl/kgsl-3d0/gpu_model",
                "/sys/kernel/gpu/gpu_model",
                "/sys/class/devfreq/gpufreq/gpu_model",
                "/sys/class/gpu/gpu_model",
                "/sys/class/kgsl/kgsl-3d0/device/driver/name"
        };
        for (String path : gpuModelPaths) {
            String value = readFile(path);
            if (value != null && !value.isEmpty()) {
                String mapped = mapToChineseGpuName(value.trim());
                return mapped != null ? mapped : value.trim();
            }
        }

        // 2. Try system properties for GPU identification
        String[] gpuProps = {
                "ro.hardware.gpu",
                "ro.hardware.egl",
                "ro.hardware.vulkan"
        };
        for (String prop : gpuProps) {
            String value = SystemPropertiesCompat.get(prop);
            if (value != null && !value.isEmpty()) {
                String mapped = mapToChineseGpuName(value);
                if (mapped != null) return mapped;
            }
        }

        // 3. Try GL_RENDERER via reflection
        String glRenderer = getGlRendererViaReflection();
        if (glRenderer != null && !glRenderer.isEmpty()) {
            String mapped = mapToChineseGpuName(glRenderer);
            return mapped != null ? mapped : glRenderer;
        }

        // 4. Infer GPU from CPU SoC
        String cpuHardware = getCpuHardware();
        if (cpuHardware != null) {
            String mapped = inferGpuFromSoc(cpuHardware);
            if (mapped != null) return mapped;
        }

        // 5. From /proc/gpuinfo
        String procGpu = readFile("/proc/gpuinfo");
        if (procGpu != null && !procGpu.isEmpty()) {
            String mapped = mapToChineseGpuName(procGpu.trim());
            return mapped != null ? mapped : procGpu.trim();
        }

        return context.getString(R.string.status_not_recognized);
    }

    /**
     * Map raw GPU identifiers to Chinese marketing names.
     */
    private String mapToChineseGpuName(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String lower = raw.toLowerCase(Locale.ROOT);

        // Adreno GPU (Qualcomm Snapdragon)
        if (lower.contains("adreno")) {
            if (lower.contains("830")) return "Adreno 830";
            if (lower.contains("820")) return "Adreno 820";
            if (lower.contains("810")) return "Adreno 810";
            if (lower.contains("800")) return "Adreno 800";
            if (lower.contains("750")) return "Adreno 750";
            if (lower.contains("740")) return "Adreno 740";
            if (lower.contains("730")) return "Adreno 730";
            if (lower.contains("720")) return "Adreno 720";
            if (lower.contains("710")) return "Adreno 710";
            if (lower.contains("660")) return "Adreno 660";
            if (lower.contains("650")) return "Adreno 650";
            if (lower.contains("644")) return "Adreno 644";
            if (lower.contains("642l")) return "Adreno 642L";
            if (lower.contains("642")) return "Adreno 642";
            if (lower.contains("637")) return "Adreno 637";
            if (lower.contains("630")) return "Adreno 630";
            if (lower.contains("620")) return "Adreno 620";
            if (lower.contains("619")) return "Adreno 619";
            if (lower.contains("618")) return "Adreno 618";
            if (lower.contains("616")) return "Adreno 616";
            if (lower.contains("613")) return "Adreno 613";
            if (lower.contains("610")) return "Adreno 610";
            return "Adreno GPU";
        }

        // Mali GPU (MediaTek, Samsung, HiSilicon)
        if (lower.contains("mali")) {
            if (lower.contains("g925")) return "Mali-G925";
            if (lower.contains("g920")) return "Mali-G920";
            if (lower.contains("g815")) return "Mali-G815";
            if (lower.contains("g810")) return "Mali-G810";
            if (lower.contains("g800")) return "Mali-G800";
            if (lower.contains("g715")) return "Mali-G715";
            if (lower.contains("g710")) return "Mali-G710";
            if (lower.contains("g620")) return "Mali-G620";
            if (lower.contains("g615")) return "Mali-G615";
            if (lower.contains("g610")) return "Mali-G610";
            if (lower.contains("g600")) return "Mali-G600";
            if (lower.contains("g510")) return "Mali-G510";
            if (lower.contains("g78")) return "Mali-G78";
            if (lower.contains("g77")) return "Mali-G77";
            if (lower.contains("g76")) return "Mali-G76";
            if (lower.contains("g57")) return "Mali-G57";
            return "Mali GPU";
        }

        // Apple GPU (won't appear on Android but just in case)
        if (lower.contains("apple")) return "Apple GPU";

        // Intel GPU
        if (lower.contains("intel")) return "Intel GPU";

        // NVIDIA GPU
        if (lower.contains("nvidia") || lower.contains("geforce")) return "NVIDIA GPU";

        return null;
    }

    /**
     * Infer GPU model from SoC/CPU hardware identifier.
     */
    private String inferGpuFromSoc(String cpuHardware) {
        if (cpuHardware == null) return null;
        String lower = cpuHardware.toLowerCase(Locale.ROOT);

        // Qualcomm Snapdragon SoCs → Adreno GPU
        if (lower.contains("qcom") || lower.contains("qualcomm") || lower.contains("snapdragon")
                || lower.startsWith("sm")) {
            if (lower.contains("sm8750") || lower.contains("8 elite")) return "Adreno 830";
            if (lower.contains("sm8650") || lower.contains("8 gen 3")) return "Adreno 750";
            if (lower.contains("sm8550") || lower.contains("8 gen 2")) return "Adreno 740";
            if (lower.contains("sm8450") || lower.contains("8 gen 1") || lower.contains("8+ gen 1")) return "Adreno 730";
            if (lower.contains("sm7675") || lower.contains("7+ gen 3")) return "Adreno 732";
            if (lower.contains("sm7550") || lower.contains("7 gen 1")) return "Adreno 642L";
            if (lower.contains("sm7475") || lower.contains("7+ gen 2")) return "Adreno 730";
            if (lower.contains("sm7435") || lower.contains("7 gen 3")) return "Adreno 720";
            if (lower.contains("sm7635") || lower.contains("7s gen 3")) return "Adreno 710";
            if (lower.contains("sm6450") || lower.contains("6 gen 1")) return "Adreno 610";
            if (lower.contains("sm4450") || lower.contains("4 gen 2")) return "Adreno 613";
            // Generic Qualcomm
            return context.getString(R.string.gpu_adreno);
        }

        // MediaTek SoCs → Mali GPU
        if (lower.contains("mtk") || lower.contains("mediatek") || lower.startsWith("mt")
                || lower.contains("dimensity")) {
            if (lower.contains("9400") || lower.contains("9400+")) return "Mali-G925";
            if (lower.contains("9300") || lower.contains("9300+")) return "Mali-G720";
            if (lower.contains("9200") || lower.contains("9200+")) return "Mali-G715";
            if (lower.contains("8400")) return "Mali-G820";
            if (lower.contains("8350")) return "Mali-G815";
            if (lower.contains("8300")) return "Mali-G615";
            if (lower.contains("8250") || lower.contains("8200")) return "Mali-G610";
            if (lower.contains("7300")) return "Mali-G615";
            if (lower.contains("7200")) return "Mali-G610";
            if (lower.contains("7050")) return "Mali-G610";
            return context.getString(R.string.gpu_mali_mediatek);
        }

        // HiSilicon Kirin → Mali GPU
        if (lower.contains("kirin") || lower.contains("hisilicon")) {
            if (lower.contains("9020")) return "Maleoon 920";
            if (lower.contains("9010")) return "Maleoon 910";
            if (lower.contains("9000s")) return "Maleoon 910";
            return context.getString(R.string.gpu_mali_hisilicon);
        }

        // Samsung Exynos → Mali or Xclipse GPU
        if (lower.contains("exynos")) {
            if (lower.contains("2400")) return "Xclipse 940";
            if (lower.contains("2200")) return "Xclipse 920";
            if (lower.contains("2100")) return "Mali-G78";
            return context.getString(R.string.gpu_mali_samsung);
        }

        // Google Tensor → Mali GPU
        if (lower.contains("tensor") || lower.contains("google")) {
            if (lower.contains("g5")) return "Mali-G715";
            if (lower.contains("g4")) return "Mali-G715";
            return "Mali GPU";
        }

        // UNISOC → Mali GPU
        if (lower.contains("unisoc") || lower.startsWith("ud7") || lower.startsWith("t7")
                || lower.startsWith("t8") || lower.startsWith("s8")) {
            return "Mali GPU";
        }

        return null;
    }

    private String getGlRendererViaReflection() {
        try {
            // GLES20.glGetString 要求当前线程已绑定 EGL Context
            // 在主线程没有 EGL Context 时直接返回 null，让流程继续走 SoC 推断
            Class<?> gles20Class = Class.forName("android.opengl.GLES20");
            java.lang.reflect.Method glGetStringMethod = gles20Class.getMethod("glGetString", int.class);
            // GL_RENDERER = 0x1F01
            Object result = glGetStringMethod.invoke(null, 0x1F01);
            if (result != null) {
                String renderer = result.toString();
                if (renderer != null && !renderer.isEmpty() && !renderer.contains("Emulator")) {
                    return renderer;
                }
            }
        } catch (java.lang.reflect.InvocationTargetException ite) {
            // glGetString 在没有 EGL Context 时会抛异常——这在主线程调用时是预期情况
            // 静默忽略，让后续 fallback 路径接管
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
        return SystemPropertiesCompat.get(context, propertyName);
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
