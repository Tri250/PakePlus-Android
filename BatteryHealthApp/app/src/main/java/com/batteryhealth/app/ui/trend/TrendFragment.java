package com.batteryhealth.app.ui.trend;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.database.BatteryInfoDao;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;

public class TrendFragment extends Fragment {

    private LineChart lineChart;
    private TextView tvInitialHealth, tvCurrentHealth, tvTotalDecay, tvMonthlyDecay;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_trend, container, false);
        initViews(view);
        animateEntry(view);
        loadData();
        return view;
    }

    private void initViews(View view) {
        lineChart = view.findViewById(R.id.line_chart);
        tvInitialHealth = view.findViewById(R.id.tv_initial_health);
        tvCurrentHealth = view.findViewById(R.id.tv_current_health);
        tvTotalDecay = view.findViewById(R.id.tv_total_decay);
        tvMonthlyDecay = view.findViewById(R.id.tv_monthly_decay);
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    private void loadData() {
        // 优先从数据库读取真实历史健康度数据
        Executors.newSingleThreadExecutor().execute(() -> {
            List<BatteryInfo> history = loadHistoryForTrend();
            float currentHealth = history.isEmpty() ? getCurrentHealth() : history.get(history.size() - 1).getHealthPercentage();
            float initialHealth = currentHealth;
            for (BatteryInfo info : history) {
                if (info.getHealthPercentage() > 0) {
                    initialHealth = Math.max(initialHealth, info.getHealthPercentage());
                }
            }

            float totalDecay = initialHealth - currentHealth;
            float monthlyDecay = totalDecay / 6f;

            final float finalInitial = initialHealth;
            final float finalCurrent = currentHealth;
            final List<BatteryInfo> finalHistory = history;
            requireActivity().runOnUiThread(() -> {
                tvInitialHealth.setText(String.format(Locale.getDefault(), "%.1f%%", finalInitial));
                tvCurrentHealth.setText(String.format(Locale.getDefault(), "%.1f%%", finalCurrent));
                tvTotalDecay.setText(String.format(Locale.getDefault(), "%.1f%%", totalDecay));
                tvMonthlyDecay.setText(String.format(Locale.getDefault(), "%.1f%%", monthlyDecay));
                setupChart(finalHistory, finalInitial, finalCurrent);
            });
        });
    }

    private List<BatteryInfo> loadHistoryForTrend() {
        try {
            AppDatabase db = BatteryHealthApplication.getInstance() != null
                    ? BatteryHealthApplication.getInstance().getDatabase() : null;
            if (db == null) return new ArrayList<>();
            BatteryInfoDao dao = db.batteryInfoDao();
            // 取最近 6 个月的数据
            long sixMonthsAgo = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000;
            return dao.getSince(sixMonthsAgo);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private int getCurrentHealth() {
        android.content.Intent intent = requireContext().registerReceiver(null,
                new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
        if (intent != null) {
            int level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            return (int) ((level / (float) scale) * 100);
        }
        return 100;
    }

    private void setupChart(List<BatteryInfo> history, float initialHealth, float currentHealth) {
        List<Entry> entries = new ArrayList<>();
        int months = 6;
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());

        if (history.size() >= 2) {
            // 按月份聚合真实历史数据，每月取平均值
            long monthMs = 30L * 24 * 60 * 60 * 1000;
            long windowStart = now - months * monthMs;
            for (int i = 0; i < months; i++) {
                long start = windowStart + i * monthMs;
                long end = start + monthMs;
                float sum = 0f;
                int count = 0;
                for (BatteryInfo info : history) {
                    long ts = info.getTimestamp();
                    if (ts >= start && ts < end && info.getHealthPercentage() > 0) {
                        sum += info.getHealthPercentage();
                        count++;
                    }
                }
                float value = count > 0 ? sum / count : Math.max(0, Math.min(100, initialHealth - (initialHealth - currentHealth) * i / (months - 1)));
                entries.add(new Entry(i, value));
            }
        } else {
            // 数据不足时使用当前健康度填充
            for (int i = 0; i < months; i++) {
                entries.add(new Entry(i, currentHealth));
            }
        }

        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setDrawCircles(true);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(3f);
        dataSet.setColor(Color.parseColor("#0A84FF"));
        dataSet.setCircleColor(Color.parseColor("#0A84FF"));
        dataSet.setCircleRadius(3f);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#0A84FF"));
        dataSet.setFillAlpha(72);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);

        lineChart.getDescription().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
        lineChart.setTouchEnabled(false);
        lineChart.setDragEnabled(false);
        lineChart.setScaleEnabled(false);
        lineChart.setPinchZoom(false);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(Color.parseColor("#8A8A8E"));
        xAxis.setTextSize(10f);
        xAxis.setLabelCount(6, true);
        xAxis.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            private final String[] labels = new String[]{"1月", "2月", "3月", "4月", "5月", "6月"};
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                return (idx >= 0 && idx < labels.length) ? labels[idx] : "";
            }
        });

        lineChart.getAxisLeft().setDrawGridLines(true);
        lineChart.getAxisLeft().setGridColor(Color.parseColor("#E5E5EA"));
        lineChart.getAxisLeft().setTextColor(Color.parseColor("#8A8A8E"));
        lineChart.getAxisLeft().setTextSize(10f);
        lineChart.getAxisLeft().setAxisMinimum(0f);
        lineChart.getAxisLeft().setAxisMaximum(100f);
        lineChart.getAxisRight().setEnabled(false);

        lineChart.invalidate();
    }
}
