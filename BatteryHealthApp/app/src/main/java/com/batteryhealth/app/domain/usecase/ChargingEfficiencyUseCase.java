package com.batteryhealth.app.domain.usecase;

import com.batteryhealth.app.data.model.PowerHistory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 充电效率分析 UseCase
 *
 * 功能：
 * 1. 分析每次充电会话的效率指标
 * 2. 计算总充电量、总能量、平均效率、各阶段占比
 * 3. 识别快充阶段功率峰值、涓流阶段时长
 * 4. 效率评分：基于与理论值的偏差
 */
public class ChargingEfficiencyUseCase {

    private static final float THEORETICAL_EFFICIENCY = 95f;
    private static final float DESIGN_CAPACITY_MAH = 4000f;

    public Result execute(List<PowerHistory> allRecords, float batteryCapacityMah) {
        Result result = new Result();

        if (allRecords == null || allRecords.isEmpty()) {
            result.hasData = false;
            return result;
        }

        result.hasData = true;
        float capacity = batteryCapacityMah > 0 ? batteryCapacityMah : DESIGN_CAPACITY_MAH;

        Map<String, List<PowerHistory>> sessionMap = groupBySession(allRecords);
        List<SessionEfficiency> sessions = new ArrayList<>();

        for (Map.Entry<String, List<PowerHistory>> entry : sessionMap.entrySet()) {
            List<PowerHistory> records = entry.getValue();
            if (records == null || records.size() < 2) continue;

            SessionEfficiency se = analyzeSession(records, capacity);
            if (se != null) {
                se.sessionId = entry.getKey();
                sessions.add(se);
            }
        }

        Collections.sort(sessions, new Comparator<SessionEfficiency>() {
            @Override
            public int compare(SessionEfficiency a, SessionEfficiency b) {
                return Long.compare(b.startTime, a.startTime);
            }
        });

        result.allSessions = sessions;

        if (sessions.isEmpty()) {
            result.hasData = false;
            return result;
        }

        if (sessions.size() > 5) {
            result.recentSessions = new ArrayList<>(sessions.subList(0, 5));
        } else {
            result.recentSessions = new ArrayList<>(sessions);
        }

        calculateOverallStats(result, sessions);
        calculateEfficiencyScore(result);

        return result;
    }

    private Map<String, List<PowerHistory>> groupBySession(List<PowerHistory> records) {
        Map<String, List<PowerHistory>> sessionMap = new LinkedHashMap<>();
        for (PowerHistory r : records) {
            String sid = r.getSessionId();
            if (sid == null || sid.isEmpty()) sid = "unknown_" + r.getTimestamp();
            if (!sessionMap.containsKey(sid)) {
                sessionMap.put(sid, new ArrayList<PowerHistory>());
            }
            sessionMap.get(sid).add(r);
        }
        return sessionMap;
    }

    private SessionEfficiency analyzeSession(List<PowerHistory> records, float batteryCapacityMah) {
        if (records == null || records.size() < 2) return null;

        Collections.sort(records, new Comparator<PowerHistory>() {
            @Override
            public int compare(PowerHistory a, PowerHistory b) {
                return Long.compare(a.getTimestamp(), b.getTimestamp());
            }
        });

        SessionEfficiency se = new SessionEfficiency();
        se.startTime = records.get(0).getTimestamp();
        se.endTime = records.get(records.size() - 1).getTimestamp();
        se.durationMs = se.endTime - se.startTime;
        se.startLevel = records.get(0).getBatteryLevel();
        se.endLevel = records.get(records.size() - 1).getBatteryLevel();
        se.levelGain = Math.max(0, se.endLevel - se.startLevel);

        float totalEnergyWh = 0;
        float peakPower = 0;
        long trickleDuration = 0;
        long ccDuration = 0;
        long cvDuration = 0;
        long trickleStartTime = -1;
        long ccStartTime = -1;
        long cvStartTime = -1;
        String prevPhase = null;

        for (int i = 0; i < records.size(); i++) {
            PowerHistory r = records.get(i);
            float power = r.getPower();
            if (power > peakPower) peakPower = power;

            long intervalMs = 60000;
            if (i > 0) {
                intervalMs = r.getTimestamp() - records.get(i - 1).getTimestamp();
            }
            totalEnergyWh += power * (intervalMs / (1000f * 60 * 60));

            String phase = r.getChargingPhase();
            if (phase == null) phase = "constant_current";

            if ("trickle".equals(phase)) {
                if (trickleStartTime < 0) trickleStartTime = r.getTimestamp();
                if (prevPhase != null && !"trickle".equals(prevPhase)) {
                    trickleStartTime = r.getTimestamp();
                }
            } else if (trickleStartTime > 0 && !"trickle".equals(phase)) {
                trickleDuration += r.getTimestamp() - trickleStartTime;
                trickleStartTime = -1;
            }

            if ("constant_current".equals(phase)) {
                if (ccStartTime < 0) ccStartTime = r.getTimestamp();
                if (prevPhase != null && !"constant_current".equals(prevPhase)) {
                    ccStartTime = r.getTimestamp();
                }
            } else if (ccStartTime > 0 && !"constant_current".equals(phase)) {
                ccDuration += r.getTimestamp() - ccStartTime;
                ccStartTime = -1;
            }

            if ("constant_voltage".equals(phase)) {
                if (cvStartTime < 0) cvStartTime = r.getTimestamp();
                if (prevPhase != null && !"constant_voltage".equals(prevPhase)) {
                    cvStartTime = r.getTimestamp();
                }
            } else if (cvStartTime > 0 && !"constant_voltage".equals(phase)) {
                cvDuration += r.getTimestamp() - cvStartTime;
                cvStartTime = -1;
            }

            prevPhase = phase;
        }

        if (trickleStartTime > 0) {
            trickleDuration += records.get(records.size() - 1).getTimestamp() - trickleStartTime;
        }
        if (ccStartTime > 0) {
            ccDuration += records.get(records.size() - 1).getTimestamp() - ccStartTime;
        }
        if (cvStartTime > 0) {
            cvDuration += records.get(records.size() - 1).getTimestamp() - cvStartTime;
        }

        se.totalEnergyWh = totalEnergyWh;
        se.peakPower = peakPower;
        se.trickleDurationMs = Math.max(0, trickleDuration);
        se.constantCurrentDurationMs = Math.max(0, ccDuration);
        se.constantVoltageDurationMs = Math.max(0, cvDuration);

        float totalPhaseDuration = se.trickleDurationMs + se.constantCurrentDurationMs + se.constantVoltageDurationMs;
        if (totalPhaseDuration <= 0) totalPhaseDuration = se.durationMs;

        se.trickleRatio = totalPhaseDuration > 0 ? (se.trickleDurationMs / totalPhaseDuration) * 100f : 0;
        se.constantCurrentRatio = totalPhaseDuration > 0 ? (se.constantCurrentDurationMs / totalPhaseDuration) * 100f : 0;
        se.constantVoltageRatio = totalPhaseDuration > 0 ? (se.constantVoltageDurationMs / totalPhaseDuration) * 100f : 0;

        float chargedMah = (se.levelGain / 100f) * batteryCapacityMah;
        se.chargedMah = chargedMah;

        float theoreticalEnergyWh = (chargedMah / 1000f) * 4.2f * 0.95f;
        se.avgEfficiency = totalEnergyWh > 0 ? (theoreticalEnergyWh / totalEnergyWh) * 100f : THEORETICAL_EFFICIENCY;
        se.avgEfficiency = Math.min(se.avgEfficiency, 100f);

        return se;
    }

