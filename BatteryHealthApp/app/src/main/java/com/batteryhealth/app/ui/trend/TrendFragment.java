package com.batteryhealth.app.ui.trend;

import android.os.Bundle;
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
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PowerHistory;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.chip.Chip;
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
 * 趋势追踪Fragment
 * 
 * 功能：
 * 1. 电池健康度趋势图表
 * 2. 电量变化趋势图表
 * 3. 温度变化趋势图表
 * 4. 充电功率趋势图表
 */
public class TrendFragment extends Fragment {
    
    private static final String TAG = "TrendFragment";
    
    private LineChart chartHealth;
    private LineChart chartLevel;
    private LineChart chartTemperature;
    private LineChart chartPower;
    private TextView tvDataCount;
    private TextView tvNoData;
    private ChipGroup chipGroupTimeRange;
    private int selectedTimeRangeDays = 7; // 默认7天
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_trend, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
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
            chartHealth = view.findViewById(R.id.chart_health);
            chartLevel = view.findViewById(R.id.chart_level);
            chartTemperature = view.findViewById(R.id.chart_temperature);
            chartPower = view.findViewById(R.id.chart_power);
            tvDataCount = view.findViewById(R.id.tv_data_count);
            tvNoData = view.findViewById(R.id.tv_no_data);
            chipGroupTimeRange = view.findViewById(R.id.chip_group_time_range);
            setupTimeRangeSelector();
            
            setupCharts();
            loadData();
            animateCardsEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
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

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }
    
    private void setupTimeRangeSelector() {
        if (chipGroupTimeRange == null) return;
        chipGroupTimeRange.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chip_1day) {
                selectedTimeRangeDays = 1;
            } else if (checkedId == R.id.chip_7days) {
                selectedTimeRangeDays = 7;
            } else if (checkedId == R.id.chip_30days) {
                selectedTimeRangeDays = 30;
            }
            loadData();
        });
    }

    private void setupCharts() {
        setupChart(chartHealth, "健康度 (%)", R.color.ios_green);
        setupChart(chartLevel, "电量 (%)", R.color.ios_blue);
        setupChart(chartTemperature, "温度 (°C)", R.color.ios_orange);
        setupChart(chartPower, "功率 (W)", R.color.ios_purple);
    }
    
    private void setupChart(LineChart chart, String label, int colorRes) {
        if (chart == null) return;
        
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);
        chart.setExtraOffsets(8, 8, 8, 8);
        
        // 获取颜色
        int secondaryLabelColor = ContextCompat.getColor(requireContext(), R.color.ios_secondary_label);
        int separatorColor = ContextCompat.getColor(requireContext(), R.color.ios_separator);
        
        // X轴 - 时间
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(secondaryLabelColor);
        xAxis.setTextSize(11f);
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.getDefault());
            @Override
            public String getFormattedValue(float value) {
                return sdf.format(new Date((long) value));
            }
        });
        
        // Y轴 - 左侧
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(separatorColor);
        leftAxis.setTextColor(secondaryLabelColor);
        leftAxis.setTextSize(11f);
        
        // Y轴 - 右侧禁用
        chart.getAxisRight().setEnabled(false);
        
        // 图例
        chart.getLegend().setEnabled(true);
        chart.getLegend().setTextColor(secondaryLabelColor);
        chart.getLegend().setTextSize(12f);
        
        // 动画
        chart.animateXY(800, 600);
    }
    
    private void loadData() {
        new Thread(() -> {
            try {
                BatteryHealthApplication app = BatteryHealthApplication.getInstance();
                if (app == null) return;
                AppDatabase db = app.getDatabase();
                if (db == null) return;
                
                // 获取最近7天的电池数据
                long startTime = System.currentTimeMillis() - (long) selectedTimeRangeDays * 24 * 60 * 60 * 1000;
                List<BatteryInfo> batteryData = db.batteryInfoDao().getSince(startTime);

                // 获取充电功率历史
                List<PowerHistory> powerData = db.powerHistoryDao().getSince(startTime);
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> updateCharts(batteryData, powerData));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading trend data: " + e.getMessage());
            }
        }).start();
    }
    
    private void updateCharts(List<BatteryInfo> batteryData, List<PowerHistory> powerData) {
        try {
            int recordCount = batteryData != null ? batteryData.size() : 0;
            boolean hasEnoughData = recordCount >= 10;

            // 无数据或数据不足时显示友好引导
            if (!hasEnoughData) {
                showEmptyState(recordCount);
                return;
            }

            showCharts();
            if (tvDataCount != null) {
                tvDataCount.setText(recordCount + " 条记录");
            }

            // 健康度趋势
            List<Entry> healthEntries = new ArrayList<>();
            List<Entry> levelEntries = new ArrayList<>();
            List<Entry> tempEntries = new ArrayList<>();

            for (BatteryInfo info : batteryData) {
                float time = info.getTimestamp();
                healthEntries.add(new Entry(time, info.getHealthPercentage()));
                levelEntries.add(new Entry(time, info.getLevel()));
                tempEntries.add(new Entry(time, info.getTemperature()));
            }

            setChartData(chartHealth, "健康度", healthEntries, R.color.ios_green);
            setChartData(chartLevel, "电量", levelEntries, R.color.ios_blue);
            setChartData(chartTemperature, "温度", tempEntries, R.color.ios_orange);

            // 充电功率趋势
            if (powerData != null && !powerData.isEmpty()) {
                List<Entry> powerEntries = new ArrayList<>();
                for (PowerHistory ph : powerData) {
                    powerEntries.add(new Entry(ph.getTimestamp(), ph.getPower()));
                }
                setChartData(chartPower, "功率", powerEntries, R.color.ios_purple);
            } else {
                clearChart(chartPower, "暂无充电记录");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating charts: " + e.getMessage());
        }
    }

    private void showEmptyState(int recordCount) {
        if (tvNoData != null) {
            tvNoData.setVisibility(View.VISIBLE);
            if (recordCount == 0) {
                tvNoData.setText("数据收集中\n\n趋势图表需要至少 10 条记录。保持应用在后台运行约 10 分钟后即可查看。");
            } else {
                tvNoData.setText(String.format("已收集 %d 条记录\n\n继续监测约 %d 分钟后即可生成趋势图表。", recordCount, (10 - recordCount) * 1));
            }
        }
        if (tvDataCount != null) tvDataCount.setText("暂无数据");
        if (chartHealth != null) chartHealth.setNoDataText("数据收集中");
        if (chartLevel != null) chartLevel.setNoDataText("数据收集中");
        if (chartTemperature != null) chartTemperature.setNoDataText("数据收集中");
        if (chartPower != null) chartPower.setNoDataText("数据收集中");
    }

    private void showCharts() {
        if (tvNoData != null) tvNoData.setVisibility(View.GONE);
    }

    private void clearChart(LineChart chart, String text) {
        if (chart == null) return;
        chart.setNoDataText(text);
        chart.clear();
    }
    
    private void setChartData(LineChart chart, String label, List<Entry> entries, int colorRes) {
        if (chart == null || entries == null || entries.isEmpty()) return;
        
        LineDataSet dataSet = new LineDataSet(entries, label);
        int color = ContextCompat.getColor(requireContext(), colorRes);
        dataSet.setColor(color);
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleColor(color);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextSize(0f); // 不显示数值
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(color);
        dataSet.setFillAlpha(30);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); // 平滑曲线
        
        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.invalidate();
    }
}