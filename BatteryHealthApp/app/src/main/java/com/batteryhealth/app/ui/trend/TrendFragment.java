package com.batteryhealth.app.ui.trend;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.StateLayoutHelper;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
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
 * 4. 线性回归预测未来健康度
 * 5. 异常事件检测与标注
 * 6. 衰减加速警告
 * 7. 支持7/30/90天时间范围切换
 */
public class TrendFragment extends Fragment {

    private static final String PREFS_TREND = "trend_prefs";
    private static final String PREF_INITIAL_HEALTH = "initial_health";

    private static final int RANGE_7 = 7;
    private static final int RANGE_30 = 30;
    private static final int RANGE_90 = 90;

    // Anomaly thresholds
    private static final float HEALTH_DROP_THRESHOLD = 3.0f; // >3% in a single day
    private static final float TEMP_SPIKE_THRESHOLD = 45.0f; // >45°C
    private static final float VOLTAGE_CHANGE_THRESHOLD = 200.0f; // >200mV rapid change

    // Acceleration threshold: recent monthly decay exceeds earlier by this factor
    private static final float ACCEL_RATIO_THRESHOLD = 1.5f;

    private LineChart chartHealth;
    private LineChart chartTemp;
    private TextView tvInitialHealth, tvCurrentHealth, tvTotalDecay, tvMonthlyDecay;
    private TextView tvDataPoints, tvAvgTemp, tvMaxTemp;
    private TextView tvPredict30, tvPredict60, tvPredict90, tvRegressionInfo;
    private TextView tvAccelWarningDetail;
    private View emptyView;
    private View cardAccelWarning;
    private View cardAnomaly;
    private LinearLayout layoutAnomalyList;
    private TextView btnRange7, btnRange30, btnRange90;

    private int selectedRange = RANGE_30;

