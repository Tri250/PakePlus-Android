package com.batteryhealth.app.bugreport;

import java.io.File;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * bugreport 文本解析器（等价于 digiguide C++ BugreportParser）。
 *
 * <p>解析 ZIP 包或纯文本，返回 {@link BatteryRawData} + {@link ParseDetail}。各厂商 bugreport
 * 字段名差异很大（小米/华为/OPPO/vivo/三星），本类按优先级匹配；任一字段缺失不会影响其他字段
 * 解析。</p>
 */
public final class BugreportParser {

    private BugreportParser() {}

    public static BatteryRawData parseFromText(String text) {
        BatteryRawData data = new BatteryRawData();
        if (text == null || text.isEmpty()) return data;

        extractBrandModel(text, data);
        extractSN(text, data);
        extractCapacity(text, data);
        extractCycleCount(text, data);
        extractManufacturingDate(text, data);
        extractTemperature(text, data);
        extractScreenOnTime(text, data);
        extractVoltageCurrent(text, data);
        extractChargeCount(text, data);
        extractAppPowerUsage(text, data);

        data.setExtractedFieldCount(data.getAvailableDataCount());
        return data;
    }

    public static BatteryRawData parseFromZip(File zipFile) {
        ZipParser.Result zip = ZipParser.parseFromFile(zipFile);
        if (!zip.success || zip.mainBugreportContent == null) return new BatteryRawData();
        return parseFromText(zip.mainBugreportContent);
    }

    public static BatteryRawData parseFromInputStream(InputStream is) {
        ZipParser.Result zip = ZipParser.parseFromInputStream(is);
        if (!zip.success || zip.mainBugreportContent == null) return new BatteryRawData();
        return parseFromText(zip.mainBugreportContent);
    }

    public static ParseDetail getParseDetail(BatteryRawData data) {
        ParseDetail d = new ParseDetail();
        if (data.getBrand() != null) d.addExtracted("品牌");
        if (data.getModel() != null) d.addExtracted("型号");
        if (data.getSn() != null) d.addExtracted("SN");
        if (data.getDesignCapacityMah() != null) d.addExtracted("设计容量");
        if (data.getCurrentCapacityMah() != null) d.addExtracted("当前容量");
        if (data.getCycleCount() != null) d.addExtracted("循环次数");
        if (data.getManufacturingDate() != null) d.addExtracted("制造日期");
        if (data.getTemperatureCelsius() != null) d.addExtracted("温度");
        if (data.getScreenOnTimeHours() != null) d.addExtracted("亮屏时间");
        if (data.getChargeCount() != null) d.addExtracted("充电次数");
        if (!data.getVoltageCurrentPairs().isEmpty()) d.addExtracted("电压电流数据");
        if (!data.getAppPowerUsages().isEmpty()) d.addExtracted("应用耗电");

        if (data.getBrand() == null) d.addMissing("品牌");
        if (data.getModel() == null) d.addMissing("型号");
        if (data.getDesignCapacityMah() == null) d.addMissing("设计容量");
        if (data.getCurrentCapacityMah() == null) d.addMissing("当前容量");
        if (data.getCycleCount() == null) d.addMissing("循环次数");
        if (data.getManufacturingDate() == null) d.addMissing("制造日期");
        if (data.getTemperatureCelsius() == null) d.addMissing("温度");
        return d;
    }

    // ========== 子解析器 ==========

    private static void extractBrandModel(String text, BatteryRawData data) {
        String brand = firstMatch(text, RegexPatterns.getBrandPattern());
        if (brand == null) brand = firstMatch(text, RegexPatterns.getManufacturerPattern());
        if (brand != null) data.setBrand(brand.trim());

        String model = firstMatch(text, RegexPatterns.getModelPattern());
        if (model != null) data.setModel(model.trim());
    }

    private static void extractSN(String text, BatteryRawData data) {
        String sn = firstMatch(text, RegexPatterns.getSNPattern());
        if (sn == null) sn = firstMatch(text, RegexPatterns.getIMEIPattern());
        if (sn != null) data.setSn(sn.trim());
    }

