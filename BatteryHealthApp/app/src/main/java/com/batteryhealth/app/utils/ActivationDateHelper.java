package com.batteryhealth.app.utils;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 激活日期检测工具。
 *
 * 优先级：
 *  0. 各品牌系统电子保卡激活时间（最高置信度）
 *  1. Settings.Global first_boot_time
 *  2. DevicePolicyManager.getProvisioningTime
 *  3. Google Play 服务首次安装时间
 *  4. 系统框架首次安装时间
 *  5. 应用首次安装时间
 *  6. 应用数据目录创建时间
 */
public final class ActivationDateHelper {

    private ActivationDateHelper() {}

    public static final class Result {
        public final long timestamp;
        public final String source;
        public final float confidence;
        public final int usageDays;

        public Result(long timestamp, String source, float confidence, int usageDays) {
            this.timestamp = timestamp;
            this.source = source;
            this.confidence = confidence;
            this.usageDays = usageDays;
        }

        public boolean isValid() {
            return timestamp > 0;
        }
    }

    public static Result detect(Context context) {
        if (context == null) {
            return unknown();
        }
        lastDetectionLogs.clear();
        Context app = context.getApplicationContext();

        long t = readElectronicWarrantyActivation(app);
        if (t > 0) return build(t, "electronic_warranty_card", 0.98f);

        try {
            long firstBoot = Settings.Global.getLong(app.getContentResolver(), "first_boot_time", -1);
            if (firstBoot > 0) return build(firstBoot, "system_first_boot_time", 0.95f);
        } catch (Exception ignored) { }

        try {
            DevicePolicyManager dpm = (DevicePolicyManager) app.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null) {
                long provisioningTime = invokeLongMethod(dpm, "getProvisioningTime");
                if (provisioningTime > 0) return build(provisioningTime, "device_policy_manager", 0.90f);
            }
        } catch (Exception ignored) { }

        long gms = packageFirstInstallTime(app, "com.google.android.gms");
        if (gms > 0) return build(gms, "gms_first_install", 0.85f);

        long sys = packageFirstInstallTime(app, "android");
        if (sys > 0) return build(sys, "system_framework_install", 0.80f);

        long runtimeFirstBoot = systemPropertyLong("ro.runtime.firstboot");
        if (runtimeFirstBoot > 0) return build(runtimeFirstBoot, "system_first_boot_time", 0.75f);

        long appInstall = packageFirstInstallTime(app, app.getPackageName());
        if (appInstall > 0) return build(appInstall, "app_first_install", 0.60f);

        try {
            File dataDir = app.getDataDir();
            if (dataDir != null) {
                long lastModified = dataDir.lastModified();
                if (lastModified > 0) return build(lastModified, "app_data_directory", 0.40f);
            }
        } catch (Exception ignored) { }

