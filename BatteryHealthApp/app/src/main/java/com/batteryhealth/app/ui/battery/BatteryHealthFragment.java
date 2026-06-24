package com.batteryhealth.app.ui.battery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.ui.view.HealthRingView;
import com.batteryhealth.app.ui.viewmodel.BatteryHealthViewModel;
import com.batteryhealth.app.utils.BatteryReportGenerator;
import com.batteryhealth.app.utils.ThreadExecutor;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.Locale;

public class BatteryHealthFragment extends Fragment {

    private HealthRingView healthRing;
    private TextView tvHealthPercentage;
    private TextView tvHealthGrade;
    private TextView tvHealthStatus;
    private TextView tvBatteryLevel;
    private TextView tvChargingStatus;
    private TextView tvCurrentNow;
    private TextView tvCapacity;
    private TextView tvCycleCount;
    private TextView tvTemperature;
    private TextView tvVoltage;
    private TextView tvBatterySource;
    private TextView tvTechnology;
    private Button btnWeeklyReport;
    private Button btnMonthlyReport;
    private TextView tvReportSummary;

    private BatteryHealthViewModel viewModel;
    private BatteryReportGenerator reportGenerator;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_battery_health, container, false);
        initViews(view);
        initViewModel();
        animateEntry(view);
        return view;
    }

    private void initViews(View view) {
        healthRing = view.findViewById(R.id.health_ring);
        tvHealthPercentage = view.findViewById(R.id.tv_health_percentage);
        tvHealthGrade = view.findViewById(R.id.tv_health_grade);
        tvHealthStatus = view.findViewById(R.id.tv_health_status);
        tvBatteryLevel = view.findViewById(R.id.tv_battery_level);
        tvChargingStatus = view.findViewById(R.id.tv_charging_status);
        tvCurrentNow = view.findViewById(R.id.tv_current_now);
        tvCapacity = view.findViewById(R.id.tv_capacity);
        tvCycleCount = view.findViewById(R.id.tv_cycle_count);
        tvTemperature = view.findViewById(R.id.tv_temperature);
        tvVoltage = view.findViewById(R.id.tv_voltage);
        tvBatterySource = view.findViewById(R.id.tv_battery_source);
        tvTechnology = view.findViewById(R.id.tv_technology);
        btnWeeklyReport = view.findViewById(R.id.btn_weekly_report);
        btnMonthlyReport = view.findViewById(R.id.btn_monthly_report);
        tvReportSummary = view.findViewById(R.id.tv_report_summary);

        btnWeeklyReport.setOnClickListener(v -> generateReport(true));
        btnMonthlyReport.setOnClickListener(v -> generateReport(false));
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(BatteryHealthViewModel.class);
        
        viewModel.getBatteryInfo().observe(getViewLifecycleOwner(), this::updateUI);
        viewModel.getHealthGrade().observe(getViewLifecycleOwner(), grade -> 
                tvHealthGrade.setText(String.format(Locale.getDefault(), "等级 %s", grade)));
        viewModel.getHealthStatus().observe(getViewLifecycleOwner(), tvHealthStatus::setText);
        viewModel.getBatterySource().observe(getViewLifecycleOwner(), tvBatterySource::setText);
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onResume() {
        super.onResume();
        reportGenerator = new BatteryReportGenerator(requireContext());
        viewModel.refreshData();
    }

    private void updateUI(BatteryInfo info) {
        if (info == null || !isAdded()) return;

        tvBatteryLevel.setText(String.format(Locale.getDefault(), "%d%%", info.getLevel()));
        tvChargingStatus.setText(viewModel.getBatterySource().getValue() != null ? 
                viewModel.getBatterySource().getValue() : "--");

        float currentMa = info.getCurrentNow() / 1000f;
        tvCurrentNow.setText(String.format(Locale.getDefault(), "%.0f mA", Math.abs(currentMa)));

        tvCapacity.setText(viewModel.formatCapacity(info));
        tvCycleCount.setText(viewModel.formatCycleCount(info));
        tvTemperature.setText(viewModel.formatTemperature(info.getTemperature()));
        tvVoltage.setText(viewModel.formatVoltage(info.getVoltage()));
        tvTechnology.setText(info.getTechnology());

        float healthPct = info.getHealthPercentage();
        if (healthPct >= 0) {
            tvHealthPercentage.setText(String.format(Locale.getDefault(), "%.0f%%", healthPct));
            UiAnimationHelper.animateRingProgress(healthRing, (int) healthPct);
        } else {
            tvHealthPercentage.setText("--");
            tvHealthGrade.setText("--");
            tvHealthStatus.setText(getString(R.string.health_unknown));
        }
    }

    private void generateReport(boolean weekly) {
        tvReportSummary.setText(getString(R.string.status_calculating));
        btnWeeklyReport.setEnabled(false);
        btnMonthlyReport.setEnabled(false);

        ThreadExecutor.execute(() -> {
            BatteryReportGenerator.Report report = weekly
                    ? reportGenerator.generateWeeklyReport()
                    : reportGenerator.generateMonthlyReport();

            String summary = formatReportSummary(report);
            if (isAdded() && getActivity() != null) {
                ThreadExecutor.runOnMain(() -> {
                    if (!isAdded()) return;
                    tvReportSummary.setText(summary);
                    btnWeeklyReport.setEnabled(true);
                    btnMonthlyReport.setEnabled(true);
                });
            }
        });
    }

    private String formatReportSummary(BatteryReportGenerator.Report report) {
        if (report.startHealth < 0) {
            return getString(R.string.health_check_no_data);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.getDefault(), "健康度：%.1f%% → %.1f%%（%.2f%%变化）\n",
                report.startHealth, report.endHealth, report.healthDecay));
        sb.append(String.format(Locale.getDefault(), "平均温度：%.1f°C（最高：%.1f°C）\n",
                report.avgTemperature, report.maxTemperature));
        sb.append(String.format("充电次数：%d 次\n", report.chargeCount));
        sb.append(String.format("趋势：%s\n\n", report.healthTrend));
        sb.append(report.recommendation);

        return sb.toString();
    }
}