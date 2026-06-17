package com.batteryhealth.app.ui.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
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
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_device_config, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
            return createErrorView("界面加载失败，请重启应用");
        }
    }

    private View createErrorView(String message) {
        android.widget.TextView errorView = new android.widget.TextView(requireContext());
        errorView.setText(message);
        errorView.setTextColor(0xFF000000);
        errorView.setTextSize(16);
        errorView.setPadding(40, 100, 40, 40);
        errorView.setBackgroundColor(0xFFF2F2F7);
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
                tvActivation.setText(config.getActivationDateStr() != null ? config.getActivationDateStr() : "--");
            }
            if (tvUsageDays != null) {
                tvUsageDays.setText(config.getUsageDays() + " 天");
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
    }
}