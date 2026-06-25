package com.batteryhealth.app.ui.viewmodel;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.HealthRadarData;
import com.batteryhealth.app.data.repository.BatteryRepositoryImpl;
import com.batteryhealth.app.data.repository.DeviceRepositoryImpl;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.domain.repository.DeviceRepository;
import com.batteryhealth.app.domain.usecase.BatteryInsightUseCase;
import com.batteryhealth.app.domain.usecase.CalculateHealthUseCase;
import com.batteryhealth.app.utils.BatteryChemistryDetector;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.DeviceInfoManager;
import com.batteryhealth.app.utils.HealthReportGenerator;
import com.batteryhealth.app.utils.ThreadExecutor;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class BatteryHealthViewModel extends ViewModel {

    private static final String PREFS_BATTERY_HEALTH = "battery_health_prefs";
    private static final String PREF_REPLACEMENT_THRESHOLD = "replacement_threshold";
    private static final String PREF_CALIBRATION_SHOWN = "calibration_shown";
    private static final float DEFAULT_REPLACEMENT_THRESHOLD = 70f;

    private final MutableLiveData<BatteryInfo> batteryInfo = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<String> healthGrade = new MutableLiveData<>();
    private final MutableLiveData<String> healthStatus = new MutableLiveData<>();
    private final MutableLiveData<String> batterySource = new MutableLiveData<>();

    private final MutableLiveData<HealthRadarData> healthRadarData = new MutableLiveData<>();
    private final MutableLiveData<BatteryChemistryDetector.ChemistryResult> chemistryResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> showCalibrationSuggestion = new MutableLiveData<>(false);
    private final MutableLiveData<String> calibrationReason = new MutableLiveData<>();
    private final MutableLiveData<Boolean> showReplacementReminder = new MutableLiveData<>(false);
    private final MutableLiveData<Float> replacementThreshold = new MutableLiveData<>(DEFAULT_REPLACEMENT_THRESHOLD);
    private final MutableLiveData<HealthReportGenerator.HealthReport> dailyReport = new MutableLiveData<>();
    private final MutableLiveData<HealthReportGenerator.HealthReport> weeklyReport = new MutableLiveData<>();
    private final MutableLiveData<List<BatteryInsightUseCase.InsightItem>> insights = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentInsightIndex = new MutableLiveData<>(0);

    private final BatteryRepository batteryRepository;
    private final CalculateHealthUseCase calculateHealthUseCase;
    private BatteryInsightUseCase batteryInsightUseCase;
    private final BatteryDataManager batteryDataManager;
    private Context appContext;

    private final LinkedList<Float> recentHealthValues = new LinkedList<>();
    private int chargeDischargeCycleCount = 0;
    private boolean lastWasCharging = false;

    /** 标记 ViewModel 是否已销毁，用于取消后台任务回调 */
    private final AtomicBoolean isCleared = new AtomicBoolean(false);

    public BatteryHealthViewModel() {
        BatteryHealthApplication app = BatteryHealthApplication.getInstance();
        if (app == null) {
            android.util.Log.e("BatteryHealthViewModel", "Application instance is null, using fallback");
            batteryRepository = null;
            calculateHealthUseCase = null;
            batteryDataManager = null;
            appContext = null;
            return;
        }
        appContext = app.getApplicationContext();
        DeviceInfoManager deviceInfoManager = new DeviceInfoManager(app.getApplicationContext());
        
        batteryRepository = new BatteryRepositoryImpl(app);
        DeviceRepository deviceRepository = new DeviceRepositoryImpl(deviceInfoManager);
        calculateHealthUseCase = new CalculateHealthUseCase(batteryRepository, deviceRepository);
        batteryInsightUseCase = new BatteryInsightUseCase(batteryRepository, deviceRepository);
        batteryDataManager = batteryRepository instanceof BatteryRepositoryImpl 
                ? ((BatteryRepositoryImpl) batteryRepository).getBatteryDataManager() 
                : new BatteryDataManager(app.getApplicationContext());
        
        deviceInfoManager.setActivationInfoListener(new com.batteryhealth.app.utils.DeviceInfoManager.ActivationInfoListener() {
            @Override
            public void onActivationInfoReady(com.batteryhealth.app.utils.DeviceInfoManager.ActivationInfo activation) {
                if (batteryDataManager != null) {
                    batteryDataManager.setUsageDays(activation.usageDays);
                }
            }
        });

        loadReplacementThreshold();
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

    public LiveData<String> getHealthGrade() {
        return healthGrade;
    }

    public LiveData<String> getHealthStatus() {
        return healthStatus;
    }

    public LiveData<String> getBatterySource() {
        return batterySource;
    }

    public LiveData<HealthRadarData> getHealthRadarData() {
        return healthRadarData;
    }

    public LiveData<BatteryChemistryDetector.ChemistryResult> getChemistryResult() {
        return chemistryResult;
    }

    public LiveData<Boolean> getShowCalibrationSuggestion() {
        return showCalibrationSuggestion;
    }

    public LiveData<String> getCalibrationReason() {
        return calibrationReason;
    }

    public LiveData<Boolean> getShowReplacementReminder() {
        return showReplacementReminder;
    }

    public LiveData<Float> getReplacementThreshold() {
        return replacementThreshold;
    }

    public LiveData<HealthReportGenerator.HealthReport> getDailyReport() {
        return dailyReport;
    }

    public LiveData<HealthReportGenerator.HealthReport> getWeeklyReport() {
        return weeklyReport;
    }

    public LiveData<List<BatteryInsightUseCase.InsightItem>> getInsights() {
        return insights;
    }

    public LiveData<Integer> getCurrentInsightIndex() {
        return currentInsightIndex;
    }

    public void nextInsight() {
        List<BatteryInsightUseCase.InsightItem> list = insights.getValue();
        if (list == null || list.isEmpty()) return;
        int next = (currentInsightIndex.getValue() != null ? currentInsightIndex.getValue() + 1 : 1) % list.size();
        currentInsightIndex.postValue(next);
    }

    public void generateInsights() {
        if (batteryInsightUseCase == null) return;
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCleared.get()) return;
                try {
                    List<BatteryInsightUseCase.InsightItem> result = batteryInsightUseCase.getDailyInsights();
                    insights.postValue(result);
                    currentInsightIndex.postValue(0);
                } catch (Exception e) {
                    android.util.Log.e("BatteryHealthViewModel", "Error generating insights: " + e.getMessage());
                }
            }
        });
    }

    public void refreshData() {
        isLoading.postValue(true);
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCleared.get()) return;
                if (batteryRepository == null) {
                    android.util.Log.e("BatteryHealthViewModel", "batteryRepository is null, cannot refresh");
                    postNoDataState();
                    isLoading.postValue(false);
                    return;
                }
                try {
                    BatteryInfo info = batteryRepository.getCurrentBatteryInfo();
                    if (info != null) {
                        updateHealthInfo(info);
                        batteryInfo.postValue(info);
                        if (batteryDataManager != null) {
                            batterySource.postValue(batteryDataManager.getBatterySourceText());
                        } else {
                            batterySource.postValue("--");
                        }

                        updateHealthRadar(info);
                        detectChemistry();
                        checkCalibrationNeeded(info);
                        checkReplacementReminder(info);
                        trackChargeDischargeCycle(info);
                        generateInsights();
                    } else {
                        postNoDataState();
                    }
                } catch (Exception e) {
                    android.util.Log.e("BatteryHealthViewModel", "Error refreshing data: " + e.getMessage(), e);
                    postNoDataState();
                } finally {
                    isLoading.postValue(false);
                }
            }
        });
    }

    public void generateDailyReport() {
        if (appContext == null) return;
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCleared.get()) return;
                try {
                    HealthReportGenerator generator = new HealthReportGenerator(appContext);
                    HealthReportGenerator.HealthReport report = generator.generateDailyReport();
                    dailyReport.postValue(report);
                } catch (Exception e) {
                    android.util.Log.e("BatteryHealthViewModel", "Error generating daily report: " + e.getMessage());
                }
            }
        });
    }

    public void generateWeeklyReport() {
        if (appContext == null) return;
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCleared.get()) return;
                try {
                    HealthReportGenerator generator = new HealthReportGenerator(appContext);
                    HealthReportGenerator.HealthReport report = generator.generateWeeklyReport();
                    weeklyReport.postValue(report);
                } catch (Exception e) {
                    android.util.Log.e("BatteryHealthViewModel", "Error generating weekly report: " + e.getMessage());
                }
            }
        });
    }

    public void setReplacementThreshold(float threshold) {
        if (appContext == null) return;
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_BATTERY_HEALTH, Context.MODE_PRIVATE);
        prefs.edit().putFloat(PREF_REPLACEMENT_THRESHOLD, threshold).apply();
        replacementThreshold.postValue(threshold);
        BatteryInfo info = batteryInfo.getValue();
        if (info != null && info.hasValidHealthData()) {
            checkReplacementReminder(info);
        }
    }

    public void dismissCalibrationSuggestion() {
        if (appContext == null) return;
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_BATTERY_HEALTH, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_CALIBRATION_SHOWN, true).apply();
        showCalibrationSuggestion.postValue(false);
    }

    public void dismissReplacementReminder() {
        showReplacementReminder.postValue(false);
    }

    private void loadReplacementThreshold() {
        if (appContext == null) return;
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_BATTERY_HEALTH, Context.MODE_PRIVATE);
        float threshold = prefs.getFloat(PREF_REPLACEMENT_THRESHOLD, DEFAULT_REPLACEMENT_THRESHOLD);
        replacementThreshold.postValue(threshold);
    }

    private void updateHealthRadar(BatteryInfo info) {
        HealthRadarData radar = new HealthRadarData();

        float capacityHealth = info.hasValidHealthData() ? info.getHealthPercentage() : 50f;
        radar.setCapacityHealth(Math.max(0f, Math.min(100f, capacityHealth)));
        radar.setCapacityHealthDesc(capacityHealth >= 85 ? "容量状态良好" :
                capacityHealth >= 70 ? "容量有一定损耗" : "容量损耗较大");

        float cycleHealth;
        if (info.hasValidCycleCount()) {
            int cycles = info.getCycleCount();
            if (cycles < 300) cycleHealth = 95f;
            else if (cycles < 500) cycleHealth = 85f;
            else if (cycles < 800) cycleHealth = 70f;
            else if (cycles < 1000) cycleHealth = 55f;
            else cycleHealth = 40f;
        } else {
            cycleHealth = 60f;
        }
        radar.setCycleHealth(cycleHealth);
        radar.setCycleHealthDesc(cycleHealth >= 80 ? "循环寿命充足" :
                cycleHealth >= 60 ? "循环寿命中等" : "循环寿命较短");

        float temp = info.getTemperature();
        float tempHealth;
        if (temp <= 0) {
            tempHealth = 70f;
        } else if (temp >= 20f && temp <= 30f) {
            tempHealth = 100f;
        } else if (temp >= 10f && temp <= 35f) {
            tempHealth = 90f;
        } else if (temp >= 0f && temp <= 40f) {
            tempHealth = 75f;
        } else if (temp < 0f || temp > 45f) {
            tempHealth = 50f;
        } else {
            tempHealth = 60f;
        }
        radar.setTemperatureHealth(tempHealth);
        radar.setTemperatureHealthDesc(tempHealth >= 80 ? "温度状态良好" :
                tempHealth >= 60 ? "温度略高/低" : "温度异常");

        float voltage = info.getVoltage() / 1000f;
        float voltageHealth;
        if (voltage <= 0) {
            voltageHealth = 70f;
        } else if (voltage >= 3.7f && voltage <= 4.2f) {
            voltageHealth = 95f;
        } else if (voltage >= 3.5f && voltage <= 4.35f) {
            voltageHealth = 85f;
        } else if (voltage >= 3.2f && voltage <= 4.4f) {
            voltageHealth = 70f;
        } else {
            voltageHealth = 50f;
        }
        radar.setVoltageHealth(voltageHealth);
        radar.setVoltageHealthDesc(voltageHealth >= 80 ? "电压状态正常" :
                voltageHealth >= 60 ? "电压略有偏差" : "电压异常");

        float chargingHabitHealth = calculateChargingHabitHealth(info);
        radar.setChargingHabitHealth(chargingHabitHealth);
        radar.setChargingHabitHealthDesc(chargingHabitHealth >= 80 ? "充电习惯良好" :
                chargingHabitHealth >= 60 ? "充电习惯一般" : "需改善充电习惯");

        radar.calculateOverallScore();

        healthRadarData.postValue(radar);
    }

    private float calculateChargingHabitHealth(BatteryInfo info) {
        float score = 75f;

        int level = info.getLevel();
        if (level >= 20 && level <= 80) {
            score += 10f;
        } else if (level > 95 || level < 10) {
            score -= 10f;
        }

        if (info.isCharging() && level >= 90) {
            score -= 10f;
        }

        float temp = info.getTemperature();
        if (info.isCharging() && temp > 38f) {
            score -= 10f;
        }

        return Math.max(0f, Math.min(100f, score));
    }

    private void detectChemistry() {
        if (appContext == null) return;
        try {
            BatteryChemistryDetector.ChemistryResult result =
                    BatteryChemistryDetector.detect(appContext);
            chemistryResult.postValue(result);
        } catch (Exception e) {
            android.util.Log.e("BatteryHealthViewModel", "Chemistry detection error: " + e.getMessage());
        }
    }

    private void checkCalibrationNeeded(BatteryInfo info) {
        if (appContext == null || !info.hasValidHealthData()) return;

        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_BATTERY_HEALTH, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_CALIBRATION_SHOWN, false)) return;

        float currentHealth = info.getHealthPercentage();
        synchronized (recentHealthValues) {
            recentHealthValues.add(currentHealth);
            if (recentHealthValues.size() > 10) {
                recentHealthValues.removeFirst();
            }

            if (chargeDischargeCycleCount >= 3 && recentHealthValues.size() >= 5) {
                float max = Float.MIN_VALUE;
                float min = Float.MAX_VALUE;
                for (float h : recentHealthValues) {
                    if (h > max) max = h;
                    if (h < min) min = h;
                }
                float fluctuation = max - min;
                if (fluctuation > 5f) {
                    showCalibrationSuggestion.postValue(true);
                    calibrationReason.postValue(String.format(Locale.getDefault(),
                            "最近%d次充放电循环中健康度波动%.1f%%，建议进行容量校准",
                            chargeDischargeCycleCount, fluctuation));
                    chargeDischargeCycleCount = 0;
                }
            }
        }
    }

    private void trackChargeDischargeCycle(BatteryInfo info) {
        boolean isCharging = info.isCharging();
        if (isCharging && !lastWasCharging) {
            chargeDischargeCycleCount++;
        }
        lastWasCharging = isCharging;
    }

    private void checkReplacementReminder(BatteryInfo info) {
        if (!info.hasValidHealthData()) return;
        float threshold = replacementThreshold.getValue() != null
                ? replacementThreshold.getValue() : DEFAULT_REPLACEMENT_THRESHOLD;
        boolean needsReplacement = info.getHealthPercentage() < threshold;
        showReplacementReminder.postValue(needsReplacement);
    }

    private void postNoDataState() {
        batterySource.postValue("--");
        healthGrade.postValue("--");
        healthStatus.postValue("数据加载失败");
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