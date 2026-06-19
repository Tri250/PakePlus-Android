package com.batteryhealth.app.ui.battery;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.service.BatteryMonitorService;
import com.batteryhealth.app.service.ChargingMonitorService;
import com.batteryhealth.app.utils.BatteryDataManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 电池健康Fragment
 * 
 * 注意：电池数据由BatteryMonitorService统一处理并通过BatteryDataManager获取
 * 不再在此Fragment中注册广播接收器，避免重复监听
 */
public class BatteryHealthFragment extends Fragment {
    
    private static final String TAG = "BatteryHealthFragment";
    private static final long UPDATE_INTERVAL = 5000; // 5秒更新一次UI
    public static final String PREFS_NAME = "battery_health_prefs";
    public static final String PREF_SHOW_NOTIFICATION = "show_background_notification";

    private TextView tvHealthPercentage;
    private TextView tvHealthGrade;
    private TextView tvHealthStatus;
    private ProgressBar progressHealth;

    private TextView tvCapacity;
    private TextView tvCycleCount;
    private TextView tvTemperature;
    private TextView tvVoltage;
    private TextView tvBatterySource;
    private TextView tvTechnology;
    private TextView tvBatteryLevel;
    private TextView tvChargingStatus;
    private TextView tvCurrentNow;
    private View btnCalibrate;
    private SwitchCompat switchNotification;
    private View btnChargingHistory;

    private BatteryDataManager batteryDataManager;
    private SharedPreferences prefs;
    private Handler mainHandler;
    private boolean isRunning = false;
    
    // 定时更新UI的Runnable
    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            // 每次刷新UI前先从系统读取最新基本数据（电量、温度、电压等）
            if (batteryDataManager != null) {
                batteryDataManager.refreshFromStickyIntent();
            }

