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
import android.app.AlertDialog;
import android.widget.ProgressBar;
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
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
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
    private ProgressBar progressLoading;
    private TextView tvInitialHealth, tvCurrentHealth, tvTotalDecay, tvMonthlyDecay;
    private TextView tvAvgTemperature, tvMaxTemperature, tvRecordCount, tvDataSpan;
    private TextView tvRemainingMonths, tvLifespanPrediction;
    private TextView tvSubtitle;
    private ChipGroup chipGroupRange;
    private View sectionAnomalies, cardAnomalies, sectionChargingAdvice, cardChargingAdvice;
    private LinearLayout anomalyList, adviceList;

    private TrendViewModel viewModel;

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
        progressLoading = view.findViewById(R.id.progress_loading);
        tvInitialHealth = view.findViewById(R.id.tv_initial_health);
        tvCurrentHealth = view.findViewById(R.id.tv_current_health);
        tvTotalDecay = view.findViewById(R.id.tv_total_decay);
        tvMonthlyDecay = view.findViewById(R.id.tv_monthly_decay);
        tvAvgTemperature = view.findViewById(R.id.tv_avg_temperature);
        tvMaxTemperature = view.findViewById(R.id.tv_max_temperature);
        tvRecordCount = view.findViewById(R.id.tv_record_count);
        tvDataSpan = view.findViewById(R.id.tv_data_span);
        tvRemainingMonths = view.findViewById(R.id.tv_remaining_months);
        tvLifespanPrediction = view.findViewById(R.id.tv_lifespan_prediction);
        tvSubtitle = view.findViewById(R.id.tv_subtitle);
        chipGroupRange = view.findViewById(R.id.chip_group_range);
        sectionAnomalies = view.findViewById(R.id.section_anomalies);
        cardAnomalies = view.findViewById(R.id.card_anomalies);
        sectionChargingAdvice = view.findViewById(R.id.section_charging_advice);
        cardChargingAdvice = view.findViewById(R.id.card_charging_advice);
        anomalyList = view.findViewById(R.id.anomaly_list);
        adviceList = view.findViewById(R.id.advice_list);
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
        chipGroupRange.setOnCheckedChangeListener((group, checkedId) -> {
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

        // 数据点点击回调：显示数据点详情弹窗
        lineChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                if (e == null || h == null) return;
                // 从 DailyPoint 列表中找到对应索引的原始数据
                int dataIndex = (int) h.getX();
                List<GetTrendDataUseCase.DailyPoint> dailyPoints =
                        viewModel.getTrendData().getValue() != null
                        ? viewModel.getTrendData().getValue().dailyPoints : null;
                if (dailyPoints == null || dataIndex < 0 || dataIndex >= dailyPoints.size()) return;

                GetTrendDataUseCase.DailyPoint dp = dailyPoints.get(dataIndex);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String dateStr = sdf.format(new Date(dp.timestamp));
                StringBuilder detail = new StringBuilder();
                detail.append("日期：").append(dateStr).append("\n");
                if (dp.health >= 0) {
                    detail.append("健康度：").append(String.format(Locale.getDefault(), "%.1f%%", dp.health)).append("\n");
                }
                if (dp.avgTemperature > 0) {
                    detail.append("平均温度：").append(String.format(Locale.getDefault(), "%.1f°C", dp.avgTemperature)).append("\n");
                }
                if (dp.maxTemperature > 0) {
                    detail.append("最高温度：").append(String.format(Locale.getDefault(), "%.1f°C", dp.maxTemperature)).append("\n");
                }
                if (dp.cycleCount >= 0) {
                    detail.append("充电次数：").append(dp.cycleCount).append(" 次");
                }
                showDataPointDetailDialog(detail.toString().trim());
            }

            @Override
            public void onNothingSelected() {
                // 用户取消选择，无需操作
            }
        });
    }

    private void observeViewModel() {
        viewModel.getTrendData().observe(getViewLifecycleOwner(), this::updateUI);

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            if (progressLoading != null) {
                progressLoading.setVisibility(loading != null && loading ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void updateUI(GetTrendDataUseCase.Result result) {
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
        tvRecordCount.setText(String.valueOf(result.recordCount));
        tvDataSpan.setText(result.dataSpanDays > 0
                ? String.format(Locale.getDefault(), "%d天", result.dataSpanDays) : "--");
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

        List<Entry> healthEntries = new ArrayList<>();
        List<Entry> tempEntries = new ArrayList<>();

        long minTs = dailyPoints.get(0).timestamp;
        long maxTs = dailyPoints.get(dailyPoints.size() - 1).timestamp;
        long tsRange = maxTs - minTs;

        for (int i = 0; i < dailyPoints.size(); i++) {
            GetTrendDataUseCase.DailyPoint dp = dailyPoints.get(i);
            float x;
            if (tsRange > 0 && dailyPoints.size() > 1) {
                x = (dp.timestamp - minTs) / (float) tsRange;
            } else {
                x = i;
            }
            if (dp.health >= 0) {
                healthEntries.add(new Entry(x, dp.health));
            }
            if (dp.avgTemperature > 0) {
                tempEntries.add(new Entry(x, dp.avgTemperature));
            }
        }

        // 健康度数据集
        LineDataSet healthDataSet = new LineDataSet(healthEntries, "");
        healthDataSet.setDrawCircles(true);
        healthDataSet.setCircleRadius(3f);
        healthDataSet.setCircleColor(chartMainColor);
        healthDataSet.setDrawValues(false);
        healthDataSet.setLineWidth(3f);
        healthDataSet.setColor(chartMainColor);
        healthDataSet.setDrawFilled(true);
        healthDataSet.setFillColor(chartFillColor);
        healthDataSet.setFillAlpha(40);
        healthDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        healthDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);

        // 温度数据集
        LineDataSet tempDataSet = new LineDataSet(tempEntries, "");
        tempDataSet.setDrawCircles(false);
        tempDataSet.setDrawValues(false);
        tempDataSet.setLineWidth(2f);
        tempDataSet.setColor(chartTempColor);
        tempDataSet.setDrawFilled(true);
        tempDataSet.setFillColor(chartTempFillColor);
        tempDataSet.setFillAlpha(20);
        tempDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        tempDataSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        tempDataSet.enableDashedLine(8f, 4f, 0f);

        LineData lineData = new LineData(healthDataSet, tempDataSet);
        lineChart.setData(lineData);

        // X轴日期格式化
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

    private void showNoDataState() {
        tvInitialHealth.setText("--");
        tvCurrentHealth.setText("--");
        tvTotalDecay.setText("--");
        tvMonthlyDecay.setText("--");
        tvAvgTemperature.setText("--");
        tvMaxTemperature.setText("--");
        tvRecordCount.setText("--");
        tvDataSpan.setText("--");
        tvRemainingMonths.setText("--");
        tvLifespanPrediction.setText(getString(R.string.lifespan_no_data));

        sectionAnomalies.setVisibility(View.GONE);
        cardAnomalies.setVisibility(View.GONE);
        sectionChargingAdvice.setVisibility(View.GONE);
        cardChargingAdvice.setVisibility(View.GONE);

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
     * 显示数据点详情弹窗
     */
    private void showDataPointDetailDialog(String detail) {
        if (detail == null || detail.isEmpty() || getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("数据详情")
                .setMessage(detail)
                .setPositiveButton("确定", null)
                .show();
    }
}
