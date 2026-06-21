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

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.database.BatteryInfoDao;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.BatteryDataManager;

import java.util.List;
import java.util.Locale;

public class EnduranceFragment extends Fragment {

    private TextView tvEnduranceHours;
    private TextView tvEnduranceMeta;
    private TextView tvMetricBattery, tvMetricDischarge, tvMetricTemp;
    private TextView tvChargingStatus, tvUsedTime, tvConsumedBattery, tvEstimatedFull, tvScreenOnTime;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    private BatteryDataManager batteryDataManager;

    // Discharge rate tracking
    private int lastBatteryLevel = -1;
    private long lastUpdateTime = -1;
    private float dischargeRate = 0f;
    private boolean hasRealDischargeRate = false;

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
        int batteryPct = (scale > 0) ? (int) ((level / (float) scale) * 100) : -1;

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        String chargingStatus = isCharging ? getString(R.string.status_charging) : getString(R.string.status_discharging);

        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        float tempC = temp / 10f;

        // Calculate discharge rate from real data
        long now = System.currentTimeMillis();
        if (!isCharging && batteryPct >= 0) {
            if (lastBatteryLevel >= 0 && lastUpdateTime > 0 && lastBatteryLevel != batteryPct) {
                long elapsedMs = now - lastUpdateTime;
                float elapsedHours = elapsedMs / (1000f * 60 * 60);
                if (elapsedHours > 0.01f) { // at least ~36 seconds
                    int delta = lastBatteryLevel - batteryPct;
                    if (delta > 0) {
                        float instantRate = delta / elapsedHours;
                        // Smooth the rate using exponential moving average
                        if (hasRealDischargeRate) {
                            dischargeRate = dischargeRate * 0.7f + instantRate * 0.3f;
                        } else {
                            dischargeRate = instantRate;
                            hasRealDischargeRate = true;
                        }
                    }
                }
            }
        }

        // If we don't have a real discharge rate yet, try to estimate from historical data
        if (!hasRealDischargeRate) {
            dischargeRate = estimateDischargeRateFromHistory(batteryPct, isCharging);
        }

        lastBatteryLevel = batteryPct;
        lastUpdateTime = now;

        // Endurance estimate
        float remainingHours = 0f;
        if (dischargeRate > 0 && batteryPct > 0 && !isCharging) {
            remainingHours = batteryPct / dischargeRate;
        }
        int hours = (int) remainingHours;
        int minutes = (int) ((remainingHours - hours) * 60);

        if (isCharging) {
            tvEnduranceHours.setText("--");
        } else if (hasRealDischargeRate || dischargeRate > 0) {
            tvEnduranceHours.setText(String.valueOf(hours));
        } else {
            tvEnduranceHours.setText("--");
        }

        tvEnduranceMeta.setText(String.format(Locale.getDefault(),
                getString(R.string.meta_endurance), batteryPct, dischargeRate));

        // Quick metrics
        tvMetricBattery.setText(String.format(Locale.getDefault(), "%d%%", batteryPct));
        if (hasRealDischargeRate) {
            tvMetricDischarge.setText(String.format(Locale.getDefault(), "%.1f%%/h", dischargeRate));
        } else {
            tvMetricDischarge.setText(String.format(Locale.getDefault(), "~%.1f%%/h", dischargeRate));
        }
        tvMetricTemp.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));

        // Details
        tvChargingStatus.setText(chargingStatus);
        tvUsedTime.setText(formatDuration(SystemClock.elapsedRealtime()));
        tvConsumedBattery.setText(String.format(Locale.getDefault(), "%d%%", 100 - batteryPct));

        // Estimated time to full charge
        if (isCharging) {
            tvEstimatedFull.setText(estimateChargeTimeRemaining(batteryPct));
        } else {
            tvEstimatedFull.setText("--");
        }

        // Screen-on time estimate based on real discharge rate
        tvScreenOnTime.setText(formatDuration(SystemClock.uptimeMillis()));
    }

    /**
     * Estimate discharge rate from historical database records.
     * Looks at recent discharge sessions to compute an average rate.
     */
    private float estimateDischargeRateFromHistory(int currentLevel, boolean isCharging) {
        if (isCharging) return 0f;
        try {
            BatteryHealthApplication app = (BatteryHealthApplication) requireContext().getApplicationContext();
            if (app == null) return 0f;
            AppDatabase db = app.getDatabase();
            if (db == null) return 0f;
            BatteryInfoDao dao = db.batteryInfoDao();

            // Look at last 24 hours of data
            long since = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
            List<BatteryInfo> records = dao.getSince(since);
            if (records == null || records.size() < 2) return 0f;

            // Find discharge segments (not charging, level decreasing)
            float totalRate = 0f;
            int rateCount = 0;
            BatteryInfo prev = null;

            for (BatteryInfo info : records) {
                if (prev != null && !info.isCharging() && !prev.isCharging()) {
                    int levelDelta = prev.getLevel() - info.getLevel();
                    long timeDeltaMs = info.getTimestamp() - prev.getTimestamp();
                    float timeDeltaHours = timeDeltaMs / (1000f * 60 * 60);
                    if (levelDelta > 0 && timeDeltaHours > 0.01f) {
                        totalRate += levelDelta / timeDeltaHours;
                        rateCount++;
                    }
                }
                prev = info;
            }

            if (rateCount > 0) {
                return totalRate / rateCount;
            }
        } catch (Exception ignored) {
        }
        return 0f;
    }

    /**
     * Estimate remaining charge time based on current charging power and battery level.
     */
    private String estimateChargeTimeRemaining(int currentLevel) {
        BatteryDataManager bdm = getBatteryDataManager();
        if (bdm == null) return getString(R.string.status_calculating);

        try {
            BatteryInfo info = bdm.getCurrentBatteryInfo();
            if (info == null) return getString(R.string.status_calculating);

            float chargingPower = info.getChargingPower();
            int designCapacity = info.getDesignCapacity();
            int currentCapacity = info.getCurrentCapacity();

            // Use current capacity if available, otherwise estimate from design capacity and health
            int effectiveCapacity = currentCapacity > 0 ? currentCapacity : designCapacity;
            if (effectiveCapacity <= 0) return getString(R.string.status_calculating);

            if (chargingPower > 0) {
                int remainingMah = (int) (effectiveCapacity * (100 - currentLevel) / 100f);
                // Charging is not 100% efficient; assume ~80% efficiency
                float hoursNeeded = (remainingMah / (chargingPower * 1000f)) / 0.8f;
                int h = (int) hoursNeeded;
                int m = (int) ((hoursNeeded - h) * 60);
                if (h > 0) {
                    return String.format(Locale.getDefault(), "%d小时%d分", h, m);
                } else {
                    return String.format(Locale.getDefault(), "%d分", m);
                }
            }
        } catch (Exception ignored) {
        }
        return getString(R.string.status_calculating);
    }

    private String formatDuration(long ms) {
        long hours = ms / (1000 * 60 * 60);
        long minutes = (ms % (1000 * 60 * 60)) / (1000 * 60);
        return String.format(Locale.getDefault(), "%d小时%d分", hours, minutes);
    }
}
