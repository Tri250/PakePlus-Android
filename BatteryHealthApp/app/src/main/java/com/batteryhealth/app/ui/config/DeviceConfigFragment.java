package com.batteryhealth.app.ui.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.utils.DeviceInfoManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DeviceConfigFragment extends Fragment {

    private static final String PREFS_CONFIG = "config_prefs";
    private static final String PREF_HEALTH_ALERT = "health_decay_alert";

    private TextView tvDeviceName, tvDeviceModel, tvAndroidVersion, tvProcessor, tvRam, tvStorage,
            tvScreen, tvActivationDate, tvUsageDays, tvActivationSource, tvAvailableRam,
            tvAvailableStorage, tvNetworkType;
    private Switch switchHealthAlert;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private DeviceInfoManager deviceInfoManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_device_config, container, false);
        initViews(view);
        animateEntry(view);
        // 优先复用 MainActivity 中的 DeviceInfoManager，保持数据源一致
        if (getActivity() instanceof MainActivity) {
            deviceInfoManager = ((MainActivity) getActivity()).getDeviceInfoManager();
        }
        if (deviceInfoManager == null) {
            deviceInfoManager = new DeviceInfoManager(requireContext().getApplicationContext());
        }
        loadDataAsync();
        return view;
    }

    private void initViews(View view) {
        tvDeviceName = view.findViewById(R.id.tv_device_name);
        tvDeviceModel = view.findViewById(R.id.tv_device_model);
        tvAndroidVersion = view.findViewById(R.id.tv_android_version);
        tvProcessor = view.findViewById(R.id.tv_processor);
        tvRam = view.findViewById(R.id.tv_ram);
        tvStorage = view.findViewById(R.id.tv_storage);
        tvScreen = view.findViewById(R.id.tv_screen);
        tvActivationDate = view.findViewById(R.id.tv_activation_date);
        tvUsageDays = view.findViewById(R.id.tv_usage_days);
        tvActivationSource = view.findViewById(R.id.tv_activation_source);
        tvAvailableRam = view.findViewById(R.id.tv_available_ram);
        tvAvailableStorage = view.findViewById(R.id.tv_available_storage);
        tvNetworkType = view.findViewById(R.id.tv_network_type);
        switchHealthAlert = view.findViewById(R.id.switch_health_alert);

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_CONFIG, Context.MODE_PRIVATE);
        switchHealthAlert.setChecked(prefs.getBoolean(PREF_HEALTH_ALERT, true));
        switchHealthAlert.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_HEALTH_ALERT, isChecked).apply();
        });
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (ioExecutor != null) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException ie) {
                ioExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 异步调用 DeviceInfoManager 收集完整设备信息（CPU/内存/存储/屏幕/网络/激活日期等），
     * 然后回到主线程刷新 UI。所有 sysfs / 反射调用都在后台线程。
     */
    private void loadDataAsync() {
        if (deviceInfoManager == null) {
            loadFallback();
            return;
        }
        ioExecutor.submit(() -> {
            final DeviceConfig config;
            try {
                config = deviceInfoManager.getDeviceConfig();
            } catch (Exception e) {
                handler.post(this::loadFallback);
                return;
            }
            handler.post(() -> {
                if (!isAdded() || config == null) return;
                applyConfig(config);
            });
        });
    }

    private void applyConfig(DeviceConfig config) {
        // 1. 设备名称 / 型号：优先使用 DeviceDatabaseManager 中的营销名
        String marketName = deviceInfoManager.getMarketModelName();
        String displayName;
        if (marketName != null && !marketName.isEmpty() && !marketName.equals(config.getModel())) {
            displayName = config.getBrand() + " " + marketName;
        } else {
            displayName = config.getBrand() + " " + config.getModel();
        }
        tvDeviceName.setText(displayName);
        tvDeviceModel.setText(config.getDevice() + " / " + config.getProduct());

        // 2. Android 版本
        tvAndroidVersion.setText(String.format(Locale.getDefault(), "%s (API %d)",
                config.getAndroidVersion(), config.getSdkVersion()));

        // 3. 处理器：优先使用 getProcessorInfo() 解析后的营销名
        String cpu = deviceInfoManager.getProcessorInfo();
        if (cpu == null || cpu.isEmpty()) cpu = config.getCpuInfo();
        if (cpu != null && !cpu.isEmpty()) {
            int cores = Math.max(config.getCpuCores(), 1);
            int freqMhz = config.getCpuFreqMax();
            String freqStr = freqMhz > 0 ? " · " + freqMhz + " MHz" : "";
            tvProcessor.setText(String.format(Locale.getDefault(), "%s · %d 核%s", cpu, cores, freqStr));
        } else {
            tvProcessor.setText(getString(R.string.status_not_recognized));
        }

        // 4. 内存（MB → GB 显示）
        long totalMemBytes = config.getTotalMemory() * 1024L * 1024L;
        long availMemBytes = config.getAvailableMemory() * 1024L * 1024L;
        tvRam.setText(formatSize(totalMemBytes));
        tvAvailableRam.setText(formatSize(availMemBytes));

        // 5. 存储（GB → GB）
        long totalStorageBytes = config.getTotalStorage() * 1024L * 1024L * 1024L;
        long availStorageBytes = config.getAvailableStorage() * 1024L * 1024L * 1024L;
        tvStorage.setText(formatSize(totalStorageBytes));
        tvAvailableStorage.setText(formatSize(availStorageBytes));

        // 6. 屏幕
        tvScreen.setText(String.format(Locale.getDefault(), "%d × %d · %s",
                config.getScreenWidth(), config.getScreenHeight(),
                config.getFormattedScreenSize()));

        // 7. 激活日期
        if (config.getActivationDate() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            tvActivationDate.setText(sdf.format(new Date(config.getActivationDate())));
        } else {
            tvActivationDate.setText("--");
        }
        int usageDays = config.getUsageDays();
        tvUsageDays.setText(usageDays > 0
                ? (usageDays + " 天")
                : getString(R.string.status_unknown));
        // 激活来源：取 ActivationDateHelper 的 source 文本
        String sourceText = mapActivationSource(config.getActivationSource());
        tvActivationSource.setText(sourceText);

        // 8. 网络类型（DeviceInfoManager 已根据 Android 版本区分 NetworkCapabilities / NetworkInfo）
        String net = config.getNetworkType();
        tvNetworkType.setText(net != null && !net.isEmpty() ? net : getString(R.string.status_unknown));
    }

    private String mapActivationSource(String source) {
        if (source == null) return getString(R.string.status_unknown);
        switch (source) {
            case "electronic_warranty_card": return "电子保卡";
            case "system_first_boot_time":
            case "first_unlock_time": return "系统首次启动";
            case "device_policy_manager": return "设备管理";
            case "gms_first_install": return "GMS 安装时间";
            case "system_framework_install": return "系统框架";
            case "app_first_install": return "应用安装时间";
            case "app_data_directory": return "应用数据目录";
            default: return source;
        }
    }

    /**
     * 兜底方案：异步加载失败时显示 Build 基本信息，避免页面空白。
     */
    private void loadFallback() {
        if (!isAdded()) return;
        tvDeviceName.setText(Build.BRAND + " " + Build.MODEL);
        tvDeviceModel.setText(Build.MODEL);
        tvAndroidVersion.setText(Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        tvProcessor.setText(Build.HARDWARE);
        tvRam.setText(getString(R.string.status_not_recognized));
        tvStorage.setText(getString(R.string.status_not_recognized));
        tvScreen.setText(getString(R.string.status_not_recognized));
        tvActivationDate.setText(getString(R.string.status_unknown));
        tvUsageDays.setText(getString(R.string.status_unknown));
        tvActivationSource.setText(getString(R.string.status_unknown));
        tvAvailableRam.setText(getString(R.string.status_not_recognized));
        tvAvailableStorage.setText(getString(R.string.status_not_recognized));
        tvNetworkType.setText(getTelephonyNetworkType());
    }

    private String getTelephonyNetworkType() {
        try {
            TelephonyManager tm = (TelephonyManager) requireContext().getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return getString(R.string.status_unknown);
            int networkType = tm.getNetworkType();
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    return "2G";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_LTE:
                    return "4G";
                case TelephonyManager.NETWORK_TYPE_NR:
                    return "5G";
                default:
                    return getString(R.string.status_unknown);
            }
        } catch (Exception e) {
            return getString(R.string.status_unknown);
        }
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
