package com.batteryhealth.app.ui.endurance;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.Locale;
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

            // 将 IO 操作下沉到后台线程，UI 更新回主线程
            new Thread(() -> {
                if (batteryDataManager != null) {
                    batteryDataManager.refreshFromStickyIntent();
                }
                if (mainHandler != null) {
                    mainHandler.post(() -> {
                        if (isAdded()) {
                            updateUI();
                        }
                    });
                }
            }).start();

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
            return inflater.inflate(R.layout.fragment_endurance, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
            return createErrorView(e);
        }
    }

    private View createErrorView(Exception e) {
        android.widget.TextView errorView = new android.widget.TextView(requireContext());
        String message = getString(R.string.error_view_load_failed, e.getClass().getSimpleName(), e.getMessage());
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_label));
        errorView.setTextSize(16);
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

    private static final String PREFS_GLOBAL = "app_global_prefs";
    private static final String PREF_DISABLE_ANIMATIONS = "disable_animations";

    private boolean shouldSkipAnimations() {
        try {
            Context ctx = requireContext();
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_GLOBAL, Context.MODE_PRIVATE);
            if (prefs.getBoolean(PREF_DISABLE_ANIMATIONS, false)) {
                return true;
            }
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long totalMemGb = mi.totalMem / (1024L * 1024L * 1024L);
                if (totalMemGb < 4) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Animation check skipped: " + e.getMessage());
        }
        return false;
    }

    private void animateCardsEntry(View view) {
        try {
            if (shouldSkipAnimations()) return;
            java.util.List<View> cards = new java.util.ArrayList<>();
            collectCards(view, cards);
            for (int i = 0; i < cards.size(); i++) {
                View child = cards.get(i);
                child.setAlpha(0f);
                child.setTranslationY(60f);
                child.setScaleX(0.94f);
                child.setScaleY(0.94f);
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setStartDelay(i * 60L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f))
                    .start();
            }
        } catch (Exception e) {
            Log.d(TAG, "Card entry animation skipped: " + e.getMessage());
        }
    }

    private void collectCards(View view, java.util.List<View> cards) {
        if (view instanceof com.google.android.material.card.MaterialCardView) {
            cards.add(view);
            return;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectCards(group.getChildAt(i), cards);
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
                int currentNowUa = info.getCurrentNow(); // 正值充电，负值放电
                int designCapacity = info.getDesignCapacity();
                int remainingCapacityMah = designCapacity > 0 ? (int) (designCapacity * level / 100.0) : -1;

                // 计算放电速率（%/h）
                calculateDischargeRate(level);

                // 更新当前电量
                if (tvCurrentLevel != null) {
                    tvCurrentLevel.setText(level + "%");
                }

                // 更新放电速率
                if (tvDischargeRate != null) {
                    if (isCharging) {
                        tvDischargeRate.setText(getString(R.string.status_charging));
                    } else if (dischargeRate > 0) {
                        tvDischargeRate.setText(String.format(Locale.getDefault(), "%.1f%%/h", dischargeRate));
                    } else {
                        tvDischargeRate.setText(getString(R.string.status_calculating_short));
                    }
                }

                // 更新充电状态
                if (tvChargeStatus != null) {
                    tvChargeStatus.setText(batteryDataManager.getChargingStatusText());
                }

                // 更新电池温度
                if (tvBatteryTemp != null) {
                    tvBatteryTemp.setText(String.format(Locale.getDefault(), "%.1f°C", temperature));
                }

                // 计算预计续航时间：优先基于历史放电速率，其次基于实时电流
                if (tvEnduranceTime != null) {
                    if (isCharging) {
                        tvEnduranceTime.setText(getString(R.string.status_charging));
                        tvEnduranceTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_blue));
                    } else {
                        float remainingHours = estimateRemainingHours(level, currentNowUa, remainingCapacityMah);
                        if (remainingHours > 0) {
                            if (remainingHours >= 24) {
                                tvEnduranceTime.setText(String.format(Locale.getDefault(), getString(R.string.status_endurance_days), remainingHours / 24));
                            } else {
                                tvEnduranceTime.setText(String.format(Locale.getDefault(), getString(R.string.status_endurance_hours), remainingHours));
                            }
                            tvEnduranceTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_green));
                        } else {
                            tvEnduranceTime.setText(getString(R.string.status_hours_placeholder));
                        }
                    }
                }

                // 更新续航状态描述
                if (tvEnduranceStatus != null) {
                    if (isCharging) {
                        tvEnduranceStatus.setText(getString(R.string.status_device_charging));
                    } else if (dischargeRate > 0) {
                        if (dischargeRate < 5) {
                            tvEnduranceStatus.setText(getString(R.string.status_excellent));
                        } else if (dischargeRate < 10) {
                            tvEnduranceStatus.setText(getString(R.string.status_good));
                        } else if (dischargeRate < 20) {
                            tvEnduranceStatus.setText(getString(R.string.status_average));
                        } else {
                            tvEnduranceStatus.setText(getString(R.string.status_poor));
                        }
                    } else {
                        tvEnduranceStatus.setText(getString(R.string.status_calculating_short));
                    }
                }

                // 计算预计充满时间：考虑恒压阶段（80% 后电流衰减）
                if (tvFullChargeTime != null) {
                    if (isCharging && level < 100) {
                        float fullChargeHours = estimateFullChargeHours(level, currentNowUa, designCapacity);
                        if (fullChargeHours > 0) {
                            if (fullChargeHours < 1) {
                                tvFullChargeTime.setText(String.format(Locale.getDefault(), getString(R.string.status_full_charge_time_minutes), fullChargeHours * 60));
                            } else {
                                tvFullChargeTime.setText(String.format(Locale.getDefault(), getString(R.string.status_full_charge_time_hours), fullChargeHours));
                            }
                        } else {
                            tvFullChargeTime.setText(getString(R.string.status_calculating_short));
                        }
                    } else if (level >= 100) {
                        tvFullChargeTime.setText(getString(R.string.status_full_charge_done));
                    } else {
                        tvFullChargeTime.setText(getString(R.string.status_not_charging_short));
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error updating UI: " + e.getMessage());
            }
        });
    }

    /**
     * 综合历史放电速率和实时电流估算剩余续航时间。
     */
    private float estimateRemainingHours(int level, int currentNowUa, int remainingCapacityMah) {
        // 方法1：历史放电速率（%/h）
        if (dischargeRate > 0.1f && level > 0) {
            return level / dischargeRate;
        }

        // 方法2：实时电流（currentNow 放电时为负）
        if (currentNowUa < 0 && remainingCapacityMah > 0) {
            float dischargeMa = Math.abs(currentNowUa) / 1000.0f;
            if (dischargeMa > 0) {
                return remainingCapacityMah / dischargeMa;
            }
        }

        return -1;
    }

    /**
     * 估算充满时间：80% 前按当前电流，80% 后考虑电流衰减。
     */
    private float estimateFullChargeHours(int level, int currentNowUa, int designCapacityMah) {
        if (currentNowUa <= 0 || designCapacityMah <= 0) return -1;

        float chargeMa = currentNowUa / 1000.0f;
        float remainingMah = designCapacityMah * (100 - level) / 100.0f;

        // 80% 后电流会下降，按 0.55 倍估算平均电流
        float effectiveChargeMa = level < 80 ? chargeMa : chargeMa * 0.55f;
        if (effectiveChargeMa <= 0) return -1;

        return remainingMah / effectiveChargeMa;
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