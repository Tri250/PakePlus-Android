package com.batteryhealth.app.ui.trend;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PowerHistory;
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
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_trend, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage());
            return new View(requireContext());
        }
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
            
            setupCharts();
            loadData();
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        loadData();
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
        
        // X轴 - 时间
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(getResources().getColor(R.color.ios_secondary_label, null));
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
        leftAxis.setGridColor(getResources().getColor(R.color.ios_separator, null));
        leftAxis.setTextColor(getResources().getColor(R.color.ios_secondary_label, null));
        leftAxis.setTextSize(11f);
        
        // Y轴 - 右侧禁用
        chart.getAxisRight().setEnabled(false);
        
        // 图例
        chart.getLegend().setEnabled(true);
        chart.getLegend().setTextColor(getResources().getColor(R.color.ios_secondary_label, null));
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
                long sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
                List<BatteryInfo> batteryData = db.batteryInfoDao().getSince(sevenDaysAgo);
                
                // 获取充电功率历史
                List<PowerHistory> powerData = db.powerHistoryDao().getSince(sevenDaysAgo);
                
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
            boolean hasData = batteryData != null && !batteryData.isEmpty();
            
            if (tvNoData != null) {
                tvNoData.setVisibility(hasData ? View.GONE : View.VISIBLE);
            }
            if (tvDataCount != null) {
                tvDataCount.setText(hasData ? batteryData.size() + " 条记录" : "暂无数据");
            }
            
            if (!hasData) return;
            
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
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating charts: " + e.getMessage());
        }
    }
    
    private void setChartData(LineChart chart, String label, List<Entry> entries, int colorRes) {
        if (chart == null || entries.isEmpty()) return;
        
        LineDataSet dataSet = new LineDataSet(entries, label);
        int color = getResources().getColor(colorRes, null);
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