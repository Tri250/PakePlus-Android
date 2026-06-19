package com.batteryhealth.app.ui.config;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.service.BatteryMonitorService;
import com.batteryhealth.app.utils.DeviceInfoManager;

/**
 * 设备配置Fragment
 */
public class DeviceConfigFragment extends Fragment {
    
    private static final String TAG = "DeviceConfigFragment";
    
    private DeviceInfoManager deviceInfoManager;

    private TextView tvDeviceName;
    private TextView tvAndroidVersion;
    private TextView tvProcessor;
    private TextView tvMemory;
    private TextView tvStorage;
    private TextView tvScreen;
    private TextView tvActivation;
    private TextView tvUsageDays;
    private TextView tvActivationSource;
    private TextView tvAvailableMemory;
    private TextView tvAvailableStorage;
    private TextView tvNetworkType;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_device_config, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
            return createErrorView(e);
        }
    }

    private View createErrorView(Exception e) {
        Context ctx = getContext();
        if (ctx == null) {
            ctx = requireActivity();
        }
        android.widget.TextView errorView = new android.widget.TextView(ctx);
        String message = "界面加载失败\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(ctx, R.color.ios_label));
        errorView.setTextSize(16);
        errorView.setPadding(40, 100, 40, 40);
        errorView.setBackgroundColor(ContextCompat.getColor(ctx, R.color.ios_background));
        return errorView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            if (getActivity() instanceof MainActivity) {
                deviceInfoManager = ((MainActivity) getActivity()).getDeviceInfoManager();
            }

            bindViews(view);
            setDefaultValues();
            initHealthAlertSwitch(view);
            animateCardsEntry(view);

            loadDeviceConfigAsync();
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }

    private void bindViews(View view) {
        tvDeviceName = view.findViewById(R.id.tv_device_name);
        tvAndroidVersion = view.findViewById(R.id.tv_android_version);
        tvProcessor = view.findViewById(R.id.tv_processor);
        tvMemory = view.findViewById(R.id.tv_memory);
        tvStorage = view.findViewById(R.id.tv_storage);
        tvScreen = view.findViewById(R.id.tv_screen);
        tvActivation = view.findViewById(R.id.tv_activation);
        tvUsageDays = view.findViewById(R.id.tv_usage_days);
        tvActivationSource = view.findViewById(R.id.tv_activation_source);
        tvAvailableMemory = view.findViewById(R.id.tv_available_memory);
        tvAvailableStorage = view.findViewById(R.id.tv_available_storage);
        tvNetworkType = view.findViewById(R.id.tv_network_type);
    }

    private void loadDeviceConfigAsync() {
        if (deviceInfoManager == null) {
            return;
        }
        deviceInfoManager.getDeviceConfigAsync(new DeviceInfoManager.DeviceConfigCallback() {
            @Override
            public void onConfigLoaded(DeviceConfig config) {
                if (!isAdded()) return;
                updateViews(config);
            }

            @Override
            public void onConfigLoadFailed(Exception e) {
                Log.e(TAG, "Failed to load device config", e);
            }
        });
    }

    private void updateViews(DeviceConfig config) {
        if (config == null || !isAdded()) return;

        try {
            if (tvDeviceName != null) {
                tvDeviceName.setText(config.getFullModelName());
            }
            if (tvAndroidVersion != null) {
                tvAndroidVersion.setText(config.getAndroidCodename());
            }
            if (tvProcessor != null) {
                tvProcessor.setText(deviceInfoManager != null ? deviceInfoManager.getProcessorInfo() : "--");
            }
            if (tvMemory != null) {
                tvMemory.setText(config.getFormattedMemory());
            }
            if (tvStorage != null) {
                tvStorage.setText(config.getFormattedStorage());
            }
            if (tvScreen != null) {
                tvScreen.setText(config.getScreenResolution());
            }
            if (tvActivation != null) {
                String dateStr = config.getActivationDateStr();
                tvActivation.setText(dateStr != null && !"--".equals(dateStr) ? dateStr : "--");
            }
            if (tvUsageDays != null) {
                int usageDays = config.getUsageDays();
                tvUsageDays.setText(usageDays >= 0 ? usageDays + " 天" : "--");
            }

            // 激活来源与可信度
            if (tvActivationSource != null) {
                String sourceText = config.getActivationSource();
                float confidence = config.getActivationConfidence();
                tvActivationSource.setText(String.format(Locale.getDefault(), "%s (可信度 %.0f%%)", getActivationSourceLabel(sourceText), confidence * 100));
            }
            // 可用内存
            if (tvAvailableMemory != null) {
                long availMem = config.getAvailableMemory();
                if (availMem > 0) {
                    tvAvailableMemory.setText(availMem >= 1024
                            ? String.format(Locale.getDefault(), "%.1f GB", availMem / 1024.0)
                            : availMem + " MB");
                } else {
                    tvAvailableMemory.setText("--");
                }
            }
            // 可用存储
            if (tvAvailableStorage != null) {
                long availStorage = config.getAvailableStorage();
                if (availStorage > 0) {
                    tvAvailableStorage.setText(availStorage + " GB");
                } else {
                    tvAvailableStorage.setText("--");
                }
            }
            // 网络类型
            if (tvNetworkType != null) {
                String networkType = config.getNetworkType();
                tvNetworkType.setText(networkType != null ? networkType : "--");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating views: " + e.getMessage());
        }
    }

    /**
     * 初始化健康度衰减预警开关
     */
    private void initHealthAlertSwitch(View view) {
        try {
            SwitchCompat switchAlert = view.findViewById(R.id.switch_health_alert);
            if (switchAlert == null) {
                return;
            }

            SharedPreferences prefs = requireContext().getSharedPreferences(
                    BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean(BatteryMonitorService.PREF_ALERT_ENABLED, true);
            switchAlert.setChecked(enabled);

            switchAlert.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(BatteryMonitorService.PREF_ALERT_ENABLED, isChecked).apply();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing health alert switch: " + e.getMessage());
        }
    }
    
    private void animateCardsEntry(View view) {
        try {
            if (!(view instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup root = (android.view.ViewGroup) view;
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if (child.getId() == R.id.view_pager) continue;
                child.setAlpha(0f);
                child.setTranslationY(60f);
                child.setScaleX(0.94f);
                child.setScaleY(0.94f);
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(650)
                    .setStartDelay(i * 100L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f))
                    .start();
            }
        } catch (Exception e) {
            Log.d(TAG, "Liquid glass card animation skipped: " + e.getMessage());
        }
    }

    private String getActivationSourceLabel(String source) {
        if (source == null || "unknown".equals(source)) return "未知";
        switch (source) {
            case "electronic_warranty_card":
                return "电子保卡";
            case "system_first_boot_time":
                return "系统首次开机";
            case "device_policy_manager":
                return "设备管理策略";
            case "gms_first_install":
                return "Google 服务首次安装";
            case "system_framework_install":
                return "系统框架安装";
            case "app_first_install":
                return "本应用首次安装";
            case "app_data_directory":
                return "应用数据目录";
            default:
                return source;
        }
    }

    private void setDefaultValues() {
        if (tvDeviceName != null) tvDeviceName.setText("--");
        if (tvAndroidVersion != null) tvAndroidVersion.setText("--");
        if (tvProcessor != null) tvProcessor.setText("--");
        if (tvMemory != null) tvMemory.setText("--");
        if (tvStorage != null) tvStorage.setText("--");
        if (tvScreen != null) tvScreen.setText("--");
        if (tvActivation != null) tvActivation.setText("--");
        if (tvUsageDays != null) tvUsageDays.setText("--");
        if (tvActivationSource != null) tvActivationSource.setText("--");
        if (tvAvailableMemory != null) tvAvailableMemory.setText("--");
        if (tvAvailableStorage != null) tvAvailableStorage.setText("--");
        if (tvNetworkType != null) tvNetworkType.setText("--");
    }
}