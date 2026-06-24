package com.batteryhealth.app.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.data.repository.DeviceRepositoryImpl;
import com.batteryhealth.app.domain.repository.DeviceRepository;
import com.batteryhealth.app.utils.DeviceInfoManager;
import com.batteryhealth.app.utils.ThreadExecutor;

public class DeviceConfigViewModel extends ViewModel {

    private static final String TAG = "DeviceConfigViewModel";

    private final MutableLiveData<DeviceConfig> deviceConfig = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<Integer> usageDays = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private final DeviceRepository deviceRepository;

    public DeviceConfigViewModel() {
        BatteryHealthApplication app = BatteryHealthApplication.getInstance();
        if (app == null) {
            android.util.Log.e(TAG, "BatteryHealthApplication instance is null");
            deviceRepository = null;
            return;
        }
        try {
            DeviceInfoManager deviceInfoManager = new DeviceInfoManager(app.getApplicationContext());
            deviceRepository = new DeviceRepositoryImpl(deviceInfoManager);

            deviceInfoManager.setActivationInfoListener(activation -> {
                if (activation != null) {
                    usageDays.postValue(activation.usageDays);
                }
            });
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error initializing DeviceConfigViewModel: " + e.getMessage(), e);
            throw new RuntimeException("Failed to initialize DeviceConfigViewModel", e);
        }
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

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void loadDeviceConfig() {
        if (deviceRepository == null) {
            errorMessage.postValue("初始化失败，请重启应用");
            isLoading.postValue(false);
            return;
        }
        isLoading.postValue(true);
        ThreadExecutor.execute(() -> {
            try {
                DeviceConfig config = deviceRepository.getDeviceConfig();
                deviceConfig.postValue(config);

                int days = deviceRepository.getUsageDays();
                usageDays.postValue(days);
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error loading config: " + e.getMessage(), e);
                errorMessage.postValue("配置信息加载失败: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }
}