package com.batteryhealth.app.utils;

import android.os.Build;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * 充电功率滑动窗口校准工具类。
 *
 * 核心功能：
 *  1. 滑动窗口均值：维护最近 30 秒的功率采样窗口（每秒 1 次采样），
 *     使用加权移动平均（近期权重更高），过滤瞬时波动。
 *  2. dV/dt + dI/dt 分析：通过电压变化率和电流变化率判断充电阶段。
 *  3. 充电阶段智能判断：恒流快充（CC）、恒压（CV）、涓流（Trickle）。
 *  4. 充电协议识别增强：基于功率区间和品牌特征判断协议。
 *  5. 预估充满时间：基于当前充电阶段和剩余电量，使用历史数据校准。
 *  6. 充电效率计算：实际输入功率 vs 电池充电功率，效率通常 80-95%。
 *
 * 兼容 Android 7.0+（API 24+）。
 */
public class ChargingPowerAnalyzer {

    private static final String TAG = "ChargingPowerAnalyzer";

    // ==================== 滑动窗口常量 ====================

    /** 采样窗口大小：30 秒，每秒 1 次采样 */
    private static final int WINDOW_SIZE = 30;

    /** 加权移动平均的最小权重（最旧采样） */
    private static final float MIN_WEIGHT = 1.0f;

    /** 加权移动平均的最大权重（最新采样） */
    private static final float MAX_WEIGHT = 3.0f;

    // ==================== dV/dt 和 dI/dt 阈值 ====================

    /** dV/dt 正向阈值（mV/s），超过此值视为电压上升 */
    private static final float DVDT_POSITIVE_THRESHOLD = 2.0f;

    /** dV/dt 稳定阈值（mV/s），低于此值视为电压稳定 */
    private static final float DVDT_STABLE_THRESHOLD = 1.0f;

    /** dI/dt 负向阈值（mA/s），低于此值视为电流下降 */
    private static final float DIDT_NEGATIVE_THRESHOLD = -5.0f;

    /** dI/dt 稳定阈值（mA/s），绝对值低于此值视为电流稳定 */
    private static final float DIDT_STABLE_THRESHOLD = 5.0f;

    /** 涓流充电电流阈值（mA） */
    private static final float TRICKLE_CURRENT_THRESHOLD = 300.0f;

    /** 涓流充电电量阈值（%） */
    private static final float TRICKLE_LEVEL_THRESHOLD = 95.0f;

    // ==================== 充电效率常量 ====================

    /** 默认充电效率（无历史数据时） */
    private static final float DEFAULT_EFFICIENCY = 0.87f;

    /** 典型效率下限 */
    private static final float MIN_EFFICIENCY = 0.80f;

    /** 典型效率上限 */
    private static final float MAX_EFFICIENCY = 0.95f;

    // ==================== 预估充满时间常量 ====================

    /** CC 阶段速率系数：恒流阶段接近满功率充电 */
    private static final float CC_CHARGE_RATE = 1.0f;

    /** CV 阶段速率系数：恒压阶段功率下降至约 30% */
    private static final float CV_CHARGE_RATE = 0.3f;

    /** 涓流阶段速率系数：涓流阶段功率极低 */
    private static final float TRICKLE_CHARGE_RATE = 0.05f;

    /** 历史效率记录最大条数 */
    private static final int MAX_EFFICIENCY_HISTORY = 50;

    // ==================== 数据结构 ====================

    /** 功率采样点 */
    private static class PowerSample {
        final float powerW;
        final float voltageMv;
        final float currentMa;
        final long timestampMs;

        PowerSample(float powerW, float voltageMv, float currentMa, long timestampMs) {
            this.powerW = powerW;
            this.voltageMv = voltageMv;
            this.currentMa = currentMa;
            this.timestampMs = timestampMs;
        }
    }

    /**
     * 充电阶段枚举。
     */
    public enum ChargingPhase {
        /** 恒流快充阶段：电流稳定，电压逐步上升 */
        CC("恒流快充", "CC"),
        /** 恒压阶段：电压稳定，电流逐步下降 */
        CV("恒压充电", "CV"),
        /** 涓流阶段：电流极低且持续下降，电量 > 95% */
        TRICKLE("涓流充电", "Trickle"),
        /** 未充电或无法判断 */
        UNKNOWN("未知", "Unknown");

        public final String label;
        public final String code;

        ChargingPhase(String label, String code) {
            this.label = label;
            this.code = code;
        }
    }

