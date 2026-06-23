package com.batteryhealth.app.ui.guide;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BugReportGuide;
import com.batteryhealth.app.utils.BugReportAnalyzer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GuideFragment extends Fragment {

    private static final String TAG = "GuideFragment";
    private static final int REQUEST_CODE_PICK_FILE = 1001;

    private LinearLayout guideContainer;
    private LinearLayout analysisResultContainer;
    private Button btnUpload;
    private TextView tvAnalysisSummary;

    private BugReportAnalyzer analyzer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_guide, container, false);
        initViews(view);
        analyzer = new BugReportAnalyzer(requireContext());
        loadBrandGuide();
        return view;
    }

    private void initViews(View view) {
        guideContainer = view.findViewById(R.id.guide_container);
        analysisResultContainer = view.findViewById(R.id.analysis_result_container);
        btnUpload = view.findViewById(R.id.btn_upload_bugreport);
        tvAnalysisSummary = view.findViewById(R.id.tv_analysis_summary);

        btnUpload.setOnClickListener(v -> pickBugReportFile());
    }

    private void loadBrandGuide() {
        BugReportGuide.BrandGuide guide = BugReportGuide.getCurrentBrandGuide();
        if (guide == null) {
            guide = BugReportGuide.getGuideForBrand("google");
        }

        if (guide != null) {
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

    private void addCodeItem(String code) {
        View codeView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_guide_code, guideContainer, false);
        
        TextView tvCode = codeView.findViewById(R.id.tv_code);
        tvCode.setText(code);
        
        guideContainer.addView(codeView);
    }

    private void addNoteItem(String note) {
        TextView tv = new TextView(requireContext());
        tv.setText("• " + note);
        tv.setTextAppearance(requireContext(), R.style.WebListValue);
        tv.setPadding(dpToPx(32), dpToPx(4), dpToPx(16), dpToPx(4));
        tv.setTextSize(14);
        guideContainer.addView(tv);
    }

    private void pickBugReportFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择 bugreport 文件"), REQUEST_CODE_PICK_FILE);
        } catch (Exception e) {
            Log.e(TAG, "Error opening file picker: " + e.getMessage());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == android.app.Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                analyzeBugReport(uri);
            }
        }
    }

    private void analyzeBugReport(Uri uri) {
        try {
            String fileName = getFileName(uri);
            Log.d(TAG, "Analyzing bug report: " + fileName);

            btnUpload.setText("分析中...");
            btnUpload.setEnabled(false);

            new Thread(() -> {
                File tempFile = null;
                try {
                    tempFile = copyUriToTempFile(uri);
                    if (tempFile != null) {
                        BugReportGuide.AnalysisResult result = analyzer.analyze(tempFile);

                        if (isAdded() && getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                showAnalysisResult(result);
                                btnUpload.setText("上传分析报告");
                                btnUpload.setEnabled(true);
                            });
                        }
                    } else {
                        if (isAdded() && getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                tvAnalysisSummary.setText("文件读取失败");
                                btnUpload.setText("上传分析报告");
                                btnUpload.setEnabled(true);
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error analyzing bug report: " + e.getMessage());
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded()) return;
                            tvAnalysisSummary.setText("分析异常: " + e.getMessage());
                            btnUpload.setText("上传分析报告");
                            btnUpload.setEnabled(true);
                        });
                    }
                } finally {
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                    }
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Error analyzing bug report: " + e.getMessage());
            btnUpload.setText("上传分析报告");
            btnUpload.setEnabled(true);
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
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
            // Sanitize filename
            fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            File tempFile = new File(requireContext().getCacheDir(), fileName);
            try (java.io.InputStream is = requireContext().getContentResolver().openInputStream(uri);
                 java.io.OutputStream os = new java.io.FileOutputStream(tempFile)) {
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

    private void showAnalysisResult(BugReportGuide.AnalysisResult result) {
        analysisResultContainer.removeAllViews();
        
        if (result.summary != null) {
            StringBuilder summary = new StringBuilder();
            summary.append("分析完成\n\n");
            summary.append("设备健康度: ").append(result.summary.overallHealth).append("\n");
            summary.append("充电次数: ").append(result.summary.totalChargeSessions).append(" 次\n");
            summary.append("平均充电功率: ").append(String.format("%.1f", result.summary.avgChargePower)).append(" W\n");
            summary.append("异常数量: ").append(result.summary.anomalyCount).append(" (严重: ").append(result.summary.criticalAnomalyCount).append(")\n");
            if (result.batteryEvents != null) {
                summary.append("Bugreport 事件数: ").append(result.batteryEvents.size()).append("\n");
            }
            
            tvAnalysisSummary.setText(summary.toString());
            tvAnalysisSummary.setVisibility(View.VISIBLE);
        }

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
                String time = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                        .format(new java.util.Date(event.timestamp));
                String info = String.format("[%s] %s - %s",
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
                String info = String.format("电量: %d%%→%d%%, 时长: %s, 功率: %.1fW (最高: %.1fW)",
                        session.startLevel, session.endLevel, duration, session.avgPower, session.maxPower);
                
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
                String info = String.format("%s (%s) - 唤醒 %d 次, 持续 %s",
                        wakelock.appName, wakelock.packageName, wakelock.count, 
                        formatDuration(wakelock.durationMs));
                
                TextView tv = new TextView(requireContext());
                tv.setText(info);
                tv.setTextAppearance(requireContext(), R.style.WebListLabel);
                tv.setPadding(dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(4));
                analysisResultContainer.addView(tv);
            }
        }

        analysisResultContainer.setVisibility(View.VISIBLE);
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
            case "CRITICAL": return getResources().getColor(R.color.red);
            case "HIGH": return getResources().getColor(R.color.orange);
            case "MEDIUM": return getResources().getColor(R.color.yellow);
            default: return getResources().getColor(R.color.label_2);
        }
    }

    private String formatDuration(long ms) {
        if (ms < 0) ms = 0;
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d分钟", minutes);
        } else {
            return String.format("%d秒", seconds);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
