package com.batteryhealth.app.ui.trend;

import android.content.Context;
import android.content.SharedPreferences;
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

import com.batteryhealth.app.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TrendFragment extends Fragment {

    private static final String PREFS_TREND = "trend_prefs";
    private static final String PREF_INITIAL_HEALTH = "initial_health";

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
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_TREND, Context.MODE_PRIVATE);
        int currentHealth = getCurrentHealth();
        int initialHealth = prefs.getInt(PREF_INITIAL_HEALTH, currentHealth);
        if (initialHealth == 0) {
            initialHealth = currentHealth;
            prefs.edit().putInt(PREF_INITIAL_HEALTH, initialHealth).apply();
        }

        int totalDecay = initialHealth - currentHealth;
        float monthlyDecay = totalDecay / 6f;

        tvInitialHealth.setText(String.format(Locale.getDefault(), "%d%%", initialHealth));
        tvCurrentHealth.setText(String.format(Locale.getDefault(), "%d%%", currentHealth));
        tvTotalDecay.setText(String.format(Locale.getDefault(), "%.1f%%", (float) totalDecay));
        tvMonthlyDecay.setText(String.format(Locale.getDefault(), "%.1f%%", monthlyDecay));

        setupChart(initialHealth, currentHealth);
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

    private void setupChart(int initialHealth, int currentHealth) {
        List<Entry> entries = new ArrayList<>();
        int months = 6;
        float step = (initialHealth - currentHealth) / (float) (months - 1);
        for (int i = 0; i < months; i++) {
            float value = initialHealth - step * i;
            entries.add(new Entry(i, value));
        }

        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(3f);
        dataSet.setColor(Color.parseColor("#0A84FF"));
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#0A84FF"));
        dataSet.setFillAlpha(72); // ~28% opacity

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
