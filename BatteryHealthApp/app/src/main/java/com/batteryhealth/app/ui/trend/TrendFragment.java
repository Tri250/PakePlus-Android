package com.batteryhealth.app.ui.trend;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
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

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import android.content.Context;
import androidx.core.content.ContextCompat;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 趋势分析 Fragment：展示电池健康度随时间变化的趋势图。
 * 数据来源于 battery_info 数据库表的真实历史记录，而非模拟插值。
 */
public class TrendFragment extends Fragment {

    private static final String PREFS_TREND = "trend_prefs";
    private static final String PREF_INITIAL_HEALTH = "initial_health";
    private static final String PREF_FIRST_RECORD_TIME = "first_record_time";

    private LineChart lineChart;
    private TextView tvInitialHealth, tvCurrentHealth, tvTotalDecay, tvMonthlyDecay;
    private TextView tabHealth, tabTemp, tabCharge;
    private int currentTrendTab = 0; // 0=健康度, 1=温度, 2=充电次数

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private BatteryDataManager batteryDataManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_trend, container, false);
        initViews(view);
        animateEntry(view);
        if (getActivity() instanceof MainActivity) {
            batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
        }
        return view;
    }

    private void initViews(View view) {
        lineChart = view.findViewById(R.id.line_chart);
        tvInitialHealth = view.findViewById(R.id.tv_initial_health);
        tvCurrentHealth = view.findViewById(R.id.tv_current_health);
        tvTotalDecay = view.findViewById(R.id.tv_total_decay);
        tvMonthlyDecay = view.findViewById(R.id.tv_monthly_decay);
        tabHealth = view.findViewById(R.id.tab_health_trend);
        tabTemp = view.findViewById(R.id.tab_temp_trend);
        tabCharge = view.findViewById(R.id.tab_charge_trend);

        if (tabHealth != null) tabHealth.setOnClickListener(v -> switchTrendTab(0));
        if (tabTemp != null) tabTemp.setOnClickListener(v -> switchTrendTab(1));
        if (tabCharge != null) tabCharge.setOnClickListener(v -> switchTrendTab(2));
    }

    private void switchTrendTab(int tab) {
        if (currentTrendTab == tab) return;
        currentTrendTab = tab;
        updateTabStyles();
        loadDataAsync();
    }

    private void updateTabStyles() {
        Context ctx = getContext();
        if (ctx == null) return;
        int activeColor = ContextCompat.getColor(ctx, R.color.ios_blue);
        int inactiveColor = ContextCompat.getColor(ctx, R.color.label_2);

        if (tabHealth != null) {
            tabHealth.setTextColor(currentTrendTab == 0 ? activeColor : inactiveColor);
            tabHealth.setTypeface(null, currentTrendTab == 0 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        if (tabTemp != null) {
            tabTemp.setTextColor(currentTrendTab == 1 ? activeColor : inactiveColor);
            tabTemp.setTypeface(null, currentTrendTab == 1 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        if (tabCharge != null) {
            tabCharge.setTextColor(currentTrendTab == 2 ? activeColor : inactiveColor);
            tabCharge.setTypeface(null, currentTrendTab == 2 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDataAsync();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (ioExecutor != null) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException ie) {
                ioExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 异步加载真实历史数据：根据当前选中的维度加载不同数据源。
     */
    private void loadDataAsync() {
        if (!isAdded()) return;
        final Context appCtx = requireContext().getApplicationContext();
        ioExecutor.submit(() -> {
            try {
                BatteryHealthApplication app = (BatteryHealthApplication) appCtx;
                AppDatabase db = app.getDatabase();

                // 获取当前真实健康度
                float currentHealth = getCurrentHealthFromManager();
                if (currentHealth <= 0) {
                    currentHealth = getCurrentHealthFromIntent();
                }
                int currentHealthInt = Math.max(0, Math.min(100, Math.round(currentHealth)));

                // 根据维度加载不同数据
                List<BatteryInfo> history = null;
                long ninetyDaysAgo = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000;

                if (currentTrendTab == 0) {
                    // 健康度趋势
                    List<BatteryInfo> raw = db != null ? db.batteryInfoDao().getSince(ninetyDaysAgo) : null;
                    history = deduplicateByDay(raw);
                } else if (currentTrendTab == 1) {
                    // 温度趋势
                    List<BatteryInfo> raw = db != null ? db.batteryInfoDao().getSince(ninetyDaysAgo) : null;
                    history = deduplicateByDay(raw);
                } else {
                    // 充电次数趋势 - 从 power_history 表读取
                    history = null;
                }

                final List<BatteryInfo> finalHistory = history;
                final int finalCurrentHealth = currentHealthInt;
                handler.post(() -> {
                    if (!isAdded()) return;
                    renderData(finalHistory, finalCurrentHealth);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (isAdded()) {
                        int fallbackHealth = getCurrentHealthFromIntent();
                        renderData(null, fallbackHealth);
                    }
                });
            }
        });
    }

    /**
     * 按天去重：每天只保留最早一条有有效健康度的记录，使图表更简洁。
     */
    private List<BatteryInfo> deduplicateByDay(List<BatteryInfo> raw) {
        if (raw == null || raw.isEmpty()) return null;
        List<BatteryInfo> result = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        int lastDay = -1;
        for (BatteryInfo info : raw) {
            if (info == null || !info.hasValidHealthData()) continue;
            cal.setTimeInMillis(info.getTimestamp());
            int day = cal.get(Calendar.DAY_OF_YEAR) + cal.get(Calendar.YEAR) * 1000;
            if (day != lastDay) {
                result.add(info);
                lastDay = day;
            }
        }
        return result.isEmpty() ? null : result;
    }

    private void renderData(List<BatteryInfo> history, int currentHealth) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_TREND, Context.MODE_PRIVATE);

        int initialHealth;
        long firstRecordTime = prefs.getLong(PREF_FIRST_RECORD_TIME, 0);

        if (history != null && !history.isEmpty()) {
            // 有历史数据：初始健康度 = 最早记录的健康度
            float firstHealth = history.get(0).getHealthPercentage();
            initialHealth = Math.max(0, Math.min(100, Math.round(firstHealth)));
            if (firstRecordTime == 0) {
                firstRecordTime = history.get(0).getTimestamp();
                prefs.edit().putLong(PREF_FIRST_RECORD_TIME, firstRecordTime).apply();
            }
        } else {
            // 无历史数据：回退到 SharedPreferences 缓存的初始值
            initialHealth = prefs.getInt(PREF_INITIAL_HEALTH, currentHealth);
            if (initialHealth == 0 || initialHealth < currentHealth) {
                initialHealth = currentHealth;
                prefs.edit().putInt(PREF_INITIAL_HEALTH, initialHealth).apply();
            }
        }

        int totalDecay = initialHealth - currentHealth;
        float monthlyDecay;
        if (firstRecordTime > 0) {
            long days = Math.max(1, (System.currentTimeMillis() - firstRecordTime) / (24L * 60 * 60 * 1000));
            monthlyDecay = (totalDecay / (float) days) * 30f;
        } else {
            monthlyDecay = totalDecay / 6f; // 无时间基准时按6个月估算
        }

        tvInitialHealth.setText(String.format(Locale.getDefault(), "%d%%", initialHealth));
        tvCurrentHealth.setText(String.format(Locale.getDefault(), "%d%%", currentHealth));
        tvTotalDecay.setText(String.format(Locale.getDefault(), "%.1f%%", (float) totalDecay));
        tvMonthlyDecay.setText(String.format(Locale.getDefault(), "%.1f%%", monthlyDecay));

        setupChart(history, initialHealth, currentHealth);
    }

    private float getCurrentHealthFromManager() {
        if (batteryDataManager != null) {
            batteryDataManager.refreshFromStickyIntent();
            BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
            if (info != null && info.hasValidHealthData()) {
                return info.getHealthPercentage();
            }
        }
        return -1;
    }

    private int getCurrentHealthFromIntent() {
        try {
            android.content.Intent intent = requireContext().registerReceiver(null,
                    new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (intent != null) {
                int level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                return (int) ((level / (float) scale) * 100);
            }
        } catch (Exception ignored) {
        }
        return 100;
    }

    /**
     * 使用真实历史数据绘制趋势图。根据当前维度绘制不同数据。
     */
    private void setupChart(List<BatteryInfo> history, int initialHealth, int currentHealth) {
        List<Entry> entries = new ArrayList<>();
        String chartLabel = "健康度";
        int chartColor = Color.parseColor("#0A84FF");
        float yMin = 0f;
        float yMax = 100f;

        if (currentTrendTab == 0) {
            // 健康度趋势
            chartLabel = "健康度";
            chartColor = Color.parseColor("#0A84FF");
            yMin = 0f;
            yMax = 100f;
            if (history != null && history.size() >= 2) {
                long firstTime = history.get(0).getTimestamp();
                long lastTime = history.get(history.size() - 1).getTimestamp();
                long timeSpan = Math.max(1, lastTime - firstTime);
                for (BatteryInfo info : history) {
                    float x = (info.getTimestamp() - firstTime) / (float) timeSpan * 5f;
                    float y = Math.max(0, Math.min(100, info.getHealthPercentage()));
                    entries.add(new Entry(x, y));
                }
            } else {
                entries.add(new Entry(0, initialHealth));
                entries.add(new Entry(5, currentHealth));
            }
        } else if (currentTrendTab == 1) {
            // 温度趋势
            chartLabel = "温度 (°C)";
            chartColor = Color.parseColor("#FF9500");
            yMin = 15f;
            yMax = 55f;
            if (history != null && history.size() >= 2) {
                long firstTime = history.get(0).getTimestamp();
                long lastTime = history.get(history.size() - 1).getTimestamp();
                long timeSpan = Math.max(1, lastTime - firstTime);
                for (BatteryInfo info : history) {
                    if (info.getTemperature() > 0) {
                        float x = (info.getTimestamp() - firstTime) / (float) timeSpan * 5f;
                        float y = Math.min(yMax, Math.max(yMin, info.getTemperature()));
                        entries.add(new Entry(x, y));
                    }
                }
            }
            if (entries.isEmpty()) {
                entries.add(new Entry(0, 25));
                entries.add(new Entry(5, 25));
            }
        } else {
            // 充电次数趋势
            chartLabel = "充电次数";
            chartColor = Color.parseColor("#34C759");
            yMin = 0f;
            yMax = 10f;
            if (history != null && history.size() >= 2) {
                // 按天统计充电次数
                Calendar cal = Calendar.getInstance();
                java.util.Map<String, Integer> dailyCounts = new java.util.LinkedHashMap<>();
                long firstTime = Long.MAX_VALUE;
                for (BatteryInfo info : history) {
                    cal.setTimeInMillis(info.getTimestamp());
                    String dayKey = cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DAY_OF_MONTH);
                    Integer count = dailyCounts.get(dayKey);
                    dailyCounts.put(dayKey, (count == null ? 0 : count) + 1);
                    if (info.getTimestamp() < firstTime) firstTime = info.getTimestamp();
                }
                long lastTime = history.get(history.size() - 1).getTimestamp();
                long timeSpan = Math.max(1, lastTime - firstTime);
                int idx = 0;
                for (java.util.Map.Entry<String, Integer> e : dailyCounts.entrySet()) {
                    float x = (float) idx / Math.max(1, dailyCounts.size() - 1) * 5f;
                    float y = Math.min(yMax, e.getValue());
                    entries.add(new Entry(x, y));
                    idx++;
                }
            }
            if (entries.isEmpty()) {
                entries.add(new Entry(0, 0));
                entries.add(new Entry(5, 0));
            }
        }

        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setDrawCircles(entries.size() < 30);
        dataSet.setCircleColor(chartColor);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2.5f);
        dataSet.setColor(chartColor);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(chartColor);
        dataSet.setFillAlpha(72);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);

        lineChart.getDescription().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(Color.parseColor("#8A8A8E"));
        xAxis.setTextSize(10f);
        xAxis.setLabelCount(6, true);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (history == null || history.isEmpty()) return "";
                int idx = Math.min(history.size() - 1, Math.max(0, (int) ((value / 5f) * history.size())));
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(history.get(idx).getTimestamp());
                return String.format(Locale.getDefault(), "%d/%d",
                        cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            }
        });

        lineChart.getAxisLeft().setDrawGridLines(true);
        lineChart.getAxisLeft().setGridColor(Color.parseColor("#E5E5EA"));
        lineChart.getAxisLeft().setTextColor(Color.parseColor("#8A8A8E"));
        lineChart.getAxisLeft().setTextSize(10f);
        lineChart.getAxisLeft().setAxisMinimum(yMin);
        lineChart.getAxisLeft().setAxisMaximum(yMax);
        lineChart.getAxisRight().setEnabled(false);

        lineChart.invalidate();
    }
}
