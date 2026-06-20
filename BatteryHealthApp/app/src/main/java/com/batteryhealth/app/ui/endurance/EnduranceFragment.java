package com.batteryhealth.app.ui.endurance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;

import java.util.Locale;

public class EnduranceFragment extends Fragment {

    private TextView tvEnduranceHours;
    private TextView tvEnduranceMeta;
    private TextView tvMetricBattery, tvMetricDischarge, tvMetricTemp;
    private TextView tvChargingStatus, tvUsedTime, tvConsumedBattery, tvEstimatedFull, tvScreenOnTime;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    private int lastBatteryLevel = -1;
    private long lastUpdateTime = -1;
    private float dischargeRate = 0f;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_endurance, container, false);
        initViews(view);
        animateEntry(view);
        return view;
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
                handler.postDelayed(this, 3000);
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
        String chargingStatus = isCharging ? getString(R.string.status_charging) : getString(R.string.status_discharging);

        int current = 0;
        BatteryManager bm = (BatteryManager) requireContext().getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        }
        float currentMa = current / 1000f;

        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        float tempC = temp / 10f;

        // Calculate discharge rate
        long now = System.currentTimeMillis();
        if (lastBatteryLevel >= 0 && lastUpdateTime > 0 && !isCharging) {
            long elapsedHours = (now - lastUpdateTime) / (1000 * 60 * 60);
            if (elapsedHours > 0) {
                int delta = lastBatteryLevel - batteryPct;
                if (delta > 0) {
                    dischargeRate = delta / (float) elapsedHours;
                }
            }
        }
        if (dischargeRate <= 0) {
            dischargeRate = 12.2f; // default fallback
        }
        lastBatteryLevel = batteryPct;
        lastUpdateTime = now;

        // Endurance estimate
        float remainingHours = batteryPct / dischargeRate;
        int hours = (int) remainingHours;
        int minutes = (int) ((remainingHours - hours) * 60);
        tvEnduranceHours.setText(String.valueOf(hours));
        tvEnduranceMeta.setText(String.format(Locale.getDefault(),
                getString(R.string.meta_endurance), batteryPct, dischargeRate));

        // Quick metrics
        tvMetricBattery.setText(String.format(Locale.getDefault(), "%d%%", batteryPct));
        tvMetricDischarge.setText(String.format(Locale.getDefault(), "%.1f%%/h", dischargeRate));
        tvMetricTemp.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));

        // Details
        tvChargingStatus.setText(chargingStatus);
        tvUsedTime.setText(formatDuration(SystemClock.elapsedRealtime()));
        tvConsumedBattery.setText(String.format(Locale.getDefault(), "%d%%", 100 - batteryPct));
        tvEstimatedFull.setText(isCharging ? getString(R.string.status_calculating) : "--");
        tvScreenOnTime.setText(formatDuration(SystemClock.uptimeMillis()));
    }

    private String formatDuration(long ms) {
        long hours = ms / (1000 * 60 * 60);
        long minutes = (ms % (1000 * 60 * 60)) / (1000 * 60);
        return String.format(Locale.getDefault(), "%d小时%d分", hours, minutes);
    }
}
