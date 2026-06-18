package com.batteryhealth.app.ui.endurance;

import android.os.BatteryManager;
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

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.BatteryDataManager;

import java.util.Collections;
import java.util.List;

/**
 * 续航分析Fragment
 * 
 * 功能：
 * 1. 预计续航时间计算
 * 2. 放电速率监测
 * 3. 充电状态分析
 * 4. 预计充满时间计算
 * 
 * 注意：电池广播由BatteryMonitorService统一处理，此Fragment使用定时轮询
 */
public class EnduranceFragment extends Fragment {
    
    private static final String TAG = "EnduranceFragment";
    private static final long UPDATE_INTERVAL = 5000; // 5秒更新一次
    
    private TextView tvEnduranceTime;
    private TextView tvEnduranceStatus;
    private TextView tvCurrentLevel;
    private TextView tvDischargeRate;
    private TextView tvChargeStatus;
    private TextView tvBatteryTemp;
    private TextView tvFullChargeTime;
    
    private BatteryDataManager batteryDataManager;
    private Handler mainHandler;
    private boolean isRunning = false;
    
    // 放电速率计算
    private int lastLevel = -1;
    private long lastLevelTime = 0;
    private float dischargeRate = 0; // %/h
    
    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            // 检查Fragment是否仍然附加到Activity
            if (!isAdded() || isDetached() || getContext() == null) {
                isRunning = false;
                return;
            }

            // 刷新基本电池信息
            if (batteryDataManager != null) {
                batteryDataManager.refreshFromStickyIntent();
            }

            updateUI();
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
            return inflater.inflate(R.layout.fragment_endurance, container, false);
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
            
            if (getActivity() instanceof MainActivity) {
                batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
            }
            
            initViews(view);
            loadHistoricalDischargeRate();
            updateUI();
            animateCardsEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage(), e);
        }
    }

    /**
     * 从历史数据库加载平均放电速率作为初始值，避免首次进入显示“计算中...”。
     */
    private void loadHistoricalDischargeRate() {
        new Thread(() -> {
            try {
                BatteryHealthApplication app = BatteryHealthApplication.getInstance();
                if (app == null) return;
                AppDatabase db = app.getDatabase();
                if (db == null) return;

                // 取最近 24 小时内至少 10 条记录计算放电速率
                long oneDayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
                List<BatteryInfo> records = db.batteryInfoDao().getSince(oneDayAgo);
                if (records == null || records.size() < 5) return;

                // 按时间排序
                Collections.sort(records, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

                float totalRate = 0;
                int sampleCount = 0;
                for (int i = 1; i < records.size(); i++) {
                    BatteryInfo prev = records.get(i - 1);
                    BatteryInfo curr = records.get(i);
                    int levelDiff = prev.getLevel() - curr.getLevel();
                    long timeDiff = curr.getTimestamp() - prev.getTimestamp();
                    if (levelDiff > 0 && timeDiff > 60_000) {
                        float hours = timeDiff / (1000.0f * 60 * 60);
                        totalRate += levelDiff / hours;
                        sampleCount++;
                    }
                }

                if (sampleCount > 0) {
                    float avgRate = totalRate / sampleCount;
                    // 限制在合理范围
                    if (avgRate > 0.1f && avgRate < 100) {
                        dischargeRate = avgRate;
                        Log.d(TAG, "Loaded historical discharge rate: " + avgRate + "%/h");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading historical discharge rate: " + e.getMessage());
            }
        }).start();
    }

    private void animateCardsEntry(View view) {
        try {
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
                    .setDuration(650)
                    .setStartDelay(i * 100L)
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