package com.batteryhealth.app.ui.endurance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;
import com.batteryhealth.app.utils.BatteryConsumptionAnalyzer;

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
 */
public class EnduranceFragment extends Fragment {

    private static final String PREFS_ENDURANCE = "endurance_prefs";
    private static final String KEY_LAST_LEVEL = "last_level";
    private static final String KEY_LAST_TIME = "last_time";
    private static final String KEY_DISCHARGE_RATE = "discharge_rate";

    private TextView tvEnduranceHours, tvEnduranceMeta;
    private TextView tvMetricBattery, tvMetricDischarge, tvMetricTemp;
    private TextView tvChargingStatus, tvUsedTime, tvConsumedBattery, tvEstimatedFull, tvScreenOnTime;
    private TextView tvWatchEndurance, tvWatchMode, tvAppConsumptionTitle;
    private View watchSection;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int lastBatteryLevel = -1;
    private long lastUpdateTime = -1;
    private float dischargeRate = 0f;
    private boolean isWatch = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_endurance, container, false);
        isWatch = detectWatch();
        initViews(view);
        animateEntry(view);
        loadSavedState();
        return view;
    }

    private boolean detectWatch() {
        // 通过 Configuration 和 UI 模式判断是否是手表
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

        if (isWatch && watchSection != null) {
            watchSection.setVisibility(View.VISIBLE);
        }
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
        Context ctx = getContext();
        if (ctx == null) return;
        Intent intent = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent != null) {
            updateFromIntent(intent);
        }
    }

    private void updateFromIntent(Intent intent) {
        if (!isAdded() || getContext() == null) return;
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
        float currentMa = current / 1000f;

        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        float tempC = temp / 10f;

        // Calculate discharge rate
        long now = System.currentTimeMillis();
        if (lastBatteryLevel >= 0 && lastUpdateTime > 0 && !isCharging) {
            long elapsedMs = now - lastUpdateTime;
            float elapsedHours = elapsedMs / (1000f * 60f * 60f);
            if (elapsedHours > 0.05f) { // 至少3分钟
                int delta = lastBatteryLevel - batteryPct;
                if (delta > 0) {
                    float newRate = delta / elapsedHours;
                    // 平滑处理
                    dischargeRate = dischargeRate > 0 ? (dischargeRate * 0.7f + newRate * 0.3f) : newRate;
                }
            }
        }
        if (dischargeRate <= 0 && !isCharging) {
            dischargeRate = isWatch ? 5.5f : 12.2f; // 手表默认放电速率更低
        }
        lastBatteryLevel = batteryPct;
        lastUpdateTime = now;

        // Endurance estimate
        float remainingHours = isCharging ? 0 : batteryPct / dischargeRate;
        int hours = (int) remainingHours;
        int minutes = (int) ((remainingHours - hours) * 60);
        safeSetText(tvEnduranceHours, String.format(Locale.getDefault(), "%d小时%d分", hours, minutes));
        safeSetText(tvEnduranceMeta, String.format(Locale.getDefault(),
                getString(R.string.meta_endurance), batteryPct, dischargeRate));

        // Quick metrics
        safeSetText(tvMetricBattery, String.format(Locale.getDefault(), "%d%%", batteryPct));
        safeSetText(tvMetricDischarge, String.format(Locale.getDefault(), "%.1f%%/h", dischargeRate));
        safeSetText(tvMetricTemp, String.format(Locale.getDefault(), "%.1f°C", tempC));

        // Details
        safeSetText(tvChargingStatus, chargingStatus);
        safeSetText(tvUsedTime, formatDuration(SystemClock.elapsedRealtime()));
        safeSetText(tvConsumedBattery, String.format(Locale.getDefault(), "%d%%", 100 - batteryPct));
        safeSetText(tvEstimatedFull, isCharging ? estimateFullChargeTime(batteryPct, currentMa) : "--");
        safeSetText(tvScreenOnTime, formatDuration(SystemClock.uptimeMillis()));

        // 手表专属续航分析
        if (isWatch && watchSection != null) {
            updateWatchEndurance(batteryPct, isCharging);
        }

        // 应用耗电排行
        if (tvAppConsumptionTitle != null) {
            tvAppConsumptionTitle.setVisibility(isWatch ? View.GONE : View.VISIBLE);
        }
    }

    private void safeSetText(TextView tv, String text) {
        if (tv != null) tv.setText(text);
    }

    private String estimateFullChargeTime(int batteryPct, float currentMa) {
        if (currentMa <= 0) return getString(R.string.status_calculating);
        int remaining = 100 - batteryPct;
        float capacityMah = getBatteryCapacity();
        float hours = (remaining * capacityMah / 100f) / currentMa;
        int h = (int) hours;
        int m = (int) ((hours - h) * 60);
        return String.format(Locale.getDefault(), "%d小时%d分", h, m);
    }

    private float getBatteryCapacity() {
        Context ctx = getContext();
        if (ctx == null) return 4000;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int energy = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                if (energy > 0) return energy / 1000f;
            }
        }
        return 4000; // 默认4000mAh
    }

    private void updateWatchEndurance(int batteryPct, boolean isCharging) {
        if (tvWatchEndurance == null || tvWatchMode == null) return;
        Context ctx = getContext();
        if (ctx == null) return;

        // 手表模式判断：始终开启显示、运动模式、省电模式
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

        float hours = isCharging ? 0 : batteryPct / watchDischargeRate;
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
