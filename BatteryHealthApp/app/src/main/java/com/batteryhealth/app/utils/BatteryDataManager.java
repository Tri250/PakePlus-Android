package com.batteryhealth.app.utils;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import com.batteryhealth.app.BatteryHealthApplication;
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
    
    private Context context;
    private BatteryInfo currentBatteryInfo;
    
    public BatteryDataManager(Context context) {
        this.context = context.getApplicationContext();
        this.currentBatteryInfo = new BatteryInfo();
        loadBatteryInfo();
    }
    
    /**
     * 从Intent更新电池数据
     */
    public void updateFromIntent(Intent intent) {
        if (intent == null) return;
        
        // 电量百分比
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level != -1 && scale != -1) {
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
        
        // 读取高级信息
        readAdvancedInfo();
    }
    
    /**
     * 加载电池信息
     */
    private void loadBatteryInfo() {
        // 从系统读取电池信息
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null) {
            int level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            currentBatteryInfo.setLevel(level);
            
            int currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            currentBatteryInfo.setCurrentNow(currentNow);
        }
        
        readAdvancedInfo();
    }
    
    /**
     * 读取高级电池信息
     */
    private void readAdvancedInfo() {
        readCycleCount();
        readBatteryCapacity();
        detectBatterySource();
    }
    
    /**
     * 读取充电循环次数
     */
    private void readCycleCount() {
        try {
            String[] paths = {
                "/sys/class/power_supply/battery/cycle_count",
                "/sys/class/power_supply/bms/cycle_count",
                "/sys/class/power_supply/maxfg/cycle_count",
                "/sys/class/power_supply/battery/battery_cycle"
            };
            
            for (String path : paths) {
                File file = new File(path);
                if (file.exists()) {
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    String line = reader.readLine();
                    reader.close();
                    if (line != null) {
                        int cycleCount = Integer.parseInt(line.trim());
                        currentBatteryInfo.setCycleCount(cycleCount);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            // 读取失败，使用估算值
            estimateCycleCount();
        }
    }
    
    /**
     * 估算充电循环次数
     */
    private void estimateCycleCount() {
        // 基于使用时间和充电模式估算
        int estimatedCycles = 0;
        
        // 读取charge_counter估算
        try {
            File counterFile = new File("/sys/class/power_supply/battery/charge_counter");
            if (counterFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(counterFile));
                String line = reader.readLine();
                reader.close();
                if (line != null) {
                    long counter = Long.parseLong(line.trim());
                    // 估算循环次数 (假设每次完整充电为一次循环)
                    estimatedCycles = (int) (counter / 1000000 / 100); // 粗略估算
                }
            }
        } catch (Exception ignored) {
        }
        
        currentBatteryInfo.setCycleCount(estimatedCycles);
    }
    
    /**
     * 读取电池容量
     */
    private void readBatteryCapacity() {
        try {
            // 读取当前容量
            File counterFile = new File("/sys/class/power_supply/battery/charge_counter");
            if (counterFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(counterFile));
                String line = reader.readLine();
                reader.close();
                if (line != null) {
                    int chargeCounter = Integer.parseInt(line.trim());
                    currentBatteryInfo.setChargeCounter(chargeCounter);
                    currentBatteryInfo.setCurrentCapacity(Math.abs(chargeCounter) / 1000);
                }
            }
            
            // 读取设计容量
            File fullFile = new File("/sys/class/power_supply/battery/charge_full");
            if (fullFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(fullFile));
                String line = reader.readLine();
                reader.close();
                if (line != null) {
                    int chargeFull = Integer.parseInt(line.trim());
                    currentBatteryInfo.setDesignCapacity(chargeFull / 1000);
                }
            }
            
            // 计算健康度
            calculateHealth();
            
        } catch (Exception e) {
            // 使用系统API获取
            getCapacityFromBatteryManager();
        }
    }
    
    /**
     * 从BatteryManager获取容量
     */
    private void getCapacityFromBatteryManager() {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null) {
            long chargeCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            long capacity = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            
            if (chargeCounter != Long.MIN_VALUE && capacity != Long.MIN_VALUE) {
                currentBatteryInfo.setChargeCounter((int) chargeCounter);
                currentBatteryInfo.setCurrentCapacity((int) (chargeCounter / 1000));
            }
        }
    }
    
    /**
     * 计算电池健康度
     */
    private void calculateHealth() {
        int designCapacity = currentBatteryInfo.getDesignCapacity();
        int currentCapacity = currentBatteryInfo.getCurrentCapacity();
        
        if (designCapacity > 0 && currentCapacity > 0) {
            float healthPercentage = (currentCapacity * 100.0f) / designCapacity;
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
        }
    }
    
    /**
     * 判断电池来源
     */
    private void detectBatterySource() {
        try {
            File serialFile = new File("/sys/class/power_supply/battery/serial_number");
            if (serialFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(serialFile));
                String serial = reader.readLine();
                reader.close();
                if (serial != null) {
                    currentBatteryInfo.setBatterySerial(serial);
                    
                    // 判断来源
                    if (isOriginalBattery(serial)) {
                        currentBatteryInfo.setBatterySource("original");
                    } else {
                        currentBatteryInfo.setBatterySource("third_party");
                    }
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        
        currentBatteryInfo.setBatterySource("unknown");
    }
    
    /**
     * 判断是否为原装电池
     */
    private boolean isOriginalBattery(String serial) {
        if (serial == null || serial.isEmpty()) return false;
        
        String brand = android.os.Build.BRAND.toLowerCase();
        
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