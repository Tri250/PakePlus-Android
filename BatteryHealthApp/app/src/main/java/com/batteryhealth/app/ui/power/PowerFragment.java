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

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.utils.ChargeProtocolDetector;
import com.batteryhealth.app.utils.UiAnimationHelper;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 充电功率页面
 *
 * 功能：
 * 1. 实时校准充电功率
 * 2. 显示电压、电流、温度、电量等关键指标
 * 3. 识别充电协议和充电阶段
 * 4. 展示功率变化曲线
 * 5. 统计本次充电数据
 */
public class PowerFragment extends Fragment {

    private TextView tvWatt, tvPowerType;
    private ProgressBar progressCharge;
    private TextView tvVoltage, tvCurrent, tvChargeStage, tvTemperature, tvBatteryLevel, tvEstimatedFull;
    private TextView tvChargeCount, tvAvgPower, tvTotalChargeTime, tvTotalCharged, tvProtocol;
    private LineChart chartPower;
    private final List<PowerPoint> powerPoints = new ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private long sessionStartTime = -1;
    private float sessionEnergy = 0f;
    private int startLevel = -1;
    private float lastWatt = 0f;
    private long lastSampleTime = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_power, container, false);
        initViews(view);
        setupChart();
        animateEntry(view);
        loadHistory();
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
        tvProtocol = view.findViewById(R.id.tv_protocol);
        chartPower = view.findViewById(R.id.chart_power);
    }

    private void setupChart() {
        chartPower.getDescription().setEnabled(false);
        chartPower.getLegend().setEnabled(false);
        chartPower.setTouchEnabled(true);
        chartPower.setDragEnabled(true);
        chartPower.setScaleEnabled(false);
        chartPower.setDrawGridBackground(false);

        XAxis xAxis = chartPower.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(getColor(R.color.label_2));
        xAxis.setTextSize(10f);
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            @Override
            public String getFormattedValue(float value) {
                return sdf.format(new Date((long) value));
            }
        });

        chartPower.getAxisLeft().setTextColor(getColor(R.color.label_2));
        chartPower.getAxisRight().setEnabled(false);
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
        saveSessionIfNeeded();
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
        int batteryPct = scale > 0 ? (int) ((level / (float) scale) * 100) : 0;

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
        float currentMa = Math.abs(current) / 1000f;

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
        tvCurrent.setText(String.format(Locale.getDefault(), "%.0f mA", currentMa));

        String stage;
        if (batteryPct >= 95) stage = getString(R.string.stage_trickle);
        else if (batteryPct >= 80) stage = getString(R.string.stage_constant_voltage);
        else stage = getString(R.string.stage_fast);
        tvChargeStage.setText(stage);

        tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));
        tvBatteryLevel.setText(String.format(Locale.getDefault(), "%d%%", batteryPct));
        tvEstimatedFull.setText(isCharging ? calculateTimeToFull(batteryPct, currentA) : "--");

        // 充电协议识别
        ChargeProtocolDetector.Result protocolResult = ChargeProtocolDetector.detect(requireContext(), watt);
        tvProtocol.setText(protocolResult != null ? protocolResult.primary : getString(R.string.status_unknown));

        // 功率曲线数据
        long now = System.currentTimeMillis();
        if (isCharging) {
            if (sessionStartTime < 0) {
                sessionStartTime = now;
                startLevel = batteryPct;
                powerPoints.clear();
            }
            powerPoints.add(new PowerPoint(now, watt));
            if (powerPoints.size() > 60) {
                powerPoints.remove(0);
            }
            updateChart();

            // 统计
            if (lastSampleTime > 0 && lastWatt > 0) {
                float hours = (now - lastSampleTime) / (1000f * 60f * 60f);
                sessionEnergy += lastWatt * hours; // Wh
            }
            lastWatt = watt;
            lastSampleTime = now;

            tvChargeCount.setText(String.valueOf(powerPoints.size()));
            tvAvgPower.setText(String.format(Locale.getDefault(), "%.1f W", sessionEnergy / Math.max(1, (now - sessionStartTime) / (1000f * 60f * 60f))));
            long elapsedMin = (now - sessionStartTime) / (1000 * 60);
            tvTotalChargeTime.setText(String.format(Locale.getDefault(), "%d分", elapsedMin));
            tvTotalCharged.setText(String.format(Locale.getDefault(), "%d%%", Math.max(0, batteryPct - startLevel)));
        } else {
            // 未充电时保持历史曲线
            sessionStartTime = -1;
            lastSampleTime = -1;
            lastWatt = 0;
        }
    }

    private void updateChart() {
        List<Entry> entries = new ArrayList<>();
        float maxW = 0;
        for (PowerPoint p : powerPoints) {
            entries.add(new Entry(p.time, p.watt));
            if (p.watt > maxW) maxW = p.watt;
        }
        if (entries.isEmpty()) {
            chartPower.setVisibility(View.GONE);
            return;
        }
        chartPower.setVisibility(View.VISIBLE);

        LineDataSet dataSet = new LineDataSet(entries, "功率");
        dataSet.setColor(getColor(R.color.ios_green));
        dataSet.setLineWidth(2.5f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setFillColor(getColor(R.color.ios_green));
        dataSet.setFillAlpha(30);
        dataSet.setDrawFilled(true);

        chartPower.setData(new LineData(dataSet));
        chartPower.getAxisLeft().setAxisMinimum(0);
        chartPower.getAxisLeft().setAxisMaximum(Math.max(10, maxW * 1.2f));
        chartPower.invalidate();
    }

    private String calculateTimeToFull(int batteryPct, float currentA) {
        if (currentA <= 0) return "--";
        int remaining = 100 - batteryPct;
        float capacityMah = getBatteryCapacity();
        float hours = (remaining * capacityMah / 100f) / (currentA * 1000f);
        int mins = (int) (hours * 60);
        return String.format(Locale.getDefault(), "%d分", mins);
    }

    private float getBatteryCapacity() {
        BatteryManager bm = (BatteryManager) requireContext().getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            int energy = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            if (energy > 0) return energy / 1000f;
        }
        return 4000f;
    }

    private void loadHistory() {
        executor.execute(() -> {
            try {
                AppDatabase db = com.batteryhealth.app.BatteryHealthApplication.getDatabase();
                long since = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
                List<PowerHistory> history = db.powerHistoryDao().getSince(since);
                if (history != null && !history.isEmpty()) {
                    List<PowerPoint> points = new ArrayList<>();
                    for (PowerHistory h : history) {
                        points.add(new PowerPoint(h.getTimestamp(), h.getPower()));
                    }
                    handler.post(() -> {
                        powerPoints.addAll(points);
                        updateChart();
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void saveSessionIfNeeded() {
        if (sessionStartTime > 0 && powerPoints.size() >= 2) {
            executor.execute(() -> {
                try {
                    AppDatabase db = com.batteryhealth.app.BatteryHealthApplication.getDatabase();
                    PowerHistory first = new PowerHistory();
                    first.setTimestamp(sessionStartTime);
                    first.setPower(lastWatt);
                    first.setVoltage(0);
                    first.setCurrent(0);
                    first.setBatteryLevel(startLevel);
                    first.setBatteryTemp(0);
                    first.setChargingPhase("session");
                    db.powerHistoryDao().insert(first);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private int getColor(int resId) {
        return requireContext().getColor(resId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private static class PowerPoint {
        long time;
        float watt;

        PowerPoint(long time, float watt) {
            this.time = time;
            this.watt = watt;
        }
    }
}
