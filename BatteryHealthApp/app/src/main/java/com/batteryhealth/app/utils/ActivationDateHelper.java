package com.batteryhealth.app.utils;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 激活日期检测工具。
 *
 * 优先级（参考手机系统电子保卡注册为准）：
 *  0. 各品牌系统电子保卡激活时间（最高置信度）
 *  1. Settings.Global first_boot_time
 *  2. DevicePolicyManager.getProvisioningTime
 *  3. Google Play 服务首次安装时间
 *  4. 系统框架首次安装时间
 *  5. 应用首次安装时间
 *  6. 应用数据目录创建时间
 *
 * 使用天数计算：按自然日计算（非24小时制），即激活当天算第1天。
 * 例：6月18日激活，6月19日显示"已使用2天"。
 */
public final class ActivationDateHelper {

    private ActivationDateHelper() {}

    /** 一天的毫秒数 */
    private static final long MILLIS_PER_DAY = 86_400_000L;

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
            if (firstBoot > 0) return build(normalizeTimestamp(firstBoot), "system_first_boot_time", 0.95f);
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

    /**
     * 构建结果，使用天数按自然日计算（激活当天算第1天）。
     */
    private static Result build(long timestamp, String source, float confidence) {
        int usageDays = -1;
        if (timestamp > 0) {
            long now = System.currentTimeMillis();
            if (timestamp <= now) {
                usageDays = calculateNaturalDays(timestamp, now);
                if (usageDays < 1) usageDays = 1;
            }
        }
        return new Result(timestamp, source, confidence, usageDays);
    }

    /**
     * 按自然日计算使用天数（激活当天算第1天）。
     * 与手机系统"关于手机"中显示的使用天数一致。
     * 例：6月18日激活 → 6月18日显示1天，6月19日显示2天。
     */
    private static int calculateNaturalDays(long activationMs, long nowMs) {
        Calendar actCal = Calendar.getInstance();
        actCal.setTimeInMillis(activationMs);
        // 清零时分秒毫秒，只保留日期
        actCal.set(Calendar.HOUR_OF_DAY, 0);
        actCal.set(Calendar.MINUTE, 0);
        actCal.set(Calendar.SECOND, 0);
        actCal.set(Calendar.MILLISECOND, 0);

        Calendar nowCal = Calendar.getInstance();
        nowCal.setTimeInMillis(nowMs);
        nowCal.set(Calendar.HOUR_OF_DAY, 0);
        nowCal.set(Calendar.MINUTE, 0);
        nowCal.set(Calendar.SECOND, 0);
        nowCal.set(Calendar.MILLISECOND, 0);

        long diffMs = nowCal.getTimeInMillis() - actCal.getTimeInMillis();
        int days = (int) TimeUnit.MILLISECONDS.toDays(diffMs) + 1; // 激活当天算第1天
        return days;
    }

    /**
     * 校正时间戳：部分品牌系统属性返回秒级时间戳（10位），需转为毫秒（13位）。
     * 判断依据：若值 < 2000-01-01 的毫秒时间戳，则认为是秒级。
     */
    private static long normalizeTimestamp(long ts) {
        // 2000-01-01 00:00:00 UTC 的毫秒时间戳
        final long Y2K_MS = 946_684_800_000L;
        if (ts > 0 && ts < Y2K_MS) {
            // 值小于2000年的毫秒时间戳，大概率是秒级时间戳
            return ts * 1000L;
        }
        return ts;
    }

