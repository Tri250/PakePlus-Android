package com.batteryhealth.app.ui.trend;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
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
 * 趋势追踪页面
 *
 * 功能：
 * 1. 从数据库读取历史电池数据
 * 2. 展示健康度、容量、温度变化曲线
 * 3. 计算总衰减和月均衰减
 */
public class TrendFragment extends Fragment {

    private static final String PREFS_TREND = "trend_prefs";
    private static final String PREF_INITIAL_HEALTH = "initial_health";

    private LineChart chartHealth;
    private LineChart chartTemp;
    private TextView tvInitialHealth, tvCurrentHealth, tvTotalDecay, tvMonthlyDecay;
    private TextView tvDataPoints, tvAvgTemp, tvMaxTemp;
    private View emptyView;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_trend, container, false);
        initViews(view);
        animateEntry(view);
        loadDataAsync();
        return view;
    }

    private void initViews(View view) {
        chartHealth = view.findViewById(R.id.chart_health);
        chartTemp = view.findViewById(R.id.chart_temp);
        tvInitialHealth = view.findViewById(R.id.tv_initial_health);
        tvCurrentHealth = view.findViewById(R.id.tv_current_health);
        tvTotalDecay = view.findViewById(R.id.tv_total_decay);
        tvMonthlyDecay = view.findViewById(R.id.tv_monthly_decay);
        tvDataPoints = view.findViewById(R.id.tv_data_points);
        tvAvgTemp = view.findViewById(R.id.tv_avg_temp);
        tvMaxTemp = view.findViewById(R.id.tv_max_temp);
        emptyView = view.findViewById(R.id.empty_view);

        setupChart(chartHealth, "健康度 (%)");
        setupChart(chartTemp, "温度 (°C)");
    }

    private void setupChart(LineChart chart, String label) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);

        XAxis xAxis = chart.getXAxis();
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

        chart.getAxisLeft().setTextColor(getColor(R.color.label_2));
        chart.getAxisLeft().setTextSize(10f);
        chart.getAxisRight().setEnabled(false);
    }

    private void loadDataAsync() {
        executor.execute(() -> {
            try {
                AppDatabase db = com.batteryhealth.app.BatteryHealthApplication.getDatabase();
                // 读取最近30天的数据
                long since = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
                List<BatteryInfo> records = db.batteryInfoDao().getSince(since);

                if (records == null || records.isEmpty()) {
                    mainHandler.post(() -> showEmpty());
                    return;
                }

                // 健康度数据
                List<Entry> healthEntries = new ArrayList<>();
                List<Entry> tempEntries = new ArrayList<>();
                float sumHealth = 0, sumTemp = 0, maxTemp = -100;
                int healthCount = 0, tempCount = 0;
                int firstHealth = -1, lastHealth = -1;

                for (BatteryInfo info : records) {
                    long ts = info.getTimestamp();
                    if (info.hasValidHealthData()) {
                        float h = info.getHealthPercentage();
                        healthEntries.add(new Entry(ts, h));
                        sumHealth += h;
                        healthCount++;
                        if (firstHealth < 0) firstHealth = (int) h;
                        lastHealth = (int) h;
                    }
                    float t = info.getTemperature();
                    if (t > -100) {
                        tempEntries.add(new Entry(ts, t));
                        sumTemp += t;
                        tempCount++;
                        if (t > maxTemp) maxTemp = t;
                    }
                }

                float avgHealth = healthCount > 0 ? sumHealth / healthCount : 0;
                float avgTemp = tempCount > 0 ? sumTemp / tempCount : 0;
                int totalDecay = firstHealth > 0 && lastHealth > 0 ? firstHealth - lastHealth : 0;
                float monthlyDecay = totalDecay / 30f * 30f; // 简化计算

                final int finalFirstHealth = firstHealth;
                final int finalLastHealth = lastHealth;
                final int finalTotalDecay = totalDecay;
                final float finalMonthlyDecay = monthlyDecay;
                final int finalRecordCount = records.size();
                final float finalAvgTemp = avgTemp;
                final float finalMaxTemp = maxTemp;

                mainHandler.post(() -> {
                    bindData(healthEntries, tempEntries, finalFirstHealth, finalLastHealth, finalTotalDecay, finalMonthlyDecay,
                            finalRecordCount, finalAvgTemp, finalMaxTemp);
                });

            } catch (Exception e) {
                mainHandler.post(this::showEmpty);
            }
        });
    }

    private void bindData(List<Entry> healthEntries, List<Entry> tempEntries,
                          int firstHealth, int lastHealth, int totalDecay, float monthlyDecay,
                          int dataPoints, float avgTemp, float maxTemp) {
        emptyView.setVisibility(View.GONE);

        tvInitialHealth.setText(firstHealth > 0 ? firstHealth + "%" : "--");
        tvCurrentHealth.setText(lastHealth > 0 ? lastHealth + "%" : "--");
        tvTotalDecay.setText(totalDecay >= 0 ? "-" + totalDecay + "%" : "--");
        tvMonthlyDecay.setText(monthlyDecay > 0 ? String.format(Locale.getDefault(), "-%.1f%%", monthlyDecay) : "--");
        tvDataPoints.setText(getString(R.string.trend_data_points, dataPoints));
        tvAvgTemp.setText(String.format(Locale.getDefault(), "%.1f°C", avgTemp));
        tvMaxTemp.setText(String.format(Locale.getDefault(), "%.1f°C", maxTemp));

        showLineData(chartHealth, healthEntries, getColor(R.color.ios_green), 0, 100);
        showLineData(chartTemp, tempEntries, getColor(R.color.ios_orange), 0, 60);
    }

    private void showLineData(LineChart chart, List<Entry> entries, int color, float min, float max) {
        if (entries.isEmpty()) {
            chart.setVisibility(View.GONE);
            return;
        }
        chart.setVisibility(View.VISIBLE);

        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setColor(color);
        dataSet.setLineWidth(2.5f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setFillColor(color);
        dataSet.setFillAlpha(30);
        dataSet.setDrawFilled(true);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.getAxisLeft().setAxisMinimum(min);
        chart.getAxisLeft().setAxisMaximum(max);
        chart.invalidate();
    }

    private void showEmpty() {
        emptyView.setVisibility(View.VISIBLE);
        chartHealth.setVisibility(View.GONE);
        chartTemp.setVisibility(View.GONE);
    }

    private int getColor(int resId) {
        return requireContext().getColor(resId);
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
