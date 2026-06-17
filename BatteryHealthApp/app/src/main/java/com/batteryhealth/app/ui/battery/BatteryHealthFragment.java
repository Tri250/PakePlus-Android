package com.batteryhealth.app.ui.battery;

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
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.BatteryDataManager;

/**
 * 电池健康Fragment
 * 
 * 功能：
 * 1. 显示电池健康度
 * 2. 显示电池容量
 * 3. 显示循环次数
 * 4. 显示电池温度
 * 5. 显示电池来源
 */
public class BatteryHealthFragment extends Fragment {
    
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
    
    private CardView cardHealth;
    private CardView cardDetails;
    
    private BatteryDataManager batteryDataManager;
    private Handler mainHandler;
    
    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateUI();
        }
    };
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_battery_health, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        mainHandler = new Handler(Looper.getMainLooper());
        
        // 获取电池数据管理器
        if (getActivity() instanceof MainActivity) {
            batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
        }
        
        initViews(view);
        registerBatteryReceiver();
        updateUI();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getContext() != null) {
            getContext().unregisterReceiver(batteryReceiver);
        }
    }
    
    private void initViews(View view) {
        // 健康度相关
        tvHealthPercentage = view.findViewById(R.id.tv_health_percentage);
        tvHealthGrade = view.findViewById(R.id.tv_health_grade);
        tvHealthStatus = view.findViewById(R.id.tv_health_status);
        progressHealth = view.findViewById(R.id.progress_health);
        
        // 详细信息
        tvCapacity = view.findViewById(R.id.tv_capacity);
        tvCycleCount = view.findViewById(R.id.tv_cycle_count);
        tvTemperature = view.findViewById(R.id.tv_temperature);
        tvVoltage = view.findViewById(R.id.tv_voltage);
        tvBatterySource = view.findViewById(R.id.tv_battery_source);
        tvTechnology = view.findViewById(R.id.tv_technology);
        
        // 卡片
        cardHealth = view.findViewById(R.id.card_health);
        cardDetails = view.findViewById(R.id.card_details);
    }
    
    private void registerBatteryReceiver() {
        if (getContext() != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            getContext().registerReceiver(batteryReceiver, filter);
        }
    }
    
    private void updateUI() {
        if (batteryDataManager == null) return;
        
        BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
        if (info == null) return;
        
        mainHandler.post(() -> {
            // 更新健康度
            float healthPercentage = info.getHealthPercentage();
            tvHealthPercentage.setText(String.format("%.1f%%", healthPercentage));
            tvHealthGrade.setText(info.getHealthGrade());
            tvHealthStatus.setText(info.getHealthDescription());
            progressHealth.setProgress((int) healthPercentage);
            
            // 设置健康度颜色
            int healthColor = getHealthColor(healthPercentage);
            tvHealthPercentage.setTextColor(healthColor);
            progressHealth.getProgressDrawable().setColorFilter(healthColor, android.graphics.PorterDuff.Mode.SRC_IN);
            
            // 更新详细信息
            tvCapacity.setText(String.format("%d / %d mAh", 
                    info.getCurrentCapacity(), info.getDesignCapacity()));
            tvCycleCount.setText(String.format("%d 次", info.getCycleCount()));
            tvTemperature.setText(String.format("%.1f°C", info.getTemperature()));
            tvVoltage.setText(String.format("%.0f mV", info.getVoltage()));
            tvBatterySource.setText(batteryDataManager.getBatterySourceText());
            tvTechnology.setText(info.getTechnology() != null ? info.getTechnology() : "Li-ion");
        });
    }
    
    private int getHealthColor(float percentage) {
        if (percentage >= 90) {
            return getResources().getColor(R.color.health_a_plus);
        } else if (percentage >= 80) {
            return getResources().getColor(R.color.health_a);
        } else if (percentage >= 70) {
            return getResources().getColor(R.color.health_c);
        } else if (percentage >= 60) {
            return getResources().getColor(R.color.health_d);
        } else {
            return getResources().getColor(R.color.health_e);
        }
    }
}