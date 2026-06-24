package com.batteryhealth.app.ui.power;

import android.os.Build;
import android.os.Bundle;
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

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.utils.ThreadExecutor;

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

    private LinearLayout containerHistory;
    private TextView tvPeriodLabel;
    private TextView tvTotalSessions;
    private TextView tvTotalEnergy;
    private TextView tvAvgPower;

    private int periodMode = 0; // 0=日, 1=周, 2=月

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_charging_history, container, false);
        initViews(view);
        animateEntry(view);
        loadData();
        return view;
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
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    private void loadData() {
        ThreadExecutor.execute(() -> {
            try {
                BatteryHealthApplication app = (BatteryHealthApplication) requireActivity().getApplication();
                if (app == null) return;
                var db = app.getDatabase();
                if (db == null) return;

                // 计算时间范围
                long startTime = getPeriodStartTime();
                List<PowerHistory> records = db.powerHistoryDao().getSince(startTime);
                if (records == null) records = new ArrayList<>();

                // 按会话分组
                Map<String, List<PowerHistory>> sessionMap = new LinkedHashMap<>();
                for (PowerHistory r : records) {
                    String sid = r.getSessionId();
                    if (sid == null || sid.isEmpty()) sid = "unknown_" + r.getTimestamp();
                    if (!sessionMap.containsKey(sid)) {
                        sessionMap.put(sid, new ArrayList<>());
                    }
                    sessionMap.get(sid).add(r);
                }

                // 统计数据
                int totalSessions = sessionMap.size();
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

                    // 估算充电能量 (Wh) = 平均功率 × 时长(小时)
                    totalEnergyWh += summary.avgPower * (summary.duration / (1000f * 60 * 60));
                    totalPowerSum += powerSum;
                    powerCount += sessionRecords.size();

                    summaries.add(summary);
                }

                // 按时间倒序
                Collections.sort(summaries, (a, b) -> Long.compare(b.startTime, a.startTime));

                float finalTotalEnergyWh = totalEnergyWh;
                float finalAvgPower = powerCount > 0 ? totalPowerSum / powerCount : 0;

                if (isAdded()) {
                    handler.post(() -> {
                        if (!isAdded()) return;
                        // 更新统计
                        tvTotalSessions.setText(String.format(Locale.getDefault(), "%d 次", totalSessions));
                        tvTotalEnergy.setText(String.format(Locale.getDefault(), "%.1f Wh", finalTotalEnergyWh));
                        tvAvgPower.setText(String.format(Locale.getDefault(), "%.1f W", finalAvgPower));
                        tvPeriodLabel.setText(getPeriodLabel());

                        // 渲染历史列表
                        renderHistoryList(summaries);
                    });
                }
            } catch (Exception e) {
                // 静默处理
            }
        });
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
                separator.setBackgroundColor(getResources().getColor(R.color.ios_separator));
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
            tvType.setTextColor(getResources().getColor(R.color.primary));

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
