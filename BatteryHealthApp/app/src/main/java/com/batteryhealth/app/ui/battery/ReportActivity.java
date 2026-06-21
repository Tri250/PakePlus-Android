package com.batteryhealth.app.ui.battery;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.ReportGenerator;
import com.batteryhealth.app.utils.UiAnimationHelper;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 周报 / 月报展示页面
 *
 * 展示电池健康度、容量、温度等核心指标的历史统计报告。
 */
public class ReportActivity extends AppCompatActivity {

    private static final String EXTRA_TYPE = "report_type";
    public static final String TYPE_WEEKLY = "weekly";
    public static final String TYPE_MONTHLY = "monthly";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView tvTitle;
    private TextView tvPeriod;
    private TextView tvAvgHealth;
    private TextView tvMinHealth;
    private TextView tvMaxHealth;
    private TextView tvAvgTemp;
    private TextView tvCycleChange;
    private TextView tvStatusSummary;
    private LineChart chartHealth;
    private RecyclerView rvDailyStats;
    private View emptyView;

    private String reportType;
    private ReportAdapter adapter;

    public static void start(Context context, String type) {
        Intent intent = new Intent(context, ReportActivity.class);
        intent.putExtra(EXTRA_TYPE, type);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        reportType = getIntent().getStringExtra(EXTRA_TYPE);
        if (reportType == null) reportType = TYPE_WEEKLY;

        initViews();
        loadReport();
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tv_report_title);
        tvPeriod = findViewById(R.id.tv_report_period);
        tvAvgHealth = findViewById(R.id.tv_avg_health);
        tvMinHealth = findViewById(R.id.tv_min_health);
        tvMaxHealth = findViewById(R.id.tv_max_health);
        tvAvgTemp = findViewById(R.id.tv_avg_temp);
        tvCycleChange = findViewById(R.id.tv_cycle_change);
        tvStatusSummary = findViewById(R.id.tv_status_summary);
        chartHealth = findViewById(R.id.chart_health);
        rvDailyStats = findViewById(R.id.rv_daily_stats);
        emptyView = findViewById(R.id.empty_view);

        boolean isWeekly = TYPE_WEEKLY.equals(reportType);
        tvTitle.setText(isWeekly ? R.string.report_title_weekly : R.string.report_title_monthly);