    /**
     * 充电协议枚举。
     */
    public enum ChargingProtocol {
        VOOC_SUPERVOOC("VOOC/SuperVOOC", 30, Float.MAX_VALUE, 9.0f, 11.0f),
        FLASH_CHARGE("FlashCharge", 20, Float.MAX_VALUE, 0, Float.MAX_VALUE),
        QC_3_0("QC 3.0", 18, 27, 0, Float.MAX_VALUE),
        QC_4_0("QC 4.0/4+", 27, 100, 0, Float.MAX_VALUE),
        PD_PPS("PD 3.0/PPS", 18, 100, 0, Float.MAX_VALUE),
        UFCS("UFCS", 20, 40, 0, Float.MAX_VALUE),
        SCP("SCP", 22.5f, 40, 0, Float.MAX_VALUE),
        NORMAL("普通充电", 0, 10, 0, Float.MAX_VALUE),
        UNKNOWN("未知协议", 0, Float.MAX_VALUE, 0, Float.MAX_VALUE);

        public final String label;
        public final float minPowerW;
        public final float maxPowerW;
        public final float minVoltageV;
        public final float maxVoltageV;

        ChargingProtocol(String label, float minPowerW, float maxPowerW, float minVoltageV, float maxVoltageV) {
            this.label = label;
            this.minPowerW = minPowerW;
            this.maxPowerW = maxPowerW;
            this.minVoltageV = minVoltageV;
            this.maxVoltageV = maxVoltageV;
        }
    }

    /**
     * 分析结果，包含充电阶段、协议、效率、预估时间等完整信息。
     */
    public static class AnalysisResult {
        /** 加权移动平均功率（W） */
        public float smoothedPowerW;
        /** 当前充电阶段 */
        public ChargingPhase phase;
        /** 识别的充电协议 */
        public ChargingProtocol protocol;
        /** 电压变化率 dV/dt（mV/s） */
        public float dvdt;
        /** 电流变化率 dI/dt（mA/s） */
        public float didt;
        /** 充电效率（0-1） */
        public float efficiency;
        /** 预估充满剩余时间（分钟），-1 表示无法预估 */
        public float estimatedMinutesToFull;
        /** 电池端充电功率（W）= 电池电压 * 电池电流 */
        public float batteryPowerW;
        /** 实际输入功率（W）= 平滑后功率 */
        public float inputPowerW;
        /** 采样窗口中的有效采样数 */
        public int sampleCount;
        /** 当前电池电压（mV） */
        public float voltageMv;
        /** 当前电池电流（mA） */
        public float currentMa;
        /** 当前电量（%） */
        public int batteryLevel;

        @Override
        public String toString() {
            return String.format(Locale.getDefault(),
                    "AnalysisResult{phase=%s, protocol=%s, power=%.1fW, efficiency=%.0f%%, "
                            + "eta=%.0fmin, dV/dt=%.1fmV/s, dI/dt=%.1fmA/s, samples=%d}",
                    phase.code, protocol.label, smoothedPowerW, efficiency * 100,
                    estimatedMinutesToFull, dvdt, didt, sampleCount);
        }
    }

    // ==================== 实例字段 ====================

    /** 滑动窗口采样队列 */
    private final LinkedList<PowerSample> sampleWindow = new LinkedList<>();

    /** 充电效率历史记录（用于校准） */
    private final List<Float> efficiencyHistory = new ArrayList<>();

    /** 上一次分析结果缓存 */
    private AnalysisResult lastResult;

    /** 设备品牌（小写），用于协议识别增强 */
    private final String brand;

    /** 设备制造商（小写），用于协议识别增强 */
    private final String manufacturer;

    // ==================== 构造方法 ====================

    public ChargingPowerAnalyzer() {
        this.brand = Build.BRAND != null ? Build.BRAND.toLowerCase(Locale.ROOT) : "";
        this.manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(Locale.ROOT) : "";
    }

    // ==================== 公开 API ====================

    /**
     * 添加一次功率采样。
     * 应每秒调用一次，由外部定时器驱动。
     *
     * @param powerW      当前充电功率（W）
     * @param voltageMv   当前电池电压（mV）
     * @param currentMa   当前充电电流（mA）
     */
    public void addSample(float powerW, float voltageMv, float currentMa) {
        long now = System.currentTimeMillis();
        synchronized (sampleWindow) {
            sampleWindow.add(new PowerSample(powerW, voltageMv, currentMa, now));
            // 移除超过窗口大小的旧采样
            while (sampleWindow.size() > WINDOW_SIZE) {
                sampleWindow.removeFirst();
            }
        }
    }

