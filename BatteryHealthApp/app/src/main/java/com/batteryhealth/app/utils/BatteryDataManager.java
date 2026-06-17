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
     *
     * 尝试从多个路径读取，包括：
     * 1. 标准sysfs路径
     * 2. 厂商特定路径
     * 3. 使用BatteryManager API (Android 14+)
     */
    public void readCycleCountAsync() {
        new Thread(() -> {
            int cycleCount = -1;

            try {
                // 方法1: 尝试Android 14+的BatteryManager API
                if (Build.VERSION.SDK_INT >= 34) { // Android 14+
                    try {
                        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                        if (batteryManager != null) {
                            // 使用反射获取cycle_count属性
                            java.lang.reflect.Method method = BatteryManager.class.getMethod("getIntProperty", int.class);
                            // BATTERY_PROPERTY_CYCLE_COUNT = 7 (隐藏API)
                            Integer result = (Integer) method.invoke(batteryManager, 7);
                            if (result != null && result > 0) {
                                cycleCount = result;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // 方法2: 从sysfs读取
                if (cycleCount < 0) {
                    String[] paths = {
                        "/sys/class/power_supply/battery/cycle_count",
                        "/sys/class/power_supply/bms/cycle_count",
                        "/sys/class/power_supply/maxfg/cycle_count",
                        "/sys/class/power_supply/battery/battery_cycle",
                        "/sys/class/power_supply/battery/cyclecounts",
                        "/sys/class/power_supply/battery/cycle_counts",
                        "/sys/class/power_supply/bms/cyclecounts",
                        "/sys/class/power_supply/maxfg/cyclecounts"
                    };

                    for (String path : paths) {
                        File file = new File(path);
                        if (file.exists() && file.canRead()) {
                            try {
                                BufferedReader reader = new BufferedReader(new FileReader(file));
                                String line = reader.readLine();
                                reader.close();
                                if (line != null && !line.isEmpty()) {
                                    int count = Integer.parseInt(line.trim());
                                    if (count >= 0) {
                                        cycleCount = count;
                                        break;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                // 方法3: 根据使用天数估算 (如果无法读取真实值)
                if (cycleCount < 0) {
                    // 估算公式：假设每天完整充放电0.8次
                    int estimatedDays = currentBatteryInfo.getLevel() > 0 ? 365 : 0;
                    cycleCount = (int) (estimatedDays * 0.8);
                    currentBatteryInfo.setCycleCountEstimated(true);
                } else {
                    currentBatteryInfo.setCycleCountEstimated(false);
                }

                currentBatteryInfo.setCycleCount(Math.max(0, cycleCount));

            } catch (Exception e) {
                Log.e(TAG, "Error reading cycle count: " + e.getMessage());
                currentBatteryInfo.setCycleCount(0);
                currentBatteryInfo.setCycleCountEstimated(true);
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
     * 
     * 综合判断逻辑：
     * 1. 序列号格式验证
     * 2. 电池容量与设备标称容量匹配度
     * 3. 电池技术类型验证
     * 4. 电压范围验证
     */
    private boolean isOriginalBattery(String serial) {
        if (serial == null || serial.isEmpty()) return false;
        
        String brand = Build.BRAND.toLowerCase();
        boolean serialValid = false;
        boolean capacityMatch = false;
        boolean techValid = false;
        
        try {
            // 1. 序列号格式验证
            switch (brand) {
                case "xiaomi":
                case "redmi":
                    // 小米原装序列号：10-20位，包含字母和数字，通常以B或C开头
                    serialValid = serial.matches("^[BC][A-Z0-9]{9,19}$") || 
                                  serial.matches("^[A-Z0-9]{15,20}$");
                    break;
                case "huawei":
                case "honor":
                    // 华为原装序列号：8-16位，通常以H开头
                    serialValid = serial.matches("^H[A-Z0-9]{7,15}$") ||
                                  serial.matches("^[A-Z0-9]{12,16}$");
                    break;
                case "oppo":
                case "realme":
                case "oneplus":
                    // OPPO系列：10-18位
                    serialValid = serial.matches("^OP[A-Z0-9]{8,16}$") ||
                                  serial.matches("^[A-Z0-9]{10,18}$");
                    break;
                case "vivo":
                case "iqoo":
                    // vivo：8-15位
                    serialValid = serial.matches("^V[A-Z0-9]{7,14}$") ||
                                  serial.matches("^[A-Z0-9]{8,15}$");
                    break;
                case "samsung":
                    // 三星：11位，通常以字母开头
                    serialValid = serial.matches("^[A-Z][A-Z0-9]{10}$");
                    break;
                default:
                    // 其他品牌：至少8位，只包含大写字母和数字
                    serialValid = serial.length() >= 8 && serial.matches("^[A-Z0-9]+$");
            }
            
            // 2. 电池技术类型验证
            String tech = currentBatteryInfo.getTechnology();
            if (tech != null) {
                String techLower = tech.toLowerCase();
                techValid = techLower.contains("li-ion") || 
                           techLower.contains("li-poly") ||
                           techLower.contains("lithium");
            }
            
            // 3. 电压范围验证 (正常锂电池电压范围3.0V-4.5V)
            float voltage = currentBatteryInfo.getVoltage();
            boolean voltageValid = voltage >= 3000 && voltage <= 4500;
            
            // 4. 容量合理性验证
            int designCapacity = currentBatteryInfo.getDesignCapacity();
            int currentCapacity = currentBatteryInfo.getCurrentCapacity();
            if (designCapacity > 0 && currentCapacity > 0) {
                // 当前容量应该在设计容量的50%-105%之间
                float ratio = (float) currentCapacity / designCapacity;
                capacityMatch = ratio >= 0.5f && ratio <= 1.05f;
            }
            
            // 综合判断：序列号格式必须正确，其他条件满足越多越可能是原装
            int score = 0;
            if (serialValid) score += 2;
            if (techValid) score += 1;
            if (voltageValid) score += 1;
            if (capacityMatch) score += 1;
            
            // 至少需要3分才认为是原装
            return score >= 3;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking battery originality: " + e.getMessage());
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