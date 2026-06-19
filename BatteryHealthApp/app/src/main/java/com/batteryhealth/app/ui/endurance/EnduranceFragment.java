package com.batteryhealth.app.ui.endurance;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.BatteryConsumptionAnalyzer;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.Collections;
import java.util.Locale;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 续航分析Fragment
 * 
 * 功能：
 * 1. 预计续航时间计算
 * 2. 放电速率监测
 * 3. 充电状态分析
 * 4. 预计充满时间计算
 * 
 * 注意：电池广播由BatteryMonitorService统一处理，此Fragment使用定时轮询
 */
public class EnduranceFragment extends Fragment {
    
    private static final String TAG = "EnduranceFragment";
    private static final long UPDATE_INTERVAL = 5000; // 5秒更新一次
    
    private TextView tvEnduranceTime;
    private TextView tvEnduranceStatus;
    private TextView tvCurrentLevel;
    private TextView tvDischargeRate;
    private TextView tvChargeStatus;
    private TextView tvBatteryTemp;
    private TextView tvFullChargeTime;
    private TextView tvConsumptionSummary;
    private TextView tvConsumptionList;
    private TextView tvConsumptionEmpty;

    private BatteryDataManager batteryDataManager;
    private Handler mainHandler;
    private ExecutorService ioExecutor;
    private boolean isRunning = false;
    
    // 放电速率计算
    private int lastLevel = -1;
    private long lastLevelTime = 0;
    private float dischargeRate = 0; // %/h
    private float historicalDischargeRate = 0; // 从数据库加载的历史均值 %/h

