package com.batteryhealth.app.ui.trend;

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

    private LineChart lineChart;
    private TextView tvInitialHealth, tvCurrentHealth, tvTotalDecay, tvMonthlyDecay;

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

                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> updateUI(history, currentHealth));
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(this::postEmptyState);
                }
            }
        }).start();
    }

    private float getCurrentRealHealth() {
        if (getActivity() instanceof MainActivity) {
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
        if (history == null || history.isEmpty() || currentHealth < 0) {
            showNoDataState();
            return;
        }

        // 过滤有效健康度数据
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

        // 最早记录的健康度作为初始值
        float initialHealth = validHistory.get(0).getHealthPercentage();
        float latestHealth = validHistory.get(validHistory.size() - 1).getHealthPercentage();

        // 如果当前实时健康度有效，使用实时值作为最新值
        if (currentHealth >= 0) {
            latestHealth = currentHealth;
        }

        float totalDecay = initialHealth - latestHealth;

        // 计算真实月衰减：基于实际时间跨度
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

        setupChart(validHistory);
    }

    private void showNoDataState() {
        tvInitialHealth.setText("--");
        tvCurrentHealth.setText("--");
        tvTotalDecay.setText("--");
        tvMonthlyDecay.setText("--");

        // 无数据时显示提示图表
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
    }

    private void postEmptyState() {
        if (isAdded()) {
            showNoDataState();
        }
    }

    private void setupChart(List<BatteryInfo> history) {
        List<Entry> entries = new ArrayList<>();

        if (history.size() == 1) {
            // 只有一个数据点，显示单点
            entries.add(new Entry(0f, history.get(0).getHealthPercentage()));
        } else {
            // 将时间戳映射为 x 轴索引，保留实际时间间距
            long minTs = history.get(0).getTimestamp();
            long maxTs = history.get(history.size() - 1).getTimestamp();
            long tsRange = maxTs - minTs;

            if (tsRange <= 0) {
                // 所有数据时间戳相同，等距排列
                for (int i = 0; i < history.size(); i++) {
                    entries.add(new Entry(i, history.get(i).getHealthPercentage()));
                }
            } else {
                // 按实际时间比例映射
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

        // 设置 X 轴为日期格式
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
    }
}
