package com.batteryhealth.app.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 电池序列号格式校验和厂商识别工具
 */
public class BatterySerialValidator {

    public static class SerialValidationResult {
        public boolean isValid;
        public String manufacturer;
        public String manufacturerCode;
        public String productionDate;
        public int productionYear;
        public int productionMonth;
        public int productionDay;
        public String batchCode;
        public String formatType;
        public int confidence;
    }

    private static final Map<String, String> KNOWN_MANUFACTURER_CODES = new HashMap<>();

    static {
        KNOWN_MANUFACTURER_CODES.put("SD", "Desay（德赛）");
        KNOWN_MANUFACTURER_CODES.put("DY", "Desay（德赛）");
        KNOWN_MANUFACTURER_CODES.put("SWD", "Sunwoda（欣旺达）");
        KNOWN_MANUFACTURER_CODES.put("SW", "Sunwoda（欣旺达）");
        KNOWN_MANUFACTURER_CODES.put("SCUD", "Scud（飞毛腿）");
        KNOWN_MANUFACTURER_CODES.put("SC", "Scud（飞毛腿）");
        KNOWN_MANUFACTURER_CODES.put("BYD", "BYD（比亚迪）");
        KNOWN_MANUFACTURER_CODES.put("BD", "BYD（比亚迪）");
        KNOWN_MANUFACTURER_CODES.put("ATL", "ATL（新能源科技）");
        KNOWN_MANUFACTURER_CODES.put("LG", "LG Chem（乐金化学）");
        KNOWN_MANUFACTURER_CODES.put("SAMSUNG", "Samsung SDI（三星SDI）");
        KNOWN_MANUFACTURER_CODES.put("SS", "Samsung SDI（三星SDI）");
        KNOWN_MANUFACTURER_CODES.put("MURATA", "Murata（村田）");
        KNOWN_MANUFACTURER_CODES.put("LISHEN", "Lishen（力神）");
        KNOWN_MANUFACTURER_CODES.put("LS", "Lishen（力神）");
        KNOWN_MANUFACTURER_CODES.put("BAK", "BAK（比克）");
        KNOWN_MANUFACTURER_CODES.put("EVE", "EVE（亿纬锂能）");
        KNOWN_MANUFACTURER_CODES.put("COSLIGHT", "Coslight（光宇）");
        KNOWN_MANUFACTURER_CODES.put("FARASIS", "Farasis（孚能科技）");
    }

    public static SerialValidationResult validate(String serialNumber) {
        SerialValidationResult result = new SerialValidationResult();
        result.isValid = false;
        result.confidence = 0;

        if (serialNumber == null || serialNumber.isEmpty()) {
            return result;
        }

        String serial = serialNumber.trim().toUpperCase();

        if (serial.length() < 6) {
            return result;
        }

        if (serial.equals("UNKNOWN") || serial.equals("0") || serial.equals("0000000000")) {
            return result;
        }

        result.manufacturer = identifyManufacturer(serial);
        if (result.manufacturer != null) {
            result.confidence += 30;
        }

        String[] dateInfo = extractProductionDate(serial);
        if (dateInfo != null) {
            result.productionDate = dateInfo[0];
            try {
                result.productionYear = Integer.parseInt(dateInfo[1]);
                result.productionMonth = Integer.parseInt(dateInfo[2]);
                result.productionDay = Integer.parseInt(dateInfo[3]);
            } catch (NumberFormatException ignored) {}
            result.confidence += 40;
        }

        result.batchCode = extractBatchCode(serial);

        if (serial.length() >= 12) {
            result.confidence += 20;
            result.formatType = "标准格式";
        } else if (serial.length() >= 8) {
            result.confidence += 10;
            result.formatType = "简化格式";
        } else {
            result.formatType = "短格式";
        }

        if (Pattern.matches("^[A-Z0-9]+$", serial)) {
            result.confidence += 10;
        }

        result.isValid = result.confidence >= 30;

        if (result.confidence > 100) {
            result.confidence = 100;
        }

        return result;
    }

    private static String identifyManufacturer(String serial) {
        for (Map.Entry<String, String> entry : KNOWN_MANUFACTURER_CODES.entrySet()) {
            String code = entry.getKey();
            if (serial.startsWith(code) || serial.contains(code)) {
                return entry.getValue();
            }
        }

        Pattern samsungPattern = Pattern.compile("^[A-Z]{2}\\d{6}[A-Z]{3}");
        Matcher samsungMatcher = samsungPattern.matcher(serial);
        if (samsungMatcher.find() && serial.length() >= 11) {
            return "Samsung SDI（三星SDI）";
        }

        Pattern lgPattern = Pattern.compile("^LG[A-Z0-9]+");
        if (lgPattern.matcher(serial).find()) {
            return "LG Chem（乐金化学）";
        }

        return null;
    }

    private static String[] extractProductionDate(String serial) {
        Pattern pattern1 = Pattern.compile("(20\\d{2})(\\d{2})(\\d{2})");
        Matcher matcher1 = pattern1.matcher(serial);
        if (matcher1.find()) {
            return new String[]{
                    matcher1.group(1) + "-" + matcher1.group(2) + "-" + matcher1.group(3),
                    matcher1.group(1),
                    matcher1.group(2),
                    matcher1.group(3)
            };
        }

        Pattern pattern2 = Pattern.compile("([1-9])(\\d{2})(\\d{2})");
        Matcher matcher2 = pattern2.matcher(serial);
        if (matcher2.find()) {
            int yearCode = Integer.parseInt(matcher2.group(1));
            int year = 2020 + yearCode;
            if (year > 2030) {
                year = 2010 + yearCode;
            }
            String month = matcher2.group(2);
            String day = matcher2.group(3);
            try {
                int monthInt = Integer.parseInt(month);
                int dayInt = Integer.parseInt(day);
                if (monthInt >= 1 && monthInt <= 12 && dayInt >= 1 && dayInt <= 31) {
                    return new String[]{
                            year + "-" + month + "-" + day,
                            String.valueOf(year),
                            month,
                            day
                    };
                }
            } catch (NumberFormatException ignored) {}
        }

        Pattern pattern3 = Pattern.compile("^([A-Z])(\\d{2})(\\d)");
        Matcher matcher3 = pattern3.matcher(serial);
        if (matcher3.find()) {
            char yearChar = matcher3.group(1).charAt(0);
            int year = 2010 + (yearChar - 'A');
            int month = Integer.parseInt(matcher3.group(2));
            if (month >= 1 && month <= 12) {
                return new String[]{
                        year + "-" + String.format("%02d", month),
                        String.valueOf(year),
                        String.format("%02d", month),
                        "01"
                };
            }
        }

        return null;
    }

    private static String extractBatchCode(String serial) {
        if (serial.length() >= 10) {
            return serial.substring(serial.length() - 4);
        }
        return null;
    }
}
