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

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.DeviceConfig;
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
            Log.e(TAG, "Error inflating layout: " + e.getMessage());
            View errorView = new View(requireContext());
            return errorView;
        }
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
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage());
            setDefaultValues(view);
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