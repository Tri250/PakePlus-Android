package com.batteryhealth.app.ui.power;

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
import android.widget.TextView;

import java.util.Locale;

import java.util.LinkedList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.DeviceDatabaseManager;

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
    private TextView tvBatteryLevel;
    private TextView tvChargingPhase;
    private TextView tvBatteryTemp;

    private Handler mainHandler;
    private ExecutorService executor;
    private boolean isRunning = false;

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
            tvBatteryLevel = view.findViewById(R.id.tv_power_battery_level);
            tvChargingPhase = view.findViewById(R.id.tv_charging_phase);
            tvBatteryTemp = view.findViewById(R.id.tv_power_battery_temp);
            
            // 设置默认值
            setDefaultValues();
            updatePowerData();
            animateCardsEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }
    
    private static final String PREFS_GLOBAL = "app_global_prefs";
    private static final String PREF_DISABLE_ANIMATIONS = "disable_animations";

    private boolean shouldSkipAnimations() {
        try {
            Context ctx = requireContext();
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_GLOBAL, Context.MODE_PRIVATE);
            if (prefs.getBoolean(PREF_DISABLE_ANIMATIONS, false)) {
                return true;
            }
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long totalMemGb = mi.totalMem / (1024L * 1024L * 1024L);
                if (totalMemGb < 4) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Animation check skipped: " + e.getMessage());
        }
        return false;
    }

    private void animateCardsEntry(View view) {
        try {
            if (shouldSkipAnimations()) return;
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
                    .setDuration(300)
                    .setStartDelay(i * 60L)
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
        if (tvPower != null) tvPower.setText("0.0 W");
        if (tvVoltage != null) tvVoltage.setText("0.00 V");
        if (tvCurrent != null) tvCurrent.setText("0.00 A");
        if (tvChargeType != null) tvChargeType.setText(getString(R.string.status_not_charging_short));
        if (tvBatteryLevel != null) tvBatteryLevel.setText("--%");
        if (tvChargingPhase != null) tvChargingPhase.setText("--");
        if (tvBatteryTemp != null) tvBatteryTemp.setText("--°C");
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

                addSample(voltage, current, power, level);

                if (mainHandler != null) {
                    mainHandler.post(() -> updatePowerUi(voltage, current, power, level, temperature));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating power data: " + e.getMessage());
            }
        });
    }

    private void updatePowerUi(float voltage, float current, float power, int level, float temperature) {
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
                String phase = detectChargingPhase(level, power);
                if (power > 0) {
                    tvChargeType.setText(chargeType + " · " + phase);
                } else {
                    tvChargeType.setText(chargeType);
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

    private String detectChargingPhase(int level, float power) {
        if (level >= 99) return getString(R.string.status_fully_charged);

        if (samples.size() >= 8) {
            PowerSample first = samples.getFirst();
            PowerSample last = samples.getLast();
            long timeDiff = last.time - first.time;
            if (timeDiff > 8_000) {
                float hours = timeDiff / (1000.0f * 60 * 60);
                float didt = (last.current - first.current) / hours;
                float dvdt = (last.voltage - first.voltage) / hours;

                if (level >= 75 && didt < -0.3f && Math.abs(dvdt) < 0.05f) {
                    return getString(R.string.charge_phase_constant_voltage);
                }
                if (power > 5 && Math.abs(didt) < 0.5f && dvdt > 0.01f) {
                    return getString(R.string.charge_phase_constant_current);
                }
            }
        }

        if (level >= 80) return getString(R.string.charge_phase_constant_voltage);
        if (power > 5) return getString(R.string.charge_phase_constant_current);
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
                        int absCurrent = Math.abs(currentUa);
                        // 部分设备返回 mA 而非 µA，需判断单位
                        // 正常充电电流：500mA-10A = 500000-10000000 µA
                        if (absCurrent > 100000) {
                            return absCurrent / 1000000.0f; // µA → A
                        } else if (absCurrent > 0) {
                            return absCurrent / 1000.0f; // mA → A
                        }
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