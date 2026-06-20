package com.batteryhealth.app.ui.config;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

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

import com.batteryhealth.app.BuildConfig;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.service.BatteryMonitorService;
import com.batteryhealth.app.ui.policy.PolicyActivity;
import com.batteryhealth.app.utils.DeviceInfoManager;
import com.batteryhealth.app.utils.UiAnimationHelper;

/**
 * 设备配置Fragment
 */
public class DeviceConfigFragment extends Fragment {
    
    private static final String TAG = "DeviceConfigFragment";
    
    private DeviceInfoManager deviceInfoManager;

    private TextView tvDeviceName;
    private TextView tvDeviceModel;
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
    private TextView tvAppVersion;
    private View rowPrivacyPolicy;
    private View rowUserAgreement;

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
        String message = getString(R.string.error_view_load_failed, e.getClass().getSimpleName(), e.getMessage());
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
            initPolicyEntries(view);
            animateCardsEntry(view);

            loadDeviceConfigAsync();
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }

    private void bindViews(View view) {
        tvDeviceName = view.findViewById(R.id.tv_device_name);
        tvDeviceModel = view.findViewById(R.id.tv_device_model);
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
        tvAppVersion = view.findViewById(R.id.tv_app_version);
        rowPrivacyPolicy = view.findViewById(R.id.row_privacy_policy);
        rowUserAgreement = view.findViewById(R.id.row_user_agreement);
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
            if (tvDeviceModel != null) {
                String model = Build.MODEL;
                tvDeviceModel.setText(model != null && !model.isEmpty() ? model : "--");
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
                String resolution = config.getScreenResolution();
                String size = config.getFormattedScreenSize();
                tvScreen.setText(resolution + " · " + size);
            }
            if (tvActivation != null) {
                String dateStr = config.getActivationDateStr();
                tvActivation.setText(dateStr != null && !"--".equals(dateStr) ? dateStr : "--");
            }
            if (tvUsageDays != null) {
                int usageDays = config.getUsageDays();
                tvUsageDays.setText(usageDays >= 0 ? getString(R.string.usage_days_format, usageDays) : getString(R.string.unit_days_fallback));
            }

            // 激活来源与可信度
            if (tvActivationSource != null) {
                String sourceText = config.getActivationSource();
                float confidence = config.getActivationConfidence();
                tvActivationSource.setText(String.format(Locale.getDefault(), getString(R.string.activation_source_confidence_format), getActivationSourceLabel(sourceText), confidence * 100));
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
                    tvAvailableStorage.setText(availStorage >= 100
                            ? String.format(Locale.getDefault(), "%d GB", availStorage)
                            : String.format(Locale.getDefault(), "%.1f GB", availStorage / 1.0));
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
        UiAnimationHelper.animateCardsEntry(view);
    }

    private String getActivationSourceLabel(String source) {
        if (source == null || "unknown".equals(source)) return getString(R.string.activation_source_unknown);
        switch (source) {
            case "electronic_warranty_card":
                return getString(R.string.activation_source_warranty);
            case "system_first_boot_time":
                return getString(R.string.activation_source_first_boot);
            case "device_policy_manager":
                return getString(R.string.activation_source_device_policy);
            case "gms_first_install":
                return getString(R.string.activation_source_google);
            case "system_framework_install":
                return getString(R.string.activation_source_framework);
            case "app_first_install":
                return getString(R.string.activation_source_app_first_install);
            case "app_data_directory":
                return getString(R.string.activation_source_app_data);
            default:
                return source;
        }
    }

    private void setDefaultValues() {
        if (tvDeviceName != null) tvDeviceName.setText("--");
        if (tvDeviceModel != null) tvDeviceModel.setText("--");
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
        if (tvAppVersion != null) {
            try {
                tvAppVersion.setText(BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
            } catch (Exception e) {
                tvAppVersion.setText("--");
            }
        }
    }

    /**
     * 初始化隐私政策/用户协议入口点击。
     */
    private void initPolicyEntries(View view) {
        if (rowPrivacyPolicy != null) {
            rowPrivacyPolicy.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(requireContext(), PolicyActivity.class);
                    intent.putExtra(PolicyActivity.EXTRA_TYPE, PolicyActivity.TYPE_PRIVACY);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "open privacy policy failed: " + e.getMessage());
                }
            });
        }
        if (rowUserAgreement != null) {
            rowUserAgreement.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(requireContext(), PolicyActivity.class);
                    intent.putExtra(PolicyActivity.EXTRA_TYPE, PolicyActivity.TYPE_AGREEMENT);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "open user agreement failed: " + e.getMessage());
                }
            });
        }
    }
}