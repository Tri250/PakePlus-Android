package com.batteryhealth.app.ui.endurance;

import android.content.Context;
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

            // 刷新基本电池信息
            if (batteryDataManager != null) {
                batteryDataManager.refreshFromStickyIntent();
            }

            updateUI();
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
        } catch (Throwable t) {
            Log.e(TAG, "Error inflating layout: " + t.getMessage(), t);
            return createErrorView(t);
        }
    }

    /**
     * 创建友好的错误页：标题 + 提示文案 + "重试" 按钮。
     */
    private View createErrorView(Throwable t) {
        final Context[] ctxHolder = new Context[1];
        try { ctxHolder[0] = getContext(); } catch (Throwable ignored) {}
        if (ctxHolder[0] == null) ctxHolder[0] = requireActivity().getApplicationContext();
        final Context ctx = ctxHolder[0];

        android.widget.LinearLayout root = new android.widget.LinearLayout(ctx);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (40 * ctx.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad * 2, pad, pad);
        try {
            root.setBackgroundColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.ios_background));
        } catch (Throwable ignored) {
            root.setBackgroundColor(0xFFEFEFF4);
        }

        android.widget.TextView tvTitle = new android.widget.TextView(ctx);
        tvTitle.setText("界面加载失败");
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        try {
            tvTitle.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.ios_label));
        } catch (Throwable ignored) {
            tvTitle.setTextColor(0xFF1C1C1E);
        }
        tvTitle.setGravity(android.view.Gravity.CENTER);
        root.addView(tvTitle);

        android.widget.TextView tvMsg = new android.widget.TextView(ctx);
        tvMsg.setText("数据尚未就绪，请点击下方按钮重试。");
        tvMsg.setTextSize(14);
        try {
            tvMsg.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.ios_secondary_label));
        } catch (Throwable ignored) {
            tvMsg.setTextColor(0xFF3C3C43);
        }
        tvMsg.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams msgLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = (int) (12 * ctx.getResources().getDisplayMetrics().density);
        root.addView(tvMsg, msgLp);

        android.widget.Button btnRetry = new android.widget.Button(ctx);
        btnRetry.setText("重 试");
        btnRetry.setAllCaps(false);
        btnRetry.setTextSize(15);
        try {
            btnRetry.setBackgroundColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.ios_blue));
        } catch (Throwable ignored) {
            btnRetry.setBackgroundColor(0xFF0A84FF);
        }
        btnRetry.setTextColor(0xFFFFFFFF);
        android.widget.LinearLayout.LayoutParams btnLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = (int) (28 * ctx.getResources().getDisplayMetrics().density);
        int btnH = (int) (44 * ctx.getResources().getDisplayMetrics().density);
        btnRetry.setMinHeight(btnH);
        int btnPad = (int) (28 * ctx.getResources().getDisplayMetrics().density);
        btnRetry.setPadding(btnPad, 0, btnPad, 0);
        root.addView(btnRetry, btnLp);

        btnRetry.setOnClickListener(v -> {
            try {
                View newView = onCreateView(LayoutInflater.from(ctx), (ViewGroup) v.getParent(), null);
                if (newView != null && v.getParent() instanceof ViewGroup) {
                    ViewGroup parent = (ViewGroup) v.getParent();
                    int idx = parent.indexOfChild(root);
                    parent.removeView(root);
                    parent.addView(newView, idx);
                    try { onViewCreated(newView, null); } catch (Throwable ignored) {}
                    try { animateCardsEntry(newView); } catch (Throwable ignored) {}
                }
            } catch (Throwable ex) {
                Log.e(TAG, "Retry failed: " + ex.getMessage(), ex);
            }
        });
        return root;
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