    private static void extractCapacity(String text, BatteryRawData data) {
        // 设计容量
        Integer design = firstIntMatch(text, RegexPatterns.getDesignCapacityPattern());
        if (design != null) {
            // µAh → mAh 兼容
            if (design > 100000) design = design / 1000;
            data.setDesignCapacityMah(design);
        }

        // 当前容量
        for (String p : RegexPatterns.getCapacityPatterns()) {
            Integer cap = firstIntMatch(text, p);
            if (cap != null) {
                if (cap > 100000) cap = cap / 1000;
                data.setCurrentCapacityMah(cap);
                break;
            }
        }
    }

    private static void extractCycleCount(String text, BatteryRawData data) {
        for (String p : RegexPatterns.getCycleCountPatterns()) {
            Integer c = firstIntMatch(text, p);
            if (c != null && c >= 0 && c < 20000) {
                data.setCycleCount(c);
                return;
            }
        }
    }

    private static void extractManufacturingDate(String text, BatteryRawData data) {
        for (String p : RegexPatterns.getDatePatterns()) {
            String d = firstDateMatch(text, p);
            if (d != null) {
                data.setManufacturingDate(d);
                return;
            }
        }
    }

    private static void extractTemperature(String text, BatteryRawData data) {
        Float t = firstFloatMatch(text, RegexPatterns.getTemperaturePattern());
        if (t == null) t = firstFloatMatch(text, RegexPatterns.getTemperaturePatternAlt());
        if (t != null) data.setTemperatureCelsius(t);
    }

    private static void extractScreenOnTime(String text, BatteryRawData data) {
        Float f = firstFloatMatch(text, RegexPatterns.getScreenOnTimePattern());
        if (f != null) data.setScreenOnTimeHours((int) Math.round(f));
    }

    private static void extractChargeCount(String text, BatteryRawData data) {
        Integer c = firstIntMatch(text, RegexPatterns.getChargeCountPattern());
        if (c != null) data.setChargeCount(c);
    }

    private static void extractVoltageCurrent(String text, BatteryRawData data) {
        // 收集所有 (voltage, current) 配对；按出现顺序两两配对
        java.util.List<Float> voltages = allFloatMatches(text, RegexPatterns.getVoltagePattern());
        java.util.List<Float> currents = allFloatMatches(text, RegexPatterns.getCurrentPattern());
        int n = Math.min(voltages.size(), currents.size());
        for (int i = 0; i < n && i < 5000; i++) {
            data.getVoltageCurrentPairs().add(new float[]{voltages.get(i), currents.get(i)});
        }
    }

    private static void extractAppPowerUsage(String text, BatteryRawData data) {
        Matcher m = Pattern.compile(RegexPatterns.getAppPowerPattern(),
                Pattern.DOTALL).matcher(text);
        while (m.find()) {
            try {
                BatteryRawData.AppPowerUsage u = new BatteryRawData.AppPowerUsage();
                u.packageName = m.group(1).trim();
                u.displayName = u.packageName;
                u.powerMah = Float.parseFloat(m.group(2));
                u.wakeupCount = 0;
                u.isSystem = false;
                data.getAppPowerUsages().add(u);
            } catch (Exception ignored) {}
        }
        // 排序 + 取 Top 10
        data.getAppPowerUsages().sort((a, b) -> Float.compare(b.powerMah, a.powerMah));
        if (data.getAppPowerUsages().size() > 10) {
            while (data.getAppPowerUsages().size() > 10) {
                data.getAppPowerUsages().remove(data.getAppPowerUsages().size() - 1);
            }
        }
    }

    // ========== 匹配工具 ==========

    private static String firstMatch(String text, String regex) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return null;
    }

    private static Integer firstIntMatch(String text, String regex) {
        String s = firstMatch(text, regex);
        if (s == null) return null;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }

    private static Float firstFloatMatch(String text, String regex) {
        String s = firstMatch(text, regex);
        if (s == null) return null;
        try { return Float.parseFloat(s.trim()); } catch (Exception e) { return null; }
    }

    private static java.util.List<Float> allFloatMatches(String text, String regex) {
        java.util.List<Float> out = new java.util.ArrayList<>();
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            while (m.find()) {
                try { out.add(Float.parseFloat(m.group(1).trim())); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String firstDateMatch(String text, String regex) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            if (m.find()) {
                int y = Integer.parseInt(m.group(1));
                int mo = Integer.parseInt(m.group(2));
                int d = Integer.parseInt(m.group(3));
                if (RegexPatterns.isValidDate(y, mo, d)) {
                    return String.format(java.util.Locale.US, "%04d-%02d-%02d", y, mo, d);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
