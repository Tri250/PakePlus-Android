package com.batteryhealth.app.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.batteryhealth.app.data.model.DeviceConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 电池型号数据库加载器
 * 
 * 从 assets/database/ 加载电池型号库和充电协议库，
 * 提供机型匹配、序列号校验、设计容量查询、衰减率查询等功能。
 * 
 * 覆盖：小米、OPPO、vivo、荣耀、努比亚 2024-2026 年主流机型
 */
public class BatteryModelDatabase {
    private static final String TAG = "BatteryModelDatabase";
    private static volatile BatteryModelDatabase instance;
    
    private JSONObject batteryDb;
    private JSONObject chargingDb;
    private boolean loaded = false;
    
    private BatteryModelDatabase() {}
    
    public static BatteryModelDatabase getInstance() {
        if (instance == null) {
            synchronized (BatteryModelDatabase.class) {
                if (instance == null) {
                    instance = new BatteryModelDatabase();
                }
            }
        }
        return instance;
    }
    
    /**
     * 初始化数据库（从 assets 加载 JSON 文件）
     */
    public synchronized void init(Context context) {
        if (loaded) return;
        try {
            // 加载电池型号库
            batteryDb = loadJson(context, "database/battery_models.json");
            // 加载充电协议库
            chargingDb = loadJson(context, "database/charging_protocols.json");
            loaded = true;
            Log.i(TAG, "Battery model database loaded successfully, version=" 
                + (batteryDb != null ? batteryDb.optString("version", "unknown") : "none"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to load battery model database: " + e.getMessage());
            loaded = false;
        }
    }
    
    public boolean isLoaded() {
        return loaded;
    }
    
    private JSONObject loadJson(Context context, String assetPath) {
        try {
            InputStream is = context.getAssets().open(assetPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            Log.w(TAG, "Cannot load asset: " + assetPath + " - " + e.getMessage());
            return null;
        }
    }
    
    // ==================== 品牌识别 ====================
    
    /**
     * 获取品牌标识（标准化小写）
     */
    public String getBrandKey() {
        if (Build.BRAND == null) return "unknown";
        String brand = Build.BRAND.toLowerCase();
        if (brand.contains("xiaomi") || brand.contains("redmi")) return "xiaomi";
        if (brand.contains("oppo")) return "oppo";
        if (brand.contains("realme")) return "oppo";
        if (brand.contains("oneplus")) return "oppo";
        if (brand.contains("vivo")) return "vivo";
        if (brand.contains("iqoo")) return "vivo";
        if (brand.contains("honor")) return "honor";
        if (brand.contains("nubia")) return "nubia";
        if (brand.contains("redmagic")) return "nubia";
        return "other";
    }
    
    // ==================== 机型匹配 ====================
    
    /**
     * 根据型号匹配电池配置
     * @return JSONObject 或 null
     */
    public JSONObject findBatteryConfig() {
        if (!loaded || batteryDb == null) return null;
        try {
            String brandKey = getBrandKey();
            if ("other".equals(brandKey)) return null;
            
            JSONObject brandObj = batteryDb.optJSONObject("brands");
            if (brandObj == null) return null;
            
            JSONObject brandData = brandObj.optJSONObject(brandKey);
            if (brandData == null) return null;
            
            JSONObject models = brandData.optJSONObject("models");
            if (models == null) return null;
            
            String deviceModel = Build.MODEL;
            if (deviceModel == null) return null;
            
            // 遍历所有机型，匹配 model_series
            JSONArray modelNames = models.names();
            if (modelNames == null) return null;
            
            for (int i = 0; i < modelNames.length(); i++) {
                String modelName = modelNames.getString(i);
                JSONObject modelObj = models.optJSONObject(modelName);
                if (modelObj == null) continue;
                
                JSONArray series = modelObj.optJSONArray("model_series");
                if (series == null) continue;
                
                for (int j = 0; j < series.length(); j++) {
                    String s = series.getString(j);
                    if (deviceModel.equals(s) || deviceModel.contains(s) || s.contains(deviceModel)) {
                        JSONObject battery = modelObj.optJSONObject("battery");
                        if (battery != null) {
                            try { battery.put("_model_name", modelName); } catch (Exception ignored) {}
                            Log.d(TAG, "Matched battery config: " + modelName + " for " + deviceModel);
                            return battery;
                        }
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error finding battery config: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取品牌默认电池参数
     */
    public JSONObject getDefaultBatteryConfig() {
        if (!loaded || batteryDb == null) return null;
        try {
            String brandKey = getBrandKey();
            if ("other".equals(brandKey)) return null;
            
            JSONObject brandObj = batteryDb.optJSONObject("brands");
            if (brandObj == null) return null;
            
            JSONObject brandData = brandObj.optJSONObject(brandKey);
            if (brandData == null) return null;
            
            return brandData.optJSONObject("default_battery");
        } catch (Exception e) {
            return null;
        }
    }
    
    // ==================== 序列号校验 ====================
    
    /**
     * 校验电池序列号是否符合该品牌的格式
     * @return 0.0 - 1.0 的置信度分数
     */
    public float scoreSerialFormat(String serialNumber) {
        if (!loaded || batteryDb == null || serialNumber == null || serialNumber.isEmpty()) {
            return 0.0f;
        }
        try {
            String brandKey = getBrandKey();
            if ("other".equals(brandKey)) return 0.0f;
            
            JSONObject brandObj = batteryDb.optJSONObject("brands");
            if (brandObj == null) return 0.0f;
            
            JSONObject brandData = brandObj.optJSONObject(brandKey);
            if (brandData == null) return 0.0f;
            
            JSONArray formats = brandData.optJSONArray("serial_formats");
            if (formats == null) return 0.0f;
            
            float maxScore = 0.0f;
            for (int i = 0; i < formats.length(); i++) {
                String format = formats.getString(i);
                try {
                    if (Pattern.matches(format, serialNumber)) {
                        return 0.85f;
                    }
                    // 部分匹配
                    if (serialNumber.length() >= 6) {
                        String prefix = serialNumber.substring(0, Math.min(6, format.length()));
                        String formatPrefix = format.substring(0, Math.min(6, format.length()));
                        if (prefix.equalsIgnoreCase(formatPrefix) || 
                            Pattern.matches(formatPrefix.replace("\\d", "[0-9]").replace("\\w", "[A-Za-z0-9]"), prefix)) {
                            maxScore = Math.max(maxScore, 0.5f);
                        }
                    }
                } catch (Exception ignored) {}
            }
            return maxScore;
        } catch (Exception e) {
            return 0.0f;
        }
    }
    
    // ==================== 设计容量查询 ====================
    
    /**
     * 获取机型的设计容量（mAh），优先精确匹配，否则返回品牌默认值
     */
    public int getDesignCapacityMah() {
        JSONObject config = findBatteryConfig();
        if (config != null && config.has("design_capacity_mah")) {
            return config.optInt("design_capacity_mah", -1);
        }
        JSONObject defaultConfig = getDefaultBatteryConfig();
        if (defaultConfig != null) {
            return defaultConfig.optInt("design_capacity_mah", -1);
        }
        return -1;
    }
    
    /**
     * 获取机型技术类型
     */
    public String getBatteryTechnology() {
        JSONObject config = findBatteryConfig();
        if (config != null && config.has("technology")) {
            return config.optString("technology", "");
        }
        return "";
    }
    
    /**
     * 获取机型典型电压（mV）
     */
    public int getTypicalVoltageMv() {
        JSONObject config = findBatteryConfig();
        if (config != null && config.has("typical_voltage_mv")) {
            return config.optInt("typical_voltage_mv", 3850);
        }
        return 3850;
    }
    
    // ==================== 衰减率查询 ====================
    
    /**
     * 获取机型的年衰减率（百分比/年）
     */
    public float getDegradationPerYearPct() {
        JSONObject config = findBatteryConfig();
        if (config != null && config.has("degradation_per_year_pct")) {
            return (float) config.optDouble("degradation_per_year_pct", 2.5);
        }
        JSONObject defaultConfig = getDefaultBatteryConfig();
        if (defaultConfig != null) {
            return (float) defaultConfig.optDouble("degradation_per_year_pct", 2.5);
        }
        return 2.5f;
    }
    
    /**
     * 获取机型的循环寿命（次）
     */
    public int getCycleLife() {
        JSONObject config = findBatteryConfig();
        if (config != null && config.has("cycle_life")) {
            return config.optInt("cycle_life", 800);
        }
        JSONObject defaultConfig = getDefaultBatteryConfig();
        if (defaultConfig != null) {
            return defaultConfig.optInt("cycle_life", 800);
        }
        return 800;
    }
    
    /**
     * 获取机型制造商
     */
    public String getManufacturer() {
        JSONObject config = findBatteryConfig();
        if (config != null && config.has("manufacturer")) {
            return config.optString("manufacturer", "");
        }
        return "";
    }
    
    /**
     * 获取机型名称
     */
    public String getModelName() {
        JSONObject config = findBatteryConfig();
        if (config != null) {
            return config.optString("_model_name", "");
        }
        return "";
    }
    
    // ==================== 充电协议匹配 ====================
    
    /**
     * 根据功率和品牌匹配充电协议
     * @return 协议名称
     */
    public String matchChargingProtocol(float powerW) {
        if (!loaded || chargingDb == null) return null;
        try {
            String brandKey = getBrandKey();
            String matchedProtocol = null;
            int matchedPriority = Integer.MAX_VALUE;
            
            JSONObject protocols = chargingDb.optJSONObject("protocols");
            if (protocols == null) return null;
            
            JSONArray protoNames = protocols.names();
            if (protoNames == null) return null;
            
            for (int i = 0; i < protoNames.length(); i++) {
                String key = protoNames.getString(i);
                JSONObject proto = protocols.optJSONObject(key);
                if (proto == null) continue;
                
                double threshold = proto.optDouble("power_threshold_w", 0);
                if (powerW < threshold) continue;
                
                JSONArray brands = proto.optJSONArray("brands");
                boolean brandMatch = false;
                if (brands != null) {
                    for (int j = 0; j < brands.length(); j++) {
                        String b = brands.getString(j);
                        if ("*".equals(b) || brandKey.equals(b)) {
                            brandMatch = true;
                            break;
                        }
                    }
                }
                
                int priority = brandMatch ? 0 : 1;
                if (priority < matchedPriority) {
                    matchedProtocol = proto.optString("name", null);
                    matchedPriority = priority;
                }
            }
            
            return matchedProtocol;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取充电类型阈值配置
     */
    public JSONObject getChargeTypeThresholds() {
        if (chargingDb == null) return null;
        return chargingDb.optJSONObject("charge_type_thresholds");
    }
    
    /**
     * 获取充电阶段配置
     */
    public JSONObject getChargingPhases() {
        if (chargingDb == null) return null;
        return chargingDb.optJSONObject("charging_phases");
    }
    
    /**
     * 使用衰减模型计算健康度
     * formula: health = 100 - (years * degradation_per_year) - (cycles / cycle_life * 100 * cycle_weight)
     */
    public float calculateCalibratedHealth(float usageYears, int cycleCount) {
        float degradePerYear = getDegradationPerYearPct();
        int cycleLife = getCycleLife();
        float cycleWeight = 0.3f;
        float timeWeight = 0.7f;
        
        if (batteryDb != null) {
            JSONObject model = batteryDb.optJSONObject("degradation_model");
            if (model != null) {
                JSONObject params = model.optJSONObject("parameters");
                if (params != null) {
                    cycleWeight = (float) params.optDouble("cycle_weight", 0.3);
                    timeWeight = (float) params.optDouble("time_weight", 0.7);
                }
            }
        }
        
        float timeDegradation = usageYears * degradePerYear * timeWeight;
        float cycleDegradation = (cycleCount / (float) cycleLife) * 100 * cycleWeight;
        float health = 100 - timeDegradation - cycleDegradation;
        
        return Math.max(0, Math.min(100, health));
    }
}