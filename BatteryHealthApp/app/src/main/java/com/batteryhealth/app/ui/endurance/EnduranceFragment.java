package com.batteryhealth.app.ui.endurance;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;
import com.batteryhealth.app.utils.BatteryConsumptionAnalyzer;
import com.batteryhealth.app.utils.StateLayoutHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 续航分析页面
 *
 * 功能：
 * 1. 实时估算剩余续航时间
 * 2. 展示放电速率、温度等关键指标
 * 3. 多维度拆解续航表现
 * 4. 支持手表专属续航数据分析
 * 5. 15分钟滑动窗口放电速率（替代2次采样差值+指数平滑）
 * 6. 应用耗电排行（基于 UsageStatsManager）
 * 7. 续航预测增强（乐观/正常/悲观三种预估）
 * 8. 屏幕开启/关闭分开统计放电速率
 */
public class EnduranceFragment extends Fragment {

    private static final String PREFS_ENDURANCE = "endurance_prefs";
    private static final String KEY_LAST_LEVEL = "last_level";
    private static final String KEY_LAST_TIME = "last_time";
    private static final String KEY_DISCHARGE_RATE = "discharge_rate";
    private static final String KEY_HISTORICAL_AVG = "historical_avg_discharge";
    private static final String KEY_HISTORICAL_MIN = "historical_min_discharge";
    private static final String KEY_HISTORICAL_MAX = "historical_max_discharge";
    private static final String KEY_HISTORICAL_DAY = "historical_record_day";

    /** 采样间隔：1分钟 */
    private static final long SAMPLE_INTERVAL_MS = 60_000L;
    /** 滑动窗口：15分钟 */
    private static final long WINDOW_MS = 15 * 60_000L;
    /** 窗口内最少数据点数，不足时使用历史基线 */
    private static final int MIN_DATA_POINTS = 5;

    private TextView tvEnduranceHours, tvEnduranceMeta;
    private TextView tvMetricBattery, tvMetricDischarge, tvMetricTemp;
    private TextView tvChargingStatus, tvUsedTime, tvConsumedBattery, tvEstimatedFull, tvScreenOnTime;
    private TextView tvWatchEndurance, tvWatchMode, tvAppConsumptionTitle;
    private View watchSection;

    // 放电速率分析
    private TextView tvRealtimeDischarge, tvAvg15minDischarge, tvHistoricalBaseline;
    private TextView tvScreenOnDischarge, tvScreenOffDischarge;

    // 续航预估
    private TextView tvForecastOptimistic, tvForecastNormal, tvForecastPessimistic;

    // 应用耗电排行
    private View cardAppConsumption;
    private LinearLayout layoutUsagePermissionGuide;
    private LinearLayout layoutAppList;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private StateLayoutHelper stateLayoutHelper;

    private int lastBatteryLevel = -1;
    private long lastUpdateTime = -1;
    private float dischargeRate = 0f;
    private boolean isWatch = false;

    private DischargeRateTracker dischargeRateTracker;

    // ─────────────────────────────────────────────────────────
    // DischargeRateTracker：15分钟滑动窗口放电速率追踪器
    // ─────────────────────────────────────────────────────────

    /**
     * 维护最近15分钟的电量采样队列（每分钟1次采样），
     * 使用滑动窗口计算放电速率（%/h），
     * 窗口内数据点不足5个时使用历史基线。
     * 同时分别统计亮屏和息屏放电速率。
     */
    static class DischargeRateTracker {

        /** 单个采样点 */
        static class Sample {
            final long timestamp;
            final int level;
            final boolean screenOn;

            Sample(long timestamp, int level, boolean screenOn) {
                this.timestamp = timestamp;
                this.level = level;
                this.screenOn = screenOn;
            }
        }

        private final ArrayList<Sample> samples = new ArrayList<>();
        private long lastSampleTime = 0;

        /** 记录一个采样点（每分钟最多1次） */
        void addSample(long now, int level, boolean screenOn) {
            // 1分钟内不重复采样
            if (now - lastSampleTime < SAMPLE_INTERVAL_MS) return;
            lastSampleTime = now;
            samples.add(new Sample(now, level, screenOn));
            // 移除窗口外的旧数据
            long cutoff = now - WINDOW_MS;
            while (!samples.isEmpty() && samples.get(0).timestamp < cutoff) {
                samples.remove(0);
            }
        }

