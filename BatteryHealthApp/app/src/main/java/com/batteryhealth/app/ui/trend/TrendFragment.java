package com.batteryhealth.app.ui.trend;

import android.content.Context;
import android.content.res.Configuration;
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
import com.batteryhealth.app.domain.usecase.GetTrendDataUseCase;
import com.batteryhealth.app.ui.viewmodel.TrendViewModel;
import com.batteryhealth.app.utils.FragmentErrorViewHelper;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 趋势追踪 Fragment（v5.0 - 对标国内同类系统完整版）
 *
 * 支持：
 * 1. 多时间范围切换（7天/30天/90天/180天）
 * 2. 图表交互（缩放/拖拽/点击查看详情）
 * 3. 健康度+温度双Y轴趋势
 * 4. 异常衰减事件标注
 * 5. 电池寿命预测
 * 6. 充电建议
 * 7. 暗色模式适配
 * 8. Android 16 兼容
 */
public class TrendFragment extends Fragment {

    private static final String TAG = "TrendFragment";

    private LineChart lineChart;
    private TextView tvInitialHealth, tvCurrentHealth, tvTotalDecay, tvMonthlyDecay;
    private TextView tvAvgTemperature, tvMaxTemperature, tvMinTemperature, tvRecordCount, tvDataSpan;
    private TextView tvRemainingMonths, tvLifespanPrediction;
    private TextView tvSubtitle;
    private ChipGroup chipGroupRange, chipGroupChartType;
    private View sectionAnomalies, cardAnomalies, sectionChargingAdvice, cardChargingAdvice;
    private View sectionTempStats, cardTempStats;
    private LinearLayout anomalyList, adviceList;

    private TrendViewModel viewModel;

    // 缓存当前趋势数据，用于图表点击回调展示详情
    private GetTrendDataUseCase.Result currentTrendResult;