        rvDailyStats.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReportAdapter();
        rvDailyStats.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        setupChart();
    }

    private void setupChart() {
        chartHealth.getDescription().setEnabled(false);
        chartHealth.setTouchEnabled(true);
        chartHealth.setDragEnabled(true);
        chartHealth.setScaleEnabled(false);
        chartHealth.setPinchZoom(false);
        chartHealth.setDrawGridBackground(false);
        chartHealth.getLegend().setEnabled(false);

        XAxis xAxis = chartHealth.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(getColor(R.color.label_2));
        xAxis.setTextSize(10f);
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.getDefault());
            @Override
            public String getFormattedValue(float value) {
                return sdf.format(new Date((long) value));
            }
        });

        chartHealth.getAxisLeft().setTextColor(getColor(R.color.label_2));
        chartHealth.getAxisLeft().setAxisMinimum(0);
        chartHealth.getAxisLeft().setAxisMaximum(100);
        chartHealth.getAxisRight().setEnabled(false);
    }

    private void loadReport() {
        executor.execute(() -> {
            try {
                AppDatabase db = com.batteryhealth.app.BatteryHealthApplication.getDatabase();
                List<BatteryInfo> data;
                long now = System.currentTimeMillis();
                long startTime;
                String periodStr;

                if (TYPE_WEEKLY.equals(reportType)) {
                    startTime = now - 7L * 24 * 60 * 60 * 1000;
                    periodStr = getString(R.string.report_period_weekly);
                } else {
                    startTime = now - 30L * 24 * 60 * 60 * 1000;
                    periodStr = getString(R.string.report_period_monthly);
                }

                data = db.batteryInfoDao().getSince(startTime);

                if (data == null || data.isEmpty()) {
                    mainHandler.post(() -> showEmpty());
                    return;
                }

                // 计算统计
                float sumHealth = 0, minHealth = 100, maxHealth = 0;
                float sumTemp = 0;
                int cycleStart = -1, cycleEnd = -1;

                for (int i = data.size() - 1; i >= 0; i--) {
                    BatteryInfo info = data.get(i);
                    float h = info.getHealthPercentage();
                    sumHealth += h;
                    if (h < minHealth) minHealth = h;
                    if (h > maxHealth) maxHealth = h;
                    sumTemp += info.getTemperature();

                    int cc = info.getCycleCount();
                    if (cc > 0) {
                        if (cycleStart < 0) cycleStart = cc;
                        cycleEnd = cc;
                    }
                }

                int count = data.size();
                float avgHealth = sumHealth / count;
                float avgTemp = sumTemp / count;
                int cycleChange = (cycleStart > 0 && cycleEnd > 0) ? (cycleEnd - cycleStart) : 0;

                // 生成图表数据
                List<Entry> entries = new ArrayList<>();
                for (int i = data.size() - 1; i >= 0; i--) {
                    BatteryInfo info = data.get(i);
                    entries.add(new Entry(info.getTimestamp(), info.getHealthPercentage()));
                }

                // 生成每日统计列表
                List<ReportAdapter.DailyStat> dailyStats = ReportGenerator.generateDailyStats(data);

                // 状态总结
                String summary = generateSummary(avgHealth, minHealth, cycleChange);

                final float finalAvgHealth = avgHealth;
                final float finalMinHealth = minHealth;
                final float finalMaxHealth = maxHealth;
                final float finalAvgTemp = avgTemp;
                final int finalCycleChange = cycleChange;

                mainHandler.post(() -> {
                    tvPeriod.setText(periodStr);
                    tvAvgHealth.setText(String.format(Locale.getDefault(), "%.1f%%", finalAvgHealth));
                    tvMinHealth.setText(String.format(Locale.getDefault(), "%.1f%%", finalMinHealth));
                    tvMaxHealth.setText(String.format(Locale.getDefault(), "%.1f%%", finalMaxHealth));
                    tvAvgTemp.setText(String.format(Locale.getDefault(), "%.1f°C", finalAvgTemp));
                    tvCycleChange.setText(finalCycleChange > 0 ? "+" + finalCycleChange : String.valueOf(finalCycleChange));
                    tvStatusSummary.setText(summary);

                    showChart(entries);
                    adapter.setData(dailyStats);
                    emptyView.setVisibility(View.GONE);

                    UiAnimationHelper.animateCardsEntry(findViewById(R.id.content_container));
                });

            } catch (Exception e) {
                mainHandler.post(this::showEmpty);
            }
        });
    }

    private void showChart(List<Entry> entries) {
        if (entries.isEmpty()) {
            chartHealth.setVisibility(View.GONE);
            return;
        }
        chartHealth.setVisibility(View.VISIBLE);

        LineDataSet dataSet = new LineDataSet(entries, "健康度");
        dataSet.setColor(getColor(R.color.ios_green));
        dataSet.setLineWidth(2.5f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setFillColor(getColor(R.color.ios_green));
        dataSet.setFillAlpha(30);
        dataSet.setDrawFilled(true);

        LineData lineData = new LineData(dataSet);
        chartHealth.setData(lineData);
        chartHealth.invalidate();
    }

    private String generateSummary(float avgHealth, float minHealth, int cycleChange) {
        if (avgHealth >= 95) {
            return getString(R.string.report_summary_excellent);
        } else if (avgHealth >= 85) {
            return getString(R.string.report_summary_good);
        } else if (avgHealth >= 75) {
            return getString(R.string.report_summary_fair, cycleChange);
        } else {
            return getString(R.string.report_summary_poor, minHealth);
        }
    }

    private void showEmpty() {
        emptyView.setVisibility(View.VISIBLE);
        tvStatusSummary.setText(R.string.report_no_data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
