package com.batteryhealth.app.ui.origin;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryOriginRecord;
import com.batteryhealth.app.ui.viewmodel.BatteryOriginViewModel;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.BatteryOriginDetector;
import com.batteryhealth.app.utils.FragmentErrorViewHelper;

import java.util.List;
import java.util.Locale;

public class BatteryOriginFragment extends Fragment {

    private static final String TAG = "BatteryOriginFragment";

    private TextView tvOriginResult;
    private TextView tvOriginConfidence;
    private TextView tvOriginConclusion;
    private TextView tvDataSourceTag;
    private TextView tvManufactureDate;
    private TextView tvSerialNumber;
    private TextView tvManufacturer;
    private TextView tvOemInfo;
    private TextView tvTechnology;
    private TextView tvHealthStatus;
    private TextView tvCycleCount;
    private TextView tvCapacityInfo;
    private LinearLayout containerMethods;
    private ProgressBar progressDetect;
    private Button btnDetect;
    private Button btnShare;
    private TextView tvHistoryEyebrow;
    private LinearLayout cardHistory;
    private LinearLayout containerHistory;
    private TextView tvHistoryEmpty;

    private BatteryOriginViewModel viewModel;
    private boolean hasAutoDetected = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.fragment_battery_origin, container, false);
            initViews(view);
            animateEntry(view);
            return view;
        } catch (Exception e) {
            Log.e(TAG, "Error creating view", e);
            Context ctx = getContext();
            if (ctx == null && container != null) ctx = container.getContext();
            return FragmentErrorViewHelper.createErrorView(ctx, e);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            initViewModel();
            observeViewModel();
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 自动检测：首次进入页面时自动执行一次
        if (!hasAutoDetected) {
            hasAutoDetected = true;
            viewModel.autoDetect();
        }
    }

    private void initViews(View view) {
        tvOriginResult = view.findViewById(R.id.tv_origin_result);
        tvOriginConfidence = view.findViewById(R.id.tv_origin_confidence);
        tvOriginConclusion = view.findViewById(R.id.tv_origin_conclusion);
        tvDataSourceTag = view.findViewById(R.id.tv_data_source_tag);
        tvManufactureDate = view.findViewById(R.id.tv_manufacture_date);
        tvSerialNumber = view.findViewById(R.id.tv_serial_number);
        tvManufacturer = view.findViewById(R.id.tv_manufacturer);
        tvOemInfo = view.findViewById(R.id.tv_oem_info);
        tvTechnology = view.findViewById(R.id.tv_technology);
        tvHealthStatus = view.findViewById(R.id.tv_health_status);
        tvCycleCount = view.findViewById(R.id.tv_cycle_count);
        tvCapacityInfo = view.findViewById(R.id.tv_capacity_info);
        containerMethods = view.findViewById(R.id.container_methods);
        progressDetect = view.findViewById(R.id.progress_detect);
        btnDetect = view.findViewById(R.id.btn_detect);
        btnShare = view.findViewById(R.id.btn_share);
        tvHistoryEyebrow = view.findViewById(R.id.tv_history_eyebrow);
        cardHistory = view.findViewById(R.id.card_history);
        containerHistory = view.findViewById(R.id.container_history);
        tvHistoryEmpty = view.findViewById(R.id.tv_history_empty);

        btnDetect.setOnClickListener(v -> viewModel.manualDetect());
        btnShare.setOnClickListener(v -> shareReport());
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(BatteryOriginViewModel.class);

        // 初始化 ViewModel：注入 Context 和 BatteryDataManager
        BatteryDataManager batteryDataManager = null;
        if (getActivity() instanceof MainActivity) {
            batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
        }
        // 如果 MainActivity 还没初始化完成，使用 fallback 创建
        if (batteryDataManager == null) {
            Log.d(TAG, "BatteryDataManager from MainActivity is null, creating fallback");
            batteryDataManager = new BatteryDataManager(requireContext().getApplicationContext());
        }
        viewModel.initialize(requireContext().getApplicationContext(), batteryDataManager);
    }

    private void observeViewModel() {
        // 观察检测结果
        viewModel.getOriginResult().observe(getViewLifecycleOwner(), this::updateUI);

        // 观察检测状态
        viewModel.getIsDetecting().observe(getViewLifecycleOwner(), detecting -> {
            if (detecting) {
                progressDetect.setVisibility(View.VISIBLE);
                btnDetect.setEnabled(false);
                btnDetect.setText(getString(R.string.origin_detecting));
            } else {
                progressDetect.setVisibility(View.GONE);
                btnDetect.setEnabled(true);
                btnDetect.setText(getString(R.string.origin_redetect));
            }
        });

        // 观察检测错误
        viewModel.getDetectionError().observe(getViewLifecycleOwner(), error -> {
            if (error) {
                tvOriginResult.setText(getString(R.string.origin_detection_failed));
                tvOriginResult.setTextColor(ContextCompat.getColor(requireContext(), R.color.confidence_low));
                tvOriginConfidence.setText("");
                tvOriginConclusion.setText(getString(R.string.origin_detection_failed));
            }
        });

        // 观察历史记录
        viewModel.getHistoryRecords().observe(getViewLifecycleOwner(), this::updateHistoryUI);

        // 观察报告文本（用于分享）
        viewModel.getReportText().observe(getViewLifecycleOwner(), report -> {
            if (report != null && !report.isEmpty()) {
                launchShareIntent(report);
            }
        });
    }

    private void updateUI(BatteryOriginDetector.OriginResult result) {
        if (result == null) return;

        // 判定结果
        if (result.isOriginal) {
            tvOriginResult.setText(getString(R.string.result_original));
            tvOriginResult.setTextColor(ContextCompat.getColor(requireContext(), R.color.confidence_high));
        } else {
            tvOriginResult.setText(getString(R.string.result_replaced));
            tvOriginResult.setTextColor(ContextCompat.getColor(requireContext(), R.color.confidence_low));
        }

        // 置信度（带颜色分级）
        int confidenceColor = ContextCompat.getColor(requireContext(),
                viewModel.getConfidenceColorRes(result.confidence));
        tvOriginConfidence.setText(getString(R.string.origin_confidence_format, result.confidence));
        tvOriginConfidence.setTextColor(confidenceColor);

        // 结论
        tvOriginConclusion.setText(result.conclusion != null ? result.conclusion : "--");

        // 数据来源标签
        if (result.sourceTag != null && !result.sourceTag.isEmpty()) {
            tvDataSourceTag.setText(getString(R.string.origin_data_source_label) + "：" + result.sourceTag);
            tvDataSourceTag.setVisibility(View.VISIBLE);
        } else {
            tvDataSourceTag.setVisibility(View.GONE);
        }

        // 检测信息
        tvManufactureDate.setText(result.manufactureDate != null ? result.manufactureDate : "--");
        tvSerialNumber.setText(result.serialNumber != null ? result.serialNumber : "--");
        tvHealthStatus.setText(result.healthStatus != null ? result.healthStatus : "--");
        tvCycleCount.setText(result.cycleCount != null ? result.cycleCount : "--");
        tvManufacturer.setText(result.manufacturer != null ? result.manufacturer : "--");
        tvOemInfo.setText(result.oemInfo != null ? result.oemInfo : "--");
        tvTechnology.setText(result.technology != null ? result.technology : "--");

        // 容量信息
        if (result.designCapacity > 0 || result.currentCapacity > 0) {
            String capText;
            if (result.designCapacity > 0 && result.currentCapacity > 0) {
                float ratio = (result.currentCapacity * 100f) / result.designCapacity;
                capText = getString(R.string.origin_capacity_ratio_format,
                        result.designCapacity, result.currentCapacity, ratio);
            } else if (result.designCapacity > 0) {
                capText = result.designCapacity + " mAh";
            } else {
                capText = result.currentCapacity + " mAh";
            }
            tvCapacityInfo.setText(capText);
        } else {
            tvCapacityInfo.setText("--");
        }

        // 检测方法
        updateDetectionMethods(result.detectionMethods);

        // 启用分享按钮
        btnShare.setEnabled(true);
        btnShare.setAlpha(1.0f);
    }

    private void updateDetectionMethods(List<BatteryOriginDetector.DetectionMethod> methods) {
        containerMethods.removeAllViews();

        if (methods == null || methods.isEmpty()) {
            TextView emptyView = new TextView(requireContext());
            emptyView.setText(getString(R.string.status_no_data));
            emptyView.setTextSize(14);
            emptyView.setTextColor(ContextCompat.getColor(requireContext(), R.color.label_3));
            emptyView.setPadding(16, 16, 16, 16);
            containerMethods.addView(emptyView);
            return;
        }

        for (int i = 0; i < methods.size(); i++) {
            BatteryOriginDetector.DetectionMethod method = methods.get(i);
            if (i > 0) {
                View separator = new View(requireContext());
                separator.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                separator.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.separator));
                containerMethods.addView(separator);
            }

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(16, 12, 16, 12);

            TextView tvName = new TextView(requireContext());
            tvName.setText(method.name);
            tvName.setTextSize(14);
            tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.label_2));
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView tvValue = new TextView(requireContext());
            tvValue.setText(method.value);
            tvValue.setTextSize(14);
            tvValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.label));

            row.addView(tvName);
            row.addView(tvValue);
            containerMethods.addView(row);
        }
    }

    private void updateHistoryUI(List<BatteryOriginRecord> records) {
        if (records == null || records.isEmpty()) {
            tvHistoryEyebrow.setVisibility(View.GONE);
            cardHistory.setVisibility(View.GONE);
            return;
        }

        tvHistoryEyebrow.setVisibility(View.VISIBLE);
        cardHistory.setVisibility(View.VISIBLE);

        containerHistory.removeAllViews();

        // 最多显示最近5条
        int count = Math.min(records.size(), 5);
        for (int i = 0; i < count; i++) {
            BatteryOriginRecord record = records.get(i);
            if (i > 0) {
                View separator = new View(requireContext());
                separator.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                separator.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.separator));
                containerHistory.addView(separator);
            }

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(16, 12, 16, 12);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            // 左侧：时间 + 结论
            LinearLayout leftPart = new LinearLayout(requireContext());
            leftPart.setOrientation(LinearLayout.VERTICAL);
            leftPart.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView tvTime = new TextView(requireContext());
            tvTime.setText(viewModel.formatRecordTime(record.timestamp));
            tvTime.setTextSize(13);
            tvTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.label_2));

            TextView tvConclusion = new TextView(requireContext());
            tvConclusion.setText(record.conclusion != null ? record.conclusion : "--");
            tvConclusion.setTextSize(12);
            tvConclusion.setTextColor(ContextCompat.getColor(requireContext(), R.color.label_3));
            tvConclusion.setMaxLines(1);

            leftPart.addView(tvTime);
            leftPart.addView(tvConclusion);

            // 右侧：原装/非原装 + 置信度
            TextView tvBadge = new TextView(requireContext());
            if (record.isOriginal) {
                tvBadge.setText(getString(R.string.result_original));
                tvBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.confidence_high));
            } else {
                tvBadge.setText(getString(R.string.result_replaced));
                tvBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.confidence_low));
            }
            tvBadge.setTextSize(13);
            tvBadge.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvConf = new TextView(requireContext());
            tvConf.setText(String.format(Locale.getDefault(), "%d%%", record.confidence));
            tvConf.setTextSize(12);
            tvConf.setTextColor(ContextCompat.getColor(requireContext(), R.color.label_3));

            LinearLayout rightPart = new LinearLayout(requireContext());
            rightPart.setOrientation(LinearLayout.VERTICAL);
            rightPart.setGravity(android.view.Gravity.END);
            rightPart.addView(tvBadge);
            rightPart.addView(tvConf);

            row.addView(leftPart);
            row.addView(rightPart);
            containerHistory.addView(row);
        }

        // 隐藏空状态
        tvHistoryEmpty.setVisibility(View.GONE);
    }

    private void shareReport() {
        viewModel.generateReport();
    }

    private void launchShareIntent(String reportContent) {
        try {
            Intent shareIntent = viewModel.createShareIntent(reportContent);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.origin_share_title)));
        } catch (Exception e) {
            // 分享失败静默处理
        }
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }
}
