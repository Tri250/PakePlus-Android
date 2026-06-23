package com.batteryhealth.app.ui.guide;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BugReportGuide;
import com.batteryhealth.app.utils.BatteryDataManager;
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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_guide, container, false);
        initViews(view);
        analyzer = new BugReportAnalyzer(getContext());
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
        if (getContext() == null) return;

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
        Context ctx = getContext();
        if (ctx == null) return;
        View sectionView = LayoutInflater.from(ctx)
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
        Context ctx = getContext();
        if (ctx == null) return;
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextAppearance(ctx, R.style.WebListLabel);
        tv.setPadding(dpToPx(32), dpToPx(8), dpToPx(16), dpToPx(8));
        tv.setTextSize(15);
        guideContainer.addView(tv);
    }

    private void addCodeItem(String code) {
        Context ctx = getContext();
        if (ctx == null) return;
        View codeView = LayoutInflater.from(ctx)
                .inflate(R.layout.item_guide_code, guideContainer, false);
        
        TextView tvCode = codeView.findViewById(R.id.tv_code);
        tvCode.setText(code);
        
        guideContainer.addView(codeView);
    }

    private void addNoteItem(String note) {
        Context ctx = getContext();
        if (ctx == null) return;
        TextView tv = new TextView(ctx);
        tv.setText("• " + note);
        tv.setTextAppearance(ctx, R.style.WebListValue);
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

            // 在主线程获取 Context，避免后台线程调用 requireContext()
            final Context ctx = getContext();
            if (ctx == null) {
                btnUpload.setText("上传分析报告");
                btnUpload.setEnabled(true);
                return;
            }

            new Thread(() -> {
                try {
                    File tempFile = copyUriToTempFile(ctx, uri, fileName);
                    if (tempFile != null) {
                        BugReportGuide.AnalysisResult result = analyzer.analyze(tempFile);

                        // Inject bugreport data into BatteryDataManager for use by other modules
                        try {
                            if (getActivity() instanceof com.batteryhealth.app.MainActivity) {
                                com.batteryhealth.app.MainActivity activity = (com.batteryhealth.app.MainActivity) getActivity();
                                BatteryDataManager bdm = activity.getBatteryDataManager();
                                if (bdm != null) {
                                    bdm.setBugreportData(result);
                                    Log.d(TAG, "Bugreport data injected into BatteryDataManager");
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to inject bugreport data: " + e.getMessage());
                        }

                        if (isAdded()) {
                            mainHandler.post(() -> {
                                if (!isAdded()) return;
                                showAnalysisResult(result);
                                btnUpload.setText("上传分析报告");
                                btnUpload.setEnabled(true);
                            });
                        }

                        tempFile.delete();
                    } else {
                        if (isAdded()) {
                            mainHandler.post(() -> {
                                if (!isAdded()) return;
                                tvAnalysisSummary.setText("文件读取失败");
                                btnUpload.setText("上传分析报告");
                                btnUpload.setEnabled(true);
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in analysis thread: " + e.getMessage());
                    if (isAdded()) {
                        mainHandler.post(() -> {
                            if (!isAdded()) return;
                            btnUpload.setText("上传分析报告");
                            btnUpload.setEnabled(true);
                        });
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
        Context ctx = getContext();
        if (ctx == null) return uri.getLastPathSegment();
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null)) {
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

    private File copyUriToTempFile(Context ctx, Uri uri, String fileName) {
        // 保留原始文件扩展名，避免 .txt bugreport 被当作 .zip 解析
        String ext = ".txt";
        if (fileName != null) {
            int dotIdx = fileName.lastIndexOf('.');
            if (dotIdx >= 0) {
                ext = fileName.substring(dotIdx).toLowerCase();
            }
        }
        File tempFile = new File(ctx.getCacheDir(), "bugreport_temp" + ext);
        try (java.io.InputStream is = ctx.getContentResolver().openInputStream(uri);
             java.io.OutputStream os = new java.io.FileOutputStream(tempFile)) {
            if (is == null) {
                Log.e(TAG, "InputStream is null for uri: " + uri);
                return null;
            }
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            return tempFile;
        } catch (Exception e) {
            Log.e(TAG, "Error copying file: " + e.getMessage());
            tempFile.delete();
            return null;
        }
    }

    private void showAnalysisResult(BugReportGuide.AnalysisResult result) {
        if (!isAdded() || getContext() == null) return;

        Context ctx = getContext();
        analysisResultContainer.removeAllViews();
        
        if (result.summary != null) {
            StringBuilder summary = new StringBuilder();
            summary.append("分析完成\n\n");
            summary.append("设备健康度: ").append(result.summary.overallHealth).append("\n");
            summary.append("充电次数: ").append(result.summary.totalChargeSessions).append(" 次\n");
            summary.append("平均充电功率: ").append(String.format("%.1f", result.summary.avgChargePower)).append(" W\n");
            summary.append("异常数量: ").append(result.summary.anomalyCount).append(" (严重: ").append(result.summary.criticalAnomalyCount).append(")\n");
            
            tvAnalysisSummary.setText(summary.toString());
            tvAnalysisSummary.setVisibility(View.VISIBLE);
        }

        if (result.anomalies != null && !result.anomalies.isEmpty()) {
            addAnalysisSection("异常检测结果", null);
            
            for (BugReportGuide.AnalysisResult.Anomaly anomaly : result.anomalies) {
                View itemView = LayoutInflater.from(ctx)
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

        if (result.chargeSessions != null && !result.chargeSessions.isEmpty()) {
            addAnalysisSection("充电会话统计", null);
            
            for (BugReportGuide.AnalysisResult.ChargeSession session : result.chargeSessions) {
                String duration = formatDuration(session.endTime - session.startTime);
                String info = String.format("电量: %d%%→%d%%, 时长: %s, 功率: %.1fW (最高: %.1fW)",
                        session.startLevel, session.endLevel, duration, session.avgPower, session.maxPower);
                
                TextView tv = new TextView(ctx);
                tv.setText(info);
                tv.setTextAppearance(ctx, R.style.WebListLabel);
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
                
                TextView tv = new TextView(ctx);
                tv.setText(info);
                tv.setTextAppearance(ctx, R.style.WebListLabel);
                tv.setPadding(dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(4));
                analysisResultContainer.addView(tv);
            }
        }

        analysisResultContainer.setVisibility(View.VISIBLE);
    }

    private void addAnalysisSection(String title, String subtitle) {
        Context ctx = getContext();
        if (ctx == null) return;
        View sectionView = LayoutInflater.from(ctx)
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
        Context ctx = getContext();
        if (ctx == null) return 0;
        switch (severity) {
            case "CRITICAL": return ContextCompat.getColor(ctx, R.color.red);
            case "HIGH": return ContextCompat.getColor(ctx, R.color.orange);
            case "MEDIUM": return ContextCompat.getColor(ctx, R.color.yellow);
            default: return ContextCompat.getColor(ctx, R.color.label_2);
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
        if (getResources() == null) return dp;
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
