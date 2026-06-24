package com.batteryhealth.app.domain.usecase;

import com.batteryhealth.app.domain.repository.DeviceRepository;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.BatteryOriginDetector;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DetermineBatterySourceUseCase {

    private final DeviceRepository deviceRepository;
    private final BatteryDataManager batteryDataManager;

    public DetermineBatterySourceUseCase(DeviceRepository deviceRepository, BatteryDataManager batteryDataManager) {
        this.deviceRepository = deviceRepository;
        this.batteryDataManager = batteryDataManager;
    }

    public Result execute(String vendorInfo, String manufacturer, String serial,
                          int fullCapacity, int designCapacity) {
        Result result = new Result();

        // 委托给统一判定引擎
        if (batteryDataManager != null && batteryDataManager.getOriginDetector() != null) {
            BatteryOriginDetector detector = batteryDataManager.getOriginDetector();
            detector.setBatteryDataManager(batteryDataManager);
            BatteryOriginDetector.OriginResult originResult = detector.detect();

            if (originResult != null) {
                if (originResult.isOriginal) {
                    result.source = "original";
                    result.confidence = originResult.confidence / 100f;
                    result.reason = originResult.conclusion;
                } else if (originResult.confidence >= 30) {
                    result.source = "third_party";
                    result.confidence = originResult.confidence / 100f;
                    result.reason = originResult.conclusion;
                } else {
                    result.source = "unknown";
                    result.confidence = 0f;
                    result.reason = originResult.conclusion;
                }
                return result;
            }
        }

        // 回退：简化逻辑（当 BatteryOriginDetector 不可用时）
        result.confidence = 0f;
        Map<String, Float> signals = new HashMap<>();

        if (vendorInfo != null && !vendorInfo.isEmpty()) {
            if (looksLikeOemSerial(vendorInfo)) {
                signals.put("vendor_serial", 0.4f);
            } else {
                signals.put("vendor_serial", -0.3f);
            }
        }

        if (manufacturer != null && !manufacturer.isEmpty()) {
            if (manufacturer.toLowerCase(Locale.ROOT).matches(".*(coslight|sunwoda|byd|lg|chem|sanyo|tdk).*")) {
                signals.put("manufacturer", 0.3f);
            } else if (manufacturer.equalsIgnoreCase("unknown") || manufacturer.equalsIgnoreCase("0")) {
                signals.put("manufacturer", -0.1f);
            }
        }

        if (serial != null && !serial.isEmpty() && !serial.equalsIgnoreCase("unknown")) {
            if (isValidOemSerialFormat(serial)) {
                signals.put("serial_format", 0.25f);
            } else {
                signals.put("serial_format", -0.35f);
            }
        }

        if (designCapacity > 0 && fullCapacity > 0) {
            float ratio = fullCapacity / (float) designCapacity;
            if (ratio >= 0.85f && ratio <= 1.05f) {
                signals.put("capacity_ratio", 0.3f);
            } else if (ratio >= 0.55f && ratio <= 1.25f) {
                signals.put("capacity_ratio", 0f);
            } else {
                signals.put("capacity_ratio", -0.5f);
            }
        }

        if (deviceRepository.getDesignCapacity() > 0) {
            signals.put("device_database_match", 0.2f);
        } else {
            signals.put("device_database_match", -0.1f);
        }

        float total = 0f;
        for (float v : signals.values()) total += v;

        if (total >= 0.5f) {
            result.source = "original";
            result.confidence = Math.min(0.95f, 0.6f + total * 0.1f);
            result.reason = "综合多项原厂标识通过";
        } else if (total <= -0.3f) {
            result.source = "third_party";
            result.confidence = Math.min(0.9f, 0.55f - total * 0.1f);
            result.reason = "存在明显非原厂特征";
        } else {
            result.source = "unknown";
            result.confidence = 0f;
            result.reason = "原厂标识不足";
        }

        return result;
    }

    private boolean isValidOemSerialFormat(String serial) {
        if (serial == null || serial.length() < 10 || serial.length() > 24) return false;
        int letters = 0, digits = 0;
        for (int i = 0; i < serial.length(); i++) {
            char c = serial.charAt(i);
            if (Character.isLetter(c)) letters++;
            else if (Character.isDigit(c)) digits++;
            else return false;
        }
        return letters >= 3 && digits >= 3;
    }

    private boolean looksLikeOemSerial(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() < 8 || t.length() > 64) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '\n' || c == '\r') continue;
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ') continue;
            return false;
        }
        return true;
    }

    public static class Result {
        public String source;
        public String reason;
        public float confidence;
    }
}
