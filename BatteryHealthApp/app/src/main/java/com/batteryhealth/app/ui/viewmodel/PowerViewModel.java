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
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.DeviceInfoManager;
import com.batteryhealth.app.utils.ThreadExecutor;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class PowerViewModel extends ViewModel {

    private final MutableLiveData<BatteryInfo> batteryInfo = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<String> chargeType = new MutableLiveData<>();

    private final BatteryRepository batteryRepository;
    private final BatteryDataManager batteryDataManager;

    /** 标记 ViewModel 是否已销毁，用于取消后台任务回调 */
    private final AtomicBoolean isCleared = new AtomicBoolean(false);

    public PowerViewModel() {
        BatteryHealthApplication app = BatteryHealthApplication.getInstance();
        batteryRepository = new BatteryRepositoryImpl(app);
        batteryDataManager = batteryRepository instanceof BatteryRepositoryImpl 
                ? ((BatteryRepositoryImpl) batteryRepository).getBatteryDataManager() 
                : new BatteryDataManager(app.getApplicationContext());
    }

    @Override
    protected void onCleared() {
        isCleared.set(true);
    }

    public LiveData<BatteryInfo> getBatteryInfo() {
        return batteryInfo;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getChargeType() {
        return chargeType;
    }

    public void refreshData() {
        isLoading.postValue(true);
        ThreadExecutor.execute(() -> {
            if (isCleared.get()) return;
            try {
                BatteryInfo info = batteryRepository.getCurrentBatteryInfo();
                if (info != null) {
                    batteryInfo.postValue(info);
                    
                    float power = info.getChargingPower();
                    chargeType.postValue(getChargeTypeLabel(power));
                }
            } catch (Exception e) {
                android.util.Log.e("PowerViewModel", "Error refreshing data: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    private String getChargeTypeLabel(float power) {
        if (power >= 100) return "超级快充";
        if (power >= 60) return "极速快充";
        if (power >= 30) return "快充";
        if (power >= 10) return "普通充电";
        if (power > 0) return "慢速充电";
        return "未充电";
    }

    public String formatPower(float power) {
        return String.format(Locale.getDefault(), "%.1f W", power);
    }

    public String formatVoltage(float voltage) {
        return String.format(Locale.getDefault(), "%.2f V", voltage);
    }

    public String formatCurrent(float current) {
        return String.format(Locale.getDefault(), "%.2f A", current);
    }
}