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
import com.batteryhealth.app.data.database.BatteryInfoDao;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrendFragment extends Fragment {

    private static final String PREFS_TREND = "trend_prefs";
    private static final String PREF_INITIAL_HEALTH = "initial_health";

    private LineChart lineChart;
    private TextView tvInitialHealth, tvCurrentHealth, tvTotalDecay, tvMonthlyDecay;

    private BatteryDataManager batteryDataManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService dbExecutor;

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

    private BatteryDataManager getBatteryDataManager() {
        if (batteryDataManager != null) return batteryDataManager;
        if (getActivity() instanceof MainActivity) {
            batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
        }
        return batteryDataManager;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbExecutor != null) {
            dbExecutor.shutdown();
            dbExecutor = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
        lineChart = null;
        tvInitialHealth = null;
        tvCurrentHealth = null;
        tvTotalDecay = null;
        tvMonthlyDecay = null;
    }

    /**
     * Load data on a background thread to avoid blocking the UI.
     */
    private void loadDataAsync() {
        if (dbExecutor == null) {
            dbExecutor = Executors.newSingleThreadExecutor();
        }

        float currentHealth = getCurrentHealthPercentage();
        int currentHealthInt = Math.round(currentHealth);

        Context ctx = getContext();
        if (ctx == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_TREND, Context.MODE_PRIVATE);
        int initialHealth = prefs.getInt(PREF_INITIAL_HEALTH, -1);
        if (initialHealth <= 0) {
            initialHealth = currentHealthInt > 0 ? currentHealthInt : 100;
            prefs.edit().putInt(PREF_INITIAL_HEALTH, initialHealth).apply();
        }

        int totalDecay = initialHealth - currentHealthInt;
        if (totalDecay < 0) totalDecay = 0;
        final int finalInitialHealth = initialHealth;
        final int finalTotalDecay = totalDecay;
        final int finalCurrentHealthInt = currentHealthInt;

        dbExecutor.submit(() -> {
            float monthlyDecay = calculateMonthlyDecay();
            List<Entry> realEntries = loadHealthTrendFromDatabase();

            mainHandler.post(() -> {
                if (!isAdded()) return;

                if (tvInitialHealth != null) {
                    tvInitialHealth.setText(String.format(Locale.getDefault(), "%d%%", finalInitialHealth));
                }
                if (tvCurrentHealth != null) {
                    tvCurrentHealth.setText(finalCurrentHealthInt > 0
                            ? String.format(Locale.getDefault(), "%d%%", finalCurrentHealthInt)
                            : "--");
                }
                if (tvTotalDecay != null) {
                    tvTotalDecay.setText(String.format(Locale.getDefault(), "%.1f%%", (float) finalTotalDecay));
                }
                if (tvMonthlyDecay != null) {
                    tvMonthlyDecay.setText(String.format(Locale.getDefault(), "%.1f%%", monthlyDecay));
                }

                setupChart(finalInitialHealth, finalCurrentHealthInt, realEntries);
            });
        });
    }

    /**
     * Get current battery HEALTH percentage from BatteryDataManager.
     * This returns the battery health percentage (健康度), NOT the battery level (电量).
     */
    private float getCurrentHealthPercentage() {
        BatteryDataManager bdm = getBatteryDataManager();
        if (bdm != null) {
            try {
                BatteryInfo info = bdm.getCurrentBatteryInfo();
                if (info != null && info.hasValidHealthData()) {
                    return info.getHealthPercentage();
                }
            } catch (Exception ignored) {
            }
        }
        return -1f;
    }

    /**
     * Calculate monthly decay rate from historical health percentage records in the database.
     * Must be called from a background thread.
     */
    private float calculateMonthlyDecay() {
        try {
            Context ctx = getContext();
            if (ctx == null) return 0f;
            BatteryHealthApplication app = (BatteryHealthApplication) ctx.getApplicationContext();
            if (app == null) return 0f;
            AppDatabase db = app.getDatabase();
            if (db == null) return 0f;
            BatteryInfoDao dao = db.batteryInfoDao();

            // Get records from the last 6 months
            long sixMonthsAgo = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000;
            List<BatteryInfo> records = dao.getSince(sixMonthsAgo);
            if (records == null || records.size() < 2) return 0f;

            // Find the earliest and latest health percentages
            float earliestHealth = -1f;
            float latestHealth = -1f;
            long earliestTime = Long.MAX_VALUE;
            long latestTime = Long.MIN_VALUE;

            for (BatteryInfo info : records) {
                float health = info.getHealthPercentage();
                if (health < 0) continue; // skip invalid records
                long ts = info.getTimestamp();
                if (ts < earliestTime) {
                    earliestTime = ts;
                    earliestHealth = health;
                }
                if (ts > latestTime) {
                    latestTime = ts;
                    latestHealth = health;
                }
            }

            if (earliestHealth < 0 || latestHealth < 0) return 0f;

            float decay = earliestHealth - latestHealth;
            if (decay <= 0) return 0f;

            float monthsElapsed = (latestTime - earliestTime) / (1000f * 60 * 60 * 24 * 30);
            if (monthsElapsed < 0.1f) return 0f;

            return decay / monthsElapsed;
        } catch (Exception ignored) {
        }
        return 0f;
    }

    /**
     * Build the health trend chart using actual health percentage records from the database.
     * Falls back to a linear interpolation if insufficient data exists.
     */
    private void setupChart(int initialHealth, int currentHealth, List<Entry> realEntries) {
        if (lineChart == null) return;

        List<Entry> entries = new ArrayList<>();

        if (realEntries != null && realEntries.size() >= 2) {
            entries = realEntries;
        } else {
            // Fallback: linear interpolation from initial to current over 6 months
            int months = 6;
            float step = (initialHealth - currentHealth) / (float) (months - 1);
            for (int i = 0; i < months; i++) {
                float value = initialHealth - step * i;
                entries.add(new Entry(i, value));
            }
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

    /**
     * Load health percentage trend data from the database.
     * Samples one data point per month over the last 6 months,
     * using the average health percentage within each month.
     * Returns null if insufficient data is available.
     * Must be called from a background thread.
     */
    private List<Entry> loadHealthTrendFromDatabase() {
        try {
            Context ctx = getContext();
            if (ctx == null) return null;
            BatteryHealthApplication app = (BatteryHealthApplication) ctx.getApplicationContext();
            if (app == null) return null;
            AppDatabase db = app.getDatabase();
            if (db == null) return null;
            BatteryInfoDao dao = db.batteryInfoDao();

            long now = System.currentTimeMillis();
            long sixMonthsAgo = now - 180L * 24 * 60 * 60 * 1000;
            List<BatteryInfo> records = dao.getSince(sixMonthsAgo);
            if (records == null || records.size() < 2) return null;

            // Group records by month and compute average health per month
            List<Entry> entries = new ArrayList<>();
            Calendar cal = Calendar.getInstance();
            int currentMonth = -1;
            float monthHealthSum = 0f;
            int monthCount = 0;

            // Determine the starting month index (0 = 6 months ago)
            cal.setTimeInMillis(sixMonthsAgo);
            int startMonth = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH);

            for (BatteryInfo info : records) {
                float health = info.getHealthPercentage();
                if (health < 0) continue; // skip invalid health records

                cal.setTimeInMillis(info.getTimestamp());
                int recordMonth = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH);

                if (currentMonth < 0) {
                    currentMonth = recordMonth;
                }

                if (recordMonth != currentMonth) {
                    // Save the previous month's average
                    if (monthCount > 0) {
                        int idx = currentMonth - startMonth;
                        if (idx >= 0 && idx < 6) {
                            entries.add(new Entry(idx, monthHealthSum / monthCount));
                        }
                    }
                    currentMonth = recordMonth;
                    monthHealthSum = health;
                    monthCount = 1;
                } else {
                    monthHealthSum += health;
                    monthCount++;
                }
            }

            // Don't forget the last month
            if (monthCount > 0 && currentMonth >= 0) {
                int idx = currentMonth - startMonth;
                if (idx >= 0 && idx < 6) {
                    entries.add(new Entry(idx, monthHealthSum / monthCount));
                }
            }

            return entries.size() >= 2 ? entries : null;
        } catch (Exception ignored) {
        }
        return null;
    }
}
