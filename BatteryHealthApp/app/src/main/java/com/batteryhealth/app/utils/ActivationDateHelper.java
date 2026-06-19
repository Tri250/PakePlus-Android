package com.batteryhealth.app.utils;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

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
     * 归一化时间戳：部分厂商 Setting/Property 存储的是秒级时间戳，需转换为毫秒。
     * 当前毫秒时间戳约 1.7e12，秒级约 1.7e9；位于两者之间时按秒处理。
     */
    private static long normalizeTimestamp(long timestamp) {
        if (timestamp <= 0) return -1;
        if (timestamp < 1_000_000_000L) return -1; // 2001 年之前或无效
        if (timestamp < 1_000_000_000_000L) {
            // 秒级时间戳
            return timestamp * 1000L;
        }
        return timestamp;
    }

    private static long readElectronicWarrantyActivation(Context context) {
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase(Locale.ROOT) : "";
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(Locale.ROOT) : "";

        // 小米/红米：MIUI / 澎湃 OS 激活时间
        if (brand.contains("xiaomi") || brand.contains("redmi") || manufacturer.contains("xiaomi")) {
            long t = settingsLong(context, "miui_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "miui_activation_time");
            if (t > 0) return t;
            t = settingsLong(context, "miui_activated");
            if (t > 0) return t;
            t = settingsLong(context, "miui_active_time");
            if (t > 0) return t;
            t = settingsLong(context, "miui_vip_activated");
            if (t > 0) return t;
            t = systemPropertyLong("ro.miui.activated_time");
            if (t > 0) return t;
            t = systemPropertyLong("ro.miui.activated");
            if (t > 0) return t;
            t = systemPropertyLong("ro.vendor.miui.activated_time");
            if (t > 0) return t;
            t = systemPropertyLong("ro.miui.saledate");
            if (t > 0) return t;
        }

        // OPPO/realme/一加：ColorOS/OxygenOS / realme UI 激活时间
        if (brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus")
                || manufacturer.contains("oppo") || manufacturer.contains("oneplus")) {
            long t = settingsLong(context, "oppo_activate_time");
            if (t > 0) return t;
            t = settingsLong(context, "oppo_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "oppo_activated");
            if (t > 0) return t;
            t = settingsLong(context, "coloros_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "coloros_activated");
            if (t > 0) return t;
            t = settingsLong(context, "coloros_activate_time");
            if (t > 0) return t;
            t = settingsLong(context, "oneplus_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "oplus_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "oplus_activated");
            if (t > 0) return t;
            t = settingsLong(context, "heytap_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "realme_activated_time");
            if (t > 0) return t;
            t = systemPropertyLong("ro.oppo.activated_time");
            if (t > 0) return t;
            t = systemPropertyLong("ro.oppo.activated");
            if (t > 0) return t;
            t = systemPropertyLong("ro.vendor.oppo.activated_time");
            if (t > 0) return t;
            t = systemPropertyLong("ro.oplus.activated_time");
            if (t > 0) return t;
        }

        // vivo/iQOO：OriginOS/FuntouchOS 激活时间
        if (brand.contains("vivo") || brand.contains("iqoo") || manufacturer.contains("vivo")) {
            long t = settingsLong(context, "vivo_active_time");
            if (t > 0) return t;
            t = settingsLong(context, "vivo_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "vivo_activated");
            if (t > 0) return t;
            t = settingsLong(context, "vivo_warranty_time");
            if (t > 0) return t;
            t = settingsLong(context, "vivo_activate_time");
            if (t > 0) return t;
            t = settingsLong(context, "bbk_active_time");
            if (t > 0) return t;
            t = settingsLong(context, "bbk_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "originos_activated_time");
            if (t > 0) return t;
            t = systemPropertyLong("ro.vivo.activated_time");
            if (t > 0) return t;
            t = systemPropertyLong("ro.vivo.activated");
            if (t > 0) return t;
            t = systemPropertyLong("ro.vendor.vivo.activated_time");
            if (t > 0) return t;
        }

        // 华为/荣耀：EMUI/MagicUI / HarmonyOS 激活时间
        if (brand.contains("huawei") || brand.contains("honor") || manufacturer.contains("huawei")) {
            long t = settingsLong(context, "huawei_first_boot_time");
            if (t > 0) return t;
            t = settingsLong(context, "huawei_warranty_time");
            if (t > 0) return t;
            t = settingsLong(context, "huawei_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "huawei_activation_time");
            if (t > 0) return t;
            t = parseDateString(settingsString(context, "huawei_activation_date"));
            if (t > 0) return t;
            t = settingsLong(context, "honor_first_boot_time");
            if (t > 0) return t;
            t = settingsLong(context, "honor_activated_time");
            if (t > 0) return t;
            t = parseDateString(settingsString(context, "honor_activation_date"));
            if (t > 0) return t;
            t = settingsLong(context, "hms_activate_time");
            if (t > 0) return t;
            t = systemPropertyLong("ro.hw.oem.activated");
            if (t > 0) return t;
            t = systemPropertyLong("ro.vendor.hw.activated");
            if (t > 0) return t;
            t = systemPropertyLong("ro.honor.activated");
            if (t > 0) return t;
        }

        // 魅族：Flyme 激活时间
        if (brand.contains("meizu") || manufacturer.contains("meizu")) {
            long t = settingsLong(context, "meizu_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "meizu_activated");
            if (t > 0) return t;
            t = settingsLong(context, "meizu_activation_time");
            if (t > 0) return t;
            t = settingsLong(context, "flyme_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "flyme_activated");
            if (t > 0) return t;
        }

        // 三星：One UI 激活时间
        if (brand.contains("samsung") || manufacturer.contains("samsung")) {
            long t = settingsLong(context, "samsung_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "samsung_activated");
            if (t > 0) return t;
            t = settingsLong(context, "sec_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "sec_warranty_time");
            if (t > 0) return t;
            t = parseDateString(settingsString(context, "knox_activation_date"));
            if (t > 0) return t;
        }

        // 中兴/努比亚/红魔
        if (brand.contains("nubia") || brand.contains("redmagic") || brand.contains("zte")
                || manufacturer.contains("nubia") || manufacturer.contains("zte")) {
            long t = settingsLong(context, "nubia_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "nubia_activated");
            if (t > 0) return t;
            t = settingsLong(context, "redmagic_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "zte_activated_time");
            if (t > 0) return t;
            t = settingsLong(context, "zte_activated");
            if (t > 0) return t;
        }

        // 通用：尝试常见的通用电子保卡/激活时间键名
        // 注意：first_boot_time / ro.runtime.firstboot 属于“首次开机”而非电子保卡，
        // 交给 detect() 的后续 fallback 处理，避免置信度虚高。
        long t = settingsLong(context, "electronic_warranty_activated_time");
        if (t > 0) return t;
        t = settingsLong(context, "electronic_warranty_activated");
        if (t > 0) return t;
        t = settingsLong(context, "device_activated_time");
        if (t > 0) return t;
        t = settingsLong(context, "device_activate_time");
        if (t > 0) return t;
        t = settingsLong(context, "first_activate_time");
        if (t > 0) return t;
        t = settingsLong(context, "first_use_time");
        if (t > 0) return t;
        t = settingsLong(context, "device_first_use_time");
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
