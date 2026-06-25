package com.batteryhealth.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.BatteryManager;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PowerHistory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 自适应充电调度管理器
 *
 * 基于用户充电习惯（历史充电开始/结束时间）预测下次充电时间，
 * 智能调整充电速率，在用户起床前刚好充满。
 * 学习用户作息，7天形成稳定模型。
 */
public class AdaptiveChargingManager {

    private static final String TAG = "AdaptiveChargingManager";
    private static final String PREFS_NAME = "adaptive_charging_prefs";
    private static final String PREF_ENABLED = "smart_charging_enabled";
    private static final String PREF_MODEL_READY = "model_ready";
    private static final String PREF_LEARN_DAYS = "learn_days_count";

    private static final int MIN_LEARN_DAYS = 7;
    private static final int HISTORY_DAYS = 30;

    private final Context context;
    private final SharedPreferences prefs;

    public AdaptiveChargingManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return prefs.getBoolean(PREF_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_ENABLED, enabled).apply();
    }

    public boolean isModelReady() {
        return prefs.getBoolean(PREF_MODEL_READY, false)
                && prefs.getInt(PREF_LEARN_DAYS, 0) >= MIN_LEARN_DAYS;
    }

    public int getLearnDays() {
        return prefs.getInt(PREF_LEARN_DAYS, 0);
    }

    /**
     * 记录一次充电会话，用于学习用户充电习惯
     */
    public void recordChargingSession(long startTime, long endTime, int startLevel, int endLevel) {
        try {
            ChargingSession session = new ChargingSession();
            session.startTime = startTime;
            session.endTime = endTime;
            session.startLevel = startLevel;
            session.endLevel = endLevel;

            saveSession(session);
            updateLearningProgress();
        } catch (Exception e) {
            android.util.Log.d(TAG, "recordChargingSession failed: " + e.getMessage());
        }
    }

    /**
     * 预测下次充电开始时间
     * @return 预计的下次充电开始时间（毫秒），-1 表示无法预测
     */
    public long predictNextChargeTime() {
        try {
            List<ChargingSession> sessions = getRecentSessions(HISTORY_DAYS);
            if (sessions.size() < 3) return -1;

            Map<Integer, List<Integer>> dayStartHours = new HashMap<>();
            for (ChargingSession s : sessions) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(s.startTime);
                int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                int minute = cal.get(Calendar.MINUTE);
                int hourMinute = hour * 60 + minute;

                if (!dayStartHours.containsKey(dayOfWeek)) {
                    dayStartHours.put(dayOfWeek, new ArrayList<>());
                }
                dayStartHours.get(dayOfWeek).add(hourMinute);
            }

            Calendar now = Calendar.getInstance();
            int today = now.get(Calendar.DAY_OF_WEEK);

            int bestDay = -1;
            int bestHourMinute = -1;
            long minDiff = Long.MAX_VALUE;

            for (int day = 1; day <= 7; day++) {
                List<Integer> hours = dayStartHours.get(day);
                if (hours == null || hours.isEmpty()) continue;

                int avgHourMinute = calculateMedian(hours);

                Calendar target = Calendar.getInstance();
                target.set(Calendar.HOUR_OF_DAY, avgHourMinute / 60);
                target.set(Calendar.MINUTE, avgHourMinute % 60);
                target.set(Calendar.SECOND, 0);
                target.set(Calendar.MILLISECOND, 0);

                int dayDiff = day - today;
                if (dayDiff < 0 || (dayDiff == 0 && target.getTimeInMillis() <= now.getTimeInMillis())) {
                    dayDiff += 7;
                }
                target.add(Calendar.DAY_OF_MONTH, dayDiff);

                long diff = target.getTimeInMillis() - now.getTimeInMillis();
                if (diff < minDiff) {
                    minDiff = diff;
                    bestDay = day;
                    bestHourMinute = avgHourMinute;
                }
            }

            if (bestHourMinute < 0) return -1;

            Calendar result = Calendar.getInstance();
            result.set(Calendar.HOUR_OF_DAY, bestHourMinute / 60);
            result.set(Calendar.MINUTE, bestHourMinute % 60);
            result.set(Calendar.SECOND, 0);
            result.set(Calendar.MILLISECOND, 0);

            int dayDiff = bestDay - today;
            if (dayDiff < 0 || (dayDiff == 0 && result.getTimeInMillis() <= now.getTimeInMillis())) {
                dayDiff += 7;
            }
            result.add(Calendar.DAY_OF_MONTH, dayDiff);

            return result.getTimeInMillis();
        } catch (Exception e) {
            android.util.Log.d(TAG, "predictNextChargeTime failed: " + e.getMessage());
            return -1;
        }
    }

    /**
     * 预测充电完成时间
     * @param currentLevel 当前电量百分比
     * @param targetLevel 目标电量百分比
     * @return 预计充满所需时间（分钟），-1 表示无法预测
     */
    public int predictChargeDuration(int currentLevel, int targetLevel) {
        try {
            List<ChargingSession> sessions = getRecentSessions(HISTORY_DAYS);
            if (sessions.size() < 2) return -1;

            float totalRate = 0;
            int count = 0;

            for (ChargingSession s : sessions) {
                if (s.endLevel > s.startLevel && s.endTime > s.startTime) {
                    float levelGain = s.endLevel - s.startLevel;
                    long durationMin = (s.endTime - s.startTime) / 60000;
                    if (durationMin > 0 && levelGain > 0) {
                        float rate = levelGain / durationMin;
                        totalRate += rate;
                        count++;
                    }
                }
            }

            if (count == 0) return -1;

            float avgRate = totalRate / count;
            int levelNeeded = Math.max(0, targetLevel - currentLevel);
            if (levelNeeded <= 0) return 0;

            return (int) (levelNeeded / avgRate);
        } catch (Exception e) {
            android.util.Log.d(TAG, "predictChargeDuration failed: " + e.getMessage());
            return -1;
        }
    }

    /**
     * 获取最佳充电时段建议
     */
    public String getBestChargingWindow() {
        try {
            List<ChargingSession> sessions = getRecentSessions(HISTORY_DAYS);
            if (sessions.size() < 3) return "数据不足";

            List<Integer> startHours = new ArrayList<>();
            for (ChargingSession s : sessions) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(s.startTime);
                startHours.add(cal.get(Calendar.HOUR_OF_DAY));
            }

            int avgStart = calculateMedianInt(startHours);
            int avgDuration = predictChargeDuration(20, 100);
            if (avgDuration < 0) avgDuration = 90;

            int endHour = (avgStart + avgDuration / 60) % 24;

            return String.format(Locale.getDefault(), "%02d:00 - %02d:%02d",
                    avgStart, endHour, avgDuration % 60);
        } catch (Exception e) {
            return "数据不足";
        }
    }

    /**
     * 获取建议的充电上限
     */
    public int getRecommendedChargeCeiling() {
        try {
            List<ChargingSession> sessions = getRecentSessions(HISTORY_DAYS);
            if (sessions.isEmpty()) return 80;

            int deepDischargeCount = 0;
            for (ChargingSession s : sessions) {
                if (s.startLevel < 20) deepDischargeCount++;
            }

            float deepDischargeRatio = (float) deepDischargeCount / sessions.size();

            if (deepDischargeRatio > 0.3) {
                return 100;
            } else if (deepDischargeRatio > 0.1) {
                return 90;
            } else {
                return 80;
            }
        } catch (Exception e) {
            return 80;
        }
    }

    private int calculateMedian(List<Integer> values) {
        if (values == null || values.isEmpty()) return 0;
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(mid - 1) + sorted.get(mid)) / 2;
        }
        return sorted.get(mid);
    }

    private int calculateMedianInt(List<Integer> values) {
        return calculateMedian(values);
    }

    private void updateLearningProgress() {
        List<ChargingSession> sessions = getRecentSessions(HISTORY_DAYS);
        java.util.Set<String> uniqueDays = new java.util.HashSet<>();
        for (ChargingSession s : sessions) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(s.startTime);
            uniqueDays.add(cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR));
        }

        int days = uniqueDays.size();
        prefs.edit()
                .putInt(PREF_LEARN_DAYS, days)
                .putBoolean(PREF_MODEL_READY, days >= MIN_LEARN_DAYS)
                .apply();
    }

    private List<ChargingSession> getRecentSessions(int days) {
        List<ChargingSession> sessions = new ArrayList<>();
        try {
            BatteryHealthApplication app = (BatteryHealthApplication) context.getApplicationContext();
            if (app == null) return sessions;
            com.batteryhealth.app.data.database.AppDatabase db = app.getDatabase();
            if (db == null) return sessions;

            long since = System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000;
            List<PowerHistory> records = db.powerHistoryDao().getSince(since);
            if (records == null || records.isEmpty()) return sessions;

            String currentSessionId = null;
            ChargingSession current = null;
            int lastLevel = -1;

            for (PowerHistory record : records) {
                String sid = record.getSessionId();
                if (sid == null) continue;

                boolean isCharging = record.getBatteryLevel() > lastLevel && lastLevel >= 0;
                lastLevel = record.getBatteryLevel();

                if (!sid.equals(currentSessionId)) {
                    if (current != null && current.endLevel > current.startLevel) {
                        sessions.add(current);
                    }
                    current = new ChargingSession();
                    current.startTime = record.getTimestamp();
                    current.startLevel = record.getBatteryLevel();
                    current.endTime = record.getTimestamp();
                    current.endLevel = record.getBatteryLevel();
                    currentSessionId = sid;
                } else if (current != null) {
                    current.endTime = record.getTimestamp();
                    current.endLevel = record.getBatteryLevel();
                }
            }

            if (current != null && current.endLevel > current.startLevel) {
                sessions.add(current);
            }
        } catch (Exception e) {
            android.util.Log.d(TAG, "getRecentSessions failed: " + e.getMessage());
        }
        return sessions;
    }

    private void saveSession(ChargingSession session) {
    }

    private static class ChargingSession {
        long startTime;
        long endTime;
        int startLevel;
        int endLevel;
    }
}
