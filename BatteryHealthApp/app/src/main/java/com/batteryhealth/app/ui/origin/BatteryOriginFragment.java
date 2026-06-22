package com.batteryhealth.app.ui.origin;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.batteryhealth.app.utils.BatteryOriginDetector;

import java.util.List;

public class BatteryOriginFragment extends Fragment {

    private static final String TAG = "BatteryOriginFragment";

    private TextView tvOriginResult;
    private TextView tvOriginConfidence;
    private TextView tvOriginConclusion;
    private TextView tvManufactureDate;
    private TextView tvSerialNumber;
    private TextView tvHealthStatus;
    private TextView tvCycleCount;
    private LinearLayout containerMethods;
    private Button btnDetect;

    private BatteryOriginDetector originDetector;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean detectionInProgress = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_battery_origin, container, false);
        initViews(view);
        animateEntry(view);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 在 onResume 中触发检测，避免 onCreateView 阶段 Fragment 尚未完全 attached
        if (!detectionInProgress) {
            triggerDetection();
        }
    }

    private void initViews(View view) {
        tvOriginResult = view.findViewById(R.id.tv_origin_result);
        tvOriginConfidence = view.findViewById(R.id.tv_origin_confidence);
        tvOriginConclusion = view.findViewById(R.id.tv_origin_conclusion);
        tvManufactureDate = view.findViewById(R.id.tv_manufacture_date);
        tvSerialNumber = view.findViewById(R.id.tv_serial_number);
        tvHealthStatus = view.findViewById(R.id.tv_health_status);
        tvCycleCount = view.findViewById(R.id.tv_cycle_count);
        containerMethods = view.findViewById(R.id.container_methods);
        btnDetect = view.findViewById(R.id.btn_detect);

        btnDetect.setOnClickListener(v -> triggerDetection());
    }

    private void animateEntry(View view) {
        try {
            Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
            view.startAnimation(fadeUp);
        } catch (Exception ignored) {
        }
    }

    private void triggerDetection() {
        if (!isAdded() || btnDetect == null) return;

        Context ctx = getContext();
        if (ctx == null) return;

        detectionInProgress = true;
        btnDetect.setEnabled(false);
        btnDetect.setText(getString(R.string.status_detecting));

        originDetector = new BatteryOriginDetector(ctx);

        new Thread(() -> {
            BatteryOriginDetector.OriginResult result = null;
            try {
                result = originDetector.detect();
            } catch (Exception e) {
                Log.e(TAG, "Detection failed", e);
            }
            final BatteryOriginDetector.OriginResult finalResult = result;

            // 使用 Handler.post 而非 requireActivity().runOnUiThread，避免 Fragment detach 后崩溃
            mainHandler.post(() -> {
                if (!isAdded()) {
                    detectionInProgress = false;
                    return;
                }
                if (finalResult != null) {
                    updateUI(finalResult);
                } else {
                    showDetectionError();
                }
                if (btnDetect != null) {
                    btnDetect.setEnabled(true);
                    btnDetect.setText(getString(R.string.label_detect_battery));
                }
                detectionInProgress = false;
            });
        }).start();
    }

    private void showDetectionError() {
        if (tvOriginResult != null) {
            tvOriginResult.setText(getString(R.string.status_no_data));
        }
        if (tvOriginConfidence != null) {
            tvOriginConfidence.setText("置信度：0%");
        }
        if (tvOriginConclusion != null) {
            tvOriginConclusion.setText("检测失败，请重试");
        }
    }

    private void updateUI(BatteryOriginDetector.OriginResult result) {
        if (!isAdded()) return;

        if (result.isOriginal) {
            tvOriginResult.setText(getString(R.string.result_original));
            tvOriginResult.setTextColor(ContextCompat.getColor(requireContext(), R.color.health_a_plus));
        } else {
            tvOriginResult.setText(getString(R.string.result_replaced));
            tvOriginResult.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange));
        }

        tvOriginConfidence.setText(String.format("置信度：%d%%", result.confidence));
        tvOriginConclusion.setText(result.conclusion);

        tvManufactureDate.setText(result.manufactureDate != null ? result.manufactureDate : "--");
        tvSerialNumber.setText(result.serialNumber != null ? result.serialNumber : "--");
        tvHealthStatus.setText(result.healthStatus != null ? result.healthStatus : "--");
        tvCycleCount.setText(result.cycleCount != null ? result.cycleCount : "--");

        updateDetectionMethods(result.detectionMethods);
    }

    private void updateDetectionMethods(List<BatteryOriginDetector.DetectionMethod> methods) {
        if (!isAdded() || containerMethods == null) return;

        containerMethods.removeAllViews();

        Context ctx = getContext();
        if (ctx == null) return;

        if (methods == null || methods.isEmpty()) {
            TextView emptyView = new TextView(ctx);
            emptyView.setText(getString(R.string.status_no_data));
            emptyView.setTextSize(14f);
            emptyView.setTextColor(ContextCompat.getColor(ctx, R.color.label_3));
            emptyView.setPadding(16, 16, 16, 16);
            containerMethods.addView(emptyView);
            return;
        }

        for (int i = 0; i < methods.size(); i++) {
            BatteryOriginDetector.DetectionMethod method = methods.get(i);
            if (i > 0) {
                View separator = new View(ctx);
                separator.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                separator.setBackgroundColor(ContextCompat.getColor(ctx, R.color.separator));
                containerMethods.addView(separator);
            }

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(16, 12, 16, 12);

            TextView tvName = new TextView(ctx);
            tvName.setText(method.name);
            tvName.setTextSize(14f);
            tvName.setTextColor(ContextCompat.getColor(ctx, R.color.label_2));
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView tvValue = new TextView(ctx);
            tvValue.setText(method.value);
            tvValue.setTextSize(14f);
            tvValue.setTextColor(ContextCompat.getColor(ctx, R.color.label));

            row.addView(tvName);
            row.addView(tvValue);
            containerMethods.addView(row);
        }
    }
}
