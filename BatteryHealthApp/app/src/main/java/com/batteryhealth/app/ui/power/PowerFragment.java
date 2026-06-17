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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    
    private Handler mainHandler;
    private boolean isRunning = false;
    
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
            return createErrorView("界面加载失败，请重启应用");
        }
    }

    private View createErrorView(String message) {
        android.widget.TextView errorView = new android.widget.TextView(requireContext());
        errorView.setText(message);
        errorView.setTextColor(0xFF000000);
        errorView.setTextSize(16);
        errorView.setPadding(40, 100, 40, 40);
        errorView.setBackgroundColor(0xFFF2F2F7);
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
            
            // 设置默认值
            setDefaultValues();
            updatePowerData();
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage(), e);
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
    }
    
    private void updatePowerData() {
        try {
            float voltage = readVoltage();
            float current = readCurrent();
            float power = voltage * current;
            
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
                tvChargeType.setText(getChargeTypeDescription(power));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating power data: " + e.getMessage());
        }
    }
    
    private float readVoltage() {
        try {
            File voltageFile = new File("/sys/class/power_supply/battery/voltage_now");
            if (voltageFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(voltageFile));
                String line = reader.readLine();
                reader.close();
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
            e.printStackTrace();
        }
        return 0;
    }
    
    private float readCurrent() {
        try {
            File currentFile = new File("/sys/class/power_supply/battery/current_now");
            if (currentFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(currentFile));
                String line = reader.readLine();
                reader.close();
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
            e.printStackTrace();
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