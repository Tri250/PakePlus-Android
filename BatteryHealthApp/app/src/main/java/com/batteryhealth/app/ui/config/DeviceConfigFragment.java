package com.batteryhealth.app.ui.config;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.service.BatteryMonitorService;
import com.batteryhealth.app.ui.bugreport.BugreportGuideActivity;
import com.batteryhealth.app.ui.bugreport.BugreportUploadActivity;
import com.batteryhealth.app.utils.DeviceInfoManager;

import java.util.Locale;
import java.util.Map;

/**
 * 设备配置Fragment
 */
public class DeviceConfigFragment extends Fragment {

    private static final String TAG = "DeviceConfigFragment";

    private DeviceInfoManager deviceInfoManager;

    private TextView tvDeviceName;
    private TextView tvAndroidVersion;
    private TextView tvProcessor;
    private TextView tvMemory;
    private TextView tvStorage;
    private TextView tvScreen;
    private TextView tvActivation;
    private TextView tvUsageDays;
    private TextView tvActivationSource;
    private TextView tvAvailableMemory;
    private TextView tvAvailableStorage;

    private TextView tvConfigScore;
    private LinearLayout layoutSuppliers;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_device_config, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
            return createErrorView(e);
        }
    }

    private View createErrorView(Exception e) {
        Context ctx = getContext();
        if (ctx == null) {
            ctx = requireActivity();
        }
        android.widget.TextView errorView = new android.widget.TextView(ctx);
        String message = "界面加载失败\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
        errorView.setTextSize(16);
        errorView.setPadding(40, 100, 40, 40);
        errorView.setBackgroundColor(ContextCompat.getColor(ctx, R.color.background));
        return errorView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            if (getActivity() instanceof MainActivity) {
                deviceInfoManager = ((MainActivity) getActivity()).getDeviceInfoManager();
            }

            bindViews(view);
            setDefaultValues();
            initHealthAlertSwitch(view);
            initBugreportSection(view);
            animateCardsEntry(view);

            loadDeviceConfigAsync();
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }

    private void bindViews(View view) {
        tvDeviceName = view.findViewById(R.id.tv_device_name);
        tvAndroidVersion = view.findViewById(R.id.tv_android_version);
        tvProcessor = view.findViewById(R.id.tv_processor);
        tvMemory = view.findViewById(R.id.tv_memory);
        tvStorage = view.findViewById(R.id.tv_storage);
        tvScreen = view.findViewById(R.id.tv_screen);
        tvActivation = view.findViewById(R.id.tv_activation);
        tvUsageDays = view.findViewById(R.id.tv_usage_days);
        tvActivationSource = view.findViewById(R.id.tv_activation_source);
        tvAvailableMemory = view.findViewById(R.id.tv_available_memory);
        tvAvailableStorage = view.findViewById(R.id.tv_available_storage);

        tvConfigScore = view.findViewById(R.id.tv_config_score);
        layoutSuppliers = view.findViewById(R.id.layout_suppliers);
    }

    private void loadDeviceConfigAsync() {
        if (deviceInfoManager == null) {
            return;
        }
        deviceInfoManager.getDeviceConfigAsync(new DeviceInfoManager.DeviceConfigCallback() {
            @Override
            public void onConfigLoaded(DeviceConfig config) {
                if (!isAdded()) return;
                updateViews(config);
            }

            @Override
            public void onConfigLoadFailed(Exception e) {
                Log.e(TAG, "Failed to load device config", e);
            }
        });
    }

    private void updateViews(DeviceConfig config) {
        if (config == null || !isAdded()) return;

        try {
            if (tvDeviceName != null) {
                tvDeviceName.setText(config.getFullModelName());
            }
            if (tvAndroidVersion != null) {
                tvAndroidVersion.setText(config.getAndroidCodename());
            }
            if (tvProcessor != null) {
                tvProcessor.setText(deviceInfoManager != null ? deviceInfoManager.getProcessorInfo() : "--");
            }
            if (tvMemory != null) {
                tvMemory.setText(config.getFormattedMemory());
            }
            if (tvStorage != null) {
                tvStorage.setText(config.getFormattedStorage());
            }
            if (tvScreen != null) {
                tvScreen.setText(config.getScreenResolution());
            }
            if (tvActivation != null) {
                String dateStr = config.getActivationDateStr();
                tvActivation.setText(dateStr != null && !"--".equals(dateStr) ? dateStr : "--");
            }
            if (tvUsageDays != null) {
                int usageDays = config.getUsageDays();
                tvUsageDays.setText(usageDays >= 0 ? usageDays + " 天" : "--");
            }

            if (tvActivationSource != null) {
                String sourceText = config.getActivationSource();
                float confidence = config.getActivationConfidence();
                tvActivationSource.setText(String.format(Locale.getDefault(), "%s (可信度 %.0f%%)", getActivationSourceLabel(sourceText), confidence * 100));
            }
            if (tvAvailableMemory != null) {
                long availMem = config.getAvailableMemory();
                if (availMem > 0) {
                    tvAvailableMemory.setText(availMem >= 1024
                            ? String.format(Locale.getDefault(), "%.1f GB", availMem / 1024.0)
                            : availMem + " MB");
                } else {
                    tvAvailableMemory.setText("--");
                }
            }
            if (tvAvailableStorage != null) {
                long availStorage = config.getAvailableStorage();
                if (availStorage > 0) {
                    tvAvailableStorage.setText(availStorage + " GB");
                } else {
                    tvAvailableStorage.setText("--");
                }
            }

            float score = calculateConfigScore(config);
            if (tvConfigScore != null) {
                tvConfigScore.setText(String.format(Locale.getDefault(), getString(R.string.config_score_format), score));
                tvConfigScore.setTextColor(ContextCompat.getColor(requireContext(), score >= 8.0f ? R.color.primary_green : score >= 5.0f ? R.color.orange : R.color.red));
            }

            renderSuppliers(config);
        } catch (Exception e) {
            Log.e(TAG, "Error updating views: " + e.getMessage());
        }
    }

    private float calculateConfigScore(DeviceConfig config) {
        try {
            float score = 0;
            long totalMem = config.getTotalMemory();
            if (totalMem >= 12288) score += 3.0f;
            else if (totalMem >= 8192) score += 2.5f;
            else if (totalMem >= 6144) score += 2.0f;
            else if (totalMem >= 4096) score += 1.5f;
            else score += 1.0f;

            long totalStorage = config.getTotalStorage();
            if (totalStorage >= 512) score += 2.0f;
            else if (totalStorage >= 256) score += 1.7f;
            else if (totalStorage >= 128) score += 1.4f;
            else if (totalStorage >= 64) score += 1.0f;
            else score += 0.6f;

            int cpuFreqMax = config.getCpuFreqMax();
            if (cpuFreqMax >= 3200) score += 3.0f;
            else if (cpuFreqMax >= 2800) score += 2.5f;
            else if (cpuFreqMax >= 2400) score += 2.0f;
            else if (cpuFreqMax >= 2000) score += 1.5f;
            else score += 1.0f;

            int screenPixels = config.getScreenWidth() * config.getScreenHeight();
            if (screenPixels >= 3000000) score += 2.0f;
            else if (screenPixels >= 2000000) score += 1.6f;
            else if (screenPixels >= 1500000) score += 1.2f;
            else score += 0.8f;

            return Math.min(10.0f, score);
        } catch (Exception e) {
            return 6.0f;
        }
    }

    private void renderSuppliers(DeviceConfig config) {
        if (layoutSuppliers == null || !isAdded()) return;
        layoutSuppliers.removeAllViews();
        Map<String, String> suppliers = deviceInfoManager != null ? deviceInfoManager.getComponentSuppliers(config) : null;
        if (suppliers == null || suppliers.isEmpty()) {
            addSupplierRow(layoutSuppliers, getString(R.string.supplier_screen), "参考数据");
            addSupplierRow(layoutSuppliers, getString(R.string.supplier_battery), "ATL / 宁德时代");
            addSupplierRow(layoutSuppliers, getString(R.string.supplier_storage), "三星 / 海力士 / 铠侠");
            return;
        }
        for (Map.Entry<String, String> entry : suppliers.entrySet()) {
            addSupplierRow(layoutSuppliers, entry.getKey(), entry.getValue());
        }
    }

    private void addSupplierRow(LinearLayout parent, String name, String value) {
        if (!isAdded()) return;
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_supplier_row, parent, false);
        TextView tvName = row.findViewById(R.id.tv_supplier_name);
        TextView tvValue = row.findViewById(R.id.tv_supplier_value);
        if (tvName != null) tvName.setText(name);
        if (tvValue != null) tvValue.setText(value);
        parent.addView(row);
    }

    private void initHealthAlertSwitch(View view) {
        try {
            SwitchCompat switchAlert = view.findViewById(R.id.switch_health_alert);
            if (switchAlert == null) {
                return;
            }

            SharedPreferences prefs = requireContext().getSharedPreferences(
                    BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean(BatteryMonitorService.PREF_ALERT_ENABLED, true);
            switchAlert.setChecked(enabled);

            switchAlert.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(BatteryMonitorService.PREF_ALERT_ENABLED, isChecked).apply();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing health alert switch: " + e.getMessage());
        }
    }

    private void initBugreportSection(View view) {
        try {
            View btnOpenGuide = view.findViewById(R.id.btn_open_guide);
            View btnOpenUpload = view.findViewById(R.id.btn_open_upload);

            if (btnOpenGuide != null) {
                btnOpenGuide.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(requireContext(), BugreportGuideActivity.class));
                    } catch (Exception e) {
                        Log.e(TAG, "启动 Bugreport 引导页失败", e);
                    }
                });
            }
            if (btnOpenUpload != null) {
                btnOpenUpload.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(requireContext(), BugreportUploadActivity.class));
                    } catch (Exception e) {
                        Log.e(TAG, "启动本地解析页失败", e);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing bugreport section: " + e.getMessage());
        }
    }

    private void animateCardsEntry(View view) {
        try {
            if (!(view instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup root = (android.view.ViewGroup) view;
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if (child.getId() == R.id.view_pager) continue;
                child.setAlpha(0f);
                child.setTranslationY(60f);
                child.setScaleX(0.94f);
                child.setScaleY(0.94f);
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(650)
                    .setStartDelay(i * 100L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f))
                    .start();
            }
        } catch (Exception e) {
            Log.d(TAG, "Card animation skipped: " + e.getMessage());
        }
    }

    private String getActivationSourceLabel(String source) {
        if (source == null || "unknown".equals(source)) return "未知";
        switch (source) {
            case "electronic_warranty_card":
                return "电子保卡";
            case "system_first_boot_time":
                return "系统首次开机";
            case "device_policy_manager":
                return "设备管理策略";
            case "gms_first_install":
                return "Google 服务首次安装";
            case "system_framework_install":
                return "系统框架安装";
            case "app_first_install":
                return "本应用首次安装";
            case "app_data_directory":
                return "应用数据目录";
            default:
                return source;
        }
    }

    private void setDefaultValues() {
        if (tvDeviceName != null) tvDeviceName.setText("--");
        if (tvAndroidVersion != null) tvAndroidVersion.setText("--");
        if (tvProcessor != null) tvProcessor.setText("--");
        if (tvMemory != null) tvMemory.setText("--");
        if (tvStorage != null) tvStorage.setText("--");
        if (tvScreen != null) tvScreen.setText("--");
        if (tvActivation != null) tvActivation.setText("--");
        if (tvUsageDays != null) tvUsageDays.setText("--");
        if (tvActivationSource != null) tvActivationSource.setText("--");
        if (tvAvailableMemory != null) tvAvailableMemory.setText("--");
        if (tvAvailableStorage != null) tvAvailableStorage.setText("--");
        if (tvConfigScore != null) tvConfigScore.setText("--");
    }
}
