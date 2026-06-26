package com.batteryhealth.app.ui.endurance;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.batteryhealth.app.R;
import com.batteryhealth.app.ui.viewmodel.EnduranceViewModel;
import com.batteryhealth.app.utils.BatteryConsumptionAnalyzer;
import com.batteryhealth.app.utils.FragmentErrorViewHelper;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.List;
import java.util.Locale;

/**
 * 续航分析页面 — ViewModel 为唯一数据源，Fragment 仅负责 UI 展示。
 */
public class EnduranceFragment extends Fragment {

    private static final String TAG = "EnduranceFragment";

    private TextView tvBatteryLevel, tvDischargeRate, tvTemperature, tvChargingStatus;
    private TextView tvEstimatedEndurance, tvEstimatedChargeTime, tvUsedTime;
    private TextView tvEnduranceGrade, tvEnduranceGradeDescription;
    private TextView tvScreenPower, tvSystemPower, tvAppsPower;
    private TextView tvScreenOnTime;
    private LinearLayout containerTopApps;
    private LinearLayout containerPowerTips;
    private View abnormalDischargeWarning;

    // 穿戴设备区域（整体隐藏）
    private View wearableSection;

    private EnduranceViewModel viewModel;

    // 应用启动时间
    private final long appStartTimeMs = System.currentTimeMillis();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.fragment_endurance, container, false);
            initViews(view);
            initViewModel();
            animateEntry(view);
            return view;
        } catch (Exception e) {
            Log.e(TAG, "Error creating view", e);
            Context ctx = getContext();
            if (ctx == null && container != null) ctx = container.getContext();
            return FragmentErrorViewHelper.createErrorView(ctx, e);
        }
    }

    private void initViews(View view) {
        tvBatteryLevel = view.findViewById(R.id.tv_battery_level);
        tvDischargeRate = view.findViewById(R.id.tv_discharge_rate);
        tvTemperature = view.findViewById(R.id.tv_temperature);
        tvChargingStatus = view.findViewById(R.id.tv_charging_status);

        tvEstimatedEndurance = view.findViewById(R.id.tv_estimated_endurance);
        tvEstimatedChargeTime = view.findViewById(R.id.tv_estimated_charge_time);
        tvUsedTime = view.findViewById(R.id.tv_used_time);

        tvEnduranceGrade = view.findViewById(R.id.tv_endurance_grade);
        tvEnduranceGradeDescription = view.findViewById(R.id.tv_endurance_grade_description);

        tvScreenPower = view.findViewById(R.id.tv_screen_power);
        tvSystemPower = view.findViewById(R.id.tv_system_power);
        tvAppsPower = view.findViewById(R.id.tv_apps_power);

        tvScreenOnTime = view.findViewById(R.id.tv_screen_on_time);

        containerTopApps = view.findViewById(R.id.container_top_apps);
        containerPowerTips = view.findViewById(R.id.container_power_tips);

        abnormalDischargeWarning = view.findViewById(R.id.abnormal_discharge_warning);

        // 穿戴设备区域整体隐藏（无实际蓝牙/GMS集成，避免用户困惑）
        wearableSection = view.findViewById(R.id.section_wearable);
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(EnduranceViewModel.class);

        // 电量
        viewModel.getBatteryLevel().observe(getViewLifecycleOwner(), level -> {
            if (level != null) {
                tvBatteryLevel.setText(String.format(Locale.getDefault(), "%d%%", level));
            }
        });

        // 温度
        viewModel.getTemperature().observe(getViewLifecycleOwner(), temp -> {
            if (temp != null) {
                tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", temp));
            }
        });

        // 充电状态
        viewModel.getIsCharging().observe(getViewLifecycleOwner(), charging -> {
            if (charging != null) {
                tvChargingStatus.setText(charging ? "充电中" : "放电中");
                tvChargingStatus.setTextColor(ContextCompat.getColor(requireContext(),
                        charging ? R.color.ios_green : R.color.ios_label_primary));
            }
        });

        // 放电速率
        viewModel.getDischargeRate().observe(getViewLifecycleOwner(), rate -> {
            if (rate != null && rate > 0) {
                tvDischargeRate.setText(String.format(Locale.getDefault(), "%.1f%%/h", rate));
            } else {
                tvDischargeRate.setText("--");
            }
        });

        // 放电续航时间
        viewModel.getEstimatedEnduranceHours().observe(getViewLifecycleOwner(), hours -> {
            if (hours != null && hours > 0) {
                tvEstimatedEndurance.setText(formatHours(hours));
            } else {
                tvEstimatedEndurance.setText("--");
            }
        });

        // 充电预估时间
        viewModel.getEstimatedChargeHours().observe(getViewLifecycleOwner(), hours -> {
            if (hours != null && hours > 0) {
                tvEstimatedChargeTime.setText(formatHours(hours));
            } else {
                tvEstimatedChargeTime.setText("--");
            }
        });

        // 已使用时间（应用运行时间）
        updateUsedTime();

        // 续航等级
        viewModel.getEnduranceGrade().observe(getViewLifecycleOwner(), grade -> {
            if (grade != null) {
                tvEnduranceGrade.setText(grade);
                // 根据等级设置颜色
                int color;
                switch (grade) {
                    case "续航充裕":
                    case "续航良好":
                        color = ContextCompat.getColor(requireContext(), R.color.ios_green);
                        break;
                    case "续航一般":
                        color = ContextCompat.getColor(requireContext(), R.color.ios_orange);
                        break;
                    case "续航偏低":
                    case "电量告急":
                        color = ContextCompat.getColor(requireContext(), R.color.ios_red);
                        break;
                    case "即将充满":
                    case "充电中":
                        color = ContextCompat.getColor(requireContext(), R.color.ios_green);
                        break;
                    default:
                        color = ContextCompat.getColor(requireContext(), R.color.ios_label_primary);
                        break;
                }
                tvEnduranceGrade.setTextColor(color);
            }
        });

        // 续航等级描述
        viewModel.getEnduranceGradeDescription().observe(getViewLifecycleOwner(), desc -> {
            if (desc != null) {
                tvEnduranceGradeDescription.setText(desc);
            }
        });

        // 耗电异常提醒
        viewModel.getIsAbnormalDischarge().observe(getViewLifecycleOwner(), abnormal -> {
            if (abnormalDischargeWarning != null) {
                abnormalDischargeWarning.setVisibility(abnormal != null && abnormal ? View.VISIBLE : View.GONE);
            }
        });

        // 耗电排行百分比
        viewModel.getAnalysisResult().observe(getViewLifecycleOwner(), analysis -> {
            if (analysis != null && analysis.dataStatus == 2) {
                tvScreenPower.setText(String.format(Locale.getDefault(), "%.1f%%", analysis.screenPowerPercent));
                tvSystemPower.setText(String.format(Locale.getDefault(), "%.1f%%", analysis.systemPowerPercent));
                tvAppsPower.setText(String.format(Locale.getDefault(), "%.1f%%", analysis.appsPowerPercent));
            } else if (analysis != null && analysis.dataStatus == 1) {
                tvScreenPower.setText("--");
                tvSystemPower.setText("--");
                tvAppsPower.setText("--");
            } else {
                tvScreenPower.setText("--");
                tvSystemPower.setText("--");
                tvAppsPower.setText("--");
            }
        });

        // TOP 耗电应用列表
        viewModel.getTopConsumers().observe(getViewLifecycleOwner(), consumers -> {
            renderTopApps(consumers);
        });

        // 屏幕亮屏时间
        viewModel.getScreenOnTimeMs().observe(getViewLifecycleOwner(), ms -> {
            if (ms != null) {
                if (ms < 0) {
                    tvScreenOnTime.setText(getString(R.string.status_no_permission_hint));
                } else {
                    tvScreenOnTime.setText(formatDuration(ms));
                }
            }
        });

        // 省电建议
        viewModel.getPowerSavingTips().observe(getViewLifecycleOwner(), tips -> {
            renderPowerTips(tips);
        });
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 定期刷新数据（每3秒）
        viewModel.refreshData();
        startPeriodicRefresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPeriodicRefresh();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 停止刷新循环并清理 Handler 待执行回调，避免内存泄漏
        stopPeriodicRefresh();
        refreshHandler.removeCallbacksAndMessages(null);
    }

    private android.os.Handler refreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable refreshRunnable;

    private void startPeriodicRefresh() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                // 仅在 Fragment 可见时继续刷新；不可见时停止循环，避免后台无意义资源消耗
                if (!isResumed()) {
                    stopPeriodicRefresh();
                    return;
                }
                viewModel.refreshData();
                updateUsedTime();
                refreshHandler.postDelayed(this, 3000);
            }
        };
        refreshHandler.postDelayed(refreshRunnable, 3000);
    }

    private void stopPeriodicRefresh() {
        if (refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
            refreshRunnable = null;
        }
    }

    /**
     * 更新应用已使用时间。
     */
    private void updateUsedTime() {
        long usedMs = System.currentTimeMillis() - appStartTimeMs;
        tvUsedTime.setText(formatDuration(usedMs));
    }

    /**
     * 渲染 TOP 耗电应用列表。
     */
    private void renderTopApps(List<BatteryConsumptionAnalyzer.AppConsumption> consumers) {
        if (containerTopApps == null) return;
        containerTopApps.removeAllViews();

        if (consumers == null || consumers.isEmpty()) {
            TextView emptyView = new TextView(requireContext());
            emptyView.setText("暂无耗电应用数据，请授予使用情况访问权限");
            emptyView.setTextAppearance(requireContext(), R.style.iOSBody_Secondary);
            emptyView.setPadding(dpToPx(22), dpToPx(12), dpToPx(22), dpToPx(12));
            containerTopApps.addView(emptyView);
            return;
        }

        for (int i = 0; i < consumers.size(); i++) {
            BatteryConsumptionAnalyzer.AppConsumption app = consumers.get(i);

            if (i > 0) {
                View separator = new View(requireContext());
                separator.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                separator.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_separator));
                containerTopApps.addView(separator);
            }

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dpToPx(22), dpToPx(12), dpToPx(22), dpToPx(12));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            // 排名
            TextView tvRank = new TextView(requireContext());
            tvRank.setText(String.valueOf(i + 1));
            tvRank.setTextAppearance(requireContext(), R.style.iOSBody_Secondary);
            tvRank.setTextSize(14);
            LinearLayout.LayoutParams rankParams = new LinearLayout.LayoutParams(
                    dpToPx(28), LinearLayout.LayoutParams.WRAP_CONTENT);
            tvRank.setLayoutParams(rankParams);

            // 应用名
            TextView tvName = new TextView(requireContext());
            tvName.setText(app.displayName != null ? app.displayName : app.packageName);
            tvName.setTextAppearance(requireContext(), R.style.iOSBody);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT);
            nameParams.weight = 1;
            tvName.setLayoutParams(nameParams);

            // 耗电百分比
            TextView tvPercent = new TextView(requireContext());
            tvPercent.setText(String.format(Locale.getDefault(), "%.1f%%", app.percent));
            tvPercent.setTextAppearance(requireContext(), R.style.iOSBody);
            tvPercent.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_orange));

            row.addView(tvRank);
            row.addView(tvName);
            row.addView(tvPercent);
            containerTopApps.addView(row);
        }
    }

    /**
     * 渲染省电建议列表。
     */
    private void renderPowerTips(List<String> tips) {
        if (containerPowerTips == null) return;
        containerPowerTips.removeAllViews();

        if (tips == null || tips.isEmpty()) return;

        for (int i = 0; i < tips.size(); i++) {
            String tip = tips.get(i);

            if (i > 0) {
                View separator = new View(requireContext());
                separator.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                separator.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_separator));
                containerPowerTips.addView(separator);
            }

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dpToPx(22), dpToPx(12), dpToPx(22), dpToPx(12));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            // 图标点
            TextView tvDot = new TextView(requireContext());
            tvDot.setText("•");
            tvDot.setTextAppearance(requireContext(), R.style.iOSBody);
            tvDot.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_green));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                    dpToPx(20), LinearLayout.LayoutParams.WRAP_CONTENT);
            tvDot.setLayoutParams(dotParams);

            // 建议文本
            TextView tvTip = new TextView(requireContext());
            tvTip.setText(tip);
            tvTip.setTextAppearance(requireContext(), R.style.iOSBody);
            tvTip.setLineSpacing(dpToPx(3), 1f);
            LinearLayout.LayoutParams tipParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT);
            tipParams.weight = 1;
            tvTip.setLayoutParams(tipParams);

            row.addView(tvDot);
            row.addView(tvTip);
            containerPowerTips.addView(row);
        }
    }

    // ========== 工具方法 ==========

    private String formatHours(float hours) {
        int h = (int) hours;
        int m = (int) ((hours - h) * 60);
        if (h > 0) {
            return String.format(Locale.getDefault(), "%d小时%d分", h, m);
        }
        return String.format(Locale.getDefault(), "%d分钟", m);
    }

    private String formatDuration(long ms) {
        long hours = ms / (1000 * 60 * 60);
        long minutes = (ms % (1000 * 60 * 60)) / (1000 * 60);
        return String.format(Locale.getDefault(), "%d小时%d分", hours, minutes);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
