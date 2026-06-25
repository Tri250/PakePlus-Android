package com.batteryhealth.app.ui.battery;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.domain.usecase.BatteryInsightUseCase;
import com.batteryhealth.app.domain.usecase.TemperatureDamageUseCase;
import com.batteryhealth.app.ui.view.HealthRingView;
import com.batteryhealth.app.ui.viewmodel.BatteryHealthViewModel;
import com.batteryhealth.app.utils.BatteryReportGenerator;
import com.batteryhealth.app.utils.DataExportManager;
import com.batteryhealth.app.utils.FragmentErrorViewHelper;
import com.batteryhealth.app.utils.ThreadExecutor;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.List;
import java.util.Locale;

public class BatteryHealthFragment extends Fragment {

    private static final String TAG = "BatteryHealthFragment";
    private static final int REQUEST_SAVE_FILE = 1002;

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
    private TextView tvAiInsightTitle, tvAiInsightShort, tvAiInsightDetail, tvAiInsightCount, tvAiInsightMore;
    private View separatorAiDetail;
    private boolean isDetailExpanded = false;
    private java.util.List<BatteryInsightUseCase.InsightItem> currentInsights;

    private BatteryHealthViewModel viewModel;
    private BatteryReportGenerator reportGenerator;
    private BatteryReportGenerator.Report lastReport;
    private DataExportManager exportManager;
    private int pendingExportFormat = DataExportManager.FORMAT_CSV;
    private TemperatureDamageUseCase temperatureDamageUseCase;
    private TemperatureDamageUseCase.Result temperatureDamageResult;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.fragment_battery_health, container, false);
            initViews(view);
            initViewModel();
            animateEntry(view);
            return view;
        } catch (Exception e) {
            Log.e(TAG, "Error creating view", e);
            Context ctx = getContext();
            if (ctx == null && container != null) ctx = container.getContext();
            return FragmentErrorViewHelper.createErrorView(ctx, e);
        }
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

        tvAiInsightTitle = view.findViewById(R.id.tv_ai_insight_title);
        tvAiInsightShort = view.findViewById(R.id.tv_ai_insight_short);
        tvAiInsightDetail = view.findViewById(R.id.tv_ai_insight_detail);
        tvAiInsightCount = view.findViewById(R.id.tv_ai_insight_count);
        tvAiInsightMore = view.findViewById(R.id.tv_ai_insight_more);
        separatorAiDetail = view.findViewById(R.id.separator_ai_detail);

        btnWeeklyReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateReport(true);
            }
        });
        btnMonthlyReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateReport(false);
            }
        });

        Context ctx = getContext();
        if (ctx != null) {
            exportManager = new DataExportManager(ctx);
            temperatureDamageUseCase = new TemperatureDamageUseCase();
        }

        // 长按报告区域显示操作菜单
        tvReportSummary.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showReportActionsDialog();
                return true;
            }
        });

        if (tvTemperature != null) {
            tvTemperature.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTemperatureDamageDialog();
                }
            });
        }

        if (tvAiInsightMore != null) {
            tvAiInsightMore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleDetailExpanded();
                }
            });
        }
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(BatteryHealthViewModel.class);
        
        viewModel.getBatteryInfo().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<com.batteryhealth.app.data.model.BatteryInfo>() {
            @Override
            public void onChanged(com.batteryhealth.app.data.model.BatteryInfo info) {
                updateUI(info);
            }
        });
        viewModel.getHealthGrade().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<String>() {
            @Override
            public void onChanged(String grade) {
                tvHealthGrade.setText(String.format(Locale.getDefault(), "等级 %s", grade));
            }
        });
        viewModel.getHealthStatus().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<String>() {
            @Override
            public void onChanged(String status) {
                tvHealthStatus.setText(status);
            }
        });
        viewModel.getBatterySource().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<String>() {
            @Override
            public void onChanged(String source) {
                tvBatterySource.setText(source);
            }
        });

        viewModel.getInsights().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<java.util.List<BatteryInsightUseCase.InsightItem>>() {
            @Override
            public void onChanged(java.util.List<BatteryInsightUseCase.InsightItem> insightItems) {
                currentInsights = insightItems;
                updateInsightUI();
            }
        });

        viewModel.getCurrentInsightIndex().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<Integer>() {
            @Override
            public void onChanged(Integer integer) {
                updateInsightUI();
            }
        });
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onResume() {
        super.onResume();
        reportGenerator = new BatteryReportGenerator(requireContext().getApplicationContext());
        viewModel.refreshData();
        loadTemperatureDamageData();
    }

    private void loadTemperatureDamageData() {
        if (temperatureDamageUseCase == null) return;

        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    BatteryHealthApplication app = (BatteryHealthApplication) requireActivity().getApplication();
                    if (app == null || app.getDatabase() == null) return;

                    long thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
                    List<BatteryInfo> history = app.getDatabase().batteryInfoDao().getSince(thirtyDaysAgo);

                    final TemperatureDamageUseCase.Result result = temperatureDamageUseCase.execute(history);
                    temperatureDamageResult = result;
                } catch (Exception e) {
                    Log.e(TAG, "Load temperature damage failed", e);
                }
            }
        });
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

        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                BatteryReportGenerator.Report report = weekly
                        ? reportGenerator.generateWeeklyReport()
                        : reportGenerator.generateMonthlyReport();
                lastReport = report;

                final String summary = formatReportSummary(report);
                if (isAdded() && getActivity() != null) {
                    ThreadExecutor.runOnMain(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAdded()) return;
                            tvReportSummary.setText(summary);
                            btnWeeklyReport.setEnabled(true);
                            btnMonthlyReport.setEnabled(true);
                        }
                    });
                }
            }
        });
    }

    /**
     * 显示报告操作菜单（分享/导出）
     */
    private void showReportActionsDialog() {
        Context ctx = getContext();
        if (ctx == null) return;

        final String[] options = {"分享报告", "导出数据"};
        new AlertDialog.Builder(ctx)
                .setTitle("操作")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        shareReport();
                    } else if (which == 1) {
                        showExportFormatDialog();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * 分享报告内容到其他应用。
     */
    private void shareReport() {
        String content;
        if (lastReport != null && lastReport.startHealth >= 0) {
            content = reportGenerator.formatReport(lastReport);
        } else {
            CharSequence currentText = tvReportSummary.getText();
            content = currentText != null ? currentText.toString() : "";
        }
        if (content.isEmpty() || getString(R.string.status_calculating).equals(content)) {
            return;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, content);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_report_title)));
    }

    private void showExportFormatDialog() {
        Context ctx = getContext();
        if (ctx == null) return;

        final String[] formatLabels = {"CSV 格式", "JSON 格式"};
        final int[] formatValues = {DataExportManager.FORMAT_CSV, DataExportManager.FORMAT_JSON};

        new AlertDialog.Builder(ctx)
                .setTitle("选择导出格式")
                .setSingleChoiceItems(formatLabels, 0, (dialog, which) -> {
                    pendingExportFormat = formatValues[which];
                })
                .setPositiveButton("导出", (dialog, which) -> {
                    dialog.dismiss();
                    startExportFilePicker();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void startExportFilePicker() {
        Context ctx = getContext();
        if (ctx == null || exportManager == null) return;

        String fileName = exportManager.generateFileName(pendingExportFormat);
        String mimeType = pendingExportFormat == DataExportManager.FORMAT_JSON
                ? "application/json" : "text/csv";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        try {
            startActivityForResult(intent, REQUEST_SAVE_FILE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open file picker", e);
            Toast.makeText(ctx, "打开文件选择器失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SAVE_FILE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                performExport(uri);
            }
        }
    }

    private void performExport(Uri targetUri) {
        Context ctx = getContext();
        if (ctx == null || exportManager == null) return;

        Toast.makeText(ctx, "开始导出数据...", Toast.LENGTH_SHORT).show();

        ThreadExecutor.execute(() -> {
            try {
                BatteryHealthApplication app = (BatteryHealthApplication) requireActivity().getApplication();
                if (app == null || app.getDatabase() == null) {
                    ThreadExecutor.runOnMain(() ->
                            Toast.makeText(ctx, "数据库未就绪", Toast.LENGTH_SHORT).show());
                    return;
                }

                List<BatteryInfo> batteryList = app.getDatabase().batteryInfoDao().getAll();
                List<PowerHistory> powerList = app.getDatabase().powerHistoryDao().getAll();

                exportManager.exportBatteryData(batteryList, powerList, targetUri,
                        pendingExportFormat, new DataExportManager.ExportCallback() {
                            @Override
                            public void onProgress(int progress, int total) {
                            }

                            @Override
                            public void onSuccess(Uri uri, String fileName) {
                                ThreadExecutor.runOnMain(() ->
                                        Toast.makeText(ctx, "导出成功: " + fileName, Toast.LENGTH_LONG).show());
                            }

                            @Override
                            public void onError(String message) {
                                ThreadExecutor.runOnMain(() ->
                                        Toast.makeText(ctx, "导出失败: " + message, Toast.LENGTH_LONG).show());
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Export failed", e);
                ThreadExecutor.runOnMain(() ->
                        Toast.makeText(ctx, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
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

    private void updateInsightUI() {
        if (tvAiInsightTitle == null || tvAiInsightShort == null) return;

        if (currentInsights == null || currentInsights.isEmpty()) {
            tvAiInsightTitle.setText(R.string.ai_insight_loading);
            tvAiInsightShort.setText(R.string.ai_insight_loading);
            if (tvAiInsightDetail != null) tvAiInsightDetail.setText(R.string.ai_insight_loading);
            if (tvAiInsightCount != null) tvAiInsightCount.setText("0");
            return;
        }

        int index = 0;
        Integer idx = viewModel.getCurrentInsightIndex().getValue();
        if (idx != null && idx >= 0 && idx < currentInsights.size()) {
            index = idx;
        }

        BatteryInsightUseCase.InsightItem insight = currentInsights.get(index);
        if (insight == null) return;

        tvAiInsightTitle.setText(insight.title);
        tvAiInsightShort.setText(insight.shortMessage);
        if (tvAiInsightDetail != null) {
            tvAiInsightDetail.setText(insight.detailMessage);
        }
        if (tvAiInsightCount != null) {
            tvAiInsightCount.setText(String.valueOf(currentInsights.size()));
        }
    }

    private void toggleDetailExpanded() {
        isDetailExpanded = !isDetailExpanded;
        if (tvAiInsightDetail != null && separatorAiDetail != null && tvAiInsightMore != null) {
            if (isDetailExpanded) {
                tvAiInsightDetail.setVisibility(View.VISIBLE);
                separatorAiDetail.setVisibility(View.VISIBLE);
                tvAiInsightMore.setText(R.string.label_see_more);
            } else {
                tvAiInsightDetail.setVisibility(View.GONE);
                separatorAiDetail.setVisibility(View.GONE);
                tvAiInsightMore.setText(R.string.label_see_more);
            }
        }
    }

    private void showTemperatureDamageDialog() {
        Context ctx = getContext();
        if (ctx == null) return;

        if (temperatureDamageResult == null || !temperatureDamageResult.hasData) {
            Toast.makeText(ctx, "温度伤害数据计算中，请稍后再试", Toast.LENGTH_SHORT).show();
            loadTemperatureDamageData();
            return;
        }

        TemperatureDamageUseCase.Result result = temperatureDamageResult;

        StringBuilder message = new StringBuilder();
        message.append("温度伤害评分：").append(String.format(Locale.getDefault(), "%.0f 分", result.damageScore))
                .append("（等级 ").append(result.damageGrade).append("）\n");
        message.append("伤害等级：").append(result.damageLevel).append("\n\n");

        message.append("【高温区间累计时长（近30天）】\n");
        message.append(String.format(Locale.getDefault(), "轻度（35-40℃）：%.1f 小时\n", result.mildDurationHours));
        message.append(String.format(Locale.getDefault(), "中度（40-45℃）：%.1f 小时\n", result.moderateDurationHours));
        message.append(String.format(Locale.getDefault(), "重度（45-50℃）：%.1f 小时\n", result.severeDurationHours));
        message.append(String.format(Locale.getDefault(), "极重（>50℃）：%.1f 小时\n", result.criticalDurationHours));
        message.append(String.format(Locale.getDefault(), "总计高温时长：%s\n\n", result.getTotalHighTempDurationText()));

        if (result.predictedMonthlyDecayFromTemp > 0) {
            message.append(String.format(Locale.getDefault(), "【温度相关容量衰减预测】\n预计月衰减：%.2f%%\n\n",
                    result.predictedMonthlyDecayFromTemp));
        }

        message.append("【保护建议】\n");
        if (result.adviceList != null) {
            for (int i = 0; i < result.adviceList.size(); i++) {
                message.append("• ").append(result.adviceList.get(i)).append("\n");
            }
        }

        new AlertDialog.Builder(ctx)
                .setTitle("温度累积伤害评估")
                .setMessage(message.toString().trim())
                .setPositiveButton("知道了", null)
                .show();
    }
}