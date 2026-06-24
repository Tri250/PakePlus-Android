package com.batteryhealth.app.ui.power;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
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
import androidx.lifecycle.ViewModelProvider;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.service.ChargingMonitorService;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.ChargeProtocolDetector;
import com.batteryhealth.app.utils.ThreadExecutor;
import com.batteryhealth.app.utils.UiAnimationHelper;
import com.batteryhealth.app.ui.viewmodel.PowerViewModel;
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
    private PowerViewModel viewModel;

    // Service binding
    private ChargingMonitorService chargingService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ChargingMonitorService.ChargingBinder binder = (ChargingMonitorService.ChargingBinder) service;
            chargingService = binder.getService();
            serviceBound = true;
            // 立即更新一次数据
            updateBatteryData();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            chargingService = null;
            serviceBound = false;
        }
    };

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

        // 充电历史入口
        View historyEntry = view.findViewById(R.id.card_charging_history_entry);
        if (historyEntry != null) {
            historyEntry.setOnClickListener(v -> {
                if (isAdded()) {
                    requireActivity().getSupportFragmentManager().beginTransaction()
                            .replace(((ViewGroup) requireView().getParent()).getId(), new ChargingHistoryFragment())
                            .addToBackStack("charging_history")
                            .commit();
                }
            });
        }
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

        // 初始化 ViewModel
        viewModel = new ViewModelProvider(this).get(PowerViewModel.class);

        // 绑定 ChargingMonitorService
        bindChargingService();

        registerBatteryReceiver();
        startPeriodicUpdate();
        loadHistoricalChart();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterBatteryReceiver();
        stopPeriodicUpdate();
        unbindChargingService();
    }

    private void bindChargingService() {
        try {
            Intent intent = new Intent(requireContext(), ChargingMonitorService.class);
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            // Service 可能未启动，忽略
        }
    }

    private void unbindChargingService() {
        if (serviceBound) {
            try {
                requireContext().unbindService(serviceConnection);
            } catch (Exception ignored) {
            }
            serviceBound = false;
            chargingService = null;
        }
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

        ThreadExecutor.execute(() -> {
            try {
                batteryDataManager.refreshFromStickyIntent();
                BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
                if (info != null && isAdded()) {
                    TodayChargeStats stats = queryTodayChargeStats();
                    // 从 Service 获取智能充电阶段
                    String chargingPhase = null;
                    if (chargingService != null && serviceBound) {
                        chargingPhase = chargingService.getCurrentChargingPhaseDescription();
                    }
                    final String phase = chargingPhase;
                    handler.post(() -> updateUI(info, stats, phase));
                }
            } catch (Exception e) {
                // 静默处理
            }
        });
    }

    private void updateUI(BatteryInfo info, TodayChargeStats stats, String serviceChargingPhase) {
        if (info == null || !isAdded()) return;

        int batteryPct = info.getLevel();
        boolean isCharging = info.isCharging();

        float voltageV = info.getVoltage() / 1000f;
        float currentMa = Math.abs(info.getCurrentNow()) / 1000f;
        float currentA = currentMa / 1000f;
        float watt = info.getChargingPower();
        float tempC = info.getTemperature();

        ChargeProtocolDetector.Result protocolResult = ChargeProtocolDetector.detect(requireContext(), watt);

        // 功率大数字
        tvWatt.setText(String.format(Locale.getDefault(), "%.1f", watt));

        // 充电类型
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

        // 充电详情
        tvVoltage.setText(String.format(Locale.getDefault(), "%.2f V", voltageV));
        tvCurrent.setText(String.format(Locale.getDefault(), "%.0f mA", currentMa));

        // 充电阶段：优先使用 Service 的智能四阶段检测结果
        if (serviceChargingPhase != null) {
            tvChargeStage.setText(serviceChargingPhase);
        } else if (isCharging) {
            // 回退：使用 PowerHistory 的阶段描述逻辑
            PowerHistory ph = new PowerHistory();
            ph.setBatteryLevel(batteryPct);
            ph.setPower(watt);
            if (batteryPct >= 99) {
                tvChargeStage.setText(getString(R.string.stage_full));
            } else if (batteryPct >= 80) {
                tvChargeStage.setText(getString(R.string.stage_trickle));
            } else {
                tvChargeStage.setText(getString(R.string.stage_fast));
            }
        } else {
            tvChargeStage.setText("--");
        }

        tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));
        tvBatteryLevel.setText(String.format(Locale.getDefault(), "%d%%", batteryPct));
        tvEstimatedFull.setText(isCharging ? calculateTimeToFull(batteryPct, currentA, info) : "--");

        // 今日充电统计
        tvChargeCount.setText(String.format(Locale.getDefault(), "%d", stats.sessionCount));
        tvAvgPower.setText(String.format(Locale.getDefault(), "%.1f W", stats.avgPower));
        tvTotalChargeTime.setText(formatMinutes(stats.totalChargeMinutes));
        tvTotalCharged.setText(String.format(Locale.getDefault(), "%d mAh", stats.totalChargedMah));

        // 实时更新功率曲线
        if (isCharging && watt > 0) {
            addChartPoint(watt);
        }
    }

    /**
     * 从数据库加载历史功率曲线数据
     */
    private void loadHistoricalChart() {
        ThreadExecutor.execute(() -> {
            try {
                BatteryHealthApplication app = (BatteryHealthApplication) requireActivity().getApplication();
                if (app == null) return;
                var db = app.getDatabase();
                if (db == null) return;

                // 加载最近30分钟的功率数据
                long since = System.currentTimeMillis() - 30 * 60 * 1000;
                List<PowerHistory> histories = db.powerHistoryDao().getSince(since);
                if (histories != null && !histories.isEmpty() && isAdded()) {
                    handler.post(() -> {
                        if (!isAdded()) return;
                        List<Entry> entries = new ArrayList<>();
                        for (int i = 0; i < histories.size(); i++) {
                            entries.add(new Entry(i, histories.get(i).getPower()));
                        }
                        updateChart(entries);
                    });
                }
            } catch (Exception e) {
                // 静默处理
            }
        });
    }

    private void addChartPoint(float watt) {
        LineData data = chartPower.getData();
        if (data == null) return;

        LineDataSet set = (LineDataSet) data.getDataSetByIndex(0);
        if (set == null) {
            set = createChartDataSet();
            data.addDataSet(set);
        }

        // 最多保留60个点（约2分钟）
        if (set.getEntryCount() >= 60) {
            set.removeFirst();
            // 重新计算 x 值
            for (int i = 0; i < set.getEntryCount(); i++) {
                set.getEntryForIndex(i).setX(i);
            }
        }

        set.addEntry(new Entry(set.getEntryCount(), watt));
        data.notifyDataChanged();
        chartPower.notifyDataSetChanged();
        chartPower.invalidate();
    }

    private void updateChart(List<Entry> entries) {
        LineDataSet dataSet = createChartDataSet();
        dataSet.setValues(entries);

        List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(dataSet);

        LineData data = new LineData(dataSets);
        chartPower.setData(data);
        chartPower.notifyDataSetChanged();
        chartPower.invalidate();
    }

    private LineDataSet createChartDataSet() {
        LineDataSet dataSet = new LineDataSet(new ArrayList<>(), "功率");
        dataSet.setColor(getResources().getColor(R.color.primary));
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);
        return dataSet;
    }

    /**
     * 查询今日充电统计：充电会话数、总充电时间、总充电量、平均功率。
     */
    private TodayChargeStats queryTodayChargeStats() {
        TodayChargeStats stats = new TodayChargeStats();
        try {
            BatteryHealthApplication app = (BatteryHealthApplication) requireActivity().getApplication();
            if (app == null) return stats;
            var db = app.getDatabase();
            if (db == null) return stats;

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long todayStart = cal.getTimeInMillis();

            // 从 PowerHistory 表查询今日充电数据（更精确）
            List<PowerHistory> powerRecords = db.powerHistoryDao().getSince(todayStart);
            if (powerRecords != null && !powerRecords.isEmpty()) {
                // 按会话分组统计
                String currentSessionId = null;
                long sessionStartTime = 0;
                int sessionStartLevel = -1;
                int sessionEndLevel = -1;
                float sessionTotalPower = 0;
                int sessionPowerCount = 0;

                for (PowerHistory record : powerRecords) {
                    String sid = record.getSessionId();
                    if (sid != null && !sid.equals(currentSessionId)) {
                        // 新会话开始，结算上一个会话
                        if (currentSessionId != null) {
                            stats.sessionCount++;
                            stats.totalChargeMinutes += (int) ((record.getTimestamp() - sessionStartTime) / 60000);
                            stats.totalPowerSum += sessionTotalPower;
                            stats.totalPowerCount += sessionPowerCount;
                            if (sessionStartLevel >= 0 && sessionEndLevel >= sessionStartLevel) {
                                stats.totalLevelGained += sessionEndLevel - sessionStartLevel;
                            }
                        }
                        currentSessionId = sid;
                        sessionStartTime = record.getTimestamp();
                        sessionStartLevel = record.getBatteryLevel();
                        sessionTotalPower = 0;
                        sessionPowerCount = 0;
                    }
                    sessionEndLevel = record.getBatteryLevel();
                    sessionTotalPower += record.getPower();
                    sessionPowerCount++;
                }

                // 结算最后一个会话
                if (currentSessionId != null) {
                    stats.sessionCount++;
                    long lastTime = powerRecords.get(powerRecords.size() - 1).getTimestamp();
                    stats.totalChargeMinutes += (int) ((Math.max(lastTime, System.currentTimeMillis()) - sessionStartTime) / 60000);
                    stats.totalPowerSum += sessionTotalPower;
                    stats.totalPowerCount += sessionPowerCount;
                    if (sessionStartLevel >= 0 && sessionEndLevel >= sessionStartLevel) {
                        stats.totalLevelGained += sessionEndLevel - sessionStartLevel;
                    }
                }

                // 计算平均功率
                stats.avgPower = stats.totalPowerCount > 0 ? stats.totalPowerSum / stats.totalPowerCount : 0;

                // 计算充电量：使用电量差值 × 设计容量
                BatteryInfo latestInfo = batteryDataManager.getCurrentBatteryInfo();
                if (latestInfo != null) {
                    int designCapacity = latestInfo.getDesignCapacity();
                    if (designCapacity > 0 && stats.totalLevelGained > 0) {
                        stats.totalChargedMah = (int) (stats.totalLevelGained / 100f * designCapacity);
                    }
                }
            }
        } catch (Exception e) {
            // 静默处理
        }
        return stats;
    }

    private String calculateTimeToFull(int batteryPct, float currentA, BatteryInfo info) {
        if (currentA <= 0) return "--";

        int capacityMah = info.getCurrentCapacity();
        if (capacityMah <= 0) {
            capacityMah = info.getDesignCapacity();
        }
        if (capacityMah <= 0) return "--";

        int limitPct = batteryDataManager.getChargingLimitPercent();
        int remainingPct = limitPct - batteryPct;
        if (remainingPct <= 0) return "--";

        float remainingMah = capacityMah * (remainingPct / 100f);

        float efficiency = 0.9f;
        if (batteryPct >= 80) {
            efficiency = 0.6f;
        } else if (batteryPct >= 60) {
            efficiency = 0.8f;
        }

        float hours = remainingMah / (currentA * 1000f * efficiency);
        int mins = Math.max(1, (int) (hours * 60));
        return formatMinutes(mins);
    }

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

    private static class TodayChargeStats {
        int sessionCount = 0;
        int totalChargeMinutes = 0;
        int totalChargedMah = 0;
        float avgPower = 0;
        float totalPowerSum = 0;
        int totalPowerCount = 0;
        int totalLevelGained = 0;
    }
}
