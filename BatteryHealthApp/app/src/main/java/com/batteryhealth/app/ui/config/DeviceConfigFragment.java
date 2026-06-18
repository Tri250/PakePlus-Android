package com.batteryhealth.app.ui.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.service.BatteryMonitorService;
import com.batteryhealth.app.utils.DeviceInfoManager;

/**
 * 设备配置Fragment
 */
public class DeviceConfigFragment extends Fragment {
    
    private static final String TAG = "DeviceConfigFragment";
    
    private DeviceInfoManager deviceInfoManager;

    private TextView tvActivationSource;
    private TextView tvAvailableMemory;
    private TextView tvAvailableStorage;
    private TextView tvNetworkType;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_device_config, container, false);
        } catch (Throwable t) {
            Log.e(TAG, "Error inflating layout: " + t.getMessage(), t);
            return createErrorView(t);
        }
    }

    /**
     * 创建友好的错误页：标题 + 提示文案 + "重试" 按钮。
     */
    private View createErrorView(Throwable t) {
        Context ctx = null;
        try { ctx = getContext(); } catch (Throwable ignored) {}
        if (ctx == null) ctx = requireActivity().getApplicationContext();

        android.widget.LinearLayout root = new android.widget.LinearLayout(ctx);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (40 * ctx.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad * 2, pad, pad);
        try {
            root.setBackgroundColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.ios_background));
        } catch (Throwable ignored) {
            root.setBackgroundColor(0xFFEFEFF4);
        }

        android.widget.TextView tvTitle = new android.widget.TextView(ctx);
        tvTitle.setText("界面加载失败");
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        try {
            tvTitle.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.ios_label));
        } catch (Throwable ignored) {
            tvTitle.setTextColor(0xFF1C1C1E);
        }
        tvTitle.setGravity(android.view.Gravity.CENTER);
        root.addView(tvTitle);

        android.widget.TextView tvMsg = new android.widget.TextView(ctx);
        tvMsg.setText("数据尚未就绪，请点击下方按钮重试。");
        tvMsg.setTextSize(14);
        try {
            tvMsg.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.ios_secondary_label));
        } catch (Throwable ignored) {
            tvMsg.setTextColor(0xFF3C3C43);
        }
        tvMsg.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams msgLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = (int) (12 * ctx.getResources().getDisplayMetrics().density);
        root.addView(tvMsg, msgLp);

        android.widget.Button btnRetry = new android.widget.Button(ctx);
        btnRetry.setText("重 试");
        btnRetry.setAllCaps(false);
        btnRetry.setTextSize(15);
        try {
            btnRetry.setBackgroundColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.ios_blue));
        } catch (Throwable ignored) {
            btnRetry.setBackgroundColor(0xFF0A84FF);
        }
        btnRetry.setTextColor(0xFFFFFFFF);
        android.widget.LinearLayout.LayoutParams btnLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = (int) (28 * ctx.getResources().getDisplayMetrics().density);
        int btnH = (int) (44 * ctx.getResources().getDisplayMetrics().density);
        btnRetry.setMinHeight(btnH);
        int btnPad = (int) (28 * ctx.getResources().getDisplayMetrics().density);
        btnRetry.setPadding(btnPad, 0, btnPad, 0);
        root.addView(btnRetry, btnLp);

        btnRetry.setOnClickListener(v -> {
            try {
                View newView = onCreateView(LayoutInflater.from(ctx), (ViewGroup) v.getParent(), null);
                if (newView != null && v.getParent() instanceof ViewGroup) {
                    ViewGroup parent = (ViewGroup) v.getParent();
                    int idx = parent.indexOfChild(root);
                    parent.removeView(root);
                    parent.addView(newView, idx);
                    try { onViewCreated(newView, null); } catch (Throwable ignored) {}
                    try { animateCardsEntry(newView); } catch (Throwable ignored) {}
                }
            } catch (Throwable ex) {
                Log.e(TAG, "Retry failed: " + ex.getMessage(), ex);
            }
        });
        return root;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            if (getActivity() instanceof MainActivity) {
                deviceInfoManager = ((MainActivity) getActivity()).getDeviceInfoManager();
            }
            
            initViews(view);
            animateCardsEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }
    
    private void initViews(View view) {
        try {
            if (deviceInfoManager == null) {
                setDefaultValues(view);
                return;
            }
            
            DeviceConfig config = deviceInfoManager.getDeviceConfig();
            if (config == null) {
                setDefaultValues(view);
                return;
            }
            
            TextView tvDeviceName = view.findViewById(R.id.tv_device_name);
            TextView tvAndroidVersion = view.findViewById(R.id.tv_android_version);
            TextView tvProcessor = view.findViewById(R.id.tv_processor);
            TextView tvMemory = view.findViewById(R.id.tv_memory);
            TextView tvStorage = view.findViewById(R.id.tv_storage);
            TextView tvScreen = view.findViewById(R.id.tv_screen);
            TextView tvActivation = view.findViewById(R.id.tv_activation);
            TextView tvUsageDays = view.findViewById(R.id.tv_usage_days);
            tvActivationSource = view.findViewById(R.id.tv_activation_source);
            tvAvailableMemory = view.findViewById(R.id.tv_available_memory);
            tvAvailableStorage = view.findViewById(R.id.tv_available_storage);
            tvNetworkType = view.findViewById(R.id.tv_network_type);
            
            if (tvDeviceName != null) {
                tvDeviceName.setText(config.getFullModelName());
            }
            if (tvAndroidVersion != null) {
                tvAndroidVersion.setText(config.getAndroidCodename());
            }
            if (tvProcessor != null) {
                tvProcessor.setText(deviceInfoManager.getProcessorInfo());
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
                tvActivation.setText(config.getActivationDateStr() != null ? config.getActivationDateStr() : "--");
            }
            if (tvUsageDays != null) {
                tvUsageDays.setText(config.getUsageDays() + " 天");
            }

            // 激活来源与可信度
            if (tvActivationSource != null) {
                String sourceText = deviceInfoManager.getActivationSourceText();
                float confidence = deviceInfoManager.getActivationConfidence();
                tvActivationSource.setText(String.format("%s (可信度 %.0f%%)", sourceText, confidence * 100));
            }
            // 可用内存
            if (tvAvailableMemory != null) {
                long availMem = config.getAvailableMemory();
                if (availMem > 0) {
                    tvAvailableMemory.setText(availMem >= 1024
                        ? String.format("%.1f GB", availMem / 1024.0)
                        : availMem + " MB");
                } else {
                    tvAvailableMemory.setText("--");
                }
            }
            // 可用存储
            if (tvAvailableStorage != null) {
                long availStorage = config.getAvailableStorage();
                if (availStorage > 0) {
                    tvAvailableStorage.setText(availStorage + " GB");
                } else {
                    tvAvailableStorage.setText("--");
                }
            }
            // 网络类型
            if (tvNetworkType != null) {
                String networkType = config.getNetworkType();
                tvNetworkType.setText(networkType != null ? networkType : "--");
            }

            initHealthAlertSwitch(view);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage());
            setDefaultValues(view);
        }
    }

    /**
     * 初始化健康度衰减预警开关
     */
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
    
    private void animateCardsEntry(View view) {
        try {
            if (!(view instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup root = (android.view.ViewGroup) view;
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
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
            Log.d(TAG, "Liquid glass card animation skipped: " + e.getMessage());
        }
    }

    private void setDefaultValues(View view) {
        TextView tvDeviceName = view.findViewById(R.id.tv_device_name);
        TextView tvAndroidVersion = view.findViewById(R.id.tv_android_version);
        TextView tvProcessor = view.findViewById(R.id.tv_processor);
        TextView tvMemory = view.findViewById(R.id.tv_memory);
        TextView tvStorage = view.findViewById(R.id.tv_storage);
        TextView tvScreen = view.findViewById(R.id.tv_screen);
        TextView tvActivation = view.findViewById(R.id.tv_activation);
        TextView tvUsageDays = view.findViewById(R.id.tv_usage_days);
        
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
        if (tvNetworkType != null) tvNetworkType.setText("--");
    }
}