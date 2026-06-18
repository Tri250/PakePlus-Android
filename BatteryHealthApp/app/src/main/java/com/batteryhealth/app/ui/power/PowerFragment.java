package com.batteryhealth.app.ui.power;

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

import java.util.LinkedList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

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

            // 检查Fragment是否仍然附加到Activity
            if (!isAdded() || isDetached() || getContext() == null) {
                isRunning = false;
                return;
            }

            updatePowerData();
            if (mainHandler != null && isAdded()) {
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
        StringBuilder message = new StringBuilder();
        message.append("界面加载失败，请重启应用\n\n");
        message.append("错误类型: ").append(e.getClass().getSimpleName()).append("\n");
        message.append("错误信息: ").append(e.getMessage() != null ? e.getMessage() : "未知错误").append("\n\n");
        message.append("堆栈跟踪:\n");
        for (StackTraceElement element : e.getStackTrace()) {
            message.append(element.toString()).append("\n");
        }
        errorView.setText(message.toString());
        errorView.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_label));
        errorView.setTextSize(14);
        errorView.setPadding(40, 100, 40, 40);
        errorView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_background));
        return errorView;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            mainHandler = new Handler(Looper.getMainLooper());
            
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
    
    private void animateCardsEntry(View view) {
        try {
            if (view == null || !(view instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup root = (android.view.ViewGroup) view;
            animateViewGroupRecursive(root, 0);
        } catch (Exception e) {
            Log.d(TAG, "Liquid glass card animation skipped: " + e.getMessage());
        }
    }

    private void animateViewGroupRecursive(android.view.ViewGroup parent, int depth) {
        if (parent == null) return;
        if (depth > 4) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == null) continue;
            if (child.getId() == R.id.view_pager) continue;
            boolean shouldAnimate = (child instanceof com.google.android.material.card.MaterialCardView)
                    || depth == 1
                    || (depth == 0 && parent.getChildCount() > 1);
            if (shouldAnimate) {
                try {
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
                } catch (Exception ignored) {}
            }
            if (child instanceof android.view.ViewGroup) {
                animateViewGroupRecursive((android.view.ViewGroup) child, depth + 1);
            }
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
    }
    
    private void setDefaultValues() {
        if (tvPower != null) tvPower.setText("0.0 W");
        if (tvVoltage != null) tvVoltage.setText("0.00 V");
        if (tvCurrent != null) tvCurrent.setText("0.00 A");
        if (tvChargeType != null) tvChargeType.setText("未充电");
        if (tvBatteryLevel != null) tvBatteryLevel.setText("--%");
        if (tvChargingPhase != null) tvChargingPhase.setText("--");
        if (tvBatteryTemp != null) tvBatteryTemp.setText("--°C");
    }
    
    private void updatePowerData() {
        try {
            float voltage = readVoltage();
            float current = readCurrent();
            float power = voltage * current;
            int level = readBatteryLevel();

            // 记录样本用于阶段判断
            addSample(voltage, current, power, level);

            if (tvVoltage != null) {
                tvVoltage.setText(String.format("%.2f V", voltage));
            }
            if (tvCurrent != null) {
                tvCurrent.setText(String.format("%.2f A", current));
            }
            if (tvPower != null) {
                tvPower.setText(String.format("%.1f W", power));
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

            // 更新电池电量
            if (tvBatteryLevel != null) {
                tvBatteryLevel.setText(level + "%");
            }

            // 更新充电阶段
            if (tvChargingPhase != null) {
                if (power > 0) {
                    tvChargingPhase.setText(detectChargingPhase(level, power));
                } else {
                    tvChargingPhase.setText("未充电");
                }
            }

            // 更新电池温度
            if (tvBatteryTemp != null) {
                try {
                    IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent batteryStatus;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        batteryStatus = getContext().registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED);
                    } else {
                        batteryStatus = getContext().registerReceiver(null, filter);
                    }
                    if (batteryStatus != null) {
                        int temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                        if (temp != -1) {
                            tvBatteryTemp.setText(String.format("%.1f°C", temp / 10.0f));
                        }
                    }
                } catch (Exception e) {
                    tvBatteryTemp.setText("--°C");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating power data: " + e.getMessage());
        }
    }

    private void addSample(float voltage, float current, float power, int level) {
        long now = System.currentTimeMillis();
        samples.addLast(new PowerSample(now, voltage, current, power, level));
        while (samples.size() > MAX_SAMPLES) {
            samples.removeFirst();
        }
    }

    private String detectChargingPhase(int level, float power) {
        if (level >= 99) return "已充满";

        if (samples.size() >= 8) {
            PowerSample first = samples.getFirst();
            PowerSample last = samples.getLast();
            long timeDiff = last.time - first.time;
            if (timeDiff > 8_000) {
                float hours = timeDiff / (1000.0f * 60 * 60);
                float didt = (last.current - first.current) / hours;
                float dvdt = (last.voltage - first.voltage) / hours;

                if (level >= 75 && didt < -0.3f && Math.abs(dvdt) < 0.05f) {
                    return "恒压充电";
                }
                if (power > 5 && Math.abs(didt) < 0.5f && dvdt > 0.01f) {
                    return "恒流充电";
                }
            }
        }

        if (level >= 80) return "恒压充电";
        if (power > 5) return "恒流充电";
        return "涓流充电";
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
        BufferedReader reader = null;
        try {
            File voltageFile = new File("/sys/class/power_supply/battery/voltage_now");
            if (voltageFile.exists()) {
                reader = new BufferedReader(new FileReader(voltageFile));
                String line = reader.readLine();
                if (line != null) {
                    return Long.parseLong(line.trim()) / 1000000.0f;
                }
            }

            if (getContext() != null) {
                IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    batteryStatus = getContext().registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    batteryStatus = getContext().registerReceiver(null, filter);
                }
                if (batteryStatus != null) {
                    int voltageMv = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                    if (voltageMv != -1) {
                        return voltageMv / 1000.0f;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading voltage: " + e.getMessage());
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private float readCurrent() {
        BufferedReader reader = null;
        try {
            File currentFile = new File("/sys/class/power_supply/battery/current_now");
            if (currentFile.exists()) {
                reader = new BufferedReader(new FileReader(currentFile));
                String line = reader.readLine();
                if (line != null) {
                    return Math.abs(Long.parseLong(line.trim())) / 1000000.0f;
                }
            }

            if (getContext() != null) {
                BatteryManager batteryManager = (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
                if (batteryManager != null) {
                    int currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                    return Math.abs(currentUa) / 1000000.0f;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading current: " + e.getMessage());
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
        return 0;
    }
    
    private String getChargeTypeDescription(float power) {
        if (power >= 60) {
            return "超快闪充";
        } else if (power >= 40) {
            return "超级快充";
        } else if (power >= 18) {
            return "快速充电";
        } else if (power >= 10) {
            return "标准充电";
        } else if (power > 0) {
            return "慢速充电";
        } else {
            return "未充电";
        }
    }
}