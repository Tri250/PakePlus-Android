package com.batteryhealth.app.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.data.repository.DeviceRepositoryImpl;
import com.batteryhealth.app.domain.repository.DeviceRepository;
import com.batteryhealth.app.utils.DeviceInfoManager;

public class DeviceConfigViewModel extends ViewModel {

    private final MutableLiveData<DeviceConfig> deviceConfig = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<Integer> usageDays = new MutableLiveData<>();

    private final DeviceRepository deviceRepository;

    public DeviceConfigViewModel() {
        BatteryHealthApplication app = BatteryHealthApplication.getInstance();
        DeviceInfoManager deviceInfoManager = new DeviceInfoManager(app.getApplicationContext());
        deviceRepository = new DeviceRepositoryImpl(deviceInfoManager);
        
        deviceInfoManager.setActivationInfoListener(activation -> {
            if (activation != null) {
                usageDays.postValue(activation.usageDays);
            }
        });
    }

    public LiveData<DeviceConfig> getDeviceConfig() {
        return deviceConfig;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<Integer> getUsageDays() {
        return usageDays;
    }

    public void loadDeviceConfig() {
        isLoading.postValue(true);
        new Thread(() -> {
            try {
                DeviceConfig config = deviceRepository.getDeviceConfig();
                deviceConfig.postValue(config);
                
                int days = deviceRepository.getUsageDays();
                usageDays.postValue(days);
            } catch (Exception e) {
                android.util.Log.e("DeviceConfigViewModel", "Error loading config: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        }).start();
    }
}