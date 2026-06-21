package com.batteryhealth.app.ui.endurance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class EnduranceFragment extends Fragment {

    private static final String TAG = "EnduranceFragment";

    private TextView tvEnduranceHours;
    private TextView tvEnduranceMeta;
    private TextView tvMetricBattery, tvMetricDischarge, tvMetricTemp;
    private TextView tvChargingStatus, tvUsedTime, tvConsumedBattery, tvEstimatedFull, tvScreenOnTime;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private ExecutorService dbExecutor;

    private BatteryDataManager batteryDataManager;

    // Discharge rate tracking - guarded by rateLock for thread safety
    private final Object rateLock = new Object();
    private int lastBatteryLevel = -1;
    private long lastUpdateTime = -1;
    private float dischargeRate = 0f;
    private final AtomicBoolean hasRealDischargeRate = new AtomicBoolean(false);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_endurance, container, false);
        try {
            initViews(view);
            animateEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "onCreateView failed: " + e.getMessage(), e);
        }
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
        if (dbExecutor != null) {
            dbExecutor.shutdown();
            dbExecutor = null;
        }
        tvEnduranceHours = null;
        tvEnduranceMeta = null;
        tvMetricBattery = null;
        tvMetricDischarge = null;
        tvMetricTemp = null;
        tvChargingStatus = null;
        tvUsedTime = null;
        tvConsumedBattery = null;
        tvEstimatedFull = null;
        tvScreenOnTime = null;
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
                    handler.postDelayed(this, 3000);
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
                updateFromIntent(intent);
            }
        }
    };

    private void updateBatteryData() {
        Context ctx = getContext();
        if (ctx == null) return;
        try {
            Intent intent = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent != null) {
                updateFromIntent(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "updateBatteryData failed: " + e.getMessage());
        }
    }

    private void updateFromIntent(Intent intent) {
        if (!isAdded() || getContext() == null || intent == null) return;

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPct = (scale > 0) ? (int) ((level / (float) scale) * 100) : -1;

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        String chargingStatus = isCharging ? getString(R.string.status_charging) : getString(R.string.status_discharging);

        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        float tempC = temp / 10f;

        // Calculate discharge rate from real data (synchronized for cross-thread safety)
        long now = System.currentTimeMillis();
        if (!isCharging && batteryPct >= 0) {
            synchronized (rateLock) {
                if (lastBatteryLevel >= 0 && lastUpdateTime > 0 && lastBatteryLevel != batteryPct) {
                    long elapsedMs = now - lastUpdateTime;
                    float elapsedHours = elapsedMs / (1000f * 60 * 60);
                    if (elapsedHours > 0.01f) { // at least ~36 seconds
                        int delta = lastBatteryLevel - batteryPct;
                        if (delta > 0) {
                            float instantRate = delta / elapsedHours;
                            if (instantRate < 0) instantRate = 0;
                            // Smooth the rate using exponential moving average
                            if (hasRealDischargeRate.get()) {
                                dischargeRate = dischargeRate * 0.7f + instantRate * 0.3f;
                            } else {
                                dischargeRate = instantRate;
                                hasRealDischargeRate.set(true);
                            }
                            if (dischargeRate < 0) dischargeRate = 0;
                        }
                    }
                }
            }
        }

        // If we don't have a real discharge rate yet, try to estimate from historical data
        if (!hasRealDischargeRate.get()) {
            estimateDischargeRateFromHistoryAsync(batteryPct, isCharging);
        }

        synchronized (rateLock) {
            lastBatteryLevel = batteryPct;
            lastUpdateTime = now;
        }

        // Endurance estimate (snapshot under lock for consistent read)
        float currentDischargeRate;
        boolean realRate;
        synchronized (rateLock) {
            currentDischargeRate = dischargeRate;
            realRate = hasRealDischargeRate.get();
        }
        float remainingHours = 0f;
        if (currentDischargeRate > 0 && batteryPct > 0 && !isCharging) {
            remainingHours = batteryPct / currentDischargeRate;
        }
        int hours = (int) remainingHours;
        int minutes = (int) ((remainingHours - hours) * 60);

        if (tvEnduranceHours != null) {
            if (isCharging || (!realRate && currentDischargeRate <= 0)) {
                tvEnduranceHours.setText("--");
            } else {
                tvEnduranceHours.setText(String.valueOf(hours));
            }
        }

        if (tvEnduranceMeta != null) {
            tvEnduranceMeta.setText(String.format(Locale.getDefault(),
                    getString(R.string.meta_endurance), batteryPct, currentDischargeRate));
        }

        // Quick metrics
        if (tvMetricBattery != null) {
            tvMetricBattery.setText(String.format(Locale.getDefault(), "%d%%", batteryPct));
        }
        if (tvMetricDischarge != null) {
            if (realRate) {
                tvMetricDischarge.setText(String.format(Locale.getDefault(), "%.1f%%/h", currentDischargeRate));
            } else {
                tvMetricDischarge.setText(String.format(Locale.getDefault(), "~%.1f%%/h", currentDischargeRate));
            }
        }
        if (tvMetricTemp != null) {
            tvMetricTemp.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));
        }

        // Details
        if (tvChargingStatus != null) tvChargingStatus.setText(chargingStatus);
        if (tvUsedTime != null) tvUsedTime.setText(formatDuration(SystemClock.elapsedRealtime()));
        if (tvConsumedBattery != null) {
            int consumed = (batteryPct >= 0) ? (100 - batteryPct) : 0;
            tvConsumedBattery.setText(String.format(Locale.getDefault(), "%d%%", consumed));
        }

        // Estimated time to full charge
        if (tvEstimatedFull != null) {
            if (isCharging) {
                tvEstimatedFull.setText(estimateChargeTimeRemaining(batteryPct));
            } else {
                tvEstimatedFull.setText("--");
            }
        }

        // Screen-on time estimate
        if (tvScreenOnTime != null) {
            tvScreenOnTime.setText(formatDuration(SystemClock.uptimeMillis()));
        }
    }

    /**
     * Estimate discharge rate from historical database records on a background thread.
     */
    private void estimateDischargeRateFromHistoryAsync(int currentLevel, boolean isCharging) {
        if (isCharging) return;
        if (dbExecutor == null) {
            dbExecutor = Executors.newSingleThreadExecutor();
        }
        dbExecutor.submit(() -> {
            try {
                float rate = estimateDischargeRateFromHistory(currentLevel, isCharging);
                if (rate > 0 && !hasRealDischargeRate.get() && isAdded()) {
                    final float finalRate = rate;
                    handler.post(() -> {
                        // Double-check after posting to handler to avoid races
                        if (!hasRealDischargeRate.get() && isAdded()) {
                            synchronized (rateLock) {
                                if (!hasRealDischargeRate.get()) {
                                    dischargeRate = finalRate;
                                    hasRealDischargeRate.set(true);
                                }
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "estimateDischargeRateFromHistoryAsync failed: " + e.getMessage());
            }
        });
    }

    /**
     * Estimate discharge rate from historical database records.
     * Looks at recent discharge sessions to compute an average rate.
     * Must be called from a background thread.
     */
    private float estimateDischargeRateFromHistory(int currentLevel, boolean isCharging) {
        if (isCharging) return 0f;
        try {
            Context ctx = getContext();
            if (ctx == null) return 0f;
            BatteryHealthApplication app = (BatteryHealthApplication) ctx.getApplicationContext();
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
                float result = totalRate / rateCount;
                return (result < 0) ? 0f : result;
            }
        } catch (Exception e) {
            Log.e(TAG, "estimateDischargeRateFromHistory failed: " + e.getMessage());
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

            if (chargingPower > 0 && currentLevel >= 0 && currentLevel < 100) {
                int remainingMah = (int) (effectiveCapacity * (100 - currentLevel) / 100f);
                // Charging is not 100% efficient; assume ~80% efficiency
                float hoursNeeded = (remainingMah / (chargingPower * 1000f)) / 0.8f;
                if (hoursNeeded < 0) hoursNeeded = 0;
                int h = (int) hoursNeeded;
                int m = (int) ((hoursNeeded - h) * 60);
                if (m < 0) m = 0;
                if (h > 0) {
                    return String.format(Locale.getDefault(), "%d小时%d分", h, m);
                } else {
                    return String.format(Locale.getDefault(), "%d分", m);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "estimateChargeTimeRemaining failed: " + e.getMessage());
        }
        return getString(R.string.status_calculating);
    }

    private String formatDuration(long ms) {
        if (ms < 0) ms = 0;
        long hours = ms / (1000L * 60 * 60);
        long minutes = (ms % (1000L * 60 * 60)) / (1000L * 60);
        return String.format(Locale.getDefault(), "%d小时%d分", hours, minutes);
    }
}
