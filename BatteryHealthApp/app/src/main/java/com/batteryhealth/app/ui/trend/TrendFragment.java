package com.batteryhealth.app.ui.trend;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
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
import com.batteryhealth.app.utils.UiAnimationHelper;
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
import java.util.Calendar;
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
    private static final int MAX_DATA_POINTS = 500;
    private static final int DEMO_DATA_DAYS = 7;
    private static final int DEMO_DATA_POINTS = 168; // 7天 * 24小时
    private static final float DEMO_HEALTH_START = 98.0f;
    private static final float DEMO_HEALTH_END = 95.5f;

    private LineChart chartHealth;
    private LineChart chartLevel;
    private LineChart chartTemperature;
    private LineChart chartPower;
    private TextView tvDataCount;
    private TextView tvNoData;
    private TextView tvDemoHint;
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
        String message = getString(R.string.error_view_load_failed, e.getClass().getSimpleName(), e.getMessage());
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
            tvDemoHint = view.findViewById(R.id.tv_demo_hint);
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
        UiAnimationHelper.animateCardsEntry(view);
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
        setupChart(chartHealth, getString(R.string.chart_health_trend), R.color.ios_green);
        setupChart(chartLevel, getString(R.string.chart_level_trend), R.color.ios_blue);
        setupChart(chartTemperature, getString(R.string.chart_temperature_trend), R.color.ios_orange);
        setupChart(chartPower, getString(R.string.chart_power_trend), R.color.ios_purple);
    }

    private void setupChart(LineChart chart, String label, int colorRes) {
        if (chart == null || !isAdded()) return;

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
            private final SimpleDateFormat sdfDay = new SimpleDateFormat("MM/dd", Locale.getDefault());
            private final SimpleDateFormat sdfHour = new SimpleDateFormat("HH:mm", Locale.getDefault());
            @Override
            public String getFormattedValue(float value) {
                if (selectedTimeRangeDays == 1) {
                    return sdfHour.format(new Date((long) value));
                }
                return sdfDay.format(new Date((long) value));
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
        if (!isAdded() || isDetached()) return;
        new Thread(() -> {
            try {
                if (!isAdded() || isDetached()) return;
                BatteryHealthApplication app = BatteryHealthApplication.getInstance();
                if (app == null) return;
                AppDatabase db = app.getDatabase();
                if (db == null) return;

                // 获取最近指定天数的电池数据
                long startTime = System.currentTimeMillis() - (long) selectedTimeRangeDays * 24 * 60 * 60 * 1000;
                List<BatteryInfo> batteryData = db.batteryInfoDao().getSince(startTime);

                // 获取充电功率历史
                List<PowerHistory> powerData = db.powerHistoryDao().getSince(startTime);

                // 获取总记录数（用于判断是否显示演示数据）
                int totalRecordCount = db.batteryInfoDao().getCount();

                final List<BatteryInfo> finalBattery = batteryData;
                final List<PowerHistory> finalPower = powerData;
                final int finalTotalCount = totalRecordCount;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded() || isDetached()) return;
                        updateCharts(finalBattery, finalPower, finalTotalCount);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading trend data: " + e.getMessage());
            }
        }).start();
    }

    private void updateCharts(List<BatteryInfo> batteryData, List<PowerHistory> powerData, int totalRecordCount) {
        try {
            int recordCount = batteryData != null ? batteryData.size() : 0;
            boolean isDemoMode = totalRecordCount < 5;

            if (isDemoMode) {
                showDemoData();
                return;
            }

            // 正常数据处理
            boolean hasEnoughData = recordCount >= 10;
            if (!hasEnoughData) {
                showEmptyState(recordCount);
                return;
            }

            showCharts();
            if (tvDemoHint != null) tvDemoHint.setVisibility(View.GONE);
            if (tvDataCount != null) {
                tvDataCount.setText(getString(R.string.record_count_format, recordCount));
            }

            // 数据聚合与降采样
            List<Entry> healthEntries = aggregateBatteryData(batteryData, info -> info.getHealthPercentage());
            List<Entry> levelEntries = aggregateBatteryData(batteryData, info -> (float) info.getLevel());
            List<Entry> tempEntries = aggregateBatteryData(batteryData, info -> info.getTemperature());

            setChartData(chartHealth, getString(R.string.chart_health_trend), healthEntries, R.color.ios_green, false);
            setChartData(chartLevel, getString(R.string.chart_level_trend), levelEntries, R.color.ios_blue, false);
            setChartData(chartTemperature, getString(R.string.chart_temperature_trend), tempEntries, R.color.ios_orange, false);

            // 充电功率趋势
            if (powerData != null && !powerData.isEmpty()) {
                List<Entry> powerEntries = aggregatePowerData(powerData);
                setChartData(chartPower, getString(R.string.chart_power_trend), powerEntries, R.color.ios_purple, false);
            } else {
                clearChart(chartPower, getString(R.string.status_no_records));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating charts: " + e.getMessage());
        }
    }

    /**
     * 数据聚合与降采样
     * - 1天视图：按小时聚合，每小时取平均值
     * - 7天视图：按6小时聚合
     * - 30天视图：按天聚合
     * - 超出500点时自动降采样
     */
    private List<Entry> aggregateBatteryData(List<BatteryInfo> data, ValueExtractor extractor) {
        if (data == null || data.isEmpty()) return new ArrayList<>();

        long bucketMillis;
        switch (selectedTimeRangeDays) {
            case 1:
                bucketMillis = 60 * 60 * 1000L; // 1小时
                break;
            case 7:
                bucketMillis = 6 * 60 * 60 * 1000L; // 6小时
                break;
            case 30:
                bucketMillis = 24 * 60 * 60 * 1000L; // 1天
                break;
            default:
                bucketMillis = 24 * 60 * 60 * 1000L;
        }

        List<Entry> aggregated = new ArrayList<>();
        long currentBucket = -1;
        float sum = 0;
        int count = 0;

        for (BatteryInfo info : data) {
            long bucket = info.getTimestamp() / bucketMillis;
            if (bucket != currentBucket) {
                if (count > 0) {
                    aggregated.add(new Entry(currentBucket * bucketMillis, sum / count));
                }
                currentBucket = bucket;
                sum = 0;
                count = 0;
            }
            sum += extractor.extract(info);
            count++;
        }
        if (count > 0) {
            aggregated.add(new Entry(currentBucket * bucketMillis, sum / count));
        }

        // 降采样：如果仍超出上限，均匀采样
        if (aggregated.size() > MAX_DATA_POINTS) {
            return downsample(aggregated, MAX_DATA_POINTS);
        }
        return aggregated;
    }

    private List<Entry> aggregatePowerData(List<PowerHistory> data) {
        if (data == null || data.isEmpty()) return new ArrayList<>();

        long bucketMillis;
        switch (selectedTimeRangeDays) {
            case 1:
                bucketMillis = 60 * 60 * 1000L;
                break;
            case 7:
                bucketMillis = 6 * 60 * 60 * 1000L;
                break;
            case 30:
                bucketMillis = 24 * 60 * 60 * 1000L;
                break;
            default:
                bucketMillis = 24 * 60 * 60 * 1000L;
        }

        List<Entry> aggregated = new ArrayList<>();
        long currentBucket = -1;
        float sum = 0;
        int count = 0;

        for (PowerHistory ph : data) {
            long bucket = ph.getTimestamp() / bucketMillis;
            if (bucket != currentBucket) {
                if (count > 0) {
                    aggregated.add(new Entry(currentBucket * bucketMillis, sum / count));
                }
                currentBucket = bucket;
                sum = 0;
                count = 0;
            }
            sum += ph.getPower();
            count++;
        }
        if (count > 0) {
            aggregated.add(new Entry(currentBucket * bucketMillis, sum / count));
        }

        if (aggregated.size() > MAX_DATA_POINTS) {
            return downsample(aggregated, MAX_DATA_POINTS);
        }
        return aggregated;
    }

    private List<Entry> downsample(List<Entry> entries, int maxPoints) {
        if (entries.size() <= maxPoints) return entries;
        List<Entry> result = new ArrayList<>();
        int step = entries.size() / maxPoints;
        if (step < 1) step = 1;
        for (int i = 0; i < entries.size(); i += step) {
            result.add(entries.get(i));
        }
        // 确保最后一个点被包含
        Entry last = entries.get(entries.size() - 1);
        if (result.isEmpty() || result.get(result.size() - 1).getX() != last.getX()) {
            result.add(last);
        }
        return result;
    }

    private interface ValueExtractor {
        float extract(BatteryInfo info);
    }

    /**
     * 数据不足时显示空状态提示，不使用模拟/假数据。
     * 安兔兔/鲁大师在数据不足时同样显示"数据采集中"，不会生成假曲线。
     */
    private void showDemoData() {
        showEmptyState(0);
    }

    private void showEmptyState(int recordCount) {
        if (tvNoData != null) {
            tvNoData.setVisibility(View.VISIBLE);
            if (recordCount == 0) {
                tvNoData.setText(getString(R.string.status_no_data_trend));
            } else {
                tvNoData.setText(getString(R.string.status_partial_data_trend, recordCount, (10 - recordCount) * 1));
            }
        }
        if (tvDataCount != null) tvDataCount.setText(getString(R.string.status_no_data_count));
        if (tvDemoHint != null) tvDemoHint.setVisibility(View.GONE);
        if (chartHealth != null) chartHealth.setNoDataText(getString(R.string.status_data_collecting));
        if (chartLevel != null) chartLevel.setNoDataText(getString(R.string.status_data_collecting));
        if (chartTemperature != null) chartTemperature.setNoDataText(getString(R.string.status_data_collecting));
        if (chartPower != null) chartPower.setNoDataText(getString(R.string.status_data_collecting));
    }

    private void showCharts() {
        if (tvNoData != null) tvNoData.setVisibility(View.GONE);
    }

    private void clearChart(LineChart chart, String text) {
        if (chart == null) return;
        chart.setNoDataText(text);
        chart.clear();
    }

    private void setChartData(LineChart chart, String label, List<Entry> entries, int colorRes, boolean isDemo) {
        if (chart == null || entries == null || entries.isEmpty() || !isAdded()) return;

        LineDataSet dataSet = new LineDataSet(entries, label);
        int color = ContextCompat.getColor(requireContext(), colorRes);
        if (isDemo) {
            // 演示数据使用不同颜色（降低饱和度）
            int demoColor = adjustAlpha(color, 0.6f);
            dataSet.setColor(demoColor);
            dataSet.setCircleColor(demoColor);
            dataSet.setLineWidth(2f);
            dataSet.enableDashedLine(10f, 5f, 0f); // 虚线
            dataSet.setDrawFilled(false);
        } else {
            dataSet.setColor(color);
            dataSet.setCircleColor(color);
            dataSet.setLineWidth(2.5f);
            dataSet.setDrawFilled(true);
            dataSet.setFillColor(color);
            dataSet.setFillAlpha(30);
        }
        dataSet.setCircleRadius(2f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextSize(0f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.invalidate();
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(((color >> 24) & 0xFF) * factor);
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
