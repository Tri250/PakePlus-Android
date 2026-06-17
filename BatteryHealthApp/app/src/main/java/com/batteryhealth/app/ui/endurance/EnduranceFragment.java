package com.batteryhealth.app.ui.endurance;

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

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.utils.BatteryDataManager;

/**
 * 续航分析Fragment
 * 
 * 功能：
 * 1. 预计续航时间计算
 * 2. 放电速率监测
 * 3. 充电状态分析
 * 4. 预计充满时间计算
 */
public class EnduranceFragment extends Fragment {
    
    private static final String TAG = "EnduranceFragment";
    
    private TextView tvEnduranceTime;
    private TextView tvEnduranceStatus;
    private TextView tvCurrentLevel;
    private TextView tvDischargeRate;
    private TextView tvChargeStatus;
    private TextView tvBatteryTemp;
    private TextView tvFullChargeTime;
    
    private BatteryDataManager batteryDataManager;
    private Handler mainHandler;
    private boolean isReceiverRegistered = false;
    
    // 放电速率计算
    private int lastLevel = -1;
    private long lastLevelTime = 0;
    private float dischargeRate = 0; // %/h
    
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
            return inflater.inflate(R.layout.fragment_endurance, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage());
            return new View(requireContext());
        }
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            mainHandler = new Handler(Looper.getMainLooper());
            
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
        tvEnduranceTime = view.findViewById(R.id.tv_endurance_time);
        tvEnduranceStatus = view.findViewById(R.id.tv_endurance_status);
        tvCurrentLevel = view.findViewById(R.id.tv_current_level);
        tvDischargeRate = view.findViewById(R.id.tv_discharge_rate);
        tvChargeStatus = view.findViewById(R.id.tv_charge_status);
        tvBatteryTemp = view.findViewById(R.id.tv_battery_temp);
        tvFullChargeTime = view.findViewById(R.id.tv_full_charge_time);
    }
    
    private void registerBatteryReceiver() {
        try {
            if (getContext() != null) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                filter.addAction(Intent.ACTION_POWER_CONNECTED);
                filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    getContext().registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    getContext().registerReceiver(batteryReceiver, filter);
                }
                isReceiverRegistered = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering receiver: " + e.getMessage());
        }
    }
    
    private void updateUI() {
        if (batteryDataManager == null || mainHandler == null) return;
        
        mainHandler.post(() -> {
            try {
                com.batteryhealth.app.data.model.BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
                if (info == null) return;
                
                int level = info.getLevel();
                float temperature = info.getTemperature();
                int status = info.getStatus();
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                   status == BatteryManager.BATTERY_STATUS_FULL;
                
                // 计算放电速率
                calculateDischargeRate(level);
                
                // 更新当前电量
                if (tvCurrentLevel != null) {
                    tvCurrentLevel.setText(level + "%");
                }
                
                // 更新放电速率
                if (tvDischargeRate != null) {
                    if (isCharging) {
                        tvDischargeRate.setText("充电中");
                    } else if (dischargeRate > 0) {
                        tvDischargeRate.setText(String.format("%.1f%%/h", dischargeRate));
                    } else {
                        tvDischargeRate.setText("计算中...");
                    }
                }
                
                // 更新充电状态
                if (tvChargeStatus != null) {
                    tvChargeStatus.setText(batteryDataManager.getChargingStatusText());
                }
                
                // 更新电池温度
                if (tvBatteryTemp != null) {
                    tvBatteryTemp.setText(String.format("%.1f°C", temperature));
                }
                
                // 计算预计续航时间
                if (tvEnduranceTime != null) {
                    if (isCharging) {
                        tvEnduranceTime.setText("充电中");
                        tvEnduranceTime.setTextColor(getResources().getColor(R.color.ios_blue));
                    } else if (dischargeRate > 0 && level > 0) {
                        float remainingHours = level / dischargeRate;
                        if (remainingHours >= 24) {
                            tvEnduranceTime.setText(String.format("%.0f 天", remainingHours / 24));
                        } else {
                            tvEnduranceTime.setText(String.format("%.1f 小时", remainingHours));
                        }
                        tvEnduranceTime.setTextColor(getResources().getColor(R.color.ios_green));
                    } else {
                        tvEnduranceTime.setText("-- 小时");
                    }
                }
                
                // 更新续航状态描述
                if (tvEnduranceStatus != null) {
                    if (isCharging) {
                        tvEnduranceStatus.setText("设备正在充电");
                    } else if (dischargeRate > 0) {
                        if (dischargeRate < 5) {
                            tvEnduranceStatus.setText("续航表现优秀");
                        } else if (dischargeRate < 10) {
                            tvEnduranceStatus.setText("续航表现良好");
                        } else if (dischargeRate < 20) {
                            tvEnduranceStatus.setText("续航表现一般");
                        } else {
                            tvEnduranceStatus.setText("耗电较快，请检查后台应用");
                        }
                    } else {
                        tvEnduranceStatus.setText("正在计算...");
                    }
                }
                
                // 计算预计充满时间
                if (tvFullChargeTime != null) {
                    if (isCharging && level < 100) {
                        // 读取充电电流估算充满时间
                        int currentNow = info.getCurrentNow();
                        if (currentNow > 0) {
                            int designCapacity = info.getDesignCapacity();
                            if (designCapacity > 0) {
                                int remainingMah = (int) (designCapacity * (100 - level) / 100.0);
                                float currentA = currentNow / 1000000.0f;
                                if (currentA > 0) {
                                    float hoursNeeded = remainingMah / (currentA * 1000);
                                    if (hoursNeeded < 1) {
                                        tvFullChargeTime.setText(String.format("%.0f 分钟", hoursNeeded * 60));
                                    } else {
                                        tvFullChargeTime.setText(String.format("%.1f 小时", hoursNeeded));
                                    }
                                } else {
                                    tvFullChargeTime.setText("计算中...");
                                }
                            } else {
                                tvFullChargeTime.setText("计算中...");
                            }
                        } else {
                            tvFullChargeTime.setText("计算中...");
                        }
                    } else if (level >= 100) {
                        tvFullChargeTime.setText("已充满");
                    } else {
                        tvFullChargeTime.setText("未充电");
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error updating UI: " + e.getMessage());
            }
        });
    }
    
    /**
     * 计算放电速率
     */
    private void calculateDischargeRate(int currentLevel) {
        long currentTime = System.currentTimeMillis();
        
        if (lastLevel >= 0 && lastLevelTime > 0 && currentLevel < lastLevel) {
            long timeDiff = currentTime - lastLevelTime; // 毫秒
            int levelDiff = lastLevel - currentLevel;
            
            if (timeDiff > 0 && levelDiff > 0) {
                // 计算每小时放电百分比
                float hoursDiff = timeDiff / (1000.0f * 60 * 60);
                dischargeRate = levelDiff / hoursDiff;
            }
        }
        
        lastLevel = currentLevel;
        lastLevelTime = currentTime;
    }
}