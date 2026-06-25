package com.batteryhealth.app.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.repository.BatteryRepositoryImpl;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.domain.usecase.GetTrendDataUseCase;
import com.batteryhealth.app.utils.ThreadExecutor;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 趋势追踪 ViewModel（v5.0 - 对标国内同类系统完整版）
 *
 * 支持：
 * - 多时间范围切换（7天/30天/90天/180天）
 * - 加载状态管理
 * - 错误处理
 */
public class TrendViewModel extends ViewModel {

    private final MutableLiveData<GetTrendDataUseCase.Result> trendData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentRange = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentChartType = new MutableLiveData<>();

    private GetTrendDataUseCase getTrendDataUseCase;

    /** 标记 ViewModel 是否已销毁，用于取消后台任务回调 */
    private final AtomicBoolean isCleared = new AtomicBoolean(false);

    public TrendViewModel() {
        getTrendDataUseCase = null;
        try {
            BatteryHealthApplication app = resolveApplication();
            if (app != null) {
                BatteryRepository batteryRepository = new BatteryRepositoryImpl(app);
                getTrendDataUseCase = new GetTrendDataUseCase(batteryRepository);
            }
        } catch (Exception e) {
            android.util.Log.e("TrendViewModel", "init failed", e);
        }
        currentRange.setValue(GetTrendDataUseCase.RANGE_30D);
        currentChartType.setValue(GetTrendDataUseCase.CHART_TYPE_HEALTH);
    }

    private BatteryHealthApplication resolveApplication() {
        BatteryHealthApplication app = BatteryHealthApplication.getInstance();
        if (app != null) return app;
        // 退路：通过反射调用 ActivityThread.currentApplication()
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object currentApp = activityThread.getMethod("currentApplication").invoke(null);
            if (currentApp instanceof BatteryHealthApplication) {
                return (BatteryHealthApplication) currentApp;
            }
        } catch (Throwable t) {
            android.util.Log.e("TrendViewModel", "Reflection fallback failed", t);
        }
        return null;
    }

    @Override
    protected void onCleared() {
        isCleared.set(true);
    }

    public LiveData<GetTrendDataUseCase.Result> getTrendData() {
        return trendData;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Integer> getCurrentRange() {
        return currentRange;
    }

    public LiveData<Integer> getCurrentChartType() {
        return currentChartType;
    }

    /**
     * 加载指定时间范围的趋势数据
     */
    public void loadTrendData(final int rangeIndex) {
        currentRange.setValue(rangeIndex);
        isLoading.postValue(true);
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCleared.get()) return;
                try {
                    if (getTrendDataUseCase == null) {
                        errorMessage.postValue("趋势模块未初始化，请稍后重试");
                        trendData.postValue(GetTrendDataUseCase.Result.empty(rangeIndex));
                        return;
                    }
                    GetTrendDataUseCase.Result result = getTrendDataUseCase.execute(rangeIndex);
                    if (result == null) {
                        result = GetTrendDataUseCase.Result.empty(rangeIndex);
                    }
                    trendData.postValue(result);
                } catch (Exception e) {
                    android.util.Log.e("TrendViewModel", "loadTrendData failed: " + e.getMessage(), e);
                    errorMessage.postValue("加载趋势数据失败: " + e.getMessage());
                    try {
                        trendData.postValue(GetTrendDataUseCase.Result.empty(rangeIndex));
                    } catch (Exception ignored) {
                    }
                } finally {
                    isLoading.postValue(false);
                }
            }
        });
    }

    /**
     * 切换时间范围
     */
    public void switchRange(int rangeIndex) {
        if (currentRange.getValue() != null && currentRange.getValue() == rangeIndex) {
            return;
        }
        loadTrendData(rangeIndex);
    }

    /**
     * 切换图表类型
     */
    public void switchChartType(int chartType) {
        if (currentChartType.getValue() != null && currentChartType.getValue() == chartType) {
            return;
        }
        currentChartType.setValue(chartType);
    }

    /**
     * 刷新当前范围的数据
     */
    public void refresh() {
        Integer range = currentRange.getValue();
        if (range != null) {
            loadTrendData(range);
        } else {
            loadTrendData(GetTrendDataUseCase.RANGE_30D);
        }
    }
}
