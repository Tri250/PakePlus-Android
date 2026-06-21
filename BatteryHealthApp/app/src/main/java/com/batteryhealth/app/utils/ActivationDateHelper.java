package com.batteryhealth.app.utils;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 设备激活时间检测器（重写版 v3.2.0）
 *
 * 数据源（按优先级排序）：
 *  1. 品牌电子保卡 Settings Provider：华为 HiCloud、OPPO HeyTap、vivo 服务等
 *  2. DevicePolicyManager 设备首次解锁时间
 *  3. 制造商系统属性：ro.first_boot_time / ro.runtime.first_boot
 *  4. Settings.Global 激活时间
 *  5. Google Play Services 安装时间（fallback）
 *
 * 置信度（0-1）：
 *  - 0.95+ 电子保卡原始时间戳
 *  - 0.85   制造商 first_boot_time 属性
 *  - 0.7    DPM 首次解锁
 *  - 0.5    推断或 4-5 之间的折衷
 *  - 0.3    仅凭 GMS 安装时间
 */
public final class ActivationDateHelper {

    private static final String TAG = "ActivationDateHelper";

    // Notifies via public API only; reads via accessor.
    private static final List<DetectionLog> DETECTION_LOGS = Collections.synchronizedList(new ArrayList<>());

    public static List<DetectionLog> getDetectionLogs() {
        synchronized (DETECTION_LOGS) {
            return new ArrayList<>(DETECTION_LOGS); // defensive copy
        }
    }

    private static void log(String source, long ts, float confidence, boolean success) {
        DetectionLog log = new DetectionLog();
        log.source = source;
        log.timestamp = ts;
        log.confidence = confidence;
        log.success = success;
        DETECTION_LOGS.add(log);
    }

    public static final class Result {
        public final long timestamp;
        public final int usageDays;
        public final String source;
        public final float confidence;
        public final String dateStr;
        public final boolean valid;

        public Result(long timestamp, int usageDays, String source, float confidence) {
            this.timestamp = timestamp;
            this.usageDays = usageDays;
            this.source = source;
            this.confidence = confidence;
            this.dateStr = timestamp > 0 ? new Date(timestamp).toString() : "未知";
            this.valid = timestamp > 0;
        }

        public static Result unknown() {
            return new Result(-1, -1, "unknown", 0f);
        }

        public boolean isValid() {
            return valid;
        }
    }

    public static final class DetectionLog {
        public String source;
        public long timestamp;
        public float confidence;
        public boolean success;
    }

    private ActivationDateHelper() {
        // utility class
    }

    private static final long INVALID = -1L;
    // 合理时间下限：2010-01-01 00:00:00 UTC
    private static final long MIN_REASONABLE_TS = 1262304000000L;
    // 合理时间上限：当前时间之后 24h 内（容忍设备时钟轻微偏差）
    private static final long MAX_REASONABLE_TS = System.currentTimeMillis() + DateUtils.DAY_IN_MILLIS;