    private static long readElectronicWarrantyActivation(Context context) {
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase(Locale.ROOT) : "";
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(Locale.ROOT) : "";

        // 小米/红米/POCO：MIUI/HyperOS 激活时间
        if (brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")
                || manufacturer.contains("xiaomi")) {
            // MIUI 12+ / HyperOS 使用 miui_activated_time（毫秒时间戳）
            long t = settingsLong(context, "miui_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "miui_activated");
            if (t > 0) return normalizeTimestamp(t);
            t = systemPropertyLong("ro.miui.activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = systemPropertyLong("ro.miui.activated");
            if (t > 0) return normalizeTimestamp(t);
            // HyperOS 新增键名
            t = settingsLong(context, "hyperos_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = systemPropertyLong("persist.sys.miui.activation_time");
            if (t > 0) return normalizeTimestamp(t);
        }

        // OPPO/realme/一加：ColorOS/OxygenOS 激活时间
        if (brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus")
                || manufacturer.contains("oppo") || manufacturer.contains("oneplus")) {
            long t = settingsLong(context, "oppo_activate_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "oppo_activated");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "coloros_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "coloros_activated");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "oneplus_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "oplus_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = systemPropertyLong("ro.oppo.activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = systemPropertyLong("ro.oppo.activated");
            if (t > 0) return normalizeTimestamp(t);
            t = systemPropertyLong("ro.oplus.activated_time");
            if (t > 0) return normalizeTimestamp(t);
            // realme UI
            t = settingsLong(context, "realme_activated_time");
            if (t > 0) return normalizeTimestamp(t);
        }

        // vivo/iQOO：OriginOS/FuntouchOS 激活时间
        if (brand.contains("vivo") || brand.contains("iqoo") || manufacturer.contains("vivo")) {
            long t = settingsLong(context, "vivo_active_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "vivo_activated");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "vivo_warranty_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "vivo_activate_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "vivo_activation_time");
            if (t > 0) return normalizeTimestamp(t);
            t = systemPropertyLong("ro.vivo.activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = systemPropertyLong("ro.vivo.activated");
            if (t > 0) return normalizeTimestamp(t);
            // iQOO 专属
            t = settingsLong(context, "iqoo_activated_time");
            if (t > 0) return normalizeTimestamp(t);
        }

        // 华为/荣耀：EMUI/MagicUI 激活时间
        if (brand.contains("huawei") || brand.contains("honor") || manufacturer.contains("huawei")
                || manufacturer.contains("honor")) {
            long t = settingsLong(context, "huawei_first_boot_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "huawei_warranty_time");
            if (t > 0) return normalizeTimestamp(t);
            t = parseDateString(settingsString(context, "huawei_activation_date"));
            if (t > 0) return t;
            t = settingsLong(context, "honor_first_boot_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "honor_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = systemPropertyLong("ro.hw.oem.activated");
            if (t > 0) return normalizeTimestamp(t);
            // HarmonyOS 新增
            t = settingsLong(context, "harmonyos_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "huawei_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            // 荣耀独立后 MagicOS
            t = settingsLong(context, "magicos_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "honor_activation_date");
            if (t > 0) return normalizeTimestamp(t);
        }

        // 魅族：Flyme 激活时间
        if (brand.contains("meizu") || manufacturer.contains("meizu")) {
            long t = settingsLong(context, "meizu_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "meizu_activated");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "flyme_activated_time");
            if (t > 0) return normalizeTimestamp(t);
        }

        // 三星：One UI 激活时间
        if (brand.contains("samsung") || manufacturer.contains("samsung")) {
            long t = settingsLong(context, "samsung_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "samsung_activated");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "sec_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = systemPropertyLong("ro.shipment_date");
            if (t > 0) return normalizeTimestamp(t);
        }

        // 中兴/努比亚/红魔
        if (brand.contains("nubia") || brand.contains("redmagic") || brand.contains("zte")
                || manufacturer.contains("nubia") || manufacturer.contains("zte")) {
            long t = settingsLong(context, "nubia_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "zte_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "redmagic_activated_time");
            if (t > 0) return normalizeTimestamp(t);
        }

        // 传音系（Tecno/Infinix/Itel）- 非洲/东南亚/中东市场大品牌
        if (brand.contains("tecno") || brand.contains("infinix") || brand.contains("itel")
                || manufacturer.contains("tecno") || manufacturer.contains("infinix")
                || manufacturer.contains("itel")) {
            long t = settingsLong(context, "tecno_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "infinix_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "itel_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "transsion_activated_time");
            if (t > 0) return normalizeTimestamp(t);
        }

        // 联想/摩托罗拉
        if (brand.contains("lenovo") || brand.contains("motorola") || brand.contains("moto")
                || manufacturer.contains("lenovo") || manufacturer.contains("motorola")) {
            long t = settingsLong(context, "lenovo_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "moto_activated_time");
            if (t > 0) return normalizeTimestamp(t);
            t = settingsLong(context, "motorola_activated_time");
            if (t > 0) return normalizeTimestamp(t);
        }

        // 华硕/ROG
        if (brand.contains("asus") || brand.contains("rog")
                || manufacturer.contains("asus")) {
            long t = settingsLong(context, "asus_activated_time");
            if (t > 0) return normalizeTimestamp(t);
        }

        // 通用：尝试常见的通用激活时间键名
        long t = settingsLong(context, "electronic_warranty_activated_time");
        if (t > 0) return normalizeTimestamp(t);
        t = settingsLong(context, "electronic_warranty_activated");
        if (t > 0) return normalizeTimestamp(t);
        t = settingsLong(context, "device_activated_time");
        if (t > 0) return normalizeTimestamp(t);
        t = settingsLong(context, "device_activate_time");
        if (t > 0) return normalizeTimestamp(t);
        t = settingsLong(context, "first_activate_time");
        if (t > 0) return normalizeTimestamp(t);
        t = settingsLong(context, "warranty_start_time");
        if (t > 0) return normalizeTimestamp(t);
        t = settingsLong(context, "device_first_use_time");
        if (t > 0) return normalizeTimestamp(t);
        t = systemPropertyLong("ro.runtime.firstboot");
        if (t > 0) return normalizeTimestamp(t);
        t = systemPropertyLong("ro.build.date.utc");
        if (t > 0) return normalizeTimestamp(t);
        return -1;
    }

    private static long settingsLong(Context context, String key) {
        try {
            return Settings.Secure.getLong(context.getContentResolver(), key, -1);
        } catch (Exception e) {
            try {
                return Settings.Global.getLong(context.getContentResolver(), key, -1);
            } catch (Exception ignored) {
                return -1;
            }
        }
    }

    private static String settingsString(Context context, String key) {
        try {
            String value = Settings.Secure.getString(context.getContentResolver(), key);
            if (value != null && !value.isEmpty()) return value;
            return Settings.Global.getString(context.getContentResolver(), key);
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
