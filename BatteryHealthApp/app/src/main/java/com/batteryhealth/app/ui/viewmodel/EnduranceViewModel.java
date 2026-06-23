package com.batteryhealth.app.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.repository.BatteryRepositoryImpl;
import com.batteryhealth.app.domain.repository.BatteryRepository;

import java.util.Locale;

public class EnduranceViewModel extends ViewModel {

    private final MutableLiveData<BatteryInfo> batteryInfo = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<Float> estimatedHours = new MutableLiveData<>();

    private final BatteryRepository batteryRepository;

    public EnduranceViewModel() {
        BatteryHealthApplication app = BatteryHealthApplication.getInstance();
        batteryRepository = new BatteryRepositoryImpl(app);
    }

    public LiveData<BatteryInfo> getBatteryInfo() {
        return batteryInfo;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<Float> getEstimatedHours() {
        return estimatedHours;
    }

    public void refreshData() {
        isLoading.postValue(true);
        new Thread(() -> {
            try {
                BatteryInfo info = batteryRepository.getCurrentBatteryInfo();
                if (info != null) {
                    batteryInfo.postValue(info);
                    estimatedHours.postValue(calculateEndurance(info));
                }
            } catch (Exception e) {
                android.util.Log.e("EnduranceViewModel", "Error refreshing data: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        }).start();
    }

    private float calculateEndurance(BatteryInfo info) {
        if (info == null || info.getCurrentCapacity() <= 0) return 0f;
        
        float batteryCapacity = info.getCurrentCapacity();
        float dischargeRate = info.getCurrentNow() / 1000f;
        
        if (dischargeRate <= 0) {
            return estimateBasedOnLevel(info.getLevel());
        }
        
        float hours = (batteryCapacity / Math.abs(dischargeRate)) * (info.getLevel() / 100f);
        return Math.max(0.5f, hours);
    }

    private float estimateBasedOnLevel(int level) {
        float baseHours = 8f;
        return (level / 100f) * baseHours;
    }

    public String formatEstimatedHours(float hours) {
        if (hours < 1) {
            return String.format(Locale.getDefault(), "%d 分钟", (int) (hours * 60));
        }
        return String.format(Locale.getDefault(), "%.1f 小时", hours);
    }

    public String formatDischargeRate(float rate) {
        return String.format(Locale.getDefault(), "%.1f %%/h", Math.abs(rate));
    }
}