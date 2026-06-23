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
import com.batteryhealth.app.data.model.BugReportGuide;
import com.batteryhealth.app.data.model.DeviceConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
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

    private final Context context;
    private final DeviceDatabaseManager deviceDb;

    private DeviceConfig cachedConfig;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("config-loader"));
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BugReportGuide.AnalysisResult bugreportData;

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

        // 8.5 保存原始 Build 信息（在数据库覆盖之前）
        config.setBrand(Build.BRAND);
        config.setOriginalModel(Build.MODEL);
        if (config.getModel() == null) {
            config.setModel(Build.MODEL);
        }

        // 9. 使用机型数据库覆盖营销名称/处理器/屏幕规格
        DeviceDatabaseManager.DeviceEntry entry = deviceDb.findDevice();
        if (entry != null) {
            if (entry.marketName != null && !entry.marketName.isEmpty()) {
                config.setModel(entry.marketName); // 营销名覆盖 model，originalModel 保留 Build.MODEL
            }
            if (entry.processor != null && !entry.processor.isEmpty()) {
                String proc = entry.processor;
                // 安全检查：如果数据库中的处理器名不含中文字符且不匹配已知营销名模式，尝试映射
                if (!containsChinese(proc) && !isKnownMarketingPattern(proc)) {
                    proc = toChineseProcessorName(proc);
                }
                config.setCpuInfo(proc);
            }
            if (entry.batteryMah > 0) {
                config.setBatteryCapacity(entry.batteryMah);
            }
        }
        config.setGpuInfo(gpuInfo);

        // 9.5 Bugreport 数据回退（当其他来源都失败时）
        if (bugreportData != null && bugreportData.deviceInfo != null) {
            BugReportGuide.AnalysisResult.DeviceInfo di = bugreportData.deviceInfo;
            // 处理器：数据库没有时用 bugreport 的
            if ((config.getCpuInfo() == null || config.getCpuInfo().isEmpty() 
                    || config.getCpuInfo().equals(context.getString(R.string.status_not_recognized)))
                    && di.processor != null && !di.processor.isEmpty()) {
                config.setCpuInfo(toChineseProcessorName(di.processor));
            }
            // GPU：数据库没有时用 bugreport 的
            if (config.getGpuInfo() == null || config.getGpuInfo().equals(context.getString(R.string.status_not_recognized))
                    || config.getGpuInfo().equals(context.getString(R.string.gpu_adreno))
                    || config.getGpuInfo().equals(context.getString(R.string.gpu_mali_mediatek))) {
                if (di.gpuInfo != null && !di.gpuInfo.isEmpty()) {
                    config.setGpuInfo(di.gpuInfo);
                }
            }
            // 电池容量：数据库没有时用 bugreport 的
            if (config.getBatteryCapacity() <= 0 && di.batteryCapacity > 0) {
                config.setBatteryCapacity(di.batteryCapacity);
            }
        }

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
            return toChineseProcessorName(dbProcessor);
        }

        // 2. sysprop SoC 标识
        String soc = SystemPropertiesCompat.getSoC();
        if (soc != null && !soc.isEmpty()) {
            String normalized = normalizeProcessorName(soc);
            if (normalized != null) return toChineseProcessorName(normalized);
        }

        // 3. /proc/cpuinfo Hardware / model name / Processor 行
        String cpuInfo = readCpuInfoFromProc();
        if (cpuInfo != null && !cpuInfo.isEmpty()) {
            return toChineseProcessorName(cpuInfo);
        }

        // 4. 设备配置中已经收集的 CPU 字段
        DeviceConfig config = getDeviceConfig();
        if (config != null) {
            String cfgCpu = config.getCpuInfo();
            if (cfgCpu != null && !cfgCpu.isEmpty()) return cfgCpu;
        }

        return context.getString(R.string.status_not_recognized);
    }

    public void setBugreportData(BugReportGuide.AnalysisResult result) {
        this.bugreportData = result;
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

    /** 处理器原始标识 → 中文营销名映射表（key 为小写标识符，value 为中文营销名） */
    private static final LinkedHashMap<String, String> PROCESSOR_CN_MAP = new LinkedHashMap<String, String>() {{
        // Qualcomm Snapdragon (骁龙)
        put("kalama", "骁龙8 Gen 2");
        put("pineapple", "骁龙8 Gen 3");
        put("sm8750", "骁龙8 Elite");
        put("sm8650", "骁龙8 Gen 3");
        put("sm8450", "骁龙8 Gen 1");
        put("sm8350", "骁龙888");
        put("sm7675", "骁龙7+ Gen 3");
        put("sm7550", "骁龙7 Gen 1");
        put("sm7475", "骁龙7+ Gen 2");
        put("sm7450", "骁龙7 Gen 1");
        put("sm6450", "骁龙6 Gen 1");
        put("sm6375", "骁龙695");
        put("taro", "骁龙8 Gen 1");
        put("cape", "骁龙8 Gen 1+");
        put("shima", "骁龙888+");
        put("yupik", "骁龙870");
        put("lahaina", "骁龙888");
        put("kona", "骁龙865");
        put("parrot", "骁龙6 Gen 1");
        put("khaje", "骁龙6 Gen 1");
        put("holi", "骁龙695");
        put("bengal", "骁龙680");
        put("sun", "骁龙8 Elite (骁龙8至尊版)");
        put("cliff", "骁龙8 Elite (骁龙8至尊版)");
        // MediaTek Dimensity (天玑)
        put("mt6991", "天玑9400");
        put("mt6989z", "天玑9300");
        put("mt6989w", "天玑9300+");
        put("mt6989", "天玑9300");
        put("mt6985", "天玑9200+");
        put("mt6983", "天玑9200");
        put("mt6897", "天玑8300");
        put("mt6895", "天玑8200");
        put("mt6893", "天玑1200");
        put("mt6891", "天玑1100");
        put("mt6889", "天玑1000+");
        put("mt6879", "天玑1080");
        put("mt6877", "天玑920");
        put("mt6855", "天玑7050");
        put("mt6853", "天玑720");
        put("mt6833", "天玑720");
        // HiSilicon Kirin (麒麟)
        put("kirin9010", "麒麟9010");
        put("kirin9000s", "麒麟9000S");
        put("kirin9000", "麒麟9000");
        put("kirin990", "麒麟990");
        put("kirin985", "麒麟985");
        put("kirin980", "麒麟980");
        put("kirin820", "麒麟820");
        // Samsung Exynos
        put("exynos2500", "Exynos 2500");
        put("exynos2400", "Exynos 2400");
        put("exynos2200", "Exynos 2200");
        put("exynos2100", "Exynos 2100");
        put("exynos1380", "Exynos 1380");
        put("exynos1280", "Exynos 1280");
        // UNISOC
        put("ud712", "唐古拉T760");
        put("ud710", "唐古拉T770");
        put("t820", "唐古拉T820");
        // Google Tensor
        put("tensor g5", "Google Tensor G5");
        put("tensor g4", "Google Tensor G4");
        put("tensor g3", "Google Tensor G3");
        put("tensor g2", "Google Tensor G2");
        put("tensor", "Google Tensor");
    }};

    /**
     * 将原始处理器标识转换为中文营销名。
     * 匹配策略：将输入转为小写后，检查是否包含映射表中的某个 key。
     * 优先匹配较长的 key（如 "mt6989w" 优先于 "mt6989"），通过 LinkedHashMap 的插入顺序保证。
     *
     * @param raw 原始处理器字符串（来自 /proc/cpuinfo、sysprops 等）
     * @return 中文营销名，若未匹配则返回原始字符串
     */
    private String toChineseProcessorName(String raw) {
        if (raw == null || raw.isEmpty()) return raw;

        // First: try English marketing name → Chinese mapping
        String chineseName = englishToChineseProcessorName(raw);
        if (chineseName != null) return chineseName;

        // Then: try SoC code name mapping (existing logic)
        String lower = raw.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : PROCESSOR_CN_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return raw;
    }

    private String englishToChineseProcessorName(String raw) {
        if (raw == null) return null;
        String lower = raw.toLowerCase(Locale.ROOT);

        // Snapdragon English → 骁龙
        if (lower.contains("snapdragon")) {
            // Try to match specific models
            if (lower.contains("8 elite") || lower.contains("8elite")) return "骁龙8 Elite";
            if (lower.contains("8 gen 4") || lower.contains("8gen4")) return "骁龙8 Gen 4";
            if (lower.contains("8 gen 3") || lower.contains("8gen3")) return "骁龙8 Gen 3";
            if (lower.contains("8 gen 2") || lower.contains("8gen2")) return "骁龙8 Gen 2";
            if (lower.contains("8+ gen 1") || lower.contains("8plus gen 1") || lower.contains("8+gen1")) return "骁龙8+ Gen 1";
            if (lower.contains("8 gen 1") || lower.contains("8gen1")) return "骁龙8 Gen 1";
            if (lower.contains("888+")) return "骁龙888+";
            if (lower.contains("888")) return "骁龙888";
            if (lower.contains("870")) return "骁龙870";
            if (lower.contains("865")) return "骁龙865";
            if (lower.contains("855")) return "骁龙855";
            if (lower.contains("845")) return "骁龙845";
            if (lower.contains("7+ gen 3") || lower.contains("7plus gen 3")) return "骁龙7+ Gen 3";
            if (lower.contains("7+ gen 2") || lower.contains("7plus gen 2")) return "骁龙7+ Gen 2";
            if (lower.contains("7 gen 1") || lower.contains("7gen1")) return "骁龙7 Gen 1";
            if (lower.contains("6 gen 1") || lower.contains("6gen1")) return "骁龙6 Gen 1";
            if (lower.contains("695")) return "骁龙695";
            if (lower.contains("680")) return "骁龙680";
            if (lower.contains("665")) return "骁龙665";
            // Generic fallback
            return raw.replaceAll("(?i)Snapdragon", "骁龙");
        }

        // Dimensity English → 天玑
        if (lower.contains("dimensity")) {
            if (lower.contains("9500")) return "天玑9500";
            if (lower.contains("9400")) return "天玑9400";
            if (lower.contains("9300+")) return "天玑9300+";
            if (lower.contains("9300")) return "天玑9300";
            if (lower.contains("9200+")) return "天玑9200+";
            if (lower.contains("9200")) return "天玑9200";
            if (lower.contains("8300")) return "天玑8300";
            if (lower.contains("8200")) return "天玑8200";
            if (lower.contains("1200")) return "天玑1200";
            if (lower.contains("1100")) return "天玑1100";
            if (lower.contains("1080")) return "天玑1080";
            if (lower.contains("1000+")) return "天玑1000+";
            if (lower.contains("920")) return "天玑920";
            if (lower.contains("7050")) return "天玑7050";
            if (lower.contains("720")) return "天玑720";
            return raw.replaceAll("(?i)Dimensity", "天玑");
        }

        // Kirin English → 麒麟
        if (lower.contains("kirin")) {
            if (lower.contains("9010")) return "麒麟9010";
            if (lower.contains("9000s")) return "麒麟9000S";
            if (lower.contains("9000")) return "麒麟9000";
            if (lower.contains("990")) return "麒麟990";
            if (lower.contains("985")) return "麒麟985";
            if (lower.contains("980")) return "麒麟980";
            if (lower.contains("820")) return "麒麟820";
            return raw.replaceAll("(?i)Kirin", "麒麟");
        }

        // Exynos stays as-is (no Chinese name)
        // Tensor stays as-is

        return null; // No English marketing name match
    }

    /**
     * 判断字符串是否包含中文字符。
     */
    private boolean containsChinese(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') return true;
        }
        return false;
    }

    /**
     * 判断字符串是否匹配已知的处理器营销名模式（如 "骁龙*", "天玑*", "Exynos *", "Google Tensor*" 等）。
     */
    private boolean isKnownMarketingPattern(String s) {
        if (s == null || s.isEmpty()) return false;
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.startsWith("骁龙") || lower.startsWith("天玑") || lower.startsWith("麒麟")
                || lower.startsWith("唐古拉") || lower.startsWith("exynos")
                || lower.startsWith("google tensor");
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

        config.setCpuInfo(toChineseProcessorName(cpuResult));
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

    /** GPU 原始标识 → 营销名映射表（key 为小写匹配模式，value 为营销名+SoC 关联） */
    private static final LinkedHashMap<String, String> GPU_MARKETING_MAP = new LinkedHashMap<String, String>() {{
        // Additional Adreno models
        put("adreno 830", "Adreno 830 (骁龙8 Elite)");
        put("adreno 810", "Adreno 810 (骁龙8s Gen 3)");
        put("adreno 732", "Adreno 732 (骁龙7+ Gen 3)");
        put("adreno 710", "Adreno 710 (骁龙7+ Gen 2)");
        put("adreno 642l", "Adreno 642L (骁龙780G)");
        put("adreno 619", "Adreno 619 (骁龙750G)");
        put("adreno 613", "Adreno 613 (骁龙4 Gen 1)");
        // Additional Mali models
        put("mali-g76", "Mali-G76 (麒麟990/980)");
        put("mali g76", "Mali-G76 (麒麟990/980)");
        put("mali-g57 mc2", "Mali-G57 MC2 (天玑700/810)");
        put("mali-g57 mc3", "Mali-G57 MC3 (天玑8100)");
        put("mali-g57 mc5", "Mali-G57 MC5 (天玑8100)");
        // Qualcomm Adreno
        put("adreno 750", "Adreno 750 (骁龙8 Gen 3)");
        put("a750", "Adreno 750 (骁龙8 Gen 3)");
        put("adreno 740", "Adreno 740 (骁龙8 Gen 2)");
        put("a740", "Adreno 740 (骁龙8 Gen 2)");
        put("adreno 730", "Adreno 730 (骁龙8+ Gen 1 / 骁龙8 Gen 1)");
        put("a730", "Adreno 730 (骁龙8+ Gen 1 / 骁龙8 Gen 1)");
        put("adreno 660", "Adreno 660 (骁龙888)");
        put("a660", "Adreno 660 (骁龙888)");
        put("adreno 650", "Adreno 650 (骁龙865)");
        put("a650", "Adreno 650 (骁龙865)");
        put("adreno 640", "Adreno 640 (骁龙855)");
        put("a640", "Adreno 640 (骁龙855)");
        put("adreno 630", "Adreno 630 (骁龙845)");
        put("a630", "Adreno 630 (骁龙845)");
        put("adreno 620", "Adreno 620 (骁龙765G)");
        put("a620", "Adreno 620 (骁龙765G)");
        put("adreno 618", "Adreno 618 (骁龙778G)");
        put("a618", "Adreno 618 (骁龙778G)");
        put("adreno 615", "Adreno 615 (骁龙690)");
        put("a615", "Adreno 615 (骁龙690)");
        put("adreno 612", "Adreno 612 (骁龙675)");
        put("a612", "Adreno 612 (骁龙675)");
        put("adreno 610", "Adreno 610 (骁龙665)");
        put("a610", "Adreno 610 (骁龙665)");
        put("adreno 830", "Adreno 830 (骁龙8 Elite)");
        put("a830", "Adreno 830 (骁龙8 Elite)");
        put("adreno 810", "Adreno 810 (骁龙8s Gen 3)");
        put("a810", "Adreno 810 (骁龙8s Gen 3)");
        // ARM Mali
        put("mali-g715 mc11", "Mali-G715 MC11 (天玑9200+)");
        put("mali-g710 mc7", "Mali-G710 MC7 (天玑9000)");
        put("mali-g715", "Mali-G715 (天玑9200/9300)");
        put("mali g715", "Mali-G715 (天玑9200/9300)");
        put("mali-g710", "Mali-G710");
        put("mali g710", "Mali-G710");
        put("mali-g78", "Mali-G78");
        put("mali g78", "Mali-G78");
        put("mali-g77", "Mali-G77 (天玑1000+/1100/1200)");
        put("mali g77", "Mali-G77 (天玑1000+/1100/1200)");
        put("mali-g720", "Mali-G720 (天玑9400)");
        put("mali g720", "Mali-G720 (天玑9400)");
        put("mali-g920", "Mali-G920 (天玑9400)");
        put("mali g920", "Mali-G920 (天玑9400)");
        put("mali-g615", "Mali-G615 (天玑8300)");
        put("mali g615", "Mali-G615 (天玑8300)");
        put("mali-g610", "Mali-G610");
        put("mali g610", "Mali-G610");
        put("mali-g68", "Mali-G68");
        put("mali g68", "Mali-G68");
        put("mali-g57", "Mali-G57");
        put("mali g57", "Mali-G57");
        put("mali-g510", "Mali-G510");
        put("mali g510", "Mali-G510");
        put("mali-c720", "Mali-C720 (麒麟9010)");
        put("mali c720", "Mali-C720 (麒麟9010)");
        put("mali-c715", "Mali-C715 (麒麟9000S)");
        put("mali c715", "Mali-C715 (麒麟9000S)");
        // Samsung Xclipse
        put("xclipse 940", "Xclipse 940 (Exynos 2500)");
        put("xclipse940", "Xclipse 940 (Exynos 2500)");
        put("xclipse 930", "Xclipse 930 (Exynos 2400)");
        put("xclipse930", "Xclipse 930 (Exynos 2400)");
        put("xclipse 920", "Xclipse 920 (Exynos 2200)");
        put("xclipse920", "Xclipse 920 (Exynos 2200)");
        // IMG PowerVR
        put("powervr bxm", "PowerVR BXM (天玑9200)");
        put("bxm", "PowerVR BXM (天玑9200)");
        put("powervr bxs", "PowerVR BXS");
        put("bxs", "PowerVR BXS");
    }};

    /**
     * 将原始 GPU 标识映射为营销名称。
     * 匹配策略：将输入转为小写后，检查是否包含映射表中的某个 key。
     * 优先匹配较长的 key（如 "mali-g715 mc11" 优先于 "mali-g715"），通过 LinkedHashMap 插入顺序保证。
     *
     * @param raw 原始 GPU 字符串（来自 GL_RENDERER、sysfs、系统属性等）
     * @return 营销名（含 SoC 关联），若未匹配则返回清理后的原始字符串
     */
    private String normalizeGpuInfo(String raw) {
        if (raw == null || raw.trim().isEmpty()) return raw;
        // Strip special characters for more flexible matching
        String cleaned = raw.toLowerCase(Locale.ROOT)
                .replace("(tm)", "")
                .replace("(r)", "")
                .replace("®", "")
                .replace("™", "")
                .replaceAll("[()]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        for (Map.Entry<String, String> entry : GPU_MARKETING_MAP.entrySet()) {
            if (cleaned.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        // 未匹配时返回清理后的原始字符串
        return raw.trim();
    }

    private String collectGpuInfo() {
        // 0. 优先尝试 Qualcomm 专用 gpu_model 节点（直接返回 GPU 型号名称）
        String kgslModel = readFile("/sys/class/kgsl/kgsl-3d0/gpu_model");
        if (kgslModel != null && !kgslModel.isEmpty()) {
            String normalized = normalizeGpuInfo(kgslModel.trim());
            if (normalized != null && !normalized.isEmpty()) {
                return normalized;
            }
        }

        // 1. 尝试读取 sysfs
        for (String path : GPU_RENDERER_PATHS) {
            String value = readFile(path);
            if (value != null && !value.isEmpty()) {
                String normalized = normalizeGpuInfo(value.trim());
                if (normalized != null && !normalized.isEmpty()) {
                    return normalized;
                }
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
                String normalized = normalizeGpuInfo(value);
                if (normalized != null && !normalized.isEmpty()) {
                    return normalized;
                }
            }
        }

        // 2b. Android 16 新增 GPU 检测属性（通过 SystemPropertiesCompat）
        String[] newGpuProps = {
                "ro.hardware.gpu"
        };
        for (String prop : newGpuProps) {
            String value = SystemPropertiesCompat.get(prop);
            if (value != null && !value.isEmpty()) {
                String normalized = normalizeGpuInfo(value);
                if (normalized != null && !normalized.isEmpty()) {
                    return normalized;
                }
            }
        }

        // 3. 通过反射调用 GLES20.glGetString(GL_RENDERER)
        String glRenderer = getGlRendererViaReflection();
        if (glRenderer != null && !glRenderer.isEmpty()) {
            String normalized = normalizeGpuInfo(glRenderer);
            if (normalized != null && !normalized.isEmpty()) {
                return normalized;
            }
        }

        // 4. 从 /proc/gpuinfo 读取（部分设备存在）
        String procGpu = readFile("/proc/gpuinfo");
        if (procGpu != null && !procGpu.isEmpty()) {
            String normalized = normalizeGpuInfo(procGpu.trim());
            if (normalized != null && !normalized.isEmpty()) {
                return normalized;
            }
        }

        // 5. 从 /proc/cpuinfo 的 Hardware 字段推断具体 GPU 型号
        String cpuHardware = getCpuHardware();
        if (cpuHardware != null) {
            String gpuFromHardware = inferGpuFromHardware(cpuHardware);
            if (gpuFromHardware != null) {
                return gpuFromHardware;
            }
        }

        return context.getString(R.string.status_not_recognized);
    }

    /**
     * 根据 CPU Hardware 标识推断具体 GPU 型号。
     * 覆盖高通、联发科、海思、三星、谷歌等主流 SoC 平台。
     */
    private String inferGpuFromHardware(String hardware) {
        if (hardware == null || hardware.isEmpty()) return null;
        String lower = hardware.toLowerCase(Locale.ROOT);

        // Qualcomm Snapdragon 平台 → Adreno
        if (lower.contains("qcom") || lower.contains("qualcomm") || lower.contains("snapdragon")) {
            // 尝试从 SoC 代号推断具体 Adreno 型号
            if (lower.contains("pineapple") || lower.contains("sm8650")) return "Adreno 750 (骁龙8 Gen 3)";
            if (lower.contains("kalama") || lower.contains("sm8550")) return "Adreno 740 (骁龙8 Gen 2)";
            if (lower.contains("taro") || lower.contains("cape") || lower.contains("sm8450")) return "Adreno 730 (骁龙8+ Gen 1 / 骁龙8 Gen 1)";
            if (lower.contains("shima") || lower.contains("lahaina") || lower.contains("sm8350")) return "Adreno 660 (骁龙888)";
            if (lower.contains("yupik") || lower.contains("kona") || lower.contains("sm8250")) return "Adreno 650 (骁龙865)";
            if (lower.contains("sm8150") || lower.contains("msmnile")) return "Adreno 640 (骁龙855)";
            if (lower.contains("sdm845")) return "Adreno 630 (骁龙845)";
            if (lower.contains("sm7675")) return "Adreno 732 (骁龙7+ Gen 3)";
            if (lower.contains("sm7475")) return "Adreno 710 (骁龙7+ Gen 2)";
            if (lower.contains("sun") || lower.contains("cliff") || lower.contains("sm8750")) return "Adreno 830 (骁龙8 Elite)";
            if (lower.contains("sm8635")) return "Adreno 810 (骁龙8s Gen 3)";
            if (lower.contains("sm7325") || lower.contains("tundra")) return "Adreno 620 (骁龙765G)";
            if (lower.contains("sm7325-g")) return "Adreno 620 (骁龙765G)";
            if (lower.contains("sm7350")) return "Adreno 642L (骁龙780G)";
            if (lower.contains("sm7225")) return "Adreno 618 (骁龙778G)";
            if (lower.contains("sm6375")) return "Adreno 615 (骁龙690)";
            if (lower.contains("sm6150")) return "Adreno 612 (骁龙675)";
            if (lower.contains("sm6125")) return "Adreno 610 (骁龙665)";
            // 通用高通平台兜底
            return context.getString(R.string.gpu_adreno);
        }

        // MediaTek Dimensity / Helio 平台 → Mali
        if (lower.contains("mtk") || lower.contains("mediatek")) {
            if (lower.contains("mt6991")) return "Mali-G920 (天玑9400)";
            if (lower.contains("mt6989z")) return "Mali-G720 (天玑9300)";
            if (lower.contains("mt6989w")) return "Mali-G720 (天玑9300+)";
            if (lower.contains("mt6989")) return "Mali-G720 (天玑9300)";
            if (lower.contains("mt6985")) return "Mali-G715 MC11 (天玑9200+)";
            if (lower.contains("mt6983")) return "Mali-G715 (天玑9200/9300)";
            if (lower.contains("mt6897")) return "Mali-G615 (天玑8300)";
            if (lower.contains("mt6895")) return "Mali-G610 (天玑8200)";
            if (lower.contains("mt6893")) return "Mali-G77 (天玑1000+/1100/1200)";
            if (lower.contains("mt6891")) return "Mali-G77 (天玑1000+/1100/1200)";
            if (lower.contains("mt6889")) return "Mali-G77 (天玑1000+/1100/1200)";
            if (lower.contains("mt6879")) return "Mali-G68 (天玑1080)";
            if (lower.contains("mt6877")) return "Mali-G68 (天玑920)";
            return context.getString(R.string.gpu_mali_mediatek);
        }

        // HiSilicon Kirin 平台 → Mali
        if (lower.contains("kirin") || lower.contains("hisilicon")) {
            if (lower.contains("kirin9010")) return "Mali-C720 (麒麟9010)";
            if (lower.contains("kirin9000s")) return "Mali-C715 (麒麟9000S)";
            if (lower.contains("kirin9000")) return "Mali-G78 (麒麟9000)";
            if (lower.contains("kirin990")) return "Mali-G76 (麒麟990)";
            if (lower.contains("kirin985")) return "Mali-G77 (麒麟985)";
            if (lower.contains("kirin980")) return "Mali-G76 (麒麟980)";
            if (lower.contains("kirin820")) return "Mali-G57 (麒麟820)";
            return context.getString(R.string.gpu_mali_hisilicon);
        }

        // Samsung Exynos 平台 → Xclipse / Mali
        if (lower.contains("exynos")) {
            if (lower.contains("exynos2500")) return "Xclipse 940 (Exynos 2500)";
            if (lower.contains("exynos2400")) return "Xclipse 930 (Exynos 2400)";
            if (lower.contains("exynos2200")) return "Xclipse 920 (Exynos 2200)";
            if (lower.contains("exynos2100")) return "Mali-G78 (Exynos 2100)";
            if (lower.contains("exynos1380")) return "Mali-G68 (Exynos 1380)";
            if (lower.contains("exynos1280")) return "Mali-G68 (Exynos 1280)";
            return context.getString(R.string.gpu_mali_samsung);
        }

        // Google Tensor 平台 → Mali / IMG
        if (lower.contains("tensor") || lower.contains("google")) {
            if (lower.contains("g5")) return "Mali-G715 (Tensor G5)";
            if (lower.contains("g4")) return "Mali-G715 (Tensor G4)";
            if (lower.contains("g3")) return "Mali-G715 (Tensor G3)";
            if (lower.contains("g2")) return "Mali-G710 (Tensor G2)";
            return "Mali-G710 (Google Tensor)";
        }

        return null;
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
