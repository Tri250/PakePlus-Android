package com.batteryhealth.app.ui.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
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

/**
 * 电池健康主页面 Fragment。
 * 使用 BatteryDataManager 获取真实健康度、循环次数、电池来源等数据，
 * 不使用模拟数据或空实现。
 */
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
    private BatteryDataManager batteryDataManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_battery_health, container, false);
        initViews(view);
        animateEntry(view);
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
        // 从 MainActivity 获取共享的 BatteryDataManager
        if (getActivity() instanceof MainActivity) {
            batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
        }
        if (batteryDataManager == null) {
            batteryDataManager = new BatteryDataManager(requireContext());
        }
        registerBatteryReceiver();
        startPeriodicUpdate();
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
        // 在 onDestroyView 中也停止更新，防止 onPause 因异常跳过导致 Handler 继续持有引用
        stopPeriodicUpdate();
    }

    private void registerBatteryReceiver() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requireContext().registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(batteryReceiver, filter);
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
     * 使用 BatteryDataManager 获取真实电池数据并更新 UI。
     */
    private void updateBatteryData() {
        if (batteryDataManager == null) return;

        // 在后台线程获取完整电池信息（含 sysfs 读取）
        new Thread(() -> {
            try {
                batteryDataManager.refreshFromStickyIntent();
                BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
                if (info != null && isAdded()) {
                    handler.post(() -> updateUI(info));
                }
            } catch (Exception e) {
                // 静默处理
            }
        }).start();
    }

    /**
     * 根据真实 BatteryInfo 更新所有 UI 元素。
     */
    private void updateUI(BatteryInfo info) {
        if (info == null || !isAdded()) return;

        // 1. 电量
        tvBatteryLevel.setText(String.format(Locale.getDefault(), "%d%%", info.getLevel()));

        // 2. 充电状态
        tvChargingStatus.setText(batteryDataManager.getChargingStatusText());

        // 3. 电流
        float currentMa = info.getCurrentNow() / 1000f;
        tvCurrentNow.setText(String.format(Locale.getDefault(), "%.0f mA", Math.abs(currentMa)));

        // 4. 容量（设计容量 + 当前满充容量）
        int displayCapacity = info.getCurrentCapacity() > 0 ? info.getCurrentCapacity() : info.getDesignCapacity();
        tvCapacity.setText(String.format(Locale.getDefault(), "%d mAh", displayCapacity > 0 ? displayCapacity : 0));

        // 5. 循环次数（使用 BatteryDataManager 的真实循环次数）
        tvCycleCount.setText(batteryDataManager.formatCycleCount(info));

        // 6. 温度
        tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", info.getTemperature()));

        // 7. 电压
        float voltageV = info.getVoltage() / 1000f;
        tvVoltage.setText(String.format(Locale.getDefault(), "%.2f V", voltageV));

        // 8. 电池技术
        tvTechnology.setText(info.getTechnology());

        // 9. 电池来源（使用真实的多维验证结果）
        tvBatterySource.setText(batteryDataManager.getBatterySourceText());

        // 10. 健康度（使用真实的三段损耗计算结果）
        float healthPct = info.getHealthPercentage();
        if (healthPct >= 0) {
            tvHealthPercentage.setText(String.format(Locale.getDefault(), "%.0f%%", healthPct));
            String grade = calculateGrade(healthPct);
            tvHealthGrade.setText(String.format(Locale.getDefault(), "等级 %s", grade));

            String statusText;
            if (healthPct >= 90) {
                statusText = getString(R.string.status_excellent);
            } else if (healthPct >= 80) {
                statusText = getString(R.string.status_good);
            } else if (healthPct >= 60) {
                statusText = getString(R.string.status_fair);
            } else {
                statusText = getString(R.string.status_poor);
            }
            tvHealthStatus.setText(statusText);

            // 健康度来源信息
            String healthSource = batteryDataManager.getHealthSourceText();

            UiAnimationHelper.animateRingProgress(healthRing, (int) healthPct);
        } else {
            tvHealthPercentage.setText("--");
            tvHealthGrade.setText("--");
            tvHealthStatus.setText(getString(R.string.health_unknown));
        }
    }

    private String calculateGrade(float health) {
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