    /**
     * 执行完整分析，返回当前充电状态的综合评估结果。
     *
     * @param batteryLevel      当前电量百分比（0-100）
     * @param designCapacityMah 电池设计容量（mAh），用于预估充满时间
     * @return 分析结果
     */
    public AnalysisResult analyze(int batteryLevel, int designCapacityMah) {
        AnalysisResult result = new AnalysisResult();
        result.batteryLevel = batteryLevel;

        synchronized (sampleWindow) {
            result.sampleCount = sampleWindow.size();

            if (sampleWindow.isEmpty()) {
                result.smoothedPowerW = 0;
                result.phase = ChargingPhase.UNKNOWN;
                result.protocol = ChargingProtocol.UNKNOWN;
                result.dvdt = 0;
                result.didt = 0;
                result.efficiency = DEFAULT_EFFICIENCY;
                result.estimatedMinutesToFull = -1;
                result.batteryPowerW = 0;
                result.inputPowerW = 0;
                result.voltageMv = 0;
                result.currentMa = 0;
                lastResult = result;
                return result;
            }

            // 1. 加权移动平均
            result.smoothedPowerW = calculateWeightedMovingAverage();
            result.inputPowerW = result.smoothedPowerW;

            // 2. 取最新采样的电压和电流
            PowerSample latest = sampleWindow.getLast();
            result.voltageMv = latest.voltageMv;
            result.currentMa = latest.currentMa;

            // 3. 计算电池端功率
            result.batteryPowerW = (latest.voltageMv * Math.abs(latest.currentMa)) / 1_000_000.0f;

            // 4. dV/dt 和 dI/dt 分析
            float[] rates = calculateDerivatives();
            result.dvdt = rates[0];
            result.didt = rates[1];

            // 5. 充电阶段判断
            result.phase = determineChargingPhase(result.dvdt, result.didt,
                    Math.abs(latest.currentMa), batteryLevel);

            // 6. 充电协议识别
            result.protocol = identifyChargingProtocol(result.smoothedPowerW,
                    latest.voltageMv / 1000.0f);

            // 7. 充电效率计算
            result.efficiency = calculateEfficiency(result.inputPowerW, result.batteryPowerW);

            // 8. 预估充满时间
            result.estimatedMinutesToFull = estimateTimeToFull(
                    result.phase, batteryLevel, result.smoothedPowerW,
                    result.efficiency, designCapacityMah);
        }

        lastResult = result;
        return result;
    }

    /**
     * 获取最近一次分析结果（不重新计算）。
     */
    public AnalysisResult getLastResult() {
        return lastResult;
    }

    /**
     * 记录一次充电会话的效率，用于后续校准。
     * 应在充电会话结束时调用。
     *
     * @param efficiency 本次的充电效率（0-1）
     */
    public void recordEfficiency(float efficiency) {
        float clamped = Math.max(MIN_EFFICIENCY, Math.min(MAX_EFFICIENCY, efficiency));
        synchronized (efficiencyHistory) {
            efficiencyHistory.add(clamped);
            if (efficiencyHistory.size() > MAX_EFFICIENCY_HISTORY) {
                efficiencyHistory.remove(0);
            }
        }
    }

    /**
     * 获取历史平均充电效率。
     */
    public float getHistoricalAverageEfficiency() {
        synchronized (efficiencyHistory) {
            if (efficiencyHistory.isEmpty()) return DEFAULT_EFFICIENCY;
            float sum = 0;
            for (float e : efficiencyHistory) sum += e;
            return sum / efficiencyHistory.size();
        }
    }

    /**
     * 清除所有采样数据和历史记录。
     */
    public void reset() {
        synchronized (sampleWindow) {
            sampleWindow.clear();
        }
        synchronized (efficiencyHistory) {
            efficiencyHistory.clear();
        }
        lastResult = null;
    }

    /**
     * 获取当前滑动窗口中的采样数量。
     */
    public int getSampleCount() {
        synchronized (sampleWindow) {
            return sampleWindow.size();
        }
    }

    // ==================== 滑动窗口加权移动平均 ====================

