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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.utils.BatteryDataManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * 充电续航Fragment
 * 
 * 合并了原PowerFragment和EnduranceFragment的功能：
 * 1. 充电功率监测
 * 2. 续航时间估算
 * 3. 放电速率监测
 * 4. 充电状态分析
 * 5. 预计充满时间计算
 */
public class PowerFragment extends Fragment {
    
    private static final String TAG = "PowerFragment";
    
    // 充电功率相关
    private TextView tvPower;
    private TextView tvVoltage;
    private TextView tvCurrent;
    private TextView tvChargeType;
    
    // 续航相关
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
    
    private BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && mainHandler != null && isAdded() && !isDetached()) {
                mainHandler.post(() -> updateAllData());
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
            
            if (getActivity() instanceof MainActivity) {
                batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
            }
            
            // 充电功率相关视图
            tvPower = view.findViewById(R.id.tv_power);
            tvVoltage = view.findViewById(R.id.tv_power_voltage);
            tvCurrent = view.findViewById(R.id.tv_power_current);
            tvChargeType = view.findViewById(R.id.tv_charge_type);
            
            // 续航相关视图
            tvEnduranceTime = view.findViewById(R.id.tv_endurance_time);
            tvEnduranceStatus = view.findViewById(R.id.tv_endurance_status);
            tvCurrentLevel = view.findViewById(R.id.tv_current_level);
            tvDischargeRate = view.findViewById(R.id.tv_discharge_rate);
            tvChargeStatus = view.findViewById(R.id.tv_charge_status);
            tvBatteryTemp = view.findViewById(R.id.tv_battery_temp);
            tvFullChargeTime = view.findViewById(R.id.tv_full_charge_time);
            
            // 设置默认值
            setDefaultValues();
            
            registerPowerReceiver();
            updateAllData();
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }
    
    private void setDefaultValues() {
        if (tvPower != null) tvPower.setText("0.0 W");
        if (tvVoltage != null) tvVoltage.setText("0.00 V");
        if (tvCurrent != null) tvCurrent.setText("0.00 A");
        if (tvChargeType != null) tvChargeType.setText("未充电");
        if (tvEnduranceTime != null) tvEnduranceTime.setText("-- 小时");
        if (tvEnduranceStatus != null) tvEnduranceStatus.setText("正在计算...");
        if (tvCurrentLevel != null) tvCurrentLevel.setText("--%");
        if (tvDischargeRate != null) tvDischargeRate.setText("--%/h");
        if (tvChargeStatus != null) tvChargeStatus.setText("--");
        if (tvBatteryTemp != null) tvBatteryTemp.setText("--°C");
        if (tvFullChargeTime != null) tvFullChargeTime.setText("--");
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
    
    private void updateAllData() {
        updatePowerData();
        updateEnduranceData();
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
    
    private void updateEnduranceData() {
        if (batteryDataManager == null) return;
        
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
                    tvEnduranceTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_blue));
                } else if (dischargeRate > 0 && level > 0) {
                    float remainingHours = level / dischargeRate;
                    if (remainingHours >= 24) {
                        tvEnduranceTime.setText(String.format("%.0f 天", remainingHours / 24));
                    } else {
                        tvEnduranceTime.setText(String.format("%.1f 小时", remainingHours));
                    }
                    tvEnduranceTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_green));
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
            Log.e(TAG, "Error updating endurance data: " + e.getMessage());
        }
    }
    
    private void calculateDischargeRate(int currentLevel) {
        long currentTime = System.currentTimeMillis();
        
        if (lastLevel >= 0 && lastLevelTime > 0 && currentLevel < lastLevel) {
            long timeDiff = currentTime - lastLevelTime;
            int levelDiff = lastLevel - currentLevel;
            
            if (timeDiff > 0 && levelDiff > 0) {
                float hoursDiff = timeDiff / (1000.0f * 60 * 60);
                dischargeRate = levelDiff / hoursDiff;
            }
        }
        
        lastLevel = currentLevel;
        lastLevelTime = currentTime;
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
