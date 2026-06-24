package com.batteryhealth.app.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.repository.BatteryRepositoryImpl;
import com.batteryhealth.app.data.repository.DeviceRepositoryImpl;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.domain.repository.DeviceRepository;
import com.batteryhealth.app.domain.usecase.CalculateHealthUseCase;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.DeviceInfoManager;
import com.batteryhealth.app.utils.ThreadExecutor;

import java.util.Locale;

public class BatteryHealthViewModel extends ViewModel {

    private final MutableLiveData<BatteryInfo> batteryInfo = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<String> healthGrade = new MutableLiveData<>();
    private final MutableLiveData<String> healthStatus = new MutableLiveData<>();
    private final MutableLiveData<String> batterySource = new MutableLiveData<>();

    private final BatteryRepository batteryRepository;
    private final CalculateHealthUseCase calculateHealthUseCase;
    private final BatteryDataManager batteryDataManager;

    public BatteryHealthViewModel() {
        BatteryHealthApplication app = BatteryHealthApplication.getInstance();
        DeviceInfoManager deviceInfoManager = new DeviceInfoManager(app.getApplicationContext());
        
        batteryRepository = new BatteryRepositoryImpl(app);
        DeviceRepository deviceRepository = new DeviceRepositoryImpl(deviceInfoManager);
        calculateHealthUseCase = new CalculateHealthUseCase(batteryRepository, deviceRepository);
        batteryDataManager = batteryRepository instanceof BatteryRepositoryImpl 
                ? ((BatteryRepositoryImpl) batteryRepository).getBatteryDataManager() 
                : new BatteryDataManager(app.getApplicationContext());
        
        deviceInfoManager.setActivationInfoListener(activation -> {
            if (batteryDataManager != null) {
                batteryDataManager.setUsageDays(activation.usageDays);
            }
        });
    }

    public LiveData<BatteryInfo> getBatteryInfo() {
        return batteryInfo;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getHealthGrade() {
        return healthGrade;
    }

    public LiveData<String> getHealthStatus() {
        return healthStatus;
    }

    public LiveData<String> getBatterySource() {
        return batterySource;
    }

    public void refreshData() {
        isLoading.postValue(true);
        ThreadExecutor.execute(() -> {
            try {
                BatteryInfo info = batteryRepository.getCurrentBatteryInfo();
                if (info != null) {
                    updateHealthInfo(info);
                    batteryInfo.postValue(info);
                    batterySource.postValue(batteryDataManager.getBatterySourceText());
                }
            } catch (Exception e) {
                android.util.Log.e("BatteryHealthViewModel", "Error refreshing data: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    private void updateHealthInfo(BatteryInfo info) {
        if (info.hasValidHealthData()) {
            float health = info.getHealthPercentage();
            // 统一使用 BatteryInfo 作为唯一数据源，阈值：95+极佳，85+良好，75+一般，60+较差，<60极差
            healthGrade.postValue(info.getHealthGrade());
            healthStatus.postValue(info.getHealthDescription());
        } else {
            healthGrade.postValue("--");
            healthStatus.postValue("无法获取健康数据");
        }
    }

    public String formatCapacity(BatteryInfo info) {
        if (info == null) return "--";
        
        int designCap = info.getDesignCapacity();
        int currentCap = info.getCurrentCapacity();
        
        if (designCap > 0 && currentCap > 0) {
            return String.format(Locale.getDefault(), "%d / %d mAh", currentCap, designCap);
        } else if (designCap > 0) {
            return String.format(Locale.getDefault(), "%d mAh", designCap);
        } else if (currentCap > 0) {
            return String.format(Locale.getDefault(), "%d mAh", currentCap);
        }
        return "--";
    }

    public String formatCycleCount(BatteryInfo info) {
        if (info == null || !info.hasValidCycleCount()) return "--";
        return String.format(Locale.getDefault(), "%d 次", info.getCycleCount());
    }

    public String formatTemperature(float temp) {
        return String.format(Locale.getDefault(), "%.1f°C", temp);
    }

    public String formatVoltage(float voltage) {
        return String.format(Locale.getDefault(), "%.2f V", voltage / 1000f);
    }

    public String formatCurrent(int current) {
        return String.format(Locale.getDefault(), "%.0f mA", Math.abs(current / 1000f));
    }
}