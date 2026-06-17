package com.batteryhealth.app.ui.config;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.utils.AppManager;
import com.batteryhealth.app.utils.DeviceInfoManager;

public class DeviceConfigFragment extends Fragment {
    
    private static final String TAG = "DeviceConfigFragment";
    
    private DeviceInfoManager deviceInfoManager;
    private boolean initialized = false;
    
    private final Runnable dataChangeListener = () -> {
        if (getView() != null) {
            Log.d(TAG, "Data changed, refreshing config UI");
            initViews(getView());
        }
    };
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_device_config, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage());
            return new View(requireContext());
        }
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        deviceInfoManager = AppManager.getInstance().getDeviceInfoManager();
        AppManager.getInstance().addDataChangeListener(dataChangeListener);
        Log.d(TAG, "onViewCreated, deviceInfoManager=" + deviceInfoManager);
        initViews(view);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        deviceInfoManager = AppManager.getInstance().getDeviceInfoManager();
        if (getView() != null) {
            initViews(getView());
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        AppManager.getInstance().removeDataChangeListener(dataChangeListener);
    }
    
    private void initViews(View view) {
        try {
            // 每次都从单例获取
            deviceInfoManager = AppManager.getInstance().getDeviceInfoManager();
            
            TextView tvDeviceName = view.findViewById(R.id.tv_device_name);
            TextView tvAndroidVersion = view.findViewById(R.id.tv_android_version);
            TextView tvProcessor = view.findViewById(R.id.tv_processor);
            TextView tvMemory = view.findViewById(R.id.tv_memory);
            TextView tvStorage = view.findViewById(R.id.tv_storage);
            TextView tvScreen = view.findViewById(R.id.tv_screen);
            TextView tvActivation = view.findViewById(R.id.tv_activation);
            TextView tvUsageDays = view.findViewById(R.id.tv_usage_days);
            
            if (deviceInfoManager == null) {
                setDefaultValues(view);
                return;
            }
            
            DeviceConfig config = deviceInfoManager.getDeviceConfig();
            if (config == null) {
                setDefaultValues(view);
                return;
            }
            
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
                String act = config.getActivationDateStr();
                tvActivation.setText(act != null && !act.isEmpty() ? act : "--");
            }
            if (tvUsageDays != null) {
                tvUsageDays.setText(config.getUsageDays() + " 天");
            }
            initialized = true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage());
            setDefaultValues(view);
        }
    }
    
    private void setDefaultValues(View view) {
        try {
            TextView tvDeviceName = view.findViewById(R.id.tv_device_name);
            TextView tvAndroidVersion = view.findViewById(R.id.tv_android_version);
            TextView tvProcessor = view.findViewById(R.id.tv_processor);
            TextView tvMemory = view.findViewById(R.id.tv_memory);
            TextView tvStorage = view.findViewById(R.id.tv_storage);
            TextView tvScreen = view.findViewById(R.id.tv_screen);
            TextView tvActivation = view.findViewById(R.id.tv_activation);
            TextView tvUsageDays = view.findViewById(R.id.tv_usage_days);
            
            if (tvDeviceName != null) tvDeviceName.setText(BuildInfoHelper.getDeviceName());
            if (tvAndroidVersion != null) tvAndroidVersion.setText(BuildInfoHelper.getAndroidVersion());
            if (tvProcessor != null) tvProcessor.setText(BuildInfoHelper.getProcessorInfo());
            if (tvMemory != null) tvMemory.setText(BuildInfoHelper.getMemoryInfo());
            if (tvStorage != null) tvStorage.setText(BuildInfoHelper.getStorageInfo());
            if (tvScreen != null) tvScreen.setText(BuildInfoHelper.getScreenInfo());
            if (tvActivation != null) tvActivation.setText("--");
            if (tvUsageDays != null) tvUsageDays.setText("--");
        } catch (Exception e) {
            Log.e(TAG, "Error setting default values: " + e.getMessage());
        }
    }
}
