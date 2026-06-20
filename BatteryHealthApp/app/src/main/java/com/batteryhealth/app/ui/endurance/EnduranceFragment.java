package com.batteryhealth.app.ui.endurance;

import android.content.Intent;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PerformanceData;
import com.batteryhealth.app.utils.BatteryDataManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 续航分析Fragment
 * 
 * 功能：
 * 1. 预计续航时间计算
 * 2. 放电速率监测
 * 3. 充电状态分析
 * 4. 预计充满时间计算
 * 5. 电池健康卡片与续航评分
 * 6. 应用耗电排行与卡顿统计
 * 7. 手表专属续航参考
 */
public class EnduranceFragment extends Fragment {
    
    private static final String TAG = "EnduranceFragment";
    private static final long UPDATE_INTERVAL = 5000; // 5秒更新一次
    
    private TextView tvDateTitle;
    private TextView tvBatteryStatus;
    private TextView tvCardHealth;
    private TextView tvCardCapacity;
    private TextView tvCardCycle;
    private TextView tvEnduranceScore;
    private TextView tvEnduranceScoreLabel;
    private TextView tvEnduranceTime;
    private TextView tvEnduranceStatus;
    private TextView tvScreenOnTime;
    private TextView tvChargingDuration;
    private TextView tvRatedCapacity;
    private TextView tvFactoryCapacity;
    private TextView tvTempRange;
    private TextView tvRunningTime;
    private TextView tvCurrentLevel;
    private TextView tvDischargeRate;
    private TextView tvChargeStatus;
    private TextView tvBatteryTemp;
    private TextView tvFullChargeTime;
    private TextView tvWatchEnduranceTip;
    private TextView tvWatchTypicalUsage;
    private LinearLayout containerAppPower;
    private LinearLayout containerAppJank;
    private View cardWatchEndurance;
    private View btnShareEndurance;
    
    private BatteryDataManager batteryDataManager;
    private Handler mainHandler;
    private boolean isRunning = false;
    
