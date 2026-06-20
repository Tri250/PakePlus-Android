package com.batteryhealth.app.ui.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
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

import com.batteryhealth.app.R;

import java.io.File;
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

    private void loadData() {
        tvDeviceName.setText(Build.BRAND + " " + Build.MODEL);
        tvDeviceModel.setText(Build.MODEL);
        tvAndroidVersion.setText(Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        tvProcessor.setText(Build.HARDWARE);

        long totalRam = getTotalRam();
        tvRam.setText(formatSize(totalRam));

        long totalStorage = getTotalStorage();
        tvStorage.setText(formatSize(totalStorage));

        tvScreen.setText(getScreenResolution());

        long activationTime = getActivationTime();
        tvActivationDate.setText(formatDate(activationTime));
        tvUsageDays.setText(getUsageDays(activationTime) + " 天");
        tvActivationSource.setText(getString(R.string.source_internal));

        long availableRam = getAvailableRam();
        tvAvailableRam.setText(formatSize(availableRam));

        long availableStorage = getAvailableStorage();
        tvAvailableStorage.setText(formatSize(availableStorage));

        tvNetworkType.setText(getNetworkType());
    }

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
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        return stat.getTotalBytes();
    }

    private long getAvailableStorage() {
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        return stat.getAvailableBytes();
    }

    private String getScreenResolution() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return dm.widthPixels + " x " + dm.heightPixels;
    }

    private long getActivationTime() {
        File file = new File(Environment.getRootDirectory(), "build.prop");
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
        TelephonyManager tm = (TelephonyManager) requireContext().getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) return "Unknown";
        int networkType = tm.getNetworkType();
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "2G";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "3G";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "4G";
            case TelephonyManager.NETWORK_TYPE_NR:
                return "5G";
            default:
                return "Unknown";
        }
    }
}
