package com.batteryhealth.app.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

/**
 * 电池化学配方识别工具
 * 基于 sysfs 节点和 technology 字段判断 Li-ion / LiPo / LiFePO4
 */
public class BatteryChemistryDetector {

    public static final String CHEMISTRY_LI_ION = "Li-ion";
    public static final String CHEMISTRY_LIPO = "LiPo (锂聚合物)";
    public static final String CHEMISTRY_LIFEPO4 = "LiFePO4 (磷酸铁锂)";
    public static final String CHEMISTRY_NIMH = "NiMH (镍氢)";
    public static final String CHEMISTRY_UNKNOWN = "未知";

    private static final String[] BATTERY_SYSFS_PATHS = {
            "/sys/class/power_supply/battery",
            "/sys/class/power_supply/bms",
            "/sys/class/power_supply/battery0",
            "/sys/class/power_supply/maxfg"
    };

    public static class ChemistryResult {
        public String chemistry;
        public String chemistryType;
        public String nominalVoltage;
        public String fullChargeVoltage;
        public String description;
        public int confidence;

        public ChemistryResult() {
            this.chemistry = CHEMISTRY_UNKNOWN;
            this.chemistryType = "unknown";
            this.confidence = 0;
        }
    }

    public static ChemistryResult detect(Context context) {
        ChemistryResult result = new ChemistryResult();

        String technology = getTechnology(context);
        String sysfsChemistry = readSysfsChemistry();
        float nominalVoltage = getNominalVoltage(context);

        result.nominalVoltage = nominalVoltage > 0
                ? String.format(Locale.getDefault(), "%.2f V", nominalVoltage)
                : "--";

        if (sysfsChemistry != null && !sysfsChemistry.isEmpty()) {
            identifyByChemistryField(sysfsChemistry, result);
        } else if (technology != null && !technology.isEmpty()) {
            identifyByTechnology(technology, result);
        }

        if (result.confidence < 60 && nominalVoltage > 0) {
            identifyByVoltage(nominalVoltage, result);
        }

        result.description = generateDescription(result);

        return result;
    }

    private static String getTechnology(Context context) {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent = androidx.core.content.ContextCompat.registerReceiver(
                        context, null,
                        new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                        androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
                );
            } else {
                intent = context.registerReceiver(null,
                        new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            }
            if (intent != null) {
                return intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static float getNominalVoltage(Context context) {
        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int voltage = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (voltage > 0) {
                    return voltage / 1000000f;
                }
            }
        } catch (Exception ignored) {}

        try {
            Intent intent = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent != null) {
                int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                if (voltage > 0) {
                    return voltage / 1000f;
                }
            }
        } catch (Exception ignored) {}

        return 0f;
    }

    private static String readSysfsChemistry() {
        String[] chemistryFiles = {
                "/sys/class/power_supply/battery/chemistry",
                "/sys/class/power_supply/bms/chemistry",
                "/sys/class/power_supply/battery/battery_chemistry",
                "/sys/class/power_supply/bms/battery_chemistry",
                "/sys/class/power_supply/battery/fg_chem",
                "/sys/class/power_supply/battery/type"
        };
        for (String path : chemistryFiles) {
            String value = readSysfsFile(path);
            if (value != null && !value.isEmpty() && !value.equalsIgnoreCase("unknown")) {
                return value.trim();
            }
        }
        return null;
    }

    private static String readSysfsFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists() || !f.canRead()) return null;
            try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                String line = reader.readLine();
                return line != null ? line.trim() : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void identifyByChemistryField(String chemistry, ChemistryResult result) {
        String lower = chemistry.toLowerCase(Locale.ROOT);

        if (lower.contains("lifepo") || lower.contains("lfp") || lower.contains("iron phosphate")) {
            result.chemistry = CHEMISTRY_LIFEPO4;
            result.chemistryType = "lifepo4";
            result.confidence = Math.max(result.confidence, 90);
        } else if (lower.contains("lipo") || lower.contains("li-poly") || lower.contains("polymer")) {
            result.chemistry = CHEMISTRY_LIPO;
            result.chemistryType = "lipo";
            result.confidence = Math.max(result.confidence, 85);
        } else if (lower.contains("lion") || lower.contains("li-ion") || lower.contains("liion")) {
            result.chemistry = CHEMISTRY_LI_ION;
            result.chemistryType = "li_ion";
            result.confidence = Math.max(result.confidence, 80);
        } else if (lower.contains("nimh") || lower.contains("ni-mh")) {
            result.chemistry = CHEMISTRY_NIMH;
            result.chemistryType = "nimh";
            result.confidence = Math.max(result.confidence, 75);
        }
    }

    private static void identifyByTechnology(String technology, ChemistryResult result) {
        String lower = technology.toLowerCase(Locale.ROOT);

        if (lower.contains("li-poly") || lower.contains("lipo") || lower.contains("polymer")) {
            result.chemistry = CHEMISTRY_LIPO;
            result.chemistryType = "lipo";
            result.confidence = Math.max(result.confidence, 75);
        } else if (lower.contains("li-ion") || lower.contains("lion") || lower.contains("li ion")) {
            result.chemistry = CHEMISTRY_LI_ION;
            result.chemistryType = "li_ion";
            result.confidence = Math.max(result.confidence, 70);
        } else if (lower.contains("nimh") || lower.contains("ni-mh")) {
            result.chemistry = CHEMISTRY_NIMH;
            result.chemistryType = "nimh";
            result.confidence = Math.max(result.confidence, 70);
        }
    }

    private static void identifyByVoltage(float voltage, ChemistryResult result) {
        if (voltage >= 3.0f && voltage <= 3.4f) {
            result.chemistry = CHEMISTRY_LIFEPO4;
            result.chemistryType = "lifepo4";
            result.confidence = Math.max(result.confidence, 50);
        } else if (voltage >= 3.5f && voltage <= 4.4f) {
            if (result.chemistryType.equals("unknown")) {
                result.chemistry = CHEMISTRY_LI_ION;
                result.chemistryType = "li_ion";
                result.confidence = Math.max(result.confidence, 40);
            }
        }
    }

    private static String generateDescription(ChemistryResult result) {
        switch (result.chemistryType) {
            case "li_ion":
                return "锂离子电池，能量密度高，是目前智能手机最常用的电池类型。最佳工作温度 0-40°C，建议在 20%-80% 电量区间使用以延长寿命。";
            case "lipo":
                return "锂聚合物电池，采用凝胶状电解质，可做成更薄的形状。常见于高端手机和平板，特性与锂离子电池类似。";
            case "lifepo4":
                return "磷酸铁锂电池，循环寿命更长，安全性更好，但能量密度较低。常见于储能和部分电动工具。";
            case "nimh":
                return "镍氢电池，记忆效应明显，能量密度低。目前已很少在智能手机中使用。";
            default:
                return "无法确定电池化学类型，多数智能手机使用锂离子或锂聚合物电池。";
        }
    }
}