    private void calculateOverallStats(Result result, List<SessionEfficiency> sessions) {
        float totalChargedMah = 0;
        float totalEnergyWh = 0;
        float efficiencySum = 0;
        float totalTrickleRatio = 0;
        float totalCCRatio = 0;
        float totalCVRatio = 0;
        int validCount = 0;

        for (SessionEfficiency se : sessions) {
            totalChargedMah += se.chargedMah;
            totalEnergyWh += se.totalEnergyWh;
            efficiencySum += se.avgEfficiency;
            totalTrickleRatio += se.trickleRatio;
            totalCCRatio += se.constantCurrentRatio;
            totalCVRatio += se.constantVoltageRatio;
            validCount++;
        }

        result.totalChargedMah = totalChargedMah;
        result.totalEnergyWh = totalEnergyWh;
        result.avgEfficiency = validCount > 0 ? efficiencySum / validCount : 0;
        result.avgTrickleRatio = validCount > 0 ? totalTrickleRatio / validCount : 0;
        result.avgConstantCurrentRatio = validCount > 0 ? totalCCRatio / validCount : 0;
        result.avgConstantVoltageRatio = validCount > 0 ? totalCVRatio / validCount : 0;
        result.sessionCount = validCount;
    }

    private void calculateEfficiencyScore(Result result) {
        if (result.avgEfficiency <= 0) {
            result.efficiencyScore = 0;
            result.efficiencyGrade = "--";
            result.efficiencyComment = "暂无数据";
            return;
        }

        float efficiency = Math.min(result.avgEfficiency, 100f);
        float deviation = Math.abs(THEORETICAL_EFFICIENCY - efficiency);

        if (deviation <= 2f) {
            result.efficiencyScore = 95;
            result.efficiencyGrade = "A+";
            result.efficiencyComment = "充电效率极佳，接近理论值";
        } else if (deviation <= 5f) {
            result.efficiencyScore = 85;
            result.efficiencyGrade = "A";
            result.efficiencyComment = "充电效率良好，处于正常范围";
        } else if (deviation <= 10f) {
            result.efficiencyScore = 70;
            result.efficiencyGrade = "B";
            result.efficiencyComment = "充电效率一般，建议检查充电器";
        } else if (deviation <= 15f) {
            result.efficiencyScore = 55;
            result.efficiencyGrade = "C";
            result.efficiencyComment = "充电效率偏低，建议使用原装充电器";
        } else {
            result.efficiencyScore = 40;
            result.efficiencyGrade = "D";
            result.efficiencyComment = "充电效率较差，建议检查电池和充电设备";
        }
    }

    // ======================== 内部数据类 ========================

    public static class Result {
        public boolean hasData;
        public int sessionCount;
        public float totalChargedMah;
        public float totalEnergyWh;
        public float avgEfficiency;
        public float avgTrickleRatio;
        public float avgConstantCurrentRatio;
        public float avgConstantVoltageRatio;
        public int efficiencyScore;
        public String efficiencyGrade;
        public String efficiencyComment;
        public List<SessionEfficiency> recentSessions;
        public List<SessionEfficiency> allSessions;

        public Result() {
            recentSessions = new ArrayList<>();
            allSessions = new ArrayList<>();
            efficiencyGrade = "--";
            efficiencyComment = "";
        }
    }

    public static class SessionEfficiency {
        public String sessionId;
        public long startTime;
        public long endTime;
        public long durationMs;
        public int startLevel;
        public int endLevel;
        public int levelGain;
        public float chargedMah;
        public float totalEnergyWh;
        public float avgEfficiency;
        public float peakPower;
        public long trickleDurationMs;
        public long constantCurrentDurationMs;
        public long constantVoltageDurationMs;
        public float trickleRatio;
        public float constantCurrentRatio;
        public float constantVoltageRatio;
    }
}
