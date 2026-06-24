package com.batteryhealth.app.ui.config;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
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
import androidx.lifecycle.ViewModelProvider;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.ui.community.CommunityFragment;
import com.batteryhealth.app.ui.guide.GuideFragment;
import com.batteryhealth.app.ui.viewmodel.DeviceConfigViewModel;
import com.batteryhealth.app.utils.DeviceConfigQuery;

import java.util.Locale;

public class DeviceConfigFragment extends Fragment {

    private static final String PREFS_CONFIG = "config_prefs";
    private static final String PREF_HEALTH_ALERT = "health_decay_alert";
    // 与 BatteryMonitorService 对齐的 SharedPreferences 键名
    private static final String PREFS_BATTERY_HEALTH = "battery_health_prefs";
    private static final String PREF_ALERT_ENABLED = "health_alert_enabled";

    private TextView tvDeviceName, tvDeviceModel, tvAndroidVersion, tvProcessor, tvRam, tvStorage,
            tvScreen, tvActivationDate, tvUsageDays, tvActivationSource, tvAvailableRam,
            tvAvailableStorage, tvNetworkType, tvGpu, tvCpuCores, tvCpuFreq,
            tvBatteryCapacityConfig, tvStorageEncryption,
            tvVersionAssessment, tvSecurityAssessment,
            tvPerformanceAssessment, tvSuggestions;
    private Switch switchHealthAlert;
    private DeviceConfigQuery configQuery;

