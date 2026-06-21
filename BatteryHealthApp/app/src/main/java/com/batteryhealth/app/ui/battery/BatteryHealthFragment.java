package com.batteryhealth.app.ui.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.ui.view.HealthRingView;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BatteryHealthFragment extends Fragment {

    private HealthRingView healthRing;
    private TextView tvHealthPercentage;
    private TextView tvHealthGrade;
    private TextView tvHealthStatus;
    private TextView tvBatteryLevel;
    private TextView tvChargingStatus;
    private TextView tvCurrentNow;
    private TextView tvCapacity;
    private TextView tvCycleCount;
    private TextView tvTemperature;
    private TextView tvVoltage;
    private TextView tvBatterySource;
    private TextView tvTechnology;
    private TextView tvHealthSource;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private BatteryDataManager batteryDataManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_battery_health, container, false);
        batteryDataManager = BatteryDataManager.getInstance(requireContext());
        initViews(view);
        animateEntry(view);
        return view;
    }

    private void initViews(View view) {
        healthRing = view.findViewById(R.id.health_ring);
        tvHealthPercentage = view.findViewById(R.id.tv_health_percentage);
        tvHealthGrade = view.findViewById(R.id.tv_health_grade);
        tvHealthStatus = view.findViewById(R.id.tv_health_status);
        tvBatteryLevel = view.findViewById(R.id.tv_battery_level);
        tvChargingStatus = view.findViewById(R.id.tv_charging_status);
        tvCurrentNow = view.findViewById(R.id.tv_current_now);
        tvCapacity = view.findViewById(R.id.tv_capacity);
        tvCycleCount = view.findViewById(R.id.tv_cycle_count);
        tvTemperature = view.findViewById(R.id.tv_temperature);
        tvVoltage = view.findViewById(R.id.tv_voltage);
        tvBatterySource = view.findViewById(R.id.tv_battery_source);
        tvTechnology = view.findViewById(R.id.tv_technology);
        tvHealthSource = view.findViewById(R.id.tv_health_source);

        // 周报/月报入口
        View btnWeeklyReport = view.findViewById(R.id.btn_weekly_report);
        View btnMonthlyReport = view.findViewById(R.id.btn_monthly_report);
        if (btnWeeklyReport != null) {
            btnWeeklyReport.setOnClickListener(v -> ReportActivity.start(requireContext(), ReportActivity.TYPE_WEEKLY));
        }
        if (btnMonthlyReport != null) {
            btnMonthlyReport.setOnClickListener(v -> ReportActivity.start(requireContext(), ReportActivity.TYPE_MONTHLY));
        }

        // 电池溯源 / 健康检查入口
        View btnBatterySource = view.findViewById(R.id.btn_battery_source);
        View btnHealthCheck = view.findViewById(R.id.btn_health_check);
        if (btnBatterySource != null) {
            btnBatterySource.setOnClickListener(v -> com.batteryhealth.app.ui.source.BatterySourceActivity.start(requireContext()));
        }
        if (btnHealthCheck != null) {
            btnHealthCheck.setOnClickListener(v -> com.batteryhealth.app.ui.healthcheck.HealthCheckActivity.start(requireContext()));
        }
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
        UiAnimationHelper.animateCardsEntry(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerBatteryReceiver();
        startPeriodicUpdate();
        updateBatteryData();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterBatteryReceiver();
        stopPeriodicUpdate();
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
        executor.execute(() -> {
            try {
                BatteryInfo info = batteryDataManager.getBatteryInfo();
                // 持久化到数据库，供趋势追踪和报告使用
                persistBatteryInfo(info);
                mainHandler.post(() -> {
                    if (isAdded()) bindBatteryInfo(info);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (isAdded()) showDetecting();
                });
            }
        });
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private void persistBatteryInfo(BatteryInfo info) {
        try {
            AppDatabase db = BatteryHealthApplication.getDatabase();
            if (db != null) {
                db.batteryInfoDao().insert(info.copy());
            }
        } catch (Exception e) {
            // 数据库写入失败不应影响 UI 展示
        }
    }

    private void bindBatteryInfo(BatteryInfo info) {
        if (!isAdded() || getContext() == null) return;
        if (info == null) {
            showDetecting();
            return;
        }

        int health = info.hasValidHealthData() ? Math.round(info.getHealthPercentage()) : -1;
        int level = info.getLevel();

        safeSetText(tvBatteryLevel, level >= 0 ? String.format(Locale.getDefault(), "%d%%", level) : "--");
        safeSetText(tvChargingStatus, getChargingStatusText(info));
        safeSetText(tvCurrentNow, String.format(Locale.getDefault(), "%.0f mA", Math.abs(info.getCurrentNow() / 1000f)));

        int currentCapacity = info.getCurrentCapacity();
        safeSetText(tvCapacity, currentCapacity > 0
                ? String.format(Locale.getDefault(), "%d / %d mAh", currentCapacity, Math.max(currentCapacity, info.getDesignCapacity()))
                : String.format(Locale.getDefault(), "%d mAh", info.getDesignCapacity()));

        safeSetText(tvCycleCount, batteryDataManager.formatCycleCount(info));
        safeSetText(tvTemperature, String.format(Locale.getDefault(), "%.1f°C", info.getTemperature()));
        safeSetText(tvVoltage, String.format(Locale.getDefault(), "%.2f V", info.getVoltage() / 1000f));
        safeSetText(tvTechnology, info.getTechnology());
        safeSetText(tvBatterySource, formatBatterySource(info));

        if (tvHealthSource != null) {
            tvHealthSource.setText(batteryDataManager.getHealthSourceText());
        }

        if (health >= 0) {
            safeSetText(tvHealthPercentage, String.format(Locale.getDefault(), "%d%%", health));
            safeSetText(tvHealthGrade, String.format(Locale.getDefault(), "等级 %s", info.getHealthGrade()));
            safeSetText(tvHealthStatus, getHealthStatusText(health));
            if (healthRing != null) {
                UiAnimationHelper.animateRingProgress(healthRing, health);
            }
        } else {
            safeSetText(tvHealthPercentage, "--");
            safeSetText(tvHealthGrade, "等级 --");
            safeSetText(tvHealthStatus, getString(R.string.health_status_no_data));
        }
    }

    private void safeSetText(TextView tv, String text) {
        if (tv != null) tv.setText(text);
    }

    private String getChargingStatusText(BatteryInfo info) {
        int status = info.getStatus();
        if (status == android.os.BatteryManager.BATTERY_STATUS_CHARGING) {
            return getString(R.string.status_charging);
        } else if (status == android.os.BatteryManager.BATTERY_STATUS_FULL) {
            return getString(R.string.status_fully_charged);
        } else if (status == android.os.BatteryManager.BATTERY_STATUS_DISCHARGING) {
            return getString(R.string.status_discharging);
        } else if (status == android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
            return getString(R.string.status_not_charging_short);
        }
        return getString(R.string.status_unknown);
    }

    private String getHealthStatusText(int health) {
        if (health >= 90) return getString(R.string.status_excellent);
        if (health >= 80) return getString(R.string.status_good);
        if (health >= 60) return getString(R.string.status_fair);
        return getString(R.string.status_poor);
    }

    private String formatBatterySource(BatteryInfo info) {
        String source = info.getBatterySource();
        if ("original".equals(source)) {
            return getString(R.string.battery_source_original_confidence, (int) (info.getBatterySourceConfidence() * 100));
        } else if ("third_party".equals(source)) {
            return getString(R.string.battery_source_third_party_confidence, (int) (info.getBatterySourceConfidence() * 100));
        }
        return getString(R.string.battery_source_unverifiable);
    }

    private void showDetecting() {
        if (!isAdded() || getContext() == null) return;
        safeSetText(tvHealthPercentage, "--");
        safeSetText(tvHealthGrade, "等级 --");
        safeSetText(tvHealthStatus, getString(R.string.status_detecting));
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
