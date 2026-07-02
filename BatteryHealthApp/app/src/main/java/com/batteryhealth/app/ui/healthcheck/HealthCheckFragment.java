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
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.HealthCheckResult;
import com.batteryhealth.app.utils.FragmentErrorViewHelper;
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
    // 保存当前订阅的 callback 引用，Fragment 销毁时调用 removeCallback 避免泄漏
    private HealthCheckEngine.Callback pendingCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_health_check, container, false);
        } catch (Exception e) {
            Log.e(TAG, "onCreateView failed: " + e.getMessage(), e);
            return FragmentErrorViewHelper.createErrorView(getContext(), e);
        }
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

            // 保护性检查：若关键视图缺失，跳过后续初始化
            if (tvActionCheck == null) {
                Log.w(TAG, "tv_action_check is null, skipping health check setup");
                return;
            }

            tvActionCheck.setOnClickListener(v -> startCheck());
            if (tvActionReport != null) {
                tvActionReport.setOnClickListener(v -> exportReport());
            }

            if (recyclerResults != null) {
                adapter = new HealthCheckAdapter();
                recyclerResults.setLayoutManager(new LinearLayoutManager(getContext()));
                recyclerResults.setAdapter(adapter);
                // 移除与 XML 冲突的 nestedScrolling 设置，使用 XML 中定义的 false
                // 让 NestedScrollView 统一管理滚动，避免双滚动冲突
            }

            engine = HealthCheckEngine.getInstance();

            // 首次进入自动触发一次检测
            if (isAdded()) {
                startCheck();
            }
        } catch (Exception e) {
            Log.e(TAG, "onViewCreated failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 取消订阅 engine 回调，避免持有已销毁 Fragment 视图导致泄漏
        if (engine != null && pendingCallback != null) {
            engine.removeCallback(pendingCallback);
            pendingCallback = null;
        }
        // 清理 Handler 待执行回调，避免内存泄漏
        mainHandler.removeCallbacksAndMessages(null);
    }

    private void startCheck() {
        if (engine == null || !isAdded()) return;

        // 仅在新触发一轮检测时重置 UI；若 engine 已在运行，本次调用会订阅当前轮次，
        // 保持已有进度显示不重置，避免用户看到进度突然跳回 0%。
        boolean isNewRun = !engine.isRunning();
        if (isNewRun) {
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
        } else {
            // 引擎已在运行：显示进度指示，让用户知道检测正在进行中
            if (progressScanning != null) progressScanning.setVisibility(View.VISIBLE);
            if (tvScanningStatus != null) tvScanningStatus.setVisibility(View.VISIBLE);
            if (tvActionCheck != null) {
                tvActionCheck.setEnabled(false);
                tvActionCheck.setText(R.string.health_check_action_checking);
            }
        }

        Context ctx = requireContext().getApplicationContext();
        // 若上一次 callback 尚未清理（理论不会发生，保险起见），先移除
        if (pendingCallback != null && engine != null) {
            engine.removeCallback(pendingCallback);
        }
        pendingCallback = new HealthCheckEngine.Callback() {
            @Override
            public void onProgress(final int percent) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (tvScanningStatus != null) {
                        tvScanningStatus.setText(String.format(Locale.getDefault(), "%d%%", percent));
                    }
                });
            }

            @Override
            public void onCompleted(final List<HealthCheckResult> results) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    renderResults(results);
                });
            }

            @Override
            public void onError(final String message) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    Context c = getContext();
                    if (c != null) {
                        Toast.makeText(c, message, Toast.LENGTH_SHORT).show();
                    }
                    finishCheckingUI();
                });
            }
        };
        engine.startCheck(ctx, pendingCallback);
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
        if (lastResults.isEmpty()) {
            Toast.makeText(getContext(), R.string.health_check_no_data, Toast.LENGTH_SHORT).show();
            return;
        }
        String csv = engine.exportCsv(lastResults);
        if (csv == null || csv.isEmpty()) {
            Toast.makeText(getContext(), R.string.health_check_export_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Context ctx = getContext();
            if (ctx == null) return;
            ClipboardManager cm;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm = (ClipboardManager) ctx.getSystemService(ClipboardManager.class);
            } else {
                cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            }
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("health-check-report", csv));
            }
            Toast.makeText(ctx, getString(R.string.health_check_export_success), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.health_check_export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private class HealthCheckAdapter extends RecyclerView.Adapter<ResultViewHolder> {
        private final AsyncListDiffer<HealthCheckResult> differ = new AsyncListDiffer<>(
                this, new DiffUtil.ItemCallback<HealthCheckResult>() {
            @Override
            public boolean areItemsTheSame(@NonNull HealthCheckResult oldItem, @NonNull HealthCheckResult newItem) {
                String oldId = oldItem.getId();
                String newId = newItem.getId();
                return oldId != null && oldId.equals(newId);
            }

            @Override
            public boolean areContentsTheSame(@NonNull HealthCheckResult oldItem, @NonNull HealthCheckResult newItem) {
                return oldItem.getItemScore() == newItem.getItemScore()
                        && oldItem.getSeverity() == newItem.getSeverity()
                        && oldItem.getStatus() != null && oldItem.getStatus().equals(newItem.getStatus());
            }
        });

        void setData(List<HealthCheckResult> list) {
            differ.submitList(list != null ? new ArrayList<>(list) : new ArrayList<>());
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
            HealthCheckResult r = differ.getCurrentList().get(position);
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
                if (engine != null) {
                    boolean ok = engine.applyFix(getContext(), r);
                    if (!ok) Toast.makeText(getContext(), R.string.health_check_fix_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return differ.getCurrentList().size();
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
            sb.append("建议: ").append(r.getAdvice());
        }

        String title = r.getTitle() != null ? r.getTitle() : getString(R.string.health_check_no_data);

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setMessage(sb.toString())
                .setPositiveButton(android.R.string.cancel, null);
        if (r.isRepairable()) {
            builder.setNeutralButton(R.string.health_check_action_fix, (dialog, which) -> {
                if (engine != null) engine.applyFix(getContext(), r);
            });
        }
        try {
            builder.show();
        } catch (Exception ignored) {}
    }
}
