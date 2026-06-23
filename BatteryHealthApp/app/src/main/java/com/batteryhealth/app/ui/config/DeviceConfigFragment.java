package com.batteryhealth.app.ui.config;

import android.content.Context;
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
import com.batteryhealth.app.ui.viewmodel.DeviceConfigViewModel;
import com.batteryhealth.app.utils.DeviceConfigQuery;

import java.util.Locale;

public class DeviceConfigFragment extends Fragment {

    private static final String PREFS_CONFIG = "config_prefs";
    private static final String PREF_HEALTH_ALERT = "health_decay_alert";

    private TextView tvDeviceName, tvDeviceModel, tvAndroidVersion, tvProcessor, tvRam, tvStorage,
            tvScreen, tvActivationDate, tvUsageDays, tvActivationSource, tvAvailableRam,
            tvAvailableStorage, tvNetworkType, tvVersionAssessment, tvSecurityAssessment,
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

        tvDeviceName.setText(config.getFullModelName());
        tvDeviceModel.setText(config.getModel());
        tvAndroidVersion.setText(Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");

        String cpuInfo = config.getCpuInfo();
        tvProcessor.setText(cpuInfo != null && !cpuInfo.isEmpty() ? cpuInfo : Build.HARDWARE);

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