        /** 计算滑动窗口内的放电速率（%/h），数据不足时返回 -1 */
        float calculateWindowRate() {
            if (samples.size() < 2) return -1f;
            Sample oldest = samples.get(0);
            Sample newest = samples.get(samples.size() - 1);
            long elapsedMs = newest.timestamp - oldest.timestamp;
            if (elapsedMs <= 0) return -1f;
            float elapsedHours = elapsedMs / (1000f * 60f * 60f);
            if (elapsedHours <= 0) return -1f;
            int delta = oldest.level - newest.level;
            if (delta <= 0) return 0f; // 充电中或电量不变
            return delta / elapsedHours;
        }

        /** 计算实时放电速率（最近2个采样点） */
        float calculateRealtimeRate() {
            int size = samples.size();
            if (size < 2) return -1f;
            Sample prev = samples.get(size - 2);
            Sample curr = samples.get(size - 1);
            long elapsedMs = curr.timestamp - prev.timestamp;
            if (elapsedMs <= 0) return -1f;
            float elapsedHours = elapsedMs / (1000f * 60f * 60f);
            if (elapsedHours <= 0) return -1f;
            int delta = prev.level - curr.level;
            if (delta <= 0) return 0f;
            return delta / elapsedHours;
        }

        /** 亮屏放电速率（%/h） */
        float calculateScreenOnRate() {
            return calculateRateByScreen(true);
        }

        /** 息屏放电速率（%/h） */
        float calculateScreenOffRate() {
            return calculateRateByScreen(false);
        }

        private float calculateRateByScreen(boolean screenOn) {
            ArrayList<Sample> filtered = new ArrayList<>();
            for (Sample s : samples) {
                if (s.screenOn == screenOn) filtered.add(s);
            }
            if (filtered.size() < 2) return -1f;
            Sample oldest = filtered.get(0);
            Sample newest = filtered.get(filtered.size() - 1);
            long elapsedMs = newest.timestamp - oldest.timestamp;
            if (elapsedMs <= 0) return -1f;
            float elapsedHours = elapsedMs / (1000f * 60f * 60f);
            if (elapsedHours <= 0) return -1f;
            int delta = oldest.level - newest.level;
            if (delta <= 0) return 0f;
            return delta / elapsedHours;
        }

        int getSampleCount() {
            return samples.size();
        }
    }

