package com.batteryhealth.app.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 2024-2026年国内品牌在售机型数据库管理器。
 * 用于补充设备配置信息、电池设计容量、快充功率、处理器营销名称等。
 * 同时兼容 v2.1.16 batteryDatabase.js 转换的 models 列表（仅含品牌/型号/容量）。
 */
public class DeviceDatabaseManager {

    private static final String TAG = "DeviceDatabase";
    private static final String ASSET_FILE = "device_database.json";

    private static DeviceDatabaseManager instance;
    private volatile DeviceDatabase database;
    private final CountDownLatch loadLatch = new CountDownLatch(1);

    public static synchronized DeviceDatabaseManager getInstance(Context context) {
        if (instance == null) {
            instance = new DeviceDatabaseManager(context.getApplicationContext());
        }
        return instance;
    }

    private DeviceDatabaseManager(final Context context) {
        // 在后台线程异步加载，避免 Application.onCreate 阻塞主线程
        new Thread(() -> {
            try {
                loadDatabase(context);
            } finally {
                loadLatch.countDown();
            }
        }, "DeviceDbLoader").start();
    }

    private void loadDatabase(Context context) {
        try (InputStream is = context.getAssets().open(ASSET_FILE);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            String json = baos.toString(StandardCharsets.UTF_8.name());
            database = new Gson().fromJson(json, DeviceDatabase.class);
            if (database == null) {
                database = new DeviceDatabase();
            }
            if (database.devices == null) {
                database.devices = new ArrayList<>();
            }
            if (database.models == null) {
                database.models = new ArrayList<>();
            }
            if (database.brands == null) {
                database.brands = new ArrayList<>();
            }
            Log.i(TAG, "Loaded device database: devices=" + database.devices.size()
                    + ", models=" + database.models.size());
        } catch (Exception e) {
            Log.e(TAG, "Failed to load device database", e);
            database = new DeviceDatabase();
            database.devices = new ArrayList<>();
            database.models = new ArrayList<>();
            database.brands = new ArrayList<>();
        }
    }

