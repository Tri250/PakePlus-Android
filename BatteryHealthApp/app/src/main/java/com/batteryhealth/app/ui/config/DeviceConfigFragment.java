package com.batteryhealth.app.ui.config;

import android.os.Bundle;
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
    
    private DeviceInfoManager deviceInfoManager;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device_config, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        if (getActivity() instanceof MainActivity) {
            deviceInfoManager = ((MainActivity) getActivity()).getDeviceInfoManager();
        }
        
        initViews(view);
    }
    
    private void initViews(View view) {
        if (deviceInfoManager == null) return;
        
        DeviceConfig config = deviceInfoManager.getDeviceConfig();
        
        ((TextView) view.findViewById(R.id.tv_device_name)).setText(config.getFullModelName());
        ((TextView) view.findViewById(R.id.tv_android_version)).setText(config.getAndroidCodename());
        ((TextView) view.findViewById(R.id.tv_processor)).setText(deviceInfoManager.getProcessorInfo());
        ((TextView) view.findViewById(R.id.tv_memory)).setText(config.getFormattedMemory());
        ((TextView) view.findViewById(R.id.tv_storage)).setText(config.getFormattedStorage());
        ((TextView) view.findViewById(R.id.tv_screen)).setText(config.getScreenResolution());
        ((TextView) view.findViewById(R.id.tv_activation)).setText(config.getActivationDateStr());
        ((TextView) view.findViewById(R.id.tv_usage_days)).setText(config.getUsageDays() + " 天");
    }
}