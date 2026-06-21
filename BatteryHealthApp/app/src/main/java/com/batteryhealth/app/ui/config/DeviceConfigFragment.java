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
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_CONFIG, Context.MODE_PRIVATE);
            switchHealthAlert.setChecked(prefs.getBoolean(PREF_HEALTH_ALERT, true));
            switchHealthAlert.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(PREF_HEALTH_ALERT, isChecked).apply();
            });
        }
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    private void loadDataAsync() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        DeviceInfoManager manager = new DeviceInfoManager(requireContext());
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
        safeSetText(tvProcessor, config.getCpuInfo() != null && !config.getCpuInfo().isEmpty()
                ? config.getCpuInfo() : getString(R.string.status_not_recognized));
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
        safeSetText(tvProcessor, android.os.Build.HARDWARE);
        safeSetText(tvActivationDate, "--");
        safeSetText(tvUsageDays, "--");
        safeSetText(tvActivationSource, getString(R.string.status_unknown));
        safeSetText(tvConfidence, "--");
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
