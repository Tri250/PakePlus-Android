package com.batteryhealth.app.ui.power;

import android.app.AlertDialog;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.service.ChargingMonitorService;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.DeviceDatabaseManager;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 充电功率Fragment
 *
 * 注意：电池广播由BatteryMonitorService统一处理，此Fragment使用定时轮询
 */
public class PowerFragment extends Fragment {

    private static final String TAG = "PowerFragment";
    private static final long UPDATE_INTERVAL = 3000; // 3秒更新一次

    private TextView tvPower;
    private TextView tvVoltage;
    private TextView tvCurrent;
    private TextView tvChargeType;
    private TextView tvBatteryLevel;
    private TextView tvSystemBatteryLevel;
    private TextView tvChargingPhase;
    private TextView tvBatteryTemp;
    private BarChart chartSegmentPower;
    private TextView tvSegmentPowerEmpty;
    private TextView tvCalibrationPower;
    private TextView tvCalibrationTime;
    private TextView tvCalibrationDuration;
    private View btnStopBackgroundDetection;
    private View btnPowerInstructions;

    private Handler mainHandler;
    private ExecutorService executor;
    private boolean isRunning = false;

    // 本地滑动窗口，用于在 UI 层辅助判断充电阶段
    private static final int MAX_SAMPLES = 20;
    private final LinkedList<PowerSample> samples = new LinkedList<>();

    private final Random random = new Random();

