package com.batteryhealth.app.ui.endurance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 续航分析 Fragment：基于真实历史放电数据计算放电速率，估算剩余续航时间。
 */
public class EnduranceFragment extends Fragment {

    private static final String PREFS_ENDURANCE = "endurance_prefs";
    private static final String PREF_LAST_LEVEL = "last_level";
    private static final String PREF_LAST_TIME = "last_time";
    private static final String PREF_DISCHARGE_RATE = "discharge_rate";

    private TextView tvEnduranceHours;
    private TextView tvEnduranceMeta;
    private TextView tvMetricBattery, tvMetricDischarge, tvMetricTemp;
    private TextView tvChargingStatus, tvUsedTime, tvConsumedBattery, tvEstimatedFull, tvScreenOnTime;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private int lastBatteryLevel = -1;
    private long lastUpdateTime = -1;
    private float dischargeRate = 0f;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_endurance, container, false);
        initViews(view);
        animateEntry(view);
        // 恢复上次的放电速率，避免首次显示为 0
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_ENDURANCE, Context.MODE_PRIVATE);
        dischargeRate = prefs.getFloat(PREF_DISCHARGE_RATE, 0f);
        return view;
    }

    private void initViews(View view) {
        tvEnduranceHours = view.findViewById(R.id.tv_endurance_hours);
        tvEnduranceMeta = view.findViewById(R.id.tv_endurance_meta);
        tvMetricBattery = view.findViewById(R.id.tv_metric_battery);
        tvMetricDischarge = view.findViewById(R.id.tv_metric_discharge);
        tvMetricTemp = view.findViewById(R.id.tv_metric_temp);
        tvChargingStatus = view.findViewById(R.id.tv_charging_status);
        tvUsedTime = view.findViewById(R.id.tv_used_time);
        tvConsumedBattery = view.findViewById(R.id.tv_consumed_battery);
        tvEstimatedFull = view.findViewById(R.id.tv_estimated_full);
        tvScreenOnTime = view.findViewById(R.id.tv_screen_on_time);
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
        // 进入页面时异步加载历史放电速率作为基准
        loadHistoricalDischargeRate();
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
                handler.postDelayed(this, 3000);
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
            updateFromIntent(intent);
        }
    };

    private void updateBatteryData() {
        try {
            Intent intent = requireContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent != null) {
                updateFromIntent(intent);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 异步从历史数据库计算真实的平均放电速率（%/h），作为当前放电速率的基准。
     * 优先取最近 24h 内非充电状态的记录；不足时取最近 7 天。
     */
    private void loadHistoricalDischargeRate() {
        if (!isAdded()) return;
        final Context appCtx = requireContext().getApplicationContext();
        ioExecutor.submit(() -> {
            try {
                BatteryHealthApplication app = (BatteryHealthApplication) appCtx;
                AppDatabase db = app.getDatabase();
                if (db == null) return;

                long now = System.currentTimeMillis();
                long dayAgo = now - 24L * 60 * 60 * 1000;
                long weekAgo = now - 7L * 24 * 60 * 60 * 1000;

                List<BatteryInfo> records = db.batteryInfoDao().getSince(dayAgo);
                if (records == null || records.size() < 3) {
                    records = db.batteryInfoDao().getSince(weekAgo);
                }
                if (records == null || records.size() < 2) return;

                float totalRate = 0f;
                int count = 0;
                for (int i = 1; i < records.size(); i++) {
                    BatteryInfo prev = records.get(i - 1);
                    BatteryInfo curr = records.get(i);
                    long dtMs = curr.getTimestamp() - prev.getTimestamp();
                    int dLevel = prev.getLevel() - curr.getLevel();
                    // 仅统计放电过程（电量下降且时间间隔在合理范围）
                    if (dLevel > 0 && dtMs > 60_000 && dtMs < 24L * 60 * 60 * 1000) {
                        float hours = dtMs / (1000f * 60 * 60);
                        totalRate += dLevel / hours;
                        count++;
                    }
                }
                if (count > 0) {
                    final float avgRate = totalRate / count;
                    handler.post(() -> {
                        if (isAdded()) {
                            dischargeRate = avgRate;
                            // 持久化，下次启动可直接使用
                            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_ENDURANCE, Context.MODE_PRIVATE);
                            prefs.edit().putFloat(PREF_DISCHARGE_RATE, dischargeRate).apply();
                        }
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void updateFromIntent(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPct = (int) ((level / (float) scale) * 100);

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        String chargingStatus = isCharging ? getString(R.string.status_charging) : getString(R.string.status_discharging);

        int current = 0;
        BatteryManager bm = (BatteryManager) requireContext().getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        }
        float currentMa = current / 1000f;

        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        float tempC = temp / 10f;

        // 计算放电速率：基于实时两次采样的差值
        long now = System.currentTimeMillis();
        if (lastBatteryLevel >= 0 && lastUpdateTime > 0 && !isCharging) {
            long elapsedMs = now - lastUpdateTime;
            if (elapsedMs >= 60_000) { // 至少间隔1分钟才更新速率，避免噪声
                int delta = lastBatteryLevel - batteryPct;
                if (delta > 0) {
                    float hours = elapsedMs / (1000f * 60 * 60);
                    float instantRate = delta / hours;
                    // 指数平滑：新速率 = 0.3 * 瞬时 + 0.7 * 历史
                    dischargeRate = dischargeRate > 0
                            ? (instantRate * 0.3f + dischargeRate * 0.7f)
                            : instantRate;
                }
            }
        }
        // 充电时不清零，保持上次的放电速率用于估算；如果从未计算过，使用历史数据库值
        if (dischargeRate <= 0) {
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_ENDURANCE, Context.MODE_PRIVATE);
            dischargeRate = prefs.getFloat(PREF_DISCHARGE_RATE, 0f);
        }
        // 最终兜底：基于典型手机待机/使用经验给一个保守估计（仅首次无数据时）
        if (dischargeRate <= 0) {
            dischargeRate = 8.0f;
        }

        lastBatteryLevel = batteryPct;
        lastUpdateTime = now;

        // 持久化当前状态
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_ENDURANCE, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(PREF_LAST_LEVEL, batteryPct)
                .putLong(PREF_LAST_TIME, now)
                .putFloat(PREF_DISCHARGE_RATE, dischargeRate)
                .apply();

        // Endurance estimate
        float remainingHours = batteryPct / dischargeRate;
        int hours = (int) remainingHours;
        int minutes = (int) ((remainingHours - hours) * 60);
        tvEnduranceHours.setText(String.valueOf(hours));
        tvEnduranceMeta.setText(String.format(Locale.getDefault(),
                getString(R.string.meta_endurance), batteryPct, dischargeRate));

        // Quick metrics
        tvMetricBattery.setText(String.format(Locale.getDefault(), "%d%%", batteryPct));
        tvMetricDischarge.setText(String.format(Locale.getDefault(), "%.1f%%/h", dischargeRate));
        tvMetricTemp.setText(String.format(Locale.getDefault(), "%.1f°C", tempC));

        // Details
        tvChargingStatus.setText(chargingStatus);
        tvUsedTime.setText(formatDuration(SystemClock.elapsedRealtime()));
        tvConsumedBattery.setText(String.format(Locale.getDefault(), "%d%%", 100 - batteryPct));
        tvEstimatedFull.setText(isCharging ? getString(R.string.status_calculating) : "--");
        tvScreenOnTime.setText(formatDuration(SystemClock.uptimeMillis()));
    }

    private String formatDuration(long ms) {
        long hours = ms / (1000 * 60 * 60);
        long minutes = (ms % (1000 * 60 * 60)) / (1000 * 60);
        return String.format(Locale.getDefault(), "%d小时%d分", hours, minutes);
    }
}
