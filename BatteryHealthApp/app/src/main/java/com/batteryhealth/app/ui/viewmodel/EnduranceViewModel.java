package com.batteryhealth.app.ui.viewmodel;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.repository.BatteryRepositoryImpl;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.utils.BatteryConsumptionAnalyzer;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.ThreadExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 续航分析 ViewModel — 唯一数据源，Fragment 不再直接采集数据。
 * 所有续航估算基于真实电流/容量计算，不使用固定基准值。
 */
public class EnduranceViewModel extends ViewModel {

    // 系统状态
    private final MutableLiveData<Integer> batteryLevel = new MutableLiveData<>();
    private final MutableLiveData<Float> temperature = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isCharging = new MutableLiveData<>();
    private final MutableLiveData<Float> dischargeRate = new MutableLiveData<>(); // %/h

    // 续航估算
    private final MutableLiveData<Float> estimatedEnduranceHours = new MutableLiveData<>();
    private final MutableLiveData<Float> estimatedChargeHours = new MutableLiveData<>();
    private final MutableLiveData<String> enduranceGrade = new MutableLiveData<>();
    private final MutableLiveData<String> enduranceGradeDescription = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isAbnormalDischarge = new MutableLiveData<>();

    // 耗电排行
    private final MutableLiveData<BatteryConsumptionAnalyzer.Result> analysisResult = new MutableLiveData<>();
    private final MutableLiveData<List<BatteryConsumptionAnalyzer.AppConsumption>> topConsumers = new MutableLiveData<>();

    // 省电建议
    private final MutableLiveData<List<String>> powerSavingTips = new MutableLiveData<>();

    // 屏幕亮屏时间
    private final MutableLiveData<Long> screenOnTimeMs = new MutableLiveData<>();
    private final MutableLiveData<Boolean> hasUsageAccess = new MutableLiveData<>();

    private final BatteryRepository batteryRepository;
    private Context appContext;

    /** 标记 ViewModel 是否已销毁，用于取消后台任务回调 */
    private final AtomicBoolean isCleared = new AtomicBoolean(false);

    public EnduranceViewModel() {
        BatteryHealthApplication app = BatteryHealthApplication.getInstance();
        if (app == null) {
            android.util.Log.e("EnduranceViewModel", "Application instance is null");
            batteryRepository = null;
            appContext = null;
            return;
        }
        batteryRepository = new BatteryRepositoryImpl(app);
        appContext = app.getApplicationContext();
    }

    @Override
    protected void onCleared() {
        isCleared.set(true);
    }

    public LiveData<Integer> getBatteryLevel() { return batteryLevel; }
    public LiveData<Float> getTemperature() { return temperature; }
    public LiveData<Boolean> getIsCharging() { return isCharging; }
    public LiveData<Float> getDischargeRate() { return dischargeRate; }
    public LiveData<Float> getEstimatedEnduranceHours() { return estimatedEnduranceHours; }
    public LiveData<Float> getEstimatedChargeHours() { return estimatedChargeHours; }
    public LiveData<String> getEnduranceGrade() { return enduranceGrade; }
    public LiveData<String> getEnduranceGradeDescription() { return enduranceGradeDescription; }
    public LiveData<Boolean> getIsAbnormalDischarge() { return isAbnormalDischarge; }
    public LiveData<BatteryConsumptionAnalyzer.Result> getAnalysisResult() { return analysisResult; }
    public LiveData<List<BatteryConsumptionAnalyzer.AppConsumption>> getTopConsumers() { return topConsumers; }
    public LiveData<List<String>> getPowerSavingTips() { return powerSavingTips; }
    public LiveData<Long> getScreenOnTimeMs() { return screenOnTimeMs; }
    public LiveData<Boolean> getHasUsageAccess() { return hasUsageAccess; }

