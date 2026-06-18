package com.batteryhealth.app.ui.battery;

import android.os.BatteryManager;
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

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.BatteryDataManager;

/**
 * 电池健康Fragment
 * 
 * 注意：电池数据由BatteryMonitorService统一处理并通过BatteryDataManager获取
 * 不再在此Fragment中注册广播接收器，避免重复监听
 */
public class BatteryHealthFragment extends Fragment {
    
    private static final String TAG = "BatteryHealthFragment";
    private static final long UPDATE_INTERVAL = 5000; // 5秒更新一次UI
    
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
    private TextView tvBatteryLevel;
    private TextView tvChargingStatus;
    private TextView tvCurrentNow;
    
    private BatteryDataManager batteryDataManager;
    private Handler mainHandler;
    private boolean isRunning = false;
    
    // 定时更新UI的Runnable
    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            // 检查Fragment是否仍然附加到Activity
            if (!isAdded() || isDetached() || getContext() == null) {
                isRunning = false;
                return;
            }

            // 每次刷新UI前先从系统读取最新基本数据（电量、温度、电压等）
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
            return inflater.inflate(R.layout.fragment_battery_health, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
            // 创建错误提示视图，避免完全空白，同时显示异常信息便于排查
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
            
            // 获取电池数据管理器
            if (getActivity() instanceof MainActivity) {
                batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
            }
            
            initViews(view);
            updateUI();
            animateCardsEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();

        // 检查Fragment状态
        if (!isAdded() || getContext() == null) {
            Log.w(TAG, "Fragment not attached, skipping onResume");
            return;
        }

        isRunning = true;

        // 进入页面时刷新一次完整数据（容量、循环次数、电池来源等）
        if (batteryDataManager != null) {
            try {
                batteryDataManager.refreshAllDataAsync();
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing data: " + e.getMessage());
            }
        }

        // 启动定时更新，刷新UI和基本电池信息
        if (mainHandler != null && isAdded()) {
            mainHandler.post(updateRunnable);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isRunning = false;
        // 停止定时更新
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
            tvBatteryLevel = view.findViewById(R.id.tv_battery_level);
            tvChargingStatus = view.findViewById(R.id.tv_charging_status);
            tvCurrentNow = view.findViewById(R.id.tv_current_now);
            
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
        if (tvBatteryLevel != null) tvBatteryLevel.setText("--%");
        if (tvChargingStatus != null) tvChargingStatus.setText("--");
        if (tvCurrentNow != null) tvCurrentNow.setText("-- mA");
    }
    
    private void updateUI() {
        if (batteryDataManager == null || mainHandler == null) return;
        if (!isAdded() || getContext() == null) return;

        mainHandler.post(() -> {
            try {
                if (!isAdded() || getContext() == null) return;

                BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
                if (info == null) return;
                
                // 更新健康度
                float healthPercentage = info.getHealthPercentage();
                if (tvHealthPercentage != null) {
                    if (info.hasValidHealthData()) {
                        tvHealthPercentage.setText(String.format("%.1f%%", healthPercentage));
                    } else {
                        tvHealthPercentage.setText("--");
                    }
                }
                
                if (tvHealthGrade != null) {
                    tvHealthGrade.setText(info.getHealthGrade());
                }
                
                if (tvHealthStatus != null) {
                    String source = batteryDataManager.getHealthSourceText();
                    float confidence = info.getHealthConfidence();
                    String confidenceText = confidence > 0
                            ? String.format(" (可信度 %.0f%%)", confidence * 100)
                            : "";
                    tvHealthStatus.setText(info.getHealthDescription() + " · " + source + confidenceText);
                }

                if (progressHealth != null) {
                    if (info.hasValidHealthData()) {
                        progressHealth.setProgress((int) healthPercentage);
                    } else {
                        progressHealth.setProgress(0);
                    }
                }
                
                // 设置健康度颜色
                if (tvHealthPercentage != null && progressHealth != null && info.hasValidHealthData()) {
                    int healthColor = getHealthColor(healthPercentage);
                    tvHealthPercentage.setTextColor(healthColor);
                    try {
                        progressHealth.getProgressDrawable().setColorFilter(healthColor, android.graphics.PorterDuff.Mode.SRC_IN);
                    } catch (Exception e) {
                        // 某些设备可能不支持setColorFilter
                    }
                } else if (tvHealthPercentage != null) {
                    // 未知状态使用灰色
                    tvHealthPercentage.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_tertiary_label));
                }
                
                // 更新详细信息
                if (tvCapacity != null) {
                    int currentCap = info.getCurrentCapacity();
                    int designCap = info.getDesignCapacity();
                    if (currentCap > 0 && designCap > 0) {
                        tvCapacity.setText(String.format("%d / %d mAh", currentCap, designCap));
                    } else {
                        tvCapacity.setText("无法读取");
                    }
                }

                if (tvCycleCount != null) {
                    if (info.hasValidCycleCount()) {
                        String estimatedMark = info.isCycleCountEstimated() ? " · 估算" : "";
                        tvCycleCount.setText(String.format("%d 次%s", info.getCycleCount(), estimatedMark));
                    } else {
                        tvCycleCount.setText("无法读取");
                    }
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

                // 更新电池电量
                if (tvBatteryLevel != null) {
                    tvBatteryLevel.setText(info.getLevel() + "%");
                }

                // 更新充电状态
                if (tvChargingStatus != null) {
                    tvChargingStatus.setText(batteryDataManager.getChargingStatusText());
                }

                // 更新电流
                if (tvCurrentNow != null) {
                    int currentNow = info.getCurrentNow();
                    if (currentNow != 0) {
                        tvCurrentNow.setText(String.format("%.0f mA", Math.abs(currentNow / 1000.0f)));
                    } else {
                        tvCurrentNow.setText("-- mA");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating UI: " + e.getMessage());
            }
        });
    }
    
    /**
     * 递归遍历视图树，对每个 MaterialCardView 应用淡入动画。
     * 原实现只对 NestedScrollView 的直接子 LinearLayout 应用动画，导致内部卡片没有动画。
     */
    private void animateCardsEntry(View view) {
        try {
            if (view == null || !(view instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup root = (android.view.ViewGroup) view;
            animateViewGroupRecursive(root, 0);
        } catch (Exception e) {
            Log.d(TAG, "Liquid glass card animation skipped: " + e.getMessage());
        }
    }

    private void animateViewGroupRecursive(android.view.ViewGroup parent, int depth) {
        if (parent == null) return;
        // 限制递归深度，避免深层嵌套导致性能问题
        if (depth > 4) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == null) continue;
            // 跳过 ViewPager 容器（保持兼容历史代码）
            if (child.getId() == R.id.view_pager) continue;
            // 仅对卡片或顶层子元素执行动画
            boolean shouldAnimate = (child instanceof com.google.android.material.card.MaterialCardView)
                    || depth == 1
                    || (depth == 0 && parent.getChildCount() > 1);
            if (shouldAnimate) {
                try {
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
                } catch (Exception ignored) {}
            }
            if (child instanceof android.view.ViewGroup) {
                animateViewGroupRecursive((android.view.ViewGroup) child, depth + 1);
            }
        }
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
            // 返回默认颜色
            return ContextCompat.getColor(requireContext(), R.color.ios_green);
        }
    }
}