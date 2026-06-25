package com.batteryhealth.app.ui.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.BuildConfig;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.utils.DataExportManager;
import com.batteryhealth.app.utils.FragmentErrorViewHelper;
import com.batteryhealth.app.utils.ThreadExecutor;

import java.io.File;
import java.util.List;

public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_NOTIFICATION = "settings_notification";
    private static final String KEY_REFRESH_RATE = "settings_refresh_rate";
    private static final String KEY_DATA_RETENTION = "settings_data_retention";
    private static final String KEY_DARK_MODE = "settings_dark_mode";
    private static final String KEY_CHARGE_COMPLETE = "settings_charge_complete";
    private static final String KEY_TEMP_WARNING = "settings_temp_warning";

    private static final int REFRESH_1S = 1000;
    private static final int REFRESH_2S = 2000;
    private static final int REFRESH_5S = 5000;
    private static final int REFRESH_10S = 10000;

    private static final int RETENTION_7D = 7;
    private static final int RETENTION_30D = 30;
    private static final int RETENTION_90D = 90;
    private static final int RETENTION_FOREVER = -1;

    private static final int DARK_FOLLOW_SYSTEM = 0;
    private static final int DARK_LIGHT = 1;
    private static final int DARK_DARK = 2;

    private static final int REQUEST_SAVE_FILE = 1001;

    private SharedPreferences prefs;

    private SwitchCompat switchNotification;
    private SwitchCompat switchChargeComplete;
    private SwitchCompat switchTempWarning;
    private TextView tvRefreshRateValue;
    private TextView tvDataRetentionValue;
    private TextView tvDarkModeValue;
    private TextView tvVersion;

    private DataExportManager exportManager;
    private int pendingExportFormat = DataExportManager.FORMAT_CSV;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.fragment_settings, container, false);
            initViews(view);
            initPreferences();
            setupClickListeners(view);
            loadSettings();
            return view;
        } catch (Exception e) {
            Log.e(TAG, "Error creating view", e);
            Context ctx = getContext();
            if (ctx == null && container != null) ctx = container.getContext();
            return FragmentErrorViewHelper.createErrorView(ctx, e);
        }
    }

    private void initViews(View view) {
        switchNotification = view.findViewById(R.id.switch_notification);
        switchChargeComplete = view.findViewById(R.id.switch_charge_complete);
        switchTempWarning = view.findViewById(R.id.switch_temp_warning);
        tvRefreshRateValue = view.findViewById(R.id.tv_refresh_rate_value);
        tvDataRetentionValue = view.findViewById(R.id.tv_data_retention_value);
        tvDarkModeValue = view.findViewById(R.id.tv_dark_mode_value);
        tvVersion = view.findViewById(R.id.tv_version);

        tvVersion.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));

        Context ctx = getContext();
        if (ctx != null) {
            exportManager = new DataExportManager(ctx);
        }
    }

    private void initPreferences() {
        Context ctx = getContext();
        if (ctx != null) {
            prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    private void setupClickListeners(View view) {
        View notificationItem = view.findViewById(R.id.settings_notification);
        if (notificationItem != null) {
            notificationItem.setOnClickListener(v -> openNotificationSettings());
        }

        View batteryOptItem = view.findViewById(R.id.settings_battery_optimization);
        if (batteryOptItem != null) {
            batteryOptItem.setOnClickListener(v -> openBatteryOptimizationSettings());
        }

        View refreshRateItem = view.findViewById(R.id.settings_refresh_rate);
        if (refreshRateItem != null) {
            refreshRateItem.setOnClickListener(v -> showRefreshRateDialog());
        }

        View dataRetentionItem = view.findViewById(R.id.settings_data_retention);
        if (dataRetentionItem != null) {
            dataRetentionItem.setOnClickListener(v -> showDataRetentionDialog());
        }

        View darkModeItem = view.findViewById(R.id.settings_dark_mode);
        if (darkModeItem != null) {
            darkModeItem.setOnClickListener(v -> showDarkModeDialog());
        }

        View chargeCompleteItem = view.findViewById(R.id.settings_charge_complete);
        if (chargeCompleteItem != null) {
            chargeCompleteItem.setOnClickListener(v -> {
                boolean newState = !switchChargeComplete.isChecked();
                switchChargeComplete.setChecked(newState);
                saveBooleanSetting(KEY_CHARGE_COMPLETE, newState);
            });
        }

        View tempWarningItem = view.findViewById(R.id.settings_temp_warning);
        if (tempWarningItem != null) {
            tempWarningItem.setOnClickListener(v -> {
                boolean newState = !switchTempWarning.isChecked();
                switchTempWarning.setChecked(newState);
                saveBooleanSetting(KEY_TEMP_WARNING, newState);
            });
        }

        View aboutItem = view.findViewById(R.id.settings_about);
        if (aboutItem != null) {
            aboutItem.setOnClickListener(v -> showAboutDialog());
        }

        View exportItem = view.findViewById(R.id.settings_export);
        if (exportItem != null) {
            exportItem.setOnClickListener(v -> exportAllData());
        }

        View clearCacheItem = view.findViewById(R.id.settings_clear_cache);
        if (clearCacheItem != null) {
            clearCacheItem.setOnClickListener(v -> showClearCacheDialog());
        }
    }

    private void loadSettings() {
        if (prefs == null) return;

        boolean notificationEnabled = prefs.getBoolean(KEY_NOTIFICATION, true);
        switchNotification.setChecked(notificationEnabled);

        boolean chargeCompleteEnabled = prefs.getBoolean(KEY_CHARGE_COMPLETE, true);
        switchChargeComplete.setChecked(chargeCompleteEnabled);

        boolean tempWarningEnabled = prefs.getBoolean(KEY_TEMP_WARNING, true);
        switchTempWarning.setChecked(tempWarningEnabled);

        int refreshRate = prefs.getInt(KEY_REFRESH_RATE, REFRESH_2S);
        tvRefreshRateValue.setText(getRefreshRateLabel(refreshRate));

        int retention = prefs.getInt(KEY_DATA_RETENTION, RETENTION_90D);
        tvDataRetentionValue.setText(getRetentionLabel(retention));

        int darkMode = prefs.getInt(KEY_DARK_MODE, DARK_FOLLOW_SYSTEM);
        tvDarkModeValue.setText(getDarkModeLabel(darkMode));
    }

    private String getRefreshRateLabel(int value) {
        switch (value) {
            case REFRESH_1S: return getString(R.string.settings_refresh_1s);
            case REFRESH_2S: return getString(R.string.settings_refresh_2s);
            case REFRESH_5S: return getString(R.string.settings_refresh_5s);
            case REFRESH_10S: return getString(R.string.settings_refresh_10s);
            default: return getString(R.string.settings_refresh_2s);
        }
    }

    private String getRetentionLabel(int value) {
        switch (value) {
            case RETENTION_7D: return getString(R.string.settings_retention_7d);
            case RETENTION_30D: return getString(R.string.settings_retention_30d);
            case RETENTION_90D: return getString(R.string.settings_retention_90d);
            case RETENTION_FOREVER: return getString(R.string.settings_retention_forever);
            default: return getString(R.string.settings_retention_90d);
        }
    }

    private String getDarkModeLabel(int value) {
        switch (value) {
            case DARK_LIGHT: return getString(R.string.settings_dark_light);
            case DARK_DARK: return getString(R.string.settings_dark_dark);
            case DARK_FOLLOW_SYSTEM:
            default: return getString(R.string.settings_dark_follow_system);
        }
    }

    private void saveBooleanSetting(String key, boolean value) {
        if (prefs != null) {
            prefs.edit().putBoolean(key, value).apply();
        }
    }

    private void saveIntSetting(String key, int value) {
        if (prefs != null) {
            prefs.edit().putInt(key, value).apply();
        }
    }

    private void openNotificationSettings() {
        Context ctx = getContext();
        if (ctx == null) return;

        saveBooleanSetting(KEY_NOTIFICATION, !switchNotification.isChecked());

        try {
            Intent intent = new Intent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
            } else {
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + ctx.getPackageName()));
            }
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open notification settings", e);
            Toast.makeText(ctx, getString(R.string.status_init_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void openBatteryOptimizationSettings() {
        Context ctx = getContext();
        if (ctx == null) return;

        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + ctx.getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open battery optimization settings", e);
            Toast.makeText(ctx, getString(R.string.status_init_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void showRefreshRateDialog() {
        Context ctx = getContext();
        if (ctx == null || prefs == null) return;

        int current = prefs.getInt(KEY_REFRESH_RATE, REFRESH_2S);
        final int[] values = {REFRESH_1S, REFRESH_2S, REFRESH_5S, REFRESH_10S};
        String[] labels = {
                getString(R.string.settings_refresh_1s),
                getString(R.string.settings_refresh_2s),
                getString(R.string.settings_refresh_5s),
                getString(R.string.settings_refresh_10s)
        };

        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                selectedIndex = i;
                break;
            }
        }

        final int finalSelectedIndex = selectedIndex;
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.settings_refresh_rate)
                .setSingleChoiceItems(labels, finalSelectedIndex, (dialog, which) -> {
                    int selected = values[which];
                    saveIntSetting(KEY_REFRESH_RATE, selected);
                    tvRefreshRateValue.setText(getRefreshRateLabel(selected));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showDataRetentionDialog() {
        Context ctx = getContext();
        if (ctx == null || prefs == null) return;

        int current = prefs.getInt(KEY_DATA_RETENTION, RETENTION_90D);
        final int[] values = {RETENTION_7D, RETENTION_30D, RETENTION_90D, RETENTION_FOREVER};
        String[] labels = {
                getString(R.string.settings_retention_7d),
                getString(R.string.settings_retention_30d),
                getString(R.string.settings_retention_90d),
                getString(R.string.settings_retention_forever)
        };

        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.settings_data_retention)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    int selected = values[which];
                    saveIntSetting(KEY_DATA_RETENTION, selected);
                    tvDataRetentionValue.setText(getRetentionLabel(selected));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showDarkModeDialog() {
        Context ctx = getContext();
        if (ctx == null || prefs == null) return;

        int current = prefs.getInt(KEY_DARK_MODE, DARK_FOLLOW_SYSTEM);
        final int[] values = {DARK_FOLLOW_SYSTEM, DARK_LIGHT, DARK_DARK};
        String[] labels = {
                getString(R.string.settings_dark_follow_system),
                getString(R.string.settings_dark_light),
                getString(R.string.settings_dark_dark)
        };

        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.settings_dark_mode)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    int selected = values[which];
                    saveIntSetting(KEY_DARK_MODE, selected);
                    tvDarkModeValue.setText(getDarkModeLabel(selected));
                    applyDarkMode(selected);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void applyDarkMode(int mode) {
        int nightMode;
        switch (mode) {
            case DARK_LIGHT:
                nightMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case DARK_DARK:
                nightMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
                break;
            case DARK_FOLLOW_SYSTEM:
            default:
                nightMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    private void showAboutDialog() {
        Context ctx = getContext();
        if (ctx == null) return;

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.settings_about)
                .setMessage(getString(R.string.settings_version, BuildConfig.VERSION_NAME))
                .setPositiveButton(R.string.settings_privacy_policy, (dialog, which) -> {
                    Toast.makeText(ctx, R.string.settings_privacy_policy, Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(R.string.settings_user_agreement, (dialog, which) -> {
                    Toast.makeText(ctx, R.string.settings_user_agreement, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    private void exportAllData() {
        Context ctx = getContext();
        if (ctx == null) return;

        final String[] formatLabels = {"CSV 格式", "JSON 格式"};
        final int[] formatValues = {DataExportManager.FORMAT_CSV, DataExportManager.FORMAT_JSON};

        new AlertDialog.Builder(ctx)
                .setTitle("选择导出格式")
                .setSingleChoiceItems(formatLabels, 0, (dialog, which) -> {
                    pendingExportFormat = formatValues[which];
                })
                .setPositiveButton("导出", (dialog, which) -> {
                    dialog.dismiss();
                    startExportFilePicker();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void startExportFilePicker() {
        Context ctx = getContext();
        if (ctx == null || exportManager == null) return;

        String fileName = exportManager.generateFileName(pendingExportFormat);
        String mimeType = pendingExportFormat == DataExportManager.FORMAT_JSON
                ? "application/json" : "text/csv";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        try {
            startActivityForResult(intent, REQUEST_SAVE_FILE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open file picker", e);
            Toast.makeText(ctx, "打开文件选择器失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SAVE_FILE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                performExport(uri);
            }
        }
    }

    private void performExport(Uri targetUri) {
        Context ctx = getContext();
        if (ctx == null || exportManager == null) return;

        Toast.makeText(ctx, "开始导出数据...", Toast.LENGTH_SHORT).show();

        ThreadExecutor.execute(() -> {
            try {
                BatteryHealthApplication app = (BatteryHealthApplication) requireActivity().getApplication();
                if (app == null || app.getDatabase() == null) {
                    ThreadExecutor.runOnMain(() ->
                            Toast.makeText(ctx, "数据库未就绪", Toast.LENGTH_SHORT).show());
                    return;
                }

                List<BatteryInfo> batteryList = app.getDatabase().batteryInfoDao().getAll();
                List<PowerHistory> powerList = app.getDatabase().powerHistoryDao().getAll();

                exportManager.exportBatteryData(batteryList, powerList, targetUri,
                        pendingExportFormat, new DataExportManager.ExportCallback() {
                            @Override
                            public void onProgress(int progress, int total) {
                            }

                            @Override
                            public void onSuccess(Uri uri, String fileName) {
                                ThreadExecutor.runOnMain(() ->
                                        Toast.makeText(ctx, "导出成功: " + fileName, Toast.LENGTH_LONG).show());
                            }

                            @Override
                            public void onError(String message) {
                                ThreadExecutor.runOnMain(() ->
                                        Toast.makeText(ctx, "导出失败: " + message, Toast.LENGTH_LONG).show());
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Export failed", e);
                ThreadExecutor.runOnMain(() ->
                        Toast.makeText(ctx, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showClearCacheDialog() {
        Context ctx = getContext();
        if (ctx == null) return;

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.dialog_clear_cache_title)
                .setMessage(R.string.dialog_clear_cache_message)
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> clearCache())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void clearCache() {
        Context ctx = getContext();
        if (ctx == null) return;

        ThreadExecutor.execute(() -> {
            try {
                File cacheDir = ctx.getCacheDir();
                deleteDir(cacheDir);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(ctx, R.string.cache_cleared, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear cache", e);
            }
        });
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        return dir != null && dir.delete();
    }
}