    // 充电电流滑动窗口（用于充满时间估算的平滑电流）
    private static final int CURRENT_WINDOW_SIZE = 12; // 最近 12 次采样（约 60 秒）
    private final java.util.LinkedList<Integer> currentWindow = new java.util.LinkedList<>();
    private long currentWindowSum = 0;
    private int currentWindowSamples = 0;
    
    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            // 将 IO 操作下沉到后台线程，UI 更新回主线程
            new Thread(() -> {
                if (batteryDataManager != null) {
                    batteryDataManager.refreshFromStickyIntent();
                }
                if (mainHandler != null) {
                    mainHandler.post(() -> {
                        if (isAdded()) {
                            updateUI();
                        }
                    });
                }
            }).start();

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
            return inflater.inflate(R.layout.fragment_endurance, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
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
            ioExecutor = Executors.newSingleThreadExecutor();

            if (getActivity() instanceof MainActivity) {
                batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
            }

            initViews(view);
            loadHistoricalDischargeRate();
            updateUI();
            refreshConsumptionAsync();
            animateCardsEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage(), e);
        }
    }

    /**
     * 从历史数据库加载平均放电速率作为初始值，避免首次进入显示“计算中...”。
     */
    private void loadHistoricalDischargeRate() {
        new Thread(() -> {
            try {
                BatteryHealthApplication app = BatteryHealthApplication.getInstance();
                if (app == null) return;
                AppDatabase db = app.getDatabase();
                if (db == null) return;

                // 取最近 24 小时内至少 10 条记录计算放电速率
                long oneDayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
                List<BatteryInfo> records = db.batteryInfoDao().getSince(oneDayAgo);
                if (records == null || records.size() < 5) return;

                // 按时间排序
                Collections.sort(records, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

                float totalRate = 0;
                int sampleCount = 0;
                for (int i = 1; i < records.size(); i++) {
                    BatteryInfo prev = records.get(i - 1);
                    BatteryInfo curr = records.get(i);
                    int levelDiff = prev.getLevel() - curr.getLevel();
                    long timeDiff = curr.getTimestamp() - prev.getTimestamp();
                    if (levelDiff > 0 && timeDiff > 60_000) {
                        float hours = timeDiff / (1000.0f * 60 * 60);
                        totalRate += levelDiff / hours;
                        sampleCount++;
                    }
                }

                if (sampleCount > 0) {
                    float avgRate = totalRate / sampleCount;
                    // 限制在合理范围
                    if (avgRate > 0.1f && avgRate < 100) {
                        dischargeRate = avgRate;
                        historicalDischargeRate = avgRate;
                        Log.d(TAG, "Loaded historical discharge rate: " + avgRate + "%/h");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading historical discharge rate: " + e.getMessage());
            }
        }).start();
    }

    private void animateCardsEntry(View view) {
        UiAnimationHelper.animateCardsEntry(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        isRunning = true;
        if (mainHandler != null) {
            mainHandler.post(updateRunnable);
        }
        // 进入页面时拉取一次真实耗电榜
        refreshConsumptionAsync();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        isRunning = false;
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
        if (ioExecutor != null) {
            ioExecutor.shutdown();
            ioExecutor = null;
        }
    }

    private void initViews(View view) {
        tvEnduranceTime = view.findViewById(R.id.tv_endurance_time);
        tvEnduranceStatus = view.findViewById(R.id.tv_endurance_status);
        tvCurrentLevel = view.findViewById(R.id.tv_current_level);
        tvDischargeRate = view.findViewById(R.id.tv_discharge_rate);
        tvChargeStatus = view.findViewById(R.id.tv_charge_status);
        tvBatteryTemp = view.findViewById(R.id.tv_battery_temp);
        tvFullChargeTime = view.findViewById(R.id.tv_full_charge_time);
        tvConsumptionSummary = view.findViewById(R.id.tv_consumption_summary);
        tvConsumptionList = view.findViewById(R.id.tv_consumption_list);
        tvConsumptionEmpty = view.findViewById(R.id.tv_consumption_empty);
    }

    /**
     * 异步刷新耗电榜（24 小时窗口）。
     * 数据源：android.app.usage.BatteryStatsManager（通过 "batterystats" 字符串获取隐藏服务），
     *         配合 UsageStatsManager 获取前台应用列表，最终给出真实耗电排名。
     */
    private void refreshConsumptionAsync() {
        if (!isAdded() || ioExecutor == null || ioExecutor.isShutdown()) return;
        ioExecutor.submit(() -> {
            try {
                Context ctx = getContext();
                if (ctx == null) return;
                final BatteryConsumptionAnalyzer.Result result = BatteryConsumptionAnalyzer.analyze(ctx, 24L * 60 * 60 * 1000);
                if (mainHandler != null) {
                    mainHandler.post(() -> {
                        if (!isAdded() || isDetached()) return;
                        renderConsumption(result);
                    });
                }
            } catch (Throwable t) {
                Log.e(TAG, "refreshConsumptionAsync failed: " + t.getMessage(), t);
            }
        });
    }

    private void renderConsumption(BatteryConsumptionAnalyzer.Result result) {
        if (tvConsumptionSummary == null || tvConsumptionList == null || tvConsumptionEmpty == null) return;
        if (result == null) {
            tvConsumptionSummary.setText(R.string.consumption_calculating);
            tvConsumptionList.setText("");
            tvConsumptionEmpty.setVisibility(View.VISIBLE);
            return;
        }

        // 顶部摘要：电池容量 + 系统预估续航
        String hoursStr;
        if (result.systemEstimatedHours > 0) {
            hoursStr = String.format(Locale.getDefault(), getString(R.string.consumption_hours_format), result.systemEstimatedHours);
        } else {
            hoursStr = getString(R.string.consumption_calculating);
        }
        if (result.batteryCapacityMah > 0) {
            tvConsumptionSummary.setText(String.format(Locale.getDefault(),
                    getString(R.string.consumption_summary_format), result.batteryCapacityMah, hoursStr));
        } else {
            tvConsumptionSummary.setText(R.string.consumption_summary_unknown);
        }

        // 耗电榜列表
        if (result.topConsumers == null || result.topConsumers.isEmpty()) {
            tvConsumptionList.setText("");
            tvConsumptionEmpty.setVisibility(View.VISIBLE);
        } else {
            tvConsumptionEmpty.setVisibility(View.GONE);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < result.topConsumers.size(); i++) {
                BatteryConsumptionAnalyzer.AppConsumption c = result.topConsumers.get(i);
                if (c == null) continue;
                if (i > 0) sb.append("\n");
                sb.append(String.format(Locale.getDefault(), "%d. %s  %.1f%% · %d mAh",
                        i + 1, c.displayName, c.percent, c.totalMahConsumed));
            }
            tvConsumptionList.setText(sb.toString());
        }
    }
    
    private void updateUI() {
        if (batteryDataManager == null || mainHandler == null) return;

        mainHandler.post(() -> {
            try {
                com.batteryhealth.app.data.model.BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
                if (info == null) return;

                int level = info.getLevel();
                float temperature = info.getTemperature();
                int status = info.getStatus();
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;
                int currentNowUa = info.getCurrentNow(); // 正值充电，负值放电
                int designCapacity = info.getDesignCapacity();
                // 优先使用 charge_counter（当前剩余电量 mAh），更精确
                int remainingCapacityMah = info.getChargeCounter() > 0
                        ? info.getChargeCounter() / 1000  // uAh → mAh
                        : (designCapacity > 0 ? (int) (designCapacity * level / 100.0) : -1);

                // 计算放电速率（%/h）
                calculateDischargeRate(level);

                // 更新充电电流滑动窗口
                if (currentNowUa > 0) {
                    currentWindow.addLast(currentNowUa);
                    currentWindowSum += currentNowUa;
                    currentWindowSamples++;
                    while (currentWindow.size() > CURRENT_WINDOW_SIZE) {
                        currentWindowSum -= currentWindow.removeFirst();
                        currentWindowSamples--;
                    }
                }

                // 更新当前电量
                if (tvCurrentLevel != null) {
                    tvCurrentLevel.setText(level + "%");
                }

                // 更新放电速率
                if (tvDischargeRate != null) {
                    if (isCharging) {
                        tvDischargeRate.setText(getString(R.string.status_charging));
                    } else if (dischargeRate > 0) {
                        tvDischargeRate.setText(String.format(Locale.getDefault(), "%.1f%%/h", dischargeRate));
                    } else {
                        tvDischargeRate.setText(getString(R.string.status_calculating_short));
                    }
                }

                // 更新充电状态
                if (tvChargeStatus != null) {
                    tvChargeStatus.setText(batteryDataManager.getChargingStatusText());
                }

                // 更新电池温度
                if (tvBatteryTemp != null) {
                    tvBatteryTemp.setText(String.format(Locale.getDefault(), "%.1f°C", temperature));
                }

                // 计算预计续航时间：优先基于历史放电速率，其次基于实时电流
                if (tvEnduranceTime != null) {
                    if (isCharging) {
                        tvEnduranceTime.setText(getString(R.string.status_charging));
                        tvEnduranceTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_blue));
                    } else {
                        float remainingHours = estimateRemainingHours(level, currentNowUa, remainingCapacityMah);
                        if (remainingHours > 0) {
                            if (remainingHours >= 24) {
                                tvEnduranceTime.setText(String.format(Locale.getDefault(), getString(R.string.status_endurance_days), remainingHours / 24));
                            } else {
                                tvEnduranceTime.setText(String.format(Locale.getDefault(), getString(R.string.status_endurance_hours), remainingHours));
                            }
                            tvEnduranceTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_green));
                        } else {
                            tvEnduranceTime.setText(getString(R.string.status_hours_placeholder));
                        }
                    }
                }

                // 更新续航状态描述
                if (tvEnduranceStatus != null) {
                    if (isCharging) {
                        tvEnduranceStatus.setText(getString(R.string.status_device_charging));
                    } else if (dischargeRate > 0) {
                        if (dischargeRate < 5) {
                            tvEnduranceStatus.setText(getString(R.string.status_excellent));
                        } else if (dischargeRate < 10) {
                            tvEnduranceStatus.setText(getString(R.string.status_good));
                        } else if (dischargeRate < 20) {
                            tvEnduranceStatus.setText(getString(R.string.status_average));
                        } else {
                            tvEnduranceStatus.setText(getString(R.string.status_poor));
                        }
                    } else {
                        tvEnduranceStatus.setText(getString(R.string.status_calculating_short));
                    }
                }

                // 计算预计充满时间：考虑恒压阶段（80% 后电流衰减）
                if (tvFullChargeTime != null) {
                    if (isCharging && level < 100) {
                        float fullChargeHours = estimateFullChargeHours(level, currentNowUa, designCapacity);
                        if (fullChargeHours > 0) {
                            if (fullChargeHours < 1) {
                                tvFullChargeTime.setText(String.format(Locale.getDefault(), getString(R.string.status_full_charge_time_minutes), fullChargeHours * 60));
                            } else {
                                tvFullChargeTime.setText(String.format(Locale.getDefault(), getString(R.string.status_full_charge_time_hours), fullChargeHours));
                            }
                        } else {
                            tvFullChargeTime.setText(getString(R.string.status_calculating_short));
                        }
                    } else if (level >= 100) {
                        tvFullChargeTime.setText(getString(R.string.status_full_charge_done));
                    } else {
                        tvFullChargeTime.setText(getString(R.string.status_not_charging_short));
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error updating UI: " + e.getMessage());
            }
        });
    }

    /**
     * 综合多种方法估算剩余续航时间。
     * 优先级：历史放电速率 + 实时电流 加权平均 → 单一方法 → 数据库历史均值兜底。
     */
    private float estimateRemainingHours(int level, int currentNowUa, int remainingCapacityMah) {
        float method1Result = -1; // 历史放电速率
        float method2Result = -1; // 实时电流

        // 方法1：历史放电速率（%/h）
        if (dischargeRate > 0.1f && level > 0) {
            method1Result = level / dischargeRate;
        }

        // 方法2：实时电流（currentNow 放电时为负）
        if (currentNowUa < 0 && remainingCapacityMah > 0) {
            float dischargeMa = Math.abs(currentNowUa) / 1000.0f;
            if (dischargeMa > 0) {
                method2Result = remainingCapacityMah / dischargeMa;
            }
        }

        // 两种方法都有效时取加权平均（放电速率更稳定，权重更高）
        if (method1Result > 0 && method2Result > 0) {
            return method1Result * 0.6f + method2Result * 0.4f;
        }

        // 只有一种方法有效
        if (method1Result > 0) return method1Result;
        if (method2Result > 0) return method2Result;

        // 兜底：从数据库加载历史均值放电速率
        if (historicalDischargeRate > 0.1f && level > 0) {
            return level / historicalDischargeRate;
        }

        return -1;
    }

    /**
     * 估算充满时间：分段计算 CC（恒流）和 CV（恒压）阶段。
     * CC 阶段（0-80%）：按滑动窗口平均充电电流估算，避免瞬时波动。
     * CV 阶段（80-100%）：电流逐渐衰减，平均约为 CC 阶段的 55%。
     */
    private float estimateFullChargeHours(int level, int currentNowUa, int designCapacityMah) {
        if (designCapacityMah <= 0) return -1;

        // 使用滑动窗口平均电流，避免瞬时值波动导致估算不稳定
        float chargeMa;
        if (currentWindowSamples > 3) {
            chargeMa = currentWindowSum / currentWindowSamples / 1000.0f; // µA → mA → A 的 mA 部分
        } else {
            // 采样不足，使用瞬时值
            if (currentNowUa <= 0) return -1;
            chargeMa = currentNowUa / 1000.0f;
        }
        if (chargeMa <= 0) return -1;

        float remainingMah = designCapacityMah * (100 - level) / 100.0f;

        if (level < 80) {
            // CC 阶段：0→80% 按当前电流
            float ccRemainingMah = designCapacityMah * (80 - level) / 100.0f;
            float ccHours = ccRemainingMah / chargeMa;
            // CV 阶段：80→100% 电流衰减，平均约 55%
            float cvRemainingMah = designCapacityMah * 20 / 100.0f;
            float cvHours = cvRemainingMah / (chargeMa * 0.55f);
            return ccHours + cvHours;
        } else {
            // 已在 CV 阶段：电流衰减，平均约 55%
            float cvHours = remainingMah / (chargeMa * 0.55f);
            return cvHours;
        }
    }
    
    /**
     * 计算放电速率
     */
    private void calculateDischargeRate(int currentLevel) {
        long currentTime = System.currentTimeMillis();
        
        if (lastLevel >= 0 && lastLevelTime > 0 && currentLevel < lastLevel) {
            long timeDiff = currentTime - lastLevelTime; // 毫秒
            int levelDiff = lastLevel - currentLevel;
            
            if (timeDiff > 0 && levelDiff > 0) {
                // 计算每小时放电百分比
                float hoursDiff = timeDiff / (1000.0f * 60 * 60);
                dischargeRate = levelDiff / hoursDiff;
            }
        }
        
        lastLevel = currentLevel;
        lastLevelTime = currentTime;
    }
}