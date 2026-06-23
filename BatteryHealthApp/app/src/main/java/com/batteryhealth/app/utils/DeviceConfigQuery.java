package com.batteryhealth.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DeviceConfigQuery {

    private static final String TAG = "DeviceConfigQuery";
    private static final String PREFS_NAME = "DeviceConfigPrefs";
    private static final String KEY_FIRST_INSTALL_TIME = "first_install_time";
    private static final String KEY_DEVICE_ACTIVATION_DATE = "device_activation_date";
    private static final String KEY_ANALYSIS_CACHE = "analysis_cache";

    private final Context context;
    private final SharedPreferences prefs;

    public DeviceConfigQuery(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initializeFirstInstallTime();
    }

    private void initializeFirstInstallTime() {
        if (!prefs.contains(KEY_FIRST_INSTALL_TIME)) {
            long now = System.currentTimeMillis();
            prefs.edit().putLong(KEY_FIRST_INSTALL_TIME, now).apply();
            Log.i(TAG, "First install time recorded: " + now);
        }
    }

    public DeviceInfo getDeviceInfo() {
        DeviceInfo info = new DeviceInfo();
        info.brand = Build.BRAND;
        info.model = Build.MODEL;
        info.device = Build.DEVICE;
        info.product = Build.PRODUCT;
        info.manufacturer = Build.MANUFACTURER;
        info.androidVersion = Build.VERSION.RELEASE;
        info.sdkVersion = Build.VERSION.SDK_INT;
        info.buildNumber = Build.DISPLAY;
        info.buildId = Build.ID;
        info.bootloader = Build.BOOTLOADER;
        info.hardware = Build.HARDWARE;
        info.fingerprint = Build.FINGERPRINT;
        info.codename = Build.VERSION.CODENAME;
        info.securityPatch = Build.VERSION.SECURITY_PATCH;
        return info;
    }

    public long getFirstInstallTime() {
        return prefs.getLong(KEY_FIRST_INSTALL_TIME, System.currentTimeMillis());
    }

    public long getDeviceActivationDate() {
        long cached = prefs.getLong(KEY_DEVICE_ACTIVATION_DATE, -1);
        if (cached != -1) return cached;

        long activationDate = detectActivationDate();
        if (activationDate > 0) {
            prefs.edit().putLong(KEY_DEVICE_ACTIVATION_DATE, activationDate).apply();
        }
        return activationDate;
    }

    private long detectActivationDate() {
        // Use ActivationDateHelper as the single source of truth
        ActivationDateHelper.Result result = ActivationDateHelper.detect(context);
        if (result.isValid()) {
            return result.timestamp;
        }

        // Fallback: use first install time
        return getFirstInstallTime();
    }

    private long parseDateFromBuild(String buildDate) {
        SimpleDateFormat[] formats = {
                new SimpleDateFormat("yyyyMMdd", Locale.getDefault()),
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
                new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        };

        for (SimpleDateFormat format : formats) {
            try {
                String dateStr = buildDate.replaceAll("[^0-9.]", "");
                if (dateStr.length() >= 8) {
                    dateStr = dateStr.substring(0, 8);
                    Date date = format.parse(dateStr);
                    if (date != null) return date.getTime();
                }
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    public long getDaysUsed() {
        long activationDate = getDeviceActivationDate();
        if (activationDate <= 0) return 0;
        long now = System.currentTimeMillis();
        return (now - activationDate) / (1000L * 60 * 60 * 24);
    }

    public String getFormattedActivationDate() {
        long date = getDeviceActivationDate();
        if (date <= 0) return "未知";
        ActivationDateHelper.Result result = ActivationDateHelper.detect(context);
        String sourceLabel = "";
        if (result.isValid()) {
            switch (result.source) {
                case "electronic_warranty_card": sourceLabel = "（电子保卡）"; break;
                case "system_first_boot_time": sourceLabel = "（首次开机）"; break;
                case "first_unlock_time": sourceLabel = "（首次解锁）"; break;
                case "gms_first_install": sourceLabel = "（GMS安装）"; break;
                case "system_framework_install": sourceLabel = "（系统安装）"; break;
                case "app_first_install": sourceLabel = "（应用安装）"; break;
                default: sourceLabel = ""; break;
            }
        }
        return new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(new Date(date)) + sourceLabel;
    }

    public ConfigAnalysisResult analyzeConfiguration() {
        ConfigAnalysisResult result = new ConfigAnalysisResult();
        DeviceInfo deviceInfo = getDeviceInfo();

        result.deviceInfo = deviceInfo;
        result.daysUsed = getDaysUsed();
        result.activationDate = getFormattedActivationDate();
        result.androidVersion = deviceInfo.androidVersion;
        result.sdkVersion = deviceInfo.sdkVersion;

        result.versionAssessment = assessAndroidVersion(deviceInfo.sdkVersion);
        result.hardwareAssessment = assessHardware(deviceInfo);
        result.securityAssessment = assessSecurity(deviceInfo.securityPatch);
        result.performanceAssessment = assessPerformance(deviceInfo);
        result.suggestions = generateSuggestions(result);

        return result;
    }

    private String assessAndroidVersion(int sdkVersion) {
        if (sdkVersion >= 34) return "当前系统版本为 Android 14，功能完整，安全更新及时";
        if (sdkVersion >= 33) return "当前系统版本为 Android 13，建议升级到最新版本";
        if (sdkVersion >= 31) return "当前系统版本为 Android 12，仍在安全支持范围内";
        if (sdkVersion >= 29) return "当前系统版本为 Android 10，建议考虑升级";
        return "当前系统版本较旧，建议及时更新系统以获取更好的安全保障";
    }

    private String assessHardware(DeviceInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("品牌：").append(info.brand).append("\n");
        sb.append("型号：").append(info.model).append("\n");
        sb.append("处理器：").append(info.hardware).append("\n");
        sb.append("产品代号：").append(info.product);
        return sb.toString();
    }

    private String assessSecurity(String securityPatch) {
        if (securityPatch == null || securityPatch.isEmpty()) {
            return "安全补丁信息不可用";
        }

        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date patchDate = format.parse(securityPatch);
            Date now = new Date();

            long daysDiff = (now.getTime() - patchDate.getTime()) / (1000L * 60 * 60 * 24);

            if (daysDiff <= 30) return "安全补丁更新及时（" + securityPatch + "）";
            if (daysDiff <= 90) return "安全补丁有一定滞后，建议检查系统更新";
            return "安全补丁过期超过90天（" + securityPatch + "），强烈建议更新系统";

        } catch (Exception e) {
            return "安全补丁日期：" + securityPatch;
        }
    }

    private String assessPerformance(DeviceInfo info) {
        if (info.hardware.toLowerCase().contains("sdm8") || 
            info.hardware.toLowerCase().contains("sm8")) {
            return "高端处理器，性能强劲";
        }
        if (info.hardware.toLowerCase().contains("sdm7") || 
            info.hardware.toLowerCase().contains("sm7")) {
            return "中端处理器，性能均衡";
        }
        return "基础处理器，日常使用足够";
    }

    private String generateSuggestions(ConfigAnalysisResult result) {
        StringBuilder sb = new StringBuilder();

        if (result.sdkVersion < 31) {
            sb.append("✓ 建议升级系统到 Android 12 或更高版本\n");
        }

        if (result.daysUsed > 365) {
            sb.append("✓ 设备已使用超过一年，建议定期检查电池健康状态\n");
        }

        if (result.securityAssessment.contains("过期")) {
            sb.append("✓ 请尽快更新系统安全补丁\n");
        }

        if (sb.length() == 0) {
            sb.append("✓ 设备配置良好，继续保持良好的使用习惯");
        }

        return sb.toString();
    }

    public static class DeviceInfo {
        public String brand;
        public String model;
        public String device;
        public String product;
        public String manufacturer;
        public String androidVersion;
        public int sdkVersion;
        public String buildNumber;
        public String buildId;
        public String bootloader;
        public String hardware;
        public String fingerprint;
        public String codename;
        public String securityPatch;
    }

    public static class ConfigAnalysisResult {
        public DeviceInfo deviceInfo;
        public long daysUsed;
        public String activationDate;
        public String androidVersion;
        public int sdkVersion;
        public String versionAssessment;
        public String hardwareAssessment;
        public String securityAssessment;
        public String performanceAssessment;
        public String suggestions;
    }
}