    // 暗色模式颜色
    private int chartMainColor;
    private int chartTempColor;
    private int chartGridColor;
    private int chartTextColor;
    private int chartFillColor;
    private int chartTempFillColor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.fragment_trend, container, false);
            initViews(view);
            initThemeColors();
            animateEntry(view);
            return view;
        } catch (Exception e) {
            Log.e(TAG, "Error creating view", e);
            Context ctx = getContext();
            if (ctx == null && container != null) ctx = container.getContext();
            return FragmentErrorViewHelper.createErrorView(ctx, e);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            viewModel = new ViewModelProvider(this).get(TrendViewModel.class);
            setupChipGroup();
            setupChart();
            observeViewModel();
            viewModel.loadTrendData(GetTrendDataUseCase.RANGE_30D);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated", e);
        }
    }

    private void initViews(View view) {
        lineChart = view.findViewById(R.id.line_chart);
        tvInitialHealth = view.findViewById(R.id.tv_initial_health);
        tvCurrentHealth = view.findViewById(R.id.tv_current_health);
        tvTotalDecay = view.findViewById(R.id.tv_total_decay);
        tvMonthlyDecay = view.findViewById(R.id.tv_monthly_decay);
        tvAvgTemperature = view.findViewById(R.id.tv_avg_temperature);
        tvMaxTemperature = view.findViewById(R.id.tv_max_temperature);
        tvMinTemperature = null;
        tvRecordCount = view.findViewById(R.id.tv_record_count);
        tvDataSpan = view.findViewById(R.id.tv_data_span);
        tvRemainingMonths = view.findViewById(R.id.tv_remaining_months);
        tvLifespanPrediction = view.findViewById(R.id.tv_lifespan_prediction);
        tvSubtitle = view.findViewById(R.id.tv_subtitle);
        chipGroupRange = view.findViewById(R.id.chip_group_range);
        chipGroupChartType = null;
        sectionAnomalies = view.findViewById(R.id.section_anomalies);
        cardAnomalies = view.findViewById(R.id.card_anomalies);
        sectionChargingAdvice = view.findViewById(R.id.section_charging_advice);
        cardChargingAdvice = view.findViewById(R.id.card_charging_advice);
        sectionTempStats = null;
        cardTempStats = null;
        anomalyList = view.findViewById(R.id.anomaly_list);
        adviceList = view.findViewById(R.id.advice_list);

        buildChartTypeChipGroup(view);
    }

    private void buildChartTypeChipGroup(View view) {
        try {
            Context ctx = getContext();
            if (ctx == null) return;

            ChipGroup chartTypeGroup = new ChipGroup(ctx);
            chartTypeGroup.setSingleSelection(true);
            chartTypeGroup.setSelectionRequired(true);
            chartTypeGroup.setChipSpacingHorizontal(dpToPxTrend(6));
            chipGroupChartType = chartTypeGroup;

            Chip chipHealth = new Chip(ctx);
            chipHealth.setId(View.generateViewId());
            chipHealth.setText("健康度");
            chipHealth.setTextSize(13f);
            chipHealth.setCheckable(true);
            chipHealth.setChecked(true);
            chartTypeGroup.addView(chipHealth);
            final int chipHealthId = chipHealth.getId();

            Chip chipTemp = new Chip(ctx);
            chipTemp.setId(View.generateViewId());
            chipTemp.setText("温度");
            chipTemp.setTextSize(13f);
            chipTemp.setCheckable(true);
            chartTypeGroup.addView(chipTemp);
            final int chipTempId = chipTemp.getId();

            Chip chipCycle = new Chip(ctx);
            chipCycle.setId(View.generateViewId());
            chipCycle.setText("循环次数");
            chipCycle.setTextSize(13f);
            chipCycle.setCheckable(true);
            chartTypeGroup.addView(chipCycle);
            final int chipCycleId = chipCycle.getId();

            chartTypeGroup.setOnCheckedChangeListener(new ChipGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(ChipGroup group, int checkedId) {
                    int chartType;
                    if (checkedId == chipHealthId) {
                        chartType = GetTrendDataUseCase.CHART_TYPE_HEALTH;
                    } else if (checkedId == chipTempId) {
                        chartType = GetTrendDataUseCase.CHART_TYPE_TEMPERATURE;
                    } else if (checkedId == chipCycleId) {
                        chartType = GetTrendDataUseCase.CHART_TYPE_CYCLE;
                    } else {
                        chartType = GetTrendDataUseCase.CHART_TYPE_HEALTH;
                    }
                    if (viewModel != null) {
                        viewModel.switchChartType(chartType);
                    }
                    if (currentTrendResult != null) {
                        updateChart(currentTrendResult);
                    }
                }
            });

            View cardChart = view.findViewById(R.id.card_chart);
            if (cardChart != null && cardChart.getParent() instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) cardChart.getParent();
                int idx = parent.indexOfChild(cardChart);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, dpToPxTrend(12));
                chartTypeGroup.setLayoutParams(lp);
                parent.addView(chartTypeGroup, idx);
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "buildChartTypeChipGroup error", e);
        }
    }

    private int dpToPxTrend(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    /**
     * 根据当前主题初始化图表颜色（暗色模式适配）
     */
    private void initThemeColors() {
        boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        if (isDark) {
            chartMainColor = Color.parseColor("#0A84FF");
            chartTempColor = Color.parseColor("#FF9F0A");
            chartGridColor = Color.parseColor("#38383A");
            chartTextColor = Color.parseColor("#6B6F78");
            chartFillColor = Color.parseColor("#0A84FF");
            chartTempFillColor = Color.parseColor("#FF9F0A");
        } else {
            chartMainColor = Color.parseColor("#007AFF");
            chartTempColor = Color.parseColor("#FF9500");
            chartGridColor = Color.parseColor("#E5E5EA");
            chartTextColor = Color.parseColor("#8A8A8E");
            chartFillColor = Color.parseColor("#007AFF");
            chartTempFillColor = Color.parseColor("#FF9500");
        }
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    private void setupChipGroup() {
        chipGroupRange.setOnCheckedChangeListener(new com.google.android.material.chip.ChipGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(com.google.android.material.chip.ChipGroup group, int checkedId) {
                int rangeIndex;
                if (checkedId == R.id.chip_7d) {
                    rangeIndex = GetTrendDataUseCase.RANGE_7D;
                } else if (checkedId == R.id.chip_30d) {
                    rangeIndex = GetTrendDataUseCase.RANGE_30D;
                } else if (checkedId == R.id.chip_90d) {
                    rangeIndex = GetTrendDataUseCase.RANGE_90D;
                } else if (checkedId == R.id.chip_180d) {
                    rangeIndex = GetTrendDataUseCase.RANGE_180D;
                } else {
                    rangeIndex = GetTrendDataUseCase.RANGE_30D;
                }
                viewModel.switchRange(rangeIndex);
            }
        });
    }

    private void setupChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.getLegend().setEnabled(false);

        // 启用交互：缩放/拖拽/点击
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setDoubleTapToZoomEnabled(true);

        // X轴
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(chartTextColor);
        xAxis.setTextSize(10f);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(6, true);

        // 左Y轴（健康度）
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(chartGridColor);
        leftAxis.setTextColor(chartTextColor);
        leftAxis.setTextSize(10f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(100f);

        // 右Y轴（温度）
        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(true);
        rightAxis.setDrawGridLines(false);
        rightAxis.setTextColor(chartTempColor);
        rightAxis.setTextSize(10f);
        rightAxis.setAxisMinimum(0f);
        rightAxis.setAxisMaximum(60f);

        // 数据点点击回调：显示该时间点的健康度和温度详情
        lineChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                if (currentTrendResult == null || currentTrendResult.dailyPoints == null) return;
                int index = Math.min(Math.max((int) e.getX(), 0), currentTrendResult.dailyPoints.size() - 1);
                GetTrendDataUseCase.DailyPoint point = currentTrendResult.dailyPoints.get(index);
                showPointDetailDialog(point);
            }

            @Override
            public void onNothingSelected() {
            }
        });
    }

    private void observeViewModel() {
        viewModel.getTrendData().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<GetTrendDataUseCase.Result>() {
            @Override
            public void onChanged(GetTrendDataUseCase.Result result) {
                updateUI(result);
            }
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<Boolean>() {
            @Override
            public void onChanged(Boolean loading) {
                // 可扩展：显示/隐藏加载指示器
            }
        });
    }

    private void updateUI(GetTrendDataUseCase.Result result) {
        this.currentTrendResult = result;
        if (result == null) return;

        if (!result.hasData || result.dailyPoints == null || result.dailyPoints.isEmpty()) {
            showNoDataState();
            return;
        }

        // 更新副标题
        updateSubtitle(result.rangeIndex);

        // 统计数据
        updateStats(result);

        // 图表
        updateChart(result);

        // 寿命预测
        updateLifespan(result);

        // 异常事件
        updateAnomalies(result);

        // 充电建议
        updateChargingAdvice(result);
    }

    private void updateSubtitle(int rangeIndex) {
        String subtitle;
        switch (rangeIndex) {
            case GetTrendDataUseCase.RANGE_7D:
                subtitle = getString(R.string.subtitle_trend_7d);
                break;
            case GetTrendDataUseCase.RANGE_30D:
                subtitle = getString(R.string.subtitle_trend_30d);
                break;
            case GetTrendDataUseCase.RANGE_90D:
                subtitle = getString(R.string.subtitle_trend_90d);
                break;
            case GetTrendDataUseCase.RANGE_180D:
                subtitle = getString(R.string.subtitle_trend_180d);
                break;
            default:
                subtitle = getString(R.string.subtitle_trend);
                break;
        }
        tvSubtitle.setText(subtitle);
    }

    private void updateStats(GetTrendDataUseCase.Result result) {
        tvInitialHealth.setText(result.initialHealth >= 0
                ? String.format(Locale.getDefault(), "%.1f%%", result.initialHealth) : "--");
        tvCurrentHealth.setText(result.currentHealth >= 0
                ? String.format(Locale.getDefault(), "%.1f%%", result.currentHealth) : "--");
        tvTotalDecay.setText(result.totalDecay > 0
                ? String.format(Locale.getDefault(), "%.1f%%", result.totalDecay) : "--");
        tvMonthlyDecay.setText(result.monthlyDecay > 0
                ? String.format(Locale.getDefault(), "%.2f%%", result.monthlyDecay) : "--");

        tvAvgTemperature.setText(result.avgTemperature > 0
                ? String.format(Locale.getDefault(), "%.1f°C", result.avgTemperature) : "--");
        tvMaxTemperature.setText(result.maxTemperature > 0
                ? String.format(Locale.getDefault(), "%.1f°C", result.maxTemperature) : "--");
        if (tvMinTemperature != null) {
            tvMinTemperature.setText(result.minTemperature > 0
                    ? String.format(Locale.getDefault(), "%.1f°C", result.minTemperature) : "--");
        }
        tvRecordCount.setText(String.valueOf(result.recordCount));
        tvDataSpan.setText(result.dataSpanDays > 0
                ? String.format(Locale.getDefault(), "%d天", result.dataSpanDays) : "--");

        updateTempStatsCard(result);
    }

    private void updateTempStatsCard(GetTrendDataUseCase.Result result) {
        if (sectionTempStats == null || cardTempStats == null) return;
        if (result.temperaturePoints == null || result.temperaturePoints.isEmpty()) {
            sectionTempStats.setVisibility(View.GONE);
            cardTempStats.setVisibility(View.GONE);
            return;
        }
        sectionTempStats.setVisibility(View.VISIBLE);
        cardTempStats.setVisibility(View.VISIBLE);
    }

    private void updateLifespan(GetTrendDataUseCase.Result result) {
        if (result.remainingMonths >= 0) {
            tvRemainingMonths.setText(String.format(Locale.getDefault(), "%.0f", result.remainingMonths));
            // 根据剩余月数设置颜色
            if (result.remainingMonths > 24) {
                tvRemainingMonths.setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            } else if (result.remainingMonths > 12) {
                tvRemainingMonths.setTextColor(ContextCompat.getColor(requireContext(), R.color.label));
            } else if (result.remainingMonths > 6) {
                tvRemainingMonths.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange));
            } else {
                tvRemainingMonths.setTextColor(ContextCompat.getColor(requireContext(), R.color.red));
            }
        } else {
            tvRemainingMonths.setText("--");
            tvRemainingMonths.setTextColor(ContextCompat.getColor(requireContext(), R.color.label));
        }

        tvLifespanPrediction.setText(result.lifespanPrediction != null && !result.lifespanPrediction.isEmpty()
                ? result.lifespanPrediction : getString(R.string.lifespan_no_data));
    }

    private void updateAnomalies(GetTrendDataUseCase.Result result) {
        if (result.anomalies == null || result.anomalies.isEmpty()) {
            sectionAnomalies.setVisibility(View.GONE);
            cardAnomalies.setVisibility(View.GONE);
            return;
        }

        sectionAnomalies.setVisibility(View.VISIBLE);
        cardAnomalies.setVisibility(View.VISIBLE);
        anomalyList.removeAllViews();

        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.getDefault());
        for (GetTrendDataUseCase.Anomaly anomaly : result.anomalies) {
            TextView tv = new TextView(requireContext());
            String date = sdf.format(new Date(anomaly.timestamp));
            tv.setText(String.format(Locale.getDefault(),
                    "%s  健康度骤降 %.1f%%（%.1f%% → %.1f%%）",
                    date, anomaly.healthDrop, anomaly.healthBefore, anomaly.healthAfter));
            tv.setTextSize(13f);
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.red));
            tv.setPadding(0, 6, 0, 6);
            anomalyList.addView(tv);
        }
    }

    private void updateChargingAdvice(GetTrendDataUseCase.Result result) {
        if (result.chargingAdvice == null || result.chargingAdvice.isEmpty()) {
            sectionChargingAdvice.setVisibility(View.GONE);
            cardChargingAdvice.setVisibility(View.GONE);
            return;
        }

        sectionChargingAdvice.setVisibility(View.VISIBLE);
        cardChargingAdvice.setVisibility(View.VISIBLE);
        adviceList.removeAllViews();

        for (String tip : result.chargingAdvice) {
            TextView tv = new TextView(requireContext());
            tv.setText("• " + tip);
            tv.setTextSize(13f);
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.label_2));
            tv.setPadding(0, 6, 0, 6);
            adviceList.addView(tv);
        }
    }

    private void updateChart(GetTrendDataUseCase.Result result) {
        List<GetTrendDataUseCase.DailyPoint> dailyPoints = result.dailyPoints;
        if (dailyPoints.isEmpty()) {
            showNoDataState();
            return;
        }

        int chartType = GetTrendDataUseCase.CHART_TYPE_HEALTH;
        if (viewModel != null && viewModel.getCurrentChartType().getValue() != null) {
            chartType = viewModel.getCurrentChartType().getValue();
        }

        long minTs = dailyPoints.get(0).timestamp;
        long maxTs = dailyPoints.get(dailyPoints.size() - 1).timestamp;
        long tsRange = maxTs - minTs;

        YAxis leftAxis = lineChart.getAxisLeft();
        YAxis rightAxis = lineChart.getAxisRight();

        leftAxis.removeAllLimitLines();
        rightAxis.removeAllLimitLines();

        List<ILineDataSet> dataSets = new ArrayList<>();

        switch (chartType) {
            case GetTrendDataUseCase.CHART_TYPE_TEMPERATURE:
                dataSets.addAll(buildTemperatureDataSets(dailyPoints, minTs, tsRange));
                leftAxis.setEnabled(false);
                rightAxis.setEnabled(true);
                rightAxis.setAxisMinimum(0f);
                rightAxis.setAxisMaximum(60f);
                addHighTempLimitLine(rightAxis);
                break;

            case GetTrendDataUseCase.CHART_TYPE_CYCLE:
                dataSets.add(buildCycleDataSet(dailyPoints, minTs, tsRange));
                leftAxis.setEnabled(true);
                rightAxis.setEnabled(false);
                leftAxis.setAxisMinimum(0f);
                leftAxis.setAxisMaximum(calculateMaxCycle(dailyPoints) * 1.2f);
                break;

            case GetTrendDataUseCase.CHART_TYPE_HEALTH:
            default:
                dataSets.add(buildHealthDataSet(dailyPoints, minTs, tsRange));
                LineDataSet tempDataSet = buildTempOverlayDataSet(dailyPoints, minTs, tsRange);
                if (tempDataSet != null) {
                    dataSets.add(tempDataSet);
                }
                leftAxis.setEnabled(true);
                rightAxis.setEnabled(tempDataSet != null);
                leftAxis.setAxisMinimum(0f);
                leftAxis.setAxisMaximum(100f);
                rightAxis.setAxisMinimum(0f);
                rightAxis.setAxisMaximum(60f);
                if (tempDataSet != null) {
                    addHighTempLimitLine(rightAxis);
                }
                break;
        }

        LineData lineData = new LineData(dataSets);
        lineChart.setData(lineData);

        XAxis xAxis = lineChart.getXAxis();
        if (tsRange > 0 && dailyPoints.size() >= 2) {
            final long finalMinTs = minTs;
            final long finalTsRange = tsRange;
            xAxis.setValueFormatter(new ValueFormatter() {
                private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.getDefault());

                @Override
                public String getFormattedValue(float value) {
                    long ts = finalMinTs + (long) (value * finalTsRange);
                    return sdf.format(new Date(ts));
                }
            });
            xAxis.setLabelCount(Math.min(6, dailyPoints.size()), true);
        } else {
            xAxis.setLabelCount(Math.min(6, dailyPoints.size()), true);
        }

        lineChart.invalidate();
    }

    private float calculateMaxCycle(List<GetTrendDataUseCase.DailyPoint> dailyPoints) {
        float maxCycle = 0;
        for (GetTrendDataUseCase.DailyPoint dp : dailyPoints) {
            if (dp.cycleCount > maxCycle) maxCycle = dp.cycleCount;
        }
        return Math.max(maxCycle, 100f);
    }

    private void addHighTempLimitLine(YAxis axis) {
        LimitLine highTempLine = new LimitLine(GetTrendDataUseCase.HIGH_TEMP_THRESHOLD, "");
        highTempLine.setLineWidth(1f);
        highTempLine.setLineColor(Color.parseColor("#FF3B30"));
        highTempLine.enableDashedLine(6f, 4f, 0f);
        axis.addLimitLine(highTempLine);
    }

    private LineDataSet buildHealthDataSet(List<GetTrendDataUseCase.DailyPoint> dailyPoints,
                                            long minTs, long tsRange) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < dailyPoints.size(); i++) {
            GetTrendDataUseCase.DailyPoint dp = dailyPoints.get(i);
            float x = getXValue(dp.timestamp, minTs, tsRange, i, dailyPoints.size());
            if (dp.health >= 0) {
                entries.add(new Entry(x, dp.health));
            }
        }
        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setDrawCircles(true);
        dataSet.setCircleRadius(3f);
        dataSet.setCircleColor(chartMainColor);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(3f);
        dataSet.setColor(chartMainColor);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(chartFillColor);
        dataSet.setFillAlpha(40);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        return dataSet;
    }

    private LineDataSet buildTempOverlayDataSet(List<GetTrendDataUseCase.DailyPoint> dailyPoints,
                                                 long minTs, long tsRange) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < dailyPoints.size(); i++) {
            GetTrendDataUseCase.DailyPoint dp = dailyPoints.get(i);
            float x = getXValue(dp.timestamp, minTs, tsRange, i, dailyPoints.size());
            if (dp.avgTemperature > 0) {
                entries.add(new Entry(x, dp.avgTemperature));
            }
        }
        if (entries.isEmpty()) return null;
        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);
        dataSet.setColor(chartTempColor);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(chartTempFillColor);
        dataSet.setFillAlpha(20);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        dataSet.enableDashedLine(8f, 4f, 0f);
        return dataSet;
    }

    private List<ILineDataSet> buildTemperatureDataSets(List<GetTrendDataUseCase.DailyPoint> dailyPoints,
                                                          long minTs, long tsRange) {
        List<ILineDataSet> sets = new ArrayList<>();

        List<Entry> avgEntries = new ArrayList<>();
        List<Entry> maxEntries = new ArrayList<>();
        List<Entry> minEntries = new ArrayList<>();

        for (int i = 0; i < dailyPoints.size(); i++) {
            GetTrendDataUseCase.DailyPoint dp = dailyPoints.get(i);
            float x = getXValue(dp.timestamp, minTs, tsRange, i, dailyPoints.size());
            if (dp.avgTemperature > 0) {
                avgEntries.add(new Entry(x, dp.avgTemperature));
            }
            if (dp.maxTemperature > 0) {
                maxEntries.add(new Entry(x, dp.maxTemperature));
            }
            if (dp.minTemperature > 0) {
                minEntries.add(new Entry(x, dp.minTemperature));
            }
        }

        if (!maxEntries.isEmpty()) {
            LineDataSet maxSet = new LineDataSet(maxEntries, "");
            maxSet.setDrawCircles(false);
            maxSet.setDrawValues(false);
            maxSet.setLineWidth(1.5f);
            maxSet.setColor(Color.parseColor("#FF9F0A"));
            maxSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            maxSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
            maxSet.enableDashedLine(4f, 3f, 0f);
            sets.add(maxSet);
        }

        if (!minEntries.isEmpty()) {
            LineDataSet minSet = new LineDataSet(minEntries, "");
            minSet.setDrawCircles(false);
            minSet.setDrawValues(false);
            minSet.setLineWidth(1.5f);
            minSet.setColor(Color.parseColor("#64D2FF"));
            minSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            minSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
            minSet.enableDashedLine(4f, 3f, 0f);
            sets.add(minSet);
        }

        if (!avgEntries.isEmpty()) {
            LineDataSet avgSet = new LineDataSet(avgEntries, "");
            avgSet.setDrawCircles(true);
            avgSet.setCircleRadius(2.5f);
            avgSet.setCircleColor(chartTempColor);
            avgSet.setDrawValues(false);
            avgSet.setLineWidth(2.5f);
            avgSet.setColor(chartTempColor);
            avgSet.setDrawFilled(true);
            avgSet.setFillColor(chartTempFillColor);
            avgSet.setFillAlpha(30);
            avgSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            avgSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
            sets.add(avgSet);
        }

        return sets;
    }

    private LineDataSet buildCycleDataSet(List<GetTrendDataUseCase.DailyPoint> dailyPoints,
                                           long minTs, long tsRange) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < dailyPoints.size(); i++) {
            GetTrendDataUseCase.DailyPoint dp = dailyPoints.get(i);
            float x = getXValue(dp.timestamp, minTs, tsRange, i, dailyPoints.size());
            if (dp.cycleCount >= 0) {
                entries.add(new Entry(x, dp.cycleCount));
            }
        }
        int cycleColor = Color.parseColor("#30D158");
        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setDrawCircles(true);
        dataSet.setCircleRadius(3f);
        dataSet.setCircleColor(cycleColor);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2.5f);
        dataSet.setColor(cycleColor);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(cycleColor);
        dataSet.setFillAlpha(25);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        return dataSet;
    }

    private float getXValue(long timestamp, long minTs, long tsRange, int index, int totalCount) {
        if (tsRange > 0 && totalCount > 1) {
            return (timestamp - minTs) / (float) tsRange;
        } else {
            return index;
        }
    }

    private void showNoDataState() {
        tvInitialHealth.setText("--");
        tvCurrentHealth.setText("--");
        tvTotalDecay.setText("--");
        tvMonthlyDecay.setText("--");
        tvAvgTemperature.setText("--");
        tvMaxTemperature.setText("--");
        if (tvMinTemperature != null) tvMinTemperature.setText("--");
        tvRecordCount.setText("--");
        tvDataSpan.setText("--");
        tvRemainingMonths.setText("--");
        tvLifespanPrediction.setText(getString(R.string.lifespan_no_data));

        sectionAnomalies.setVisibility(View.GONE);
        cardAnomalies.setVisibility(View.GONE);
        sectionChargingAdvice.setVisibility(View.GONE);
        cardChargingAdvice.setVisibility(View.GONE);
        if (sectionTempStats != null) sectionTempStats.setVisibility(View.GONE);
        if (cardTempStats != null) cardTempStats.setVisibility(View.GONE);

        List<Entry> entries = new ArrayList<>();
        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(3f);
        dataSet.setColor(chartMainColor);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(chartFillColor);
        dataSet.setFillAlpha(40);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        lineChart.setNoDataText(getString(R.string.health_check_no_data));
        lineChart.invalidate();
    }

    /**
     * 显示数据点详情弹窗，展示该时间点的健康度、温度等详细信息。
     */
    private void showPointDetailDialog(GetTrendDataUseCase.DailyPoint point) {
        if (!isAdded() || point == null) return;
        Context ctx = requireContext();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
        String dateStr = sdf.format(new Date(point.timestamp));

        StringBuilder message = new StringBuilder();
        message.append("日期：").append(dateStr).append("\n\n");
        if (point.health >= 0) {
            message.append("健康度：").append(String.format(Locale.getDefault(), "%.1f%%", point.health)).append("\n");
        }
        if (point.avgTemperature > 0) {
            message.append("平均温度：").append(String.format(Locale.getDefault(), "%.1f°C", point.avgTemperature)).append("\n");
        }
        if (point.maxTemperature > 0) {
            message.append("最高温度：").append(String.format(Locale.getDefault(), "%.1f°C", point.maxTemperature)).append("\n");
        }
        if (point.minTemperature > 0) {
            message.append("最低温度：").append(String.format(Locale.getDefault(), "%.1f°C", point.minTemperature)).append("\n");
        }
        if (point.recordCount > 0) {
            message.append("当日记录数：").append(point.recordCount).append("\n");
        }

        new androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle("数据详情")
                .setMessage(message.toString().trim())
                .setPositiveButton("确定", null)
                .show();
    }
}
