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
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.PowerHistoryDao;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.List;
import java.util.Locale;

public class PowerFragment extends Fragment {

    private TextView tvWatt, tvPowerType;
    private ProgressBar progressCharge;
    private TextView tvVoltage, tvCurrent, tvChargeStage, tvTemperature, tvBatteryLevel, tvEstimatedFull;
    private TextView tvChargeCount, tvAvgPower, tvTotalChargeTime, tvTotalCharged;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    private BatteryDataManager batteryDataManager;

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
            updateBatteryData();
        }
    };

    private void updateBatteryData() {
        BatteryDataManager bdm = getBatteryDataManager();
        if (bdm == null) return;

        BatteryInfo info = bdm.getBatteryInfo();
        if (info == null) return;

        int batteryPct = info.getLevel();
        boolean isCharging = info.isCharging();

        // Read real voltage (mV) and current (mA) via BatteryDataManager
        int voltageMv = bdm.readVoltageNow();
        int currentMa = bdm.readCurrentMa();

        // Calculate power correctly: Power(W) = Voltage(V) × Current(A)
        // voltageMv is in mV, currentMa is in mA
        // W = (voltageMv / 1000.0) × (Math.abs(currentMa) / 1000.0)
        float voltageV = voltageMv > 0 ? voltageMv / 1000f : 0f;
        float currentA = Math.abs(currentMa) / 1000f;
        float watt = voltageV * currentA;

        float tempC = info.getTemperature();

        // Update hero
        tvWatt.setText(isCharging && watt > 0
                ? String.format(Locale.getDefault(), "%.1f", watt)
                : "--");
        String powerType = isCharging ? bdm.getPowerLevelLabel(watt) : getString(R.string.status_not_charging);
        tvPowerType.setText(powerType);
        UiAnimationHelper.animateProgressBar(progressCharge, batteryPct);

        // Update details
        tvVoltage.setText(voltageMv > 0
                ? String.format(Locale.getDefault(), "%.2f V", voltageV)
                : "-- V");
        tvCurrent.setText(currentMa != 0
                ? String.format(Locale.getDefault(), "%.0f mA", (float) Math.abs(currentMa))
                : "-- mA");
        tvChargeStage.setText(isCharging
                ? (batteryPct >= 80 ? getString(R.string.stage_trickle) : getString(R.string.stage_fast))
                : "--");
        tvTemperature.setText(tempC >= 0
                ? String.format(Locale.getDefault(), "%.1f°C", tempC)
                : "--°C");
        tvBatteryLevel.setText(batteryPct >= 0
                ? String.format(Locale.getDefault(), "%d%%", batteryPct)
                : "--%");
        tvEstimatedFull.setText(isCharging && currentA > 0
                ? calculateTimeToFull(batteryPct, currentA, voltageV)
                : "--");
    }

    /**
     * Load real charging history from the database on a background thread.
     */
    private void loadChargingHistory() {
        new Thread(() -> {
            try {
                BatteryHealthApplication app = (BatteryHealthApplication) requireActivity().getApplication();
                if (app == null) return;
                PowerHistoryDao dao = app.getDatabase().powerHistoryDao();
                if (dao == null) return;

                // Get today's start timestamp
                long todayStart = getTodayStartTime();

                // Query today's charging sessions
                List<PowerHistory> todayRecords = dao.getSince(todayStart);
                List<String> allSessions = dao.getAllSessions();

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
                    if (record.getTimestamp() < earliestTs) earliestTs = record.getTimestamp();
                    if (record.getTimestamp() > latestTs) latestTs = record.getTimestamp();
                    if (record.getBatteryLevel() < lowestLevel) lowestLevel = record.getBatteryLevel();
                    if (record.getBatteryLevel() > highestLevel) highestLevel = record.getBatteryLevel();
                }

                float avgPower = powerCount > 0 ? totalPower / powerCount : 0f;
                long totalChargeMs = (latestTs > earliestTs) ? (latestTs - earliestTs) : 0;
                int totalChargeMin = (int) (totalChargeMs / 60000);
                int totalChargedPct = (highestLevel > lowestLevel && lowestLevel < Integer.MAX_VALUE)
                        ? (highestLevel - lowestLevel) : 0;

                // Update UI on main thread
                float finalAvgPower = avgPower;
                int finalChargeSessionCount = chargeSessionCount;
                int finalTotalChargeMin = totalChargeMin;
                int finalTotalChargedPct = totalChargedPct;
                handler.post(() -> {
                    tvChargeCount.setText(finalChargeSessionCount > 0
                            ? String.format(Locale.getDefault(), "%d", finalChargeSessionCount)
                            : "--");
                    tvAvgPower.setText(finalAvgPower > 0
                            ? String.format(Locale.getDefault(), "%.1f W", finalAvgPower)
                            : "-- W");
                    tvTotalChargeTime.setText(finalTotalChargeMin > 0
                            ? String.format(Locale.getDefault(), "%d分", finalTotalChargeMin)
                            : "--");
                    tvTotalCharged.setText(finalTotalChargedPct > 0
                            ? String.format(Locale.getDefault(), "%d%%", finalTotalChargedPct)
                            : "--");
                });
            } catch (Exception e) {
                // Database not available, show placeholders
                handler.post(() -> {
                    tvChargeCount.setText("--");
                    tvAvgPower.setText("-- W");
                    tvTotalChargeTime.setText("--");
                    tvTotalCharged.setText("--");
                });
            }
        }).start();
    }

    private long getTodayStartTime() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
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
        float remainingMah = designCapacityMah * (remaining / 100f);
        float currentMa = currentA * 1000f;
        if (currentMa <= 0) return "--";

        float hours = remainingMah / currentMa;
        int mins = Math.max(1, (int) (hours * 60));
        return String.format(Locale.getDefault(), "%d分", mins);
    }
}