            updateUI();
            if (mainHandler != null) {
                mainHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        }
    };
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_battery_health, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
            // 创建错误提示视图，避免完全空白，同时显示异常信息便于排查
            return createErrorView(e);
        }
    }

    private View createErrorView(Exception e) {
        android.widget.TextView errorView = new android.widget.TextView(requireContext());
        String message = getString(R.string.error_view_load_failed, e.getClass().getSimpleName(), e.getMessage());
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_label));
        errorView.setTextSize(16);
        errorView.setPadding(40, 100, 40, 40);
        errorView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_background));
        return errorView;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            mainHandler = new Handler(Looper.getMainLooper());
            prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            // 获取电池数据管理器
            if (getActivity() instanceof MainActivity) {
                batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
            }

            initViews(view);
            updateUI();
            animateCardsEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        isRunning = true;

        // 进入页面时刷新一次完整数据（容量、循环次数、电池来源等）
        if (batteryDataManager != null) {
            batteryDataManager.refreshAllDataAsync();
        }

        // 启动定时更新，刷新UI和基本电池信息
        if (mainHandler != null) {
            mainHandler.post(updateRunnable);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isRunning = false;
        // 停止定时更新
        if (mainHandler != null) {
            mainHandler.removeCallbacks(updateRunnable);
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isRunning = false;
        if (mainHandler != null) {
            mainHandler.removeCallbacks(updateRunnable);
        }
    }
    
    private void initViews(View view) {
        try {
            // 健康度相关
            tvHealthPercentage = view.findViewById(R.id.tv_health_percentage);
            tvHealthGrade = view.findViewById(R.id.tv_health_grade);
            tvHealthStatus = view.findViewById(R.id.tv_health_status);
            progressHealth = view.findViewById(R.id.progress_health);
            
            // 详细信息
            tvCapacity = view.findViewById(R.id.tv_capacity);
            tvCycleCount = view.findViewById(R.id.tv_cycle_count);
            tvTemperature = view.findViewById(R.id.tv_temperature);
            tvVoltage = view.findViewById(R.id.tv_voltage);
            tvBatterySource = view.findViewById(R.id.tv_battery_source);
            tvTechnology = view.findViewById(R.id.tv_technology);
            tvBatteryLevel = view.findViewById(R.id.tv_battery_level);
            tvChargingStatus = view.findViewById(R.id.tv_charging_status);
            tvCurrentNow = view.findViewById(R.id.tv_current_now);
            btnCalibrate = view.findViewById(R.id.btn_calibrate);
            switchNotification = view.findViewById(R.id.switch_notification);
            btnChargingHistory = view.findViewById(R.id.btn_charging_history);

            if (btnCalibrate != null) {
                btnCalibrate.setOnClickListener(v -> showCalibrateDialog());
            }

            if (btnChargingHistory != null) {
                btnChargingHistory.setOnClickListener(v -> showChargingHistoryDialog());
            }

            // 初始化通知开关
            if (switchNotification != null) {
                boolean showNotification = prefs.getBoolean(PREF_SHOW_NOTIFICATION, true);
                switchNotification.setChecked(showNotification);
                switchNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    prefs.edit().putBoolean(PREF_SHOW_NOTIFICATION, isChecked).apply();
                    updateServiceNotificationState(isChecked);
                });
            }

            // 设置默认值
            setDefaultValues();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage());
        }
    }
    
    private void updateServiceNotificationState(boolean showNotification) {
        try {
            Context context = requireContext();
            if (showNotification) {
                Intent batteryIntent = new Intent(context, BatteryMonitorService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(batteryIntent);
                } else {
                    context.startService(batteryIntent);
                }

                Intent chargingIntent = new Intent(context, ChargingMonitorService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(chargingIntent);
                } else {
                    context.startService(chargingIntent);
                }
            } else {
                Intent stopBatteryForeground = new Intent(context, BatteryMonitorService.class);
                stopBatteryForeground.setAction("STOP_FOREGROUND");
                context.startService(stopBatteryForeground);

                Intent stopChargingForeground = new Intent(context, ChargingMonitorService.class);
                stopChargingForeground.setAction("STOP_FOREGROUND");
                context.startService(stopChargingForeground);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating service notification state: " + e.getMessage());
        }
    }

    private void setDefaultValues() {
        if (tvHealthPercentage != null) tvHealthPercentage.setText("--");
        if (tvHealthGrade != null) tvHealthGrade.setText("--");
        if (tvHealthStatus != null) tvHealthStatus.setText(getString(R.string.status_detecting));
        if (tvCapacity != null) tvCapacity.setText("-- mAh");
        if (tvCycleCount != null) tvCycleCount.setText(getString(R.string.unit_days_fallback) + " " + getString(R.string.cycle_count_format, 0));
        if (tvTemperature != null) tvTemperature.setText("-- °C");
        if (tvVoltage != null) tvVoltage.setText("-- V");
        if (tvBatterySource != null) tvBatterySource.setText(getString(R.string.status_detecting_short));
        if (tvTechnology != null) tvTechnology.setText("--");
        if (tvBatteryLevel != null) tvBatteryLevel.setText("--%");
        if (tvChargingStatus != null) tvChargingStatus.setText("--");
        if (tvCurrentNow != null) tvCurrentNow.setText("-- mA");
    }
    
    private void updateUI() {
        if (batteryDataManager == null || mainHandler == null) return;
        
        mainHandler.post(() -> {
            try {
                BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
                if (info == null) return;
                
                // 更新健康度
                float healthPercentage = info.getHealthPercentage();
                if (tvHealthPercentage != null) {
                    if (info.hasValidHealthData()) {
                        tvHealthPercentage.setText(String.format(Locale.getDefault(), "%.1f%%", healthPercentage));
                    } else {
                        tvHealthPercentage.setText("--");
                    }
                }
                
                if (tvHealthGrade != null) {
                    tvHealthGrade.setText(info.getHealthGrade());
                }
                
                if (tvHealthStatus != null) {
                    String source = batteryDataManager.getHealthSourceText();
                    float confidence = info.getHealthConfidence();
                    String confidenceText = confidence > 0
                            ? String.format(Locale.getDefault(), getString(R.string.health_confidence_format), confidence * 100)
                            : "";
                    tvHealthStatus.setText(info.getHealthDescription() + " · " + source + confidenceText);
                }

                if (progressHealth != null) {
                    if (info.hasValidHealthData()) {
                        progressHealth.setProgress((int) healthPercentage);
                    } else {
                        progressHealth.setProgress(0);
                    }
                }
                
                // 设置健康度颜色
                if (tvHealthPercentage != null && progressHealth != null && info.hasValidHealthData()) {
                    int healthColor = getHealthColor(healthPercentage);
                    tvHealthPercentage.setTextColor(healthColor);
                    try {
                        progressHealth.getProgressDrawable().setColorFilter(healthColor, android.graphics.PorterDuff.Mode.SRC_IN);
                    } catch (Exception e) {
                        // 某些设备可能不支持setColorFilter
                    }
                } else if (tvHealthPercentage != null) {
                    // 未知状态使用灰色
                    tvHealthPercentage.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_tertiary_label));
                }
                
                // 更新详细信息
                if (tvCapacity != null) {
                    int currentCap = info.getCurrentCapacity();
                    int designCap = info.getDesignCapacity();
                    if (currentCap > 0 && designCap > 0) {
                        tvCapacity.setText(String.format(Locale.getDefault(), "%d / %d mAh", currentCap, designCap));
                    } else {
                        tvCapacity.setText(getString(R.string.status_unreadable));
                    }
                }

                if (tvCycleCount != null) {
                    if (info.hasValidCycleCount()) {
                        tvCycleCount.setText(batteryDataManager.formatCycleCount(info));
                    } else {
                        tvCycleCount.setText(getString(R.string.cycle_count_unreadable));
                    }
                }
                
                if (tvTemperature != null) {
                    tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", info.getTemperature()));
                }
                
                if (tvVoltage != null) {
                    float voltageMv = info.getVoltage();
                    if (voltageMv > 0) {
                        tvVoltage.setText(String.format(Locale.getDefault(), "%.2f V", voltageMv / 1000.0f));
                    } else {
                        tvVoltage.setText("-- V");
                    }
                }
                
                if (tvBatterySource != null) {
                    tvBatterySource.setText(batteryDataManager.getBatterySourceText());
                }
                
                if (tvTechnology != null) {
                    String tech = info.getTechnology();
                    tvTechnology.setText(tech != null && !tech.isEmpty() ? tech : "Li-ion");
                }

                // 更新电池电量
                if (tvBatteryLevel != null) {
                    tvBatteryLevel.setText(info.getLevel() + "%");
                }

                // 更新充电状态
                if (tvChargingStatus != null) {
                    tvChargingStatus.setText(batteryDataManager.getChargingStatusText());
                }

                // 更新电流
                if (tvCurrentNow != null) {
                    int currentNow = info.getCurrentNow();
                    if (currentNow != 0) {
                        tvCurrentNow.setText(String.format(Locale.getDefault(), "%.0f mA", Math.abs(currentNow / 1000.0f)));
                    } else {
                        tvCurrentNow.setText("-- mA");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating UI: " + e.getMessage());
            }
        });
    }
    
    private static final String PREFS_GLOBAL = "app_global_prefs";
    private static final String PREF_DISABLE_ANIMATIONS = "disable_animations";

    private boolean shouldSkipAnimations() {
        try {
            Context ctx = requireContext();
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_GLOBAL, Context.MODE_PRIVATE);
            if (prefs.getBoolean(PREF_DISABLE_ANIMATIONS, false)) {
                return true;
            }
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long totalMemGb = mi.totalMem / (1024L * 1024L * 1024L);
                if (totalMemGb < 4) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Animation check skipped: " + e.getMessage());
        }
        return false;
    }

    private void animateCardsEntry(View view) {
        try {
            if (shouldSkipAnimations()) return;
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
                    .setDuration(300)
                    .setStartDelay(i * 60L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f))
                    .start();
            }
        } catch (Exception e) {
            Log.d(TAG, "Liquid glass card animation skipped: " + e.getMessage());
        }
    }

    private int getHealthColor(float percentage) {
        try {
            if (percentage >= 90) {
                return ContextCompat.getColor(requireContext(), R.color.health_a_plus);
            } else if (percentage >= 80) {
                return ContextCompat.getColor(requireContext(), R.color.health_a);
            } else if (percentage >= 70) {
                return ContextCompat.getColor(requireContext(), R.color.health_c);
            } else if (percentage >= 60) {
                return ContextCompat.getColor(requireContext(), R.color.health_d);
            } else {
                return ContextCompat.getColor(requireContext(), R.color.health_e);
            }
        } catch (Exception e) {
            // 返回默认颜色
            return ContextCompat.getColor(requireContext(), R.color.ios_green);
        }
    }

    private void showCalibrateDialog() {
        try {
            Context context = requireContext();
            EditText input = new EditText(context);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setHint("输入当前实际电池容量（mAh）");

            new AlertDialog.Builder(context)
                    .setTitle("校准电池容量")
                    .setMessage("请输入当前电池的实际容量（mAh），用于更准确地计算健康度。")
                    .setView(input)
                    .setPositiveButton("保存", (dialog, which) -> {
                        String value = input.getText().toString().trim();
                        if (value.isEmpty()) {
                            Toast.makeText(context, "容量不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            int capacity = Integer.parseInt(value);
                            if (capacity <= 0 || capacity > 20000) {
                                Toast.makeText(context, "请输入合理的容量值", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            SharedPreferences prefs = context.getSharedPreferences(
                                    BatteryDataManager.PREFS_NAME, Context.MODE_PRIVATE);
                            prefs.edit().putInt(BatteryDataManager.PREF_CALIBRATED_CAPACITY, capacity).apply();
                            if (batteryDataManager != null) {
                                batteryDataManager.refreshAllDataAsync();
                            }
                            Toast.makeText(context, "校准已保存", Toast.LENGTH_SHORT).show();
                            updateUI();
                        } catch (NumberFormatException e) {
                            Toast.makeText(context, "输入格式错误", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing calibrate dialog: " + e.getMessage());
        }
    }

    private void showChargingHistoryDialog() {
        try {
            Context context = requireContext();
            new Thread(() -> {
                List<String> historyItems = loadChargingHistory();
                if (mainHandler != null) {
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle("充电历史");
                        if (historyItems.isEmpty()) {
                            builder.setMessage("暂无充电记录");
                        } else {
                            StringBuilder sb = new StringBuilder();
                            for (String item : historyItems) {
                                sb.append(item).append("\n\n");
                            }
                            builder.setMessage(sb.toString().trim());
                        }
                        builder.setPositiveButton("确定", null);
                        builder.show();
                    });
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "Error showing charging history dialog: " + e.getMessage());
        }
    }

    private List<String> loadChargingHistory() {
        List<String> result = new ArrayList<>();
        try {
            BatteryHealthApplication app = BatteryHealthApplication.getInstance();
            if (app == null) return result;
            AppDatabase db = app.getDatabase();
            if (db == null) return result;

            List<String> sessions = db.powerHistoryDao().getAllSessions();
            if (sessions == null || sessions.isEmpty()) return result;

            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            int count = 0;
            for (String sessionId : sessions) {
                if (count >= 10) break; // 只展示最近10次
                List<PowerHistory> records = db.powerHistoryDao().getBySession(sessionId);
                if (records == null || records.isEmpty()) continue;

                PowerHistory first = records.get(0);
                PowerHistory last = records.get(records.size() - 1);
                float maxPower = 0;
                float totalPower = 0;
                for (PowerHistory r : records) {
                    if (r.getPower() > maxPower) maxPower = r.getPower();
                    totalPower += r.getPower();
                }
                float avgPower = totalPower / records.size();
                long durationMin = (last.getTimestamp() - first.getTimestamp()) / (1000 * 60);

                String item = String.format(Locale.getDefault(),
                        "%s\n时长: %d 分钟 · 峰值: %.1f W · 平均: %.1f W · 电量: %d%% → %d%%",
                        sdf.format(new Date(first.getTimestamp())),
                        durationMin,
                        maxPower,
                        avgPower,
                        first.getBatteryLevel(),
                        last.getBatteryLevel());
                result.add(item);
                count++;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading charging history: " + e.getMessage());
        }
        return result;
    }
}