        return unknown();
    }

    private static Result unknown() {
        return new Result(-1, "unknown", 0f, -1);
    }

    private static Result build(long timestamp, String source, float confidence) {
        timestamp = normalizeTimestamp(timestamp);
        int usageDays = -1;
        if (timestamp > 0) {
            long now = System.currentTimeMillis();
            if (timestamp <= now) {
                usageDays = (int) ((now - timestamp) / 86_400_000L);
                if (usageDays < 0) usageDays = 0;
            }
        }
        return new Result(timestamp, source, confidence, usageDays);
    }

    /**
     * 归一化时间戳：部分厂商 Setting/Property 存储的是秒级或微秒级时间戳，需转换为毫秒。
     * 当前毫秒时间戳约 1.7e12，秒级约 1.7e9，微秒级约 1.7e15。
     */
    private static long normalizeTimestamp(long timestamp) {
        if (timestamp <= 0) return -1;

        // 微秒级 -> 毫秒
        if (timestamp > 10_000_000_000_000L) {
            timestamp /= 1000L;
        }

        // 秒级 -> 毫秒
        if (timestamp < 1_000_000_000L) return -1; // 2001 年之前或无效
        if (timestamp < 1_000_000_000_000L) {
            timestamp *= 1000L;
        }

        // 未来超过 1 年视为无效
        long now = System.currentTimeMillis();
        if (timestamp > now + 365L * 24 * 60 * 60 * 1000) return -1;

        return timestamp;
    }

    private static final String TAG = "ActivationDateHelper";

    /**
     * 记录本次检测命中了哪些键，便于调试与自检。
     */
    public static final class DetectionLog {
        public final String key;
        public final long value;
        public final boolean isSystemProperty;

        public DetectionLog(String key, long value, boolean isSystemProperty) {
            this.key = key;
            this.value = value;
            this.isSystemProperty = isSystemProperty;
        }

        @Override
        public String toString() {
            String isoTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new java.util.Date(value));
            return (isSystemProperty ? "[prop]" : "[setting]") + " " + key + " = " + isoTime;
        }
    }

    public static java.util.List<DetectionLog> lastDetectionLogs = new java.util.ArrayList<>();

    private static long firstPositive(String key, java.util.concurrent.Callable<Long> supplier) {
        try {
            Long v = supplier.call();
            if (v != null && v > 0) {
                lastDetectionLogs.add(new DetectionLog(key, v, key.startsWith("ro.")));
                return v;
            }
        } catch (Exception e) {
            Log.d(TAG, "key read failed: " + key);
        }
        return -1;
    }

    private static long readElectronicWarrantyActivation(Context context) {
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase(Locale.ROOT) : "";
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(Locale.ROOT) : "";

        // 小米/红米：MIUI / 澎湃 OS 激活时间
        if (brand.contains("xiaomi") || brand.contains("redmi") || manufacturer.contains("xiaomi")) {
            long t = firstPositive("miui_activated_time", () -> settingsLong(context, "miui_activated_time"));
            if (t > 0) return t;
            t = firstPositive("miui_activation_time", () -> settingsLong(context, "miui_activation_time"));
            if (t > 0) return t;
            t = firstPositive("miui_activated", () -> settingsLong(context, "miui_activated"));
            if (t > 0) return t;
            t = firstPositive("miui_active_time", () -> settingsLong(context, "miui_active_time"));
            if (t > 0) return t;
            t = firstPositive("miui_vip_activated", () -> settingsLong(context, "miui_vip_activated"));
            if (t > 0) return t;
            t = firstPositive("activate_time", () -> settingsLong(context, "activate_time"));
            if (t > 0) return t;
            t = firstPositive("activated_time", () -> settingsLong(context, "activated_time"));
            if (t > 0) return t;
            // ro.miui.saledate 是出厂日期，置信度低，留给通用兜底
            t = firstPositive("ro.miui.activated_time", () -> systemPropertyLong("ro.miui.activated_time"));
            if (t > 0) return t;
            t = firstPositive("ro.miui.activated", () -> systemPropertyLong("ro.miui.activated"));
            if (t > 0) return t;
            t = firstPositive("ro.vendor.miui.activated_time", () -> systemPropertyLong("ro.vendor.miui.activated_time"));
            if (t > 0) return t;
        }

        // OPPO/realme/一加：ColorOS/OxygenOS / realme UI 激活时间
        if (brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus")
                || manufacturer.contains("oppo") || manufacturer.contains("oneplus")) {
            long t = firstPositive("oppo_activate_time", () -> settingsLong(context, "oppo_activate_time"));
            if (t > 0) return t;
            t = firstPositive("oppo_activated_time", () -> settingsLong(context, "oppo_activated_time"));
            if (t > 0) return t;
            t = firstPositive("oppo_activated", () -> settingsLong(context, "oppo_activated"));
            if (t > 0) return t;
            t = firstPositive("coloros_activated_time", () -> settingsLong(context, "coloros_activated_time"));
            if (t > 0) return t;
            t = firstPositive("coloros_activated", () -> settingsLong(context, "coloros_activated"));
            if (t > 0) return t;
            t = firstPositive("coloros_activate_time", () -> settingsLong(context, "coloros_activate_time"));
            if (t > 0) return t;
            t = firstPositive("oneplus_activated_time", () -> settingsLong(context, "oneplus_activated_time"));
            if (t > 0) return t;
            t = firstPositive("oplus_activated_time", () -> settingsLong(context, "oplus_activated_time"));
            if (t > 0) return t;
            t = firstPositive("oplus_activated", () -> settingsLong(context, "oplus_activated"));
            if (t > 0) return t;
            t = firstPositive("heytap_activated_time", () -> settingsLong(context, "heytap_activated_time"));
            if (t > 0) return t;
            t = firstPositive("heytap_activated", () -> settingsLong(context, "heytap_activated"));
            if (t > 0) return t;
            t = firstPositive("realme_activated_time", () -> settingsLong(context, "realme_activated_time"));
            if (t > 0) return t;
            t = firstPositive("realme_activated", () -> settingsLong(context, "realme_activated"));
            if (t > 0) return t;
            t = firstPositive("activate_time", () -> settingsLong(context, "activate_time"));
            if (t > 0) return t;
            t = firstPositive("activated_time", () -> settingsLong(context, "activated_time"));
            if (t > 0) return t;
            t = firstPositive("ro.oppo.activated_time", () -> systemPropertyLong("ro.oppo.activated_time"));
            if (t > 0) return t;
            t = firstPositive("ro.oppo.activated", () -> systemPropertyLong("ro.oppo.activated"));
            if (t > 0) return t;
            t = firstPositive("ro.vendor.oppo.activated_time", () -> systemPropertyLong("ro.vendor.oppo.activated_time"));
            if (t > 0) return t;
            t = firstPositive("ro.oplus.activated_time", () -> systemPropertyLong("ro.oplus.activated_time"));
            if (t > 0) return t;
            t = firstPositive("ro.oplus.activated", () -> systemPropertyLong("ro.oplus.activated"));
            if (t > 0) return t;
        }

        // vivo/iQOO：OriginOS/FuntouchOS 激活时间
        if (brand.contains("vivo") || brand.contains("iqoo") || manufacturer.contains("vivo")) {
            long t = firstPositive("vivo_active_time", () -> settingsLong(context, "vivo_active_time"));
            if (t > 0) return t;
            t = firstPositive("vivo_activated_time", () -> settingsLong(context, "vivo_activated_time"));
            if (t > 0) return t;
            t = firstPositive("vivo_activated", () -> settingsLong(context, "vivo_activated"));
            if (t > 0) return t;
            t = firstPositive("vivo_warranty_time", () -> settingsLong(context, "vivo_warranty_time"));
            if (t > 0) return t;
            t = firstPositive("vivo_activate_time", () -> settingsLong(context, "vivo_activate_time"));
            if (t > 0) return t;
            t = firstPositive("bbk_active_time", () -> settingsLong(context, "bbk_active_time"));
            if (t > 0) return t;
            t = firstPositive("bbk_activated_time", () -> settingsLong(context, "bbk_activated_time"));
            if (t > 0) return t;
            t = firstPositive("originos_activated_time", () -> settingsLong(context, "originos_activated_time"));
            if (t > 0) return t;
            t = firstPositive("originos_activated", () -> settingsLong(context, "originos_activated"));
            if (t > 0) return t;
            t = firstPositive("activate_time", () -> settingsLong(context, "activate_time"));
            if (t > 0) return t;
            t = firstPositive("activated_time", () -> settingsLong(context, "activated_time"));
            if (t > 0) return t;
            t = firstPositive("ro.vivo.activated_time", () -> systemPropertyLong("ro.vivo.activated_time"));
            if (t > 0) return t;
            t = firstPositive("ro.vivo.activated", () -> systemPropertyLong("ro.vivo.activated"));
            if (t > 0) return t;
            t = firstPositive("ro.vendor.vivo.activated_time", () -> systemPropertyLong("ro.vendor.vivo.activated_time"));
            if (t > 0) return t;
        }

        // 华为/荣耀：EMUI/MagicUI / HarmonyOS 激活时间
        if (brand.contains("huawei") || brand.contains("honor") || manufacturer.contains("huawei")) {
            long t = firstPositive("huawei_first_boot_time", () -> settingsLong(context, "huawei_first_boot_time"));
            if (t > 0) return t;
            t = firstPositive("huawei_warranty_time", () -> settingsLong(context, "huawei_warranty_time"));
            if (t > 0) return t;
            t = firstPositive("huawei_activated_time", () -> settingsLong(context, "huawei_activated_time"));
            if (t > 0) return t;
            t = firstPositive("huawei_activation_time", () -> settingsLong(context, "huawei_activation_time"));
            if (t > 0) return t;
            t = firstPositive("hw_activation_time", () -> settingsLong(context, "hw_activation_time"));
            if (t > 0) return t;
            t = firstPositive("hw_activated_time", () -> settingsLong(context, "hw_activated_time"));
            if (t > 0) return t;
            t = firstPositive("hw_warranty_time", () -> settingsLong(context, "hw_warranty_time"));
            if (t > 0) return t;
            t = firstPositive("huawei_activation_date", () -> parseDateString(settingsString(context, "huawei_activation_date")));
            if (t > 0) return t;
            t = firstPositive("honor_first_boot_time", () -> settingsLong(context, "honor_first_boot_time"));
            if (t > 0) return t;
            t = firstPositive("honor_activated_time", () -> settingsLong(context, "honor_activated_time"));
            if (t > 0) return t;
            t = firstPositive("honor_activation_date", () -> parseDateString(settingsString(context, "honor_activation_date")));
            if (t > 0) return t;
            t = firstPositive("hms_activate_time", () -> settingsLong(context, "hms_activate_time"));
            if (t > 0) return t;
            t = firstPositive("activate_time", () -> settingsLong(context, "activate_time"));
            if (t > 0) return t;
            t = firstPositive("activated_time", () -> settingsLong(context, "activated_time"));
            if (t > 0) return t;
            t = firstPositive("ro.hw.oem.activated", () -> systemPropertyLong("ro.hw.oem.activated"));
            if (t > 0) return t;
            t = firstPositive("ro.vendor.hw.activated", () -> systemPropertyLong("ro.vendor.hw.activated"));
            if (t > 0) return t;
            t = firstPositive("ro.honor.activated", () -> systemPropertyLong("ro.honor.activated"));
            if (t > 0) return t;
            t = firstPositive("ro.honor.activated_time", () -> systemPropertyLong("ro.honor.activated_time"));
            if (t > 0) return t;
        }

        // 魅族：Flyme 激活时间
        if (brand.contains("meizu") || manufacturer.contains("meizu")) {
            long t = firstPositive("meizu_activated_time", () -> settingsLong(context, "meizu_activated_time"));
            if (t > 0) return t;
            t = firstPositive("meizu_activated", () -> settingsLong(context, "meizu_activated"));
            if (t > 0) return t;
            t = firstPositive("meizu_activation_time", () -> settingsLong(context, "meizu_activation_time"));
            if (t > 0) return t;
            t = firstPositive("flyme_activated_time", () -> settingsLong(context, "flyme_activated_time"));
            if (t > 0) return t;
            t = firstPositive("flyme_activated", () -> settingsLong(context, "flyme_activated"));
            if (t > 0) return t;
        }

        // 三星：One UI 激活时间
        if (brand.contains("samsung") || manufacturer.contains("samsung")) {
            long t = firstPositive("samsung_activated_time", () -> settingsLong(context, "samsung_activated_time"));
            if (t > 0) return t;
            t = firstPositive("samsung_activated", () -> settingsLong(context, "samsung_activated"));
            if (t > 0) return t;
            t = firstPositive("sec_activated_time", () -> settingsLong(context, "sec_activated_time"));
            if (t > 0) return t;
            t = firstPositive("sec_active_time", () -> settingsLong(context, "sec_active_time"));
            if (t > 0) return t;
            t = firstPositive("sec_activated", () -> settingsLong(context, "sec_activated"));
            if (t > 0) return t;
            t = firstPositive("sec_warranty_time", () -> settingsLong(context, "sec_warranty_time"));
            if (t > 0) return t;
            t = firstPositive("knox_activation_date", () -> parseDateString(settingsString(context, "knox_activation_date")));
            if (t > 0) return t;
            t = firstPositive("activate_time", () -> settingsLong(context, "activate_time"));
            if (t > 0) return t;
            t = firstPositive("activated_time", () -> settingsLong(context, "activated_time"));
            if (t > 0) return t;
        }

        // 中兴/努比亚/红魔
        if (brand.contains("nubia") || brand.contains("redmagic") || brand.contains("zte")
                || manufacturer.contains("nubia") || manufacturer.contains("zte")) {
            long t = firstPositive("nubia_activated_time", () -> settingsLong(context, "nubia_activated_time"));
            if (t > 0) return t;
            t = firstPositive("nubia_activated", () -> settingsLong(context, "nubia_activated"));
            if (t > 0) return t;
            t = firstPositive("redmagic_activated_time", () -> settingsLong(context, "redmagic_activated_time"));
            if (t > 0) return t;
            t = firstPositive("zte_activated_time", () -> settingsLong(context, "zte_activated_time"));
            if (t > 0) return t;
            t = firstPositive("zte_activated", () -> settingsLong(context, "zte_activated"));
            if (t > 0) return t;
        }

        // 通用：尝试常见的通用电子保卡/激活时间键名
        // 注意：first_boot_time / ro.runtime.firstboot 属于“首次开机”而非电子保卡，
        // 交给 detect() 的后续 fallback 处理，避免置信度虚高。
        long t = firstPositive("electronic_warranty_activated_time", () -> settingsLong(context, "electronic_warranty_activated_time"));
        if (t > 0) return t;
        t = firstPositive("electronic_warranty_activated", () -> settingsLong(context, "electronic_warranty_activated"));
        if (t > 0) return t;
        t = firstPositive("device_activated_time", () -> settingsLong(context, "device_activated_time"));
        if (t > 0) return t;
        t = firstPositive("device_activate_time", () -> settingsLong(context, "device_activate_time"));
        if (t > 0) return t;
        t = firstPositive("device_activated_date", () -> settingsLong(context, "device_activated_date"));
        if (t > 0) return t;
        t = firstPositive("first_activate_time", () -> settingsLong(context, "first_activate_time"));
        if (t > 0) return t;
        t = firstPositive("first_use_time", () -> settingsLong(context, "first_use_time"));
        if (t > 0) return t;
        t = firstPositive("device_first_use_time", () -> settingsLong(context, "device_first_use_time"));
        if (t > 0) return t;
        t = firstPositive("activation_date", () -> settingsLong(context, "activation_date"));
        if (t > 0) return t;
        t = firstPositive("activation_date_str", () -> parseDateString(settingsString(context, "activation_date")));
        if (t > 0) return t;
        t = firstPositive("warranty_start_date", () -> settingsLong(context, "warranty_start_date"));
        if (t > 0) return t;
        t = firstPositive("warranty_start_date_str", () -> parseDateString(settingsString(context, "warranty_start_date")));
        if (t > 0) return t;
        t = firstPositive("warranty_time", () -> settingsLong(context, "warranty_time"));
        if (t > 0) return t;
        t = firstPositive("device_warranty_time", () -> settingsLong(context, "device_warranty_time"));
        if (t > 0) return t;
        t = firstPositive("activate_time", () -> settingsLong(context, "activate_time"));
        if (t > 0) return t;
        t = firstPositive("activated_time", () -> settingsLong(context, "activated_time"));
        if (t > 0) return t;
        return -1;
    }

    private static long settingsLong(Context context, String key) {
        try {
            return Settings.Secure.getLong(context.getContentResolver(), key, -1);
        } catch (Exception e) {
            try {
                return Settings.Global.getLong(context.getContentResolver(), key, -1);
            } catch (Exception e2) {
                try {
                    return Settings.System.getLong(context.getContentResolver(), key, -1);
                } catch (Exception ignored) {
                    return -1;
                }
            }
        }
    }

    private static String settingsString(Context context, String key) {
        try {
            String value = Settings.Secure.getString(context.getContentResolver(), key);
            if (value != null && !value.isEmpty()) return value;
            value = Settings.Global.getString(context.getContentResolver(), key);
            if (value != null && !value.isEmpty()) return value;
            return Settings.System.getString(context.getContentResolver(), key);
        } catch (Exception e) {
            return null;
        }
    }

    private static long systemPropertyLong(String propertyName) {
        try {
            String value = SystemPropertiesCompat.get(propertyName);
            if (value != null && !value.isEmpty()) {
                return Long.parseLong(value.trim());
            }
        } catch (Exception ignored) { }
        return -1;
    }

    private static long parseDateString(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return -1;
        String[] patterns = {"yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd", "yyyy-MM-dd HH:mm:ss"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
                Date date = sdf.parse(dateStr.trim());
                if (date != null) return date.getTime();
            } catch (Exception ignored) { }
        }
        return -1;
    }

    private static long packageFirstInstallTime(Context context, String packageName) {
        try {
            android.content.pm.PackageInfo info = context.getPackageManager()
                    .getPackageInfo(packageName, 0);
            return info.firstInstallTime;
        } catch (Exception e) {
            return -1;
        }
    }

    private static long invokeLongMethod(Object target, String methodName) {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(methodName);
            Object result = m.invoke(target);
            if (result instanceof Long) return (Long) result;
        } catch (Exception ignored) { }
        return -1;
    }
}
