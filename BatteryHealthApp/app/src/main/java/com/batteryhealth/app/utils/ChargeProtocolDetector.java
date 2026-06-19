package com.batteryhealth.app.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

/**
 * 充电协议识别工具：
 * 通过读取 sysfs 节点与系统属性，识别当前充电使用的协议。
 *
 * 支持的协议：
 *  - QC 2.0/3.0/4+ (Qualcomm Quick Charge)
 *  - PD 2.0/3.0/3.1 (USB Power Delivery, 含 PPS)
 *  - SCP / FCP (Huawei)
 *  - VOOC / SuperVOOC / Dart (OPPO / realme)
 *  - FlashCharge / SuperFlashCharge (vivo)
 *  - MI Turbo Charge / HyperCharge (Xiaomi)
 *  - Samsung Adaptive Fast Charging
 *  - Apple (lightning/usb-c pd)
 */
public class ChargeProtocolDetector {

    private static final String TAG = "ChargeProtocolDetector";

    public static final class Result {
        public final String primary;        // 主要协议
        public final String secondary;      // 次要协议
        public final String detail;         // 详情描述
        public final float powerW;          // 当前功率
        public final boolean fastCharging;  // 是否快充

        public Result(String primary, String secondary, String detail, float powerW, boolean fastCharging) {
            this.primary = primary;
            this.secondary = secondary;
            this.detail = detail;
            this.powerW = powerW;
            this.fastCharging = fastCharging;
        }
    }

    /**
     * 通过系统属性和 sysfs 综合判定当前充电协议。
     */
    public static Result detect(Context context, float currentPowerW) {
        // 1. 读取 sysfs
        StringBuilder details = new StringBuilder();
        String primary = readFileTrim("/sys/class/power_supply/battery/charge_type");
        String adapterType = readFileTrim("/sys/class/power_supply/usb/typec_mode");
        String currentType = readFileTrim("/sys/class/power_supply/battery/constant_charge_current_max");
        // 2. 系统属性
        String quickCharge = sysProperty("persist.sys.quick_charge");
        String powerDelivery = sysProperty("persist.sys.pd");
        // 3. 制造商
        String mfg = Build.MANUFACTURER.toLowerCase();
        String brand = Build.BRAND.toLowerCase();

        boolean fast = currentPowerW >= 18.0f;
        String result = "标准充电";
        String secondary = "";

        // 高通 QC
        if ("Qualcomm".equalsIgnoreCase(Build.SOC_MANUFACTURER)
                || "qc".equalsIgnoreCase(quickCharge) || "qc3".equalsIgnoreCase(quickCharge)
                || "qc4".equalsIgnoreCase(quickCharge) || "qcb".equalsIgnoreCase(quickCharge)) {
            if (currentPowerW >= 60) result = "QC 5 (Quick Charge 5)";
            else if (currentPowerW >= 27) result = "QC 4+";
            else if (currentPowerW >= 18) result = "QC 3.0";
            else if (currentPowerW >= 10) result = "QC 2.0";
            fast = true;
        }
        // USB PD
        else if (powerDelivery != null && powerDelivery.toLowerCase().contains("pd")) {
            if (currentPowerW >= 100) result = "USB PD 3.1 EPR";
            else if (currentPowerW >= 60) result = "USB PD 3.0 / PPS";
            else if (currentPowerW >= 30) result = "USB PD 2.0 / 3.0";
            else result = "USB PD";
            fast = true;
        }
        // 华为 SCP/FCP
        else if (mfg.contains("huawei") || brand.contains("huawei") || brand.contains("honor")) {
            if (currentPowerW >= 100) result = "SCP (100W)";
            else if (currentPowerW >= 66) result = "SCP 2.0 (66W)";
            else if (currentPowerW >= 40) result = "SCP (40W)";
            else if (currentPowerW >= 22) result = "FCP (22.5W)";
            else if (currentPowerW >= 18) result = "FCP (18W)";
            fast = true;
        }
        // 小米
        else if (mfg.contains("xiaomi") || brand.contains("xiaomi") || brand.contains("redmi")) {
            if (currentPowerW >= 120) result = "小米 HyperCharge (120W)";
            else if (currentPowerW >= 67) result = "小米 Turbo Charge (67W)";
            else if (currentPowerW >= 33) result = "小米快充 (33W)";
            else if (currentPowerW >= 18) result = "QC 3.0 兼容";
            fast = currentPowerW >= 18;
        }
        // OPPO/realme/一加
        else if (mfg.contains("oppo") || brand.contains("oppo") || brand.contains("realme")
                || mfg.contains("oneplus") || brand.contains("oneplus")) {
            if (currentPowerW >= 100) result = "SuperVOOC 240W / 100W+";
            else if (currentPowerW >= 80) result = "SuperVOOC 80W";
            else if (currentPowerW >= 65) result = "SuperVOOC 65W / VOOC 4.0";
            else if (currentPowerW >= 30) result = "VOOC 30W";
            else if (currentPowerW >= 20) result = "VOOC 20W";
            fast = currentPowerW >= 20;
        }
        // vivo/iQOO
        else if (mfg.contains("vivo") || brand.contains("vivo") || brand.contains("iqoo")) {
            if (currentPowerW >= 120) result = "vivo FlashCharge 120W";
            else if (currentPowerW >= 80) result = "vivo FlashCharge 80W";
            else if (currentPowerW >= 44) result = "vivo FlashCharge 44W";
            else if (currentPowerW >= 33) result = "vivo FlashCharge 33W";
            else if (currentPowerW >= 22) result = "vivo Dual-Engine 22.5W";
            fast = currentPowerW >= 22;
        }
        // 三星
        else if (mfg.contains("samsung") || brand.contains("samsung")) {
            if (currentPowerW >= 45) result = "Samsung Super Fast Charging 2.0";
            else if (currentPowerW >= 25) result = "Samsung Super Fast Charging";
            else if (currentPowerW >= 15) result = "Samsung Adaptive Fast Charging";
            else if (currentPowerW >= 10) result = "Samsung Fast Charging";
            fast = currentPowerW >= 15;
        }

        // 兜底：按功率分档
        if ("标准充电".equals(result)) {
            if (currentPowerW >= 5) {
                result = "BC 1.2 / Apple 5W+";
            }
        }

        if (currentPowerW > 0) {
            details.append(String.format("%.1f W", currentPowerW));
        }
        if (adapterType != null && !adapterType.isEmpty()) {
            if (details.length() > 0) details.append(" · ");
            details.append("USB ").append(adapterType);
        }

        return new Result(result, secondary, details.toString(), currentPowerW, fast);
    }

    private static String readFileTrim(String path) {
        try {
            File f = new File(path);
            if (!f.exists() || !f.canRead()) return "";
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line = r.readLine();
                return line != null ? line.trim() : "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private static String sysProperty(String key) {
        try {
            return (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class)
                    .invoke(null, key, "");
        } catch (Exception e) {
            return "";
        }
    }
}
