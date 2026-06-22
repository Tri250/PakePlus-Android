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
import com.batteryhealth.app.data.database.PowerHistoryDao;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;

public class PowerFragment extends Fragment {

    private TextView tvWatt, tvPowerType;
    private ProgressBar progressCharge;
    private TextView tvVoltage, tvCurrent, tvChargeStage, tvTemperature, tvBatteryLevel, tvEstimatedFull;
    private TextView tvChargeCount, tvAvgPower, tvTotalChargeTime, tvTotalCharged;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

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

        // Update details
        tvVoltage.setText(String.format(Locale.getDefault(), "%.2f V", voltageV));
        tvCurrent.setText(String.format(Locale.getDefault(), "%.0f mA", Math.abs(current) / 1000f));
        tvChargeStage.setText(batteryPct >= 80 ? getString(R.string.stage_trickle) : getString(R.string.stage_fast));
        tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));
        tvBatteryLevel.setText(String.format(Locale.getDefault(), "%d%%", batteryPct));
        tvEstimatedFull.setText(isCharging ? calculateTimeToFull(batteryPct, currentA) : "--");

        // 今日充电统计：从数据库读取真实历史数据
        loadTodayStats();
    }

    /**
     * 异步加载今日充电统计（充电次数、平均功率、累计时长、累计充入电量）。
     */
    private void loadTodayStats() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = BatteryHealthApplication.getInstance() != null
                        ? BatteryHealthApplication.getInstance().getDatabase() : null;
                if (db == null) return;
                PowerHistoryDao dao = db.powerHistoryDao();

                long[] todayRange = getTodayRange();
                List<PowerHistory> todayList = dao.getBetween(todayRange[0], todayRange[1]);

                int sessionCount = 0;
                float totalPower = 0f;
                long totalDurationMs = 0;
                int totalChargedPercent = 0;
                String lastSessionId = null;
                long sessionStartMs = 0;
                int sessionStartLevel = -1;

                for (PowerHistory h : todayList) {
                    totalPower += h.getPower();
                    if (lastSessionId == null || !lastSessionId.equals(h.getSessionId())) {
                        if (lastSessionId != null) {
                            totalDurationMs += Math.max(0, h.getTimestamp() - sessionStartMs);
                            totalChargedPercent += Math.max(0, h.getBatteryLevel() - sessionStartLevel);
                        }
                        lastSessionId = h.getSessionId();
                        sessionStartMs = h.getTimestamp();
                        sessionStartLevel = h.getBatteryLevel();
                        sessionCount++;
                    }
                }
                // 结束最后一个会话估算
                if (lastSessionId != null && !todayList.isEmpty()) {
                    PowerHistory last = todayList.get(todayList.size() - 1);
                    totalDurationMs += Math.max(0, last.getTimestamp() - sessionStartMs);
                    totalChargedPercent += Math.max(0, last.getBatteryLevel() - sessionStartLevel);
                }

                float avgPower = todayList.isEmpty() ? 0f : totalPower / todayList.size();
                long totalMinutes = totalDurationMs / 60000L;

                final int finalSessionCount = Math.max(0, sessionCount);
                final float finalAvgPower = avgPower;
                final long finalTotalMinutes = totalMinutes;
                final int finalTotalCharged = Math.max(0, totalChargedPercent);

                handler.post(() -> {
                    tvChargeCount.setText(String.format(Locale.getDefault(), "%d", finalSessionCount));
                    tvAvgPower.setText(String.format(Locale.getDefault(), "%.1f W", finalAvgPower));
                    tvTotalChargeTime.setText(String.format(Locale.getDefault(), "%d分", finalTotalMinutes));
                    tvTotalCharged.setText(String.format(Locale.getDefault(), "%d%%", finalTotalCharged));
                });
            } catch (Exception e) {
                // 数据库不可用时不阻塞 UI，保持默认显示
            }
        });
    }

    private long[] getTodayRange() {
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        long end = cal.getTimeInMillis();
        return new long[]{start, end};
    }

    private String calculateTimeToFull(int batteryPct, float currentA) {
        if (currentA <= 0) return "--";
        int remaining = 100 - batteryPct;
        float hours = remaining / (currentA * 100 / 3f); // rough estimate
        int mins = (int) (hours * 60);
        return String.format(Locale.getDefault(), "%d分", mins);
    }
}