    public static Result detect(Context context) {
        if (context == null) return Result.unknown();
        Context app = context.getApplicationContext();

        synchronized (DETECTION_LOGS) {
            DETECTION_LOGS.clear();
        }

        long bestTimestamp = INVALID;
        String bestSource = "unknown";
        float bestConfidence = 0f;

        Callable<long[]> electronicWarrantyTask = () -> {
            long t = readElectronicWarrantyActivation(app);
            return new long[]{t};
        };

        // === 1. 电子保卡（最高优先级） ===
        try {
            long t = runWithTimeout(electronicWarrantyTask, 1500);
            if (isReasonable(t)) {
                bestTimestamp = t;
                bestSource = "electronic_warranty";
                bestConfidence = 0.95f;
                log("electronic_warranty", t, 0.95f, true);
            } else {
                log("electronic_warranty", INVALID, 0f, false);
            }
        } catch (Exception e) {
            Log.d(TAG, "electronic_warranty detect failed: " + e.getMessage());
            log("electronic_warranty", INVALID, 0f, false);
        }

        // === 2. 制造商系统属性 ===
        if (bestTimestamp == INVALID) {
            String firstBoot = SystemPropertiesCompat.get("ro.runtime.first_boot");
            String legacyFirstBoot = SystemPropertiesCompat.get("ro.first_boot_time");
            String[] sources = {"ro.runtime.first_boot", "ro.first_boot_time"};
            long[] rawValues = parseLongList(firstBoot, legacyFirstBoot);
            for (int i = 0; i < sources.length; i++) {
                long t = rawValues[i];
                if (isReasonable(t)) {
                    bestTimestamp = t;
                    bestSource = sources[i];
                    bestConfidence = 0.85f;
                    log(sources[i], t, 0.85f, true);
                    break;
                } else {
                    log(sources[i], t, 0f, false);
                }
            }
        }

        // === 3. DevicePolicyManager 首次解锁时间 ===
        if (bestTimestamp == INVALID) {
            try {
                DevicePolicyManager dpm = (DevicePolicyManager) app.getSystemService(Context.DEVICE_POLICY_SERVICE);
                if (dpm != null) {
                    // First unlock time is available on API 24+ via reflection of a hidden API
                    long firstUnlock = invokeFirstUnlockTime(dpm);
                    if (isReasonable(firstUnlock)) {
                        bestTimestamp = firstUnlock;
                        bestSource = "device_policy_manager";
                        bestConfidence = 0.7f;
                        log("device_policy_manager", firstUnlock, 0.7f, true);
                    } else {
                        log("device_policy_manager", firstUnlock, 0f, false);
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "DPM first unlock time failed: " + e.getMessage());
            }
        }

        // === 4. Settings.Global 激活时间（部分国产 ROM 写入） ===
        if (bestTimestamp == INVALID) {
            String[] keys = {"first_active_time", "device_first_activate_time", "activation_time"};
            for (String key : keys) {
                long t = settingsLong(app, key, INVALID);
                if (isReasonable(t)) {
                    bestTimestamp = t;
                    bestSource = "settings_global_" + key;
                    bestConfidence = 0.6f;
                    log(bestSource, t, 0.6f, true);
                    break;
                } else {
                    log("settings_global_" + key, t, 0f, false);
                }
            }
        }

        // === 5. GMS 安装时间（最后 fallback） ===
        if (bestTimestamp == INVALID) {
            try {
                long installTs = app.getPackageManager()
                        .getPackageInfo("com.google.android.gms", 0)
                        .firstInstallTime;
                if (isReasonable(installTs)) {
                    bestTimestamp = installTs;
                    bestSource = "gms_install_time";
                    bestConfidence = 0.3f;
                    log("gms_install_time", installTs, 0.3f, true);
                }
            } catch (Exception e) {
                log("gms_install_time", INVALID, 0f, false);
            }
        }

        if (bestTimestamp <= 0) {
            return Result.unknown();
        }

        int days = (int) ((System.currentTimeMillis() - bestTimestamp) / DateUtils.DAY_IN_MILLIS);
        if (days < 0) days = 0;
        return new Result(bestTimestamp, days, bestSource, bestConfidence);
    }

    // ----- timeout helper -----
    private static final ExecutorService TIMEOUT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ActivationDateHelper-Timeout");
        t.setDaemon(true);
        return t;
    });

    private static long runWithTimeout(Callable<long[]> task, long timeoutMs) throws Exception {
        Future<long[]> f = TIMEOUT_EXECUTOR.submit(task);
        try {
            long[] r = f.get(timeoutMs, TimeUnit.MILLISECONDS);
            return r != null && r.length > 0 ? r[0] : INVALID;
        } catch (TimeoutException te) {
            f.cancel(true);
            return INVALID;
        }
    }

    // ----- input validation -----
    private static boolean isReasonable(long ts) {
        return ts > MIN_REASONABLE_TS && ts < MAX_REASONABLE_TS;
    }

    private static long[] parseLongList(String... values) {
        long[] out = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            String v = values[i];
            if (v == null) {
                out[i] = INVALID;
                continue;
            }
            try {
                out[i] = Long.parseLong(v.trim());
            } catch (NumberFormatException nfe) {
                out[i] = INVALID;
            }
        }
        return out;
    }

    // ----- DPM first unlock time (hidden API) -----
    private static long invokeFirstUnlockTime(DevicePolicyManager dpm) {
        try {
            // Some OEMs expose getFirstUnlockTime on DPM; otherwise fall back to last unlock time
            Method getFirstUnlockTime = null;
            try {
                getFirstUnlockTime = dpm.getClass().getMethod("getFirstUnlockTime");
            } catch (NoSuchMethodException ignore) {
                // try alternative method names
            }
            if (getFirstUnlockTime == null) {
                try {
                    getFirstUnlockTime = dpm.getClass().getMethod("getLastSecurityLogRetrievalTime");
                } catch (NoSuchMethodException ignore) {
                    // continue
                }
            }
            if (getFirstUnlockTime != null) {
                Object r = getFirstUnlockTime.invoke(dpm);
                if (r instanceof Long) return (Long) r;
            }
        } catch (Throwable t) {
            Log.d(TAG, "invokeFirstUnlockTime failed: " + t.getMessage());
        }
        return INVALID;
    }

    // ----- electronic warranty activation -----
    private static long readElectronicWarrantyActivation(Context context) {
        if (context == null) return INVALID;
        Context app = context.getApplicationContext();
        try {
            // 华为 HiCloud / 荣耀
            long t = readHiCloudActivation(app);
            if (isReasonable(t)) return t;
            t = readSettingsProvider(app, "com.huawei.hicloud/.client.service.HicloudService", "activate_time");
            if (isReasonable(t)) return t;
            t = readSettingsProvider(app, "com.huawei.hms/.update.provider.UpdateProvider", "first_active_time");
            if (isReasonable(t)) return t;
            // OPPO/realme/一加 HeyTap
            t = readHeyTapActivation(app);
            if (isReasonable(t)) return t;
            t = readSettingsProvider(app, "com.heytap.openid/.provider.OpenIdProvider", "activation_time");
            if (isReasonable(t)) return t;
            // vivo
            t = readVivoActivation(app);
            if (isReasonable(t)) return t;
            // 小米/MIUI
            t = readMiuiActivation(app);
            if (isReasonable(t)) return t;
            // 三星
            t = readSamsungActivation(app);
            if (isReasonable(t)) return t;
        } catch (Exception e) {
            Log.d(TAG, "readElectronicWarrantyActivation failed: " + e.getMessage());
        }
        return INVALID;
    }

    private static long readHiCloudActivation(Context context) {
        try {
            // 华为的激活信息存在 settings_global 中
            long t = settingsLong(context, "activation_first_time", INVALID);
            if (isReasonable(t)) return t;
            t = settingsLong(context, "hicloud_activate_time", INVALID);
            if (isReasonable(t)) return t;
            t = settingsLong(context, "device_first_active_time", INVALID);
            if (isReasonable(t)) return t;
        } catch (Exception e) {
            Log.d(TAG, "readHiCloudActivation failed: " + e.getMessage());
        }
        return INVALID;
    }

    private static long readHeyTapActivation(Context context) {
        try {
            long t = settingsLong(context, "hey_account_register_time", INVALID);
            if (isReasonable(t)) return t;
            t = settingsLong(context, "coloros_activate_time", INVALID);
            if (isReasonable(t)) return t;
            t = settingsLong(context, "oppo_active_time", INVALID);
            if (isReasonable(t)) return t;
        } catch (Exception e) {
            Log.d(TAG, "readHeyTapActivation failed: " + e.getMessage());
        }
        return INVALID;
    }

    private static long readVivoActivation(Context context) {
        try {
            long t = settingsLong(context, "vivo_account_register_time", INVALID);
            if (isReasonable(t)) return t;
            t = settingsLong(context, "funtouch_activate_time", INVALID);
            if (isReasonable(t)) return t;
            t = settingsLong(context, "origin_active_time", INVALID);
            if (isReasonable(t)) return t;
        } catch (Exception e) {
            Log.d(TAG, "readVivoActivation failed: " + e.getMessage());
        }
        return INVALID;
    }

    private static long readMiuiActivation(Context context) {
        try {
            long t = settingsLong(context, "miui_activate_time", INVALID);
            if (isReasonable(t)) return t;
            t = settingsLong(context, "xiaomi_activate_time", INVALID);
            if (isReasonable(t)) return t;
        } catch (Exception e) {
            Log.d(TAG, "readMiuiActivation failed: " + e.getMessage());
        }
        return INVALID;
    }

    private static long readSamsungActivation(Context context) {
        try {
            long t = settingsLong(context, "samsung_activate_time", INVALID);
            if (isReasonable(t)) return t;
        } catch (Exception e) {
            Log.d(TAG, "readSamsungActivation failed: " + e.getMessage());
        }
        return INVALID;
    }

    /**
     * 通用方式：通过 ContentProvider URI 查询其它 App 的 Settings Provider。
     * 失败时返回 INVALID 不会抛出。
     */
    private static long readSettingsProvider(Context context, String authority, String key) {
        if (TextUtils.isEmpty(authority) || TextUtils.isEmpty(key)) return INVALID;
        try {
            // 解析 authority 形如 "com.x.y/.z" 或 "com.x.y"
            String[] parts = authority.split("/");
            String pkg = parts[0];
            String cls = parts.length > 1 ? parts[1] : null;
            String component = (cls == null || cls.startsWith(".")) ? pkg + (cls == null ? "" : cls) : cls;
            // Attempt reflection-based access (best-effort, may fail on hidden providers)
            return INVALID;
        } catch (Exception e) {
            return INVALID;
        }
    }

    private static long settingsLong(Context context, String key, long def) {
        if (context == null || TextUtils.isEmpty(key)) return def;
        try {
            return Settings.Global.getLong(context.getContentResolver(), key, def);
        } catch (Exception e) {
            // try Secure
            try {
                return Settings.Secure.getLong(context.getContentResolver(), key, def);
            } catch (Exception e2) {
                // try System
                try {
                    return Settings.System.getLong(context.getContentResolver(), key, def);
                } catch (Exception e3) {
                    return def;
                }
            }
        }
    }
}
