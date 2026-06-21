package com.batteryhealth.app.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.service.BatteryMonitorService;
import com.batteryhealth.app.utils.ThemeManager;
import java.util.Locale;

/**
 * 设置页面：暗色模式、目标充电电量、通知开关、刷新频率、关于
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = BatteryMonitorService.PREFS_NAME;
    private SharedPreferences prefs;

    private RadioGroup rgThemeMode;
    private SeekBar seekBarTargetLevel;
    private TextView tvTargetLevel;
    private Switch switchNotification;
    private LinearLayout sectionRefreshRate;
    private LinearLayout sectionAbout;
    private TextView tvRefreshRate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        initViews();
        loadSettings();
        setupListeners();
    }

    private void initViews() {
        rgThemeMode = findViewById(R.id.rg_theme_mode);
        seekBarTargetLevel = findViewById(R.id.seekbar_target_level);
        tvTargetLevel = findViewById(R.id.tv_target_level);
        switchNotification = findViewById(R.id.switch_notification);
        sectionRefreshRate = findViewById(R.id.section_refresh_rate);
        sectionAbout = findViewById(R.id.section_about);
        tvRefreshRate = findViewById(R.id.tv_refresh_rate);

        // 设置返回按钮
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }

    private void loadSettings() {
        // 暗色模式
        int themeMode = ThemeManager.getThemeMode(this);
        switch (themeMode) {
            case ThemeManager.MODE_LIGHT:
                rgThemeMode.check(R.id.rb_light);
                break;
            case ThemeManager.MODE_DARK:
                rgThemeMode.check(R.id.rb_dark);
                break;
            default:
                rgThemeMode.check(R.id.rb_system);
                break;
        }

        // 目标充电电量
        int targetLevel = prefs.getInt("target_battery_level", 80);
        seekBarTargetLevel.setProgress(targetLevel);
        tvTargetLevel.setText(String.format(Locale.getDefault(), "%d%%", targetLevel));

        // 通知开关
        switchNotification.setChecked(prefs.getBoolean(BatteryMonitorService.PREF_ALERT_ENABLED, true));

        // 刷新频率
        int refreshRate = prefs.getInt("refresh_rate_seconds", 2);
        tvRefreshRate.setText(String.format(Locale.getDefault(), "%d 秒", refreshRate));
    }

    private void setupListeners() {
        // 暗色模式
        rgThemeMode.setOnCheckedChangeListener((group, checkedId) -> {
            int mode;
            if (checkedId == R.id.rb_light) {
                mode = ThemeManager.MODE_LIGHT;
            } else if (checkedId == R.id.rb_dark) {
                mode = ThemeManager.MODE_DARK;
            } else {
                mode = ThemeManager.MODE_SYSTEM;
            }
            ThemeManager.setThemeMode(this, mode);
            Toast.makeText(this, "主题已切换，重启应用生效", Toast.LENGTH_SHORT).show();
        });

        // 目标充电电量
        seekBarTargetLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvTargetLevel.setText(String.format(Locale.getDefault(), "%d%%", progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt("target_battery_level", seekBar.getProgress()).apply();
            }
        });

        // 通知开关
        switchNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(BatteryMonitorService.PREF_ALERT_ENABLED, isChecked).apply();
        });

        // 刷新频率
        sectionRefreshRate.setOnClickListener(v -> showRefreshRateDialog());

        // 关于
        sectionAbout.setOnClickListener(v -> showAboutDialog());
    }

    private void showRefreshRateDialog() {
        String[] options = {"1 秒", "2 秒", "3 秒", "5 秒", "10 秒"};
        int[] values = {1, 2, 3, 5, 10};
        int current = prefs.getInt("refresh_rate_seconds", 2);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("数据刷新频率")
                .setSingleChoiceItems(options, indexOf(values, current), (dialog, which) -> {
                    prefs.edit().putInt("refresh_rate_seconds", values[which]).apply();
                    tvRefreshRate.setText(options[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int indexOf(int[] arr, int value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) return i;
        }
        return 1; // default to 2 seconds
    }

    private void showAboutDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("关于电池健康")
                .setMessage("电池健康 v4.9.5\n\n实时监测电池健康状态\n充电智能保护\n性能全面分析\n\n© 2026 BatteryHealth")
                .setPositiveButton("确定", null)
                .show();
    }
}