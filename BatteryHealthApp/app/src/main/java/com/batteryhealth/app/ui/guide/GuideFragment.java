package com.batteryhealth.app.ui.guide;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import android.content.DialogInterface;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BugReportGuide;
import com.batteryhealth.app.utils.BugReportAnalyzer;
import com.batteryhealth.app.utils.BugReportExportUtil;
import com.batteryhealth.app.utils.BugReportHistoryManager;
import com.batteryhealth.app.utils.FragmentErrorViewHelper;
import com.batteryhealth.app.utils.ThreadExecutor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GuideFragment extends Fragment {

    private static final String TAG = "GuideFragment";

    private LinearLayout guideContainer;
    private LinearLayout analysisResultContainer;
    private LinearLayout historyContainer;
    private View historyCard;
    private Button btnUpload;
    private Button btnExport;
    private Button btnShare;
    private Button btnClearHistory;
    private LinearLayout brandTabContainer;
    private TextView tvAnalysisSummary;
    private TextView tvProgressStatus;
    private ProgressBar progressBar;
    private LinearLayout exportButtonContainer;

    private BugReportAnalyzer analyzer;
    private BugReportHistoryManager historyManager;

    private String currentBrand;
    private BugReportGuide.AnalysisResult currentResult;

    private ActivityResultLauncher<String[]> filePickerLauncher;
    private ActivityResultLauncher<Intent> fallbackPickerLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.fragment_guide, container, false);

            analyzer = new BugReportAnalyzer(requireContext().getApplicationContext());
            historyManager = new BugReportHistoryManager(requireContext().getApplicationContext());

            filePickerLauncher = registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    new androidx.activity.result.ActivityResultCallback<Uri>() {
                        @Override
                        public void onActivityResult(Uri uri) {
                            if (uri != null) {
                                analyzeBugReport(uri);
                            }
                        }
                    }
            );

            fallbackPickerLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new androidx.activity.result.ActivityResultCallback<androidx.activity.result.ActivityResult>() {
                        @Override
                        public void onActivityResult(androidx.activity.result.ActivityResult result) {
                            if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                                Uri uri = result.getData().getData();
                                if (uri != null) analyzeBugReport(uri);
                            }
                        }
                    }
            );

            initViews(view);
            loadBrandTabs();
            loadHistory();

            return view;
        } catch (Exception e) {
            Log.e(TAG, "Error creating view", e);
            Context ctx = getContext();
            if (ctx == null && container != null) ctx = container.getContext();
            return FragmentErrorViewHelper.createErrorView(ctx, e);
        }
    }

    private void initViews(View view) {
        guideContainer = view.findViewById(R.id.guide_container);
        analysisResultContainer = view.findViewById(R.id.analysis_result_container);
        historyContainer = view.findViewById(R.id.history_container);
        historyCard = view.findViewById(R.id.history_card);
        btnUpload = view.findViewById(R.id.btn_upload_bugreport);
        btnExport = view.findViewById(R.id.btn_export_report);
        btnShare = view.findViewById(R.id.btn_share_report);
        btnClearHistory = view.findViewById(R.id.btn_clear_history);
        brandTabContainer = view.findViewById(R.id.brand_tab_container);
        tvAnalysisSummary = view.findViewById(R.id.tv_analysis_summary);
        tvProgressStatus = view.findViewById(R.id.tv_progress_status);
        progressBar = view.findViewById(R.id.progress_analysis);
        exportButtonContainer = view.findViewById(R.id.export_button_container);

        btnUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickBugReportFile();
            }
        });
        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportReport();
            }
        });
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareReport();
            }
        });
        btnClearHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showClearHistoryDialog();
            }
        });
    }

    private void pickBugReportFile() {
        try {
            filePickerLauncher.launch(new String[]{"*/*"});
        } catch (Exception e) {
            Log.e(TAG, "Error opening file picker: " + e.getMessage());
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            fallbackPickerLauncher.launch(intent);
        }
    }

    private void loadBrandTabs() {
        List<BugReportGuide.BrandGuide> guides = BugReportGuide.getAllGuides();
        currentBrand = BugReportGuide.getCurrentBrand();

        brandTabContainer.removeAllViews();

        for (BugReportGuide.BrandGuide guide : guides) {
            TextView tab = (TextView) LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_brand_tab, brandTabContainer, false);
            tab.setText(guide.brandZh);
            tab.setTag(guide.brand);

            if (guide.brand.equals(currentBrand)) {
                tab.setActivated(true);
                tab.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
            }

            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int i = 0; i < brandTabContainer.getChildCount(); i++) {
                        View child = brandTabContainer.getChildAt(i);
                        child.setActivated(false);
                        if (child instanceof TextView) {
                            ((TextView) child).setTextColor(
                                    ContextCompat.getColor(requireContext(), R.color.label_2));
                        }
                    }
                    tab.setActivated(true);
                    tab.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
                    loadBrandGuide(guide.brand);
                }
            });

            brandTabContainer.addView(tab);
        }

        loadBrandGuide(currentBrand);
    }

    private void loadBrandGuide(String brand) {
        BugReportGuide.BrandGuide guide = BugReportGuide.getGuideForBrand(brand);
        guideContainer.removeAllViews();

        if (guide == null) return;

        addSection("品牌指南", guide.brandZh);

        addSection("开启开发者选项步骤", null);
        for (int i = 0; i < guide.steps.length; i++) {
            addListItem((i + 1) + ". " + guide.steps[i]);
        }

        addSection("常用 ADB 命令", null);
        for (String cmd : guide.adbCommands) {
            addCodeItem(cmd);
        }

        addSection("注意事项", null);
        for (String note : guide.notes) {
            addNoteItem(note);
        }

        addSection("快捷方式", guide.screenshotGuide);
    }

    private void addSection(String title, String subtitle) {
        View sectionView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_guide_section, guideContainer, false);

        TextView tvTitle = sectionView.findViewById(R.id.tv_section_title);
        TextView tvSubtitle = sectionView.findViewById(R.id.tv_section_subtitle);

        tvTitle.setText(title);
        if (subtitle != null) {
            tvSubtitle.setText(subtitle);
            tvSubtitle.setVisibility(View.VISIBLE);
        } else {
            tvSubtitle.setVisibility(View.GONE);
        }

        guideContainer.addView(sectionView);
    }

    private void addListItem(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextAppearance(requireContext(), R.style.WebListLabel);
        tv.setPadding(dpToPx(32), dpToPx(8), dpToPx(16), dpToPx(8));
        tv.setTextSize(15);
        guideContainer.addView(tv);
    }

    private void addNoteItem(String note) {
        TextView tv = new TextView(requireContext());
        tv.setText("• " + note);
        tv.setTextAppearance(requireContext(), R.style.WebListValue);
        tv.setPadding(dpToPx(32), dpToPx(4), dpToPx(16), dpToPx(4));
        tv.setTextSize(14);
        guideContainer.addView(tv);
    }

    private void addCodeItem(String code) {
        View codeView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_guide_code, guideContainer, false);
        TextView tvCode = codeView.findViewById(R.id.tv_code);
        tvCode.setText(code);
        tvCode.setTextIsSelectable(true);
        codeView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) requireContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("ADB Command", code);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(requireContext(), R.string.action_copy_command, Toast.LENGTH_SHORT).show();
            }
        });
        guideContainer.addView(codeView);
    }

    private void analyzeBugReport(Uri uri) {
        String fileName = getFileName(uri);
        Log.d(TAG, "Analyzing bug report: " + fileName);

        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        tvProgressStatus.setVisibility(View.VISIBLE);
        tvProgressStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.label_2));
        tvProgressStatus.setText(R.string.action_upload_bugreport);
        btnUpload.setEnabled(false);

        BugReportAnalyzer.AnalysisProgressCallback callback =
                new BugReportAnalyzer.AnalysisProgressCallback() {
            @Override
            public void onProgress(final int step, final int totalSteps, final String description) {
                if (isAdded() && getActivity() != null) {
                    ThreadExecutor.runOnMain(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAdded()) return;
                            int progress = (int) ((step / (float) totalSteps) * 100);
                            progressBar.setProgress(progress);
                            tvProgressStatus.setText(description);
                        }
                    });
                }
            }

            @Override
            public void onFileValidated(final boolean valid, final String reason) {
                if (isAdded() && getActivity() != null) {
                    ThreadExecutor.runOnMain(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAdded()) return;
                            if (valid) {
                                tvProgressStatus.setText(R.string.success_file_validated);
                            } else {
                                showError(reason);
                            }
                        }
                    });
                }
            }

            @Override
            public void onSectionParsed(String sectionName, int itemsFound) {
                Log.d(TAG, "Section parsed: " + sectionName + ", items: " + itemsFound);
            }
        };

        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                File tempFile = null;
                try {
                    tempFile = copyUriToTempFile(uri);
                    if (tempFile == null) {
                        if (isAdded() && getActivity() != null) {
                            ThreadExecutor.runOnMain(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isAdded()) return;
                                    showError("文件读取失败，请检查文件权限");
                                }
                            });
                        }
                        return;
                    }

                    boolean valid = analyzer.validateBugReportFile(tempFile);
                    if (!valid) {
                        if (isAdded() && getActivity() != null) {
                            ThreadExecutor.runOnMain(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isAdded()) return;
                                    showError("文件格式验证失败，请确保文件为有效的 Android bugreport（.zip 或 .txt）");
                                }
                            });
                        }
                        return;
                    }

                    if (isAdded() && getActivity() != null) {
                        ThreadExecutor.runOnMain(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded()) return;
                                tvProgressStatus.setText(R.string.success_file_validated);
                            }
                        });
                    }

                    final BugReportGuide.AnalysisResult result = analyzer.analyze(tempFile, callback);

                    if (isAdded() && getActivity() != null) {
                        ThreadExecutor.runOnMain(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded()) return;
                                showAnalysisResult(result);
                                if (historyManager != null) {
                                    historyManager.saveRecord(result);
                                }
                                loadHistory();
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error analyzing bug report: " + e.getMessage(), e);
                    if (isAdded() && getActivity() != null) {
                        ThreadExecutor.runOnMain(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded()) return;
                                showError("分析异常: " + e.getMessage());
                            }
                        });
                    }
                } finally {
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                    }
                }
            }
        });
    }

    private void showAnalysisResult(BugReportGuide.AnalysisResult result) {
        currentResult = result;
        analysisResultContainer.removeAllViews();

        if (result.summary != null) {
            StringBuilder summary = new StringBuilder();
            summary.append("分析完成\n\n");
            summary.append("设备健康度: ").append(result.summary.overallHealth).append("\n");
            summary.append("充电次数: ").append(result.summary.totalChargeSessions).append(" 次\n");
            summary.append(String.format(Locale.getDefault(), "平均充电功率: %.1f W\n",
                    result.summary.avgChargePower));
            summary.append("异常数量: ").append(result.summary.anomalyCount);
            summary.append(" (严重: ").append(result.summary.criticalAnomalyCount).append(")\n");

            if (result.batteryEvents != null) {
                summary.append("Bugreport 事件数: ").append(result.batteryEvents.size()).append("\n");
            }

            tvAnalysisSummary.setText(summary.toString());
            tvAnalysisSummary.setVisibility(View.VISIBLE);
        }

        showDeviceInfoSection(result);
        showParseDetailSection(result);

        if (result.anomalies != null && !result.anomalies.isEmpty()) {
            addAnalysisSection("异常检测结果", null);

            for (BugReportGuide.AnalysisResult.Anomaly anomaly : result.anomalies) {
                View itemView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_analysis_anomaly, analysisResultContainer, false);

                TextView tvSeverity = itemView.findViewById(R.id.tv_severity);
                TextView tvType = itemView.findViewById(R.id.tv_type);
                TextView tvDesc = itemView.findViewById(R.id.tv_description);
                TextView tvSuggestion = itemView.findViewById(R.id.tv_suggestion);

                tvSeverity.setText(anomaly.severity);
                tvSeverity.setTextColor(getSeverityColor(anomaly.severity));
                tvType.setText(anomaly.type);
                tvDesc.setText(anomaly.description);
                tvSuggestion.setText("建议: " + anomaly.suggestion);

                analysisResultContainer.addView(itemView);
            }
        }

        if (result.batteryEvents != null && !result.batteryEvents.isEmpty()) {
            addAnalysisSection("Bugreport 关键指标", "从 bugreport 解析出的电池/性能指标");

            for (BugReportGuide.AnalysisResult.BatteryEvent event : result.batteryEvents) {
                String time = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        .format(new Date(event.timestamp));
                String info = String.format(Locale.getDefault(), "[%s] %s - %s",
                        time, event.type != null ? event.type : "未知",
                        event.detail != null ? event.detail : "");

                TextView tv = new TextView(requireContext());
                tv.setText(info);
                tv.setTextAppearance(requireContext(), R.style.WebListLabel);
                tv.setPadding(dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(4));
                tv.setTextIsSelectable(true);
                analysisResultContainer.addView(tv);
            }
        }

        if (result.chargeSessions != null && !result.chargeSessions.isEmpty()) {
            addAnalysisSection("充电会话统计", null);

            for (BugReportGuide.AnalysisResult.ChargeSession session : result.chargeSessions) {
                String duration = formatDuration(session.endTime - session.startTime);
                String info = String.format(Locale.getDefault(),
                        "电量: %d%%→%d%%, 时长: %s, 功率: %.1fW (最高: %.1fW)",
                        session.startLevel, session.endLevel, duration,
                        session.avgPower, session.maxPower);

                TextView tv = new TextView(requireContext());
                tv.setText(info);
                tv.setTextAppearance(requireContext(), R.style.WebListLabel);
                tv.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
                analysisResultContainer.addView(tv);
            }
        }

        if (result.wakelocks != null && !result.wakelocks.isEmpty()) {
            addAnalysisSection("耗电应用排行", null);

            for (BugReportGuide.AnalysisResult.AppWakelock wakelock : result.wakelocks) {
                String info = String.format(Locale.getDefault(),
                        "%s (%s) - 唤醒 %d 次, 持续 %s",
                        wakelock.appName, wakelock.packageName, wakelock.count,
                        formatDuration(wakelock.durationMs));

                TextView tv = new TextView(requireContext());
                tv.setText(info);
                tv.setTextAppearance(requireContext(), R.style.WebListLabel);
                tv.setPadding(dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(4));
                analysisResultContainer.addView(tv);
            }
        }

        if (result.healthChecks != null && !result.healthChecks.isEmpty()) {
            addAnalysisSection("健康检查", null);

            for (BugReportGuide.AnalysisResult.HealthCheck check : result.healthChecks) {
                String info = String.format(Locale.getDefault(), "[%s] %s - %s",
                        check.status, check.checkType,
                        check.detail != null ? check.detail : "");

                TextView tv = new TextView(requireContext());
                tv.setText(info);
                tv.setTextAppearance(requireContext(), R.style.WebListLabel);
                tv.setPadding(dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(4));
                analysisResultContainer.addView(tv);
            }
        }

        analysisResultContainer.setVisibility(View.VISIBLE);
        exportButtonContainer.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        tvProgressStatus.setVisibility(View.GONE);
        btnUpload.setText(R.string.action_upload_bugreport);
        btnUpload.setEnabled(true);
    }

    private void showDeviceInfoSection(BugReportGuide.AnalysisResult result) {
        if (result.deviceInfo == null) return;

        BugReportGuide.AnalysisResult.DeviceInfo di = result.deviceInfo;
        boolean hasData = (di.model != null && !di.model.isEmpty())
                || (di.brand != null && !di.brand.isEmpty())
                || (di.androidVersion != null && !di.androidVersion.isEmpty())
                || (di.buildNumber != null && !di.buildNumber.isEmpty())
                || (di.serialNumber != null && !di.serialNumber.isEmpty())
                || (di.manufacturingDate != null && !di.manufacturingDate.isEmpty())
                || di.batteryCapacity > 0
                || di.cycleCount > 0
                || di.healthPercentage > 0
                || di.designCapacityMah > 0
                || di.currentCapacityMah > 0
                || di.temperatureCelsius > 0
                || di.screenOnTimeHours > 0;

        if (!hasData) return;

        addAnalysisSection("设备信息", null);

        addDeviceInfoRow("设备型号", di.model);
        addDeviceInfoRow("品牌", di.brand);
        addDeviceInfoRow("Android 版本", di.androidVersion);
        addDeviceInfoRow("Build 编号", di.buildNumber);
        addDeviceInfoRow("序列号", di.serialNumber);
        addDeviceInfoRow("制造日期", di.manufacturingDate);

        if (di.designCapacityMah > 0) {
            addDeviceInfoRow("设计容量", di.designCapacityMah + " mAh");
        }
        if (di.currentCapacityMah > 0) {
            addDeviceInfoRow("满充容量", di.currentCapacityMah + " mAh");
        }
        if (di.batteryCapacity > 0) {
            addDeviceInfoRow("电池容量", di.batteryCapacity + " mAh");
        }
        if (di.cycleCount > 0) {
            addDeviceInfoRow("循环次数", di.cycleCount + " 次");
        }
        if (di.healthPercentage > 0) {
            addDeviceInfoRow("健康度", String.format(Locale.getDefault(), "%.1f%%", di.healthPercentage));
        }
        if (di.temperatureCelsius > 0) {
            addDeviceInfoRow("温度", String.format(Locale.getDefault(), "%.1f°C", di.temperatureCelsius));
        }
        if (di.screenOnTimeHours > 0) {
            addDeviceInfoRow("屏幕使用时间", di.screenOnTimeHours + " 小时");
        }
    }

    private void addDeviceInfoRow(String key, String value) {
        if (value == null || value.isEmpty()) return;

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(4));

        TextView tvKey = new TextView(requireContext());
        tvKey.setText(key + ": ");
        tvKey.setTextAppearance(requireContext(), R.style.WebListValue);
        tvKey.setTextColor(ContextCompat.getColor(requireContext(), R.color.label_2));

        TextView tvValue = new TextView(requireContext());
        tvValue.setText(value);
        tvValue.setTextAppearance(requireContext(), R.style.WebListLabel);
        tvValue.setTextIsSelectable(true);

        row.addView(tvKey);
        row.addView(tvValue);
        analysisResultContainer.addView(row);
    }

    private void showParseDetailSection(BugReportGuide.AnalysisResult result) {
        if (result.summary == null) return;
        if (result.summary.extractedFields.isEmpty() && result.summary.missingFields.isEmpty()) return;

        addAnalysisSection("解析详情", result.parseDetail);

        if (!result.summary.extractedFields.isEmpty()) {
            StringBuilder sb = new StringBuilder("✓ 已提取: ");
            for (int i = 0; i < result.summary.extractedFields.size(); i++) {
                if (i > 0) sb.append("、");
                sb.append(result.summary.extractedFields.get(i));
            }
            TextView tv = new TextView(requireContext());
            tv.setText(sb.toString());
            tv.setTextAppearance(requireContext(), R.style.WebListValue);
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            tv.setPadding(dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(4));
            analysisResultContainer.addView(tv);
        }

        if (!result.summary.missingFields.isEmpty()) {
            StringBuilder sb = new StringBuilder("⚠ 缺少: ");
            for (int i = 0; i < result.summary.missingFields.size(); i++) {
                if (i > 0) sb.append("、");
                sb.append(result.summary.missingFields.get(i));
            }
            TextView tv = new TextView(requireContext());
            tv.setText(sb.toString());
            tv.setTextAppearance(requireContext(), R.style.WebListValue);
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange));
            tv.setPadding(dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(4));
            analysisResultContainer.addView(tv);
        }
    }

    private void addAnalysisSection(String title, String subtitle) {
        View sectionView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_guide_section, analysisResultContainer, false);

        TextView tvTitle = sectionView.findViewById(R.id.tv_section_title);
        TextView tvSubtitle = sectionView.findViewById(R.id.tv_section_subtitle);

        tvTitle.setText(title);
        if (subtitle != null) {
            tvSubtitle.setText(subtitle);
            tvSubtitle.setVisibility(View.VISIBLE);
        } else {
            tvSubtitle.setVisibility(View.GONE);
        }

        analysisResultContainer.addView(sectionView);
    }

    private int getSeverityColor(String severity) {
        switch (severity) {
            case "CRITICAL":
                return ContextCompat.getColor(requireContext(), R.color.red);
            case "HIGH":
                return ContextCompat.getColor(requireContext(), R.color.orange);
            case "MEDIUM":
                return ContextCompat.getColor(requireContext(), R.color.yellow);
            default:
                return ContextCompat.getColor(requireContext(), R.color.label_2);
        }
    }

    private String formatDuration(long ms) {
        if (ms < 0) ms = 0;
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d小时%d分钟", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format(Locale.getDefault(), "%d分钟", minutes);
        } else {
            return String.format(Locale.getDefault(), "%d秒", seconds);
        }
    }

    private void loadHistory() {
        List<BugReportGuide.HistoryRecord> records = historyManager.getRecords();

        if (records.isEmpty()) {
            historyCard.setVisibility(View.GONE);
            return;
        }

        historyCard.setVisibility(View.VISIBLE);
        historyContainer.removeAllViews();

        for (BugReportGuide.HistoryRecord record : records) {
            View itemView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_guide_section, historyContainer, false);

            TextView tvTitle = itemView.findViewById(R.id.tv_section_title);
            TextView tvSubtitle = itemView.findViewById(R.id.tv_section_subtitle);

            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(new Date(record.timestamp));
            tvTitle.setText(time + " · " + record.deviceModel);

            StringBuilder detail = new StringBuilder();
            detail.append("健康度: ").append(record.overallHealth);
            if (record.healthPercentage > 0) {
                detail.append(String.format(Locale.getDefault(), " (%.1f%%)", record.healthPercentage));
            }
            detail.append(" · 异常: ").append(record.anomalyCount);
            if (record.criticalAnomalyCount > 0) {
                detail.append(" (严重: ").append(record.criticalAnomalyCount).append(")");
            }
            tvSubtitle.setText(detail.toString());
            tvSubtitle.setVisibility(View.VISIBLE);

            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("删除记录")
                            .setMessage("确定要删除这条分析记录吗？")
                            .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    historyManager.deleteRecord(record.timestamp);
                                    loadHistory();
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    return true;
                }
            });

            historyContainer.addView(itemView);
        }
    }

    private void showClearHistoryDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("清除历史")
                .setMessage("确定要清除所有分析历史记录吗？此操作不可撤销。")
                .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        historyManager.clearAll();
                        loadHistory();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void exportReport() {
        if (currentResult == null) return;

        File file = BugReportExportUtil.exportToTextFile(requireContext(), currentResult);
        if (file != null && file.exists()) {
            Toast.makeText(requireContext(), R.string.health_check_export_success,
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), R.string.health_check_export_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void shareReport() {
        if (currentResult == null) return;

        boolean success = BugReportExportUtil.shareReport(requireContext(), currentResult);
        if (!success) {
            Toast.makeText(requireContext(), R.string.health_check_export_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showError(String message) {
        tvProgressStatus.setText(message);
        tvProgressStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red));
        tvProgressStatus.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        btnUpload.setText(R.string.action_upload_bugreport);
        btnUpload.setEnabled(true);
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = requireContext().getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private File copyUriToTempFile(Uri uri) {
        try {
            String fileName = getFileName(uri);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "bugreport_temp.dat";
            }
            fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            File tempFile = new File(requireContext().getCacheDir(), fileName);
            try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
            return tempFile;
        } catch (Exception e) {
            Log.e(TAG, "Error copying file: " + e.getMessage());
            return null;
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