    private StateLayoutHelper stateLayoutHelper;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_trend, container, false);
        initViews(view);
        // 初始化 StateLayoutHelper
        if (view instanceof ViewGroup) {
            ViewGroup scrollChild = (ViewGroup) view;
            if (scrollChild.getChildCount() > 0 && scrollChild.getChildAt(0) instanceof ViewGroup) {
                stateLayoutHelper = new StateLayoutHelper((ViewGroup) scrollChild.getChildAt(0));
            }
        }
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

        // Prediction views
        tvPredict30 = view.findViewById(R.id.tv_predict_30);
        tvPredict60 = view.findViewById(R.id.tv_predict_60);
        tvPredict90 = view.findViewById(R.id.tv_predict_90);
        tvRegressionInfo = view.findViewById(R.id.tv_regression_info);

        // Acceleration warning
        cardAccelWarning = view.findViewById(R.id.card_accel_warning);
        tvAccelWarningDetail = view.findViewById(R.id.tv_accel_warning_detail);

        // Anomaly
        cardAnomaly = view.findViewById(R.id.card_anomaly);
        layoutAnomalyList = view.findViewById(R.id.layout_anomaly_list);

        // Range toggle
        btnRange7 = view.findViewById(R.id.btn_range_7);
        btnRange30 = view.findViewById(R.id.btn_range_30);
        btnRange90 = view.findViewById(R.id.btn_range_90);

        setupChart(chartHealth, "健康度 (%)");
        setupChart(chartTemp, "温度 (°C)");

        setupRangeToggle();
    }

    private void setupRangeToggle() {
        View.OnClickListener rangeListener = v -> {
            int id = v.getId();
            if (id == R.id.btn_range_7) {
                selectedRange = RANGE_7;
            } else if (id == R.id.btn_range_30) {
                selectedRange = RANGE_30;
            } else if (id == R.id.btn_range_90) {
                selectedRange = RANGE_90;
            }
            updateRangeToggleUI();
            loadDataAsync();
        };

        if (btnRange7 != null) btnRange7.setOnClickListener(rangeListener);
        if (btnRange30 != null) btnRange30.setOnClickListener(rangeListener);
        if (btnRange90 != null) btnRange90.setOnClickListener(rangeListener);
    }

    private void updateRangeToggleUI() {
        Context ctx = getContext();
        if (ctx == null) return;

        int selectedBg = R.drawable.bg_range_button_selected;
        int normalBg = R.drawable.bg_range_button;
        int selectedColor = ctx.getColor(R.color.coloros_blue);
        int normalColor = ctx.getColor(R.color.label_3);

        updateRangeButton(btnRange7, selectedRange == RANGE_7, selectedBg, normalBg, selectedColor, normalColor);
        updateRangeButton(btnRange30, selectedRange == RANGE_30, selectedBg, normalBg, selectedColor, normalColor);
        updateRangeButton(btnRange90, selectedRange == RANGE_90, selectedBg, normalBg, selectedColor, normalColor);
    }

    private void updateRangeButton(TextView btn, boolean isSelected, int selectedBg, int normalBg, int selectedColor, int normalColor) {
        if (btn == null) return;
        btn.setBackgroundResource(isSelected ? selectedBg : normalBg);
        btn.setTextColor(isSelected ? selectedColor : normalColor);
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
        if (stateLayoutHelper != null) {
            stateLayoutHelper.showLoading(null);
        }
        executor.execute(() -> {
            try {
                AppDatabase db = com.batteryhealth.app.BatteryHealthApplication.getDatabase();
                if (db == null) {
                    mainHandler.post(() -> { if (isAdded()) showEmptyState(); });
                    return;
                }
                long since = System.currentTimeMillis() - (long) selectedRange * 24 * 60 * 60 * 1000;
                List<BatteryInfo> records = db.batteryInfoDao().getSince(since);

                if (records == null || records.isEmpty()) {
                    mainHandler.post(() -> { if (isAdded()) showEmptyState(); });
                    return;
                }

                // 健康度数据
                List<Entry> healthEntries = new ArrayList<>();
                List<Entry> tempEntries = new ArrayList<>();
                float sumHealth = 0, sumTemp = 0, maxTemp = -100;
                int healthCount = 0, tempCount = 0;
                int firstHealth = -1, lastHealth = -1;

                // For anomaly detection: track previous values
                List<BatteryInfo> healthRecords = new ArrayList<>();
                List<BatteryInfo> allRecords = new ArrayList<>();

                for (BatteryInfo info : records) {
                    long ts = info.getTimestamp();
                    allRecords.add(info);
                    if (info.hasValidHealthData()) {
                        float h = info.getHealthPercentage();
                        healthEntries.add(new Entry(ts, h));
                        sumHealth += h;
                        healthCount++;
                        healthRecords.add(info);
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
                long daysSpan = records.size() >= 2
                        ? Math.max(1, (records.get(records.size() - 1).getTimestamp() - records.get(0).getTimestamp()) / (24L * 60 * 60 * 1000))
                        : 1;
                float monthlyDecay = daysSpan > 0 ? (totalDecay / (float) daysSpan) * 30f : 0;

                // --- Linear Regression ---
                float[] regression = calculateLinearRegression(healthEntries);
                final float slope = regression[0];
                final float intercept = regression[1];
                final float rSquared = regression[2];

                // Predictions
                final float predict30, predict60, predict90;
                if (healthEntries.size() >= 2 && !Float.isNaN(slope)) {
                    long lastTs = healthEntries.get(healthEntries.size() - 1).getX() != 0
                            ? (long) healthEntries.get(healthEntries.size() - 1).getX()
                            : System.currentTimeMillis();
                    long msPerDay = 24L * 60 * 60 * 1000;
                    predict30 = slope * (lastTs + 30L * msPerDay) + intercept;
                    predict60 = slope * (lastTs + 60L * msPerDay) + intercept;
                    predict90 = slope * (lastTs + 90L * msPerDay) + intercept;
                } else {
                    predict30 = predict60 = predict90 = Float.NaN;
                }

                // --- Anomaly Detection ---
                List<AnomalyEvent> anomalies = detectAnomalies(allRecords);

                // --- Acceleration Warning ---
                // Compare monthly decay rates in first half vs second half of data
                final float accelRecentDecay;
                final float accelEarlierDecay;
                final boolean isAccelerating;
                if (healthRecords.size() >= 4) {
                    int mid = healthRecords.size() / 2;
                    float firstHalfHealth = healthRecords.get(0).getHealthPercentage();
                    float midHealth = healthRecords.get(mid).getHealthPercentage();
                    float lastH = healthRecords.get(healthRecords.size() - 1).getHealthPercentage();

                    long firstTs = healthRecords.get(0).getTimestamp();
                    long midTs = healthRecords.get(mid).getTimestamp();
                    long lastTsH = healthRecords.get(healthRecords.size() - 1).getTimestamp();

                    long firstHalfDays = Math.max(1, (midTs - firstTs) / (24L * 60 * 60 * 1000));
                    long secondHalfDays = Math.max(1, (lastTsH - midTs) / (24L * 60 * 60 * 1000));

                    accelEarlierDecay = Math.abs((firstHalfHealth - midHealth) / firstHalfDays * 30f);
                    accelRecentDecay = Math.abs((midHealth - lastH) / secondHalfDays * 30f);
                    isAccelerating = accelRecentDecay > 0 && accelEarlierDecay > 0
                            && accelRecentDecay > accelEarlierDecay * ACCEL_RATIO_THRESHOLD;
                } else {
                    accelEarlierDecay = 0;
                    accelRecentDecay = 0;
                    isAccelerating = false;
                }

                final int finalFirstHealth = firstHealth;
                final int finalLastHealth = lastHealth;
                final int finalTotalDecay = totalDecay;
                final float finalMonthlyDecay = monthlyDecay;
                final int finalRecordCount = records.size();
                final float finalAvgTemp = avgTemp;
                final float finalMaxTemp = maxTemp;
                final List<Entry> finalHealthEntries = healthEntries;
                final List<Entry> finalTempEntries = tempEntries;
                final List<AnomalyEvent> finalAnomalies = anomalies;

                mainHandler.post(() -> {
                    if (isAdded()) {
                        if (stateLayoutHelper != null) stateLayoutHelper.showContent();
                        bindData(finalHealthEntries, finalTempEntries, finalFirstHealth, finalLastHealth, finalTotalDecay, finalMonthlyDecay,
                                finalRecordCount, finalAvgTemp, finalMaxTemp);
                        bindPrediction(predict30, predict60, predict90, slope, rSquared);
                        bindAnomalies(finalAnomalies);
                        bindAccelerationWarning(isAccelerating, accelRecentDecay, accelEarlierDecay);
                    }
                });

            } catch (Exception e) {
                mainHandler.post(() -> { if (isAdded()) showErrorState(); });
            }
        });
    }

    /**
     * Calculate linear regression using least-squares method.
     * x values are timestamps (ms), y values are health percentages.
     * Returns [slope, intercept, rSquared].
     * slope = (n*Σxy - Σx*Σy) / (n*Σx² - (Σx)²)
     * intercept = (Σy - slope*Σx) / n
     */
    private float[] calculateLinearRegression(List<Entry> entries) {
        int n = entries.size();
        if (n < 2) return new float[]{Float.NaN, Float.NaN, Float.NaN};

        // Normalize x to days from first entry to avoid numerical overflow
        double x0 = entries.get(0).getX();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        double msPerDay = 24.0 * 60 * 60 * 1000;

        for (Entry e : entries) {
            double x = (e.getX() - x0) / msPerDay; // days from first entry
            double y = e.getY();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
            sumY2 += y * y;
        }

        double denominator = n * sumX2 - sumX * sumX;
        if (denominator == 0) return new float[]{Float.NaN, Float.NaN, Float.NaN};

        // slope in %/day (normalized)
        double slopeDays = (n * sumXY - sumX * sumY) / denominator;
        double interceptDays = (sumY - slopeDays * sumX) / n;

        // Convert slope back to %/ms for chart prediction (x is in ms)
        double slopeMs = slopeDays / msPerDay;
        // intercept needs adjustment: y = slopeMs * x + intercept
        // y = slopeDays * ((x - x0)/msPerDay) + interceptDays
        // y = (slopeDays/msPerDay) * x - (slopeDays/msPerDay) * x0 + interceptDays
        double interceptMs = interceptDays - (slopeDays / msPerDay) * x0;

        // R² calculation
        double yMean = sumY / n;
        double ssTot = 0, ssRes = 0;
        for (Entry e : entries) {
            double x = (e.getX() - x0) / msPerDay;
            double y = e.getY();
            double yPred = slopeDays * x + interceptDays;
            ssTot += (y - yMean) * (y - yMean);
            ssRes += (y - yPred) * (y - yPred);
        }
        double rSquared = ssTot > 0 ? 1.0 - ssRes / ssTot : 0;

        return new float[]{(float) slopeMs, (float) interceptMs, (float) rSquared};
    }

    /**
     * Detect anomaly events from battery records.
     * - Sudden health drop (>3% in a single day)
     * - Temperature spike (>45°C)
     * - Rapid voltage change (>200mV between consecutive readings)
     */
    private List<AnomalyEvent> detectAnomalies(List<BatteryInfo> records) {
        List<AnomalyEvent> anomalies = new ArrayList<>();
        if (records.size() < 2) return anomalies;

        long msPerDay = 24L * 60 * 60 * 1000;

        // Group by day for health drop detection
        BatteryInfo prev = null;
        for (BatteryInfo info : records) {
            if (prev != null) {
                // Health drop: compare with previous record within 1 day
                if (info.hasValidHealthData() && prev.hasValidHealthData()) {
                    float drop = prev.getHealthPercentage() - info.getHealthPercentage();
                    long timeDiff = info.getTimestamp() - prev.getTimestamp();
                    if (drop > HEALTH_DROP_THRESHOLD && timeDiff <= msPerDay) {
                        anomalies.add(new AnomalyEvent(
                                AnomalyEvent.TYPE_HEALTH_DROP,
                                info.getTimestamp(),
                                drop
                        ));
                    }
                }

                // Voltage rapid change
                float vDiff = Math.abs(info.getVoltage() - prev.getVoltage());
                if (vDiff > VOLTAGE_CHANGE_THRESHOLD && info.getVoltage() > 0 && prev.getVoltage() > 0) {
                    long timeDiff = info.getTimestamp() - prev.getTimestamp();
                    if (timeDiff <= msPerDay) {
                        anomalies.add(new AnomalyEvent(
                                AnomalyEvent.TYPE_VOLTAGE_CHANGE,
                                info.getTimestamp(),
                                vDiff
                        ));
                    }
                }
            }

            // Temperature spike (absolute threshold)
            if (info.getTemperature() > TEMP_SPIKE_THRESHOLD) {
                anomalies.add(new AnomalyEvent(
                        AnomalyEvent.TYPE_TEMP_SPIKE,
                        info.getTimestamp(),
                        info.getTemperature()
                ));
            }

            prev = info;
        }

        return anomalies;
    }

    private void bindData(List<Entry> healthEntries, List<Entry> tempEntries,
                          int firstHealth, int lastHealth, int totalDecay, float monthlyDecay,
                          int dataPoints, float avgTemp, float maxTemp) {
        if (!isAdded() || getContext() == null) return;

        safeSetText(tvInitialHealth, firstHealth > 0 ? firstHealth + "%" : "--");
        safeSetText(tvCurrentHealth, lastHealth > 0 ? lastHealth + "%" : "--");
        safeSetText(tvTotalDecay, totalDecay >= 0 ? "-" + totalDecay + "%" : "--");
        safeSetText(tvMonthlyDecay, monthlyDecay > 0 ? String.format(Locale.getDefault(), "-%.1f%%", monthlyDecay) : "--");
        safeSetText(tvDataPoints, getString(R.string.trend_data_points, dataPoints));
        safeSetText(tvAvgTemp, String.format(Locale.getDefault(), "%.1f°C", avgTemp));
        safeSetText(tvMaxTemp, String.format(Locale.getDefault(), "%.1f°C", maxTemp));

        showLineData(chartHealth, healthEntries, getColor(R.color.coloros_green), 0, 100);
        showLineData(chartTemp, tempEntries, getColor(R.color.coloros_orange), 0, 60);
    }

    private void bindPrediction(float predict30, float predict60, float predict90, float slope, float rSquared) {
        if (!isAdded() || getContext() == null) return;

        if (Float.isNaN(predict30)) {
            safeSetText(tvPredict30, "--");
            safeSetText(tvPredict60, "--");
            safeSetText(tvPredict90, "--");
            safeSetText(tvRegressionInfo, getString(R.string.prediction_no_data));
            return;
        }

        // Clamp predictions to reasonable range [0, 100]
        float p30 = Math.max(0, Math.min(100, predict30));
        float p60 = Math.max(0, Math.min(100, predict60));
        float p90 = Math.max(0, Math.min(100, predict90));

        safeSetText(tvPredict30, String.format(Locale.getDefault(), "%.1f%%", p30));
        safeSetText(tvPredict60, String.format(Locale.getDefault(), "%.1f%%", p60));
        safeSetText(tvPredict90, String.format(Locale.getDefault(), "%.1f%%", p90));

        // Color the prediction values based on severity
        Context ctx = getContext();
        if (ctx != null) {
            tvPredict30.setTextColor(getPredictionColor(p30));
            tvPredict60.setTextColor(getPredictionColor(p60));
            tvPredict90.setTextColor(getPredictionColor(p90));
        }

        // Show regression info
        if (!Float.isNaN(slope) && !Float.isNaN(rSquared)) {
            // Convert slope from %/ms to %/day for display
            double msPerDay = 24.0 * 60 * 60 * 1000;
            double slopePerDay = slope * msPerDay;
            safeSetText(tvRegressionInfo, String.format(Locale.getDefault(),
                    getString(R.string.regression_info_format), slopePerDay, rSquared));
        }

        // Add regression line to health chart
        addRegressionLineToChart(slope);
    }

    private int getPredictionColor(float value) {
        Context ctx = getContext();
        if (ctx == null) return getColor(R.color.label);
        if (value >= 85) return ctx.getColor(R.color.coloros_green);
        if (value >= 70) return ctx.getColor(R.color.coloros_orange);
        return ctx.getColor(R.color.coloros_red);
    }

    private void addRegressionLineToChart(float slope) {
        if (chartHealth == null || chartHealth.getData() == null) return;
        LineData lineData = chartHealth.getData();

        if (lineData.getDataSetCount() > 1) {
            // Already has regression line, remove it
            lineData.removeDataSet(1);
        }

        LineDataSet originalSet = (LineDataSet) lineData.getDataSetByIndex(0);
        if (originalSet == null || originalSet.getEntryCount() < 2) return;

        float x0 = originalSet.getEntryForIndex(0).getX();
        float x1 = originalSet.getEntryForIndex(originalSet.getEntryCount() - 1).getX();

        // Extend regression line 30 days into the future
        long ms30Days = 30L * 24 * 60 * 60 * 1000;
        float xEnd = x1 + ms30Days;

        float y0 = slope * x0 + (lineData.getDataSetCount() > 1 ? 0 : 0);
        // Recalculate intercept from the regression
        // We need the intercept from the regression calculation
        // Since we only have slope here, use the actual data to derive intercept
        // Use midpoint method: find y at mean x
        float sumX = 0, sumY = 0;
        int n = originalSet.getEntryCount();
        for (int i = 0; i < n; i++) {
            Entry e = originalSet.getEntryForIndex(i);
            sumX += e.getX();
            sumY += e.getY();
        }
        float meanX = sumX / n;
        float meanY = sumY / n;
        float intercept = meanY - slope * meanX;

        float yStart = slope * x0 + intercept;
        float yEnd = slope * xEnd + intercept;

        List<Entry> regressionEntries = new ArrayList<>();
        regressionEntries.add(new Entry(x0, yStart));
        regressionEntries.add(new Entry(xEnd, yEnd));

        LineDataSet regressionSet = new LineDataSet(regressionEntries, "回归预测");
        regressionSet.setColor(getColor(R.color.coloros_blue));
        regressionSet.setLineWidth(1.5f);
        regressionSet.setDrawCircles(false);
        regressionSet.setDrawValues(false);
        regressionSet.enableDashedLine(10f, 6f, 0f);
        regressionSet.setMode(LineDataSet.Mode.LINEAR);
        regressionSet.setDrawFilled(false);

        lineData.addDataSet(regressionSet);
        chartHealth.invalidate();
    }

    private void bindAnomalies(List<AnomalyEvent> anomalies) {
        if (!isAdded() || getContext() == null) return;

        // Clear previous anomaly items (keep the header at index 0)
        if (layoutAnomalyList != null && layoutAnomalyList.getChildCount() > 1) {
            layoutAnomalyList.removeViews(1, layoutAnomalyList.getChildCount() - 1);
        }

        if (anomalies.isEmpty()) {
            if (cardAnomaly != null) cardAnomaly.setVisibility(View.GONE);
            return;
        }

        if (cardAnomaly != null) cardAnomaly.setVisibility(View.VISIBLE);

        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());
        Context ctx = getContext();
        if (ctx == null) return;

        for (AnomalyEvent event : anomalies) {
            LinearLayout itemLayout = new LinearLayout(ctx);
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setGravity(Gravity.CENTER_VERTICAL);
            int padV = (int) (8 * ctx.getResources().getDisplayMetrics().density);
            int padH = 0;
            itemLayout.setPadding(padH, padV, padH, padV);

            // Color dot
            View dot = new View(ctx);
            int dotSize = (int) (8 * ctx.getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotParams.setMarginEnd((int) (12 * ctx.getResources().getDisplayMetrics().density));
            dot.setLayoutParams(dotParams);
            int dotColor;
            switch (event.type) {
                case AnomalyEvent.TYPE_HEALTH_DROP:
                    dotColor = ctx.getColor(R.color.coloros_red);
                    break;
                case AnomalyEvent.TYPE_TEMP_SPIKE:
                    dotColor = ctx.getColor(R.color.coloros_orange);
                    break;
                case AnomalyEvent.TYPE_VOLTAGE_CHANGE:
                    dotColor = ctx.getColor(R.color.coloros_purple);
                    break;
                default:
                    dotColor = ctx.getColor(R.color.label_3);
                    break;
            }
            dot.setBackgroundColor(dotColor);

            // Text container
            LinearLayout textContainer = new LinearLayout(ctx);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            textContainer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            // Description
            TextView tvDesc = new TextView(ctx);
            tvDesc.setTextSize(14);
            tvDesc.setTextColor(ctx.getColor(R.color.label));
            String desc;
            switch (event.type) {
                case AnomalyEvent.TYPE_HEALTH_DROP:
                    desc = getString(R.string.anomaly_health_drop, event.value);
                    break;
                case AnomalyEvent.TYPE_TEMP_SPIKE:
                    desc = getString(R.string.anomaly_temp_spike, event.value);
                    break;
                case AnomalyEvent.TYPE_VOLTAGE_CHANGE:
                    desc = getString(R.string.anomaly_voltage_change, event.value);
                    break;
                default:
                    desc = "未知异常";
                    break;
            }
            tvDesc.setText(desc);

            // Timestamp
            TextView tvTime = new TextView(ctx);
            tvTime.setTextSize(11);
            tvTime.setTextColor(ctx.getColor(R.color.label_3));
            tvTime.setText(sdf.format(new Date(event.timestamp)));

            textContainer.addView(tvDesc);
            textContainer.addView(tvTime);

            itemLayout.addView(dot);
            itemLayout.addView(textContainer);

            // Add separator before (except first)
            if (layoutAnomalyList.getChildCount() > 1) {
                View separator = new View(ctx);
                LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (int) (0.5 * ctx.getResources().getDisplayMetrics().density));
                separator.setLayoutParams(sepParams);
                separator.setBackgroundColor(ctx.getColor(R.color.separator));
                layoutAnomalyList.addView(separator);
            }

            layoutAnomalyList.addView(itemLayout);
        }

        // Add anomaly markers to health chart
        addAnomalyMarkersToChart(anomalies);
    }

    private void addAnomalyMarkersToChart(List<AnomalyEvent> anomalies) {
        if (chartHealth == null || chartHealth.getData() == null) return;
        LineData lineData = chartHealth.getData();
        LineDataSet originalSet = (LineDataSet) lineData.getDataSetByIndex(0);
        if (originalSet == null) return;

        // Add LimitLines for health drop anomalies
        for (AnomalyEvent event : anomalies) {
            if (event.type == AnomalyEvent.TYPE_HEALTH_DROP) {
                LimitLine ll = new LimitLine(event.timestamp, "");
                ll.setLineColor(getColor(R.color.coloros_red));
                ll.setLineWidth(1f);
                ll.enableDashedLine(4f, 4f, 0f);
                ll.setLabelPosition(LimitLine.LimitLabelPosition.LEFT_TOP);
                chartHealth.getAxisLeft().addLimitLine(ll);
            }
        }
        chartHealth.invalidate();
    }

    private void bindAccelerationWarning(boolean isAccelerating, float recentDecay, float earlierDecay) {
        if (!isAdded() || getContext() == null) return;

        if (cardAccelWarning != null) {
            cardAccelWarning.setVisibility(isAccelerating ? View.VISIBLE : View.GONE);
        }

        if (isAccelerating && tvAccelWarningDetail != null) {
            float diff = recentDecay - earlierDecay;
            safeSetText(tvAccelWarningDetail, String.format(Locale.getDefault(),
                    getString(R.string.accel_warning_detail_format), recentDecay, earlierDecay, diff));
        }
    }

    private void safeSetText(TextView tv, String text) {
        if (tv != null) tv.setText(text);
    }

    private void showLineData(LineChart chart, List<Entry> entries, int color, float min, float max) {
        if (chart == null) return;
        if (entries == null || entries.isEmpty()) {
            chart.setVisibility(View.GONE);
            return;
        }
        chart.setVisibility(View.VISIBLE);

        // Clear previous limit lines
        chart.getAxisLeft().removeAllLimitLines();

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

    private void showEmptyState() {
        if (!isAdded() || getContext() == null) return;
        if (stateLayoutHelper != null) {
            stateLayoutHelper.showEmpty("暂无历史数据，使用应用一段时间后即可查看趋势",
                    R.drawable.ic_trend, null, null);
        }
        if (chartHealth != null) chartHealth.setVisibility(View.GONE);
        if (chartTemp != null) chartTemp.setVisibility(View.GONE);
    }

    private void showErrorState() {
        if (!isAdded() || getContext() == null) return;
        if (stateLayoutHelper != null) {
            stateLayoutHelper.showError("数据加载失败", v -> loadDataAsync());
        }
    }

    private int getColor(int resId) {
        Context ctx = getContext();
        return ctx != null ? ctx.getColor(resId) : 0;
    }

    private void animateEntry(View view) {
        Context ctx = getContext();
        if (ctx == null) return;
        Animation fadeUp = AnimationUtils.loadAnimation(ctx, R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    /**
     * Internal data class for anomaly events
     */
    private static class AnomalyEvent {
        static final int TYPE_HEALTH_DROP = 1;
        static final int TYPE_TEMP_SPIKE = 2;
        static final int TYPE_VOLTAGE_CHANGE = 3;

        final int type;
        final long timestamp;
        final float value; // magnitude of the anomaly

        AnomalyEvent(int type, long timestamp, float value) {
            this.type = type;
            this.timestamp = timestamp;
            this.value = value;
        }
    }
}
