package com.batteryhealth.app.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * Bugreport 获取指南助手
 * 提供按品牌的拨号指令、ADB 命令复制、USB 调试检测等能力。
 *
 * 使用说明（对应 2.1.14）：
 * 1. 优先使用对应品牌拨号指令生成 bugreport；
 * 2. 若拨号指令失效或品牌无专用指令，使用 ADB 命令生成；
 * 3. 生成后通过系统文件管理器找到 bugreport 文件（通常为 zip）；
 * 4. 回到本应用「配置 → 上传并分析 Bugreport」选择该文件。
 */
public class BugreportHelper {
    private static final String TAG = "BugreportHelper";
    private static final String ADB_COMMAND = "adb bugreport bugreport.zip";

    public static class BugreportInfo {
        public final String brandName;
        public final String dialCode;
        public final String method;
        public final String adbCommand;
        public final String savePathHint;

        public BugreportInfo(String brandName, String dialCode, String method,
                             String adbCommand, String savePathHint) {
            this.brandName = brandName;
            this.dialCode = dialCode;
            this.method = method;
            this.adbCommand = adbCommand;
            this.savePathHint = savePathHint;
        }
    }

    /**
     * 根据当前设备品牌返回对应的 Bugreport 获取指南。
     */
    public static BugreportInfo getBugreportInfo() {
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase() : "";
        String defaultSaveHint = "文件通常保存在系统通知栏提示位置，或通过 ADB 保存在当前电脑目录。";

        if (brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")) {
            return new BugreportInfo(
                    "小米 / Redmi",
                    "*#*#284#*#*",
                    "拨号盘输入 *#*#284#*#*，等待状态栏提示“已抓取 bug 报告”后，从通知栏保存文件。",
                    ADB_COMMAND,
                    "通过拨号生成后，文件位于系统通知提示路径；通过 ADB 生成则保存在执行命令的目录。"
            );
        }
        if (brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus")) {
            return new BugreportInfo(
                    "OPPO / realme / OnePlus",
                    "*#800# 或 *#899#",
                    "拨号盘输入 *#800# 或 *#899# 进入工程模式，选择“日志抓取”或“生成 bugreport”。",
                    ADB_COMMAND,
                    defaultSaveHint
            );
        }
        if (brand.contains("vivo") || brand.contains("iqoo")) {
            return new BugreportInfo(
                    "vivo / iQOO",
                    "*#558#",
                    "拨号盘输入 *#558# 进入工程测试，选择“日志导出”生成 bugreport。",
                    ADB_COMMAND,
                    defaultSaveHint
            );
        }
        if (brand.contains("huawei") || brand.contains("honor")) {
            return new BugreportInfo(
                    "华为 / 荣耀",
                    "*#*#2846579#*#*",
                    "拨号盘输入 *#*#2846579#*#* → 后台设置 → 打开 LOG 开关 → 返回选择“生成 bugreport”。",
                    ADB_COMMAND,
                    defaultSaveHint
            );
        }
        if (brand.contains("samsung")) {
            return new BugreportInfo(
                    "三星",
                    "*#9900#",
                    "拨号盘输入 *#9900#，选择 run dumpstate/logcat，等待生成完成。",
                    ADB_COMMAND,
                    defaultSaveHint
            );
        }
        if (brand.contains("meizu")) {
            return new BugreportInfo(
                    "魅族",
                    "*#*#6961#*#*",
                    "拨号盘输入 *#*#6961#*#*，选择导出日志。",
                    ADB_COMMAND,
                    defaultSaveHint
            );
        }
        if (brand.contains("sony") || brand.contains("xperia")) {
            return new BugreportInfo(
                    "索尼 Xperia",
                    "无专用指令",
                    "请使用下方 ADB 命令生成 bugreport。",
                    ADB_COMMAND,
                    defaultSaveHint
            );
        }
        if (brand.contains("google") || brand.contains("pixel")) {
            return new BugreportInfo(
                    "Google Pixel",
                    "无专用指令",
                    "请使用下方 ADB 命令生成 bugreport：开发者选项中开启 USB 调试后连接电脑执行。",
                    ADB_COMMAND,
                    "执行命令后，bugreport.zip 保存在当前命令行目录。"
            );
        }
        return new BugreportInfo(
                Build.BRAND != null ? Build.BRAND : "未知品牌",
                "无专用指令",
                "请使用下方 ADB 命令生成 bugreport：设置 → 关于手机 → 连续点击版本号开启开发者选项 → 打开 USB 调试 → 连接电脑执行命令。",
                ADB_COMMAND,
                defaultSaveHint
        );
    }

    /**
     * 复制 ADB 命令到剪贴板。
     */
    public static void copyAdbCommand(Context context) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("ADB 命令", ADB_COMMAND);
                clipboard.setPrimaryClip(clip);
            }
        } catch (Exception e) {
            Log.e(TAG, "复制 ADB 命令失败", e);
        }
    }

    /**
     * 使用拨号盘打开指定代码。
     */
    public static void openDialPad(Context context, String code) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "打开拨号盘失败", e);
        }
    }

    /**
     * 检测 USB 调试是否开启。
     */
    public static boolean isUsbDebuggingEnabled(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
        } catch (Exception e) {
            Log.e(TAG, "检测 USB 调试失败", e);
            return false;
        }
    }
}