    private void awaitLoaded() {
        try {
            loadLatch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 根据 Build.MODEL 或 Build.DEVICE 匹配机型。
     * 支持：精确 model 匹配、codename 匹配、market_name 匹配、品牌+型号关键词模糊匹配。
     */
    public DeviceEntry findDevice() {
        awaitLoaded();
        if (database == null || database.devices == null) {
            return null;
        }
        String rawModel = Build.MODEL != null ? Build.MODEL : "";
        String rawDevice = Build.DEVICE != null ? Build.DEVICE : "";
        String rawBrand = Build.BRAND != null ? Build.BRAND : "";

        String modelNorm = normalizeModel(rawModel);
        String deviceNorm = normalizeModel(rawDevice);
        String brandNorm = normalizeBrand(rawBrand);

        // 1. 精确匹配 model（忽略大小写/空格）
        for (DeviceEntry entry : database.devices) {
            if (entry.model != null && normalizeModel(entry.model).equals(modelNorm)) {
                return entry;
            }
        }

        // 2. 精确匹配 marketing name（中文 model 常见于国产 ROM）
        for (DeviceEntry entry : database.devices) {
            if (entry.marketName != null && normalizeModel(entry.marketName).equals(modelNorm)) {
                return entry;
            }
        }

        // 3. 匹配 codename/device
        for (DeviceEntry entry : database.devices) {
            if (entry.codename != null && !entry.codename.equalsIgnoreCase("unknown")) {
                String codeNorm = entry.codename.toLowerCase(Locale.ROOT);
                if (codeNorm.equals(deviceNorm) || deviceNorm.contains(codeNorm)) {
                    return entry;
                }
            }
        }

        // 4. 模糊匹配：品牌 + 型号关键词
        for (DeviceEntry entry : database.devices) {
            if (entry.brand != null) {
                String entryBrandNorm = normalizeBrand(entry.brand);
                if (brandNorm.contains(entryBrandNorm) || entryBrandNorm.contains(brandNorm)) {
                    if (entry.model != null) {
                        String keyword = extractModelKeyword(entry.model);
                        if (!keyword.isEmpty() && modelNorm.contains(keyword)) {
                            return entry;
                        }
                    }
                    if (entry.marketName != null) {
                        String keyword = extractModelKeyword(entry.marketName);
                        if (!keyword.isEmpty() && modelNorm.contains(keyword)) {
                            return entry;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 在 v2.1.16 转换的 models 列表中按品牌+型号查找容量。
     * 用于完整 device 条目未覆盖时的兜底查询。
     */
    public ModelEntry findModel(String brand, String model) {
        awaitLoaded();
        if (database == null || database.models == null || brand == null || model == null) {
            return null;
        }
        String brandNorm = normalizeBrand(brand);
        String modelNorm = normalizeModel(model);
        for (ModelEntry entry : database.models) {
            if (entry.brand == null || entry.model == null) continue;
            if (normalizeBrand(entry.brand).equals(brandNorm)
                    && normalizeModel(entry.model).equals(modelNorm)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 按型号关键词在所有 models 中模糊搜索，返回最多 10 条结果。
     */
    public List<ModelEntry> searchModels(String keyword) {
        awaitLoaded();
        List<ModelEntry> result = new ArrayList<>();
        if (database == null || database.models == null || keyword == null) {
            return result;
        }
        String lower = keyword.toLowerCase(Locale.ROOT).trim();
        if (lower.isEmpty()) return result;
        for (ModelEntry entry : database.models) {
            if (entry.brand == null || entry.model == null) continue;
            String brandLower = entry.brand.toLowerCase(Locale.ROOT);
            String modelLower = entry.model.toLowerCase(Locale.ROOT);
            String full = brandLower + " " + modelLower;
            if (brandLower.contains(lower) || modelLower.contains(lower) || full.contains(lower)) {
                result.add(entry);
                if (result.size() >= 10) break;
            }
        }
        return result;
    }

    private String normalizeModel(String model) {
        if (model == null || model.trim().isEmpty()) return "";
        return model.toLowerCase(Locale.ROOT).replaceAll("[\\s-_]+", "").trim();
    }

    private String normalizeBrand(String brand) {
        if (brand == null || brand.trim().isEmpty()) return "";
        String lower = brand.toLowerCase(Locale.ROOT).trim();
        switch (lower) {
            case "荣耀":
            case "honor":
                return "honor";
            case "小米":
            case "xiaomi":
                return "xiaomi";
            case "红米":
            case "redmi":
                return "redmi";
            case "oppo":
                return "oppo";
            case "一加":
            case "oneplus":
                return "oneplus";
            case "真我":
            case "realme":
                return "realme";
            case "vivo":
                return "vivo";
            case "iqoo":
            case "iQOO":
                return "iqoo";
            case "努比亚":
            case "nubia":
                return "nubia";
            case "红魔":
            case "redmagic":
                return "redmagic";
            case "华为":
            case "huawei":
                return "huawei";
            case "魅族":
            case "meizu":
                return "meizu";
            case "中兴":
            case "zte":
                return "zte";
            case "三星":
            case "samsung":
                return "samsung";
            case "联想":
            case "lenovo":
                return "lenovo";
            case "摩托罗拉":
            case "motorola":
                return "motorola";
            case "苹果":
            case "apple":
                return "apple";
            default:
                return lower;
        }
    }

    private String extractModelKeyword(String modelOrMarketName) {
        if (modelOrMarketName == null) return "";
        String norm = normalizeModel(modelOrMarketName);
        // 去掉品牌前缀，只保留型号关键词
        for (String brand : new String[]{"xiaomi", "redmi", "oppo", "oneplus", "realme",
                "vivo", "iqoo", "honor", "nubia", "redmagic", "huawei", "meizu", "zte",
                "samsung", "lenovo", "motorola", "apple",
                "小米", "红米", "一加", "真我", "荣耀", "努比亚", "红魔", "华为", "魅族",
                "中兴", "三星", "联想", "摩托罗拉", "苹果"}) {
            String brandNorm = normalizeModel(brand);
            if (norm.startsWith(brandNorm)) {
                norm = norm.substring(brandNorm.length());
                break;
            }
        }
        return norm;
    }

    /**
     * 获取设计容量，用于校准健康度计算。
     * 优先匹配完整 device 条目，其次查询 v2.1.16 models 列表。
     */
    public int getDesignCapacity() {
        DeviceEntry entry = findDevice();
        if (entry != null && entry.batteryMah > 0) {
            return entry.batteryMah;
        }
        ModelEntry model = findModel(Build.BRAND, Build.MODEL);
        return model != null ? model.capacity : 0;
    }

    /**
     * 根据品牌和型号获取设计容量（mAh），供 Bugreport 解析等场景使用。
     */
    public int getCapacity(String brand, String model) {
        if (brand == null || model == null) return 0;
        ModelEntry modelEntry = findModel(brand, model);
        if (modelEntry != null) return modelEntry.capacity;
        // 兜底：在完整 devices 列表中按品牌+型号匹配
        awaitLoaded();
        if (database == null || database.devices == null) return 0;
        String brandNorm = normalizeBrand(brand);
        String modelNorm = normalizeModel(model);
        for (DeviceEntry entry : database.devices) {
            if (entry.brand != null && normalizeBrand(entry.brand).equals(brandNorm)
                    && entry.model != null && normalizeModel(entry.model).equals(modelNorm)) {
                return entry.batteryMah;
            }
        }
        return 0;
    }

    /**
     * 获取营销名称。
     */
    public String getMarketName() {
        DeviceEntry entry = findDevice();
        return entry != null && entry.marketName != null ? entry.marketName : Build.MODEL;
    }

    /**
     * 获取处理器营销名称。
     */
    public String getProcessorName() {
        DeviceEntry entry = findDevice();
        return entry != null && entry.processor != null ? entry.processor : Build.HARDWARE;
    }

    /**
     * 获取官方典型快充功率（W）。
     */
    public int getTypicalChargePower() {
        DeviceEntry entry = findDevice();
        return entry != null ? entry.typicalChargeW : 0;
    }

    /**
     * 是否支持无线充电。
     */
    public boolean isWirelessChargingSupported() {
        DeviceEntry entry = findDevice();
        return entry != null && entry.wirelessChargeW > 0;
    }

    /**
     * 获取数据库版本。
     */
    public String getDatabaseVersion() {
        awaitLoaded();
        return database != null && database.version != null ? database.version : "unknown";
    }

    public List<DeviceEntry> getAllDevices() {
        awaitLoaded();
        return database != null && database.devices != null ? database.devices : new ArrayList<>();
    }

    public List<ModelEntry> getAllModels() {
        awaitLoaded();
        return database != null && database.models != null ? database.models : new ArrayList<>();
    }

    /**
     * 获取所有品牌列表（保持 models 中的出现顺序）。
     */
    public List<String> getBrands() {
        awaitLoaded();
        List<String> result = new ArrayList<>();
        if (database == null || database.models == null) return result;
        Set<String> seen = new LinkedHashSet<>();
        for (ModelEntry entry : database.models) {
            if (entry.brand != null) {
                seen.add(entry.brand);
            }
        }
        result.addAll(seen);
        return result;
    }

    /**
     * 获取指定品牌的所有型号。
     */
    public List<String> getModelsByBrand(String brand) {
        awaitLoaded();
        List<String> result = new ArrayList<>();
        if (database == null || database.models == null || brand == null) return result;
        String brandNorm = normalizeBrand(brand);
        for (ModelEntry entry : database.models) {
            if (entry.brand != null && normalizeBrand(entry.brand).equals(brandNorm)
                    && entry.model != null) {
                result.add(entry.model);
            }
        }
        return result;
    }

    /**
     * 获取数据库统计信息。
     */
    public Map<String, Object> getStats() {
        awaitLoaded();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalModels", database != null && database.models != null ? database.models.size() : 0);
        stats.put("totalDevices", database != null && database.devices != null ? database.devices.size() : 0);
        stats.put("brands", getBrands());
        stats.put("version", getDatabaseVersion());
        return stats;
    }

    public static class DeviceDatabase {
        public String version;
        public String description;
        public List<String> brands;
        @SerializedName("devices")
        public List<DeviceEntry> devices;
        @SerializedName("models")
        public List<ModelEntry> models;
    }

    public static class DeviceEntry {
        public String brand;
        public String model;
        public String codename;
        @SerializedName("market_name")
        public String marketName;
        @SerializedName("release_date")
        public String releaseDate;
        @SerializedName("battery_mah")
        public int batteryMah;
        @SerializedName("typical_charge_w")
        public int typicalChargeW;
        @SerializedName("wireless_charge_w")
        public int wirelessChargeW;
        public String processor;
        @SerializedName("ram_gb")
        public int ramGb;
        @SerializedName("storage_gb")
        public int storageGb;
        public String screen;
    }

    /**
     * v2.1.16 batteryDatabase.js 转换后的精简条目：品牌/型号/容量。
     */
    public static class ModelEntry {
        public String brand;
        public String model;
        public int capacity;
    }
}
