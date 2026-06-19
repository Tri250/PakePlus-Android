package com.batteryhealth.app.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

public class BugreportHelper {
    private static final String TAG = "BugreportHelper";

    public static class BugreportInfo {
        public String brandName;
        public String dialCode;
        public String method;
        public String adbCommand;
    }

    public static BugreportInfo getBugreportInfo() {
        BugreportInfo info = new BugreportInfo();
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase(Locale.ROOT) : "";
        info.adbCommand = "adb bugreport bugreport.zip";

        if (brand.contains("xiaomi") || brand.contains("redmi")) {
            info.brandName = "小米/红米";
            info.dialCode = "*#*#284#*#*";
            info.method = "拨号盘输入 " + info.dialCode + " 或在 设置→我的设备→全部参数→连续点击「内核版本」";
        } else if (brand.contains("oppo") || brand.contains("realme")) {
            info.brandName = "OPPO/realme";
            info.dialCode = "*#800#";
            info.method = "拨号盘输入 " + info.dialCode;
        } else if (brand.contains("oneplus")) {
            info.brandName = "一加";
            info.dialCode = "*#800#";
            info.method = "拨号盘输入 " + info.dialCode;
        } else if (brand.contains("vivo") || brand.contains("iqoo")) {
            info.brandName = "vivo/iQOO";
            info.dialCode = "*#*#4838#*#*";
            info.method = "拨号盘输入 " + info.dialCode;
        } else if (brand.contains("huawei") || brand.contains("honor")) {
            info.brandName = "华为/荣耀";
            info.dialCode = "*#*#2846579#*#*";
            info.method = "拨号盘输入 " + info.dialCode + " → 后台设置 → LOG打开 → 生成bugreport";
        } else if (brand.contains("samsung")) {
            info.brandName = "三星";
            info.dialCode = "*#9900#";
            info.method = "拨号盘输入 " + info.dialCode + " → 选择 Run dumpstate/logcat";
        } else if (brand.contains("meizu")) {
            info.brandName = "魅族";
            info.dialCode = "*#*#6961#*#*";
            info.method = "拨号盘输入 " + info.dialCode;
        } else {
            info.brandName = "通用";
            info.dialCode = "";
            info.method = "通过USB连接电脑，执行 adb bugreport bugreport.zip";
        }
        return info;
    }

    public static boolean isUsbDebuggingEnabled(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0) > 0;
        } catch (Exception e) { return false; }
    }

    public static void copyAdbCommand(Context context) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("adb command", "adb bugreport bugreport.zip");
        clipboard.setPrimaryClip(clip);
    }

    public static String collectLogcat() {
        StringBuilder sb = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "threadtime", "*:E"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < 500) {
                sb.append(line).append("\n");
                count++;
            }
            reader.close();
            process.destroy();
        } catch (Exception e) { Log.e(TAG, "Error collecting logcat: " + e.getMessage()); }
        return sb.toString();
    }
}