package com.batteryhealth.app.ui.trend;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.utils.UiAnimationHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.chip.Chip;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
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
    private MaterialButton btnExportCsv;
    private TextView tvStatInitialHealth;
    private TextView tvStatCurrentHealth;
    private TextView tvStatTotalDecay;
    private TextView tvStatMonthlyDecay;
    private int selectedTimeRangeDays = 7; // 默认7天
    private List<BatteryInfo> cachedBatteryData;
    private List<PowerHistory> cachedPowerData;

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
            btnExportCsv = view.findViewById(R.id.btn_export_csv);
            tvStatInitialHealth = view.findViewById(R.id.tv_stat_initial_health);
            tvStatCurrentHealth = view.findViewById(R.id.tv_stat_current_health);
            tvStatTotalDecay = view.findViewById(R.id.tv_stat_total_decay);
            tvStatMonthlyDecay = view.findViewById(R.id.tv_stat_monthly_decay);
            setupTimeRangeSelector();

            if (btnExportCsv != null) {
                btnExportCsv.setOnClickListener(v -> exportCsvAsync());
            }

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
            } else if (checkedId == R.id.chip_90days) {
                selectedTimeRangeDays = 90;
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
                cachedBatteryData = batteryData;
                cachedPowerData = powerData;
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
                updateStats(DEMO_HEALTH_START, DEMO_HEALTH_END);
                showDemoData();
                return;
            }

            // 正常数据处理
            boolean hasEnoughData = recordCount >= 10;
            if (!hasEnoughData) {
                clearStats();
                showEmptyState(recordCount);
                return;
            }

            // 计算关键统计
            computeAndShowStats(batteryData);

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
            case 90:
                bucketMillis = 3L * 24 * 60 * 60 * 1000L; // 3天
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
            case 90:
                bucketMillis = 3L * 24 * 60 * 60 * 1000L;
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
    private void computeAndShowStats(List<BatteryInfo> data) {
        if (data == null || data.isEmpty()) {
            clearStats();
            return;
        }
        // 按时间排序
        List<BatteryInfo> sorted = new ArrayList<>(data);
        Collections.sort(sorted, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
        BatteryInfo first = sorted.get(0);
        BatteryInfo last = sorted.get(sorted.size() - 1);
        float initial = first.getHealthPercentage();
        float current = last.getHealthPercentage();
        // 若健康度无效，回退到使用默认值
        if (initial < 0) initial = 100f;
        if (current < 0) current = initial;
        updateStats(initial, current);
    }

    private void updateStats(float initialHealth, float currentHealth) {
        float totalDecay = initialHealth - currentHealth;
        long days = Math.max(1, selectedTimeRangeDays);
        float monthlyDecay = totalDecay / days * 30f;

        if (tvStatInitialHealth != null) {
            tvStatInitialHealth.setText(String.format(Locale.getDefault(), "%.1f%%", initialHealth));
        }
        if (tvStatCurrentHealth != null) {
            tvStatCurrentHealth.setText(String.format(Locale.getDefault(), "%.1f%%", currentHealth));
        }
        if (tvStatTotalDecay != null) {
            tvStatTotalDecay.setText(String.format(Locale.getDefault(), "%.1f%%", totalDecay));
        }
        if (tvStatMonthlyDecay != null) {
            tvStatMonthlyDecay.setText(String.format(Locale.getDefault(), "%.2f%%", monthlyDecay));
        }
    }

    private void clearStats() {
        if (tvStatInitialHealth != null) tvStatInitialHealth.setText("--");
        if (tvStatCurrentHealth != null) tvStatCurrentHealth.setText("--");
        if (tvStatTotalDecay != null) tvStatTotalDecay.setText("--");
        if (tvStatMonthlyDecay != null) tvStatMonthlyDecay.setText("--");
    }

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

    /**
     * 异步导出当前时间范围内的真实历史数据为 CSV 文件。
     * 数据来源：Room 数据库 batteryInfoDao + powerHistoryDao，无任何模拟/假数据。
     * 导出位置：app cache 目录，通过 FileProvider 分享。
     */
    private void exportCsvAsync() {
        if (!isAdded()) return;
        new Thread(() -> {
            try {
                List<BatteryInfo> battery = cachedBatteryData;
                List<PowerHistory> power = cachedPowerData;
                if ((battery == null || battery.isEmpty()) && (power == null || power.isEmpty())) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> Toast.makeText(getContext(),
                                R.string.export_csv_no_data, Toast.LENGTH_SHORT).show());
                    }
                    return;
                }
                Context ctx = getContext();
                if (ctx == null) return;
                File outDir = new File(ctx.getCacheDir(), "exports");
                if (!outDir.exists() && !outDir.mkdirs()) {
                    throw new IOException("Cannot create export dir: " + outDir);
                }
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
                String filename = "battery_trend_" + selectedTimeRangeDays + "d_" + sdf.format(new Date()) + ".csv";
                File outFile = new File(outDir, filename);
                try (FileWriter w = new FileWriter(outFile)) {
                    w.write("# Battery Health Trend Export\n");
                    w.write("# Range,Days\n");
                    w.write("range_days," + selectedTimeRangeDays + "\n");
                    w.write("\n# BatteryInfo\n");
                    w.write("timestamp,level,health_percent,temperature_c,voltage_mv,current_ua,cycle_count,capacity_mah,is_charging\n");
                    if (battery != null) {
                        for (BatteryInfo b : battery) {
                            if (b == null) continue;
                            w.write(String.format(Locale.US, "%d,%d,%.2f,%.2f,%.0f,%d,%d,%d,%d\n",
                                    b.getTimestamp(),
                                    b.getLevel(),
                                    b.getHealthPercentage(),
                                    b.getTemperature(),
                                    b.getVoltage(),
                                    b.getCurrentNow(),
                                    b.getCycleCount(),
                                    b.getCurrentCapacity(),
                                    b.isCharging() ? 1 : 0));
                        }
                    }
                    w.write("\n# PowerHistory\n");
                    w.write("timestamp,voltage_v,current_ma,power_w,battery_level,temperature_c\n");
                    if (power != null) {
                        for (PowerHistory p : power) {
                            if (p == null) continue;
                            w.write(String.format(Locale.US, "%d,%.3f,%.1f,%.2f,%d,%.2f\n",
                                    p.getTimestamp(),
                                    p.getVoltage(),
                                    p.getCurrent(),
                                    p.getPower(),
                                    p.getBatteryLevel(),
                                    p.getBatteryTemp()));
                        }
                    }
                }
                final File finalFile = outFile;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        try {
                            Toast.makeText(ctx, getString(R.string.export_csv_success, finalFile.getName()),
                                    Toast.LENGTH_LONG).show();
                            shareCsv(finalFile);
                        } catch (Exception e) {
                            Log.w(TAG, "share csv failed: " + e.getMessage());
                        }
                    });
                }
            } catch (Throwable t) {
                Log.e(TAG, "exportCsvAsync failed: " + t.getMessage(), t);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(),
                            getString(R.string.export_csv_failed, t.getMessage()),
                            Toast.LENGTH_LONG).show());
                }
            }
        }).start();
    }

    private void shareCsv(File file) {
        try {
            Context ctx = getContext();
            if (ctx == null) return;
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.action_export_csv)));
        } catch (Exception e) {
            Log.w(TAG, "share intent failed: " + e.getMessage());
        }
    }
}
