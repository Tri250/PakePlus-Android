package com.batteryhealth.app.ui.origin;

import android.os.Bundle;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_battery_origin, container, false);
        initViews(view);
        animateEntry(view);
        originDetector = new BatteryOriginDetector(requireContext());
        return view;
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

        btnDetect.setOnClickListener(v -> performDetection());
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    private void performDetection() {
        btnDetect.setEnabled(false);
        btnDetect.setText(getString(R.string.status_detecting));

        new Thread(() -> {
            BatteryOriginDetector.OriginResult result = originDetector.detect();

            requireActivity().runOnUiThread(() -> {
                updateUI(result);
                btnDetect.setEnabled(true);
                btnDetect.setText(getString(R.string.label_detect_battery));
            });
        }).start();
    }

    private void updateUI(BatteryOriginDetector.OriginResult result) {
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
        containerMethods.removeAllViews();

        if (methods == null || methods.isEmpty()) {
            TextView emptyView = new TextView(requireContext());
            emptyView.setText(getString(R.string.status_no_data));
            emptyView.setTextSize(14f);
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
            tvName.setTextSize(14f);
            tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.label_2));
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView tvValue = new TextView(requireContext());
            tvValue.setText(method.value);
            tvValue.setTextSize(14f);
            tvValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.label));

            row.addView(tvName);
            row.addView(tvValue);
            containerMethods.addView(row);
        }
    }
}