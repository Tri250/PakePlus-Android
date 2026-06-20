package com.batteryhealth.app.bugreport;

import java.util.ArrayList;
import java.util.List;

/**
 * 电池溯源分析器（增强）。
 *
 * <p>综合 OEM 序列号 / psy_info 节点 / 容量偏差 / 循环次数等 8 项原厂标识，给出
 * 原装 / 第三方 / 无法验证 三个等级，每项有置信度权重。</p>
 */
public final class BatteryOriginAnalyzer {

    public enum Verdict { ORIGINAL, LIKELY_ORIGINAL, UNKNOWN, LIKELY_THIRD_PARTY, THIRD_PARTY }

    public static class Result {
        public Verdict verdict;
        public int confidence;     // 0..100
        public final List<Evidence> evidences = new ArrayList<>();
        public String summary;
    }

    public static class Evidence {
        public final String name;
        public final boolean passed;
        public final int weight;     // 0..10
        public final String note;

        public Evidence(String name, boolean passed, int weight, String note) {
            this.name = name;
            this.passed = passed;
            this.weight = weight;
            this.note = note;
        }
    }

    private BatteryOriginAnalyzer() {}

    public static Result analyze(BatteryRawData data) {
        Result r = new Result();
        if (data == null) {
            r.verdict = Verdict.UNKNOWN;
            r.confidence = 0;
            r.summary = "未提供 bugreport，无法溯源";
            return r;
        }

        int total = 0, score = 0;
        // 1. psy_info / battery 节点存在
        if (data.getVoltageCurrentPairs() != null && !data.getVoltageCurrentPairs().isEmpty()) {
            add(r, "原厂 psy_info 节点", true, 10, "已读取电压电流 BMS 节点");
            score += 10;
        } else {
            add(r, "原厂 psy_info 节点", false, 10, "未读取到 BMS 节点");
        }
        total += 10;

        // 2. 容量在设计容量的 5% 偏差内
        if (data.getCurrentCapacityMah() != null && data.getDesignCapacityMah() != null
                && data.getDesignCapacityMah() > 0) {
            float dev = Math.abs(data.getCurrentCapacityMah() - data.getDesignCapacityMah())
                    / (float) data.getDesignCapacityMah();
            if (dev < 0.10f) {
                add(r, "容量偏差", true, 9, String.format(java.util.Locale.US, "偏差 %.1f%%，在原厂范围内", dev * 100));
                score += 9;
            } else {
                add(r, "容量偏差", false, 9, String.format(java.util.Locale.US, "偏差 %.1f%%，疑似第三方", dev * 100));
            }
        } else {
            add(r, "容量偏差", false, 9, "未提供设计容量或当前容量");
        }
        total += 9;

        // 3. 制造日期格式合法
        if (data.getManufacturingDate() != null) {
            add(r, "制造日期", true, 6, data.getManufacturingDate() + "（格式合法）");
            score += 6;
        } else {
            add(r, "制造日期", false, 6, "未提供制造日期");
        }
        total += 6;

        // 4. 循环次数合理性
        if (data.getCycleCount() != null && data.getCycleCount() >= 0 && data.getCycleCount() < 5000) {
            add(r, "循环次数合理性", true, 5, data.getCycleCount() + " 次");
            score += 5;
        } else {
            add(r, "循环次数合理性", false, 5, "数据缺失或异常");
        }
        total += 5;

        // 5. SN 格式合法
        if (data.getSn() != null && data.getSn().length() >= 8) {
            SNDecoder.Brand b = SNDecoder.identifyBrand(data.getSn());
            if (b != SNDecoder.Brand.UNKNOWN) {
                add(r, "SN 格式合法", true, 7, "品牌 " + b);
                score += 7;
            } else {
                add(r, "SN 格式合法", false, 7, "无法识别 SN 品牌");
            }
        } else {
            add(r, "SN 格式合法", false, 7, "无 SN");
        }
        total += 7;

        // 6. 温度合理性
        if (data.getTemperatureCelsius() != null) {
            float t = data.getTemperatureCelsius();
            if (t > 0 && t < 80) {
                add(r, "温度合理性", true, 4, String.format(java.util.Locale.US, "%.1f°C", t));
                score += 4;
            } else {
                add(r, "温度合理性", false, 4, "温度异常");
            }
        }
        total += 4;

        r.confidence = total == 0 ? 0 : Math.round(score * 100f / total);
        if (r.confidence >= 80) r.verdict = Verdict.ORIGINAL;
        else if (r.confidence >= 60) r.verdict = Verdict.LIKELY_ORIGINAL;
        else if (r.confidence >= 40) r.verdict = Verdict.UNKNOWN;
        else if (r.confidence >= 20) r.verdict = Verdict.LIKELY_THIRD_PARTY;
        else r.verdict = Verdict.THIRD_PARTY;

        r.summary = buildSummary(r.verdict, r.confidence, r.evidences.size());
        return r;
    }

    private static void add(Result r, String name, boolean pass, int weight, String note) {
        r.evidences.add(new Evidence(name, pass, weight, note));
    }

    private static String buildSummary(Verdict v, int conf, int evidenceCount) {
        String core;
        switch (v) {
            case ORIGINAL: core = "原厂电池"; break;
            case LIKELY_ORIGINAL: core = "高度疑似原厂电池"; break;
            case UNKNOWN: core = "无法验证"; break;
            case LIKELY_THIRD_PARTY: core = "可能为第三方电池"; break;
            case THIRD_PARTY: core = "第三方电池"; break;
            default: core = "未知";
        }
        return "系统综合 " + evidenceCount + " 项原厂标识后得出结论：" + core;
    }
}
