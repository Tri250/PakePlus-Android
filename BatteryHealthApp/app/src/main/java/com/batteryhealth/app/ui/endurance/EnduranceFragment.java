package com.batteryhealth.app.ui.endurance;

import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.utils.BatteryConsumptionAnalyzer;
import com.batteryhealth.app.utils.BatteryDataManager;

import java.util.Locale;

public class EnduranceFragment extends Fragment {

    private TextView tvEnduranceHours;
    private TextView tvEnduranceMeta;
    private TextView tvMetricBattery, tvMetricDischarge, tvMetricTemp;
    private TextView tvChargingStatus, tvUsedTime, tvConsumedBattery, tvEstimatedFull, tvScreenOnTime;
    private TextView tvScreenPower, tvSystemPower, tvAppsPower;
    private TextView tvWearableStatus, tvWearableBattery, tvWearableEndurance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    private float dischargeRate = 0f;

    /** 最近一次耗电分析结果，缓存供 UI 刷新使用 */
    private BatteryConsumptionAnalyzer.Result lastAnalysisResult;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_endurance, container, false);
        initViews(view);
        animateEntry(view);
        return view;
    }

    private void initViews(View view) {
        tvEnduranceHours = view.findViewById(R.id.tv_endurance_hours);
        tvEnduranceMeta = view.findViewById(R.id.tv_endurance_meta);
        tvMetricBattery = view.findViewById(R.id.tv_metric_battery);
        tvMetricDischarge = view.findViewById(R.id.tv_metric_discharge);
        tvMetricTemp = view.findViewById(R.id.tv_metric_temp);
        tvChargingStatus = view.findViewById(R.id.tv_charging_status);
        tvUsedTime = view.findViewById(R.id.tv_used_time);
        tvConsumedBattery = view.findViewById(R.id.tv_consumed_battery);
        tvEstimatedFull = view.findViewById(R.id.tv_estimated_full);
        tvScreenOnTime = view.findViewById(R.id.tv_screen_on_time);
        tvScreenPower = view.findViewById(R.id.tv_screen_power);
        tvSystemPower = view.findViewById(R.id.tv_system_power);
        tvAppsPower = view.findViewById(R.id.tv_apps_power);
        tvWearableStatus = view.findViewById(R.id.tv_wearable_status);
        tvWearableBattery = view.findViewById(R.id.tv_wearable_battery);
        tvWearableEndurance = view.findViewById(R.id.tv_wearable_endurance);
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerBatteryReceiver();
        startPeriodicUpdate();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterBatteryReceiver();
        stopPeriodicUpdate();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 在 onDestroyView 中也停止更新，防止 onPause 因异常跳过导致 Handler 继续持有引用
        stopPeriodicUpdate();
    }

    private void registerBatteryReceiver() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requireContext().registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(batteryReceiver, filter);
        }
    }

    private void unregisterBatteryReceiver() {
        try {
            requireContext().unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void startPeriodicUpdate() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateBatteryData();
                handler.postDelayed(this, 3000);
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
            updateFromIntent(intent);
        }
    };

    private void updateBatteryData() {
        Intent intent = requireContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent != null) {
            updateFromIntent(intent);
        }
    }

    private void updateFromIntent(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPct = (int) ((level / (float) scale) * 100);

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        String chargingStatus = isCharging ? getString(R.string.status_charging) : getString(R.string.status_discharging);

        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        float tempC = temp / 10f;

        // 通过 BatteryConsumptionAnalyzer 获取真实耗电分析
        if (lastAnalysisResult == null) {
            runAnalysisAsync();
        }

        // 从分析结果获取真实放电速率
        BatteryConsumptionAnalyzer.Result analysis = lastAnalysisResult;
        if (analysis != null && analysis.systemEstimatedHours > 0 && batteryPct > 0) {
            // 根据系统预估续航和当前电量反算真实放电速率
            dischargeRate = batteryPct / (float) analysis.systemEstimatedHours;
        }

        // 如果分析结果不可用，从 BatteryDataManager 获取电流数据估算放电速率
        if (dischargeRate <= 0) {
            BatteryDataManager bdm = getBatteryDataManager();
            if (bdm != null) {
                int currentMa = bdm.readCurrentMa();
                if (currentMa != 0) {
                    // 通过电流和容量估算放电速率
                    BatteryManager bm = (BatteryManager) requireContext().getSystemService(Context.BATTERY_SERVICE);
                    if (bm != null) {
                        int capacityMicroAh = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                        if (capacityMicroAh == Integer.MIN_VALUE || capacityMicroAh == 0) {
                            capacityMicroAh = bm.getIntProperty(24); // BATTERY_PROPERTY_CHARGE_FULL
                        }
                        int capacityMah = -1;
                        if (capacityMicroAh > 100000) {
                            capacityMah = capacityMicroAh / 1000;
                        } else if (capacityMicroAh > 100) {
                            capacityMah = capacityMicroAh;
                        }
                        if (capacityMah > 0 && batteryPct > 0) {
                            float remainingMah = capacityMah * (batteryPct / 100f);
                            float absCurrentMa = Math.abs(currentMa);
                            if (absCurrentMa > 0) {
                                float remainingHours = remainingMah / absCurrentMa;
                                dischargeRate = batteryPct / remainingHours;
                            }
                        }
                    }
                }
            }
        }

        // Endurance estimate
        float remainingHours;
        if (analysis != null && analysis.systemEstimatedHours > 0) {
            // 使用系统预估续航（基于当前电量比例）
            remainingHours = (float) analysis.systemEstimatedHours;
        } else if (dischargeRate > 0) {
            remainingHours = batteryPct / dischargeRate;
        } else {
            remainingHours = 0;
        }

        int hours = (int) remainingHours;
        int minutes = (int) ((remainingHours - hours) * 60);
        tvEnduranceHours.setText(String.valueOf(hours));
        tvEnduranceMeta.setText(String.format(Locale.getDefault(),
                getString(R.string.meta_endurance), batteryPct, dischargeRate));

        // Quick metrics
        tvMetricBattery.setText(String.format(Locale.getDefault(), "%d%%", batteryPct));
        tvMetricDischarge.setText(String.format(Locale.getDefault(), "%.1f%%/h", dischargeRate));
        tvMetricTemp.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));

        // Details
        tvChargingStatus.setText(chargingStatus);
        tvUsedTime.setText(formatDuration(SystemClock.elapsedRealtime()));
        tvConsumedBattery.setText(String.format(Locale.getDefault(), "%d%%", 100 - batteryPct));

        // 预计充满时间：充电时使用 BatteryConsumptionAnalyzer 的系统预估
        if (isCharging) {
            if (analysis != null && analysis.systemEstimatedHours > 0) {
                // 充电时 systemEstimatedHours 表示充满预估
                int fullHours = (int) analysis.systemEstimatedHours;
                int fullMinutes = (int) ((analysis.systemEstimatedHours - fullHours) * 60);
                tvEstimatedFull.setText(String.format(Locale.getDefault(), "%d小时%d分", fullHours, fullMinutes));
            } else {
                tvEstimatedFull.setText(getString(R.string.status_calculating));
            }
        } else {
            // 放电时显示预估续航时间
            if (remainingHours > 0) {
                int estHours = (int) remainingHours;
                int estMinutes = (int) ((remainingHours - estHours) * 60);
                tvEstimatedFull.setText(String.format(Locale.getDefault(), "%d小时%d分", estHours, estMinutes));
            } else {
                tvEstimatedFull.setText("--");
            }
        }

        // 使用 UsageStatsManager 获取真实屏幕亮屏时间
        long screenOnTimeMs = queryScreenOnTime();
        tvScreenOnTime.setText(formatDuration(screenOnTimeMs));

        updatePowerRanking();
        updateWearableData();
    }

    private void updatePowerRanking() {
        if (lastAnalysisResult != null
                && lastAnalysisResult.screenPowerPercent >= 0
                && lastAnalysisResult.systemPowerPercent >= 0
                && lastAnalysisResult.appsPowerPercent >= 0) {
            tvScreenPower.setText(String.format(Locale.getDefault(), "%.1f%%",
                    lastAnalysisResult.screenPowerPercent));
            tvSystemPower.setText(String.format(Locale.getDefault(), "%.1f%%",
                    lastAnalysisResult.systemPowerPercent));
            tvAppsPower.setText(String.format(Locale.getDefault(), "%.1f%%",
                    lastAnalysisResult.appsPowerPercent));
        } else {
            // 真实数据未就绪，诚实展示"--"而非硬编码假数据
            tvScreenPower.setText("--");
            tvSystemPower.setText("--");
            tvAppsPower.setText("--");
        }
    }

    private void updateWearableData() {
        tvWearableStatus.setText(getString(R.string.status_not_connected));
        tvWearableBattery.setText("--");
        tvWearableEndurance.setText("--");
    }

    /**
     * 在后台线程执行耗电分析，避免阻塞 UI。
     */
    private void runAnalysisAsync() {
        new Thread(() -> {
            try {
                BatteryConsumptionAnalyzer.Result result =
                        BatteryConsumptionAnalyzer.analyze(requireContext(), 24 * 60 * 60 * 1000L);
                if (result != null) {
                    lastAnalysisResult = result;
                    // 切回主线程刷新 UI
                    if (isAdded()) {
                        handler.post(this::updateBatteryData);
                    }
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    /**
     * 通过 UsageStatsManager 查询真实屏幕亮屏时间。
     * 需要 PACKAGE_USAGE_STATS 权限，无权限时回退到 0。
     */
    private long queryScreenOnTime() {
        if (!BatteryConsumptionAnalyzer.hasUsageAccess(requireContext())) {
            return 0;
        }
        try {
            UsageStatsManager usm = (UsageStatsManager) requireContext()
                    .getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return 0;

            long endTime = System.currentTimeMillis();
            long startTime = endTime - 24 * 60 * 60 * 1000L; // 最近 24 小时

            long totalForegroundMs = 0;
            java.util.Map<String, android.app.usage.UsageStats> stats = usm.queryAndAggregateUsageStats(startTime, endTime);
            if (stats != null) {
                for (android.app.usage.UsageStats usageStats : stats.values()) {
                    totalForegroundMs += usageStats.getTotalTimeInForeground();
                }
            }
            return totalForegroundMs;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 从 MainActivity 获取 BatteryDataManager 实例。
     */
    private BatteryDataManager getBatteryDataManager() {
        if (getActivity() instanceof MainActivity) {
            return ((MainActivity) getActivity()).getBatteryDataManager();
        }
        return null;
    }

    private String formatDuration(long ms) {
        long hours = ms / (1000 * 60 * 60);
        long minutes = (ms % (1000 * 60 * 60)) / (1000 * 60);
        return String.format(Locale.getDefault(), "%d小时%d分", hours, minutes);
    }
}
