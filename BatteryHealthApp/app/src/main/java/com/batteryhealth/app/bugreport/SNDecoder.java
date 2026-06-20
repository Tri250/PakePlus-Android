package com.batteryhealth.app.bugreport;

import java.util.HashMap;
import java.util.Map;

/**
 * SN 解码器（等价于 digiguide C++ SNDecoder）。
 *
 * <p>覆盖 Apple / Samsung / Huawei / Honor / Xiaomi / OPPO / vivo 等主流品牌。
 * 对于无法精确识别的 SN，调用方应提示用户"需要官方 API 查询"。</p>
 */
public final class SNDecoder {

    public enum Brand {
        APPLE, APPLE_MAC, SAMSUNG, HUAWEI, HONOR, XIAOMI, REDMI, OPPO,
        ONEPLUS, REALME, VIVO, IQOO, LENOVO, HP, ASUS, DELL, UNKNOWN
    }

    public enum Status { SUCCESS, PARTIAL, FAILED }

    public static class Result {
        public String rawSn;
        public Brand brand = Brand.UNKNOWN;
        public Integer factoryYear;
        public Integer factoryMonth;
        public Integer factoryWeek;
        public String halfYear;
        public String productionDateEstimate;
        public Status status = Status.FAILED;
        public String errorMessage;

        public String getProductionDateEstimate() {
            StringBuilder sb = new StringBuilder();
            if (factoryYear != null) {
                sb.append(factoryYear);
                if (factoryMonth != null) sb.append("-").append(factoryMonth);
                else if (factoryWeek != null) {
                    int m = Math.min(12, (factoryWeek - 1) / 4 + 1);
                    sb.append("-").append(m).append(" (第").append(factoryWeek).append("周)");
                } else if (halfYear != null) sb.append(" ").append(halfYear);
            }
            return sb.toString();
        }
    }

    // Apple 年份映射（基于公开 SN 编码规则）
    private static final Map<Character, String> APPLE_YEAR = new HashMap<>();
    private static final Map<Character, Integer> APPLE_WEEK = new HashMap<>();
    static {
        // Apple 年份编码：C=2020上半年, D=2020下半年, F=2021上半年, G=2021下半年, ...
        char[] codes = {'C','D','F','G','H','J','K','L','M','N','P','Q','R','S','T','V','W','X','Y','Z'};
        int[] years = {2020,2020,2021,2021,2022,2022,2023,2023,2024,2024,2025,2025,2026,2026,2027,2027,2028,2028,2029,2029};
        int[] half = {1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,2};
        for (int i = 0; i < codes.length; i++) {
            APPLE_YEAR.put(codes[i], years[i] + "_" + half[i]);
        }
        // 周次：1-9 直接 1-9, A=10, B=11, C=12, D=13... Y=34, 0-9 表示下半年
        for (int i = 1; i <= 9; i++) APPLE_WEEK.put((char)('0' + i), i);
        APPLE_WEEK.put('A', 10); APPLE_WEEK.put('B', 11); APPLE_WEEK.put('C', 12);
        APPLE_WEEK.put('D', 13); APPLE_WEEK.put('E', 14); APPLE_WEEK.put('F', 15);
        APPLE_WEEK.put('G', 16); APPLE_WEEK.put('H', 17); APPLE_WEEK.put('J', 18);
        APPLE_WEEK.put('K', 19); APPLE_WEEK.put('L', 20); APPLE_WEEK.put('M', 21);
        APPLE_WEEK.put('N', 22); APPLE_WEEK.put('P', 23); APPLE_WEEK.put('Q', 24);
        APPLE_WEEK.put('R', 25); APPLE_WEEK.put('S', 26); APPLE_WEEK.put('T', 27);
        APPLE_WEEK.put('V', 28); APPLE_WEEK.put('W', 29); APPLE_WEEK.put('X', 30);
        APPLE_WEEK.put('Y', 31);
    }

