package com.batteryhealth.app.ui.battery;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.ui.endurance.EnduranceActivity;
import com.batteryhealth.app.ui.trend.TrendActivity;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.ReportGenerator;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

    private TextView tvDeviceModelHeader;
    private TextView tvBeyondDevices;
    private TextView tvAgingPrediction;
    private TextView tvSourceExplanation;
    private LineChart chartHealthTrend;
    private LinearLayout layoutHistory;
    private TextView tvHistoryEmpty;

    private View btnWeeklyReport;
    private View btnMonthlyReport;
    private View btnTrend;
    private View btnEndurance;

    private BatteryDataManager batteryDataManager;
    private Handler mainHandler;
    private boolean isRunning = false;
    
    // 定时更新UI的Runnable
    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            // 每次刷新UI前先从系统读取最新基本数据（电量、温度、电压等）
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
            return inflater.inflate(R.layout.fragment_battery_health, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
            // 创建错误提示视图，避免完全空白，同时显示异常信息便于排查
            return createErrorView(e);
        }
    }

    private View createErrorView(Exception e) {
        android.widget.TextView errorView = new android.widget.TextView(requireContext());
        String message = "界面加载失败\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
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
            
            // 获取电池数据管理器
            if (getActivity() instanceof MainActivity) {
                batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
            }
            
            initViews(view);
            updateUI();
            loadTrendAndHistory();
            animateCardsEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        isRunning = true;

        // 进入页面时刷新一次完整数据（容量、循环次数、电池来源等）
        if (batteryDataManager != null) {
            batteryDataManager.refreshAllDataAsync();
        }

        // 启动定时更新，刷新UI和基本电池信息
        if (mainHandler != null) {
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

            tvDeviceModelHeader = view.findViewById(R.id.tv_device_model_header);
            tvBeyondDevices = view.findViewById(R.id.tv_beyond_devices);
            tvAgingPrediction = view.findViewById(R.id.tv_aging_prediction);
            tvSourceExplanation = view.findViewById(R.id.tv_source_explanation);
            chartHealthTrend = view.findViewById(R.id.chart_health_trend);
            layoutHistory = view.findViewById(R.id.layout_history);
            tvHistoryEmpty = view.findViewById(R.id.tv_history_empty);

            btnWeeklyReport = view.findViewById(R.id.btn_weekly_report);
            btnMonthlyReport = view.findViewById(R.id.btn_monthly_report);
            btnTrend = view.findViewById(R.id.btn_trend);
            btnEndurance = view.findViewById(R.id.btn_endurance);

            setupActionButtons();

            // 设置默认值
            setDefaultValues();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage());
        }
    }
    
    private void setDefaultValues() {
        if (tvHealthPercentage != null) tvHealthPercentage.setText("--");
        if (tvHealthGrade != null) tvHealthGrade.setText("--");
        if (tvHealthStatus != null) tvHealthStatus.setText(R.string.status_detecting);
        if (tvCapacity != null) tvCapacity.setText("-- mAh");
        if (tvCycleCount != null) tvCycleCount.setText("-- 次");
        if (tvTemperature != null) tvTemperature.setText("-- °C");
        if (tvVoltage != null) tvVoltage.setText("-- mV");
        if (tvBatterySource != null) tvBatterySource.setText(R.string.status_detecting);
        if (tvTechnology != null) tvTechnology.setText("--");
        if (tvBatteryLevel != null) tvBatteryLevel.setText("--%");
        if (tvChargingStatus != null) tvChargingStatus.setText("--");
        if (tvCurrentNow != null) tvCurrentNow.setText("-- mA");
        if (tvBeyondDevices != null) tvBeyondDevices.setText(R.string.beyond_devices_unavailable);
        if (tvAgingPrediction != null) tvAgingPrediction.setText(R.string.aging_prediction_unavailable);
    }
    
    private void updateUI() {
        if (batteryDataManager == null || mainHandler == null) return;
        
        mainHandler.post(() -> {
            try {
                BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
                if (info == null) return;
                
                // 更新设备型号与超越设备
                if (tvDeviceModelHeader != null) {
                    tvDeviceModelHeader.setText(info.getDeviceModel() != null ? info.getDeviceModel() : getString(R.string.label_device_model));
                }
                if (tvBeyondDevices != null) {
                    int beyond = batteryDataManager.getBeyondDevicesPercent();
                    if (beyond >= 0) {
                        tvBeyondDevices.setText(String.format(Locale.getDefault(), getString(R.string.beyond_devices_format), beyond));
                    } else {
                        tvBeyondDevices.setText(R.string.beyond_devices_unavailable);
                    }
                }

                // 更新健康度
                float healthPercentage = info.getHealthPercentage();
                if (tvHealthPercentage != null) {
                    if (info.hasValidHealthData()) {
                        tvHealthPercentage.setText(String.format(Locale.getDefault(), "%.1f%%", healthPercentage));
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
                            ? String.format(Locale.getDefault(), " (可信度 %.0f%%)", confidence * 100)
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
                        tvCapacity.setText(String.format(Locale.getDefault(), "%d / %d mAh", currentCap, designCap));
                    } else {
                        tvCapacity.setText(R.string.unknown);
                    }
                }

                if (tvCycleCount != null) {
                    if (info.hasValidCycleCount()) {
                        String estimatedMark = info.isCycleCountEstimated() ? " · 估算" : "";
                        tvCycleCount.setText(String.format(Locale.getDefault(), "%d 次%s", info.getCycleCount(), estimatedMark));
                    } else {
                        tvCycleCount.setText(R.string.unknown);
                    }
                }
                
                if (tvTemperature != null) {
                    tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", info.getTemperature()));
                }
                
                if (tvVoltage != null) {
                    tvVoltage.setText(String.format(Locale.getDefault(), "%.0f mV", info.getVoltage()));
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
                        tvCurrentNow.setText(String.format(Locale.getDefault(), "%.0f mA", Math.abs(currentNow / 1000.0f)));
                    } else {
                        tvCurrentNow.setText("-- mA");
                    }
                }

                // 更新老化预测
                updateAgingPrediction();
            } catch (Exception e) {
                Log.e(TAG, "Error updating UI: " + e.getMessage());
            }
        });
    }

    private void updateAgingPrediction() {
        if (tvAgingPrediction == null || batteryDataManager == null) return;
        try {
            int[] prediction = batteryDataManager.getAgingPrediction();
            if (prediction != null) {
                if (prediction[0] == 0 && prediction[1] == 0) {
                    tvAgingPrediction.setText(R.string.aging_prediction_unavailable);
                } else {
                    tvAgingPrediction.setText(String.format(Locale.getDefault(), getString(R.string.aging_prediction_format), prediction[0], prediction[1]));
                }
            } else {
                tvAgingPrediction.setText(R.string.aging_prediction_unavailable);
            }
        } catch (Exception e) {
            tvAgingPrediction.setText(R.string.aging_prediction_unavailable);
        }
    }
    
    private void setupActionButtons() {
        try {
            if (tvSourceExplanation != null) {
                tvSourceExplanation.setOnClickListener(v -> showSourceExplanationDialog());
            }
            if (tvBatterySource != null) {
                tvBatterySource.setOnClickListener(v -> showSourceExplanationDialog());
            }
            if (btnWeeklyReport != null) {
                btnWeeklyReport.setOnClickListener(v -> showReport(false));
            }
            if (btnMonthlyReport != null) {
                btnMonthlyReport.setOnClickListener(v -> showReport(true));
            }
            if (btnTrend != null) {
                btnTrend.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(requireContext(), TrendActivity.class));
                    } catch (Exception e) {
                        Log.e(TAG, "启动趋势页面失败", e);
                    }
                });
            }
            if (btnEndurance != null) {
                btnEndurance.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(requireContext(), EnduranceActivity.class));
                    } catch (Exception e) {
                        Log.e(TAG, "启动续航页面失败", e);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up action buttons: " + e.getMessage());
        }
    }

    private void showSourceExplanationDialog() {
        if (!isAdded()) return;
        try {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.what_does_this_mean)
                    .setMessage(R.string.battery_source_explanation)
                    .setPositiveButton(R.string.close, null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "显示来源说明失败", e);
        }
    }

    private void showReport(boolean monthly) {
        try {
            new Thread(() -> {
                try {
                    ReportGenerator generator = new ReportGenerator(requireContext());
                    ReportGenerator.BatteryReport report = monthly
                            ? generator.generateMonthlyReport()
                            : generator.generateWeeklyReport();
                    if (mainHandler != null) {
                        mainHandler.post(() -> {
                            if (!isAdded()) return;
                            showReportDialog(report);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "生成报告失败", e);
                    if (mainHandler != null) {
                        mainHandler.post(() -> Toast.makeText(requireContext(),
                                "生成报告失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "启动报告生成失败", e);
        }
    }

    private void showReportDialog(ReportGenerator.BatteryReport report) {
        if (!isAdded()) return;
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle(report.title != null ? report.title : (report.title = getString(R.string.report_title_weekly)))
                    .setMessage((report.summary != null ? report.summary : "")
                            + "\n\n建议：\n" + (report.recommendation != null ? report.recommendation : ""))
                    .setPositiveButton(R.string.report_share, (dialog, which) -> shareReport(report))
                    .setNegativeButton(R.string.close, null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "显示报告弹窗失败", e);
        }
    }

    private void shareReport(ReportGenerator.BatteryReport report) {
        try {
            String shareText = (report.title != null ? report.title : getString(R.string.report_title_weekly)) + "\n\n"
                    + (report.period != null ? report.period + "\n" : "")
                    + (report.summary != null ? report.summary + "\n\n" : "")
                    + "建议：\n" + (report.recommendation != null ? report.recommendation : "");
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, report.title);
            intent.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(Intent.createChooser(intent, getString(R.string.report_share)));
        } catch (Exception e) {
            Log.e(TAG, "分享报告失败", e);
            Toast.makeText(requireContext(), "分享失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadTrendAndHistory() {
        new Thread(() -> {
            try {
                BatteryHealthApplication app = BatteryHealthApplication.getInstance();
                if (app == null) return;
                AppDatabase db = app.getDatabase();
                if (db == null) return;

                long oneWeekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
                List<BatteryInfo> records = db.batteryInfoDao().getSince(oneWeekAgo);

                if (mainHandler != null) {
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        updateMiniTrend(records);
                        updateHistoryList(records);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "加载趋势与历史数据失败", e);
            }
        }).start();
    }

    private void updateMiniTrend(List<BatteryInfo> records) {
        if (chartHealthTrend == null || !isAdded()) return;
        try {
            setupMiniChart();

            if (records == null || records.isEmpty()) {
                chartHealthTrend.setNoDataText(getString(R.string.status_no_data));
                chartHealthTrend.invalidate();
                return;
            }

            List<Entry> healthEntries = new ArrayList<>();
            List<Entry> capacityEntries = new ArrayList<>();
            int index = 0;
            for (BatteryInfo info : records) {
                if (info.hasValidHealthData()) {
                    healthEntries.add(new Entry(index, info.getHealthPercentage()));
                }
                if (info.getCurrentCapacity() > 0) {
                    capacityEntries.add(new Entry(index, info.getCurrentCapacity()));
                }
                index++;
            }

            if (healthEntries.isEmpty() && capacityEntries.isEmpty()) {
                chartHealthTrend.setNoDataText(getString(R.string.status_no_data));
                chartHealthTrend.invalidate();
                return;
            }

            LineData lineData = new LineData();
            if (!healthEntries.isEmpty()) {
                LineDataSet healthSet = createDataSet(healthEntries, getString(R.string.label_health), R.color.primary_green);
                lineData.addDataSet(healthSet);
            }
            if (!capacityEntries.isEmpty()) {
                LineDataSet capSet = createDataSet(capacityEntries, getString(R.string.label_capacity), R.color.blue);
                lineData.addDataSet(capSet);
            }
            chartHealthTrend.setData(lineData);
            chartHealthTrend.invalidate();
        } catch (Exception e) {
            Log.e(TAG, "更新趋势图失败", e);
        }
    }

    private LineDataSet createDataSet(List<Entry> entries, String label, int colorRes) {
        int color = ContextCompat.getColor(requireContext(), colorRes);
        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(color);
        dataSet.setFillAlpha(30);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        return dataSet;
    }

    private void setupMiniChart() {
        chartHealthTrend.getDescription().setEnabled(false);
        chartHealthTrend.setTouchEnabled(false);
        chartHealthTrend.setDragEnabled(false);
        chartHealthTrend.setScaleEnabled(false);
        chartHealthTrend.setDrawGridBackground(false);
        chartHealthTrend.getLegend().setEnabled(true);
        chartHealthTrend.getLegend().setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));

        XAxis xAxis = chartHealthTrend.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawLabels(false);

        YAxis leftAxis = chartHealthTrend.getAxisLeft();
        leftAxis.setDrawGridLines(false);
        leftAxis.setDrawLabels(false);
        leftAxis.setAxisMinimum(0f);

        chartHealthTrend.getAxisRight().setEnabled(false);
    }

    private void updateHistoryList(List<BatteryInfo> records) {
        if (layoutHistory == null || tvHistoryEmpty == null || !isAdded()) return;
        try {
            layoutHistory.removeAllViews();
            if (records == null || records.isEmpty()) {
                tvHistoryEmpty.setVisibility(View.VISIBLE);
                return;
            }
            tvHistoryEmpty.setVisibility(View.GONE);

            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            int count = 0;
            for (int i = records.size() - 1; i >= 0 && count < 7; i--) {
                BatteryInfo info = records.get(i);
                View item = inflater.inflate(R.layout.item_list_row, layoutHistory, false);
                TextView tvTitle = item.findViewById(R.id.tv_title);
                TextView tvSubtitle = item.findViewById(R.id.tv_subtitle);
                TextView tvDetail = item.findViewById(R.id.tv_detail);
                View icon = item.findViewById(R.id.iv_icon);
                if (icon != null) icon.setVisibility(View.GONE);

                if (tvTitle != null) {
                    tvTitle.setText(sdf.format(new Date(info.getTimestamp())));
                }
                if (tvSubtitle != null) {
                    tvSubtitle.setText(String.format(Locale.getDefault(), getString(R.string.history_item_format),
                            info.getLevel(), info.getHealthPercentage(), info.getCycleCount()));
                }
                if (tvDetail != null) {
                    tvDetail.setText(String.format(Locale.getDefault(), getString(R.string.history_item_detail_format),
                            "--", 0));
                    tvDetail.setVisibility(View.VISIBLE);
                }
                layoutHistory.addView(item);
                count++;
            }
        } catch (Exception e) {
            Log.e(TAG, "更新历史记录失败", e);
        }
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
