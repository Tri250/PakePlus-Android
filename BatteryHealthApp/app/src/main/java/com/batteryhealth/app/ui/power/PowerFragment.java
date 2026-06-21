package com.batteryhealth.app.ui.power;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.PowerHistoryDao;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PowerFragment extends Fragment {

    private static final String TAG = "PowerFragment";

    private TextView tvWatt, tvPowerType;
    private ProgressBar progressCharge;
    private TextView tvVoltage, tvCurrent, tvChargeStage, tvTemperature, tvBatteryLevel, tvEstimatedFull;
    private TextView tvChargeCount, tvAvgPower, tvTotalChargeTime, tvTotalCharged;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    private BatteryDataManager batteryDataManager;
    private ExecutorService historyExecutor;
    private final AtomicBoolean historyLoading = new AtomicBoolean(false);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_power, container, false);
        try {
            initViews(view);
            animateEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "onCreateView failed: " + e.getMessage(), e);
        }
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
        try {
            Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
            view.startAnimation(fadeUp);
        } catch (Exception e) {
            Log.e(TAG, "animateEntry failed: " + e.getMessage());
        }
    }

    private BatteryDataManager getBatteryDataManager() {
        if (batteryDataManager != null) return batteryDataManager;
        if (getActivity() instanceof MainActivity) {
            batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
        }
        return batteryDataManager;
    }

    @Override
    public void onResume() {
        super.onResume();
        registerBatteryReceiver();
        startPeriodicUpdate();
        loadChargingHistory();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterBatteryReceiver();
        stopPeriodicUpdate();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPeriodicUpdate();
        // Clean up handler to prevent leaks
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        // Shut down the shared executor; will be re-created on next use
        if (historyExecutor != null) {
            historyExecutor.shutdown();
            historyExecutor = null;
        }
        tvWatt = null;
        tvPowerType = null;
        progressCharge = null;
        tvVoltage = null;
        tvCurrent = null;
        tvChargeStage = null;
        tvTemperature = null;
        tvBatteryLevel = null;
        tvEstimatedFull = null;
        tvChargeCount = null;
        tvAvgPower = null;
        tvTotalChargeTime = null;
        tvTotalCharged = null;
    }

    private void registerBatteryReceiver() {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                requireContext().registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                requireContext().registerReceiver(batteryReceiver, filter);
            }
        } catch (Exception e) {
            Log.e(TAG, "registerBatteryReceiver failed: " + e.getMessage());
        }
    }

    private void unregisterBatteryReceiver() {
        try {
            if (getContext() != null) {
                getContext().unregisterReceiver(batteryReceiver);
            }
        } catch (IllegalArgumentException ignored) {
        } catch (Exception e) {
            Log.e(TAG, "unregisterBatteryReceiver failed: " + e.getMessage());
        }
    }

    private void startPeriodicUpdate() {
        stopPeriodicUpdate();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) return;
                updateBatteryData();
                if (isAdded()) {
                    handler.postDelayed(this, 2000);
                }
            }
        };
        handler.post(updateRunnable);
    }

    private void stopPeriodicUpdate() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
            updateRunnable = null;
        }
    }

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isAdded()) {
                updateBatteryData();
            }
        }
    };

    private void updateBatteryData() {
        if (!isAdded() || getContext() == null) return;

        BatteryDataManager bdm = getBatteryDataManager();
        if (bdm == null) return;

        try {
            BatteryInfo info = bdm.getBatteryInfo();
            if (info == null) return;

            int batteryPct = info.getLevel();
            boolean isCharging = info.isCharging();

            // Read real voltage (mV) and current (mA) via BatteryDataManager
            int voltageMv = bdm.readVoltageNow();
            int currentMa = bdm.readCurrentMa();

            // Calculate power correctly: Power(W) = Voltage(V) × Current(A)
            float voltageV = voltageMv > 0 ? voltageMv / 1000f : 0f;
            float currentA = Math.abs(currentMa) / 1000f;
            float watt = voltageV * currentA;
            if (watt < 0) watt = 0;

            float tempC = info.getTemperature();

            // Update hero
            if (tvWatt != null) {
                tvWatt.setText(isCharging && watt > 0
                        ? String.format(Locale.getDefault(), "%.1f", watt)
                        : "--");
            }
            if (tvPowerType != null) {
                String powerType = isCharging ? bdm.getPowerLevelLabel(watt) : getString(R.string.status_not_charging);
                tvPowerType.setText(powerType);
            }
            if (progressCharge != null) {
                UiAnimationHelper.animateProgressBar(progressCharge, batteryPct);
            }

            // Update details
            if (tvVoltage != null) {
                tvVoltage.setText(voltageMv > 0
                        ? String.format(Locale.getDefault(), "%.2f V", voltageV)
                        : "-- V");
            }
            if (tvCurrent != null) {
                tvCurrent.setText(currentMa != 0
                        ? String.format(Locale.getDefault(), "%.0f mA", (float) Math.abs(currentMa))
                        : "-- mA");
            }
            if (tvChargeStage != null) {
                tvChargeStage.setText(isCharging
                        ? (batteryPct >= 80 ? getString(R.string.stage_trickle) : getString(R.string.stage_fast))
                        : "--");
            }
            if (tvTemperature != null) {
                tvTemperature.setText(tempC >= 0
                        ? String.format(Locale.getDefault(), "%.1f°C", tempC)
                        : "--°C");
            }
            if (tvBatteryLevel != null) {
                tvBatteryLevel.setText(batteryPct >= 0
                        ? String.format(Locale.getDefault(), "%d%%", batteryPct)
                        : "--%");
            }
            if (tvEstimatedFull != null) {
                tvEstimatedFull.setText(isCharging && currentA > 0
                        ? calculateTimeToFull(batteryPct, currentA, voltageV)
                        : "--");
            }
        } catch (Exception e) {
            Log.e(TAG, "updateBatteryData failed: " + e.getMessage(), e);
        }
    }

    /**
     * Load real charging history from the database on a background thread.
     * Uses a shared ExecutorService to avoid creating a new thread on every onResume.
     */
    private void loadChargingHistory() {
        // Prevent overlapping loads
        if (!historyLoading.compareAndSet(false, true)) {
            return;
        }
        if (historyExecutor == null || historyExecutor.isShutdown()) {
            historyExecutor = Executors.newSingleThreadExecutor();
        }
        historyExecutor.submit(() -> {
            try {
                Context ctx = getContext();
                if (ctx == null) return;
                BatteryHealthApplication app = (BatteryHealthApplication) ctx.getApplicationContext();
                if (app == null) return;
                PowerHistoryDao dao = app.getDatabase().powerHistoryDao();
                if (dao == null) return;

                // Get today's start timestamp
                long todayStart = getTodayStartTime();

                // Query today's charging sessions
                List<PowerHistory> todayRecords = dao.getSince(todayStart);

                // Calculate stats from real data
                int chargeSessionCount = 0;
                float totalPower = 0f;
                int powerCount = 0;
                long earliestTs = Long.MAX_VALUE;
                long latestTs = 0;
                int lowestLevel = Integer.MAX_VALUE;
                int highestLevel = Integer.MIN_VALUE;

                // Count distinct sessions from today's records
                String lastSessionId = null;
                if (todayRecords != null) {
                    for (PowerHistory record : todayRecords) {
                        String sid = record.getSessionId();
                        if (sid != null && !sid.equals(lastSessionId)) {
                            chargeSessionCount++;
                            lastSessionId = sid;
                        }
                        if (record.getPower() > 0) {
                            totalPower += record.getPower();
                            powerCount++;
                        }
                        long ts = record.getTimestamp();
                        if (ts < earliestTs) earliestTs = ts;
                        if (ts > latestTs) latestTs = ts;
                        int level = record.getBatteryLevel();
                        if (level < lowestLevel) lowestLevel = level;
                        if (level > highestLevel) highestLevel = level;
                    }
                }

                float avgPower = powerCount > 0 ? totalPower / powerCount : 0f;
                long totalChargeMs = (latestTs > earliestTs && earliestTs < Long.MAX_VALUE)
                        ? (latestTs - earliestTs) : 0;
                int totalChargeMin = (int) (totalChargeMs / 60000);
                int totalChargedPct = (highestLevel > lowestLevel && lowestLevel < Integer.MAX_VALUE)
                        ? (highestLevel - lowestLevel) : 0;

                final float finalAvgPower = avgPower;
                final int finalChargeSessionCount = chargeSessionCount;
                final int finalTotalChargeMin = totalChargeMin;
                final int finalTotalChargedPct = totalChargedPct;
                if (handler != null) {
                    handler.post(() -> {
                        try {
                            if (!isAdded()) return;
                            if (tvChargeCount != null) {
                                tvChargeCount.setText(finalChargeSessionCount > 0
                                        ? String.format(Locale.getDefault(), "%d", finalChargeSessionCount)
                                        : "--");
                            }
                            if (tvAvgPower != null) {
                                tvAvgPower.setText(finalAvgPower > 0
                                        ? String.format(Locale.getDefault(), "%.1f W", finalAvgPower)
                                        : "-- W");
                            }
                            if (tvTotalChargeTime != null) {
                                tvTotalChargeTime.setText(finalTotalChargeMin > 0
                                        ? String.format(Locale.getDefault(), "%d分", finalTotalChargeMin)
                                        : "--");
                            }
                            if (tvTotalCharged != null) {
                                tvTotalCharged.setText(finalTotalChargedPct > 0
                                        ? String.format(Locale.getDefault(), "%d%%", finalTotalChargedPct)
                                        : "--");
                            }
                        } finally {
                            historyLoading.set(false);
                        }
                    });
                } else {
                    historyLoading.set(false);
                }
            } catch (Exception e) {
                Log.e(TAG, "loadChargingHistory failed: " + e.getMessage(), e);
                if (handler != null) {
                    handler.post(() -> {
                        try {
                            if (!isAdded()) return;
                            if (tvChargeCount != null) tvChargeCount.setText("--");
                            if (tvAvgPower != null) tvAvgPower.setText("-- W");
                            if (tvTotalChargeTime != null) tvTotalChargeTime.setText("--");
                            if (tvTotalCharged != null) tvTotalCharged.setText("--");
                        } finally {
                            historyLoading.set(false);
                        }
                    });
                } else {
                    historyLoading.set(false);
                }
            }
        });
    }

    private long getTodayStartTime() {
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            Log.e(TAG, "getTodayStartTime failed: " + e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * Estimate time to full charge.
     * Uses: remaining capacity (mAh) / charging current (mA) = hours
     * Remaining capacity estimated from battery level and design capacity.
     */
    private String calculateTimeToFull(int batteryPct, float currentA, float voltageV) {
        if (currentA <= 0) return "--";
        BatteryDataManager bdm = getBatteryDataManager();
        if (bdm == null) return "--";

        // Get design capacity from BatteryInfo for a reasonable estimate
        BatteryInfo info = bdm.getCurrentBatteryInfo();
        int designCapacityMah = info != null ? info.getDesignCapacity() : -1;
        if (designCapacityMah <= 0) designCapacityMah = 4000; // fallback typical capacity

        int remaining = 100 - batteryPct;
        if (remaining < 0) remaining = 0;
        float remainingMah = designCapacityMah * (remaining / 100f);
        float currentMa = currentA * 1000f;
        if (currentMa <= 0) return "--";

        float hours = remainingMah / currentMa;
        if (hours < 0) hours = 0;
        int mins = Math.max(1, (int) (hours * 60));
        return String.format(Locale.getDefault(), "%d分", mins);
    }
}
