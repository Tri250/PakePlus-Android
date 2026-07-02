package com.batteryhealth.app.ui.healthcheck;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
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
import com.google.android.material.snackbar.Snackbar;

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
    private LinearLayout layoutEmptyState;

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
            layoutEmptyState = view.findViewById(R.id.layout_empty_state);

            // 保护性检查：若关键视图缺失，跳过后续初始化
            if (tvActionCheck == null) {
                Log.w(TAG, "tv_action_check is null, skipping health check setup");
                return;
            }

            tvActionCheck.setOnClickListener(new DebouncedClickListener(v -> startCheck()));
            if (tvActionReport != null) {
                tvActionReport.setOnClickListener(new DebouncedClickListener(v -> exportReport()));
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
                tvScanningStatus.setText(getString(R.string.health_check_scan_status, 0, ""));
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
            public void onProgress(final int percent, final String currentItemName) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (tvScanningStatus != null) {
                        String item = currentItemName != null && !currentItemName.isEmpty()
                                ? currentItemName : "";
                        tvScanningStatus.setText(
                                getString(R.string.health_check_scan_status, percent, item));
                    }
                });
            }

            @Override
            public void onProgress(final int percent) {
                onProgress(percent, "");
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

        boolean isEmpty = lastResults.isEmpty();
        if (recyclerResults != null) {
            recyclerResults.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }

        int score = engine != null ? engine.getOverallScore(lastResults) : 0;
        int colorRes = scoreToColorRes(score);
        int color = ContextCompat.getColor(requireContext(), colorRes);
        if (tvOverallScore != null) {
            tvOverallScore.setText(String.format(Locale.getDefault(), "%d", score));
            tvOverallScore.setTextColor(color);
        }
        if (tvOverallLabel != null) {
            String label;
            if (score >= 85) label = getString(R.string.health_check_score_excellent);
            else if (score >= 70) label = getString(R.string.health_check_score_good);
            else if (score >= 50) label = getString(R.string.health_check_score_normal);
            else if (score >= 30) label = getString(R.string.health_check_score_warning);
            else label = getString(R.string.health_check_score_critical);
            tvOverallLabel.setText(label);
            tvOverallLabel.setTextColor(color);
        }
        if (progressOverall != null) {
            progressOverall.setProgress(Math.max(0, Math.min(100, score)));
            progressOverall.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        }

        finishCheckingUI();
    }

    private static int scoreToColorRes(int score) {
        if (score >= 85) return R.color.ios_green;
        if (score >= 70) return R.color.ios_blue;
        if (score >= 50) return R.color.ios_orange;
        return R.color.ios_red;
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
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(ClipboardManager.class);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("health-check-report", csv));
            }
            View anchor = getView();
            if (anchor != null) {
                Snackbar.make(anchor, R.string.health_check_export_success, Snackbar.LENGTH_LONG)
                        .setAction(R.string.health_check_export_share, v -> shareText(csv))
                        .show();
            } else {
                Toast.makeText(ctx, R.string.health_check_export_success, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            View anchor = getView();
            if (anchor != null) {
                Snackbar.make(anchor, R.string.health_check_export_failed, Snackbar.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), R.string.health_check_export_failed, Toast.LENGTH_SHORT).show();
            }
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

            int color = ContextCompat.getColor(h.itemView.getContext(), severityToColorRes(r.getSeverity()));
            animateSeverityColor(h.vSeverity, color);
            if (r.isRepairable()) {
                h.tvAction.setVisibility(View.VISIBLE);
                h.tvAction.setText(R.string.health_check_action_fix);
                h.tvAction.setTextColor(ContextCompat.getColor(h.tvAction.getContext(), R.color.ios_blue));
            } else {
                h.tvAction.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(new DebouncedClickListener(v -> showDetailDialog(r)));
            h.tvAction.setOnClickListener(new DebouncedClickListener(v -> {
                if (engine != null) {
                    boolean ok = engine.applyFix(getContext(), r);
                    if (!ok) {
                        View anchor = getView();
                        if (anchor != null) {
                            Snackbar.make(anchor, R.string.health_check_fix_failed, Snackbar.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(), R.string.health_check_fix_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }));
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

    /**
     * 防抖点击监听器：避免用户快速连点导致重复触发检测或导出。
     */
    private static class DebouncedClickListener implements View.OnClickListener {
        private final View.OnClickListener delegate;
        private static final long DEBOUNCE_MS = 800;
        private long lastClickTime;

        DebouncedClickListener(View.OnClickListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onClick(View v) {
            long now = System.currentTimeMillis();
            if (now - lastClickTime < DEBOUNCE_MS) return;
            lastClickTime = now;
            delegate.onClick(v);
        }
    }

    private void shareText(String text) {
        if (text == null) return;
        Context ctx = getContext();
        if (ctx == null) return;
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.health_check_share_subject));
            startActivity(Intent.createChooser(shareIntent, getString(R.string.health_check_share_title)));
        } catch (Exception e) {
            Toast.makeText(ctx, R.string.health_check_share_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showDetailDialog(HealthCheckResult r) {
        if (r == null) return;
        Context ctx = getContext();
        if (ctx == null) return;

        View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_health_check_detail, null);
        TextView tvDialogTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvDialogStatus = dialogView.findViewById(R.id.tv_dialog_status);
        TextView tvDialogScore = dialogView.findViewById(R.id.tv_dialog_score);
        TextView tvDialogDesc = dialogView.findViewById(R.id.tv_dialog_desc);
        TextView tvDialogAdvice = dialogView.findViewById(R.id.tv_dialog_advice);
        View vDialogSeverity = dialogView.findViewById(R.id.view_dialog_severity);

        if (tvDialogTitle != null) {
            tvDialogTitle.setText(r.getTitle() != null ? r.getTitle() : getString(R.string.health_check_no_data));
        }
        if (tvDialogStatus != null) {
            tvDialogStatus.setText(r.getStatus() != null ? r.getStatus() : "");
            tvDialogStatus.setTextColor(ContextCompat.getColor(ctx, severityToColorRes(r.getSeverity())));
        }
        if (tvDialogScore != null) {
            tvDialogScore.setText(getString(R.string.health_check_score_format, r.getItemScore()));
        }
        if (tvDialogDesc != null) {
            tvDialogDesc.setText(r.getDescription() != null ? r.getDescription() : "");
            tvDialogDesc.setVisibility(r.getDescription() != null && !r.getDescription().isEmpty()
                    ? View.VISIBLE : View.GONE);
        }
        if (tvDialogAdvice != null) {
            tvDialogAdvice.setText(r.getAdvice() != null ? r.getAdvice() : "");
            tvDialogAdvice.setVisibility(r.getAdvice() != null && !r.getAdvice().isEmpty()
                    ? View.VISIBLE : View.GONE);
        }
        if (vDialogSeverity != null) {
            vDialogSeverity.setBackgroundColor(ContextCompat.getColor(ctx, severityToColorRes(r.getSeverity())));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
                .setView(dialogView)
                .setNegativeButton(android.R.string.ok, null);
        if (r.isRepairable()) {
            builder.setPositiveButton(R.string.health_check_action_fix, (dialog, which) -> {
                if (engine != null) engine.applyFix(getContext(), r);
            });
        }
        try {
            builder.show();
        } catch (Exception ignored) {}
    }

    private static int severityToColorRes(int severity) {
        switch (severity) {
            case HealthCheckResult.SEVERITY_CRITICAL: return R.color.ios_red;
            case HealthCheckResult.SEVERITY_WARNING:  return R.color.ios_orange;
            case HealthCheckResult.SEVERITY_INFO:     return R.color.ios_blue;
            default:                                   return R.color.ios_green;
        }
    }

    private static void animateSeverityColor(View v, int toColor) {
        if (v == null) return;
        Drawable bg = v.getBackground();
        int fromColor = (bg instanceof ColorDrawable) ? ((ColorDrawable) bg).getColor() : Color.TRANSPARENT;
        ObjectAnimator animator = ObjectAnimator.ofObject(
                v,
                "backgroundColor",
                new ArgbEvaluator(),
                fromColor,
                toColor
        );
        animator.setDuration(250);
        animator.start();
    }
}
