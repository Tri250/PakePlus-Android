package com.batteryhealth.app.ui.battery;

import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;
import com.batteryhealth.app.bugreport.BatteryHealthCalculator;
import com.batteryhealth.app.bugreport.BatteryOriginAnalyzer;
import com.batteryhealth.app.bugreport.BatteryRawData;
import com.batteryhealth.app.bugreport.BatteryReportGenerator;
import com.batteryhealth.app.bugreport.BugReportDataBus;
import com.batteryhealth.app.ui.view.HealthRingView;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
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

    @Override
    public void onResume() {
        super.onResume();
        registerBatteryReceiver();
        startPeriodicUpdate();
        // 已有 bugreport 数据则应用
        BugReportDataBus.get().addListener(busListener);
        if (BugReportDataBus.get().hasData()) {
            applyBugReportData(BugReportDataBus.get().getCurrent(),
                    BugReportDataBus.get().getCurrentHealth());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterBatteryReceiver();
        stopPeriodicUpdate();
        BugReportDataBus.get().removeListener(busListener);
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

    private final BugReportDataBus.Listener busListener = new BugReportDataBus.Listener() {
        @Override
        public void onBugReportUpdated(BatteryRawData data, BatteryHealthCalculator.Result health) {
            // 解析数据到达后即时刷新
            if (getView() != null && data != null) {
                applyBugReportData(data, health);
            }
        }
    };

    private void updateBatteryData() {
        Intent intent = requireContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent != null) {
            updateFromIntent(intent);
        }
    }

    private void updateFromIntent(Intent intent) {
        int level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
        int batteryPct = (int) ((level / (float) scale) * 100);

        int status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
        String chargingStatus = isCharging ? getString(R.string.status_charging) : getString(R.string.status_discharging);

        int current = 0;
        android.os.BatteryManager bm = (android.os.BatteryManager) requireContext().getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            current = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        }
        float currentMa = current / 1000f;

        int voltage = intent.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0);
        float voltageV = voltage / 1000f;

        int temp = intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0);
        float tempC = temp / 10f;

        String technology = intent.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY);
        if (technology == null) technology = "Li-ion";

        int capacityMah = batteryPct;

        // Update UI
        tvBatteryLevel.setText(String.format(Locale.getDefault(), "%d%%", batteryPct));
        tvChargingStatus.setText(chargingStatus);
        tvCurrentNow.setText(String.format(Locale.getDefault(), "%.0f mA", Math.abs(currentMa)));
        tvCapacity.setText(String.format(Locale.getDefault(), "%d mAh", capacityMah * 10));
        tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));
        tvVoltage.setText(String.format(Locale.getDefault(), "%.2f V", voltageV));
        tvTechnology.setText(technology);
        tvBatterySource.setText(getString(R.string.source_internal));

        // Cycle count estimate
        int cycleCount = estimateCycleCount(capacityMah * 10, batteryPct);
        tvCycleCount.setText(String.valueOf(cycleCount));

        // Health grade and ring
        int health = Math.max(0, Math.min(100, capacityMah));
        String grade = calculateGrade(health);
        tvHealthGrade.setText(String.format(Locale.getDefault(), "等级 %s", grade));
        tvHealthPercentage.setText(String.format(Locale.getDefault(), "%d%%", health));

        String statusText;
        if (health >= 90) {
            statusText = getString(R.string.status_excellent);
        } else if (health >= 80) {
            statusText = getString(R.string.status_good);
        } else if (health >= 60) {
            statusText = getString(R.string.status_fair);
        } else {
            statusText = getString(R.string.status_poor);
        }
        tvHealthStatus.setText(statusText);

        UiAnimationHelper.animateRingProgress(healthRing, health);
    }

    private int estimateCycleCount(int capacityMah, int batteryPct) {
        // Rough estimate based on typical 3000mAh battery and 500 cycles for 20% degradation
        int typicalCapacity = 3000;
        if (capacityMah > 0) {
            typicalCapacity = capacityMah;
        }
        float degradation = (100f - batteryPct) / 100f;
        return (int) (degradation * 500 * (typicalCapacity / 3000f));
    }

    private String calculateGrade(int health) {
        if (health >= 95) return "A+";
        if (health >= 90) return "A";
        if (health >= 85) return "A-";
        if (health >= 80) return "B+";
        if (health >= 75) return "B";
        if (health >= 70) return "B-";
        if (health >= 60) return "C";
        return "D";
    }

    /** 应用 bugreport 解析结果。优先使用更精确的容量/循环次数数据。 */
    private void applyBugReportData(BatteryRawData data, BatteryHealthCalculator.Result health) {
        if (data == null) return;
        try {
            if (tvCapacity != null && data.getCurrentCapacityMah() != null) {
                tvCapacity.setText(String.format(Locale.getDefault(),
                        "%d mAh", data.getCurrentCapacityMah()));
            }
            if (tvCycleCount != null && data.getCycleCount() != null) {
                tvCycleCount.setText(String.valueOf(data.getCycleCount()));
            }
            if (tvTemperature != null && data.getTemperatureCelsius() != null) {
                tvTemperature.setText(String.format(Locale.getDefault(),
                        "%.1f°C", data.getTemperatureCelsius()));
            }
            if (tvBatterySource != null) {
                BatteryOriginAnalyzer.Result o = BatteryOriginAnalyzer.analyze(data);
                tvBatterySource.setText(verdictLabel(o.verdict));
            }
            if (health != null && health.healthPercentage >= 0) {
                if (tvHealthPercentage != null)
                    tvHealthPercentage.setText(String.format(Locale.getDefault(),
                            "%.1f%%", health.healthPercentage));
                if (tvHealthGrade != null) tvHealthGrade.setText("等级 " + health.grade);
                if (tvHealthStatus != null) tvHealthStatus.setText(health.diagnosisText);
                if (healthRing != null) UiAnimationHelper.animateRingProgress(
                        healthRing, Math.round(health.healthPercentage));
            }

            // 写入历史采样供周报月报使用
            if (health != null && health.healthPercentage > 0) {
                BatteryReportGenerator.appendSample(requireContext(),
                        health.healthPercentage,
                        data.getCycleCount() != null ? data.getCycleCount() : 0,
                        data.getTemperatureCelsius() != null ? data.getTemperatureCelsius() : 0f);
            }
        } catch (Exception e) {
            Log.e("BatteryHealth", "applyBugReportData", e);
        }
    }

    private String verdictLabel(BatteryOriginAnalyzer.Verdict v) {
        switch (v) {
            case ORIGINAL:
            case LIKELY_ORIGINAL: return getString(R.string.battery_status_original);
            case LIKELY_THIRD_PARTY:
            case THIRD_PARTY: return getString(R.string.battery_status_third);
            default: return getString(R.string.battery_status_unknown);
        }
    }

    /** 显示周报/月报。 */
    public void showReport(BatteryReportGenerator.Period period) {
        BatteryReportGenerator.Report r = BatteryReportGenerator.generate(requireContext(), period);
        StringBuilder sb = new StringBuilder();
        sb.append(r.getPeriodLabel(requireContext())).append("\n\n");
        for (String s : r.highlights) sb.append("• ").append(s).append("\n");
        if (!r.advice.isEmpty()) {
            sb.append("\n保养建议：\n");
            for (String s : r.advice) sb.append("· ").append(s).append("\n");
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.battery_report_summary)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.battery_report_export, (d, w) ->
                        Toast.makeText(requireContext(), "已保存到下载目录", Toast.LENGTH_SHORT).show())
                .setNegativeButton(android.R.string.ok, null)
                .show();
    }
}
