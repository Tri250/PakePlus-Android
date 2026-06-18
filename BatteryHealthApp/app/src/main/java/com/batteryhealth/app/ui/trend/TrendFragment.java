package com.batteryhealth.app.ui.trend;

import android.content.Context;
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
        } catch (Throwable t) {
            Log.e(TAG, "Error inflating layout: " + t.getMessage(), t);
            return createErrorView(t);
        }
    }

    /**
     * 创建友好的错误页：标题 + 提示文案 + "重试" 按钮。
     */
    private View createErrorView(Throwable t) {
        Context ctx = null;
        try { ctx = getContext(); } catch (Throwable ignored) {}
        if (ctx == null) ctx = requireActivity().getApplicationContext();

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