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
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.BatteryDataManager;

/**
 * 电池健康Fragment
 */
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
            // 创建一个简单的错误视图
            View errorView = new View(requireContext());
            return errorView;
        }
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            mainHandler = new Handler(Looper.getMainLooper());
            
            // 获取电池数据管理器
            if (getActivity() instanceof MainActivity) {
                batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
            }
            
            initViews(view);
            registerBatteryReceiver();
            updateUI();
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
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
        try {
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
            
            // 设置默认值
            setDefaultValues();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage());
        }
    }
    
    private void setDefaultValues() {
        if (tvHealthPercentage != null) tvHealthPercentage.setText("--");
        if (tvHealthGrade != null) tvHealthGrade.setText("--");
        if (tvHealthStatus != null) tvHealthStatus.setText("正在检测...");
        if (tvCapacity != null) tvCapacity.setText("-- mAh");
        if (tvCycleCount != null) tvCycleCount.setText("-- 次");
        if (tvTemperature != null) tvTemperature.setText("-- °C");
        if (tvVoltage != null) tvVoltage.setText("-- mV");
        if (tvBatterySource != null) tvBatterySource.setText("检测中");
        if (tvTechnology != null) tvTechnology.setText("--");
    }
    
    private void registerBatteryReceiver() {
        try {
            if (getContext() != null) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                
                // Android 14+ 需要指定导出标志
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
        if (batteryDataManager == null || mainHandler == null) return;
        
        mainHandler.post(() -> {
            try {
                BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
                if (info == null) return;
                
                // 更新健康度
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
                
                // 设置健康度颜色
                if (tvHealthPercentage != null && progressHealth != null) {
                    int healthColor = getHealthColor(healthPercentage);
                    tvHealthPercentage.setTextColor(healthColor);
                    try {
                        progressHealth.getProgressDrawable().setColorFilter(healthColor, android.graphics.PorterDuff.Mode.SRC_IN);
                    } catch (Exception e) {
                        // 某些设备可能不支持setColorFilter
                    }
                }
                
                // 更新详细信息
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
                return getResources().getColor(R.color.health_a_plus, null);
            } else if (percentage >= 80) {
                return getResources().getColor(R.color.health_a, null);
            } else if (percentage >= 70) {
                return getResources().getColor(R.color.health_c, null);
            } else if (percentage >= 60) {
                return getResources().getColor(R.color.health_d, null);
            } else {
                return getResources().getColor(R.color.health_e, null);
            }
        } catch (Exception e) {
            // 返回默认颜色
            return 0xFF34C759; // iOS绿色
        }
    }
}