    // ─────────────────────────────────────────────────────────
    // 生命周期
    // ─────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_endurance, container, false);
        isWatch = detectWatch();
        initViews(view);
        animateEntry(view);
        loadSavedState();
        dischargeRateTracker = new DischargeRateTracker();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 在 onViewCreated 后初始化 StateLayoutHelper，确保视图层级已完整构建
        if (view instanceof ViewGroup) {
            ViewGroup scrollChild = (ViewGroup) view;
            if (scrollChild.getChildCount() > 0 && scrollChild.getChildAt(0) instanceof ViewGroup) {
                try {
                    stateLayoutHelper = new StateLayoutHelper((ViewGroup) scrollChild.getChildAt(0));
                } catch (Exception e) {
                    android.util.Log.e("EnduranceFragment", "StateLayoutHelper init failed", e);
                }
            }
        }
    }

    private boolean detectWatch() {
        Context ctx = getContext();
        if (ctx == null) return false;
        return (ctx.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_TYPE_MASK)
                == android.content.res.Configuration.UI_MODE_TYPE_WATCH;
    }

    private void initViews(View view) {
        tvEnduranceHours = view.findViewById(R.id.tv_endurance_hours);
        tvEnduranceMeta = view.findViewById(R.id.tv_endurance_meta);
        tvMetricBattery = view.findViewById(R.id.tv_metric_battery);
        tvMetricDischarge = view.findViewById(R.id.tv_metric_discharge);
        tvMetricTemp = view.findViewById(R.id.tv_metric_temp);
        tvChargingStatus = view.findViewById(R.id.tv_charging_status);
        tvUsedTime = view.findViewById(R.id.tv_used_time);
        tvConsumedBattery = view.findViewById(R.id.tv_consumed_battery);
        tvEstimatedFull = view.findViewById(R.id.tv_estimated_full);
        tvScreenOnTime = view.findViewById(R.id.tv_screen_on_time);
        tvWatchEndurance = view.findViewById(R.id.tv_watch_endurance);
        tvWatchMode = view.findViewById(R.id.tv_watch_mode);
        watchSection = view.findViewById(R.id.watch_section);
        tvAppConsumptionTitle = view.findViewById(R.id.tv_app_consumption_title);

        // 放电速率分析
        tvRealtimeDischarge = view.findViewById(R.id.tv_realtime_discharge);
        tvAvg15minDischarge = view.findViewById(R.id.tv_avg_15min_discharge);
        tvHistoricalBaseline = view.findViewById(R.id.tv_historical_baseline);
        tvScreenOnDischarge = view.findViewById(R.id.tv_screen_on_discharge);
        tvScreenOffDischarge = view.findViewById(R.id.tv_screen_off_discharge);

        // 续航预估
        tvForecastOptimistic = view.findViewById(R.id.tv_forecast_optimistic);
        tvForecastNormal = view.findViewById(R.id.tv_forecast_normal);
        tvForecastPessimistic = view.findViewById(R.id.tv_forecast_pessimistic);

        // 应用耗电排行
        cardAppConsumption = view.findViewById(R.id.card_app_consumption);
        layoutUsagePermissionGuide = view.findViewById(R.id.layout_usage_permission_guide);
        layoutAppList = view.findViewById(R.id.layout_app_list);

        if (isWatch && watchSection != null) {
            watchSection.setVisibility(View.VISIBLE);
        }

        // 应用耗电排行权限引导按钮
        View btnGrant = view.findViewById(R.id.btn_grant_usage_stats);
        if (btnGrant != null) {
            btnGrant.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    startActivity(intent);
                } catch (Exception ignored) {}
            });
        }
    }

    private void animateEntry(View view) {
        if (!isAdded()) return;
        Context ctx = getContext();
        if (ctx == null) return;
        Animation fadeUp = AnimationUtils.loadAnimation(ctx, R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerBatteryReceiver();
        startPeriodicUpdate();
        refreshAppConsumption();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterBatteryReceiver();
        stopPeriodicUpdate();
        saveState();
    }

    private void loadSavedState() {
        Context ctx = getContext();
        if (ctx == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_ENDURANCE, Context.MODE_PRIVATE);
        lastBatteryLevel = prefs.getInt(KEY_LAST_LEVEL, -1);
        lastUpdateTime = prefs.getLong(KEY_LAST_TIME, -1);
        dischargeRate = prefs.getFloat(KEY_DISCHARGE_RATE, 0f);
    }

    private void saveState() {
        Context ctx = getContext();
        if (ctx == null) return;
        ctx.getSharedPreferences(PREFS_ENDURANCE, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_LEVEL, lastBatteryLevel)
                .putLong(KEY_LAST_TIME, lastUpdateTime)
                .putFloat(KEY_DISCHARGE_RATE, dischargeRate)
                .apply();
    }

    // ─────────────────────────────────────────────────────────
    // 历史基线（过去7天平均放电速率）
    // ─────────────────────────────────────────────────────────

    private void updateHistoricalBaseline(float currentRate) {
        if (currentRate <= 0) return;
        Context ctx = getContext();
        if (ctx == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_ENDURANCE, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long todayStart = now / (24 * 60 * 60 * 1000);

        long recordDay = prefs.getLong(KEY_HISTORICAL_DAY, 0);
        float avg = prefs.getFloat(KEY_HISTORICAL_AVG, 0f);
        float min = prefs.getFloat(KEY_HISTORICAL_MIN, 0f);
        float max = prefs.getFloat(KEY_HISTORICAL_MAX, 0f);

        if (recordDay == 0 || avg <= 0) {
            // 首次记录
            prefs.edit()
                    .putLong(KEY_HISTORICAL_DAY, todayStart)
                    .putFloat(KEY_HISTORICAL_AVG, currentRate)
                    .putFloat(KEY_HISTORICAL_MIN, currentRate)
                    .putFloat(KEY_HISTORICAL_MAX, currentRate)
                    .apply();
        } else {
            // 累积平均：指数移动平均，权重偏向近期
            avg = avg * 0.8f + currentRate * 0.2f;
            if (min <= 0 || currentRate < min) min = currentRate;
            if (currentRate > max) max = currentRate;
            // 超过7天重置最小/最大值（避免过时极值）
            if (todayStart - recordDay > 7) {
                min = currentRate;
                max = currentRate;
                prefs.edit().putLong(KEY_HISTORICAL_DAY, todayStart).apply();
            }
            prefs.edit()
                    .putFloat(KEY_HISTORICAL_AVG, avg)
                    .putFloat(KEY_HISTORICAL_MIN, min)
                    .putFloat(KEY_HISTORICAL_MAX, max)
                    .apply();
        }
    }

    private float getHistoricalAvgBaseline() {
        Context ctx = getContext();
        if (ctx == null) return isWatch ? 5.5f : 12.2f;
        float avg = ctx.getSharedPreferences(PREFS_ENDURANCE, Context.MODE_PRIVATE)
                .getFloat(KEY_HISTORICAL_AVG, 0f);
        return avg > 0 ? avg : (isWatch ? 5.5f : 12.2f);
    }

    private float getHistoricalMin() {
        Context ctx = getContext();
        if (ctx == null) return isWatch ? 3.0f : 6.0f;
        float min = ctx.getSharedPreferences(PREFS_ENDURANCE, Context.MODE_PRIVATE)
                .getFloat(KEY_HISTORICAL_MIN, 0f);
        return min > 0 ? min : (isWatch ? 3.0f : 6.0f);
    }

    private float getHistoricalMax() {
        Context ctx = getContext();
        if (ctx == null) return isWatch ? 10.0f : 25.0f;
        float max = ctx.getSharedPreferences(PREFS_ENDURANCE, Context.MODE_PRIVATE)
                .getFloat(KEY_HISTORICAL_MAX, 0f);
        return max > 0 ? max : (isWatch ? 10.0f : 25.0f);
    }

    // ─────────────────────────────────────────────────────────
    // 屏幕状态判断
    // ─────────────────────────────────────────────────────────

    private boolean isScreenOn() {
        Context ctx = getContext();
        if (ctx == null) return false;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm == null) return false;
        return pm.isInteractive();
    }

    // ─────────────────────────────────────────────────────────
    // 电池数据更新
    // ─────────────────────────────────────────────────────────

    private void registerBatteryReceiver() {
        Context ctx = getContext();
        if (ctx == null) return;
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        ctx.registerReceiver(batteryReceiver, filter);
    }

    private void unregisterBatteryReceiver() {
        Context ctx = getContext();
        if (ctx == null) return;
        try {
            ctx.unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void startPeriodicUpdate() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateBatteryData();
                handler.postDelayed(this, SAMPLE_INTERVAL_MS); // 每分钟采样一次
            }
        };
        handler.post(updateRunnable);
    }

    private void stopPeriodicUpdate() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateFromIntent(intent);
        }
    };

    private void updateBatteryData() {
        Context ctx = getContext();
        if (ctx == null) return;
        Intent intent = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent != null) {
            updateFromIntent(intent);
        }
    }

    private void updateFromIntent(Intent intent) {
        if (!isAdded() || getContext() == null) return;

        // 首次数据到达，显示内容
        if (stateLayoutHelper != null && stateLayoutHelper.getCurrentState() != StateLayoutHelper.State.CONTENT) {
            stateLayoutHelper.showContent();
        }

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPct = scale > 0 ? (int) ((level / (float) scale) * 100) : 0;

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        String chargingStatus = isCharging ? getString(R.string.status_charging) : getString(R.string.status_discharging);

        int current = 0;
        Context ctx = getContext();
        if (ctx != null) {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            }
        }
        // BATTERY_PROPERTY_CURRENT_NOW 单位因设备而异：µA 或 mA
        int absCurrent = Math.abs(current);
        float currentMa;
        if (absCurrent > 100000) {
            currentMa = absCurrent / 1000f; // µA → mA
        } else {
            currentMa = absCurrent;         // 已经是 mA
        }

        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        float tempC = (temp != Integer.MIN_VALUE) ? temp / 10f : -1f;

        long now = System.currentTimeMillis();
        boolean screenOn = isScreenOn();

        // ── 采样到 DischargeRateTracker ──
        if (dischargeRateTracker != null && !isCharging) {
            dischargeRateTracker.addSample(now, batteryPct, screenOn);
        }

        // ── 计算放电速率（使用15分钟滑动窗口） ──
        float windowRate = -1f;
        float realtimeRate = -1f;
        if (dischargeRateTracker != null) {
            windowRate = dischargeRateTracker.calculateWindowRate();
            realtimeRate = dischargeRateTracker.calculateRealtimeRate();
        }

        // 确定最终使用的放电速率
        float historicalBaseline = getHistoricalAvgBaseline();
        if (windowRate > 0 && dischargeRateTracker != null
                && dischargeRateTracker.getSampleCount() >= MIN_DATA_POINTS) {
            // 窗口内数据充足，使用窗口均值
            dischargeRate = windowRate;
        } else if (windowRate > 0) {
            // 数据不足但已有窗口值，混合历史基线
            dischargeRate = windowRate * 0.4f + historicalBaseline * 0.6f;
        } else if (dischargeRate <= 0) {
            // 完全没有数据，使用历史基线
            dischargeRate = historicalBaseline;
        }

        // 更新历史基线
        if (!isCharging && dischargeRate > 0) {
            updateHistoricalBaseline(dischargeRate);
        }

        lastBatteryLevel = batteryPct;
        lastUpdateTime = now;

        // ── 续航预估（主卡片） ──
        float remainingHours = isCharging ? 0 : batteryPct / Math.max(dischargeRate, 0.01f);
        int hours = (int) remainingHours;
        int minutes = (int) ((remainingHours - hours) * 60);
        safeSetText(tvEnduranceHours, String.format(Locale.getDefault(), "%d小时%d分", hours, minutes));
        safeSetText(tvEnduranceMeta, String.format(Locale.getDefault(),
                getString(R.string.meta_endurance), batteryPct, dischargeRate));

        // ── 快速指标 ──
        safeSetText(tvMetricBattery, String.format(Locale.getDefault(), "%d%%", batteryPct));
        safeSetText(tvMetricDischarge, String.format(Locale.getDefault(), "%.1f%%/h", dischargeRate));
        if (tempC > -50 && tempC < 100) {
            safeSetText(tvMetricTemp, String.format(Locale.getDefault(), "%.1f°C", tempC));
        } else {
            safeSetText(tvMetricTemp, "--");
        }

        // ── 放电速率分析卡片 ──
        updateDischargeAnalysis(realtimeRate, windowRate, historicalBaseline);

        // ── 续航预估卡片（乐观/正常/悲观） ──
        updateEnduranceForecast(batteryPct, isCharging);

        // ── 详情 ──
        safeSetText(tvChargingStatus, chargingStatus);
        safeSetText(tvUsedTime, formatDuration(SystemClock.elapsedRealtime()));
        safeSetText(tvConsumedBattery, String.format(Locale.getDefault(), "%d%%", 100 - batteryPct));
        safeSetText(tvEstimatedFull, isCharging ? estimateFullChargeTime(batteryPct, currentMa) : "--");
        safeSetText(tvScreenOnTime, formatDuration(SystemClock.uptimeMillis()));

        // ── 手表专属续航分析 ──
        if (isWatch && watchSection != null) {
            updateWatchEndurance(batteryPct, isCharging);
        }

        // ── 应用耗电排行标题可见性 ──
        if (tvAppConsumptionTitle != null) {
            tvAppConsumptionTitle.setVisibility(isWatch ? View.GONE : View.VISIBLE);
        }
        if (cardAppConsumption != null) {
            cardAppConsumption.setVisibility(isWatch ? View.GONE : View.VISIBLE);
        }
    }

    // ─────────────────────────────────────────────────────────
    // 放电速率分析 UI 更新
    // ─────────────────────────────────────────────────────────

    private void updateDischargeAnalysis(float realtimeRate, float windowRate, float historicalBaseline) {
        // 实时放电速率
        if (realtimeRate > 0) {
            safeSetText(tvRealtimeDischarge, String.format(Locale.getDefault(), "%.1f%%/h", realtimeRate));
        } else {
            safeSetText(tvRealtimeDischarge, "--");
        }

        // 15分钟均值
        if (windowRate > 0) {
            safeSetText(tvAvg15minDischarge, String.format(Locale.getDefault(), "%.1f%%/h", windowRate));
        } else {
            safeSetText(tvAvg15minDischarge, "--");
        }

        // 历史基线
        safeSetText(tvHistoricalBaseline, String.format(Locale.getDefault(), "%.1f%%/h", historicalBaseline));

        // 亮屏放电速率
        float screenOnRate = dischargeRateTracker != null ? dischargeRateTracker.calculateScreenOnRate() : -1f;
        if (screenOnRate > 0) {
            safeSetText(tvScreenOnDischarge, String.format(Locale.getDefault(), "%.1f%%/h", screenOnRate));
        } else {
            safeSetText(tvScreenOnDischarge, "--");
        }

        // 息屏放电速率
        float screenOffRate = dischargeRateTracker != null ? dischargeRateTracker.calculateScreenOffRate() : -1f;
        if (screenOffRate > 0) {
            safeSetText(tvScreenOffDischarge, String.format(Locale.getDefault(), "%.1f%%/h", screenOffRate));
        } else {
            safeSetText(tvScreenOffDischarge, "--");
        }
    }

    // ─────────────────────────────────────────────────────────
    // 续航预估增强（乐观/正常/悲观）
    // ─────────────────────────────────────────────────────────

    private void updateEnduranceForecast(int batteryPct, boolean isCharging) {
        if (isCharging) {
            safeSetText(tvForecastOptimistic, "--");
            safeSetText(tvForecastNormal, "--");
            safeSetText(tvForecastPessimistic, "--");
            return;
        }

        float historicalMin = getHistoricalMin();
        float historicalMax = getHistoricalMax();

        // 乐观 = 历史最低放电速率 → 续航最长
        float optimisticHours = batteryPct / Math.max(historicalMin, 0.01f);
        // 正常 = 15分钟滑动窗口均值
        float normalHours = batteryPct / Math.max(dischargeRate, 0.01f);
        // 悲观 = 历史最高放电速率 → 续航最短
        float pessimisticHours = batteryPct / Math.max(historicalMax, 0.01f);

        safeSetText(tvForecastOptimistic, formatHoursMinutes(optimisticHours));
        safeSetText(tvForecastNormal, formatHoursMinutes(normalHours));
        safeSetText(tvForecastPessimistic, formatHoursMinutes(pessimisticHours));
    }

    private String formatHoursMinutes(float totalHours) {
        if (totalHours <= 0 || Float.isInfinite(totalHours) || Float.isNaN(totalHours)) return "--";
        int h = (int) totalHours;
        int m = (int) ((totalHours - h) * 60);
        return String.format(Locale.getDefault(), "%d时%d分", h, m);
    }

    // ─────────────────────────────────────────────────────────
    // 应用耗电排行
    // ─────────────────────────────────────────────────────────

    private void refreshAppConsumption() {
        if (isWatch) return;
        if (!isAdded() || getContext() == null) return;

        boolean hasAccess = BatteryConsumptionAnalyzer.hasUsageAccess(getContext());
        if (layoutUsagePermissionGuide != null) {
            layoutUsagePermissionGuide.setVisibility(hasAccess ? View.GONE : View.VISIBLE);
        }
        if (layoutAppList != null) {
            layoutAppList.setVisibility(hasAccess ? View.VISIBLE : View.GONE);
        }

        if (!hasAccess) return;

        executor.execute(() -> {
            if (!isAdded() || getContext() == null) return;

            try {
                UsageStatsManager usm = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
                if (usm == null) return;

                long endTime = System.currentTimeMillis();
                long startTime = endTime - 24 * 60 * 60 * 1000L; // 最近24小时
                List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);
                if (stats == null || stats.isEmpty()) return;

                // 按前台使用时长排序
                Collections.sort(stats, (a, b) -> Long.compare(
                        b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));

                // 取前5名（排除本应用）
                String myPkg = getContext().getPackageName();
                List<AppUsageInfo> topApps = new ArrayList<>();
                PackageManager pm = getContext().getPackageManager();
                long totalForegroundTime = 0;

                for (UsageStats us : stats) {
                    if (us.getPackageName() == null || us.getPackageName().equals(myPkg)) continue;
                    long fgTime = us.getTotalTimeInForeground();
                    if (fgTime <= 0) continue;
                    totalForegroundTime += fgTime;
                }

                for (UsageStats us : stats) {
                    if (topApps.size() >= 5) break;
                    if (us.getPackageName() == null || us.getPackageName().equals(myPkg)) continue;
                    long fgTime = us.getTotalTimeInForeground();
                    if (fgTime <= 0) continue;

                    String displayName = us.getPackageName();
                    Drawable icon = null;
                    try {
                        ApplicationInfo appInfo = pm.getApplicationInfo(us.getPackageName(), 0);
                        displayName = pm.getApplicationLabel(appInfo).toString();
                        icon = pm.getApplicationIcon(appInfo);
                    } catch (PackageManager.NameNotFoundException ignored) {}

                    float estimatedPercent = totalForegroundTime > 0
                            ? ((float) fgTime / totalForegroundTime) * 100f : 0f;

                    topApps.add(new AppUsageInfo(us.getPackageName(), displayName, icon, fgTime, estimatedPercent));
                }

                if (!isAdded() || getContext() == null) return;
                handler.post(() -> renderAppList(topApps));
            } catch (Exception ignored) {}
        });
    }

    private static class AppUsageInfo {
        final String packageName;
        final String displayName;
        final Drawable icon;
        final long foregroundTimeMs;
        final float estimatedPercent;

        AppUsageInfo(String packageName, String displayName, Drawable icon,
                     long foregroundTimeMs, float estimatedPercent) {
            this.packageName = packageName;
            this.displayName = displayName;
            this.icon = icon;
            this.foregroundTimeMs = foregroundTimeMs;
            this.estimatedPercent = estimatedPercent;
        }
    }

    private void renderAppList(List<AppUsageInfo> apps) {
        if (!isAdded() || getContext() == null) return;
        if (layoutAppList == null) return;
        layoutAppList.removeAllViews();

        if (apps.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText(getString(R.string.no_app_data));
            empty.setTextSize(14);
            empty.setTextColor(getResources().getColor(R.color.label_3, null));
            empty.setPadding(0, 8, 0, 8);
            layoutAppList.addView(empty);
            return;
        }

        for (int i = 0; i < apps.size(); i++) {
            AppUsageInfo app = apps.get(i);
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int padV = (int) (6 * getResources().getDisplayMetrics().density);
            int padH = 0;
            row.setPadding(padH, padV, padH, padV);

            // 序号
            TextView tvIndex = new TextView(getContext());
            tvIndex.setText(String.format(Locale.getDefault(), "%d.", i + 1));
            tvIndex.setTextSize(13);
            tvIndex.setTextColor(getResources().getColor(R.color.label_3, null));
            tvIndex.setMinWidth((int) (24 * getResources().getDisplayMetrics().density));
            row.addView(tvIndex);

            // 图标
            ImageView ivIcon = new ImageView(getContext());
            int iconSize = (int) (28 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
            int iconMarginEnd = (int) (8 * getResources().getDisplayMetrics().density);
            iconLp.setMarginEnd(iconMarginEnd);
            ivIcon.setLayoutParams(iconLp);
            if (app.icon != null) {
                ivIcon.setImageDrawable(app.icon);
            } else {
                ivIcon.setImageResource(R.drawable.ic_battery);
            }
            row.addView(ivIcon);

            // 名称 + 使用时长 + 预估耗电
            LinearLayout infoLayout = new LinearLayout(getContext());
            infoLayout.setOrientation(LinearLayout.VERTICAL);
            infoLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvName = new TextView(getContext());
            tvName.setText(app.displayName);
            tvName.setTextSize(14);
            tvName.setTextColor(getResources().getColor(R.color.label, null));
            tvName.setSingleLine(true);
            infoLayout.addView(tvName);

            TextView tvDetail = new TextView(getContext());
            String usageTime = formatDuration(app.foregroundTimeMs);
            tvDetail.setText(String.format(Locale.getDefault(), "%s  约%.0f%%", usageTime, app.estimatedPercent));
            tvDetail.setTextSize(12);
            tvDetail.setTextColor(getResources().getColor(R.color.label_3, null));
            infoLayout.addView(tvDetail);

            row.addView(infoLayout);

            // 预估耗电占比
            TextView tvPercent = new TextView(getContext());
            tvPercent.setText(String.format(Locale.getDefault(), "%.0f%%", app.estimatedPercent));
            tvPercent.setTextSize(14);
            tvPercent.setTextColor(getResources().getColor(R.color.primary, null));
            tvPercent.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(tvPercent);

            layoutAppList.addView(row);

            // 分隔线（非最后一项）
            if (i < apps.size() - 1) {
                View separator = new View(getContext());
                separator.setBackgroundColor(getResources().getColor(R.color.separator, null));
                LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                sepLp.setMarginStart((int) (32 * getResources().getDisplayMetrics().density));
                separator.setLayoutParams(sepLp);
                layoutAppList.addView(separator);
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────

    private void safeSetText(TextView tv, String text) {
        if (tv != null) tv.setText(text);
    }

    private String estimateFullChargeTime(int batteryPct, float currentMa) {
        if (currentMa <= 0) return getString(R.string.status_calculating);
        int remaining = 100 - batteryPct;
        // 使用设计容量估算充满时间，而非当前剩余容量
        float designCapacityMah = getDesignCapacity();
        float hours = (remaining * designCapacityMah / 100f) / currentMa;
        int h = (int) hours;
        int m = (int) ((hours - h) * 60);
        return String.format(Locale.getDefault(), "%d小时%d分", h, m);
    }

    /**
     * 获取电池设计容量（mAh）。
     * 优先从 BatteryDataManager 获取，失败时回退到系统 API 或默认值。
     */
    private float getDesignCapacity() {
        Context ctx = getContext();
        if (ctx == null) return 4000;
        // 优先使用 BatteryDataManager 的设计容量（含用户校准、机型数据库、sysfs）
        try {
            com.batteryhealth.app.utils.BatteryDataManager bdm =
                    com.batteryhealth.app.utils.BatteryDataManager.getInstance(ctx);
            if (bdm != null) {
                com.batteryhealth.app.data.model.BatteryInfo info = bdm.getLatestBatteryInfo(false);
                if (info != null && info.getDesignCapacity() > 0) {
                    return info.getDesignCapacity();
                }
            }
        } catch (Exception ignored) {
        }
        // 回退：尝试从 BatteryManager 获取（Android 16+）
        BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null && android.os.Build.VERSION.SDK_INT >= 36) {
            try {
                int designMicroAh = bm.getIntProperty(
                        bm.getClass().getField("BATTERY_PROPERTY_CHARGE_FULL_DESIGN").getInt(null));
                if (designMicroAh > 100000 && designMicroAh <= 50000000) return designMicroAh / 1000f;
                if (designMicroAh > 50 && designMicroAh <= 50000) return designMicroAh;
            } catch (Exception ignored) {
            }
        }
        return 4000;
    }

    private void updateWatchEndurance(int batteryPct, boolean isCharging) {
        if (tvWatchEndurance == null || tvWatchMode == null) return;
        Context ctx = getContext();
        if (ctx == null) return;

        String mode = "日常模式";
        float watchDischargeRate = dischargeRate;

        try {
            if (Settings.System.getInt(ctx.getContentResolver(), "low_power", 0) == 1) {
                mode = "省电模式";
                watchDischargeRate *= 0.6f;
            } else if (isWatchAlwaysOn()) {
                mode = "AOD常显模式";
                watchDischargeRate *= 1.3f;
            }
        } catch (Exception ignored) {}

        float hours = isCharging ? 0 : batteryPct / Math.max(watchDischargeRate, 0.01f);
        int h = (int) hours;
        int m = (int) ((hours - h) * 60);

        tvWatchEndurance.setText(String.format(Locale.getDefault(), "%d小时%d分", h, m));
        tvWatchMode.setText(mode);
    }

    private boolean isWatchAlwaysOn() {
        Context ctx = getContext();
        if (ctx == null) return false;
        try {
            return Settings.System.getInt(ctx.getContentResolver(), "screen_always_on") == 1;
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    private String formatDuration(long ms) {
        long hours = ms / (1000 * 60 * 60);
        long minutes = (ms % (1000 * 60 * 60)) / (1000 * 60);
        return String.format(Locale.getDefault(), "%d小时%d分", hours, minutes);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPeriodicUpdate();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
