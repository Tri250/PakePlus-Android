package com.batteryhealth.app.utils;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

import com.batteryhealth.app.data.model.BatteryInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * 电池数据管理器
 * 
 * 功能：
 * 1. 获取实时电池信息
 * 2. 计算电池健康度
 * 3. 读取电池循环次数
 * 4. 判断电池来源
 */
public class BatteryDataManager {
    
    private static final String TAG = "BatteryDataManager";
    private Context context;
    private BatteryInfo currentBatteryInfo;
    
    public BatteryDataManager(Context context) {
        this.context = context.getApplicationContext();
        this.currentBatteryInfo = new BatteryInfo();
        // 设置默认值，防止空指针
        currentBatteryInfo.setLevel(0);
        currentBatteryInfo.setTemperature(25.0f);
        currentBatteryInfo.setVoltage(3700);
        currentBatteryInfo.setTechnology("Li-ion");
        currentBatteryInfo.setHealthPercentage(100.0f);
        currentBatteryInfo.setHealthStatus("good");
        currentBatteryInfo.setBatterySource("unknown");
        
        loadBatteryInfo();
    }
    
    /**
     * 从Intent更新电池数据
     */
    public void updateFromIntent(Intent intent) {
        if (intent == null) return;
        
        try {
            // 电量百分比
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level != -1 && scale != -1 && scale > 0) {
                currentBatteryInfo.setLevel((int) ((level / (float) scale) * 100));
            }
            
            // 电池状态
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            currentBatteryInfo.setStatus(status);
            
            // 充电方式
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            currentBatteryInfo.setPlugged(plugged);
            
            // 电池温度
            int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            if (temperature != -1) {
                currentBatteryInfo.setTemperature(temperature / 10.0f);
            }
            
            // 电池电压
            int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            if (voltage != -1) {
                currentBatteryInfo.setVoltage(voltage);
            }
            
            // 电池技术
            String technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
            if (technology != null) {
                currentBatteryInfo.setTechnology(technology);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating from intent: " + e.getMessage());
        }
    }
    
