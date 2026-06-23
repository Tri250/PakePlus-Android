package com.batteryhealth.app.ui.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
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

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.utils.DeviceConfigQuery;
import com.batteryhealth.app.utils.DeviceInfoManager;

import java.util.Locale;

public class DeviceConfigFragment extends Fragment {

    private static final String TAG = "DeviceConfigFragment";
    private static final String PREFS_CONFIG = "config_prefs";
    private static final String PREF_HEALTH_ALERT = "health_decay_alert";

    private TextView tvDeviceName, tvDeviceModel, tvAndroidVersion, tvProcessor, tvRam, tvStorage,
            tvScreen, tvActivationDate, tvUsageDays, tvActivationSource, tvAvailableRam,
            tvAvailableStorage, tvNetworkType, tvVersionAssessment, tvSecurityAssessment,
            tvPerformanceAssessment, tvSuggestions;
    private Switch switchHealthAlert;
    private DeviceConfigQuery configQuery;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_device_config, container, false);
        initViews(view);
        animateEntry(view);
        loadData();
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
        tvVersionAssessment = view.findViewById(R.id.tv_version_assessment);
        tvSecurityAssessment = view.findViewById(R.id.tv_security_assessment);
        tvPerformanceAssessment = view.findViewById(R.id.tv_performance_assessment);
        tvSuggestions = view.findViewById(R.id.tv_suggestions);
        switchHealthAlert = view.findViewById(R.id.switch_health_alert);

        configQuery = new DeviceConfigQuery(requireContext());

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_CONFIG, Context.MODE_PRIVATE);
        switchHealthAlert.setChecked(prefs.getBoolean(PREF_HEALTH_ALERT, true));
        switchHealthAlert.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_HEALTH_ALERT, isChecked).apply();
        });
    }

    private void animateEntry(View view) {
        try {
            Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
            view.startAnimation(fadeUp);
        } catch (Exception e) {
            // 动画加载失败静默处理
        }
    }

    private void loadData() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;

        DeviceInfoManager deviceInfoManager = activity.getDeviceInfoManager();
        if (deviceInfoManager == null) {
            loadFallbackData();
            return;
        }

        deviceInfoManager.getDeviceConfigAsync(new DeviceInfoManager.DeviceConfigCallback() {
            @Override
            public void onConfigLoaded(DeviceConfig config) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    applyConfig(config);
                    loadSystemAnalysis();
                });
            }

            @Override
            public void onConfigLoadFailed(Exception e) {
                Log.e(TAG, "Failed to load device config", e);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    loadFallbackData();
                    loadSystemAnalysis();
                });
            }
        });
    }

    private void loadSystemAnalysis() {
        try {
            if (configQuery == null) return;
            DeviceConfigQuery.ConfigAnalysisResult result = configQuery.analyzeConfiguration();
            if (result == null) return;
            if (tvVersionAssessment != null) tvVersionAssessment.setText(result.versionAssessment);
            if (tvSecurityAssessment != null) tvSecurityAssessment.setText(result.securityAssessment);
            if (tvPerformanceAssessment != null) tvPerformanceAssessment.setText(result.performanceAssessment);
            if (tvSuggestions != null) tvSuggestions.setText(result.suggestions);
        } catch (Exception e) {
            Log.e(TAG, "Error loading system analysis: " + e.getMessage());
        }
    }

    private void applyConfig(DeviceConfig config) {
        if (config == null) return;
        try {
            // 设备名称：使用 DeviceConfig 的营销型号名
            if (tvDeviceName != null) tvDeviceName.setText(config.getFullModelName());
            if (tvDeviceModel != null) tvDeviceModel.setText(config.getModel());

            // Android 版本
            if (tvAndroidVersion != null)
                tvAndroidVersion.setText(Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");

            // 处理器：使用 DeviceConfig 的 cpuInfo（含营销名称）
            String cpuInfo = config.getCpuInfo();
            if (tvProcessor != null)
                tvProcessor.setText(cpuInfo != null && !cpuInfo.isEmpty() ? cpuInfo : Build.HARDWARE);

            // 内存：使用 DeviceConfig 的格式化内存（按营销规格取整）
            if (tvRam != null) tvRam.setText(config.getFormattedMemory());

            // 存储：使用 DeviceConfig 的格式化存储
            if (tvStorage != null) tvStorage.setText(config.getFormattedStorage());

            // 屏幕：使用 DeviceConfig 的屏幕信息（WindowMetrics API，含尺寸）
            StringBuilder screenInfo = new StringBuilder();
            screenInfo.append(config.getScreenResolution());
            if (config.getScreenSize() > 0) {
                screenInfo.append("  ").append(config.getFormattedScreenSize());
            }
            if (tvScreen != null) tvScreen.setText(screenInfo.toString());

            // 激活日期：使用 DeviceConfig 的激活日期（ActivationDateHelper 内部实现）
            String activationDateStr = config.getActivationDateStr();
            if (tvActivationDate != null)
                tvActivationDate.setText(activationDateStr != null && !activationDateStr.isEmpty()
                        ? activationDateStr : "--");

            // 使用天数
            int usageDays = config.getUsageDays();
            if (tvUsageDays != null)
                tvUsageDays.setText(usageDays >= 0 ? usageDays + " 天" : "--");

            // 激活来源：使用 DeviceConfig 的激活来源（非硬编码）
            String activationSource = config.getActivationSource();
            if (tvActivationSource != null)
                tvActivationSource.setText(activationSource != null && !activationSource.isEmpty()
                        ? activationSource : getString(R.string.source_internal));

            // 可用内存
            long availableMemoryMb = config.getAvailableMemory();
            if (tvAvailableRam != null) {
                if (availableMemoryMb > 0) {
                    tvAvailableRam.setText(formatMemory(availableMemoryMb));
                } else {
                    tvAvailableRam.setText("--");
                }
            }

            // 可用存储
            long availableStorageGb = config.getAvailableStorage();
            if (tvAvailableStorage != null) {
                if (availableStorageGb > 0) {
                    tvAvailableStorage.setText(formatStorage(availableStorageGb));
                } else {
                    tvAvailableStorage.setText("--");
                }
            }

            // 网络类型：使用 DeviceConfig 的网络类型（ConnectivityManager 内部实现）
            String networkType = config.getNetworkType();
            if (tvNetworkType != null)
                tvNetworkType.setText(networkType != null && !networkType.isEmpty()
                        ? networkType : getString(R.string.status_unknown));
        } catch (Exception e) {
            Log.e(TAG, "Error applying config: " + e.getMessage());
        }
    }

    /**
     * DeviceInfoManager 不可用时的回退数据
     */
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
