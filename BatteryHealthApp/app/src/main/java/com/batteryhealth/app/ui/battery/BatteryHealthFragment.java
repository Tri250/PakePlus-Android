package com.batteryhealth.app.ui.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.ui.view.HealthRingView;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BatteryHealthFragment extends Fragment {

    private HealthRingView healthRing;
    private TextView tvHealthPercentage;
    private TextView tvHealthGrade;
    private TextView tvHealthStatus;
    private TextView tvBatteryLevel;
    private TextView tvChargingStatus;
    private TextView tvCurrentNow;
    private TextView tvCapacity;
    private TextView tvCycleCount;
    private TextView tvTemperature;
    private TextView tvVoltage;
    private TextView tvBatterySource;
    private TextView tvTechnology;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private BatteryDataManager batteryDataManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_battery_health, container, false);
        initViews(view);
        animateEntry(view);
        // 优先通过 MainActivity 获取共享的 BatteryDataManager，确保与监测服务使用同一份数据
        if (getActivity() instanceof MainActivity) {
            batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
        }
        if (batteryDataManager == null) {
            batteryDataManager = new BatteryDataManager(requireContext().getApplicationContext());
        }
        return view;
    }

    private void initViews(View view) {
        healthRing = view.findViewById(R.id.health_ring);
        tvHealthPercentage = view.findViewById(R.id.tv_health_percentage);
        tvHealthGrade = view.findViewById(R.id.tv_health_grade);
        tvHealthStatus = view.findViewById(R.id.tv_health_status);
        tvBatteryLevel = view.findViewById(R.id.tv_battery_level);
        tvChargingStatus = view.findViewById(R.id.tv_charging_status);
        tvCurrentNow = view.findViewById(R.id.tv_current_now);
        tvCapacity = view.findViewById(R.id.tv_capacity);
        tvCycleCount = view.findViewById(R.id.tv_cycle_count);
        tvTemperature = view.findViewById(R.id.tv_temperature);
        tvVoltage = view.findViewById(R.id.tv_voltage);
        tvBatterySource = view.findViewById(R.id.tv_battery_source);
        tvTechnology = view.findViewById(R.id.tv_technology);
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerBatteryReceiver();
        startPeriodicUpdate();
        // 立即拉取一次，避免等待首个 2 秒 tick
        updateBatteryData();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterBatteryReceiver();
        stopPeriodicUpdate();
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

    private void registerBatteryReceiver() {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                requireContext().registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                requireContext().registerReceiver(batteryReceiver, filter);
            }
        } catch (Exception ignored) {
        }
    }

    private void unregisterBatteryReceiver() {
        try {
            requireContext().unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void startPeriodicUpdate() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateBatteryData();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updateRunnable);
    }

    private void stopPeriodicUpdate() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBatteryData();
        }
    };

    /**
     * 异步调用 BatteryDataManager 获取完整电池信息（含健康度/容量/循环/来源/技术等），
     * 然后回到主线程刷新 UI。所有耗时 IO 在 ioExecutor 完成。
     */
    private void updateBatteryData() {
        if (!isAdded() || batteryDataManager == null) return;
        ioExecutor.submit(() -> {
            try {
                batteryDataManager.refreshFromStickyIntent();
                BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
                Intent live = null;
                try {
                    live = requireContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                } catch (Exception ignored) {
                }
                final BatteryInfo snapshot = info;
                final Intent sticky = live;
                if (handler != null) {
                    handler.post(() -> {
                        if (isAdded()) renderInfo(snapshot, sticky);
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void renderInfo(BatteryInfo info, Intent sticky) {
        // 1. 基础电量与状态
        int level = -1;
        int status = -1;
        int tempRaw = -1;
        int voltageMv = 0;
        int currentUa = 0;
        String technology = null;
        if (sticky != null) {
            int rawLevel = sticky.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = sticky.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            level = (rawLevel >= 0 && scale > 0) ? (int) ((rawLevel / (float) scale) * 100) : rawLevel;
            status = sticky.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
            tempRaw = sticky.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1);
            voltageMv = sticky.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0);
            currentUa = ((android.os.BatteryManager) requireContext().getSystemService(Context.BATTERY_SERVICE))
                    .getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            technology = sticky.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY);
        }

        boolean isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
        if (level < 0 && info != null) level = info.getLevel();
        if (info != null && info.getTemperature() > 0 && tempRaw < 0) {
            tempRaw = Math.round(info.getTemperature() * 10f);
        }
        if (info != null && info.getVoltage() > 0 && voltageMv <= 0) {
            voltageMv = (int) info.getVoltage();
        }
        if (info != null && info.getCurrentNow() != 0 && currentUa == 0) {
            currentUa = info.getCurrentNow();
        }

        float voltageV = voltageMv / 1000f;
        float currentMa = Math.abs(currentUa) / 1000f;
        float tempC = tempRaw / 10f;

        tvBatteryLevel.setText(String.format(Locale.getDefault(), "%d%%", Math.max(0, level)));
        tvChargingStatus.setText(batteryDataManager.getChargingStatusText());
        tvCurrentNow.setText(String.format(Locale.getDefault(), "%.0f mA", currentMa));
        tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));
        tvVoltage.setText(String.format(Locale.getDefault(), "%.2f V", voltageV));

        // 2. 容量：使用 BatteryDataManager 计算的 design/fcc
        int design = info != null ? info.getDesignCapacity() : 0;
        int fcc = info != null ? info.getCurrentCapacity() : 0;
        if (design <= 0) design = 3000; // 兜底显示，避免负值
        if (fcc <= 0) fcc = (int) (design * (info != null ? Math.max(0f, info.getHealthPercentage()) / 100f : 0.85f));
        tvCapacity.setText(String.format(Locale.getDefault(), "%d / %d mAh", fcc, design));

        // 3. 循环次数：使用 BatteryDataManager 的真实读取结果
        if (info != null && info.hasValidCycleCount()) {
            String cycleText = batteryDataManager.formatCycleCount(info);
            tvCycleCount.setText(cycleText);
        } else {
            tvCycleCount.setText(getString(R.string.cycle_count_unreadable));
        }

        // 4. 技术与电池来源
        String tech = info != null && info.getTechnology() != null && !info.getTechnology().isEmpty()
                ? info.getTechnology() : (technology != null ? technology : getString(R.string.battery_technology_default));
        tvTechnology.setText(tech);
        tvBatterySource.setText(batteryDataManager.getBatterySourceText());

        // 5. 健康度大数字、等级、状态
        float healthPct = info != null ? info.getHealthPercentage() : 0f;
        int healthInt = Math.max(0, Math.min(100, Math.round(healthPct)));
        String grade = calculateGrade(healthInt);
        tvHealthGrade.setText(String.format(Locale.getDefault(), "等级 %s", grade));
        tvHealthPercentage.setText(String.format(Locale.getDefault(), "%d%%", healthInt));

        String statusText;
        if (healthInt >= 90) {
            statusText = getString(R.string.status_excellent);
        } else if (healthInt >= 80) {
            statusText = getString(R.string.status_good);
        } else if (healthInt >= 60) {
            statusText = getString(R.string.status_fair);
        } else {
            statusText = getString(R.string.status_poor);
        }
        tvHealthStatus.setText(statusText);

        UiAnimationHelper.animateRingProgress(healthRing, healthInt);
    }

    private String calculateGrade(int health) {
        if (health >= 95) return "A+";
        if (health >= 90) return "A";
        if (health >= 85) return "A-";
        if (health >= 80) return "B+";
        if (health >= 75) return "B";
        if (health >= 70) return "B-";
        if (health >= 60) return "C";
        return "D";
    }
}
