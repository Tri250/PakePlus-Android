package com.batteryhealth.app.ui.power;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.domain.usecase.ChargingEfficiencyUseCase;
import com.batteryhealth.app.utils.FragmentErrorViewHelper;
import com.batteryhealth.app.utils.ThreadExecutor;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 充电历史页面 — 按日展示充电记录，支持按日/周/月统计。
 */
public class ChargingHistoryFragment extends Fragment {

    private static final String TAG = "ChargingHistoryFragment";

    private LinearLayout containerHistory;
    private View containerEfficiency;
    private TextView tvPeriodLabel;
    private TextView tvTotalSessions;
    private TextView tvTotalEnergy;
    private TextView tvAvgPower;
    private TextView tvEfficiencyScore;
    private TextView tvEfficiencyGrade;
    private TextView tvEfficiencyComment;
    private TextView tvEfficiencyAvg;
    private HorizontalBarChart phaseBarChart;
    private BarChart efficiencyTrendChart;
    private View btnTabHistory;
    private View btnTabEfficiency;

    private int periodMode = 0;
    private int tabMode = 0;

    private ChargingEfficiencyUseCase efficiencyUseCase;
    private ChargingEfficiencyUseCase.Result efficiencyResult;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.fragment_charging_history, container, false);
            initViews(view);
            animateEntry(view);
            loadData();
            return view;
        } catch (Exception e) {
            Log.e(TAG, "Error creating view", e);
            Context ctx = getContext();
            if (ctx == null && container != null) ctx = container.getContext();
            return FragmentErrorViewHelper.createErrorView(ctx, e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 清理 Handler 待执行回调，避免内存泄漏
        handler.removeCallbacksAndMessages(null);
    }

    private void initViews(View view) {
        containerHistory = view.findViewById(R.id.container_history);
        tvPeriodLabel = view.findViewById(R.id.tv_period_label);
        tvTotalSessions = view.findViewById(R.id.tv_total_sessions);
        tvTotalEnergy = view.findViewById(R.id.tv_total_energy);
        tvAvgPower = view.findViewById(R.id.tv_avg_power);

        View btnDay = view.findViewById(R.id.btn_day);
        View btnWeek = view.findViewById(R.id.btn_week);
        View btnMonth = view.findViewById(R.id.btn_month);

        if (btnDay != null) btnDay.setOnClickListener(v -> { periodMode = 0; loadData(); });
        if (btnWeek != null) btnWeek.setOnClickListener(v -> { periodMode = 1; loadData(); });
        if (btnMonth != null) btnMonth.setOnClickListener(v -> { periodMode = 2; loadData(); });

        efficiencyUseCase = new ChargingEfficiencyUseCase();
        buildEfficiencyTabUI(view);
        buildTabSwitcherUI(view);
    }

    private void buildTabSwitcherUI(View root) {
        Context ctx = getContext();
        if (ctx == null) return;

        LinearLayout tabBar = new LinearLayout(ctx);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setGravity(android.view.Gravity.CENTER);
        tabBar.setPadding(0, 0, 0, dpToPx(12));

        btnTabHistory = new TextView(ctx);
        ((TextView) btnTabHistory).setText("充电记录");
        ((TextView) btnTabHistory).setTextSize(14);
        ((TextView) btnTabHistory).setGravity(android.view.Gravity.CENTER);
        ((TextView) btnTabHistory).setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        ((TextView) btnTabHistory).setTextColor(ContextCompat.getColor(ctx, R.color.primary));
        btnTabHistory.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        hp.setMargins(0, 0, dpToPx(6), 0);
        btnTabHistory.setLayoutParams(hp);

        btnTabEfficiency = new TextView(ctx);
        ((TextView) btnTabEfficiency).setText("充电效率");
        ((TextView) btnTabEfficiency).setTextSize(14);
        ((TextView) btnTabEfficiency).setGravity(android.view.Gravity.CENTER);
        ((TextView) btnTabEfficiency).setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        ((TextView) btnTabEfficiency).setTextColor(ContextCompat.getColor(ctx, R.color.label_2));
        btnTabEfficiency.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        ep.setMargins(dpToPx(6), 0, 0, 0);
        btnTabEfficiency.setLayoutParams(ep);

        tabBar.addView(btnTabHistory);
        tabBar.addView(btnTabEfficiency);

        btnTabHistory.setOnClickListener(v -> switchTab(0));
        btnTabEfficiency.setOnClickListener(v -> switchTab(1));

        ViewGroup parent = (ViewGroup) containerHistory.getParent().getParent();
        if (parent != null) {
            int idx = parent.indexOfChild((View) containerHistory.getParent());
            parent.addView(tabBar, idx);
        }
    }

    private void buildEfficiencyTabUI(View root) {
        Context ctx = getContext();
        if (ctx == null) return;

        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setVisibility(View.GONE);
        container.setBackgroundResource(R.drawable.bg_card);
        container.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        containerEfficiency = container;

        tvEfficiencyScore = new TextView(ctx);
        tvEfficiencyScore.setText("--");
        tvEfficiencyScore.setTextSize(36);
        tvEfficiencyScore.setTypeface(null, android.graphics.Typeface.BOLD);
        tvEfficiencyScore.setTextColor(ContextCompat.getColor(ctx, R.color.label));
        tvEfficiencyScore.setGravity(android.view.Gravity.CENTER);
        container.addView(tvEfficiencyScore);

        tvEfficiencyGrade = new TextView(ctx);
        tvEfficiencyGrade.setText("效率等级");
        tvEfficiencyGrade.setTextSize(14);
        tvEfficiencyGrade.setTextColor(ContextCompat.getColor(ctx, R.color.label_2));
        tvEfficiencyGrade.setGravity(android.view.Gravity.CENTER);
        tvEfficiencyGrade.setPadding(0, dpToPx(4), 0, dpToPx(12));
        container.addView(tvEfficiencyGrade);

        View div1 = new View(ctx);
        div1.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        div1.setBackgroundColor(ContextCompat.getColor(ctx, R.color.separator));
        container.addView(div1);

        tvEfficiencyAvg = new TextView(ctx);
        tvEfficiencyAvg.setText("平均效率：--");
        tvEfficiencyAvg.setTextSize(13);
        tvEfficiencyAvg.setTextColor(ContextCompat.getColor(ctx, R.color.label));
        tvEfficiencyAvg.setPadding(0, dpToPx(12), 0, dpToPx(4));
        container.addView(tvEfficiencyAvg);

        tvEfficiencyComment = new TextView(ctx);
        tvEfficiencyComment.setText("--");
        tvEfficiencyComment.setTextSize(12);
        tvEfficiencyComment.setTextColor(ContextCompat.getColor(ctx, R.color.label_3));
        tvEfficiencyComment.setPadding(0, 0, 0, dpToPx(12));
        container.addView(tvEfficiencyComment);

        TextView phaseLabel = new TextView(ctx);
        phaseLabel.setText("充电阶段占比");
        phaseLabel.setTextSize(13);
        phaseLabel.setTextColor(ContextCompat.getColor(ctx, R.color.label));
        phaseLabel.setPadding(0, dpToPx(8), 0, dpToPx(8));
        container.addView(phaseLabel);

        phaseBarChart = new HorizontalBarChart(ctx);
        phaseBarChart.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(100)));
        container.addView(phaseBarChart);

        TextView trendLabel = new TextView(ctx);
        trendLabel.setText("最近5次效率趋势");
        trendLabel.setTextSize(13);
        trendLabel.setTextColor(ContextCompat.getColor(ctx, R.color.label));
        trendLabel.setPadding(0, dpToPx(12), 0, dpToPx(8));
        container.addView(trendLabel);

        efficiencyTrendChart = new BarChart(ctx);
        efficiencyTrendChart.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(140)));
        container.addView(efficiencyTrendChart);

        ViewGroup parent = (ViewGroup) containerHistory.getParent();
        if (parent != null) {
            parent.addView(container);
        }

        setupPhaseBarChart();
        setupEfficiencyTrendChart();
    }

    private void switchTab(int tab) {
        tabMode = tab;
        Context ctx = getContext();
        if (containerHistory != null) {
            containerHistory.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        }
        if (containerEfficiency != null) {
            containerEfficiency.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        }
        if (ctx != null) {
            int primaryColor = ContextCompat.getColor(ctx, R.color.primary);
            int secondaryColor = ContextCompat.getColor(ctx, R.color.label_2);
            if (btnTabHistory instanceof TextView) {
                ((TextView) btnTabHistory).setTextColor(tab == 0 ? primaryColor : secondaryColor);
            }
            if (btnTabEfficiency instanceof TextView) {
                ((TextView) btnTabEfficiency).setTextColor(tab == 1 ? primaryColor : secondaryColor);
            }
        }
    }

    private void setupPhaseBarChart() {
        phaseBarChart.getDescription().setEnabled(false);
        phaseBarChart.getLegend().setEnabled(false);
        phaseBarChart.setTouchEnabled(false);
        phaseBarChart.setDrawBarShadow(false);
        phaseBarChart.setDrawValueAboveBar(true);
        phaseBarChart.setFitBars(true);

        XAxis xAxis = phaseBarChart.getXAxis();
        xAxis.setEnabled(false);

        YAxis leftAxis = phaseBarChart.getAxisLeft();
        leftAxis.setEnabled(false);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(100f);

        YAxis rightAxis = phaseBarChart.getAxisRight();
        rightAxis.setEnabled(false);
    }

    private void setupEfficiencyTrendChart() {
        efficiencyTrendChart.getDescription().setEnabled(false);
        efficiencyTrendChart.getLegend().setEnabled(false);
        efficiencyTrendChart.setTouchEnabled(false);
        efficiencyTrendChart.setDrawBarShadow(false);
        efficiencyTrendChart.setDrawValueAboveBar(true);

        XAxis xAxis = efficiencyTrendChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#8A8A8E"));
        xAxis.setTextSize(9f);

        YAxis leftAxis = efficiencyTrendChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#E5E5EA"));
        leftAxis.setTextColor(Color.parseColor("#8A8A8E"));
        leftAxis.setTextSize(9f);
        leftAxis.setAxisMinimum(60f);
        leftAxis.setAxisMaximum(100f);

        YAxis rightAxis = efficiencyTrendChart.getAxisRight();
        rightAxis.setEnabled(false);
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    private void loadData() {
        final BatteryHealthApplication app = (BatteryHealthApplication) requireActivity().getApplication();
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (app == null) return;
                    com.batteryhealth.app.data.database.AppDatabase db = app.getDatabase();
                    if (db == null) return;

                    long startTime = getPeriodStartTime();
                    List<PowerHistory> records = db.powerHistoryDao().getSince(startTime);
                    if (records == null) records = new ArrayList<>();

                    Map<String, List<PowerHistory>> sessionMap = new LinkedHashMap<>();
                    for (PowerHistory r : records) {
                        String sid = r.getSessionId();
                        if (sid == null || sid.isEmpty()) sid = "unknown_" + r.getTimestamp();
                        if (!sessionMap.containsKey(sid)) {
                            sessionMap.put(sid, new ArrayList<>());
                        }
                        sessionMap.get(sid).add(r);
                    }

                    final int totalSessions = sessionMap.size();
                    float totalEnergyWh = 0;
                    float totalPowerSum = 0;
                    int powerCount = 0;

                    List<ChargingSessionSummary> summaries = new ArrayList<>();
                    for (Map.Entry<String, List<PowerHistory>> entry : sessionMap.entrySet()) {
                        List<PowerHistory> sessionRecords = entry.getValue();
                        if (sessionRecords.isEmpty()) continue;

                        ChargingSessionSummary summary = new ChargingSessionSummary();
                        summary.sessionId = entry.getKey();
                        summary.startTime = sessionRecords.get(0).getTimestamp();
                        summary.endTime = sessionRecords.get(sessionRecords.size() - 1).getTimestamp();
                        summary.duration = summary.endTime - summary.startTime;
                        summary.startLevel = sessionRecords.get(0).getBatteryLevel();
                        summary.endLevel = sessionRecords.get(sessionRecords.size() - 1).getBatteryLevel();
                        summary.maxPower = 0;
                        float powerSum = 0;
                        for (PowerHistory r : sessionRecords) {
                            if (r.getPower() > summary.maxPower) summary.maxPower = r.getPower();
                            powerSum += r.getPower();
                        }
                        summary.avgPower = sessionRecords.size() > 0 ? powerSum / sessionRecords.size() : 0;
                        summary.chargeType = sessionRecords.get(sessionRecords.size() - 1).getChargeType();
                        summary.phase = sessionRecords.get(sessionRecords.size() - 1).getChargingPhase();

                        totalEnergyWh += summary.avgPower * (summary.duration / (1000f * 60 * 60));
                        totalPowerSum += powerSum;
                        powerCount += sessionRecords.size();

                        summaries.add(summary);
                    }

                    Collections.sort(summaries, new Comparator<ChargingSessionSummary>() {
                        @Override
                        public int compare(ChargingSessionSummary a, ChargingSessionSummary b) {
                            return Long.compare(b.startTime, a.startTime);
                        }
                    });

                    final ChargingEfficiencyUseCase.Result effResult = efficiencyUseCase.execute(records, 4000f);
                    efficiencyResult = effResult;

                    final float finalTotalEnergyWh = totalEnergyWh;
                    final float finalAvgPower = powerCount > 0 ? totalPowerSum / powerCount : 0;
                    final List<ChargingSessionSummary> finalSummaries = summaries;

                    if (isAdded()) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded()) return;
                                tvTotalSessions.setText(String.format(Locale.getDefault(), "%d 次", totalSessions));
                                tvTotalEnergy.setText(String.format(Locale.getDefault(), "%.1f Wh", finalTotalEnergyWh));
                                tvAvgPower.setText(String.format(Locale.getDefault(), "%.1f W", finalAvgPower));
                                tvPeriodLabel.setText(getPeriodLabel());

                                renderHistoryList(finalSummaries);
                                renderEfficiencyUI(effResult);
                            }
                        });
                    }
                } catch (Exception e) {
                    android.util.Log.e(TAG, "loadData error", e);
                }
            }
        });
    }

    private void renderEfficiencyUI(ChargingEfficiencyUseCase.Result result) {
        if (result == null || containerEfficiency == null) return;

        if (!result.hasData) {
            if (tvEfficiencyScore != null) tvEfficiencyScore.setText("--");
            if (tvEfficiencyGrade != null) tvEfficiencyGrade.setText("--");
            if (tvEfficiencyComment != null) tvEfficiencyComment.setText("暂无充电效率数据");
            if (tvEfficiencyAvg != null) tvEfficiencyAvg.setText("--");
            return;
        }

        if (tvEfficiencyScore != null) {
            tvEfficiencyScore.setText(String.valueOf(result.efficiencyScore));
            int scoreColor = getScoreColor(result.efficiencyScore);
            tvEfficiencyScore.setTextColor(scoreColor);
        }
        if (tvEfficiencyGrade != null) {
            tvEfficiencyGrade.setText("等级 " + result.efficiencyGrade);
        }
        if (tvEfficiencyComment != null) {
            tvEfficiencyComment.setText(result.efficiencyComment);
        }
        if (tvEfficiencyAvg != null) {
            tvEfficiencyAvg.setText(String.format(Locale.getDefault(), "%.1f%%", result.avgEfficiency));
        }

        renderPhaseBarChart(result);
        renderEfficiencyTrendChart(result);
    }

    private int getScoreColor(int score) {
        if (score >= 90) return Color.parseColor("#30D158");
        if (score >= 75) return Color.parseColor("#007AFF");
        if (score >= 60) return Color.parseColor("#FF9500");
        return Color.parseColor("#FF3B30");
    }

    private void renderPhaseBarChart(ChargingEfficiencyUseCase.Result result) {
        if (phaseBarChart == null || result == null) return;

        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0f, result.avgTrickleRatio, "涓流"));
        entries.add(new BarEntry(1f, result.avgConstantCurrentRatio, "恒流"));
        entries.add(new BarEntry(2f, result.avgConstantVoltageRatio, "恒压"));

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColors(new int[]{
                Color.parseColor("#64D2FF"),
                Color.parseColor("#007AFF"),
                Color.parseColor("#FF9500")
        });
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(Color.parseColor("#8A8A8E"));
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.0f%%", value);
            }
        });

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);
        phaseBarChart.setData(barData);
        phaseBarChart.invalidate();
    }

    private void renderEfficiencyTrendChart(ChargingEfficiencyUseCase.Result result) {
        if (efficiencyTrendChart == null || result == null || result.recentSessions == null) return;

        List<ChargingEfficiencyUseCase.SessionEfficiency> sessions = result.recentSessions;
        if (sessions.isEmpty()) return;

        List<BarEntry> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.getDefault());

        int count = Math.min(5, sessions.size());
        for (int i = count - 1; i >= 0; i--) {
            ChargingEfficiencyUseCase.SessionEfficiency se = sessions.get(i);
            entries.add(new BarEntry((float) (count - 1 - i), se.avgEfficiency));
            labels.add(sdf.format(new Date(se.startTime)));
        }

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColor(Color.parseColor("#007AFF"));
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(9f);
        dataSet.setValueTextColor(Color.parseColor("#8A8A8E"));
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.0f%%", value);
            }
        });

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.5f);
        efficiencyTrendChart.setData(barData);

        XAxis xAxis = efficiencyTrendChart.getXAxis();
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                if (idx >= 0 && idx < labels.size()) {
                    return labels.get(idx);
                }
                return "";
            }
        });
        xAxis.setLabelCount(labels.size(), true);

        efficiencyTrendChart.invalidate();
    }

    private void renderHistoryList(List<ChargingSessionSummary> summaries) {
        if (containerHistory == null) return;
        containerHistory.removeAllViews();

        if (summaries.isEmpty()) {
            TextView emptyView = new TextView(requireContext());
            emptyView.setText("暂无充电记录");
            emptyView.setTextAppearance(requireContext(), R.style.iOSBody_Secondary);
            emptyView.setPadding(dpToPx(22), dpToPx(16), dpToPx(22), dpToPx(16));
            containerHistory.addView(emptyView);
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());

        for (int i = 0; i < summaries.size(); i++) {
            ChargingSessionSummary s = summaries.get(i);

            if (i > 0) {
                View separator = new View(requireContext());
                separator.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                separator.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_separator));
                containerHistory.addView(separator);
            }

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dpToPx(22), dpToPx(14), dpToPx(22), dpToPx(14));

            // 第一行：时间 + 充电类型
            LinearLayout row1 = new LinearLayout(requireContext());
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView tvTime = new TextView(requireContext());
            tvTime.setText(dateFormat.format(new Date(s.startTime)));
            tvTime.setTextAppearance(requireContext(), R.style.iOSBody);
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT);
            timeParams.weight = 1;
            tvTime.setLayoutParams(timeParams);

            TextView tvType = new TextView(requireContext());
            tvType.setText(getChargeTypeLabel(s.chargeType, s.maxPower));
            tvType.setTextAppearance(requireContext(), R.style.iOSCaption);
            tvType.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary));

            row1.addView(tvTime);
            row1.addView(tvType);
            row.addView(row1);

            // 第二行：时长 + 电量变化 + 峰值功率
            TextView tvDetail = new TextView(requireContext());
            String durationStr = formatDuration(s.duration);
            String levelStr = String.format(Locale.getDefault(), "%d%% → %d%%", s.startLevel, s.endLevel);
            String powerStr = String.format(Locale.getDefault(), "峰值 %.1f W", s.maxPower);
            tvDetail.setText(String.format("%s  |  %s  |  %s", durationStr, levelStr, powerStr));
            tvDetail.setTextAppearance(requireContext(), R.style.iOSBody_Secondary);
            tvDetail.setPadding(0, dpToPx(4), 0, 0);

            row.addView(tvDetail);
            containerHistory.addView(row);
        }
    }

    private long getPeriodStartTime() {
        Calendar cal = Calendar.getInstance();
        switch (periodMode) {
            case 1: // 周
                cal.add(Calendar.DAY_OF_YEAR, -7);
                break;
            case 2: // 月
                cal.add(Calendar.MONTH, -1);
                break;
            default: // 日
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                break;
        }
        return cal.getTimeInMillis();
    }

    private String getPeriodLabel() {
        switch (periodMode) {
            case 1: return "近7天";
            case 2: return "近30天";
            default: return "今日";
        }
    }

    private String getChargeTypeLabel(String type, float maxPower) {
        if (maxPower >= 60) return "超快充";
        if (maxPower >= 25) return "快充";
        if (maxPower >= 10) return "普通充电";
        return "慢充";
    }

    private String formatDuration(long ms) {
        long mins = ms / 60000;
        if (mins <= 0) return "<1分";
        long hours = mins / 60;
        mins = mins % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d时%d分", hours, mins);
        }
        return String.format(Locale.getDefault(), "%d分", mins);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    private static class ChargingSessionSummary {
        String sessionId;
        long startTime;
        long endTime;
        long duration;
        int startLevel;
        int endLevel;
        float maxPower;
        float avgPower;
        String chargeType;
        String phase;
    }
}
