package com.batteryhealth.app.ui.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.ui.view.HealthRingView;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.StateLayoutHelper;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BatteryHealthFragment extends Fragment {

    private HealthRingView healthRing;
    private TextView tvHealthPercentage;
    private TextView tvHealthGrade;
    private TextView tvHealthStatus;
    private TextView tvBatteryLevel;
    private TextView tvChargingStatus;
    private TextView tvCurrentNow;
    private TextView tvCapacity;
    private TextView tvCycleCount;
    private TextView tvTemperature;
    private TextView tvVoltage;
    private TextView tvBatterySource;
    private TextView tvTechnology;
    private TextView tvHealthSource;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private BatteryDataManager batteryDataManager;
    private StateLayoutHelper stateLayoutHelper;
    // 标记 StateLayoutHelper 是否已初始化，避免重复初始化或清理后误用
    private boolean stateLayoutInitialized = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_battery_health, container, false);
        Context ctx = getContext();
        if (ctx == null) {
            return view;
        }
        batteryDataManager = BatteryDataManager.getInstance(ctx);
        initViews(view);
        animateEntry(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 在 onViewCreated 后初始化 StateLayoutHelper，确保视图层级已完整构建
        initStateLayoutHelper(view);
        // 注意：bugreport 数据已在 MainActivity 启动时通过 BatteryDataManager 单例加载，
        // 无需在此重复加载。但需要更新 UI 显示 bugreport 数据来源。
        updateBugreportSourceUI();
    }

    /**
     * 安全初始化 StateLayoutHelper。
     * 问题修复：Fragment 被 ViewPager2 复用时，onViewCreated 可能多次执行，
     * 需要确保 StateLayoutHelper 只初始化一次，且目标 ViewGroup 有效。
     */
    private void initStateLayoutHelper(View view) {
        if (stateLayoutInitialized || stateLayoutHelper != null) {
            return;
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup root = (ViewGroup) view;
        // 查找第一个有效的内容容器（通常是 ScrollView 或 NestedScrollView 的子元素）
        ViewGroup contentContainer = findContentContainer(root);
        if (contentContainer == null) {
            // 如果找不到嵌套的内容容器，直接使用根视图作为内容容器
            contentContainer = root;
        }
        try {
            stateLayoutHelper = new StateLayoutHelper(contentContainer);
            stateLayoutHelper.showLoading(null);
            stateLayoutInitialized = true;
        } catch (Exception e) {
            // StateLayoutHelper 初始化失败时记录日志，不影响主流程
            android.util.Log.e("BatteryHealthFragment", "StateLayoutHelper init failed", e);
            stateLayoutHelper = null;
            stateLayoutInitialized = false;
        }
    }

    /**
     * 递归查找适合作为 StateLayoutHelper 内容容器的 ViewGroup。
     * 优先查找 ScrollView / NestedScrollView 的直接子 ViewGroup。
     */
    private ViewGroup findContentContainer(ViewGroup parent) {
        if (parent == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) child;
                // 如果是 ScrollView 或 NestedScrollView，返回它的第一个子元素
                if (group instanceof android.widget.ScrollView
                        || group instanceof androidx.core.widget.NestedScrollView) {
                    if (group.getChildCount() > 0 && group.getChildAt(0) instanceof ViewGroup) {
                        return (ViewGroup) group.getChildAt(0);
                    }
                    return group;
                }
                // 递归查找
                ViewGroup result = findContentContainer(group);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * 更新 bugreport 数据来源 UI。
     * 如果 BatteryDataManager 已有 bugreport 数据，显示"来自 bugreport 分析"。
     */
    private void updateBugreportSourceUI() {
        if (tvHealthSource == null) return;
        try {
            if (batteryDataManager != null && batteryDataManager.hasBugreportData()) {
                tvHealthSource.setText(R.string.health_source_bugreport);
            }
        } catch (Exception e) {
            android.util.Log.e("BatteryHealthFragment", "Failed to update bugreport source UI", e);
        }
    }

    private void initViews(View view) {
        healthRing = view.findViewById(R.id.health_ring);
        tvHealthPercentage = view.findViewById(R.id.tv_health_percentage);
        tvHealthGrade = view.findViewById(R.id.tv_health_grade);
        tvHealthStatus = view.findViewById(R.id.tv_health_status);
        tvBatteryLevel = view.findViewById(R.id.tv_battery_level);
        tvChargingStatus = view.findViewById(R.id.tv_charging_status);
        tvCurrentNow = view.findViewById(R.id.tv_current_now);
        tvCapacity = view.findViewById(R.id.tv_capacity);
        tvCycleCount = view.findViewById(R.id.tv_cycle_count);
        tvTemperature = view.findViewById(R.id.tv_temperature);
        tvVoltage = view.findViewById(R.id.tv_voltage);
        tvBatterySource = view.findViewById(R.id.tv_battery_source);
        tvTechnology = view.findViewById(R.id.tv_technology);
        tvHealthSource = view.findViewById(R.id.tv_health_source);

        // 周报/月报入口
        View btnWeeklyReport = view.findViewById(R.id.btn_weekly_report);
        View btnMonthlyReport = view.findViewById(R.id.btn_monthly_report);
        if (btnWeeklyReport != null) {
            btnWeeklyReport.setOnClickListener(v -> {
                Context ctx = getContext();
                if (ctx != null) ReportActivity.start(ctx, ReportActivity.TYPE_WEEKLY);
            });
        }
        if (btnMonthlyReport != null) {
            btnMonthlyReport.setOnClickListener(v -> {
                Context ctx = getContext();
                if (ctx != null) ReportActivity.start(ctx, ReportActivity.TYPE_MONTHLY);
            });
        }

        // 电池溯源 / 健康检查入口
        View btnBatterySource = view.findViewById(R.id.btn_battery_source);
        View btnHealthCheck = view.findViewById(R.id.btn_health_check);
        if (btnBatterySource != null) {
            btnBatterySource.setOnClickListener(v -> {
                Context ctx = getContext();
                if (ctx != null) com.batteryhealth.app.ui.source.BatterySourceActivity.start(ctx);
            });
        }
        if (btnHealthCheck != null) {
            btnHealthCheck.setOnClickListener(v -> {
                Context ctx = getContext();
                if (ctx != null) com.batteryhealth.app.ui.healthcheck.HealthCheckActivity.start(ctx);
            });
        }
    }

    private void animateEntry(View view) {
        Context ctx = getContext();
        if (ctx == null) return;
        Animation fadeUp = AnimationUtils.loadAnimation(ctx, R.anim.fade_up);
        view.startAnimation(fadeUp);
        UiAnimationHelper.animateCardsEntry(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerBatteryReceiver();
        startPeriodicUpdate();
        updateBatteryData();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterBatteryReceiver();
        stopPeriodicUpdate();
    }

    private void registerBatteryReceiver() {
        Context ctx = getContext();
        if (ctx == null) return;
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        ctx.registerReceiver(batteryReceiver, filter);
    }

    private void unregisterBatteryReceiver() {
        Context ctx = getContext();
        if (ctx == null) return;
        try {
            ctx.unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void startPeriodicUpdate() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateBatteryData();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updateRunnable);
    }

    private void stopPeriodicUpdate() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBatteryData();
        }
    };

    /**
     * 更新电池数据到 UI。
     * 问题修复：
     * 1. 增加 batteryDataManager 空指针检查，避免初始化失败时崩溃。
     * 2. 增加更完善的异常分层处理：数据获取异常、UI 绑定异常分别捕获。
     * 3. 数据加载失败时显示错误状态（通过 StateLayoutHelper），而不是一直 loading。
     * 4. 所有主线程回调都检查 isAdded() 和 getContext()，避免 Fragment 已销毁时操作 UI。
     */
    private void updateBatteryData() {
        if (batteryDataManager == null) {
            // BatteryDataManager 未初始化，显示错误状态
            mainHandler.post(() -> {
                if (isAdded() && getContext() != null) {
                    showErrorState();
                }
            });
            return;
        }
        executor.execute(() -> {
            BatteryInfo info = null;
            Exception loadException = null;
            try {
                info = batteryDataManager.getBatteryInfo();
                // 持久化到数据库，供趋势追踪和报告使用
                persistBatteryInfo(info);
            } catch (Exception e) {
                loadException = e;
                android.util.Log.e("BatteryHealthFragment", "Failed to get battery info", e);
            }
            final BatteryInfo finalInfo = info;
            final Exception finalException = loadException;
            mainHandler.post(() -> {
                if (!isAdded() || getContext() == null) {
                    return;
                }
                try {
                    if (finalException != null || finalInfo == null) {
                        // 数据加载失败：显示错误状态，而不是无限 loading
                        showErrorState();
                    } else {
                        bindBatteryInfo(finalInfo);
                    }
                } catch (Exception e) {
                    // UI 绑定异常兜底
                    android.util.Log.e("BatteryHealthFragment", "Failed to bind battery info", e);
                    showErrorState();
                }
            });
        });
    }

    /**
     * 显示错误状态：使用 StateLayoutHelper 显示错误页面，并提供重试按钮。
     * 如果 StateLayoutHelper 不可用，则回退到显示 "--" 的检测中状态。
     */
    private void showErrorState() {
        if (!isAdded() || getContext() == null) return;
        if (stateLayoutHelper != null) {
            stateLayoutHelper.showError(getString(R.string.status_load_failed), v -> {
                // 重试：先显示 loading，再重新加载数据
                if (stateLayoutHelper != null) {
                    stateLayoutHelper.showLoading(null);
                }
                updateBatteryData();
            });
        } else {
            // StateLayoutHelper 不可用时回退到旧行为
            showDetecting();
        }
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private void persistBatteryInfo(BatteryInfo info) {
        try {
            AppDatabase db = BatteryHealthApplication.getDatabase();
            if (db != null) {
                db.batteryInfoDao().insert(info.copy());
            }
        } catch (Exception e) {
            // 数据库写入失败不应影响 UI 展示
        }
    }

    private void bindBatteryInfo(BatteryInfo info) {
        if (!isAdded() || getContext() == null) return;
        if (info == null) {
            showDetecting();
            return;
        }

        // 数据加载完成，显示内容
        if (stateLayoutHelper != null && stateLayoutHelper.getCurrentState() != StateLayoutHelper.State.CONTENT) {
            stateLayoutHelper.showContent();
        }

        int health = info.hasValidHealthData() ? Math.round(info.getHealthPercentage()) : -1;
        int level = info.getLevel();

        safeSetText(tvBatteryLevel, level >= 0 ? String.format(Locale.getDefault(), "%d%%", level) : "--");
        safeSetText(tvChargingStatus, getChargingStatusText(info));
        safeSetText(tvCurrentNow, String.format(Locale.getDefault(), "%.0f mA", Math.abs(info.getCurrentNow() / 1000f)));

        int currentCapacity = info.getCurrentCapacity();
        safeSetText(tvCapacity, currentCapacity > 0
                ? String.format(Locale.getDefault(), "%d / %d mAh", currentCapacity, Math.max(currentCapacity, info.getDesignCapacity()))
                : String.format(Locale.getDefault(), "%d mAh", info.getDesignCapacity()));

        safeSetText(tvCycleCount, batteryDataManager.formatCycleCount(info));
        safeSetText(tvTemperature, String.format(Locale.getDefault(), "%.1f°C", info.getTemperature()));
        safeSetText(tvVoltage, String.format(Locale.getDefault(), "%.2f V", info.getVoltage() / 1000f));
        safeSetText(tvTechnology, info.getTechnology());
        safeSetText(tvBatterySource, formatBatterySource(info));

        if (tvHealthSource != null) {
            tvHealthSource.setText(batteryDataManager.getHealthSourceText());
        }
        if (health >= 0) {
            safeSetText(tvHealthPercentage, String.format(Locale.getDefault(), "%d%%", health));
            safeSetText(tvHealthGrade, String.format(Locale.getDefault(), "等级 %s", info.getHealthGrade()));
            safeSetText(tvHealthStatus, getHealthStatusText(health));
            if (healthRing != null) {
                UiAnimationHelper.animateRingProgress(healthRing, health);
            }
        } else {
            safeSetText(tvHealthPercentage, "--");
            safeSetText(tvHealthGrade, "等级 --");
            safeSetText(tvHealthStatus, getString(R.string.health_status_no_data));
        }
    }

    private void safeSetText(TextView tv, String text) {
        if (tv != null) tv.setText(text);
    }

    private String getChargingStatusText(BatteryInfo info) {
        if (!isAdded()) return "";
        int status = info.getStatus();
        if (status == android.os.BatteryManager.BATTERY_STATUS_CHARGING) {
            return getString(R.string.status_charging);
        } else if (status == android.os.BatteryManager.BATTERY_STATUS_FULL) {
            return getString(R.string.status_fully_charged);
        } else if (status == android.os.BatteryManager.BATTERY_STATUS_DISCHARGING) {
            return getString(R.string.status_discharging);
        } else if (status == android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
            return getString(R.string.status_not_charging_short);
        }
        return getString(R.string.status_unknown);
    }

    private String getHealthStatusText(int health) {
        if (!isAdded()) return "";
        if (health >= 90) return getString(R.string.status_excellent);
        if (health >= 80) return getString(R.string.status_good);
        if (health >= 60) return getString(R.string.status_fair);
        return getString(R.string.status_poor);
    }

    private String formatBatterySource(BatteryInfo info) {
        if (!isAdded()) return "";
        String source = info.getBatterySource();
        if ("original".equals(source)) {
            return getString(R.string.battery_source_original_confidence, (int) (info.getBatterySourceConfidence() * 100));
        } else if ("third_party".equals(source)) {
            return getString(R.string.battery_source_third_party_confidence, (int) (info.getBatterySourceConfidence() * 100));
        }
        return getString(R.string.battery_source_unverifiable);
    }

    private void showDetecting() {
        if (!isAdded() || getContext() == null) return;
        safeSetText(tvHealthPercentage, "--");
        safeSetText(tvHealthGrade, "等级 --");
        safeSetText(tvHealthStatus, getString(R.string.status_detecting));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 问题修复：清理 StateLayoutHelper，防止 Fragment 被 ViewPager2 复用时
        // 旧的 overlay 视图仍然附着在已销毁的视图层级上，导致新实例初始化失败或显示异常。
        stopPeriodicUpdate();
        handler.removeCallbacksAndMessages(null);
        mainHandler.removeCallbacksAndMessages(null);
        if (stateLayoutHelper != null) {
            try {
                // 问题修复：调用 cleanup() 彻底移除 overlay 容器，恢复原始视图层级
                stateLayoutHelper.cleanup();
            } catch (Exception e) {
                android.util.Log.e("BatteryHealthFragment", "Error cleaning up StateLayoutHelper", e);
            }
            stateLayoutHelper = null;
        }
        stateLayoutInitialized = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
