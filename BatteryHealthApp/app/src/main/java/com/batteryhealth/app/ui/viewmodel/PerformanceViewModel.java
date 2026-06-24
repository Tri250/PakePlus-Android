package com.batteryhealth.app.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.repository.BatteryRepositoryImpl;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.utils.PerformanceAnalyzer;
import com.batteryhealth.app.utils.ThreadExecutor;

public class PerformanceViewModel extends ViewModel {

    private final MutableLiveData<Integer> cpuUsage = new MutableLiveData<>();
    private final MutableLiveData<Integer> memoryUsage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();

    private final PerformanceAnalyzer performanceAnalyzer;
    private final BatteryRepository batteryRepository;

    public PerformanceViewModel() {
        BatteryHealthApplication app = BatteryHealthApplication.getInstance();
        batteryRepository = new BatteryRepositoryImpl(app);
        performanceAnalyzer = new PerformanceAnalyzer(app.getApplicationContext());
    }

    public LiveData<Integer> getCpuUsage() {
        return cpuUsage;
    }

    public LiveData<Integer> getMemoryUsage() {
        return memoryUsage;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public void refreshData() {
        isLoading.postValue(true);
        ThreadExecutor.execute(() -> {
            try {
                int cpu = performanceAnalyzer.getCpuUsage();
                int memory = performanceAnalyzer.getMemoryUsage();
                
                cpuUsage.postValue(cpu);
                memoryUsage.postValue(memory);
            } catch (Exception e) {
                android.util.Log.e("PerformanceViewModel", "Error refreshing data: " + e.getMessage());
                cpuUsage.postValue(-1);
                memoryUsage.postValue(-1);
            } finally {
                isLoading.postValue(false);
            }
        });
    }
}