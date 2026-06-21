package com.batteryhealth.app.ui.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
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

public class DeviceConfigFragment extends Fragment {

    private static final String PREFS_CONFIG = "config_prefs";
    private static final String PREF_HEALTH_ALERT = "health_decay_alert";

    private TextView tvDeviceName, tvDeviceModel, tvAndroidVersion, tvProcessor, tvRam, tvStorage,
            tvScreen, tvActivationDate, tvUsageDays, tvActivationSource, tvAvailableRam,
            tvAvailableStorage, tvNetworkType;
    private Switch switchHealthAlert;

    private DeviceInfoManager deviceInfoManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_device_config, container, false);
        initViews(view);
        animateEntry(view);
        loadData();
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
        tvDeviceName = null;
        tvDeviceModel = null;
        tvAndroidVersion = null;
        tvProcessor = null;
        tvRam = null;
        tvStorage = null;
        tvScreen = null;
        tvActivationDate = null;
        tvUsageDays = null;
        tvActivationSource = null;
        tvAvailableRam = null;
        tvAvailableStorage = null;
        tvNetworkType = null;
        switchHealthAlert = null;
    }

    private DeviceInfoManager getDeviceInfoManager() {
        if (deviceInfoManager != null) return deviceInfoManager;
        if (getActivity() instanceof MainActivity) {
            deviceInfoManager = ((MainActivity) getActivity()).getDeviceInfoManager();
        }
        if (deviceInfoManager == null) {
            deviceInfoManager = new DeviceInfoManager(requireContext());
        }
        return deviceInfoManager;
    }

    private void loadData() {
        DeviceInfoManager dim = getDeviceInfoManager();
        DeviceConfig config = dim.getDeviceConfig();

        // 设备名称：优先使用机型数据库的营销名称
        String marketName = dim.getMarketModelName();
        if (marketName != null && !marketName.equals(android.os.Build.MODEL)) {
            tvDeviceName.setText(marketName);
        } else if (config != null) {
            tvDeviceName.setText(config.getFullModelName());
        } else {
            tvDeviceName.setText(android.os.Build.BRAND + " " + android.os.Build.MODEL);
        }

        // 设备型号：优先使用营销名称，否则使用 Build.MODEL
        String modelDisplay = dim.getMarketModelName();
        tvDeviceModel.setText(modelDisplay != null ? modelDisplay : android.os.Build.MODEL);

        // Android 版本
        if (config != null) {
            tvAndroidVersion.setText(config.getAndroidCodename() + " (API " + android.os.Build.VERSION.SDK_INT + ")");
        } else {
            tvAndroidVersion.setText(android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")");
        }

        // 处理器：使用 DeviceInfoManager 的多路 fallback（数据库 > sysprop > /proc/cpuinfo > Build.HARDWARE）
        tvProcessor.setText(dim.getProcessorInfo());

        // 内存：使用 DeviceConfig 的营销规格取整
        if (config != null) {
            tvRam.setText(config.getFormattedMemory());
            tvAvailableRam.setText(formatSize(config.getAvailableMemory() * 1024 * 1024));
        } else {
            long totalRam = getTotalRam();
            tvRam.setText(formatSize(totalRam));
            tvAvailableRam.setText(formatSize(getAvailableRam()));
        }

        // 存储：使用 DeviceConfig 的格式化存储
        if (config != null) {
            tvStorage.setText(config.getFormattedStorage());
            long availStorageBytes = config.getAvailableStorage() * 1024 * 1024 * 1024;
            tvAvailableStorage.setText(formatSize(availStorageBytes > 0 ? availStorageBytes : getAvailableStorage()));
        } else {
            tvStorage.setText(formatSize(getTotalStorage()));
            tvAvailableStorage.setText(formatSize(getAvailableStorage()));
        }

        // 屏幕：使用 DeviceConfig 的屏幕信息
        if (config != null && config.getScreenWidth() > 0) {
            String screenInfo = config.getScreenResolution();
            if (config.getScreenSize() > 0) {
                screenInfo += " · " + config.getFormattedScreenSize();
            }
            tvScreen.setText(screenInfo);
        } else {
            tvScreen.setText(getScreenResolution());
        }

        // 激活日期：使用 DeviceInfoManager 的激活日期检测
        if (config != null && config.getActivationDate() > 0) {
            tvActivationDate.setText(formatDate(config.getActivationDate()));
            tvUsageDays.setText(config.getUsageDays() + " 天");
            String sourceText = config.getActivationSource();
            if (sourceText != null && !sourceText.isEmpty() && !"unknown".equals(sourceText)) {
                tvActivationSource.setText(sourceText);
            } else {
                tvActivationSource.setText(getString(R.string.source_internal));
            }
        } else {
            long activationTime = getActivationTime();
            tvActivationDate.setText(formatDate(activationTime));
            tvUsageDays.setText(getUsageDays(activationTime) + " 天");
            tvActivationSource.setText(getString(R.string.source_internal));
        }

        // 网络类型：使用 DeviceConfig 的网络信息
        if (config != null && config.getNetworkType() != null && !config.getNetworkType().isEmpty()) {
            tvNetworkType.setText(config.getNetworkType());
        } else {
            tvNetworkType.setText(getNetworkType());
        }
    }

    // === 以下为 fallback 方法，仅在 DeviceConfig 不可用时使用 ===

    private long getTotalRam() {
        android.app.ActivityManager am = (android.app.ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(mi);
            return mi.totalMem;
        }
        return 0;
    }

    private long getAvailableRam() {
        android.app.ActivityManager am = (android.app.ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(mi);
            return mi.availMem;
        }
        return 0;
    }

    private long getTotalStorage() {
        android.os.StatFs stat = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
        return stat.getTotalBytes();
    }

    private long getAvailableStorage() {
        android.os.StatFs stat = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
        return stat.getAvailableBytes();
    }

    private String getScreenResolution() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return dm.widthPixels + " x " + dm.heightPixels;
    }

    private long getActivationTime() {
        java.io.File file = new java.io.File(android.os.Environment.getRootDirectory(), "build.prop");
        long time = file.lastModified();
        if (time == 0) {
            time = System.currentTimeMillis() - 86400000L * 365;
        }
        return time;
    }

    private String formatDate(long time) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(time));
    }

    private long getUsageDays(long activationTime) {
        return (System.currentTimeMillis() - activationTime) / (1000 * 60 * 60 * 24);
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String getNetworkType() {
        try {
            android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) requireContext().getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return "Unknown";
            int networkType = tm.getNetworkType();
            switch (networkType) {
                case android.telephony.TelephonyManager.NETWORK_TYPE_GPRS:
                case android.telephony.TelephonyManager.NETWORK_TYPE_EDGE:
                case android.telephony.TelephonyManager.NETWORK_TYPE_CDMA:
                case android.telephony.TelephonyManager.NETWORK_TYPE_1xRTT:
                case android.telephony.TelephonyManager.NETWORK_TYPE_IDEN:
                    return "2G";
                case android.telephony.TelephonyManager.NETWORK_TYPE_UMTS:
                case android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_0:
                case android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_A:
                case android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA:
                case android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA:
                case android.telephony.TelephonyManager.NETWORK_TYPE_HSPA:
                case android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_B:
                case android.telephony.TelephonyManager.NETWORK_TYPE_EHRPD:
                case android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP:
                    return "3G";
                case android.telephony.TelephonyManager.NETWORK_TYPE_LTE:
                    return "4G";
                case android.telephony.TelephonyManager.NETWORK_TYPE_NR:
                    return "5G";
                default:
                    return "Unknown";
            }
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
