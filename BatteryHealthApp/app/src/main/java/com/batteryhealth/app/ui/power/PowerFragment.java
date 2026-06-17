package com.batteryhealth.app.ui.power;

import android.content.BroadcastReceiver;
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
 */
public class PowerFragment extends Fragment {
    
    private static final String TAG = "PowerFragment";
    
    private TextView tvPower;
    private TextView tvVoltage;
    private TextView tvCurrent;
    private TextView tvChargeType;
    
    private Handler mainHandler;
    private boolean isReceiverRegistered = false;
    
    private BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && mainHandler != null) {
                mainHandler.post(() -> updatePowerData());
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
            Log.e(TAG, "Error inflating layout: " + e.getMessage());
            View errorView = new View(requireContext());
            return errorView;
        }
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            mainHandler = new Handler(Looper.getMainLooper());
            
            tvPower = view.findViewById(R.id.tv_power);
            tvVoltage = view.findViewById(R.id.tv_power_voltage);
            tvCurrent = view.findViewById(R.id.tv_power_current);
            tvChargeType = view.findViewById(R.id.tv_charge_type);
            
            // 设置默认值
            setDefaultValues();
            
            registerPowerReceiver();
            updatePowerData();
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }
    
    private void setDefaultValues() {
        if (tvPower != null) tvPower.setText("0.0 W");
        if (tvVoltage != null) tvVoltage.setText("0.00 V");
        if (tvCurrent != null) tvCurrent.setText("0.00 A");
        if (tvChargeType != null) tvChargeType.setText("未充电");
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            if (isReceiverRegistered && getContext() != null) {
                getContext().unregisterReceiver(powerReceiver);
                isReceiverRegistered = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver: " + e.getMessage());
        }
    }
    
    private void registerPowerReceiver() {
        try {
            if (getContext() != null) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                filter.addAction(Intent.ACTION_POWER_CONNECTED);
                filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
                
                // Android 14+ 需要指定导出标志
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    getContext().registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    getContext().registerReceiver(powerReceiver, filter);
                }
                isReceiverRegistered = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering power receiver: " + e.getMessage());
        }
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
                Intent batteryStatus = getContext().registerReceiver(null, filter);
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