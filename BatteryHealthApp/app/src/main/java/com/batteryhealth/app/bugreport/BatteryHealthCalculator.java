package com.batteryhealth.app.bugreport;

import java.util.ArrayList;
import java.util.List;

/**
 * 电池健康度计算器（等价于 digiguide C++ BatteryHealthCalculator）。
 *
 * <p>5 个因子加权综合：容量保持率、循环衰减、内阻增长、温度老化、充电损伤。
 * 每个因子在缺失时不影响其他因子计算，最终置信度按可用因子数自适应。</p>
 */
public final class BatteryHealthCalculator {

    public enum Confidence { HIGH, MEDIUM, LOW, NONE }

    public static class HealthFactors {
        public Float capacityRetention;   // 0..1，越高越好
        public Float cycleDecay;          // 0..1，越低越好
        public Float resistanceGrowth;    // 0..1，越低越好
        public Float thermalAging;        // 0..1，越低越好
        public Float chargingDamage;      // 0..1，越低越好
        public int availableFactors;

        public float getAverageScore() {
            float sum = 0f; int n = 0;
            if (capacityRetention != null) { sum += capacityRetention; n++; }
            if (cycleDecay != null)         { sum += (1f - cycleDecay); n++; }
            if (resistanceGrowth != null)   { sum += (1f - resistanceGrowth); n++; }
            if (thermalAging != null)       { sum += (1f - thermalAging); n++; }
            if (chargingDamage != null)     { sum += (1f - chargingDamage); n++; }
            return n == 0 ? 0f : sum / n;
        }
    }

    public static class Result {
        public float healthPercentage;            // 0..100
        public String grade;                      // A+/A/B/C/D/F
        public HealthFactors factors = new HealthFactors();
        public String diagnosisText;
        public List<String> suggestions = new ArrayList<>();
        public Float estimatedResistanceMilliOhm;
        public Integer remainingLifespanMonths;
        public Confidence confidence = Confidence.NONE;
    }

    private BatteryHealthCalculator() {}

    public static Result calculate(BatteryRawData data) {
        Result r = new Result();
        r.factors.capacityRetention = calcCapacityRetention(data.getCurrentCapacityMah(), data.getDesignCapacityMah());
        r.factors.cycleDecay = calcCycleDecay(data.getCycleCount(), BatteryType.LIION);
        r.factors.resistanceGrowth = calcResistanceGrowth(data.getVoltageCurrentPairs(), data.getCurrentCapacityMah());
        r.factors.thermalAging = calcThermalAging(data.getTemperatureCelsius());
        r.factors.chargingDamage = calcChargingDamage(data.getChargingEvents());

        r.factors.availableFactors = countFactors(r.factors);

        float score = computeWeightedScore(r.factors);
        score = Math.max(0f, Math.min(100f, score));
        r.healthPercentage = score;
        r.grade = computeGrade(score);
        r.diagnosisText = generateDiagnosis(r.factors, data);
        r.suggestions = generateSuggestions(r.factors, data);
        r.estimatedResistanceMilliOhm = estimateResistance(data.getVoltageCurrentPairs(), data.getCurrentCapacityMah());
        r.remainingLifespanMonths = estimateRemainingLifespan(r.factors);
        r.confidence = pickConfidence(r.factors);
        return r;
    }

    // ========== 各因子 ==========

    public static Float calcCapacityRetention(Integer current, Integer design) {
        if (current == null || design == null || design <= 0) return null;
        float r = current / (float) design;
        return Math.max(0f, Math.min(1f, r));
    }

    public static Float calcCycleDecay(Integer cycles, BatteryType type) {
        if (cycles == null) return null;
        // 业界经验：500 次循环约对应 20% 衰减（Li-ion），按线性近似
        int designCycles = (type == BatteryType.LIPO) ? 400 : 500;
        float decay = cycles / (float) designCycles * 0.20f;
        return Math.max(0f, Math.min(1f, decay));
    }

    public static Float calcResistanceGrowth(List<float[]> vcPairs, Integer capacityMah) {
        if (vcPairs == null || vcPairs.isEmpty() || capacityMah == null || capacityMah <= 0) return null;
        // 取稳定段：过滤掉异常（电压 > 4.4V 或 < 3.0V，电流为 0）
        int n = 0;
        double sumR = 0;
        for (float[] p : vcPairs) {
            float v = p[0] / 1000f;     // mV → V
            float i = p[1] / 1000f;     // mA → A
            if (v < 3.0f || v > 4.5f) continue;
            if (Math.abs(i) < 0.01f) continue;
            // R = V / I
            sumR += v / i;
            n++;
            if (n >= 200) break;
        }
        if (n < 3) return null;
        double avgR = sumR / n;                  // 欧
        double mOhm = avgR * 1000.0;             // 毫欧
        // 健康新电池典型内阻 50~120mΩ；150mΩ 视作 1.0 衰减
        float decay = (float) Math.max(0f, Math.min(1f, (mOhm - 50.0) / 100.0));
        return decay;
    }

