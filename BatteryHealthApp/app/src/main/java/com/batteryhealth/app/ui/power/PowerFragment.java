package com.batteryhealth.app.ui.power;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PowerFragment extends Fragment {

    private TextView tvWatt, tvPowerType;
    private ProgressBar progressCharge;
    private TextView tvVoltage, tvCurrent, tvChargeStage, tvTemperature, tvBatteryLevel, tvEstimatedFull;
    private TextView tvChargeCount, tvAvgPower, tvTotalChargeTime, tvTotalCharged;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private long lastTodayStatsLoad = 0;
    private static final long TODAY_STATS_REFRESH_INTERVAL = 30_000L; // 30 秒刷新一次今日统计

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_power, container, false);
        initViews(view);
        animateEntry(view);
        return view;
    }

    private void initViews(View view) {
        tvWatt = view.findViewById(R.id.tv_watt);
        tvPowerType = view.findViewById(R.id.tv_power_type);
        progressCharge = view.findViewById(R.id.progress_charge);
        tvVoltage = view.findViewById(R.id.tv_voltage);
        tvCurrent = view.findViewById(R.id.tv_current);
        tvChargeStage = view.findViewById(R.id.tv_charge_stage);
        tvTemperature = view.findViewById(R.id.tv_temperature);
        tvBatteryLevel = view.findViewById(R.id.tv_battery_level);
        tvEstimatedFull = view.findViewById(R.id.tv_estimated_full);
        tvChargeCount = view.findViewById(R.id.tv_charge_count);
        tvAvgPower = view.findViewById(R.id.tv_avg_power);
        tvTotalChargeTime = view.findViewById(R.id.tv_total_charge_time);
        tvTotalCharged = view.findViewById(R.id.tv_total_charged);
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerBatteryReceiver();
        startPeriodicUpdate();
        // 进入页面立即拉取一次今日统计
        lastTodayStatsLoad = 0;
        loadTodayChargeStats();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterBatteryReceiver();
        stopPeriodicUpdate();
    }

    private void registerBatteryReceiver() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        requireContext().registerReceiver(batteryReceiver, filter);
    }

    private void unregisterBatteryReceiver() {
        try {
            requireContext().unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void startPeriodicUpdate() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateBatteryData();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updateRunnable);
    }

    private void stopPeriodicUpdate() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbExecutor != null) {
            dbExecutor.shutdown();
            try {
                if (!dbExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    dbExecutor.shutdownNow();
                }
            } catch (InterruptedException ie) {
                dbExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateFromIntent(intent);
        }
    };

    private void updateBatteryData() {
        Intent intent = requireContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent != null) {
            updateFromIntent(intent);
        }
    }

    private void updateFromIntent(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPct = (int) ((level / (float) scale) * 100);

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;

        int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        float voltageV = voltage / 1000f;

        int current = 0;
        BatteryManager bm = (BatteryManager) requireContext().getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        }
        float currentA = Math.abs(current) / 1000000f;

        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        float tempC = temp / 10f;

        float watt = voltageV * currentA;

        // Update hero
        tvWatt.setText(String.format(Locale.getDefault(), "%.1f", watt));
        String powerType = watt > 20 ? getString(R.string.status_super_fast_charge)
                : watt > 10 ? getString(R.string.status_fast_charge)
                : isCharging ? getString(R.string.status_normal_charge) : getString(R.string.status_not_charging);
        tvPowerType.setText(powerType);
        UiAnimationHelper.animateProgressBar(progressCharge, batteryPct);

        // Details
        tvVoltage.setText(String.format(Locale.getDefault(), "%.2f V", voltageV));
        tvCurrent.setText(String.format(Locale.getDefault(), "%.0f mA", Math.abs(current) / 1000f));
        tvChargeStage.setText(batteryPct >= 80 ? getString(R.string.stage_trickle) : getString(R.string.stage_fast));
        tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));
        tvBatteryLevel.setText(String.format(Locale.getDefault(), "%d%%", batteryPct));
        tvEstimatedFull.setText(isCharging ? calculateTimeToFull(batteryPct, currentA) : "--");

        // 今日充电统计：异步从数据库读取真实数据，每 30 秒刷新一次
        long now = System.currentTimeMillis();
        if (now - lastTodayStatsLoad >= TODAY_STATS_REFRESH_INTERVAL) {
            lastTodayStatsLoad = now;
            loadTodayChargeStats();
        }
    }

    /**
     * 异步从 power_history 表中加载今日（00:00 起）充电统计数据：
     *  - 充电次数：去重 session_id 数量
     *  - 平均功率：所有样本功率的均值
     *  - 累计时长：去重会话总持续时间（最后一次 - 第一次样本）
     *  - 累计充入：基于平均电流 × 时长换算到百分比
     */
    private void loadTodayChargeStats() {
        if (!isAdded()) return;
        final Context appCtx = requireContext().getApplicationContext();
        dbExecutor.submit(() -> {
            try {
                BatteryHealthApplication app = (BatteryHealthApplication) appCtx;
                AppDatabase db = app.getDatabase();
                if (db == null) {
                    renderTodayStatsEmpty();
                    return;
                }

                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                long todayStart = cal.getTimeInMillis();

                List<PowerHistory> records = db.powerHistoryDao().getSince(todayStart);
                if (records == null || records.isEmpty()) {
                    renderTodayStatsEmpty();
                    return;
                }

                // 1. 充电次数：按 session_id 去重
                Set<String> sessions = new HashSet<>();
                float totalPower = 0f;
                int count = 0;
                long earliestTs = Long.MAX_VALUE;
                long latestTs = Long.MIN_VALUE;
                int minLevel = Integer.MAX_VALUE;
                int maxLevel = Integer.MIN_VALUE;
                for (PowerHistory h : records) {
                    if (h.getSessionId() != null) sessions.add(h.getSessionId());
                    if (h.getPower() > 0) {
                        totalPower += h.getPower();
                        count++;
                    }
                    if (h.getTimestamp() < earliestTs) earliestTs = h.getTimestamp();
                    if (h.getTimestamp() > latestTs) latestTs = h.getTimestamp();
                    if (h.getBatteryLevel() < minLevel) minLevel = h.getBatteryLevel();
                    if (h.getBatteryLevel() > maxLevel) maxLevel = h.getBatteryLevel();
                }
                int chargeCount = sessions.size();
                float avgPower = count > 0 ? totalPower / count : 0f;
                long totalDurationMs = (sessions.isEmpty() || latestTs < earliestTs)
                        ? 0 : latestTs - earliestTs;
                int charged = (minLevel != Integer.MAX_VALUE && maxLevel != Integer.MIN_VALUE)
                        ? Math.max(0, maxLevel - minLevel) : 0;

                final int finalCount = chargeCount;
                final float finalAvgPower = avgPower;
                final long finalDuration = totalDurationMs;
                final int finalCharged = charged;
                handler.post(() -> {
                    if (!isAdded()) return;
                    tvChargeCount.setText(String.format(Locale.getDefault(), "%d 次", finalCount));
                    tvAvgPower.setText(String.format(Locale.getDefault(), "%.1f W", finalAvgPower));
                    tvTotalChargeTime.setText(formatDuration(finalDuration));
                    tvTotalCharged.setText(String.format(Locale.getDefault(), "%d%%", finalCharged));
                });
            } catch (Throwable t) {
                renderTodayStatsEmpty();
            }
        });
    }

    private void renderTodayStatsEmpty() {
        if (!isAdded()) return;
        handler.post(() -> {
            if (!isAdded()) return;
            tvChargeCount.setText("0 次");
            tvAvgPower.setText("--");
            tvTotalChargeTime.setText("--");
            tvTotalCharged.setText("--");
        });
    }

    private String formatDuration(long ms) {
        if (ms <= 0) return "--";
        long minutes = ms / (1000 * 60);
        if (minutes < 60) {
            return String.format(Locale.getDefault(), "%d 分", minutes);
        }
        long hours = minutes / 60;
        long remMins = minutes % 60;
        return String.format(Locale.getDefault(), "%d 小时 %d 分", hours, remMins);
    }

    private String calculateTimeToFull(int batteryPct, float currentA) {
        if (currentA <= 0) return "--";
        int remaining = 100 - batteryPct;
        float hours = remaining / (currentA * 100 / 3f); // rough estimate
        int mins = (int) (hours * 60);
        return String.format(Locale.getDefault(), "%d分", mins);
    }
}