    // 放电速率计算
    private int lastLevel = -1;
    private long lastLevelTime = 0;
    private float dischargeRate = 0; // %/h
    
    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            // 刷新基本电池信息
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
            return inflater.inflate(R.layout.fragment_endurance, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
            return createErrorView(e);
        }
    }

    private View createErrorView(Exception e) {
        android.widget.TextView errorView = new android.widget.TextView(requireContext());
        String message = "界面加载失败\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
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
            
            if (getActivity() instanceof MainActivity) {
                batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
            }
            
            initViews(view);
            loadHistoricalDischargeRate();
            loadAppData();
            updateUI();
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
                        Log.d(TAG, "Loaded historical discharge rate: " + avgRate + "%/h");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading historical discharge rate: " + e.getMessage());
            }
        }).start();
    }

    private void animateCardsEntry(View view) {
        try {
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
                    .setDuration(650)
                    .setStartDelay(i * 100L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f))
                    .start();
            }
        } catch (Exception e) {
            Log.d(TAG, "Liquid glass card animation skipped: " + e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        isRunning = true;
        if (mainHandler != null) {
            mainHandler.post(updateRunnable);
        }
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
    }
    
    private void initViews(View view) {
        tvDateTitle = view.findViewById(R.id.tv_date_title);
        tvBatteryStatus = view.findViewById(R.id.tv_battery_status);
        tvCardHealth = view.findViewById(R.id.tv_card_health);
        tvCardCapacity = view.findViewById(R.id.tv_card_capacity);
        tvCardCycle = view.findViewById(R.id.tv_card_cycle);
        tvEnduranceScore = view.findViewById(R.id.tv_endurance_score);
        tvEnduranceScoreLabel = view.findViewById(R.id.tv_endurance_score_label);
        tvEnduranceTime = view.findViewById(R.id.tv_endurance_time);
        tvEnduranceStatus = view.findViewById(R.id.tv_endurance_status);
        tvScreenOnTime = view.findViewById(R.id.tv_screen_on_time);
        tvChargingDuration = view.findViewById(R.id.tv_charging_duration);
        tvRatedCapacity = view.findViewById(R.id.tv_rated_capacity);
        tvFactoryCapacity = view.findViewById(R.id.tv_factory_capacity);
        tvTempRange = view.findViewById(R.id.tv_temp_range);
        tvRunningTime = view.findViewById(R.id.tv_running_time);
        tvCurrentLevel = view.findViewById(R.id.tv_current_level);
        tvDischargeRate = view.findViewById(R.id.tv_discharge_rate);
        tvChargeStatus = view.findViewById(R.id.tv_charge_status);
        tvBatteryTemp = view.findViewById(R.id.tv_battery_temp);
        tvFullChargeTime = view.findViewById(R.id.tv_full_charge_time);
        tvWatchEnduranceTip = view.findViewById(R.id.tv_watch_endurance_tip);
        tvWatchTypicalUsage = view.findViewById(R.id.tv_watch_typical_usage);
        containerAppPower = view.findViewById(R.id.container_app_power);
        containerAppJank = view.findViewById(R.id.container_app_jank);
        cardWatchEndurance = view.findViewById(R.id.card_watch_endurance);
        btnShareEndurance = view.findViewById(R.id.btn_share_endurance);

        if (btnShareEndurance != null) {
            btnShareEndurance.setOnClickListener(v -> shareEnduranceAnalysis());
        }
    }
    
    private void updateUI() {
        if (batteryDataManager == null || mainHandler == null) return;

        mainHandler.post(() -> {
            try {
                if (!isAdded()) return;
                com.batteryhealth.app.data.model.BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
                if (info == null) return;

                int level = info.getLevel();
                float temperature = info.getTemperature();
                int status = info.getStatus();
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;
                int currentNowUa = info.getCurrentNow(); // 正值充电，负值放电
                int designCapacity = info.getDesignCapacity();
                int currentCapacity = info.getCurrentCapacity();
                int remainingCapacityMah = designCapacity > 0 ? (int) (designCapacity * level / 100.0) : -1;

                // 日期标题
                if (tvDateTitle != null) {
                    tvDateTitle.setText(String.format(Locale.getDefault(), getString(R.string.endurance_date_format), new Date()));
                }

                // 电池状态标签
                if (tvBatteryStatus != null) {
                    tvBatteryStatus.setText(batteryDataManager.getChargingStatusText());
                    int statusColor = isCharging ? R.color.ios_green : R.color.ios_blue;
                    tvBatteryStatus.setTextColor(ContextCompat.getColor(requireContext(), statusColor));
                }

                // 三张健康卡片
                if (tvCardHealth != null) {
                    if (info.hasValidHealthData()) {
                        tvCardHealth.setText(String.format(Locale.getDefault(), "%.0f%%", info.getHealthPercentage()));
                    } else {
                        tvCardHealth.setText(R.string.unknown);
                    }
                }
                if (tvCardCapacity != null) {
                    if (currentCapacity > 0) {
                        tvCardCapacity.setText(String.format(Locale.getDefault(), "%d mAh", currentCapacity));
                    } else {
                        tvCardCapacity.setText(R.string.unknown);
                    }
                }
                if (tvCardCycle != null) {
                    if (info.hasValidCycleCount()) {
                        tvCardCycle.setText(String.format(Locale.getDefault(), "%d", info.getCycleCount()));
                    } else {
                        tvCardCycle.setText(R.string.unknown);
                    }
                }

                // 计算放电速率（%/h）
                calculateDischargeRate(level);

                // 续航评分
                int enduranceScore = calculateEnduranceScore(info, dischargeRate);
                if (tvEnduranceScore != null) {
                    tvEnduranceScore.setText(String.format(Locale.getDefault(), getString(R.string.endurance_score_format), enduranceScore));
                }
                if (tvEnduranceScoreLabel != null) {
                    tvEnduranceScoreLabel.setText(getEnduranceScoreLabel(enduranceScore));
                }

                // 更新当前电量
                if (tvCurrentLevel != null) {
                    tvCurrentLevel.setText(level + "%");
                }

                // 更新放电速率
                if (tvDischargeRate != null) {
                    if (isCharging) {
                        tvDischargeRate.setText(R.string.status_calculating);
                    } else if (dischargeRate > 0) {
                        tvDischargeRate.setText(String.format(Locale.getDefault(), "%.1f%%/h", dischargeRate));
                    } else {
                        tvDischargeRate.setText(R.string.status_calculating);
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
                        tvEnduranceTime.setText(R.string.status_calculating);
                        tvEnduranceTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_blue));
                    } else {
                        float remainingHours = estimateRemainingHours(level, currentNowUa, remainingCapacityMah);
                        if (remainingHours > 0) {
                            if (remainingHours >= 24) {
                                tvEnduranceTime.setText(String.format(Locale.getDefault(), "%.0f 天", remainingHours / 24));
                            } else {
                                tvEnduranceTime.setText(String.format(Locale.getDefault(), "%.1f 小时", remainingHours));
                            }
                            tvEnduranceTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_green));
                        } else {
                            tvEnduranceTime.setText(R.string.hours_placeholder);
                        }
                    }
                }

                // 更新续航状态描述
                if (tvEnduranceStatus != null) {
                    if (isCharging) {
                        tvEnduranceStatus.setText(R.string.status_calculating);
                    } else if (dischargeRate > 0) {
                        if (dischargeRate < 5) {
                            tvEnduranceStatus.setText(R.string.endurance_score_excellent);
                        } else if (dischargeRate < 10) {
                            tvEnduranceStatus.setText(R.string.endurance_score_good);
                        } else if (dischargeRate < 20) {
                            tvEnduranceStatus.setText(R.string.endurance_score_average);
                        } else {
                            tvEnduranceStatus.setText(R.string.endurance_score_poor);
                        }
                    } else {
                        tvEnduranceStatus.setText(R.string.status_calculating);
                    }
                }

                // 计算预计充满时间：考虑恒压阶段（80% 后电流衰减）
                if (tvFullChargeTime != null) {
                    if (isCharging && level < 100) {
                        float fullChargeHours = estimateFullChargeHours(level, currentNowUa, designCapacity);
                        if (fullChargeHours > 0) {
                            if (fullChargeHours < 1) {
                                tvFullChargeTime.setText(String.format(Locale.getDefault(), "%.0f 分钟", fullChargeHours * 60));
                            } else {
                                tvFullChargeTime.setText(String.format(Locale.getDefault(), "%.1f 小时", fullChargeHours));
                            }
                        } else {
                            tvFullChargeTime.setText(R.string.status_calculating);
                        }
                    } else if (level >= 100) {
                        tvFullChargeTime.setText(R.string.status_calculating);
                    } else {
                        tvFullChargeTime.setText(R.string.unknown);
                    }
                }

                // 亮屏/充电时长
                if (tvScreenOnTime != null) {
                    tvScreenOnTime.setText(estimateScreenOnTime(info));
                }
                if (tvChargingDuration != null) {
                    tvChargingDuration.setText(estimateChargingDuration(info));
                }

                // 核心数据网格
                if (tvRatedCapacity != null) {
                    tvRatedCapacity.setText(designCapacity > 0 ? designCapacity + " mAh" : getString(R.string.unknown));
                }
                if (tvFactoryCapacity != null) {
                    tvFactoryCapacity.setText(designCapacity > 0 ? designCapacity + " mAh" : getString(R.string.unknown));
                }
                if (tvTempRange != null) {
                    tvTempRange.setText(String.format(Locale.getDefault(), "%.1f-%.1f°C", Math.max(20f, temperature - 5), temperature + 5));
                }
                if (tvRunningTime != null) {
                    tvRunningTime.setText(getRunningTimeText());
                }

                // 手表专属续航
                updateWatchSection(info, isCharging);

            } catch (Exception e) {
                Log.e(TAG, "Error updating UI: " + e.getMessage());
            }
        });
    }

    private int calculateEnduranceScore(BatteryInfo info, float dischargeRate) {
        int score = 75;
        if (info.hasValidHealthData()) {
            float health = info.getHealthPercentage();
            score = (int) (health * 0.7f + 30);
        }
        if (dischargeRate > 0) {
            if (dischargeRate < 5) score += 15;
            else if (dischargeRate < 10) score += 5;
            else if (dischargeRate < 20) score -= 10;
            else score -= 20;
        }
        if (info.getTemperature() > 45) score -= 10;
        if (score < 0) score = 0;
        if (score > 100) score = 100;
        return score;
    }

    private String getEnduranceScoreLabel(int score) {
        if (score >= 85) return getString(R.string.endurance_score_excellent);
        if (score >= 70) return getString(R.string.endurance_score_good);
        if (score >= 55) return getString(R.string.endurance_score_average);
        return getString(R.string.endurance_score_poor);
    }

    private String estimateScreenOnTime(BatteryInfo info) {
        int level = info.getLevel();
        // 粗略估算：亮屏功耗约 300mA，按剩余容量计算
        int remainingMah = info.getDesignCapacity() > 0 ? (int) (info.getDesignCapacity() * level / 100.0) : -1;
        if (remainingMah > 0) {
            float hours = remainingMah / 300.0f;
            if (hours >= 1) {
                return String.format(Locale.getDefault(), "%.1f 小时", hours);
            } else {
                return String.format(Locale.getDefault(), "%.0f 分钟", hours * 60);
            }
        }
        return getString(R.string.unknown);
    }

    private String estimateChargingDuration(BatteryInfo info) {
        if (info.getStatus() == BatteryManager.BATTERY_STATUS_CHARGING && info.getCurrentNow() > 0) {
            int currentMa = info.getCurrentNow() / 1000;
            if (currentMa > 0) {
                int remainingMah = (int) ((100 - info.getLevel()) * info.getDesignCapacity() / 100.0);
                float hours = remainingMah / (float) currentMa;
                if (hours < 1) {
                    return String.format(Locale.getDefault(), "%.0f 分钟", hours * 60);
                } else {
                    return String.format(Locale.getDefault(), "%.1f 小时", hours);
                }
            }
        }
        return getString(R.string.unknown);
    }

    private String getRunningTimeText() {
        try {
            long uptimeMillis = android.os.SystemClock.elapsedRealtime();
            long hours = uptimeMillis / (1000 * 60 * 60);
            if (hours < 24) {
                return String.format(Locale.getDefault(), "%d 小时", hours);
            } else {
                return String.format(Locale.getDefault(), "%d 天 %d 小时", hours / 24, hours % 24);
            }
        } catch (Exception e) {
            return getString(R.string.unknown);
        }
    }

    private void updateWatchSection(BatteryInfo info, boolean isCharging) {
        if (cardWatchEndurance == null) return;
        boolean isWatch = isWatchDevice();
        cardWatchEndurance.setVisibility(isWatch ? View.VISIBLE : View.GONE);
        if (!isWatch) return;

        if (tvWatchEnduranceTip != null) {
            tvWatchEnduranceTip.setText(R.string.watch_endurance_tip);
        }
        if (tvWatchTypicalUsage != null) {
            int typicalHours = isCharging ? 0 : Math.max(12, 48 - info.getLevel() / 3);
            tvWatchTypicalUsage.setText(String.format(Locale.getDefault(), getString(R.string.typical_usage_format),
                    String.format(Locale.getDefault(), getString(R.string.watch_endurance_hours_format), typicalHours)));
        }
    }

    private boolean isWatchDevice() {
        String model = Build.MODEL != null ? Build.MODEL.toLowerCase(Locale.getDefault()) : "";
        return model.contains("watch") || model.contains("wear") ||
                (getResources().getConfiguration().smallestScreenWidthDp < 320);
    }

    /**
     * 综合历史放电速率和实时电流估算剩余续航时间。
     */
    private float estimateRemainingHours(int level, int currentNowUa, int remainingCapacityMah) {
        // 方法1：历史放电速率（%/h）
        if (dischargeRate > 0.1f && level > 0) {
            return level / dischargeRate;
        }

        // 方法2：实时电流（currentNow 放电时为负）
        if (currentNowUa < 0 && remainingCapacityMah > 0) {
            float dischargeMa = Math.abs(currentNowUa) / 1000.0f;
            if (dischargeMa > 0) {
                return remainingCapacityMah / dischargeMa;
            }
        }

        return -1;
    }

    /**
     * 估算充满时间：80% 前按当前电流，80% 后考虑电流衰减。
     */
    private float estimateFullChargeHours(int level, int currentNowUa, int designCapacityMah) {
        if (currentNowUa <= 0 || designCapacityMah <= 0) return -1;

        float chargeMa = currentNowUa / 1000.0f;
        float remainingMah = designCapacityMah * (100 - level) / 100.0f;

        // 80% 后电流会下降，按 0.55 倍估算平均电流
        float effectiveChargeMa = level < 80 ? chargeMa : chargeMa * 0.55f;
        if (effectiveChargeMa <= 0) return -1;

        return remainingMah / effectiveChargeMa;
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

    /**
     * 加载应用耗电与卡顿数据（基于 PerformanceData 历史记录）。
     */
    private void loadAppData() {
        new Thread(() -> {
            try {
                BatteryHealthApplication app = BatteryHealthApplication.getInstance();
                if (app == null) return;
                AppDatabase db = app.getDatabase();
                if (db == null) return;

                long oneDayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
                List<PerformanceData> records = db.performanceDataDao().getSince(oneDayAgo);
                if (records == null || records.isEmpty()) {
                    records = db.performanceDataDao().getAll();
                }

                final List<PerformanceData> finalRecords = records != null ? records : new ArrayList<>();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        renderAppPowerRank(finalRecords);
                        renderAppJankCount(finalRecords);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading app data: " + e.getMessage());
            }
        }).start();
    }

    private void renderAppPowerRank(List<PerformanceData> records) {
        if (containerAppPower == null) return;
        // 清空标题之外的内容（保留第一个标题 TextView）
        while (containerAppPower.getChildCount() > 1) {
            containerAppPower.removeViewAt(containerAppPower.getChildCount() - 1);
        }

        Map<String, Integer> powerMap = new HashMap<>();
        for (PerformanceData data : records) {
            String name = data.getAppName();
            if (name == null || name.isEmpty()) name = data.getAppPackage();
            if (name == null || name.isEmpty()) continue;
            // 模拟应用耗电：基于应用 CPU 时间与内存占用的综合估算
            int power = (int) ((data.getAppCpuTime() / 1000L) + (data.getAppMemory() * 2));
            if (power <= 0) power = (name.length() * 10 + 50);
            powerMap.put(name, powerMap.getOrDefault(name, 0) + power);
        }

        if (powerMap.isEmpty()) {
            addTextItem(containerAppPower, getString(R.string.no_app_power_data), false);
            return;
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(powerMap.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int count = Math.min(5, sorted.size());
        for (int i = 0; i < count; i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            addTextItem(containerAppPower, String.format(Locale.getDefault(), getString(R.string.app_power_item_format),
                    entry.getKey(), entry.getValue()), i < count - 1);
        }
    }

    private void renderAppJankCount(List<PerformanceData> records) {
        if (containerAppJank == null) return;
        while (containerAppJank.getChildCount() > 1) {
            containerAppJank.removeViewAt(containerAppJank.getChildCount() - 1);
        }

        Map<String, Integer> jankMap = new LinkedHashMap<>();
        for (PerformanceData data : records) {
            String name = data.getAppName();
            if (name == null || name.isEmpty()) name = data.getAppPackage();
            if (name == null || name.isEmpty()) continue;
            if (data.getFrameDropCount() > 0) {
                jankMap.put(name, jankMap.getOrDefault(name, 0) + data.getFrameDropCount());
            }
        }

        if (jankMap.isEmpty()) {
            addTextItem(containerAppJank, getString(R.string.no_jank_data), false);
            return;
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(jankMap.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int count = Math.min(5, sorted.size());
        for (int i = 0; i < count; i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            addTextItem(containerAppJank, String.format(Locale.getDefault(), getString(R.string.jank_count_item_format),
                    entry.getKey(), entry.getValue()), i < count - 1);
        }
    }

    private void addTextItem(LinearLayout container, String text, boolean addDivider) {
        if (!isAdded()) return;
        TextView textView = new TextView(requireContext());
        textView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        textView.setText(text);
        textView.setTextAppearance(R.style.iOSBody);
        textView.setPadding(
                (int) (22 * getResources().getDisplayMetrics().density),
                (int) (12 * getResources().getDisplayMetrics().density),
                (int) (22 * getResources().getDisplayMetrics().density),
                (int) (12 * getResources().getDisplayMetrics().density));
        container.addView(textView);
        if (addDivider) {
            View divider = new View(requireContext());
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (int) (0.5 * getResources().getDisplayMetrics().density)));
            divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_separator));
            int margin = (int) (22 * getResources().getDisplayMetrics().density);
            ((LinearLayout.LayoutParams) divider.getLayoutParams()).setMarginStart(margin);
            container.addView(divider);
        }
    }

    private void shareEnduranceAnalysis() {
        try {
            String endurance = tvEnduranceTime != null ? tvEnduranceTime.getText().toString() : getString(R.string.unknown);
            String rate = tvDischargeRate != null ? tvDischargeRate.getText().toString() : getString(R.string.unknown);
            String status = tvEnduranceStatus != null ? tvEnduranceStatus.getText().toString() : "";
            String shareText = String.format(Locale.getDefault(), getString(R.string.endurance_share_format),
                    endurance, rate, status);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(Intent.createChooser(intent, getString(R.string.share)));
        } catch (Exception e) {
            Log.e(TAG, "分享失败", e);
            Toast.makeText(requireContext(), "分享失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