    /**
     * 加载电池信息
     */
    private void loadBatteryInfo() {
        try {
            // 从系统读取电池信息
            BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (batteryManager != null) {
                int level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (level >= 0) {
                    currentBatteryInfo.setLevel(level);
                }
                
                int currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                currentBatteryInfo.setCurrentNow(currentNow);
                
                // 尝试获取容量信息
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    long chargeCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                    if (chargeCounter != Long.MIN_VALUE && chargeCounter > 0) {
                        currentBatteryInfo.setChargeCounter((int) chargeCounter);
                        currentBatteryInfo.setCurrentCapacity((int) (chargeCounter / 1000));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading battery info: " + e.getMessage());
        }
    }
    
    /**
     * 读取充电循环次数 (异步)
     */
    public void readCycleCountAsync() {
        new Thread(() -> {
            try {
                String[] paths = {
                    "/sys/class/power_supply/battery/cycle_count",
                    "/sys/class/power_supply/bms/cycle_count",
                    "/sys/class/power_supply/maxfg/cycle_count",
                    "/sys/class/power_supply/battery/battery_cycle"
                };
                
                for (String path : paths) {
                    File file = new File(path);
                    if (file.exists() && file.canRead()) {
                        BufferedReader reader = new BufferedReader(new FileReader(file));
                        String line = reader.readLine();
                        reader.close();
                        if (line != null && !line.isEmpty()) {
                            try {
                                int cycleCount = Integer.parseInt(line.trim());
                                currentBatteryInfo.setCycleCount(Math.max(0, cycleCount));
                                return;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading cycle count: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 读取电池容量 (异步)
     */
    public void readBatteryCapacityAsync() {
        new Thread(() -> {
            try {
                // 读取设计容量
                File fullFile = new File("/sys/class/power_supply/battery/charge_full");
                if (fullFile.exists() && fullFile.canRead()) {
                    BufferedReader reader = new BufferedReader(new FileReader(fullFile));
                    String line = reader.readLine();
                    reader.close();
                    if (line != null && !line.isEmpty()) {
                        try {
                            int chargeFull = Integer.parseInt(line.trim());
                            currentBatteryInfo.setDesignCapacity(Math.abs(chargeFull) / 1000);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                
                // 计算健康度
                calculateHealth();
            } catch (Exception e) {
                Log.e(TAG, "Error reading battery capacity: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 计算电池健康度
     */
    private void calculateHealth() {
        int designCapacity = currentBatteryInfo.getDesignCapacity();
        int currentCapacity = currentBatteryInfo.getCurrentCapacity();
        
        if (designCapacity > 0 && currentCapacity > 0) {
            float healthPercentage = Math.min(100.0f, (currentCapacity * 100.0f) / designCapacity);
            currentBatteryInfo.setHealthPercentage(healthPercentage);
            
            // 设置健康状态
            if (healthPercentage >= 90) {
                currentBatteryInfo.setHealthStatus("good");
            } else if (healthPercentage >= 80) {
                currentBatteryInfo.setHealthStatus("normal");
            } else if (healthPercentage >= 70) {
                currentBatteryInfo.setHealthStatus("warning");
            } else {
                currentBatteryInfo.setHealthStatus("poor");
            }
        } else {
            // 如果无法获取真实数据，使用默认值
            currentBatteryInfo.setHealthPercentage(100.0f);
            currentBatteryInfo.setHealthStatus("good");
        }
    }
    
    /**
     * 判断电池来源
     */
    public void detectBatterySourceAsync() {
        new Thread(() -> {
            try {
                File serialFile = new File("/sys/class/power_supply/battery/serial_number");
                if (serialFile.exists() && serialFile.canRead()) {
                    BufferedReader reader = new BufferedReader(new FileReader(serialFile));
                    String serial = reader.readLine();
                    reader.close();
                    if (serial != null && !serial.isEmpty()) {
                        currentBatteryInfo.setBatterySerial(serial);
                        
                        if (isOriginalBattery(serial)) {
                            currentBatteryInfo.setBatterySource("original");
                        } else {
                            currentBatteryInfo.setBatterySource("third_party");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error detecting battery source: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 判断是否为原装电池
     */
    private boolean isOriginalBattery(String serial) {
        if (serial == null || serial.isEmpty()) return false;
        
        String brand = Build.BRAND.toLowerCase();
        
        try {
            switch (brand) {
                case "xiaomi":
                case "redmi":
                    return serial.matches("^[A-Z0-9]{10,20}$");
                case "huawei":
                case "honor":
                    return serial.matches("^[A-Z0-9]{8,16}$");
                case "oppo":
                case "realme":
                case "oneplus":
                    return serial.matches("^[A-Z0-9]{10,18}$");
                case "vivo":
                case "iqoo":
                    return serial.matches("^[A-Z0-9]{8,15}$");
                default:
                    return serial.length() >= 8 && serial.matches("^[A-Z0-9]+$");
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取当前电池信息
     */
    public BatteryInfo getCurrentBatteryInfo() {
        return currentBatteryInfo;
    }
    
    /**
     * 获取充电状态描述
     */
    public String getChargingStatusText() {
        switch (currentBatteryInfo.getStatus()) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "充电中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "放电中";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "未充电";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "已充满";
            default:
                return "未知";
        }
    }
    
    /**
     * 获取充电方式描述
     */
    public String getPlugTypeText() {
        switch (currentBatteryInfo.getPlugged()) {
            case BatteryManager.BATTERY_PLUGGED_AC:
                return "交流电源";
            case BatteryManager.BATTERY_PLUGGED_USB:
                return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return "无线充电";
            default:
                return "未连接";
        }
    }
    
    /**
     * 获取电池来源描述
     */
    public String getBatterySourceText() {
        String source = currentBatteryInfo.getBatterySource();
        if ("original".equals(source)) {
            return "原装电池";
        } else if ("third_party".equals(source)) {
            return "第三方电池";
        } else {
            return "未知来源";
        }
    }
}