    /**
     * 计算加权移动平均功率。
     * 近期采样权重更高（线性递增），过滤瞬时波动。
     * 权重从 MIN_WEIGHT（最旧）到 MAX_WEIGHT（最新）线性分布。
     */
    private float calculateWeightedMovingAverage() {
        if (sampleWindow.isEmpty()) return 0f;
        if (sampleWindow.size() == 1) return sampleWindow.getFirst().powerW;

        float weightSum = 0;
        float weightedValueSum = 0;
        int n = sampleWindow.size();

        for (int i = 0; i < n; i++) {
            // 线性权重：最旧 MIN_WEIGHT，最新 MAX_WEIGHT
            float weight = MIN_WEIGHT + (MAX_WEIGHT - MIN_WEIGHT) * i / (n - 1);
            weightedValueSum += sampleWindow.get(i).powerW * weight;
            weightSum += weight;
        }

        return weightSum > 0 ? weightedValueSum / weightSum : 0f;
    }

    // ==================== dV/dt + dI/dt 分析 ====================

    /**
     * 计算电压变化率 dV/dt（mV/s）和电流变化率 dI/dt（mA/s）。
     * 使用窗口内的线性回归斜率，比简单差分更抗噪声。
     *
     * @return [dV/dt, dI/dt]
     */
    private float[] calculateDerivatives() {
        if (sampleWindow.size() < 2) return new float[]{0f, 0f};

        int n = sampleWindow.size();
        // 使用最近 10 个采样点做线性回归（约 10 秒），兼顾灵敏度和稳定性
        int regressionSize = Math.min(10, n);
        int startIdx = n - regressionSize;

        // 线性回归：y = a + b*x，求 b（斜率）
        float sumX = 0, sumYv = 0, sumYi = 0;
        float sumXYv = 0, sumXYi = 0, sumX2 = 0;

        for (int i = 0; i < regressionSize; i++) {
            PowerSample s = sampleWindow.get(startIdx + i);
            // x 以秒为单位
            float x = (s.timestampMs - sampleWindow.get(startIdx).timestampMs) / 1000.0f;
            float yv = s.voltageMv;
            float yi = s.currentMa;

            sumX += x;
            sumYv += yv;
            sumYi += yi;
            sumXYv += x * yv;
            sumXYi += x * yi;
            sumX2 += x * x;
        }

        float denominator = regressionSize * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 1e-6f) return new float[]{0f, 0f};

        float dvdt = (regressionSize * sumXYv - sumX * sumYv) / denominator;
        float didt = (regressionSize * sumXYi - sumX * sumYi) / denominator;

