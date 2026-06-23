package com.batteryhealth.app.ui.power;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.ChargeProtocolDetector;
import com.batteryhealth.app.utils.UiAnimationHelper;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class PowerFragment extends Fragment {

    private TextView tvWatt, tvPowerType;
    private ProgressBar progressCharge;
    private TextView tvVoltage, tvCurrent, tvChargeStage, tvTemperature, tvBatteryLevel, tvEstimatedFull;
    private TextView tvChargeCount, tvAvgPower, tvTotalChargeTime, tvTotalCharged;
    private LineChart chartPower;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private BatteryDataManager batteryDataManager;
    private List<Float> powerHistory = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 30;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_power, container, false);
        initViews(view);
        animateEntry(view);
        return view;
    }

    private void initViews(View view) {
        tvWatt = view.findViewById(R.id.tv_watt);
        tvPowerType = view.findViewById(R.id.tv_power_type);
        progressCharge = view.findViewById(R.id.progress_charge);
        tvVoltage = view.findViewById(R.id.tv_voltage);
        tvCurrent = view.findViewById(R.id.tv_current);
        tvChargeStage = view.findViewById(R.id.tv_charge_stage);
        tvTemperature = view.findViewById(R.id.tv_temperature);
        tvBatteryLevel = view.findViewById(R.id.tv_battery_level);
        tvEstimatedFull = view.findViewById(R.id.tv_estimated_full);
        tvChargeCount = view.findViewById(R.id.tv_charge_count);
        tvAvgPower = view.findViewById(R.id.tv_avg_power);
        tvTotalChargeTime = view.findViewById(R.id.tv_total_charge_time);
        tvTotalCharged = view.findViewById(R.id.tv_total_charged);
        chartPower = view.findViewById(R.id.chart_power);
        initChart();
    }

    private void initChart() {
        chartPower.setDrawGridBackground(false);
        chartPower.getDescription().setEnabled(false);
        chartPower.setTouchEnabled(true);
        chartPower.setDragEnabled(true);
        chartPower.setScaleEnabled(false);
        chartPower.setPinchZoom(false);

        XAxis xAxis = chartPower.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setLabelCount(5);
        xAxis.setTextColor(getResources().getColor(R.color.label_3));
        xAxis.setTextSize(10f);

        YAxis leftAxis = chartPower.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(getResources().getColor(R.color.separator));
        leftAxis.setTextColor(getResources().getColor(R.color.label_3));
        leftAxis.setTextSize(10f);
        leftAxis.setAxisMinimum(0f);

        YAxis rightAxis = chartPower.getAxisRight();
        rightAxis.setEnabled(false);

        chartPower.getLegend().setEnabled(false);

        LineData data = new LineData();
        data.setValueTextColor(getResources().getColor(R.color.label_3));
        chartPower.setData(data);
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从 MainActivity 获取共享的 BatteryDataManager
        if (getActivity() instanceof MainActivity) {
            batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
        }
        if (batteryDataManager == null) {
            batteryDataManager = new BatteryDataManager(requireContext());
        }
        registerBatteryReceiver();
        startPeriodicUpdate();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterBatteryReceiver();
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

    private void updateBatteryData() {
        if (batteryDataManager == null) return;

        // 在后台线程获取完整电池信息（含 sysfs 读取）
        new Thread(() -> {
            try {
                batteryDataManager.refreshFromStickyIntent();
                BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
                if (info != null && isAdded()) {
                    // 同时查询今日充电统计
                    TodayChargeStats stats = queryTodayChargeStats();
                    handler.post(() -> updateUI(info, stats));
                }
            } catch (Exception e) {
                // 静默处理
            }
        }).start();
    }

    private void updateUI(BatteryInfo info, TodayChargeStats stats) {
        if (info == null || !isAdded()) return;

        int batteryPct = info.getLevel();
        boolean isCharging = info.isCharging();

        // 电压
        float voltageV = info.getVoltage() / 1000f;
        // 电流（info.getCurrentNow() 单位 uA）
        float currentMa = Math.abs(info.getCurrentNow()) / 1000f;
        float currentA = currentMa / 1000f;

        // 功率
        float watt = info.getChargingPower();

        // 温度
        float tempC = info.getTemperature();

        // 使用 ChargeProtocolDetector 识别充电协议
        ChargeProtocolDetector.Result protocolResult = ChargeProtocolDetector.detect(requireContext(), watt);

        // Update hero
        tvWatt.setText(String.format(Locale.getDefault(), "%.1f", watt));

        // 使用 BatteryDataManager.getPowerLevelLabel() 进行功率类型分类
        String powerType;
        if (!isCharging) {
            powerType = getString(R.string.status_not_charging);
        } else if (batteryDataManager.isNearOfficialFastCharge(watt)) {
            powerType = protocolResult.primary;
        } else {
            powerType = batteryDataManager.getPowerLevelLabel(watt);
        }
        tvPowerType.setText(powerType);
        UiAnimationHelper.animateProgressBar(progressCharge, batteryPct);

        // Update details
        tvVoltage.setText(String.format(Locale.getDefault(), "%.2f V", voltageV));
        tvCurrent.setText(String.format(Locale.getDefault(), "%.0f mA", currentMa));
        tvChargeStage.setText(batteryPct >= 80 ? getString(R.string.stage_trickle) : getString(R.string.stage_fast));
        tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));
        tvBatteryLevel.setText(String.format(Locale.getDefault(), "%d%%", batteryPct));
        tvEstimatedFull.setText(isCharging ? calculateTimeToFull(batteryPct, currentA, info) : "--");

        // Today stats (from database)
        tvChargeCount.setText(String.format(Locale.getDefault(), "%d", stats.sessionCount));
        tvAvgPower.setText(String.format(Locale.getDefault(), "%.1f W", watt));
        tvTotalChargeTime.setText(formatMinutes(stats.totalChargeMinutes));
        tvTotalCharged.setText(String.format(Locale.getDefault(), "%d mAh", stats.totalChargedMah));

        updatePowerChart(watt);
    }

    /**
     * 查询今日充电统计：充电会话数、总充电时间、总充电量。
     */
    private TodayChargeStats queryTodayChargeStats() {
        TodayChargeStats stats = new TodayChargeStats();
        try {
            BatteryHealthApplication app = (BatteryHealthApplication) requireActivity().getApplication();
            if (app == null) return stats;
            var db = app.getDatabase();
            if (db == null) return stats;

            // 今日零点时间戳
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long todayStart = cal.getTimeInMillis();

            // 查询今日所有电池记录
            List<BatteryInfo> records = db.batteryInfoDao().getSince(todayStart);
            if (records == null || records.isEmpty()) return stats;

            // 统计充电会话数：连续充电记录为一段，中间出现非充电状态则分段
            int sessionCount = 0;
            boolean inChargingSession = false;
            long sessionStartTime = 0;
            long totalChargeMs = 0;
            int startLevel = -1;
            int endLevel = -1;
            int designCapacity = -1;

            // 获取设计容量用于计算充电量
            BatteryInfo latestInfo = batteryDataManager.getCurrentBatteryInfo();
            if (latestInfo != null) {
                designCapacity = latestInfo.getDesignCapacity();
            }

            for (BatteryInfo record : records) {
                boolean charging = record.isCharging();
                if (charging && !inChargingSession) {
                    // 新充电会话开始
                    sessionCount++;
                    inChargingSession = true;
                    sessionStartTime = record.getTimestamp();
                    startLevel = record.getLevel();
                } else if (!charging && inChargingSession) {
                    // 充电会话结束
                    totalChargeMs += record.getTimestamp() - sessionStartTime;
                    endLevel = record.getLevel();
                    inChargingSession = false;
                }
            }
            // 如果当前仍在充电，计入当前会话
            if (inChargingSession) {
                totalChargeMs += System.currentTimeMillis() - sessionStartTime;
                endLevel = records.get(records.size() - 1).getLevel();
            }

            stats.sessionCount = sessionCount;
            stats.totalChargeMinutes = (int) (totalChargeMs / 60000);

            // 计算总充电量：使用电量差值 × 设计容量估算
            if (startLevel >= 0 && endLevel >= 0 && designCapacity > 0) {
                int levelDiff = endLevel - startLevel;
                if (levelDiff < 0) levelDiff = 0;
                stats.totalChargedMah = (int) (levelDiff / 100f * designCapacity);
            }
        } catch (Exception e) {
            // 静默处理
        }
        return stats;
    }

    /**
     * 基于实际电流和剩余容量计算充满所需时间。
     * 使用设计容量（或当前满充容量）计算剩余需要的电量，再除以当前充电电流。
     */
    private String calculateTimeToFull(int batteryPct, float currentA, BatteryInfo info) {
        if (currentA <= 0) return "--";

        // 获取电池容量（mAh）
        int capacityMah = info.getCurrentCapacity();
        if (capacityMah <= 0) {
            capacityMah = info.getDesignCapacity();
        }
        if (capacityMah <= 0) return "--";

        // 考虑充电限制百分比
        int limitPct = batteryDataManager.getChargingLimitPercent();
        int remainingPct = limitPct - batteryPct;
        if (remainingPct <= 0) return "--";

        // 剩余需要充入的电量（mAh）
        float remainingMah = capacityMah * (remainingPct / 100f);

        // 考虑充电效率（通常 85%-95%，取 90%）
        float efficiency = 0.9f;
        // 充电效率在涓流阶段更低
        if (batteryPct >= 80) {
            efficiency = 0.6f;
        } else if (batteryPct >= 60) {
            efficiency = 0.8f;
        }

        // 充满所需时间（小时）= 剩余电量 / (电流 × 效率)
        float hours = remainingMah / (currentA * 1000f * efficiency);
        int mins = Math.max(1, (int) (hours * 60));
        return formatMinutes(mins);
    }

    /**
     * 格式化分钟数为可读字符串。
     */
    private String formatMinutes(int totalMinutes) {
        if (totalMinutes <= 0) return "--";
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        if (hours > 0 && mins > 0) {
            return String.format(Locale.getDefault(), "%d小时%d分", hours, mins);
        } else if (hours > 0) {
            return String.format(Locale.getDefault(), "%d小时", hours);
        } else {
            return String.format(Locale.getDefault(), "%d分", mins);
        }
    }

    /**
     * 今日充电统计数据结构。
     */
    private static class TodayChargeStats {
        int sessionCount = 0;
        int totalChargeMinutes = 0;
        int totalChargedMah = 0;
    }

    private void updatePowerChart(float watt) {
        powerHistory.add(watt);
        if (powerHistory.size() > MAX_HISTORY_SIZE) {
            powerHistory.remove(0);
        }

        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < powerHistory.size(); i++) {
            entries.add(new Entry(i, powerHistory.get(i)));
        }

        LineDataSet dataSet = new LineDataSet(entries, "功率");
        dataSet.setColor(getResources().getColor(R.color.primary));
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);

        List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(dataSet);

        LineData data = new LineData(dataSets);
        chartPower.setData(data);
        chartPower.notifyDataSetChanged();
        chartPower.invalidate();
    }
}
