package com.batteryhealth.app.ui.battery;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
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

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.ui.view.HealthRingView;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.Locale;

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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    private BatteryDataManager batteryDataManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_battery_health, container, false);
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
        startPeriodicUpdate();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPeriodicUpdate();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPeriodicUpdate();
        healthRing = null;
        tvHealthPercentage = null;
        tvHealthGrade = null;
        tvHealthStatus = null;
        tvBatteryLevel = null;
        tvChargingStatus = null;
        tvCurrentNow = null;
        tvCapacity = null;
        tvCycleCount = null;
        tvTemperature = null;
        tvVoltage = null;
        tvBatterySource = null;
        tvTechnology = null;
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

    private void updateBatteryData() {
        if (!isAdded() || getContext() == null) return;

        BatteryDataManager bdm = getBatteryDataManager();
        if (bdm == null) {
            updateFromBasicIntent();
            return;
        }

        BatteryInfo info = bdm.getBatteryInfo();
        if (info == null) {
            updateFromBasicIntent();
            return;
        }

        // 电量
        if (tvBatteryLevel != null) {
            tvBatteryLevel.setText(String.format(Locale.getDefault(), "%d%%", info.getLevel()));
        }

        // 充电状态
        if (tvChargingStatus != null) {
            tvChargingStatus.setText(bdm.getChargingStatusText());
        }

        // 电流
        if (tvCurrentNow != null) {
            int currentMa = bdm.readCurrentMa();
            tvCurrentNow.setText(String.format(Locale.getDefault(), "%.0f mA", (float) Math.abs(currentMa)));
        }

        // 设计容量 / 当前容量
        if (tvCapacity != null) {
            if (info.getDesignCapacity() > 0) {
                if (info.getCurrentCapacity() > 0) {
                    tvCapacity.setText(String.format(Locale.getDefault(), "%d / %d mAh",
                            info.getCurrentCapacity(), info.getDesignCapacity()));
                } else {
                    tvCapacity.setText(String.format(Locale.getDefault(), "%d mAh", info.getDesignCapacity()));
                }
            } else {
                tvCapacity.setText("--");
            }
        }

        // 循环次数
        if (tvCycleCount != null) {
            tvCycleCount.setText(bdm.formatCycleCount(info));
        }

        // 温度
        if (tvTemperature != null) {
            tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", info.getTemperature()));
        }

        // 电压
        if (tvVoltage != null) {
            float voltageV = info.getVoltage() / 1000f;
            tvVoltage.setText(String.format(Locale.getDefault(), "%.2f V", voltageV));
        }

        // 电池技术
        if (tvTechnology != null) {
            String tech = info.getTechnology();
            tvTechnology.setText(tech != null && !tech.isEmpty() ? tech : "Li-ion");
        }

        // 电池来源
        if (tvBatterySource != null) {
            tvBatterySource.setText(bdm.getBatterySourceText());
        }

        // 健康度
        float healthPct = info.getHealthPercentage();
        if (healthPct >= 0) {
            int healthInt = Math.round(healthPct);
            if (tvHealthPercentage != null) {
                tvHealthPercentage.setText(String.format(Locale.getDefault(), "%d%%", healthInt));
            }
            if (tvHealthGrade != null) {
                tvHealthGrade.setText(String.format(Locale.getDefault(), "等级 %s", info.getHealthGrade()));
            }

            String statusText;
            if (healthPct >= 90) {
                statusText = getString(R.string.status_excellent);
            } else if (healthPct >= 80) {
                statusText = getString(R.string.status_good);
            } else if (healthPct >= 60) {
                statusText = getString(R.string.status_fair);
            } else {
                statusText = getString(R.string.status_poor);
            }
            if (tvHealthStatus != null) {
                tvHealthStatus.setText(statusText);
            }
            if (healthRing != null) {
                UiAnimationHelper.animateRingProgress(healthRing, healthInt);
            }
        } else {
            if (tvHealthPercentage != null) tvHealthPercentage.setText("--");
            if (tvHealthGrade != null) tvHealthGrade.setText("--");
            if (tvHealthStatus != null) tvHealthStatus.setText(getString(R.string.health_status_no_data));
        }
    }

    /**
     * 基础 fallback：仅从 sticky intent 获取电量/温度/电压等基础数据，
     * 不含健康度/容量/循环次数等需要 sysfs 或数据库的数据。
     */
    private void updateFromBasicIntent() {
        if (getContext() == null) return;
        Intent intent = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return;

        int level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
        int batteryPct = (int) ((level / (float) scale) * 100);

        int status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
        String chargingStatus = isCharging ? getString(R.string.status_charging) : getString(R.string.status_discharging);

        int temp = intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0);
        float tempC = temp / 10f;

        int voltage = intent.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0);
        float voltageV = voltage / 1000f;

        String technology = intent.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY);
        if (technology == null) technology = "Li-ion";

        if (tvBatteryLevel != null) tvBatteryLevel.setText(String.format(Locale.getDefault(), "%d%%", batteryPct));
        if (tvChargingStatus != null) tvChargingStatus.setText(chargingStatus);
        if (tvCurrentNow != null) tvCurrentNow.setText("--");
        if (tvCapacity != null) tvCapacity.setText("--");
        if (tvCycleCount != null) tvCycleCount.setText("--");
        if (tvTemperature != null) tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));
        if (tvVoltage != null) tvVoltage.setText(String.format(Locale.getDefault(), "%.2f V", voltageV));
        if (tvTechnology != null) tvTechnology.setText(technology);
        if (tvBatterySource != null) tvBatterySource.setText(getString(R.string.source_internal));
        if (tvHealthPercentage != null) tvHealthPercentage.setText("--");
        if (tvHealthGrade != null) tvHealthGrade.setText("--");
        if (tvHealthStatus != null) tvHealthStatus.setText(getString(R.string.status_detecting));
    }
}
