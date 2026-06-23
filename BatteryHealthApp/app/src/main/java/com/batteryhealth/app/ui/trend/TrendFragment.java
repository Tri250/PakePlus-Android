package com.batteryhealth.app.ui.trend;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrendFragment extends Fragment {

    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LineChart lineChart;
    private TextView tvInitialHealth, tvCurrentHealth, tvTotalDecay, tvMonthlyDecay;
    private TextView tvAvgTemperature, tvMaxTemperature, tvRecordCount, tvDataSpan;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.fragment_trend, container, false);
            initViews(view);
            animateEntry(view);
            loadDataAsync();
            return view;
        } catch (Exception e) {
            Log.e("TrendFragment", "onCreateView failed", e);
            if (container != null && getContext() != null) {
                return new FrameLayout(getContext());
            }
            return null;
        }
    }

    private void initViews(View view) {
        try {
            lineChart = view.findViewById(R.id.line_chart);
            tvInitialHealth = view.findViewById(R.id.tv_initial_health);
            tvCurrentHealth = view.findViewById(R.id.tv_current_health);
            tvTotalDecay = view.findViewById(R.id.tv_total_decay);
            tvMonthlyDecay = view.findViewById(R.id.tv_monthly_decay);
            tvAvgTemperature = view.findViewById(R.id.tv_avg_temperature);
            tvMaxTemperature = view.findViewById(R.id.tv_max_temperature);
            tvRecordCount = view.findViewById(R.id.tv_record_count);
            tvDataSpan = view.findViewById(R.id.tv_data_span);
        } catch (Exception e) {
            Log.e("TrendFragment", "initViews failed", e);
        }
    }

    private void animateEntry(View view) {
        try {
            Context context = getContext();
            if (context == null || view == null) {
                return;
            }
            Animation fadeUp = AnimationUtils.loadAnimation(context, R.anim.fade_up);
            view.startAnimation(fadeUp);
        } catch (Exception e) {
            Log.e("TrendFragment", "animateEntry failed", e);
        }
    }

    private void loadDataAsync() {
        new Thread(() -> {
            try {
                // 1. 从 BatteryDataManager 获取真实健康度
                float currentHealth = getCurrentRealHealth();

                // 2. 从数据库查询近 30 天历史数据
                BatteryHealthApplication app = BatteryHealthApplication.getInstance();
                if (app == null) {
                    postEmptyState();
                    return;
                }
                AppDatabase db = app.getDatabase();
                if (db == null) {
                    postEmptyState();
                    return;
                }
                BatteryInfoDao dao = db.batteryInfoDao();
                long since = System.currentTimeMillis() - THIRTY_DAYS_MS;
                List<BatteryInfo> history = dao.getSince(since);

                if (!isAdded()) return;
                final float finalHealth = currentHealth;
                mainHandler.post(() -> {
                    if (isAdded()) updateUI(history, finalHealth);
                });
            } catch (Exception e) {
                if (isAdded()) {
                    mainHandler.post(() -> {
                        if (isAdded()) postEmptyState();
                    });
                }
            }
        }).start();
    }

    private float getCurrentRealHealth() {
        if (isAdded() && getActivity() instanceof MainActivity) {
            BatteryDataManager mgr = ((MainActivity) getActivity()).getBatteryDataManager();
            if (mgr != null) {
                BatteryInfo info = mgr.getCurrentBatteryInfo();
                if (info != null && info.hasValidHealthData()) {
                    return info.getHealthPercentage();
                }
            }
        }
        return -1;
    }

    private void updateUI(List<BatteryInfo> history, float currentHealth) {
        try {
            if (lineChart == null || tvInitialHealth == null || tvCurrentHealth == null
                    || tvTotalDecay == null || tvMonthlyDecay == null) {
                return;
            }
            if (history == null || history.isEmpty() || currentHealth < 0) {
                showNoDataState();
                return;
            }

            List<BatteryInfo> validHistory = new ArrayList<>();
            for (BatteryInfo info : history) {
                if (info.getHealthPercentage() >= 0) {
                    validHistory.add(info);
                }
            }

            if (validHistory.isEmpty()) {
                showNoDataState();
                return;
            }

            float initialHealth = validHistory.get(0).getHealthPercentage();
            float latestHealth = validHistory.get(validHistory.size() - 1).getHealthPercentage();

            if (currentHealth >= 0) {
                latestHealth = currentHealth;
            }

            float totalDecay = initialHealth - latestHealth;

            long earliestTs = validHistory.get(0).getTimestamp();
            long latestTs = validHistory.get(validHistory.size() - 1).getTimestamp();
            float monthlyDecay = 0f;
            if (latestTs > earliestTs) {
                float daysSpan = (latestTs - earliestTs) / (1000f * 60 * 60 * 24);
                if (daysSpan > 0) {
                    monthlyDecay = totalDecay / daysSpan * 30f;
                }
            }

            tvInitialHealth.setText(String.format(Locale.getDefault(), "%.1f%%", initialHealth));
            tvCurrentHealth.setText(String.format(Locale.getDefault(), "%.1f%%", latestHealth));
            tvTotalDecay.setText(String.format(Locale.getDefault(), "%.1f%%", totalDecay));
            tvMonthlyDecay.setText(String.format(Locale.getDefault(), "%.2f%%", monthlyDecay));

            calculateHistoryStats(validHistory);

            setupChart(validHistory);
        } catch (Exception e) {
            Log.e("TrendFragment", "updateUI failed", e);
        }
    }

    private void calculateHistoryStats(List<BatteryInfo> history) {
        try {
            if (!isAdded()) {
                return;
            }
            if (tvAvgTemperature == null || tvMaxTemperature == null
                    || tvRecordCount == null || tvDataSpan == null) {
                return;
            }
            if (history == null || history.isEmpty()) {
                tvAvgTemperature.setText("--");
                tvMaxTemperature.setText("--");
                tvRecordCount.setText("--");
                tvDataSpan.setText("--");
                return;
            }

            float sumTemp = 0f;
            float maxTemp = Float.MIN_VALUE;
            int validTempCount = 0;

            for (BatteryInfo info : history) {
                float temp = info.getTemperature();
                if (temp > 0) {
                    sumTemp += temp;
                    if (temp > maxTemp) {
                        maxTemp = temp;
                    }
                    validTempCount++;
                }
            }

            if (validTempCount > 0) {
                tvAvgTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", sumTemp / validTempCount));
                tvMaxTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", maxTemp));
            } else {
                tvAvgTemperature.setText("--");
                tvMaxTemperature.setText("--");
            }

            tvRecordCount.setText(String.valueOf(history.size()));

            long earliestTs = history.get(0).getTimestamp();
            long latestTs = history.get(history.size() - 1).getTimestamp();
            long daysSpan = (latestTs - earliestTs) / (1000L * 60 * 60 * 24);
            if (daysSpan <= 0) {
                tvDataSpan.setText("1天");
            } else {
                tvDataSpan.setText(String.format("%d天", daysSpan));
            }
        } catch (Exception e) {
            Log.e("TrendFragment", "calculateHistoryStats failed", e);
        }
    }

    private void showNoDataState() {
        try {
            if (!isAdded()) {
                return;
            }
            if (tvInitialHealth == null || tvCurrentHealth == null || tvTotalDecay == null
                    || tvMonthlyDecay == null || lineChart == null) {
                return;
            }
            tvInitialHealth.setText("--");
            tvCurrentHealth.setText("--");
            tvTotalDecay.setText("--");
            tvMonthlyDecay.setText("--");

            List<Entry> entries = new ArrayList<>();
            LineDataSet dataSet = new LineDataSet(entries, "");
            dataSet.setDrawCircles(false);
            dataSet.setDrawValues(false);
            dataSet.setLineWidth(3f);
            dataSet.setColor(Color.parseColor("#0A84FF"));
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
            lineChart.setNoDataText(getString(R.string.health_check_no_data));
            lineChart.invalidate();
        } catch (Exception e) {
            Log.e("TrendFragment", "showNoDataState failed", e);
        }
    }

    private void postEmptyState() {
        if (isAdded()) {
            showNoDataState();
        }
    }

    private void setupChart(List<BatteryInfo> history) {
        try {
            if (lineChart == null || history == null || history.isEmpty()) {
                return;
            }
            List<Entry> entries = new ArrayList<>();

            if (history.size() == 1) {
                entries.add(new Entry(0f, history.get(0).getHealthPercentage()));
            } else {
                long minTs = history.get(0).getTimestamp();
                long maxTs = history.get(history.size() - 1).getTimestamp();
                long tsRange = maxTs - minTs;

                if (tsRange <= 0) {
                    for (int i = 0; i < history.size(); i++) {
                        entries.add(new Entry(i, history.get(i).getHealthPercentage()));
                    }
                } else {
                    for (BatteryInfo info : history) {
                        float xRatio = (info.getTimestamp() - minTs) / (float) tsRange;
                        entries.add(new Entry(xRatio, info.getHealthPercentage()));
                    }
                }
            }

            LineDataSet dataSet = new LineDataSet(entries, "");
            dataSet.setDrawCircles(true);
            dataSet.setCircleRadius(3f);
            dataSet.setCircleColor(Color.parseColor("#0A84FF"));
            dataSet.setDrawValues(false);
            dataSet.setLineWidth(3f);
            dataSet.setColor(Color.parseColor("#0A84FF"));
            dataSet.setDrawFilled(true);
            dataSet.setFillColor(Color.parseColor("#0A84FF"));
            dataSet.setFillAlpha(72);
            dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

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

            if (history.size() >= 2) {
                long minTs = history.get(0).getTimestamp();
                long maxTs = history.get(history.size() - 1).getTimestamp();
                long tsRange = maxTs - minTs;

                if (tsRange > 0) {
                    xAxis.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                        private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.getDefault());

                        @Override
                        public String getFormattedValue(float value) {
                            long ts = minTs + (long) (value * tsRange);
                            return sdf.format(new Date(ts));
                        }
                    });
                    xAxis.setLabelCount(Math.min(6, history.size()), true);
                } else {
                    xAxis.setLabelCount(Math.min(6, history.size()), true);
                }
            } else {
                xAxis.setLabelCount(1, true);
            }

            lineChart.getAxisLeft().setDrawGridLines(true);
            lineChart.getAxisLeft().setGridColor(Color.parseColor("#E5E5EA"));
            lineChart.getAxisLeft().setTextColor(Color.parseColor("#8A8A8E"));
            lineChart.getAxisLeft().setTextSize(10f);
            lineChart.getAxisLeft().setAxisMinimum(0f);
            lineChart.getAxisLeft().setAxisMaximum(100f);
            lineChart.getAxisRight().setEnabled(false);

            lineChart.invalidate();
        } catch (Exception e) {
            Log.e("TrendFragment", "setupChart failed", e);
        }
    }
}
