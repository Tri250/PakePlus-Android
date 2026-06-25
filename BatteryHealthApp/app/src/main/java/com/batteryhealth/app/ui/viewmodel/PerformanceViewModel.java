package com.batteryhealth.app.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.model.PerformanceData;
import com.batteryhealth.app.data.repository.BatteryRepositoryImpl;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.utils.PerformanceAnalyzer;
import com.batteryhealth.app.utils.ThreadExecutor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class PerformanceViewModel extends ViewModel {

    private final MutableLiveData<Integer> cpuUsage = new MutableLiveData<>();
    private final MutableLiveData<Integer> memoryUsage = new MutableLiveData<>();
    private final MutableLiveData<Integer> storageUsage = new MutableLiveData<>();
    private final MutableLiveData<PerformanceAnalyzer.PerformanceScoreResult> performanceScore = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<Float> appCpuUsage = new MutableLiveData<>();
    private final MutableLiveData<Long> appMemoryUsage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> foregroundServiceRunning = new MutableLiveData<>();
    private final MutableLiveData<PerformanceAnalyzer.AnrAnalysisResult> anrResult = new MutableLiveData<>();
    private final MutableLiveData<List<String>> performanceSuggestions = new MutableLiveData<>();

    private final PerformanceAnalyzer performanceAnalyzer;
    private final BatteryRepository batteryRepository;

    /** 标记 ViewModel 是否已销毁，用于取消后台任务回调 */
    private final AtomicBoolean isCleared = new AtomicBoolean(false);

    public PerformanceViewModel() {
        BatteryHealthApplication app = BatteryHealthApplication.getInstance();
        if (app == null) {
            android.util.Log.e("PerformanceViewModel", "Application instance is null");
            batteryRepository = null;
            performanceAnalyzer = null;
            return;
        }
        batteryRepository = new BatteryRepositoryImpl(app);
        performanceAnalyzer = new PerformanceAnalyzer(app.getApplicationContext());
    }

    @Override
    protected void onCleared() {
        isCleared.set(true);
    }

    public LiveData<Integer> getCpuUsage() { return cpuUsage; }
    public LiveData<Integer> getMemoryUsage() { return memoryUsage; }
    public LiveData<Integer> getStorageUsage() { return storageUsage; }
    public LiveData<PerformanceAnalyzer.PerformanceScoreResult> getPerformanceScore() { return performanceScore; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<Float> getAppCpuUsage() { return appCpuUsage; }
    public LiveData<Long> getAppMemoryUsage() { return appMemoryUsage; }
    public LiveData<Boolean> getForegroundServiceRunning() { return foregroundServiceRunning; }
    public LiveData<PerformanceAnalyzer.AnrAnalysisResult> getAnrResult() { return anrResult; }
    public LiveData<List<String>> getPerformanceSuggestions() { return performanceSuggestions; }

    /**
     * 刷新所有性能数据 — ViewModel 为唯一数据源，Fragment 不再直接采集。
     */
    public void refreshData() {
        isLoading.postValue(true);
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCleared.get()) return;
                try {
                    // 系统级指标
                    int cpu = performanceAnalyzer.getCpuUsage();
                    int memory = performanceAnalyzer.getMemoryUsage();
                    int storage = performanceAnalyzer.getStorageUsage();

                    cpuUsage.postValue(cpu);
                    memoryUsage.postValue(memory);
                    storageUsage.postValue(storage);

                    // 多维加权性能评分
                    PerformanceAnalyzer.PerformanceScoreResult scoreResult =
                            performanceAnalyzer.calculatePerformanceScore();
                    performanceScore.postValue(scoreResult);

                    // 应用级指标
                    appCpuUsage.postValue(performanceAnalyzer.getAppCpuUsage());
                    appMemoryUsage.postValue(performanceAnalyzer.getAppMemoryUsage());
                    foregroundServiceRunning.postValue(performanceAnalyzer.isForegroundServiceRunning());

                    // ANR 分析
                    anrResult.postValue(scoreResult.anrResult);

                    // 动态性能建议
                    List<String> suggestions = performanceAnalyzer.generateDynamicSuggestions(scoreResult);
                    performanceSuggestions.postValue(suggestions);

                    // 持久化性能数据到 Room
                    savePerformanceData(cpu, memory, storage, scoreResult.totalScore);

                } catch (Exception e) {
                    android.util.Log.e("PerformanceViewModel", "Error refreshing data: " + e.getMessage());
                    cpuUsage.postValue(-1);
                    memoryUsage.postValue(-1);
                } finally {
                    isLoading.postValue(false);
                }
            }
        });
    }

    /**
     * 将性能评分数据持久化到 Room 数据库。
     */
    private void savePerformanceData(int cpu, int memory, int storage, int score) {
        try {
            PerformanceData data = new PerformanceData();
            data.setCpuUsage(cpu);
            data.setMemoryUsed(memory);
            data.setPerformanceScore(score);
            data.setHasIssue(cpu > 85 || memory > 90 || storage > 95);
            if (cpu > 85) data.setIssueType("cpu_overload");
            else if (memory > 90) data.setIssueType("memory_pressure");
            else if (storage > 95) data.setIssueType("storage_full");
            else data.setIssueType("none");
            data.setIssueDescription(String.format("CPU:%d%% MEM:%d%% STORAGE:%d%% Score:%d",
                    cpu, memory, storage, score));
            batteryRepository.savePerformanceData(data);
        } catch (Exception e) {
            android.util.Log.w("PerformanceViewModel", "Failed to save performance data: " + e.getMessage());
        }
    }
}
