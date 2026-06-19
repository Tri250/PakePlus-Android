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
        android.widget.TextView errorView = new android.widget.TextView(requireContext());
        String message = "界面加载失败\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_label));
        errorView.setTextSize(16);
        errorView.setPadding(40, 100, 40, 40);
        errorView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_background));
        return errorView;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            if (getActivity() instanceof MainActivity) {
                deviceInfoManager = ((MainActivity) getActivity()).getDeviceInfoManager();
            }
            
            initViews(view);
            animateCardsEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }
    
    private void initViews(View view) {
        try {
            if (deviceInfoManager == null) {
                setDefaultValues(view);
                return;
            }
            
            DeviceConfig config = deviceInfoManager.getDeviceConfig();
            if (config == null) {
                setDefaultValues(view);
                return;
            }
            
            TextView tvDeviceName = view.findViewById(R.id.tv_device_name);
            TextView tvAndroidVersion = view.findViewById(R.id.tv_android_version);
            TextView tvProcessor = view.findViewById(R.id.tv_processor);
            TextView tvMemory = view.findViewById(R.id.tv_memory);
            TextView tvStorage = view.findViewById(R.id.tv_storage);
            TextView tvScreen = view.findViewById(R.id.tv_screen);
            TextView tvActivation = view.findViewById(R.id.tv_activation);
            TextView tvUsageDays = view.findViewById(R.id.tv_usage_days);
            tvActivationSource = view.findViewById(R.id.tv_activation_source);
            tvAvailableMemory = view.findViewById(R.id.tv_available_memory);
            tvAvailableStorage = view.findViewById(R.id.tv_available_storage);
            tvNetworkType = view.findViewById(R.id.tv_network_type);
            
            if (tvDeviceName != null) {
                tvDeviceName.setText(config.getFullModelName());
            }
            if (tvAndroidVersion != null) {
                tvAndroidVersion.setText(config.getAndroidCodename());
            }
            if (tvProcessor != null) {
                tvProcessor.setText(deviceInfoManager.getProcessorInfo());
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
                String sourceText = deviceInfoManager.getActivationSourceText();
                float confidence = deviceInfoManager.getActivationConfidence();
                String sourceLabel = "unknown".equals(sourceText) ? "未知" : sourceText;
                tvActivationSource.setText(String.format(Locale.getDefault(), "%s (可信度 %.0f%%)", sourceLabel, confidence * 100));
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

            initHealthAlertSwitch(view);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage());
            setDefaultValues(view);
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

    private void setDefaultValues(View view) {
        TextView tvDeviceName = view.findViewById(R.id.tv_device_name);
        TextView tvAndroidVersion = view.findViewById(R.id.tv_android_version);
        TextView tvProcessor = view.findViewById(R.id.tv_processor);
        TextView tvMemory = view.findViewById(R.id.tv_memory);
        TextView tvStorage = view.findViewById(R.id.tv_storage);
        TextView tvScreen = view.findViewById(R.id.tv_screen);
        TextView tvActivation = view.findViewById(R.id.tv_activation);
        TextView tvUsageDays = view.findViewById(R.id.tv_usage_days);
        
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