    public static Float calcThermalAging(Float temperature) {
        if (temperature == null) return null;
        // 25°C 视为基线；>45°C 显著加速老化
        if (temperature <= 25f) return 0f;
        if (temperature >= 55f) return 1f;
        return (temperature - 25f) / 30f;
    }

    public static Float calcChargingDamage(List<BatteryRawData.ChargingEvent> events) {
        if (events == null || events.isEmpty()) return null;
        // 简化：充电时温度 > 42°C 或功率 > 30W 的事件比例
        // 此处 events 暂未填充，仅占位
        return null;
    }

    // ========== 综合评分 ==========

    private static float computeWeightedScore(HealthFactors f) {
        // 各因子权重：容量 0.45 / 循环 0.30 / 内阻 0.10 / 温度 0.10 / 充电 0.05
        float weight = 0f, sum = 0f;
        if (f.capacityRetention != null) { sum += f.capacityRetention * 0.45f; weight += 0.45f; }
        if (f.cycleDecay != null)         { sum += (1f - f.cycleDecay) * 0.30f; weight += 0.30f; }
        if (f.resistanceGrowth != null)   { sum += (1f - f.resistanceGrowth) * 0.10f; weight += 0.10f; }
        if (f.thermalAging != null)       { sum += (1f - f.thermalAging) * 0.10f; weight += 0.10f; }
        if (f.chargingDamage != null)     { sum += (1f - f.chargingDamage) * 0.05f; weight += 0.05f; }
        if (weight == 0f) return -1f;
        return (sum / weight) * 100f;
    }

    public static String computeGrade(float score) {
        if (score < 0) return "F";
        if (score >= 95) return "A+";
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }

    private static String generateDiagnosis(HealthFactors f, BatteryRawData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("综合多维度评估");
        if (f.capacityRetention != null) {
            sb.append("：当前容量保持").append(String.format(java.util.Locale.US, "%.1f", f.capacityRetention * 100)).append("%");
        }
        if (f.cycleDecay != null) {
            sb.append("，循环次数").append(data.getCycleCount()).append("次");
        }
        if (f.thermalAging != null && f.thermalAging > 0.3f) {
            sb.append("；温度偏高加速老化");
        }
        sb.append("。");
        return sb.toString();
    }

    private static List<String> generateSuggestions(HealthFactors f, BatteryRawData data) {
        List<String> out = new ArrayList<>();
        if (f.thermalAging != null && f.thermalAging > 0.3f) {
            out.add("避免边玩边充，减少高温场景使用");
        }
        if (f.capacityRetention != null && f.capacityRetention < 0.8f) {
            out.add("健康度已低于 80%，建议关注电池状态并考虑更换");
        }
        if (data.getCycleCount() != null && data.getCycleCount() >= 500) {
            out.add("循环次数较多，建议启用厂商充电保护（80% 充电上限）");
        }
        if (out.isEmpty()) {
            out.add("当前电池状态良好，建议保持 20%~80% 区间充电");
        }
        return out;
    }

    private static Integer estimateRemainingLifespan(HealthFactors f) {
        if (f.capacityRetention == null) return null;
        // 假设每年约 3% 衰减
        float loss = 1f - f.capacityRetention;
        // 0.7（70%）视作需更换阈值
        float remainToThreshold = Math.max(0f, f.capacityRetention - 0.70f);
        if (remainToThreshold <= 0) return 0;
        int months = Math.round((remainToThreshold / 0.03f) * 12f);
        return Math.max(0, months);
    }

    private static Float estimateResistance(List<float[]> vcPairs, Integer capacityMah) {
        if (vcPairs == null || vcPairs.isEmpty() || capacityMah == null) return null;
        int n = 0;
        double sumR = 0;
        for (float[] p : vcPairs) {
            float v = p[0] / 1000f;
            float i = p[1] / 1000f;
            if (v < 3.0f || v > 4.5f || Math.abs(i) < 0.01f) continue;
            sumR += v / i;
            n++;
            if (n >= 200) break;
        }
        if (n < 3) return null;
        return (float) (sumR / n * 1000.0);
    }

    private static int countFactors(HealthFactors f) {
        int n = 0;
        if (f.capacityRetention != null) n++;
        if (f.cycleDecay != null) n++;
        if (f.resistanceGrowth != null) n++;
        if (f.thermalAging != null) n++;
        if (f.chargingDamage != null) n++;
        return n;
    }

    private static Confidence pickConfidence(HealthFactors f) {
        if (f.availableFactors >= 3) return Confidence.HIGH;
        if (f.availableFactors == 2) return Confidence.MEDIUM;
        if (f.availableFactors == 1) return Confidence.LOW;
        return Confidence.NONE;
    }

    public enum BatteryType { LIION, LIPO }
}
