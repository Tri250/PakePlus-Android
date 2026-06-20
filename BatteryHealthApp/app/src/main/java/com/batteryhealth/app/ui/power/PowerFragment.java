package com.batteryhealth.app.ui.power;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

import java.util.LinkedList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.ChargeProtocolDetector;
import com.batteryhealth.app.utils.DeviceDatabaseManager;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

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
    private TextView tvChargeProtocol;
    private TextView tvBatteryLevel;
    private TextView tvChargingPhase;
    private TextView tvBatteryTemp;
    private ProgressBar progressCharge;
    private View powerTypeDot;
    private TextView tvTodayCount;
    private TextView tvTodayAvgPower;
    private TextView tvTodayDuration;
    private TextView tvTodayInput;

    private Handler mainHandler;
    private ExecutorService executor;
    private boolean isRunning = false;
    private AnimatorSet powerTypeDotAnimator;

    // 本地滑动窗口，用于在 UI 层辅助判断充电阶段
    private static final int MAX_SAMPLES = 20;
    private final LinkedList<PowerSample> samples = new LinkedList<>();

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
        String message = getString(R.string.error_view_load_failed, e.getClass().getSimpleName(), e.getMessage());
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_label));
        errorView.setTextSize(16);
        errorView.setPadding(40, 100, 40, 40);
        errorView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_background));
        return errorView;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            mainHandler = new Handler(Looper.getMainLooper());
            executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("power-io"));

            tvPower = view.findViewById(R.id.tv_power);
            tvVoltage = view.findViewById(R.id.tv_voltage);
            tvCurrent = view.findViewById(R.id.tv_current);
            tvChargeType = view.findViewById(R.id.tv_charge_type);
            tvChargeProtocol = view.findViewById(R.id.tv_charge_protocol);
            tvBatteryLevel = view.findViewById(R.id.tv_power_battery_level);
            tvChargingPhase = view.findViewById(R.id.tv_charging_phase);
            tvBatteryTemp = view.findViewById(R.id.tv_power_battery_temp);
            progressCharge = view.findViewById(R.id.progress_charge);
            powerTypeDot = view.findViewById(R.id.power_type_dot);
            tvTodayCount = view.findViewById(R.id.tv_today_count);
            tvTodayAvgPower = view.findViewById(R.id.tv_today_avg_power);
            tvTodayDuration = view.findViewById(R.id.tv_today_duration);
            tvTodayInput = view.findViewById(R.id.tv_today_input);

            // 设置默认值
            setDefaultValues();
            startPowerTypeDotAnimation();
            updatePowerData();
            animateCardsEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }
    
    private void animateCardsEntry(View view) {
        UiAnimationHelper.animateCardsEntry(view);
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
    
    private void setDefaultValues() {
        if (tvPower != null) tvPower.setText("0.0 W");
        if (tvVoltage != null) tvVoltage.setText("0.00 V");
        if (tvCurrent != null) tvCurrent.setText("0.00 A");
        if (tvChargeType != null) tvChargeType.setText(getString(R.string.status_not_charging_short));
        if (tvChargeProtocol != null) tvChargeProtocol.setText(getString(R.string.status_detecting_protocol));
        if (tvBatteryLevel != null) tvBatteryLevel.setText("--%");
        if (tvChargingPhase != null) tvChargingPhase.setText("--");
        if (tvBatteryTemp != null) tvBatteryTemp.setText("--°C");
        if (progressCharge != null) progressCharge.setProgress(0);
        if (tvTodayCount != null) tvTodayCount.setText("--");
        if (tvTodayAvgPower != null) tvTodayAvgPower.setText("--");
        if (tvTodayDuration != null) tvTodayDuration.setText("--");
        if (tvTodayInput != null) tvTodayInput.setText("--");
    }
    
    private void updatePowerData() {
        if (executor == null || executor.isShutdown()) return;

        executor.submit(() -> {
            try {
                final float voltage = readVoltage();
                final float current = readCurrent();
                final float power = voltage * current;
                final int level = readBatteryLevel();
                final float temperature = readBatteryTemperature();
                final TodayChargingStats todayStats = computeTodayChargingStats();

                addSample(voltage, current, power, level);

                if (mainHandler != null) {
                    mainHandler.post(() -> updatePowerUi(voltage, current, power, level, temperature, todayStats));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating power data: " + e.getMessage());
            }
        });
    }

    private void updatePowerUi(float voltage, float current, float power, int level, float temperature,
                               TodayChargingStats todayStats) {
        if (!isAdded()) return;
        try {
            if (tvVoltage != null) {
                tvVoltage.setText(String.format(Locale.getDefault(), "%.2f V", voltage));
            }
            if (tvCurrent != null) {
                tvCurrent.setText(String.format(Locale.getDefault(), "%.2f A", current));
            }
            if (tvPower != null) {
                tvPower.setText(String.format(Locale.getDefault(), "%.1f W", power));
            }
            if (tvChargeType != null) {
                String chargeType = getChargeTypeDescription(power);
                tvChargeType.setText(chargeType);
            }
            if (progressCharge != null) {
                progressCharge.setProgress(level);
            }
            if (powerTypeDot != null) {
                powerTypeDot.setVisibility(power > 0 ? View.VISIBLE : View.GONE);
            }

            // 充电协议识别（基于系统属性 + 厂商 + 实时功率）
            if (tvChargeProtocol != null) {
                Context ctx = getContext();
                if (ctx == null) {
                    tvChargeProtocol.setText(getString(R.string.status_detecting_protocol));
                } else if (power <= 0) {
                    tvChargeProtocol.setText(getString(R.string.charge_protocol_standard));
                } else {
                    try {
                        ChargeProtocolDetector.Result pr = ChargeProtocolDetector.detect(ctx, power);
                        StringBuilder protocolText = new StringBuilder();
                        protocolText.append(pr.primary);
                        if (pr.detail != null && !pr.detail.isEmpty()) {
                            protocolText.append(" · ").append(pr.detail);
                        }
                        tvChargeProtocol.setText(protocolText.toString());
                    } catch (Throwable t) {
                        Log.w(TAG, "detect charge protocol failed: " + t.getMessage());
                        tvChargeProtocol.setText(getString(R.string.status_detecting_protocol));
                    }
                }
            }

            if (tvBatteryLevel != null) {
                tvBatteryLevel.setText(level + "%");
            }

            if (tvChargingPhase != null) {
                if (power > 0) {
                    tvChargingPhase.setText(detectChargingPhase(level, power));
                } else {
                    tvChargingPhase.setText(getString(R.string.status_not_charging_short));
                }
            }

            if (tvBatteryTemp != null) {
                if (temperature > -100) {
                    tvBatteryTemp.setText(String.format(Locale.getDefault(), "%.1f°C", temperature));
                } else {
                    tvBatteryTemp.setText("--°C");
                }
            }

            if (todayStats != null) {
                if (tvTodayCount != null) tvTodayCount.setText(todayStats.count >= 0 ? String.valueOf(todayStats.count) : "--");
                if (tvTodayAvgPower != null) tvTodayAvgPower.setText(todayStats.avgPower > 0 ? String.format(Locale.getDefault(), "%.1f W", todayStats.avgPower) : "--");
                if (tvTodayDuration != null) tvTodayDuration.setText(todayStats.durationText != null ? todayStats.durationText : "--");
                if (tvTodayInput != null) tvTodayInput.setText(todayStats.totalInputText != null ? todayStats.totalInputText : "--");
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

    private void addSample(float voltage, float current, float power, int level) {
        long now = System.currentTimeMillis();
        samples.addLast(new PowerSample(now, voltage, current, power, level));
        while (samples.size() > MAX_SAMPLES) {
            samples.removeFirst();
        }
    }

    /**
     * 充电阶段检测：基于电量和电流变化趋势综合判断。
     * CC（恒流）：电流稳定，电压上升，通常 0-80%。
     * CV（恒压）：电流逐渐下降，电压稳定，通常 80-100%。
     * Trickle（涓流）：电流很小，接近充满。
     * 安兔兔/AccuBattery 采用类似的 CC/CV/Trickle 三阶段模型。
     */
    private String detectChargingPhase(int level, float power) {
        if (level >= 99) return getString(R.string.status_fully_charged);

        // 基于滑动窗口的电流趋势判断
        if (samples.size() >= 8) {
            PowerSample first = samples.getFirst();
            PowerSample last = samples.getLast();
            long timeDiff = last.time - first.time;
            if (timeDiff > 10_000) { // 至少 10 秒的采样窗口
                float hours = timeDiff / (1000.0f * 60 * 60);
                float didt = (last.current - first.current) / hours;

                // 电流明显下降 → CV 阶段
                if (level >= 70 && didt < -0.3f) {
                    return getString(R.string.charge_phase_constant_voltage);
                }
                // 电流稳定 → CC 阶段
                if (power > 5 && Math.abs(didt) < 0.5f) {
                    return getString(R.string.charge_phase_constant_current);
                }
            }
        }

        // 采样不足时基于电量和功率判断
        if (level >= 80) return getString(R.string.charge_phase_constant_voltage);
        if (power >= 5) return getString(R.string.charge_phase_constant_current);
        return getString(R.string.charge_phase_trickle);
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
        // 统一使用 BatteryDataManager 读取电压，避免重复实现单位判断逻辑
        if (getActivity() instanceof MainActivity) {
            com.batteryhealth.app.utils.BatteryDataManager bdm =
                    ((MainActivity) getActivity()).getBatteryDataManager();
            if (bdm != null) {
                int voltageMv = bdm.readVoltageNow();
                if (voltageMv > 0) {
                    return voltageMv / 1000.0f; // mV → V
                }
            }
        }

        // 兜底：直接读取 sysfs 多路径
        String[] voltagePaths = {
                "/sys/class/power_supply/battery/voltage_now",
                "/sys/class/power_supply/bms/voltage_now",
                "/sys/class/power_supply/maxfg/voltage_now"
        };
        for (String path : voltagePaths) {
            File voltageFile = new File(path);
            if (voltageFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(voltageFile))) {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        long raw = Long.parseLong(line.trim());
                        if (Math.abs(raw) > 1000000) {
                            return Math.abs(raw) / 1000000.0f; // µV → V
                        } else if (Math.abs(raw) > 2500) {
                            return Math.abs(raw) / 1000.0f; // mV → V
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error reading voltage from sysfs: " + path + " " + e.getMessage());
                }
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
                    if (voltageMv > 10000) voltageMv = voltageMv / 1000; // µV → mV
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
        // 统一使用 BatteryDataManager 读取电流，避免重复实现单位判断逻辑
        if (getActivity() instanceof MainActivity) {
            com.batteryhealth.app.utils.BatteryDataManager bdm =
                    ((MainActivity) getActivity()).getBatteryDataManager();
            if (bdm != null) {
                BatteryManager bm = (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
                int currentMa = bdm.readCurrentNow(bm);
                // readCurrentNow 返回 mA，正值充电，负值放电；功率页需要绝对值
                return Math.abs(currentMa) / 1000.0f; // mA → A
            }
        }

        // 兜底：直接读取 sysfs 多路径
        String[] currentPaths = {
                "/sys/class/power_supply/battery/current_now",
                "/sys/class/power_supply/bms/current_now",
                "/sys/class/power_supply/maxfg/current_now"
        };
        for (String path : currentPaths) {
            File currentFile = new File(path);
            if (currentFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(currentFile))) {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        long raw = Long.parseLong(line.trim());
                        long absRaw = Math.abs(raw);
                        if (absRaw > 100000) {
                            return absRaw / 1000000.0f; // µA → A
                        }
                        return absRaw / 1000.0f; // mA → A
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error reading current from sysfs: " + path + " " + e.getMessage());
                }
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

        if (power <= 0) return getString(R.string.status_not_charging_short);

        // 基于机型数据库官方快充功率判断，更准确
        if (officialPower > 0) {
            if (power >= officialPower * 0.6f && power >= 60) return getString(R.string.charge_power_ultra_fast);
            if (power >= officialPower * 0.5f && power >= 30) return getString(R.string.charge_power_fast);
            if (power >= officialPower * 0.25f && power >= 10) return getString(R.string.charge_power_standard);
            return getString(R.string.charge_power_slow);
        }

        // 通用阈值兜底
        if (power >= 60) return getString(R.string.charge_power_ultra_fast);
        if (power >= 30) return getString(R.string.charge_power_fast);
        if (power >= 10) return getString(R.string.charge_power_standard);
        return getString(R.string.charge_power_slow);
    }

    private static class TodayChargingStats {
        int count;
        float avgPower;
        String durationText;
        String totalInputText;
    }

    private TodayChargingStats computeTodayChargingStats() {
        TodayChargingStats stats = new TodayChargingStats();
        stats.count = -1;
        try {
            BatteryHealthApplication app = BatteryHealthApplication.getInstance();
            if (app == null) return stats;
            AppDatabase db = app.getDatabase();
            if (db == null) return stats;

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            long startOfDay = cal.getTimeInMillis();
            long now = System.currentTimeMillis();

            java.util.List<PowerHistory> records = db.powerHistoryDao().getBetween(startOfDay, now);
            if (records == null || records.isEmpty()) {
                stats.count = 0;
                return stats;
            }

            // 按 session 分组统计
            java.util.Map<String, java.util.List<PowerHistory>> sessions = new java.util.HashMap<>();
            for (PowerHistory r : records) {
                if (r == null) continue;
                String sid = r.getSessionId();
                if (sid == null || sid.isEmpty()) sid = "default";
                java.util.List<PowerHistory> list = sessions.get(sid);
                if (list == null) {
                    list = new java.util.ArrayList<>();
                    sessions.put(sid, list);
                }
                list.add(r);
            }

            int sessionCount = sessions.size();
            float totalPower = 0;
            int powerSamples = 0;
            long totalDurationMs = 0;
            float totalInputMah = 0;

            for (java.util.List<PowerHistory> list : sessions.values()) {
                if (list == null || list.isEmpty()) continue;
                PowerHistory first = list.get(0);
                PowerHistory last = list.get(list.size() - 1);
                long durationMs = last.getTimestamp() - first.getTimestamp();
                if (durationMs < 0) durationMs = 0;
                totalDurationMs += durationMs;

                for (PowerHistory r : list) {
                    if (r == null) continue;
                    if (r.getPower() > 0) {
                        totalPower += r.getPower();
                        powerSamples++;
                    }
                    // 估算充入电量：功率(W) * 时间(s) / 电压(V) ≈ mAh
                    // 由于采样间隔未知，按每条记录 60 秒估算；这里仅做粗略展示
                    float voltage = r.getVoltage() > 0 ? r.getVoltage() : 3.8f;
                    totalInputMah += (r.getPower() * 60f) / voltage;
                }
            }

            stats.count = sessionCount;
            stats.avgPower = powerSamples > 0 ? totalPower / powerSamples : 0;

            if (totalDurationMs < 60_000) {
                stats.durationText = String.format(Locale.getDefault(), "%d 秒", totalDurationMs / 1000);
            } else if (totalDurationMs < 60 * 60_000) {
                stats.durationText = String.format(Locale.getDefault(), "%d 分钟", totalDurationMs / 60_000);
            } else {
                stats.durationText = String.format(Locale.getDefault(), "%.1f 小时", totalDurationMs / (60f * 60_000));
            }

            if (totalInputMah >= 1000) {
                stats.totalInputText = String.format(Locale.getDefault(), "%.2f Ah", totalInputMah / 1000f);
            } else {
                stats.totalInputText = String.format(Locale.getDefault(), "%.0f mAh", totalInputMah);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error computing today charging stats: " + e.getMessage());
        }
        return stats;
    }

    private void startPowerTypeDotAnimation() {
        if (powerTypeDot == null) return;
        try {
            stopPowerTypeDotAnimation();
            ObjectAnimator scaleXOut = ObjectAnimator.ofFloat(powerTypeDot, "scaleX", 1f, 1.4f);
            ObjectAnimator scaleYOut = ObjectAnimator.ofFloat(powerTypeDot, "scaleY", 1f, 1.4f);
            ObjectAnimator alphaOut = ObjectAnimator.ofFloat(powerTypeDot, "alpha", 1f, 0.5f);
            AnimatorSet out = new AnimatorSet();
            out.playTogether(scaleXOut, scaleYOut, alphaOut);
            out.setDuration(700);

            ObjectAnimator scaleXIn = ObjectAnimator.ofFloat(powerTypeDot, "scaleX", 1.4f, 1f);
            ObjectAnimator scaleYIn = ObjectAnimator.ofFloat(powerTypeDot, "scaleY", 1.4f, 1f);
            ObjectAnimator alphaIn = ObjectAnimator.ofFloat(powerTypeDot, "alpha", 0.5f, 1f);
            AnimatorSet in = new AnimatorSet();
            in.playTogether(scaleXIn, scaleYIn, alphaIn);
            in.setDuration(700);

            powerTypeDotAnimator = new AnimatorSet();
            powerTypeDotAnimator.playSequentially(out, in);
            powerTypeDotAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (powerTypeDotAnimator != null) {
                        powerTypeDotAnimator.start();
                    }
                }
            });
            powerTypeDotAnimator.start();
        } catch (Exception e) {
            Log.e(TAG, "Error starting power type dot animation: " + e.getMessage());
        }
    }

    private void stopPowerTypeDotAnimation() {
        try {
            if (powerTypeDotAnimator != null) {
                powerTypeDotAnimator.removeAllListeners();
                powerTypeDotAnimator.cancel();
                powerTypeDotAnimator = null;
            }
            if (powerTypeDot != null) {
                powerTypeDot.setAlpha(1f);
                powerTypeDot.setScaleX(1f);
                powerTypeDot.setScaleY(1f);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping power type dot animation: " + e.getMessage());
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
        stopPowerTypeDotAnimation();
    }

    /**
     * 命名线程工厂，用于为线程池中的线程设置可读名称与未捕获异常处理器。
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            t.setUncaughtExceptionHandler((thread, ex) -> {
                Log.e("NamedThreadFactory", "Uncaught exception in thread " + thread.getName(), ex);
            });
            return t;
        }
    }
}