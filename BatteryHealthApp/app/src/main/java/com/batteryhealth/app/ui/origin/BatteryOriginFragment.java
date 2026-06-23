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
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.BatteryOriginDetector;

import java.util.List;

public class BatteryOriginFragment extends Fragment {

    private TextView tvOriginResult;
    private TextView tvOriginConfidence;
    private TextView tvOriginConclusion;
    private TextView tvManufactureDate;
    private TextView tvSerialNumber;
    private TextView tvManufacturer;
    private TextView tvOemInfo;
    private TextView tvTechnology;
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
        tvManufacturer = view.findViewById(R.id.tv_manufacturer);
        tvOemInfo = view.findViewById(R.id.tv_oem_info);
        tvTechnology = view.findViewById(R.id.tv_technology);
        tvHealthStatus = view.findViewById(R.id.tv_health_status);
        tvCycleCount = view.findViewById(R.id.tv_cycle_count);
        containerMethods = view.findViewById(R.id.container_methods);
        btnDetect = view.findViewById(R.id.btn_detect);

        btnDetect.setOnClickListener(v -> performDetection());
    }

    private void animateEntry(View view) {
        try {
            Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
            view.startAnimation(fadeUp);
        } catch (Exception e) {
            // 动画加载失败静默处理
        }
    }

    private void performDetection() {
        if (btnDetect != null) {
            btnDetect.setEnabled(false);
            btnDetect.setText(getString(R.string.status_detecting));
        }

        new Thread(() -> {
            try {
                // Pass BatteryDataManager for comprehensive data
                if (getActivity() instanceof com.batteryhealth.app.MainActivity) {
                    BatteryDataManager bdm = ((com.batteryhealth.app.MainActivity) getActivity()).getBatteryDataManager();
                    if (bdm != null && originDetector != null) {
                        originDetector.setBatteryDataManager(bdm);
                    }
                }
                // Refresh BatteryDataManager data before passing to detector
                if (originDetector != null) {
                    BatteryDataManager bdm = originDetector.getBatteryDataManager();
                    if (bdm != null) {
                        bdm.refreshFromStickyIntent();
                    }
                }
                BatteryOriginDetector.OriginResult result = originDetector != null ? originDetector.detect() : null;

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        updateUI(result);
                        if (btnDetect != null) {
                            btnDetect.setEnabled(true);
                            btnDetect.setText(getString(R.string.label_detect_battery));
                        }
                    });
                }
            } catch (Exception e) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        if (btnDetect != null) {
                            btnDetect.setEnabled(true);
                            btnDetect.setText(getString(R.string.label_detect_battery));
                        }
                    });
                }
            }
        }).start();
    }

    private void updateUI(BatteryOriginDetector.OriginResult result) {
        if (!isAdded()) return;
        try {
            if (result != null && result.isOriginal) {
                if (tvOriginResult != null) tvOriginResult.setText(getString(R.string.result_original));
                if (tvOriginResult != null)
                    tvOriginResult.setTextColor(ContextCompat.getColor(requireContext(), R.color.health_a_plus));
            } else {
                if (tvOriginResult != null) tvOriginResult.setText(getString(R.string.result_replaced));
                if (tvOriginResult != null)
                    tvOriginResult.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange));
            }

            if (tvOriginConfidence != null)
                tvOriginConfidence.setText(String.format("置信度：%d%%", result != null ? result.confidence : 0));
            if (tvOriginConclusion != null)
                tvOriginConclusion.setText(result != null && result.conclusion != null ? result.conclusion : "--");

            if (tvManufactureDate != null)
                tvManufactureDate.setText(result != null && result.manufactureDate != null ? result.manufactureDate : "--");
            if (tvSerialNumber != null)
                tvSerialNumber.setText(result != null && result.serialNumber != null ? result.serialNumber : "--");
            if (tvHealthStatus != null)
                tvHealthStatus.setText(result != null && result.healthStatus != null ? result.healthStatus : "--");
            if (tvCycleCount != null)
                tvCycleCount.setText(result != null && result.cycleCount != null ? result.cycleCount : "--");
            if (tvManufacturer != null)
                tvManufacturer.setText(result != null && result.manufacturer != null ? result.manufacturer : "--");
            if (tvOemInfo != null)
                tvOemInfo.setText(result != null && result.oemInfo != null ? result.oemInfo : "--");
            if (tvTechnology != null)
                tvTechnology.setText(result != null && result.technology != null ? result.technology : "--");

            updateDetectionMethods(result != null ? result.detectionMethods : null);
        } catch (Exception e) {
            // 静默处理
        }
    }

    private void updateDetectionMethods(List<BatteryOriginDetector.DetectionMethod> methods) {
        if (containerMethods == null) return;
        try {
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
        } catch (Exception e) {
            // 静默处理
        }
    }
}