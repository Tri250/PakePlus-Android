package com.batteryhealth.app.ui.healthcheck;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.HealthCheckResult;
import com.batteryhealth.app.utils.healthcheck.HealthCheckEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 自检报告 Fragment：综合展示电池/充电/性能/系统权限的检测结果，
 * 支持一键执行全部检测并提供相应修复建议跳转。
 */
public class HealthCheckFragment extends Fragment {

    private static final String TAG = "HealthCheckFragment";

    private TextView tvTitle;
    private TextView tvOverallScore;
    private TextView tvOverallLabel;
    private ProgressBar progressOverall;
    private TextView tvActionCheck;
    private TextView tvActionReport;
    private ProgressBar progressScanning;
    private TextView tvScanningStatus;
    private RecyclerView recyclerResults;

    private HealthCheckAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private HealthCheckEngine engine;
    private List<HealthCheckResult> lastResults = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_health_check, container, false);
        } catch (Exception e) {
            Log.e(TAG, "onCreateView failed: " + e.getMessage(), e);
            return createErrorView(e);
        }
    }

    private View createErrorView(Exception e) {
        Context ctx = getContext();
        if (ctx == null) {
            // Fallback: use a plain View if no context available
            return new View(requireActivity());
        }
        TextView tv = new TextView(ctx);
        tv.setText(getString(R.string.error_view_load_failed, e.getClass().getSimpleName(), e.getMessage()));
        tv.setTextColor(ContextCompat.getColor(ctx, R.color.ios_label));
        tv.setTextSize(16);
        tv.setPadding(40, 100, 40, 40);
        tv.setBackgroundColor(ContextCompat.getColor(ctx, R.color.ios_background));
        return tv;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            tvTitle = view.findViewById(R.id.tv_health_check_title);
            tvOverallScore = view.findViewById(R.id.tv_overall_score);
            tvOverallLabel = view.findViewById(R.id.tv_overall_label);
            progressOverall = view.findViewById(R.id.progress_overall);
            tvActionCheck = view.findViewById(R.id.tv_action_check);
            tvActionReport = view.findViewById(R.id.tv_action_report);
            progressScanning = view.findViewById(R.id.progress_scanning);
            tvScanningStatus = view.findViewById(R.id.tv_scanning_status);
            recyclerResults = view.findViewById(R.id.recycler_results);

            adapter = new HealthCheckAdapter();
            recyclerResults.setLayoutManager(new LinearLayoutManager(requireContext()));
            recyclerResults.setAdapter(adapter);
            recyclerResults.setNestedScrollingEnabled(true);

            tvActionCheck.setOnClickListener(v -> startCheck());
            tvActionReport.setOnClickListener(v -> exportReport());

            engine = HealthCheckEngine.getInstance();

            // 首次进入自动触发一次检测
            startCheck();
        } catch (Exception e) {
            Log.e(TAG, "onViewCreated failed: " + e.getMessage(), e);
        }
    }

    private void startCheck() {
        if (engine == null) return;
        if (engine.isRunning()) return;

        Context ctx = getContext();
        if (ctx == null) return;

        if (tvActionCheck != null) {
            tvActionCheck.setEnabled(false);
            tvActionCheck.setText(R.string.health_check_action_checking);
        }
        if (progressScanning != null) progressScanning.setVisibility(View.VISIBLE);
        if (tvScanningStatus != null) {
            tvScanningStatus.setVisibility(View.VISIBLE);
            tvScanningStatus.setText(String.format(Locale.getDefault(), "%d%%", 0));
        }
        // 综合评分先重置
        if (tvOverallScore != null) tvOverallScore.setText("--");
        if (tvOverallLabel != null) tvOverallLabel.setText(R.string.health_check_label_running);
        if (progressOverall != null) {
            progressOverall.setProgress(0);
        }

        engine.startCheck(ctx, new HealthCheckEngine.Callback() {
            @Override
            public void onProgress(final int percent) {
                mainHandler.post(() -> {
                    if (tvScanningStatus != null) {
                        tvScanningStatus.setText(String.format(Locale.getDefault(), "%d%%", percent));
                    }
                });
            }

            @Override
            public void onCompleted(final List<HealthCheckResult> results) {
                mainHandler.post(() -> renderResults(results));
            }

            @Override
            public void onError(final String message) {
                mainHandler.post(() -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    }
                    finishCheckingUI();
                });
            }
        });
    }

    private void renderResults(List<HealthCheckResult> results) {
        lastResults = results != null ? results : new ArrayList<>();
        if (adapter != null) adapter.setData(lastResults);

        int score = engine != null ? engine.getOverallScore(lastResults) : 0;
        if (tvOverallScore != null) {
            tvOverallScore.setText(String.format(Locale.getDefault(), "%d", score));
        }
        if (tvOverallLabel != null) {
            String label;
            if (score >= 85) label = getString(R.string.health_check_score_excellent);
            else if (score >= 70) label = getString(R.string.health_check_score_good);
            else if (score >= 50) label = getString(R.string.health_check_score_normal);
            else if (score >= 30) label = getString(R.string.health_check_score_warning);
            else label = getString(R.string.health_check_score_critical);
            tvOverallLabel.setText(label);
        }
        if (progressOverall != null) {
            progressOverall.setProgress(Math.max(0, Math.min(100, score)));
        }

        finishCheckingUI();
    }

    private void finishCheckingUI() {
        if (tvActionCheck != null) {
            tvActionCheck.setEnabled(true);
            tvActionCheck.setText(R.string.health_check_action_recheck);
        }
        if (progressScanning != null) progressScanning.setVisibility(View.GONE);
        if (tvScanningStatus != null) tvScanningStatus.setVisibility(View.GONE);
    }

    private void exportReport() {
        Context ctx = getContext();
        if (ctx == null) return;
        if (lastResults.isEmpty()) {
            Toast.makeText(ctx, R.string.health_check_no_data, Toast.LENGTH_SHORT).show();
            return;
        }
        String csv = engine.exportCsv(lastResults);
        if (csv == null || csv.isEmpty()) {
            Toast.makeText(ctx, R.string.health_check_export_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("health-check-report", csv));
            }
            Toast.makeText(ctx, getString(R.string.health_check_export_success), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(ctx, R.string.health_check_export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private class HealthCheckAdapter extends RecyclerView.Adapter<ResultViewHolder> {
        private final List<HealthCheckResult> data = new ArrayList<>();

        void setData(List<HealthCheckResult> list) {
            data.clear();
            if (list != null) data.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_health_check, parent, false);
            return new ResultViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull ResultViewHolder h, int position) {
            HealthCheckResult r = data.get(position);
            h.tvTitle.setText(r.getTitle());
            h.tvStatus.setText(r.getStatus());
            if (r.getValue() != null && !r.getValue().isEmpty()) {
                h.tvValue.setVisibility(View.VISIBLE);
                String unit = r.getUnit() != null ? r.getUnit() : "";
                h.tvValue.setText(String.format("%s %s", r.getValue(), unit));
            } else {
                h.tvValue.setVisibility(View.GONE);
            }

            int color;
            switch (r.getSeverity()) {
                case HealthCheckResult.SEVERITY_CRITICAL: color = ContextCompat.getColor(h.itemView.getContext(), R.color.ios_red); break;
                case HealthCheckResult.SEVERITY_WARNING:  color = ContextCompat.getColor(h.itemView.getContext(), R.color.ios_orange); break;
                case HealthCheckResult.SEVERITY_INFO:     color = ContextCompat.getColor(h.itemView.getContext(), R.color.ios_blue); break;
                default:                                   color = ContextCompat.getColor(h.itemView.getContext(), R.color.ios_green);
            }
            h.vSeverity.setBackgroundColor(color);
            if (r.isRepairable()) {
                h.tvAction.setVisibility(View.VISIBLE);
                h.tvAction.setText(R.string.health_check_action_fix);
                h.tvAction.setTextColor(ContextCompat.getColor(h.tvAction.getContext(), R.color.ios_blue));
            } else {
                h.tvAction.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> showDetailDialog(r));
            h.tvAction.setOnClickListener(v -> {
                Context fixCtx = h.tvAction.getContext();
                if (engine != null && fixCtx != null) {
                    boolean ok = engine.applyFix(fixCtx, r);
                    if (!ok && getContext() != null) {
                        Toast.makeText(getContext(), R.string.health_check_fix_failed, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    private static class ResultViewHolder extends RecyclerView.ViewHolder {
        final View vSeverity;
        final TextView tvTitle;
        final TextView tvStatus;
        final TextView tvValue;
        final TextView tvAction;

        ResultViewHolder(@NonNull View itemView) {
            super(itemView);
            vSeverity = itemView.findViewById(R.id.view_severity);
            tvTitle = itemView.findViewById(R.id.tv_result_title);
            tvStatus = itemView.findViewById(R.id.tv_result_status);
            tvValue = itemView.findViewById(R.id.tv_result_value);
            tvAction = itemView.findViewById(R.id.tv_result_action);
        }
    }

    private void showDetailDialog(HealthCheckResult r) {
        if (r == null) return;
        Context ctx = getContext();
        if (ctx == null) return;

        StringBuilder sb = new StringBuilder();
        if (r.getDescription() != null && !r.getDescription().isEmpty()) {
            sb.append(r.getDescription()).append("\n\n");
        }
        if (r.getAdvice() != null && !r.getAdvice().isEmpty()) {
            sb.append("📌 ").append(r.getAdvice());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
                .setTitle(r.getTitle())
                .setMessage(sb.toString())
                .setPositiveButton(android.R.string.cancel, null);
        if (r.isRepairable()) {
            builder.setNeutralButton(R.string.health_check_action_fix, (dialog, which) -> {
                Context fixCtx = getContext();
                if (engine != null && fixCtx != null) engine.applyFix(fixCtx, r);
            });
        }
        try {
            builder.show();
        } catch (Exception ignored) {}
    }
}