    /**
     * 刷新所有续航数据 — ViewModel 为唯一数据源。
     */
    public void refreshData() {
        if (appContext == null) {
            android.util.Log.e("EnduranceViewModel", "appContext is null, cannot refresh");
            return;
        }
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCleared.get()) return;
                try {
                    // 1. 读取电池基础信息
                    Intent intent = appContext.registerReceiver(null,
                            new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    if (intent == null) return;

                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    int batteryPct = (int) ((level / (float) scale) * 100);
                    int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                            || status == BatteryManager.BATTERY_STATUS_FULL;
                    int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                    float tempC = temp / 10f;

                    batteryLevel.postValue(batteryPct);
                    temperature.postValue(tempC);
                    isCharging.postValue(charging);

                    // 2. 执行耗电分析
                    BatteryConsumptionAnalyzer.Result analysis =
                            BatteryConsumptionAnalyzer.analyze(appContext, 24 * 60 * 60 * 1000L);
                    analysisResult.postValue(analysis);

                    // TOP 耗电应用
                    if (analysis != null && analysis.topConsumers != null && !analysis.topConsumers.isEmpty()) {
                        topConsumers.postValue(analysis.topConsumers);
                    } else {
                        topConsumers.postValue(new ArrayList<>());
                    }

                    // 3. 计算真实放电速率
                    float rate = calculateDischargeRate(batteryPct, charging, analysis);
                    dischargeRate.postValue(rate);

                    // 4. 计算续航/充电时间
                    if (charging) {
                        float chargeHours = calculateChargeTime(batteryPct, analysis);
                        estimatedChargeHours.postValue(chargeHours);
                        estimatedEnduranceHours.postValue(0f);
                    } else {
                        float enduranceHours = calculateEnduranceHours(batteryPct, rate, analysis);
                        estimatedEnduranceHours.postValue(enduranceHours);
                        estimatedChargeHours.postValue(0f);
                    }

                    // 5. 续航等级评估
                    float enduranceForGrade = charging ? 0f : (estimatedEnduranceHours.getValue() != null ? estimatedEnduranceHours.getValue() : 0f);
                    String grade = assessEnduranceGrade(batteryPct, rate, charging);
                    String gradeDesc = assessEnduranceDescription(grade);
                    enduranceGrade.postValue(grade);
                    enduranceGradeDescription.postValue(gradeDesc);

                    // 6. 耗电异常提醒
                    boolean abnormal = !charging && rate > 15f;
                    isAbnormalDischarge.postValue(abnormal);

                    // 7. 屏幕亮屏时间
                    boolean hasAccess = BatteryConsumptionAnalyzer.hasUsageAccess(appContext);
                    hasUsageAccess.postValue(hasAccess);
                    if (hasAccess) {
                        long screenTime = queryScreenOnTime();
                        screenOnTimeMs.postValue(screenTime);
                    } else {
                        screenOnTimeMs.postValue(-1L);
                    }

                    // 8. 省电建议
                    List<String> tips = generatePowerSavingTips(batteryPct, rate, tempC, charging, analysis);
                    powerSavingTips.postValue(tips);

                } catch (Exception e) {
                    android.util.Log.e("EnduranceViewModel", "Error refreshing: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 计算真实放电速率（%/h），基于电流和容量，不使用固定基准值。
     */
    private float calculateDischargeRate(int batteryPct, boolean isCharging,
                                          BatteryConsumptionAnalyzer.Result analysis) {
        if (isCharging) return 0f;

        // 方式1：从分析结果获取
        if (analysis != null && analysis.systemEstimatedHours > 0 && batteryPct > 0) {
            return batteryPct / (float) analysis.systemEstimatedHours;
        }

        // 方式2：从 BatteryManager 电流/容量计算
        try {
            BatteryManager bm = (BatteryManager) appContext.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
                if (currentAvg == 0 || currentAvg == Integer.MIN_VALUE) {
                    currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                }
                if (currentAvg != 0 && currentAvg != Integer.MIN_VALUE) {
                    int capacityMicroAh = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                    if (capacityMicroAh == Integer.MIN_VALUE || capacityMicroAh == 0) {
                        capacityMicroAh = bm.getIntProperty(24);
                    }
                    int capacityMah = -1;
                    if (capacityMicroAh > 100000) capacityMah = capacityMicroAh / 1000;
                    else if (capacityMicroAh > 100) capacityMah = capacityMicroAh;

                    if (capacityMah > 0) {
                        float absCurrentMa = Math.abs(currentAvg / 1000f);
                        if (absCurrentMa > 0) {
                            float hours = (capacityMah * (batteryPct / 100f)) / absCurrentMa;
                            if (hours > 0) return batteryPct / hours;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return 0f;
    }

    /**
     * 计算放电续航时间（小时），基于真实电流/容量。
     */
    private float calculateEnduranceHours(int batteryPct, float rate,
                                            BatteryConsumptionAnalyzer.Result analysis) {
        // 方式1：从分析结果获取系统预估
        if (analysis != null && analysis.systemEstimatedHours > 0) {
            return (float) analysis.systemEstimatedHours;
        }

        // 方式2：从放电速率计算
        if (rate > 0) {
            return batteryPct / rate;
        }

        // 方式3：从 BatteryManager 直接计算
        try {
            BatteryManager bm = (BatteryManager) appContext.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
                if (currentAvg == 0 || currentAvg == Integer.MIN_VALUE) {
                    currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                }
                int voltageMicroV = 0;
                try { voltageMicroV = bm.getIntProperty(2); } catch (Throwable ignored) {}
                if (voltageMicroV <= 0) {
                    Intent bi = appContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    if (bi != null) voltageMicroV = bi.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                }

                int capacityMicroAh = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                if (capacityMicroAh == Integer.MIN_VALUE || capacityMicroAh == 0) {
                    capacityMicroAh = bm.getIntProperty(24);
                }
                int capacityMah = -1;
                if (capacityMicroAh > 100000) capacityMah = capacityMicroAh / 1000;
                else if (capacityMicroAh > 100) capacityMah = capacityMicroAh;

                if (currentAvg != 0 && currentAvg != Integer.MIN_VALUE && capacityMah > 0 && voltageMicroV > 0) {
                    double voltageV = voltageMicroV / 1_000_000.0;
                    double currentMa = Math.abs(currentAvg / 1000.0);
                    double powerMw = currentMa * voltageV;
                    double energyMwh = capacityMah * voltageV * (batteryPct / 100.0);
                    if (powerMw > 0) return (float) (energyMwh / powerMw);
                }
            }
        } catch (Exception ignored) {}

        return 0f;
    }

    /**
     * 计算充电预估时间（小时），基于充电电流和剩余容量。
     */
    private float calculateChargeTime(int batteryPct, BatteryConsumptionAnalyzer.Result analysis) {
        // 方式1：Android 16+ 系统预估
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                long remainingMs = Settings.Global.getLong(
                        appContext.getContentResolver(), "battery_estimated_remaining_time_ms", -1);
                if (remainingMs > 0) return remainingMs / 3600000f;
            } catch (Throwable ignored) {}
        }

        // 方式2：基于充电电流和剩余容量计算
        try {
            BatteryManager bm = (BatteryManager) appContext.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
                if (currentAvg == 0 || currentAvg == Integer.MIN_VALUE) {
                    currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                }
                // 充电时电流为负值
                int chargingCurrentMa = Math.abs(currentAvg / 1000);
                if (chargingCurrentMa <= 0) return 0f;

                int capacityMicroAh = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                if (capacityMicroAh == Integer.MIN_VALUE || capacityMicroAh == 0) {
                    capacityMicroAh = bm.getIntProperty(24);
                }
                int capacityMah = -1;
                if (capacityMicroAh > 100000) capacityMah = capacityMicroAh / 1000;
                else if (capacityMicroAh > 100) capacityMah = capacityMicroAh;

                if (capacityMah > 0) {
                    // 剩余需要充入的容量
                    float remainingMah = capacityMah * ((100 - batteryPct) / 100f);
                    // 考虑充电效率（恒流阶段效率高，恒压/涓流阶段效率低）
                    float efficiency = batteryPct < 80 ? 0.85f : 0.55f;
                    float hours = remainingMah / (chargingCurrentMa * efficiency);
                    return Math.max(0.1f, hours);
                }
            }
        } catch (Exception ignored) {}

        return 0f;
    }

    /**
     * 续航等级评估（与国内同类系统一致）。
     */
    private String assessEnduranceGrade(int batteryPct, float rate, boolean isCharging) {
        if (isCharging) {
            if (batteryPct >= 80) return "即将充满";
            if (batteryPct >= 50) return "充电中";
            return "电量偏低";
        }
        if (rate <= 0) {
            if (batteryPct >= 50) return "续航充裕";
            if (batteryPct >= 20) return "续航一般";
            return "电量告急";
        }
        float remainingHours = batteryPct / rate;
        if (remainingHours >= 8f) return "续航充裕";
        if (remainingHours >= 4f) return "续航良好";
        if (remainingHours >= 2f) return "续航一般";
        if (remainingHours >= 1f) return "续航偏低";
        return "电量告急";
    }

    private String assessEnduranceDescription(String grade) {
        switch (grade) {
            case "续航充裕": return "可放心使用各类应用";
            case "续航良好": return "日常使用无压力";
            case "续航一般": return "建议关注电量变化";
            case "续航偏低": return "建议减少后台应用运行";
            case "电量告急": return "建议尽快接入充电器";
            case "即将充满": return "可随时拔掉充电器";
            case "充电中": return "请耐心等待充电完成";
            default: return "";
        }
    }

    /**
     * 基于真实数据动态生成省电建议。
     */
    private List<String> generatePowerSavingTips(int batteryPct, float rate, float tempC,
                                                   boolean isCharging, BatteryConsumptionAnalyzer.Result analysis) {
        List<String> tips = new ArrayList<>();

        // 放电速率异常
        if (!isCharging && rate > 15f) {
            tips.add(String.format(Locale.getDefault(), "当前放电速率 %.1f%%/h 异常偏高，建议检查后台高耗电应用", rate));
        } else if (!isCharging && rate > 10f) {
            tips.add(String.format(Locale.getDefault(), "放电速率 %.1f%%/h 偏高，建议关闭不必要的后台应用", rate));
        }

        // 低电量建议
        if (!isCharging && batteryPct <= 20) {
            tips.add("电量低于20%，建议开启省电模式并尽快充电");
        } else if (!isCharging && batteryPct <= 40) {
            tips.add("电量偏低，建议降低屏幕亮度并关闭后台刷新");
        }

        // 温度建议
        if (tempC > 40f) {
            tips.add(String.format(Locale.getDefault(), "电池温度 %.1f°C 过高，建议取下手机壳并暂停高负载应用", tempC));
        } else if (tempC > 35f && isCharging) {
            tips.add("充电时温度偏高，建议降低屏幕亮度或取下手机壳帮助散热");
        }

        // 充电建议
        if (isCharging && batteryPct >= 80) {
            tips.add("电量已充至80%以上，长期保持满充会加速电池老化，建议拔掉充电器");
        }

        // 耗电应用建议
        if (analysis != null && analysis.topConsumers != null && !analysis.topConsumers.isEmpty()) {
            BatteryConsumptionAnalyzer.AppConsumption top = analysis.topConsumers.get(0);
            if (top.percent > 30) {
                tips.add(String.format(Locale.getDefault(), "%s 耗电占比 %.0f%%，建议检查其后台活动", top.displayName, top.percent));
            }
        }

        // 屏幕建议
        if (!isCharging && batteryPct < 60) {
            tips.add("降低屏幕亮度是延长续航最有效的方式");
        }

        if (tips.isEmpty()) {
            tips.add("电池状态良好，保持20%-80%电量区间使用可延长电池寿命");
        }

        return tips;
    }

    /**
     * 查询屏幕亮屏时间（基于 UsageStatsManager）。
     */
    private long queryScreenOnTime() {
        try {
            android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager)
                    appContext.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return 0;

            long endTime = System.currentTimeMillis();
            long startTime = endTime - 24 * 60 * 60 * 1000L;

            long totalForegroundMs = 0;
            java.util.Map<String, android.app.usage.UsageStats> stats =
                    usm.queryAndAggregateUsageStats(startTime, endTime);
            if (stats != null) {
                for (android.app.usage.UsageStats usageStats : stats.values()) {
                    totalForegroundMs += usageStats.getTotalTimeInForeground();
                }
            }
            return totalForegroundMs;
        } catch (Exception e) {
            return 0;
        }
    }
}
