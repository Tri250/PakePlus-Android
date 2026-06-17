package com.batteryhealth.app.ui.power;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
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
    
    private TextView tvPower;
    private TextView tvVoltage;
    private TextView tvCurrent;
    private TextView tvChargeType;
    
    private BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updatePowerData();
        }
    };
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_power, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        tvPower = view.findViewById(R.id.tv_power);
        tvVoltage = view.findViewById(R.id.tv_voltage);
        tvCurrent = view.findViewById(R.id.tv_current);
        tvChargeType = view.findViewById(R.id.tv_charge_type);
        
        registerPowerReceiver();
        updatePowerData();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getContext() != null) {
            getContext().unregisterReceiver(powerReceiver);
        }
    }
    
    private void registerPowerReceiver() {
        if (getContext() != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            getContext().registerReceiver(powerReceiver, filter);
        }
    }
    
    private void updatePowerData() {
        float voltage = readVoltage();
        float current = readCurrent();
        float power = voltage * current;
        
        tvVoltage.setText(String.format("%.2f V", voltage));
        tvCurrent.setText(String.format("%.2f A", current));
        tvPower.setText(String.format("%.1f W", power));
        tvChargeType.setText(getChargeTypeDescription(power));
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