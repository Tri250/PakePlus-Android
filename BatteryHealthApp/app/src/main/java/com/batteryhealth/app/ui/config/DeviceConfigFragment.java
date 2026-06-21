package com.batteryhealth.app.ui.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.utils.ActivationDateHelper;
import com.batteryhealth.app.utils.DeviceInfoManager;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 设备配置查询页面
 *
 * 功能：
 * 1. 精准查询手机硬件配置记录
 * 2. 基于多源数据检测激活日期和使用天数
 * 3. 展示可信度信息
 */
public class DeviceConfigFragment extends Fragment {

    private static final String PREFS_CONFIG = "config_prefs";
    private static final String PREF_HEALTH_ALERT = "health_decay_alert";

    private TextView tvDeviceName, tvDeviceModel, tvAndroidVersion, tvProcessor, tvRam, tvStorage,
            tvScreen, tvActivationDate, tvUsageDays, tvActivationSource, tvAvailableRam,
            tvAvailableStorage, tvNetworkType, tvGpuInfo, tvConfidence;
    private Switch switchHealthAlert;
    private View progressBar;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_device_config, container, false);
        initViews(view);
        animateEntry(view);
        loadDataAsync();
        return view;
    }

    private void initViews(View view) {
        tvDeviceName = view.findViewById(R.id.tv_device_name);
        tvDeviceModel = view.findViewById(R.id.tv_device_model);
        tvAndroidVersion = view.findViewById(R.id.tv_android_version);
        tvProcessor = view.findViewById(R.id.tv_processor);
        tvRam = view.findViewById(R.id.tv_ram);
        tvStorage = view.findViewById(R.id.tv_storage);
        tvScreen = view.findViewById(R.id.tv_screen);
        tvActivationDate = view.findViewById(R.id.tv_activation_date);
        tvUsageDays = view.findViewById(R.id.tv_usage_days);
        tvActivationSource = view.findViewById(R.id.tv_activation_source);
        tvAvailableRam = view.findViewById(R.id.tv_available_ram);
        tvAvailableStorage = view.findViewById(R.id.tv_available_storage);
        tvNetworkType = view.findViewById(R.id.tv_network_type);
        tvGpuInfo = view.findViewById(R.id.tv_gpu_info);
        tvConfidence = view.findViewById(R.id.tv_confidence);
        switchHealthAlert = view.findViewById(R.id.switch_health_alert);
        progressBar = view.findViewById(R.id.progress_loading);

        if (switchHealthAlert != null) {
            Context ctx = getContext();
            if (ctx == null) return;
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_CONFIG, Context.MODE_PRIVATE);
            switchHealthAlert.setChecked(prefs.getBoolean(PREF_HEALTH_ALERT, true));
            switchHealthAlert.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(PREF_HEALTH_ALERT, isChecked).apply();
            });
        }
    }

    private void animateEntry(View view) {
        Context ctx = getContext();
        if (ctx == null) return;
        Animation fadeUp = AnimationUtils.loadAnimation(ctx, R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    private void loadDataAsync() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        Context ctx = getContext();
        if (ctx == null) {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            return;
        }
        DeviceInfoManager manager = new DeviceInfoManager(ctx);
        manager.getDeviceConfigAsync(new DeviceInfoManager.DeviceConfigCallback() {
            @Override
            public void onConfigLoaded(DeviceConfig config) {
                mainHandler.post(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    bindConfig(config);
                });
            }

            @Override
            public void onConfigLoadFailed(Exception e) {
                mainHandler.post(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    bindFallback();
                });
            }
        });
    }

    private void bindConfig(DeviceConfig config) {
        if (!isAdded() || getContext() == null) return;
        safeSetText(tvDeviceName, config.getFullModelName());
        safeSetText(tvDeviceModel, config.getModel());
        safeSetText(tvAndroidVersion, config.getAndroidCodename() + " (API " + config.getSdkVersion() + ")");
        // 问题修复：bindDeviceInfo() 中从 deviceDb 获取的 processorName 可能是原始值，
        // 需要统一使用 formatProcessorName() 格式化，确保显示规范的中文处理器名称。
        String rawProcessor = config.getCpuInfo();
        String formattedProcessor = (rawProcessor != null && !rawProcessor.isEmpty())
                ? formatProcessorName(rawProcessor)
                : getString(R.string.status_not_recognized);
        safeSetText(tvProcessor, formattedProcessor);
        safeSetText(tvRam, config.getFormattedMemory());
        safeSetText(tvStorage, config.getFormattedStorage());
        safeSetText(tvScreen, config.getScreenResolution() + " · " + config.getFormattedScreenSize());

        // 激活日期
        long activationDate = config.getActivationDate();
        if (activationDate > 0) {
            safeSetText(tvActivationDate, config.getActivationDateStr());
            int days = config.getUsageDays();
            safeSetText(tvUsageDays, days >= 0 ? getString(R.string.config_usage_days_format, days) : "--");

            String source = config.getActivationSource();
            safeSetText(tvActivationSource, formatSource(source));

            float confidence = config.getActivationConfidence();
            safeSetText(tvConfidence, getString(R.string.config_confidence_format, (int) (confidence * 100)));
        } else {
            safeSetText(tvActivationDate, "--");
            safeSetText(tvUsageDays, "--");
            safeSetText(tvActivationSource, getString(R.string.status_unknown));
            safeSetText(tvConfidence, "--");
        }

        safeSetText(tvAvailableRam, config.getAvailableMemory() > 0
                ? String.format(Locale.getDefault(), "%.1f GB", config.getAvailableMemory() / 1024.0)
                : "--");
        safeSetText(tvAvailableStorage, config.getAvailableStorage() > 0
                ? String.format(Locale.getDefault(), "%d GB", config.getAvailableStorage())
                : "--");
        safeSetText(tvNetworkType, config.getNetworkType() != null ? config.getNetworkType() : getString(R.string.status_no_network));

        String gpu = config.getGpuInfo();
        safeSetText(tvGpuInfo, gpu != null && !gpu.isEmpty() ? gpu : getString(R.string.status_not_recognized));
    }

    private void bindFallback() {
        if (!isAdded() || getContext() == null) return;
        safeSetText(tvDeviceName, android.os.Build.BRAND + " " + android.os.Build.MODEL);
        safeSetText(tvDeviceModel, android.os.Build.MODEL);
        safeSetText(tvAndroidVersion, android.os.Build.VERSION.RELEASE);
        // 处理器名称使用格式化后的友好名称（国内品牌规范）
        safeSetText(tvProcessor, formatProcessorName(android.os.Build.HARDWARE));
        safeSetText(tvActivationDate, "--");
        safeSetText(tvUsageDays, "--");
        safeSetText(tvActivationSource, getString(R.string.status_unknown));
        safeSetText(tvConfidence, "--");
    }

    /**
     * 格式化 Build.HARDWARE 为友好的处理器名称（2026年国内品牌规范）。
     * 委托给 DeviceInfoManager.formatHardwareName 实现。
     */
    private String formatProcessorName(String hw) {
        if (hw == null || hw.isEmpty()) return getString(R.string.status_not_recognized);
        Context ctx = getContext();
        if (ctx == null) return hw;
        try {
            // 使用临时 DeviceInfoManager 调用 formatHardwareName
            DeviceInfoManager mgr = new DeviceInfoManager(ctx);
            String formatted = mgr.formatHardwareNameForDisplay(hw);
            if (formatted != null && !formatted.isEmpty() && !formatted.equals(hw)) {
                return formatted;
            }
            // fallback 格式化
            return formatProcessorNameFallback(hw);
        } catch (Exception e) {
            return formatProcessorNameFallback(hw);
        }
    }

    /**
     * 内置的处理器名称格式化逻辑（不依赖 DeviceInfoManager 实例）。
     * 覆盖2026年国内主流处理器：
     * - 高通骁龙 8 Gen 4 / Gen 3 / Gen 2 / 7+ Gen 3 等
     * - 联发科天玑 9500/9400/9300/9200
     * - 华为麒麟 9010/9000S/9000
     * - 三星 Exynos、Google Tensor、展锐
     */
    private String formatProcessorNameFallback(String hw) {
        if (hw == null || hw.isEmpty()) return getString(R.string.status_not_recognized);
        String lower = hw.toLowerCase(java.util.Locale.ROOT);

        // Qualcomm Snapdragon
        if (lower.startsWith("qcom") || lower.contains("snapdragon") || lower.contains("sm")) {
            if (lower.contains("sm8750") || lower.contains("gen4") || lower.contains("8gen4"))
                return "高通骁龙 8 Gen 4";
            if (lower.contains("sm8650") || lower.contains("gen3") || lower.contains("8gen3"))
                return lower.contains("ac") || lower.contains("leading") ? "高通骁龙 8 Gen 3 领先版" : "高通骁龙 8 Gen 3";
            if (lower.contains("sm8550") || lower.contains("gen2") || lower.contains("8gen2"))
                return "高通骁龙 8 Gen 2";
            if (lower.contains("sm7650") || lower.contains("7+gen3")) return "高通骁龙 7+ Gen 3";
            if (lower.contains("sm7550") || lower.contains("7gen3")) return "高通骁龙 7 Gen 3";
            if (lower.contains("sm7475") || lower.contains("7+gen2")) return "高通骁龙 7+ Gen 2";
            if (lower.contains("sm7450") || lower.contains("7gen2")) return "高通骁龙 7 Gen 2";
            if (lower.contains("sm6")) return "高通骁龙 6 系列";
            if (lower.contains("sm4")) return "高通骁龙 4 系列";
            return "高通骁龙 (" + hw + ")";
        }

        // MediaTek Dimensity
        if (lower.startsWith("mt") || lower.startsWith("mtk") || lower.contains("dimensity")) {
            if (lower.contains("mt6999") || lower.contains("9500"))
                return lower.contains("+") ? "联发科天玑 9500+" : "联发科天玑 9500";
            if (lower.contains("mt6989") || lower.contains("9400"))
                return lower.contains("+") ? "联发科天玑 9400+" : "联发科天玑 9400";
            if (lower.contains("mt6983") || lower.contains("9300"))
                return lower.contains("+") ? "联发科天玑 9300+" : "联发科天玑 9300";
            if (lower.contains("mt6897") || lower.contains("9200"))
                return lower.contains("+") ? "联发科天玑 9200+" : "联发科天玑 9200";
            if (lower.contains("mt6893") || lower.contains("9000"))
                return lower.contains("+") ? "联发科天玑 9000+" : "联发科天玑 9000";
            if (lower.contains("mt6896") || lower.contains("8300")) return "联发科天玑 8300";
            if (lower.contains("mt6895") || lower.contains("8200")) return "联发科天玑 8200";
            if (lower.contains("mt6891") || lower.contains("8000")) return "联发科天玑 8000";
            if (lower.contains("mt7")) return "联发科天玑 7 系列";
            if (lower.contains("mt6")) return "联发科天玑 6 系列";
            return "联发科 (" + hw.toUpperCase(java.util.Locale.ROOT) + ")";
        }

        // HiSilicon Kirin
        if (lower.startsWith("kirin") || lower.contains("hi36") || lower.contains("huawei")) {
            if (lower.contains("9010")) return "华为麒麟 9010";
            if (lower.contains("9000s")) return "华为麒麟 9000S";
            if (lower.contains("9000")) return "华为麒麟 9000";
            if (lower.contains("990")) return "华为麒麟 990";
            if (lower.contains("980")) return "华为麒麟 980";
            return "华为麒麟 (" + hw + ")";
        }

        // Samsung Exynos
        if (lower.startsWith("exynos") || lower.contains("s5e")) {
            if (lower.contains("2500") || lower.contains("exynos2500")) return "三星 Exynos 2500";
            if (lower.contains("2400") || lower.contains("exynos2400")) return "三星 Exynos 2400";
            if (lower.contains("2200") || lower.contains("exynos2200")) return "三星 Exynos 2200";
            return "三星 Exynos (" + hw + ")";
        }

        // UNISOC
        if (lower.contains("unisoc") || lower.startsWith("ud7") || lower.startsWith("t7")
                || lower.startsWith("s8") || lower.startsWith("t3") || lower.contains("sprd")) {
            if (lower.contains("t820")) return "展锐 T820";
            if (lower.contains("t770")) return "展锐 T770";
            if (lower.contains("t760")) return "展锐 T760";
            return "展锐 (" + hw + ")";
        }

        // Google Tensor
        if (lower.startsWith("google") || lower.contains("tensor") || lower.contains("gs")) {
            if (lower.contains("g5") || lower.contains("tensorg5")) return "Google Tensor G5";
            if (lower.contains("g4") || lower.contains("tensorg4")) return "Google Tensor G4";
            if (lower.contains("g3") || lower.contains("tensorg3")) return "Google Tensor G3";
            return "Google Tensor (" + hw + ")";
        }

        // Apple
        if (lower.contains("apple") || lower.contains("a18") || lower.contains("a17") || lower.contains("a16")) {
            if (lower.contains("a18pro") || lower.contains("a18 pro")) return "Apple A18 Pro";
            if (lower.contains("a18")) return "Apple A18";
            if (lower.contains("a17pro") || lower.contains("a17 pro")) return "Apple A17 Pro";
            if (lower.contains("a17")) return "Apple A17";
            if (lower.contains("a16")) return "Apple A16";
            return "Apple A 系列";
        }

        return hw;
    }

    private void safeSetText(TextView tv, String text) {
        if (tv != null) tv.setText(text);
    }

    private String formatSource(String source) {
        if (source == null) return getString(R.string.status_unknown);
        switch (source) {
            case "electronic_warranty_card": return "电子保卡";
            case "first_boot_time": return "首次开机时间";
            case "first_unlock_time": return "首次解锁时间";
            case "device_policy_manager": return "设备策略管理";
            case "gms_first_launch": return "GMS首次启动";
            case "system_build_time": return "系统构建时间";
            case "settings_secure": return "系统设置";
            case "fallback_estimate": return "估算值";
            default: return source;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
