package com.batteryhealth.app.ui.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.AppManager;
import com.batteryhealth.app.utils.BatteryDataManager;

public class BatteryHealthFragment extends Fragment {
    
    private static final String TAG = "BatteryHealthFragment";
    
    private TextView tvHealthPercentage;
    private TextView tvHealthGrade;
    private TextView tvHealthStatus;
    private ProgressBar progressHealth;
    
    private TextView tvCapacity;
    private TextView tvCycleCount;
    private TextView tvTemperature;
    private TextView tvVoltage;
    private TextView tvBatterySource;
    private TextView tvTechnology;
    
    private BatteryDataManager batteryDataManager;
    private Handler mainHandler;
    private boolean isReceiverRegistered = false;
    
    private final Runnable dataChangeListener = this::updateUI;
    
    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && isAdded() && !isDetached()) {
                updateUI();
            }
        }
    };
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_battery_health, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage());
            return new View(requireContext());
        }
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        mainHandler = new Handler(Looper.getMainLooper());
        batteryDataManager = AppManager.getInstance().getBatteryDataManager();
        
        initViews(view);
        registerBatteryReceiver();
        
        // 监听数据变化
        AppManager.getInstance().addDataChangeListener(dataChangeListener);
        
        updateUI();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        batteryDataManager = AppManager.getInstance().getBatteryDataManager();
        updateUI();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // 保留监听，因为Fragment可能在ViewPager中只是不可见
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        AppManager.getInstance().removeDataChangeListener(dataChangeListener);
        try {
            if (isReceiverRegistered && getContext() != null) {
                getContext().unregisterReceiver(batteryReceiver);
                isReceiverRegistered = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver: " + e.getMessage());
        }
    }
    
    private void initViews(View view) {
        tvHealthPercentage = view.findViewById(R.id.tv_health_percentage);
        tvHealthGrade = view.findViewById(R.id.tv_health_grade);
        tvHealthStatus = view.findViewById(R.id.tv_health_status);
        progressHealth = view.findViewById(R.id.progress_health);
        tvCapacity = view.findViewById(R.id.tv_capacity);
        tvCycleCount = view.findViewById(R.id.tv_cycle_count);
        tvTemperature = view.findViewById(R.id.tv_temperature);
        tvVoltage = view.findViewById(R.id.tv_voltage);
        tvBatterySource = view.findViewById(R.id.tv_battery_source);
        tvTechnology = view.findViewById(R.id.tv_technology);
        setDefaultValues();
    }
    
    private void setDefaultValues() {
        try {
            if (tvHealthPercentage != null) tvHealthPercentage.setText("--");
            if (tvHealthGrade != null) tvHealthGrade.setText("--");
            if (tvHealthStatus != null) tvHealthStatus.setText("正在检测...");
            if (tvCapacity != null) tvCapacity.setText("-- mAh");
            if (tvCycleCount != null) tvCycleCount.setText("-- 次");
            if (tvTemperature != null) tvTemperature.setText("-- °C");
            if (tvVoltage != null) tvVoltage.setText("-- mV");
            if (tvBatterySource != null) tvBatterySource.setText("检测中");
            if (tvTechnology != null) tvTechnology.setText("--");
        } catch (Exception e) {
            Log.e(TAG, "Error setting default values: " + e.getMessage());
        }
    }
    
    private void registerBatteryReceiver() {
        try {
            if (getContext() != null && !isReceiverRegistered) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    getContext().registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    getContext().registerReceiver(batteryReceiver, filter);
                }
                isReceiverRegistered = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering battery receiver: " + e.getMessage());
        }
    }
    
    private void updateUI() {
        if (mainHandler == null || getView() == null) return;
        
        // 每次都重新从单例获取，确保拿到最新的
        batteryDataManager = AppManager.getInstance().getBatteryDataManager();
        if (batteryDataManager == null) return;
        
        mainHandler.post(() -> {
            try {
                BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
                if (info == null) return;
                
                float healthPercentage = info.getHealthPercentage();
                if (tvHealthPercentage != null) {
                    tvHealthPercentage.setText(String.format("%.1f%%", healthPercentage));
                }
                if (tvHealthGrade != null) {
                    tvHealthGrade.setText(info.getHealthGrade());
                }
                if (tvHealthStatus != null) {
                    tvHealthStatus.setText(info.getHealthDescription());
                }
                if (progressHealth != null) {
                    progressHealth.setProgress((int) healthPercentage);
                }
                if (tvHealthPercentage != null && progressHealth != null) {
                    int healthColor = getHealthColor(healthPercentage);
                    tvHealthPercentage.setTextColor(healthColor);
                    try {
                        progressHealth.getProgressDrawable().setColorFilter(healthColor, android.graphics.PorterDuff.Mode.SRC_IN);
                    } catch (Exception ignored) {}
                }
                if (tvCapacity != null) {
                    tvCapacity.setText(String.format("%d / %d mAh", 
                            info.getCurrentCapacity(), info.getDesignCapacity()));
                }
                if (tvCycleCount != null) {
                    tvCycleCount.setText(String.format("%d 次", info.getCycleCount()));
                }
                if (tvTemperature != null) {
                    tvTemperature.setText(String.format("%.1f°C", info.getTemperature()));
                }
                if (tvVoltage != null) {
                    tvVoltage.setText(String.format("%.0f mV", info.getVoltage()));
                }
                if (tvBatterySource != null) {
                    tvBatterySource.setText(batteryDataManager.getBatterySourceText());
                }
                if (tvTechnology != null) {
                    String tech = info.getTechnology();
                    tvTechnology.setText(tech != null && !tech.isEmpty() ? tech : "Li-ion");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating UI: " + e.getMessage());
            }
        });
    }
    
    private int getHealthColor(float percentage) {
        try {
            if (percentage >= 90) {
                return ContextCompat.getColor(requireContext(), R.color.health_a_plus);
            } else if (percentage >= 80) {
                return ContextCompat.getColor(requireContext(), R.color.health_a);
            } else if (percentage >= 70) {
                return ContextCompat.getColor(requireContext(), R.color.health_c);
            } else if (percentage >= 60) {
                return ContextCompat.getColor(requireContext(), R.color.health_d);
            } else {
                return ContextCompat.getColor(requireContext(), R.color.health_e);
            }
        } catch (Exception e) {
            return 0xFF34C759;
        }
    }
}
