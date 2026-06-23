package com.batteryhealth.app.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.repository.BatteryRepositoryImpl;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.domain.usecase.GetTrendDataUseCase;

public class TrendViewModel extends ViewModel {

    private static final int DEFAULT_MONTHS = 6;

    private final MutableLiveData<GetTrendDataUseCase.Result> trendData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();

    private final GetTrendDataUseCase getTrendDataUseCase;

    public TrendViewModel() {
        BatteryHealthApplication app = BatteryHealthApplication.getInstance();
        BatteryRepository batteryRepository = new BatteryRepositoryImpl(app);
        getTrendDataUseCase = new GetTrendDataUseCase(batteryRepository);
    }

    public LiveData<GetTrendDataUseCase.Result> getTrendData() {
        return trendData;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public void loadTrendData() {
        isLoading.postValue(true);
        new Thread(() -> {
            try {
                GetTrendDataUseCase.Result result = getTrendDataUseCase.execute(DEFAULT_MONTHS);
                trendData.postValue(result);
            } catch (Exception e) {
                android.util.Log.e("TrendViewModel", "Error loading trend data: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        }).start();
    }
}