        return new float[]{dvdt, didt};
    }

    // ==================== 充电阶段智能判断 ====================

    /**
     * 基于电压变化率、电流变化率、电流绝对值和电量判断充电阶段。
     *
     * 判断逻辑：
     * - 恒流快充（CC）：dV/dt > 0（电压上升），|dI/dt| < 阈值（电流稳定）
     * - 恒压（CV）：|dV/dt| < 阈值（电压稳定），dI/dt < 0（电流下降）
     * - 涓流（Trickle）：电流极低且持续下降，电量 > 95%
     */
    private ChargingPhase determineChargingPhase(float dvdt, float didt,
                                                  float absCurrentMa, int batteryLevel) {
        // 涓流阶段优先判断（条件最严格）
        if (absCurrentMa < TRICKLE_CURRENT_THRESHOLD
                && didt < DIDT_NEGATIVE_THRESHOLD
                && batteryLevel >= TRICKLE_LEVEL_THRESHOLD) {
            return ChargingPhase.TRICKLE;
        }

        // 恒流快充阶段：电压上升 + 电流稳定
        boolean voltageRising = dvdt > DVDT_POSITIVE_THRESHOLD;
        boolean currentStable = Math.abs(didt) < DIDT_STABLE_THRESHOLD;

        if (voltageRising && currentStable) {
            return ChargingPhase.CC;
        }

        // 恒压阶段：电压稳定 + 电流下降
        boolean voltageStable = Math.abs(dvdt) < DVDT_STABLE_THRESHOLD;
        boolean currentDropping = didt < DIDT_NEGATIVE_THRESHOLD;

        if (voltageStable && currentDropping) {
            return ChargingPhase.CV;
        }

        // 辅助判断：高电流 + 高功率 → CC 阶段
        if (absCurrentMa > 1000 && voltageRising) {
            return ChargingPhase.CC;
        }

        // 辅助判断：低电流 + 高电量 → CV 或 Trickle
        if (absCurrentMa < TRICKLE_CURRENT_THRESHOLD && batteryLevel >= 90) {
            return batteryLevel >= TRICKLE_LEVEL_THRESHOLD ? ChargingPhase.TRICKLE : ChargingPhase.CV;
        }

        // 中等电流 + 电压稳定 → CV 阶段
        if (voltageStable && absCurrentMa > 0 && absCurrentMa < 1500) {
            return ChargingPhase.CV;
        }

        return ChargingPhase.UNKNOWN;
    }

    // ==================== 充电协议识别增强 ====================

    /**
     * 基于功率区间和品牌特征判断充电协议。
     *
     * 识别优先级：
     * 1. VOOC/SuperVOOC：OPPO 系，功率 > 30W，电压 ≈ 10V
     * 2. FlashCharge：vivo 系，功率 > 20W
     * 3. QC 3.0/4.0：功率 18-27W / 27-100W
     * 4. PD 3.0/PPS：功率 18-100W，电压可变
     * 5. UFCS：功率 20-40W，国内融合快充
     * 6. SCP：华为超级快充，功率 22.5-40W
     * 7. 普通充电：< 10W
     */
    private ChargingProtocol identifyChargingProtocol(float powerW, float voltageV) {
        if (powerW <= 0) return ChargingProtocol.UNKNOWN;

        boolean isOppo = brand.contains("oppo") || brand.contains("realme")
                || brand.contains("oneplus") || manufacturer.contains("oppo")
                || manufacturer.contains("oneplus");
        boolean isVivo = brand.contains("vivo") || brand.contains("iqoo")
                || manufacturer.contains("vivo");
        boolean isHuawei = brand.contains("huawei") || brand.contains("honor")
                || manufacturer.contains("huawei");
        boolean isXiaomi = brand.contains("xiaomi") || brand.contains("redmi")
                || manufacturer.contains("xiaomi");

        // VOOC/SuperVOOC：OPPO 系，功率 > 30W，电压 ≈ 10V
        if (isOppo && powerW > 30 && voltageV >= 9.0f && voltageV <= 11.5f) {
            return ChargingProtocol.VOOC_SUPERVOOC;
        }
        // OPPO 系高功率但电压不在 10V 附近（可能是 SuperVOOC 的高压模式）
        if (isOppo && powerW > 30) {
            return ChargingProtocol.VOOC_SUPERVOOC;
        }

        // FlashCharge：vivo 系，功率 > 20W
        if (isVivo && powerW > 20) {
            return ChargingProtocol.FLASH_CHARGE;
        }

        // SCP：华为超级快充，功率 22.5-40W
        if (isHuawei && powerW >= 22.5f && powerW <= 40) {
            return ChargingProtocol.SCP;
        }
        // 华为更高功率也归为 SCP 系列
        if (isHuawei && powerW > 40) {
            return ChargingProtocol.SCP;
        }

        // 小米系：高功率优先走 PD/PPS，中等功率走 QC
        if (isXiaomi && powerW >= 27) {
            return ChargingProtocol.PD_PPS;
        }

        // UFCS：功率 20-40W，国内融合快充（非特定品牌专属）
        // 当功率在 20-40W 且不属于上述品牌专属协议时，可能是 UFCS
        if (powerW >= 20 && powerW <= 40 && !isOppo && !isVivo && !isHuawei) {
            // 检查 sysfs 中是否有 UFCS 标识（简化判断：功率区间匹配即可）
            return ChargingProtocol.UFCS;
        }

        // QC 4.0/4+：功率 27-100W
        if (powerW >= 27 && powerW < 100) {
            return ChargingProtocol.QC_4_0;
        }

        // PD 3.0/PPS：功率 18-100W，电压可变
        if (powerW >= 18 && powerW <= 100) {
            return ChargingProtocol.PD_PPS;
        }

        // QC 3.0：功率 18-27W
        if (powerW >= 18 && powerW < 27) {
            return ChargingProtocol.QC_3_0;
        }

        // 普通充电：< 10W
        if (powerW < 10) {
            return ChargingProtocol.NORMAL;
        }

        // 10-18W 之间，无法确定具体协议
        return ChargingProtocol.UNKNOWN;
    }

    // ==================== 充电效率计算 ====================

    /**
     * 计算充电效率 = 电池端充电功率 / 实际输入功率。
     * 效率通常在 80-95% 之间。
     *
     * @param inputPowerW   实际输入功率（W），来自充电器端
     * @param batteryPowerW 电池端充电功率（W）= 电池电压 * 电池电流
     * @return 充电效率（0-1），无有效数据时返回历史平均或默认值
     */
    private float calculateEfficiency(float inputPowerW, float batteryPowerW) {
        if (inputPowerW <= 0) {
            return getHistoricalAverageEfficiency();
        }

        // 允许 5% 的测量误差容差，避免轻微波动导致 fallback
        if (batteryPowerW <= 0 || batteryPowerW > inputPowerW * 1.05f) {
            // 电池端功率异常（可能采样不准），使用历史平均
            return getHistoricalAverageEfficiency();
        }

        float efficiency = batteryPowerW / inputPowerW;

        // 钳位到合理范围
        if (efficiency < MIN_EFFICIENCY || efficiency > MAX_EFFICIENCY) {
            return getHistoricalAverageEfficiency();
        }

        return efficiency;
    }

    // ==================== 预估充满时间 ====================

    /**
     * 基于当前充电阶段和剩余电量预估充满时间。
     *
     * 计算逻辑：
     * - 根据充电阶段确定充电速率系数
     * - 剩余容量 = 设计容量 × (1 - 当前电量/100)
     * - 预估时间 = 剩余容量 × 电压 / (功率 × 效率 × 速率系数) / 60
     *
     * @param phase             当前充电阶段
     * @param batteryLevel      当前电量（%）
     * @param smoothedPowerW    平滑后功率（W）
     * @param efficiency        充电效率
     * @param designCapacityMah 设计容量（mAh）
     * @return 预估充满时间（分钟），-1 表示无法预估
     */
    private float estimateTimeToFull(ChargingPhase phase, int batteryLevel,
                                      float smoothedPowerW, float efficiency,
                                      int designCapacityMah) {
        if (smoothedPowerW <= 0 || designCapacityMah <= 0 || batteryLevel >= 100) {
            return batteryLevel >= 100 ? 0 : -1;
        }

        if (phase == ChargingPhase.UNKNOWN) {
            // 未知阶段，使用功率直接估算
            return estimateByPower(smoothedPowerW, efficiency, batteryLevel, designCapacityMah);
        }

        // 剩余需要充入的容量（mAh）
        float remainingCapacityMah = designCapacityMah * (100 - batteryLevel) / 100.0f;

        // 根据充电阶段调整速率系数
        // CC 阶段充电快，CV 阶段充电慢，涓流阶段最慢
        float rateFactor;
        switch (phase) {
            case CC:
                rateFactor = CC_CHARGE_RATE;
                break;
            case CV:
                rateFactor = CV_CHARGE_RATE;
                break;
            case TRICKLE:
                rateFactor = TRICKLE_CHARGE_RATE;
                break;
            default:
                rateFactor = CC_CHARGE_RATE;
                break;
        }

        // 使用历史效率校准
        float calibratedEfficiency = (efficiency > 0) ? efficiency : getHistoricalAverageEfficiency();

        // 预估时间（分钟）
        // 能量 = 容量(mAh) × 标称电压(V) / 1000 → Wh
        // 假设标称电压 3.8V
        float nominalVoltageV = 3.8f;
        float remainingEnergyWh = remainingCapacityMah * nominalVoltageV / 1000.0f;

        // 有效充电功率 = 输入功率 × 效率 × 速率系数
        // 速率系数反映不同阶段实际充电速率与标称功率的比值
        float effectivePowerW = smoothedPowerW * calibratedEfficiency * rateFactor;

        if (effectivePowerW <= 0) return -1;

        float minutes = (remainingEnergyWh / effectivePowerW) * 60.0f;

        // 合理性校验：最少 1 分钟，最多 600 分钟（10 小时）
        return Math.max(1, Math.min(600, minutes));
    }

    /**
     * 使用功率直接估算充满时间（未知充电阶段的兜底方案）。
     */
    private float estimateByPower(float powerW, float efficiency,
                                   int batteryLevel, int designCapacityMah) {
        if (powerW <= 0 || designCapacityMah <= 0) return -1;

        float remainingCapacityMah = designCapacityMah * (100 - batteryLevel) / 100.0f;
        float nominalVoltageV = 3.8f;
        float remainingEnergyWh = remainingCapacityMah * nominalVoltageV / 1000.0f;
        float calibratedEfficiency = (efficiency > 0) ? efficiency : getHistoricalAverageEfficiency();
        float effectivePowerW = powerW * calibratedEfficiency;

        if (effectivePowerW <= 0) return -1;

        float minutes = (remainingEnergyWh / effectivePowerW) * 60.0f;
        return Math.max(1, Math.min(600, minutes));
    }
}
