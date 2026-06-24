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

    private final GetTrendDataUseCase getTrendDataUseCase;

    /** 标记 ViewModel 是否已销毁，用于取消后台任务回调 */
    private final AtomicBoolean isCleared = new AtomicBoolean(false);

    public TrendViewModel() {
        BatteryHealthApplication app = BatteryHealthApplication.getInstance();
        BatteryRepository batteryRepository = new BatteryRepositoryImpl(app);
        getTrendDataUseCase = new GetTrendDataUseCase(batteryRepository);
        currentRange.setValue(GetTrendDataUseCase.RANGE_30D);
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

    /**
     * 加载指定时间范围的趋势数据
     */
    public void loadTrendData(int rangeIndex) {
        currentRange.setValue(rangeIndex);
        isLoading.postValue(true);
        ThreadExecutor.execute(() -> {
            if (isCleared.get()) return;
            try {
                GetTrendDataUseCase.Result result = getTrendDataUseCase.execute(rangeIndex);
                trendData.postValue(result);
            } catch (Exception e) {
                errorMessage.postValue("加载趋势数据失败: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
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