    // Samsung：末 7 位为年份码
    private static final Map<Character, Integer> SAMSUNG_YEAR = new HashMap<>();
    private static final Map<Character, Integer> SAMSUNG_MONTH = new HashMap<>();
    static {
        // 简化的 Samsung 编码：A=2010, B=2011, ...
        char[] yearCodes = {'A','B','C','D','E','F','G','H','J','K','L','M','N','P','Q','R','S','T'};
        for (int i = 0; i < yearCodes.length; i++) {
            SAMSUNG_YEAR.put(yearCodes[i], 2010 + i);
        }
        for (int i = 1; i <= 12; i++) {
            SAMSUNG_MONTH.put((char)('0' + (i % 10)), i);
            SAMSUNG_MONTH.put((char)('A' + i - 1), i);
        }
    }

    private SNDecoder() {}

    public static Result decode(String sn) {
        return decode(sn, identifyBrand(sn));
    }

    public static Result decode(String sn, Brand brand) {
        Result r = new Result();
        r.rawSn = sn;
        r.brand = brand;
        if (sn == null || sn.isEmpty()) {
            r.status = Status.FAILED;
            r.errorMessage = "SN 为空";
            return r;
        }
        switch (brand) {
            case APPLE:
            case APPLE_MAC:
                return decodeApple(sn, r);
            case SAMSUNG:
                return decodeSamsung(sn, r);
            case HUAWEI:
                return decodeHuawei(sn, r);
            case HONOR:
                Result hr = decodeHuawei(sn, r);
                hr.brand = Brand.HONOR;
                return hr;
            case XIAOMI:
            case REDMI:
                return decodeXiaomi(sn, r);
            case OPPO:
            case ONEPLUS:
            case REALME:
                return decodeOppoFamily(sn, r);
            case VIVO:
            case IQOO:
                return decodeVivo(sn, r);
            default:
                r.status = Status.FAILED;
                r.errorMessage = "无法识别的品牌";
                return r;
        }
    }

    public static Brand identifyBrand(String sn) {
        if (sn == null) return Brand.UNKNOWN;
        String s = sn.trim().toUpperCase();
        // Apple：12 位，第 4 位是年份码
        if (s.length() == 12) {
            String yc = APPLE_YEAR.get(s.charAt(3));
            if (yc != null) return Brand.APPLE;
        }
        // Samsung：≥10 位，倒数第 7 位年份码
        if (s.length() >= 10) {
            int yearPos = s.length() - 7;
            if (yearPos >= 0 && SAMSUNG_YEAR.containsKey(s.charAt(yearPos))) {
                return Brand.SAMSUNG;
            }
        }
        // Huawei/Honor：≥10 位，第 6-7 位年份
        if (s.length() >= 10) {
            try {
                int y = Integer.parseInt(s.substring(5, 7));
                if (y >= 20 && y <= 30) {
                    if (s.startsWith("HUAWEI") || s.startsWith("HWI")) return Brand.HUAWEI;
                    if (s.startsWith("HONOR") || s.startsWith("HNR")) return Brand.HONOR;
                    return Brand.HUAWEI;
                }
            } catch (Exception ignored) {}
        }
        // Xiaomi：15 位数字 IMEI
        if (s.length() == 15 && s.matches("\\d+")) return Brand.XIAOMI;
        // OPPO / OnePlus / Realme 前缀
        if (s.startsWith("OPPO") || s.startsWith("OP")) return Brand.OPPO;
        if (s.startsWith("ONEPLUS") || s.startsWith("OPLP")) return Brand.ONEPLUS;
        if (s.startsWith("REALME") || s.startsWith("RM")) return Brand.REALME;
        // vivo
        if (s.startsWith("VIVO") || s.length() >= 10 && s.startsWith("V")) return Brand.VIVO;
        if (s.startsWith("IQOO")) return Brand.IQOO;
        return Brand.UNKNOWN;
    }

    private static Result decodeApple(String sn, Result r) {
        if (sn.length() < 5) { r.status = Status.FAILED; r.errorMessage = "SN 长度不足"; return r; }
        String yc = APPLE_YEAR.get(sn.charAt(3).toString().toUpperCase().charAt(0));
        if (yc == null) { r.status = Status.FAILED; r.errorMessage = "无法识别年份"; return r; }
        String[] parts = yc.split("_");
        r.factoryYear = Integer.parseInt(parts[0]);
        r.halfYear = "1".equals(parts[1]) ? "上半年" : "下半年";

        Integer week = APPLE_WEEK.get(sn.charAt(4).toString().toUpperCase().charAt(0));
        if (week == null) {
            r.status = Status.PARTIAL;
            r.errorMessage = "无法识别周次";
            return r;
        }
        r.factoryWeek = week;
        r.factoryMonth = Math.min(12, (week - 1) / 4 + 1);
        r.status = Status.SUCCESS;
        return r;
    }