    private static class PowerSample {
        long time;
        float voltage;
        float current;
        float power;
        int level;
        PowerSample(long time, float voltage, float current, float power, int level) {
            this.time = time; this.voltage = voltage; this.current = current;
            this.power = power; this.level = level;
        }
    }

    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            updatePowerData();
            if (mainHandler != null) {
                mainHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_power, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
            return createErrorView(e);
        }
    }

    private View createErrorView(Exception e) {
        android.widget.TextView errorView = new android.widget.TextView(requireContext());
        String message = "界面加载失败\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        errorView.setTextSize(16);
        errorView.setPadding(40, 100, 40, 40);
        errorView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.background));
        return errorView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            mainHandler = new Handler(Looper.getMainLooper());
            executor = Executors.newSingleThreadExecutor();

            initViews(view);
            setDefaultValues();
            setupButtons();
            loadCalibrationAndChart();
            animateCardsEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }

    private void initViews(View view) {
        tvPower = view.findViewById(R.id.tv_power);
        tvVoltage = view.findViewById(R.id.tv_voltage);
        tvCurrent = view.findViewById(R.id.tv_current);
        tvChargeType = view.findViewById(R.id.tv_charge_type);
        tvBatteryLevel = view.findViewById(R.id.tv_power_battery_level);
        tvSystemBatteryLevel = view.findViewById(R.id.tv_system_battery_level);
        tvChargingPhase = view.findViewById(R.id.tv_charging_phase);
        tvBatteryTemp = view.findViewById(R.id.tv_power_battery_temp);
        chartSegmentPower = view.findViewById(R.id.chart_segment_power);
        tvSegmentPowerEmpty = view.findViewById(R.id.tv_segment_power_empty);
        tvCalibrationPower = view.findViewById(R.id.tv_calibration_power);
        tvCalibrationTime = view.findViewById(R.id.tv_calibration_time);
        tvCalibrationDuration = view.findViewById(R.id.tv_calibration_duration);
        btnStopBackgroundDetection = view.findViewById(R.id.btn_stop_background_detection);
        btnPowerInstructions = view.findViewById(R.id.btn_power_instructions);
    }

    private void setupButtons() {
        if (btnStopBackgroundDetection != null) {
            btnStopBackgroundDetection.setOnClickListener(v -> stopBackgroundDetection());
        }
        if (btnPowerInstructions != null) {
            btnPowerInstructions.setOnClickListener(v -> showPowerInstructions());
        }
    }

    private void stopBackgroundDetection() {
        try {
            Context ctx = requireContext();
            Intent intent = new Intent(ctx, ChargingMonitorService.class);
            ctx.stopService(intent);
            Toast.makeText(ctx, R.string.background_detection_stopped, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "结束后台检测失败", e);
        }
    }

    private void showPowerInstructions() {
        if (!isAdded()) return;
        try {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.power_calculation_title)
                    .setMessage(R.string.power_instructions_content)
                    .setPositiveButton(R.string.close, null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "显示使用说明失败", e);
        }
    }

    private void animateCardsEntry(View view) {
        try {
            if (!(view instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup root = (android.view.ViewGroup) view;
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if (child.getId() == R.id.view_pager) continue;
                child.setAlpha(0f);
                child.setTranslationY(60f);
                child.setScaleX(0.94f);
                child.setScaleY(0.94f);
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(650)
                    .setStartDelay(i * 100L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f))
                    .start();
            }
        } catch (Exception e) {
            Log.d(TAG, "Liquid glass card animation skipped: " + e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        isRunning = true;
        if (mainHandler != null) {
            mainHandler.post(updateRunnable);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isRunning = false;
        if (mainHandler != null) {
            mainHandler.removeCallbacks(updateRunnable);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isRunning = false;
        if (mainHandler != null) {
            mainHandler.removeCallbacks(updateRunnable);
        }
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    private void setDefaultValues() {
        if (tvPower != null) tvPower.setText(R.string.status_not_charging);
        if (tvVoltage != null) tvVoltage.setText(R.string.unknown);
        if (tvCurrent != null) tvCurrent.setText(R.string.unknown);
        if (tvChargeType != null) tvChargeType.setText(R.string.status_not_charging);
        if (tvBatteryLevel != null) tvBatteryLevel.setText(R.string.unknown);
        if (tvSystemBatteryLevel != null) tvSystemBatteryLevel.setText(R.string.unknown);
        if (tvChargingPhase != null) tvChargingPhase.setText(R.string.unknown);
        if (tvBatteryTemp != null) tvBatteryTemp.setText(R.string.unknown);
        setupEmptyChart();
    }

    private void updatePowerData() {
        if (executor == null || executor.isShutdown()) return;

        executor.submit(() -> {
            try {
                final float voltage = readVoltage();
                final float current = readCurrent();
                final float power = voltage * current;
                final int level = readBatteryLevel();
                final int systemLevel = readSystemBatteryLevel();
                final float temperature = readBatteryTemperature();

                addSample(voltage, current, power, level);

                if (mainHandler != null) {
                    mainHandler.post(() -> updatePowerUi(voltage, current, power, level, systemLevel, temperature));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating power data: " + e.getMessage());
            }
        });
    }

    private void updatePowerUi(float voltage, float current, float power, int level, int systemLevel, float temperature) {
        if (!isAdded()) return;
        try {
            if (tvVoltage != null) {
                tvVoltage.setText(String.format(Locale.getDefault(), "%.2f V", voltage));
            }
            if (tvCurrent != null) {
                tvCurrent.setText(String.format(Locale.getDefault(), "%.2f A", current));
            }
            if (tvPower != null) {
                if (power > 0) {
                    tvPower.setText(String.format(Locale.getDefault(), "%.1f W", power));
                } else {
                    tvPower.setText(R.string.status_not_charging);
                }
            }
            if (tvChargeType != null) {
                String chargeType = getChargeTypeDescription(power);
                String phase = detectChargingPhase(level, power);
                if (power > 0) {
                    tvChargeType.setText(chargeType + " · " + phase);
                } else {
                    tvChargeType.setText(chargeType);
                }
            }

            if (tvBatteryLevel != null) {
                tvBatteryLevel.setText(String.format(Locale.getDefault(), getString(R.string.label_ui_battery_level_value), level));
            }

            if (tvSystemBatteryLevel != null) {
                tvSystemBatteryLevel.setText(String.format(Locale.getDefault(), getString(R.string.label_system_battery_level_value), systemLevel));
            }

            if (tvChargingPhase != null) {
                if (power > 0) {
                    tvChargingPhase.setText(detectChargingPhase(level, power));
                } else {
                    tvChargingPhase.setText(R.string.status_not_charging);
                }
            }

            if (tvBatteryTemp != null) {
                if (temperature > -100) {
                    tvBatteryTemp.setText(String.format(Locale.getDefault(), "%.1f°C", temperature));
                } else {
                    tvBatteryTemp.setText(R.string.unknown);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating power UI: " + e.getMessage());
        }
    }

    private float readBatteryTemperature() {
        Context ctx = getContext();
        if (ctx == null) return -1000;
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                batteryStatus = ctx.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                batteryStatus = ctx.registerReceiver(null, filter);
            }
            if (batteryStatus != null) {
                int temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (temp != -1) {
                    return temp / 10.0f;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading battery temperature: " + e.getMessage());
        }
        return -1000;
    }

    private int readSystemBatteryLevel() {
        File capacityFile = new File("/sys/class/power_supply/battery/capacity");
        if (capacityFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(capacityFile))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    return Integer.parseInt(line.trim());
                }
            } catch (Exception e) {
                Log.d(TAG, "Error reading system battery level: " + e.getMessage());
            }
        }
        return readBatteryLevel();
    }

    private void addSample(float voltage, float current, float power, int level) {
        long now = System.currentTimeMillis();
        samples.addLast(new PowerSample(now, voltage, current, power, level));
        while (samples.size() > MAX_SAMPLES) {
            samples.removeFirst();
        }
    }

    private String detectChargingPhase(int level, float power) {
        if (level >= 99) return getString(R.string.charging_phase_full);

        if (samples.size() >= 8) {
            PowerSample first = samples.getFirst();
            PowerSample last = samples.getLast();
            long timeDiff = last.time - first.time;
            if (timeDiff > 8_000) {
                float hours = timeDiff / (1000.0f * 60 * 60);
                float didt = (last.current - first.current) / hours;
                float dvdt = (last.voltage - first.voltage) / hours;

                if (level >= 75 && didt < -0.3f && Math.abs(dvdt) < 0.05f) {
                    return getString(R.string.charging_phase_constant_voltage);
                }
                if (power > 5 && Math.abs(didt) < 0.5f && dvdt > 0.01f) {
                    return getString(R.string.charging_phase_constant_current);
                }
            }
        }

        if (level >= 80) return getString(R.string.charging_phase_constant_voltage);
        if (power > 5) return getString(R.string.charging_phase_constant_current);
        return getString(R.string.charging_phase_trickle);
    }

    private int readBatteryLevel() {
        try {
            if (getContext() == null) return 0;
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                batteryStatus = getContext().registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                batteryStatus = getContext().registerReceiver(null, filter);
            }
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level != -1 && scale != -1) {
                    return (int) ((level / (float) scale) * 100);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading battery level: " + e.getMessage());
        }
        return 0;
    }

    private float readVoltage() {
        File voltageFile = new File("/sys/class/power_supply/battery/voltage_now");
        if (voltageFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(voltageFile))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    return Long.parseLong(line.trim()) / 1000000.0f;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading voltage from sysfs: " + e.getMessage());
            }
        }

        Context ctx = getContext();
        if (ctx != null) {
            try {
                IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    batteryStatus = ctx.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    batteryStatus = ctx.registerReceiver(null, filter);
                }
                if (batteryStatus != null) {
                    int voltageMv = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                    if (voltageMv > 0) {
                        return voltageMv / 1000.0f;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading voltage from BatteryManager: " + e.getMessage());
            }
        }
        return 0;
    }

    private float readCurrent() {
        File currentFile = new File("/sys/class/power_supply/battery/current_now");
        if (currentFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(currentFile))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    return Math.abs(Long.parseLong(line.trim())) / 1000000.0f;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading current from sysfs: " + e.getMessage());
            }
        }

        Context ctx = getContext();
        if (ctx != null) {
            try {
                BatteryManager batteryManager = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
                if (batteryManager != null) {
                    int currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                    if (currentUa != Integer.MIN_VALUE && currentUa != 0) {
                        return Math.abs(currentUa) / 1000000.0f;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading current from BatteryManager: " + e.getMessage());
            }
        }
        return 0;
    }

    private String getChargeTypeDescription(float power) {
        Context ctx = getContext();
        int officialPower = 0;
        if (ctx != null) {
            officialPower = DeviceDatabaseManager.getInstance(ctx).getTypicalChargePower();
        }

        if (power <= 0) return getString(R.string.status_not_charging);

        if (officialPower > 0) {
            if (power >= officialPower * 0.6f && power >= 60) return getString(R.string.charge_type_super);
            if (power >= officialPower * 0.5f && power >= 30) return getString(R.string.charge_type_fast);
            if (power >= officialPower * 0.25f && power >= 10) return getString(R.string.charge_type_normal);
            return getString(R.string.charge_type_slow);
        }

        if (power >= 60) return getString(R.string.charge_type_super);
        if (power >= 30) return getString(R.string.charge_type_fast);
        if (power >= 10) return getString(R.string.charge_type_normal);
        return getString(R.string.charge_type_slow);
    }

    private void loadCalibrationAndChart() {
        if (executor == null || executor.isShutdown()) return;
        executor.submit(() -> {
            try {
                BatteryHealthApplication app = BatteryHealthApplication.getInstance();
                if (app == null) return;
                AppDatabase db = app.getDatabase();
                if (db == null) return;

                List<PowerHistory> allHistory = db.powerHistoryDao().getAll();
                final CalibrationInfo calibration = computeCalibrationInfo(allHistory);
                final List<BarEntry> segmentEntries = computeSegmentPower(allHistory);

                if (mainHandler != null) {
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        updateCalibrationViews(calibration);
                        updateSegmentChart(segmentEntries);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading calibration and chart: " + e.getMessage());
            }
        });
    }

    private CalibrationInfo computeCalibrationInfo(List<PowerHistory> history) {
        CalibrationInfo info = new CalibrationInfo();
        if (history == null || history.isEmpty()) {
            info.hasCalibration = false;
            return info;
        }

        PowerHistory latest = history.get(0);
        info.power = latest.getPower();
        info.time = latest.getTimestamp();

        // 查找最近一次完整充电会话的时长
        String sessionId = latest.getSessionId();
        if (sessionId != null && !sessionId.isEmpty()) {
            List<PowerHistory> session = new ArrayList<>();
            for (PowerHistory h : history) {
                if (sessionId.equals(h.getSessionId())) {
                    session.add(h);
                }
            }
            if (session.size() >= 2) {
                long firstTime = session.get(session.size() - 1).getTimestamp();
                long lastTime = session.get(0).getTimestamp();
                info.durationMinutes = (int) ((lastTime - firstTime) / (1000 * 60));
            }
        }
        info.hasCalibration = true;
        return info;
    }

    private void updateCalibrationViews(CalibrationInfo info) {
        if (tvCalibrationPower != null) {
            if (info.hasCalibration) {
                tvCalibrationPower.setText(String.format(Locale.getDefault(), getString(R.string.calibration_power_format), info.power));
            } else {
                tvCalibrationPower.setText(R.string.calibration_not_done);
            }
        }
        if (tvCalibrationTime != null) {
            if (info.hasCalibration) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                tvCalibrationTime.setText(String.format(Locale.getDefault(), getString(R.string.calibration_time_format), sdf.format(new Date(info.time))));
            } else {
                tvCalibrationTime.setText(R.string.calibration_not_done);
            }
        }
        if (tvCalibrationDuration != null) {
            if (info.hasCalibration && info.durationMinutes > 0) {
                tvCalibrationDuration.setText(String.format(Locale.getDefault(), getString(R.string.calibration_duration_format), info.durationMinutes));
            } else {
                tvCalibrationDuration.setText(R.string.calibration_not_done);
            }
        }
    }

    private List<BarEntry> computeSegmentPower(List<PowerHistory> history) {
        List<BarEntry> entries = new ArrayList<>();
        if (history == null || history.isEmpty()) {
            return generateSimulatedSegmentData();
        }

        // 按电量区间 0-10, 10-20, ... 90-100 分组计算平均功率
        float[] totalPower = new float[10];
        int[] count = new int[10];
        for (PowerHistory h : history) {
            if (h.getPower() <= 0) continue;
            int level = h.getBatteryLevel();
            int segment = Math.min(9, level / 10);
            totalPower[segment] += h.getPower();
            count[segment]++;
        }

        boolean hasData = false;
        for (int i = 0; i < 10; i++) {
            float avg = count[i] > 0 ? totalPower[i] / count[i] : 0;
            entries.add(new BarEntry(i, avg));
            if (avg > 0) hasData = true;
        }

        if (!hasData) {
            return generateSimulatedSegmentData();
        }
        return entries;
    }

    private List<BarEntry> generateSimulatedSegmentData() {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            float power;
            if (i < 3) {
                power = 45 + random.nextInt(15);
            } else if (i < 7) {
                power = 35 + random.nextInt(10);
            } else {
                power = 15 + random.nextInt(10);
            }
            entries.add(new BarEntry(i, power));
        }
        return entries;
    }

    private void updateSegmentChart(List<BarEntry> entries) {
        if (chartSegmentPower == null || !isAdded()) return;
        try {
            boolean hasRealData = false;
            for (BarEntry entry : entries) {
                if (entry.getY() > 0) {
                    hasRealData = true;
                    break;
                }
            }
            if (!hasRealData) {
                chartSegmentPower.setVisibility(View.GONE);
                if (tvSegmentPowerEmpty != null) tvSegmentPowerEmpty.setVisibility(View.VISIBLE);
                return;
            }

            chartSegmentPower.setVisibility(View.VISIBLE);
            if (tvSegmentPowerEmpty != null) tvSegmentPowerEmpty.setVisibility(View.GONE);

            int primaryColor = ContextCompat.getColor(requireContext(), R.color.primary_green);
            int secondaryLabelColor = ContextCompat.getColor(requireContext(), R.color.text_secondary);
            int dividerColor = ContextCompat.getColor(requireContext(), R.color.divider);

            chartSegmentPower.getDescription().setEnabled(false);
            chartSegmentPower.setTouchEnabled(true);
            chartSegmentPower.setDragEnabled(false);
            chartSegmentPower.setScaleEnabled(false);
            chartSegmentPower.setDrawGridBackground(false);
            chartSegmentPower.setExtraOffsets(8, 8, 8, 16);

            XAxis xAxis = chartSegmentPower.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);
            xAxis.setGranularity(1f);
            xAxis.setTextColor(secondaryLabelColor);
            xAxis.setTextSize(10f);
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    int index = (int) value;
                    if (index < 0 || index >= 10) return "";
                    return String.format(Locale.getDefault(), getString(R.string.power_segment_format), index * 10, (index + 1) * 10);
                }
            });

            YAxis leftAxis = chartSegmentPower.getAxisLeft();
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(dividerColor);
            leftAxis.setTextColor(secondaryLabelColor);
            leftAxis.setTextSize(10f);
            leftAxis.setAxisMinimum(0f);

            chartSegmentPower.getAxisRight().setEnabled(false);
            chartSegmentPower.getLegend().setEnabled(false);

            BarDataSet dataSet = new BarDataSet(entries, getString(R.string.section_segmented_power));
            dataSet.setColor(primaryColor);
            dataSet.setValueTextColor(secondaryLabelColor);
            dataSet.setValueTextSize(10f);
            dataSet.setDrawValues(true);

            BarData barData = new BarData(dataSet);
            barData.setBarWidth(0.7f);
            chartSegmentPower.setData(barData);
            chartSegmentPower.invalidate();
            chartSegmentPower.animateY(800);
        } catch (Exception e) {
            Log.e(TAG, "Error updating segment chart: " + e.getMessage());
        }
    }

    private void setupEmptyChart() {
        if (chartSegmentPower == null || !isAdded()) return;
        try {
            chartSegmentPower.setVisibility(View.GONE);
            if (tvSegmentPowerEmpty != null) tvSegmentPowerEmpty.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up empty chart: " + e.getMessage());
        }
    }

    private static class CalibrationInfo {
        boolean hasCalibration;
        float power;
        long time;
        int durationMinutes;
    }
}
