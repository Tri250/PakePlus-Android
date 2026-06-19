package com.batteryhealth.app.utils;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class BatteryTraceManager {
    private static final String TAG = "BatteryTraceManager";
    private final Context context;
    private String batterySerial;
    private String batteryManufacturer;
    private String batteryModelName;

    public enum TraceResult {
        ORIGINAL("原装电池", 0.9f),
        REPLACEMENT("更换电池", 0.7f),
        THIRD_PARTY("第三方电池", 0.5f),
        UNKNOWN("无法判断", 0.3f);

        public final String label;
        public final float confidence;
        TraceResult(String label, float confidence) { this.label = label; this.confidence = confidence; }
    }

    public BatteryTraceManager(Context context) { this.context = context; }

    public void collect() {
        batterySerial = readSysfsString("/sys/class/power_supply/battery/serial_number");
        if (batterySerial == null) batterySerial = readSysfsString("/sys/class/power_supply/bms/serial_number");
        batteryManufacturer = readSysfsString("/sys/class/power_supply/battery/device/manufacturer");
        batteryModelName = readSysfsString("/sys/class/power_supply/battery/model_name");
    }

    public TraceResult getResult() {
        collect();
        // 如果有序列号且与设备数据库匹配，判断为原装
        if (batterySerial != null && !batterySerial.isEmpty()) {
            // 检查序列号是否与设备品牌匹配
            String brand = android.os.Build.MANUFACTURER;
            if (batteryManufacturer != null && batteryManufacturer.toLowerCase().contains(brand.toLowerCase())) {
                return TraceResult.ORIGINAL;
            }
            // 电池制造商与设备品牌不同但非空
            if (batteryManufacturer != null && !batteryManufacturer.isEmpty()) {
                return TraceResult.THIRD_PARTY;
            }
            return TraceResult.ORIGINAL; // 有序列号，制造商未知，可能是原装
        }
        // 无序列号，检查制造商
        if (batteryManufacturer != null && !batteryManufacturer.isEmpty()) {
            String brand = android.os.Build.MANUFACTURER;
            if (batteryManufacturer.toLowerCase().contains(brand.toLowerCase())) {
                return TraceResult.ORIGINAL;
            }
            return TraceResult.THIRD_PARTY;
        }
        return TraceResult.UNKNOWN;
    }

    public String getSerial() { return batterySerial; }
    public String getManufacturer() { return batteryManufacturer; }
    public String getModelName() { return batteryModelName; }

    private String readSysfsString(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) return null;
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            reader.close();
            return (line != null && !line.trim().isEmpty()) ? line.trim() : null;
        } catch (Exception e) { return null; }
    }
}