    private DeviceConfigViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_device_config, container, false);
        initViews(view);
        initViewModel();
        animateEntry(view);
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
        tvGpu = view.findViewById(R.id.tv_gpu);
        tvCpuCores = view.findViewById(R.id.tv_cpu_cores);
        tvCpuFreq = view.findViewById(R.id.tv_cpu_freq);
        tvBatteryCapacityConfig = view.findViewById(R.id.tv_battery_capacity_config);
        tvStorageEncryption = view.findViewById(R.id.tv_storage_encryption);
        tvVersionAssessment = view.findViewById(R.id.tv_version_assessment);
        tvSecurityAssessment = view.findViewById(R.id.tv_security_assessment);
        tvPerformanceAssessment = view.findViewById(R.id.tv_performance_assessment);
        tvSuggestions = view.findViewById(R.id.tv_suggestions);
        switchHealthAlert = view.findViewById(R.id.switch_health_alert);

        configQuery = new DeviceConfigQuery(requireContext());

        // 预警开关：同时读写 BatteryMonitorService 使用的 SharedPreferences，确保开关生效
        SharedPreferences servicePrefs = requireContext().getSharedPreferences(PREFS_BATTERY_HEALTH, Context.MODE_PRIVATE);
        SharedPreferences configPrefs = requireContext().getSharedPreferences(PREFS_CONFIG, Context.MODE_PRIVATE);
        // 优先读取服务端状态，若无则读取配置端状态，默认 true
        boolean alertEnabled = servicePrefs.contains(PREF_ALERT_ENABLED)
                ? servicePrefs.getBoolean(PREF_ALERT_ENABLED, true)
                : configPrefs.getBoolean(PREF_HEALTH_ALERT, true);
        switchHealthAlert.setChecked(alertEnabled);
        switchHealthAlert.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 同步写入两个 SharedPreferences，确保 BatteryMonitorService 能读取
            servicePrefs.edit().putBoolean(PREF_ALERT_ENABLED, isChecked).apply();
            configPrefs.edit().putBoolean(PREF_HEALTH_ALERT, isChecked).apply();
        });

        // 二级入口：社区
        View btnCommunity = view.findViewById(R.id.btn_community);
        if (btnCommunity != null) {
            btnCommunity.setOnClickListener(v -> {
                if (isAdded() && getActivity() != null) {
                    getActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(android.R.id.content, new CommunityFragment())
                            .addToBackStack("community")
                            .commit();
                }
            });
        }

        // 二级入口：指南
        View btnGuide = view.findViewById(R.id.btn_guide);
        if (btnGuide != null) {
            btnGuide.setOnClickListener(v -> {
                if (isAdded() && getActivity() != null) {
                    getActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(android.R.id.content, new GuideFragment())
                            .addToBackStack("guide")
                            .commit();
                }
            });
        }
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(DeviceConfigViewModel.class);
        viewModel.getDeviceConfig().observe(getViewLifecycleOwner(), this::applyConfig);
        viewModel.getUsageDays().observe(getViewLifecycleOwner(), days -> 
                tvUsageDays.setText(days >= 0 ? days + " 天" : "--"));
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.loadDeviceConfig();
        loadSystemAnalysis();
    }

    private void loadSystemAnalysis() {
        DeviceConfigQuery.ConfigAnalysisResult result = configQuery.analyzeConfiguration();
        tvVersionAssessment.setText(result.versionAssessment);
        tvSecurityAssessment.setText(result.securityAssessment);
        tvPerformanceAssessment.setText(result.performanceAssessment);
        tvSuggestions.setText(result.suggestions);
    }

    private void applyConfig(DeviceConfig config) {
        if (config == null) {
            loadFallbackData();
            return;
        }

        // 将处理器营销名传递给 DeviceConfigQuery，使性能评估与设备信息卡片一致
        String cpuInfo = config.getCpuInfo();
        if (cpuInfo != null && !cpuInfo.isEmpty()) {
            configQuery.setProcessorMarketingName(cpuInfo);
        }

        tvDeviceName.setText(config.getFullModelName());
        tvDeviceModel.setText(config.getModel());
        tvAndroidVersion.setText(Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");

        String processorDisplay = config.getCpuInfo();
        tvProcessor.setText(processorDisplay != null && !processorDisplay.isEmpty() ? processorDisplay : Build.HARDWARE);

        tvRam.setText(config.getFormattedMemory());
        tvStorage.setText(config.getFormattedStorage());

        StringBuilder screenInfo = new StringBuilder();
        screenInfo.append(config.getScreenResolution());
        if (config.getScreenSize() > 0) {
            screenInfo.append("  ").append(config.getFormattedScreenSize());
        }
        tvScreen.setText(screenInfo.toString());

        String activationDateStr = config.getActivationDateStr();
        tvActivationDate.setText(activationDateStr != null && !activationDateStr.isEmpty()
                ? activationDateStr : "--");

        String activationSource = config.getActivationSource();
        tvActivationSource.setText(activationSource != null && !activationSource.isEmpty()
                ? activationSource : getString(R.string.source_internal));

        long availableMemoryMb = config.getAvailableMemory();
        if (availableMemoryMb > 0) {
            tvAvailableRam.setText(formatMemory(availableMemoryMb));
        } else {
            tvAvailableRam.setText("--");
        }

        long availableStorageGb = config.getAvailableStorage();
        if (availableStorageGb > 0) {
            tvAvailableStorage.setText(formatStorage(availableStorageGb));
        } else {
            tvAvailableStorage.setText("--");
        }

        String networkType = config.getNetworkType();
        tvNetworkType.setText(networkType != null && !networkType.isEmpty()
                ? networkType : getString(R.string.status_unknown));

        // GPU 信息
        String gpuInfo = config.getGpuInfo();
        tvGpu.setText(gpuInfo != null && !gpuInfo.isEmpty()
                ? gpuInfo : getString(R.string.status_not_recognized));

        // CPU 核心数
        int cpuCores = config.getCpuCores();
        tvCpuCores.setText(cpuCores > 0 ? cpuCores + " 核" : "--");

        // CPU 频率
        int cpuFreq = config.getCpuFreqMax();
        tvCpuFreq.setText(cpuFreq > 0 ? cpuFreq + " MHz" : "--");

        // 电池容量
        int batteryCapacity = config.getBatteryCapacity();
        tvBatteryCapacityConfig.setText(batteryCapacity > 0 ? batteryCapacity + " mAh" : "--");

        // 存储加密状态
        String storageEncryption = config.getStorageEncryption();
        tvStorageEncryption.setText(storageEncryption != null && !storageEncryption.isEmpty()
                ? storageEncryption : getString(R.string.status_not_supported));
    }

    private void loadFallbackData() {
        tvDeviceName.setText(Build.BRAND + " " + Build.MODEL);
        tvDeviceModel.setText(Build.MODEL);
        tvAndroidVersion.setText(Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        tvProcessor.setText(Build.HARDWARE);
        tvRam.setText("--");
        tvStorage.setText("--");
        tvScreen.setText("--");
        tvActivationDate.setText("--");
        tvUsageDays.setText("--");
        tvActivationSource.setText(getString(R.string.source_internal));
        tvAvailableRam.setText("--");
        tvAvailableStorage.setText("--");
        tvNetworkType.setText(getString(R.string.status_unknown));
        tvGpu.setText(getString(R.string.status_not_recognized));
        tvCpuCores.setText("--");
        tvCpuFreq.setText("--");
        tvBatteryCapacityConfig.setText("--");
        tvStorageEncryption.setText(getString(R.string.status_not_supported));
    }

    private String formatMemory(long memoryMb) {
        if (memoryMb <= 0) return "--";
        if (memoryMb >= 1024) {
            return String.format(Locale.getDefault(), "%.1f GB", memoryMb / 1024.0);
        }
        return String.format(Locale.getDefault(), "%d MB", memoryMb);
    }

    private String formatStorage(long storageGb) {
        if (storageGb <= 0) return "--";
        if (storageGb >= 100) {
            return String.format(Locale.getDefault(), "%d GB", storageGb);
        }
        return String.format(Locale.getDefault(), "%.1f GB", storageGb / 1.0);
    }
}