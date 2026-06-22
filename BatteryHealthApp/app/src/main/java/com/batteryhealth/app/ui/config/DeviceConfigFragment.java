package com.batteryhealth.app.ui.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
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
        DeviceInfoManager manager = getDeviceInfoManager();
        if (manager != null) {
            manager.getDeviceConfigAsync(new DeviceInfoManager.DeviceConfigCallback() {
                @Override
                public void onConfigLoaded(DeviceConfig config) {
                    renderConfig(config);
                }

                @Override
                public void onConfigLoadFailed(Exception e) {
                    renderFallback();
                }
            });
        } else {
            renderFallback();
        }
    }

    private DeviceInfoManager getDeviceInfoManager() {
        if (getActivity() instanceof MainActivity) {
            return ((MainActivity) getActivity()).getDeviceInfoManager();
        }
        return null;
    }

    private void renderConfig(DeviceConfig config) {
        if (!isAdded()) return;
        tvDeviceName.setText(config.getBrand() + " " + config.getModel());
        tvDeviceModel.setText(config.getModel());
        tvAndroidVersion.setText(config.getAndroidVersion() + " (API " + config.getSdkVersion() + ")");
        tvProcessor.setText(config.getCpuInfo());
        tvRam.setText(formatSize(config.getTotalMemory()));
        tvStorage.setText(formatSize(config.getTotalStorage()));
        tvScreen.setText(config.getScreenWidth() + " x " + config.getScreenHeight());
        tvActivationDate.setText(config.getActivationDateStr());
        tvUsageDays.setText(config.getUsageDays() + " 天");
        tvActivationSource.setText(config.getActivationSource());
        tvAvailableRam.setText(formatSize(config.getAvailableMemory()));
        tvAvailableStorage.setText(formatSize(config.getAvailableStorage()));
        tvNetworkType.setText(config.getNetworkType());
    }

    private void renderFallback() {
        if (!isAdded()) return;
        tvDeviceName.setText(Build.BRAND + " " + Build.MODEL);
        tvDeviceModel.setText(Build.MODEL);
        tvAndroidVersion.setText(Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        tvProcessor.setText(Build.HARDWARE);
        tvRam.setText(formatSize(getTotalRam()));
        tvStorage.setText(formatSize(getTotalStorage()));
        tvScreen.setText(getScreenResolution());
        tvActivationDate.setText("--");
        tvUsageDays.setText("--");
        tvActivationSource.setText(getString(R.string.source_internal));
        tvAvailableRam.setText(formatSize(getAvailableRam()));
        tvAvailableStorage.setText(formatSize(getAvailableStorage()));
        tvNetworkType.setText("Unknown");
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

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