    private static Result decodeSamsung(String sn, Result r) {
        if (sn.length() < 7) { r.status = Status.FAILED; r.errorMessage = "SN 长度不足"; return r; }
        int yearPos = sn.length() - 7;
        Integer y = SAMSUNG_YEAR.get(sn.charAt(yearPos).toString().toUpperCase().charAt(0));
        if (y == null) { r.status = Status.FAILED; r.errorMessage = "无法识别年份"; return r; }
        r.factoryYear = y;
        Integer m = SAMSUNG_MONTH.get(sn.charAt(yearPos + 1).toString().toUpperCase().charAt(0));
        if (m != null) r.factoryMonth = m;
        r.status = Status.SUCCESS;
        return r;
    }

    private static Result decodeHuawei(String sn, Result r) {
        if (sn.length() < 9) { r.status = Status.FAILED; r.errorMessage = "SN 长度不足"; return r; }
        try {
            int y = Integer.parseInt(sn.substring(5, 7));
            r.factoryYear = 2000 + y;
            int w = Integer.parseInt(sn.substring(7, 9));
            r.factoryWeek = w;
            r.factoryMonth = Math.min(12, (w - 1) / 4 + 1);
            r.status = Status.SUCCESS;
        } catch (Exception e) {
            r.status = Status.FAILED;
            r.errorMessage = "解析失败: " + e.getMessage();
        }
        return r;
    }

    private static Result decodeXiaomi(String sn, Result r) {
        if (sn.length() == 15 && sn.matches("\\d+")) {
            r.status = Status.PARTIAL;
            r.errorMessage = "IMEI 格式需要官方 API 查询";
            return r;
        }
        if (sn.length() >= 4) {
            try {
                int y = Integer.parseInt(sn.substring(0, 2));
                if (y >= 20 && y <= 30) {
                    r.factoryYear = 2000 + y;
                    r.status = Status.PARTIAL;
                    r.errorMessage = "小米 SN 格式多样，结果仅供参考";
                    return r;
                }
            } catch (Exception ignored) {}
        }
        r.status = Status.FAILED;
        r.errorMessage = "无法识别的小米 SN 格式";
        return r;
    }

    private static Result decodeOppoFamily(String sn, Result r) {
        r.status = Status.PARTIAL;
        r.errorMessage = "OPPO 系 SN 需要官方 API 查询精确日期";
        return r;
    }

    private static Result decodeVivo(String sn, Result r) {
        if (sn.length() >= 8) {
            try {
                int y = Integer.parseInt(sn.substring(4, 6));
                if (y >= 20 && y <= 30) {
                    r.factoryYear = 2000 + y;
                    int w = Integer.parseInt(sn.substring(6, 8));
                    if (w >= 1 && w <= 12) r.factoryMonth = w;
                    else if (w >= 1 && w <= 52) {
                        r.factoryWeek = w;
                        r.factoryMonth = Math.min(12, (w - 1) / 4 + 1);
                    }
                    r.status = Status.PARTIAL;
                    r.errorMessage = "vivo SN 格式多样，结果仅供参考";
                    return r;
                }
            } catch (Exception ignored) {}
        }
        r.status = Status.FAILED;
        r.errorMessage = "无法识别的 vivo SN 格式";
        return r;
    }

    public static boolean validateFormat(String sn, Brand brand) {
        if (sn == null) return false;
        int len = sn.length();
        switch (brand) {
            case APPLE:
            case APPLE_MAC:
                return len == 12;
            case SAMSUNG:
            case HUAWEI:
            case HONOR:
            case XIAOMI:
            case REDMI:
            case OPPO:
            case ONEPLUS:
            case REALME:
            case VIVO:
            case IQOO:
                return len >= 10;
            default:
                return false;
        }
    }
}
