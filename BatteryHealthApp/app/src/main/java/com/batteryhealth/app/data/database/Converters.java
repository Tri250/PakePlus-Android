package com.batteryhealth.app.data.database;

import androidx.room.TypeConverter;

import com.batteryhealth.app.data.model.AppUsageInfo;
import com.batteryhealth.app.data.model.BatteryHealthReport;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Room数据库类型转换器
 */
public class Converters {

    private static final Gson GSON = new Gson();

    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }

    @TypeConverter
    public static String appUsageListToString(List<AppUsageInfo> list) {
        return list == null ? null : GSON.toJson(list);
    }

    @TypeConverter
    public static List<AppUsageInfo> stringToAppUsageList(String value) {
        if (value == null) return null;
        Type type = new TypeToken<List<AppUsageInfo>>() {}.getType();
        return GSON.fromJson(value, type);
    }

    @TypeConverter
    public static String recommendationListToString(List<BatteryHealthReport.Recommendation> list) {
        return list == null ? null : GSON.toJson(list);
    }

    @TypeConverter
    public static List<BatteryHealthReport.Recommendation> stringToRecommendationList(String value) {
        if (value == null) return null;
        Type type = new TypeToken<List<BatteryHealthReport.Recommendation>>() {}.getType();
        return GSON.fromJson(value, type);
    }

    @TypeConverter
    public static String appConsumptionListToString(List<BatteryHealthReport.AppConsumption> list) {
        return list == null ? null : GSON.toJson(list);
    }

    @TypeConverter
    public static List<BatteryHealthReport.AppConsumption> stringToAppConsumptionList(String value) {
        if (value == null) return null;
        Type type = new TypeToken<List<BatteryHealthReport.AppConsumption>>() {}.getType();
        return GSON.fromJson(value, type);
    }

    @TypeConverter
    public static String stringMapToString(Map<String, String> map) {
        return map == null ? null : GSON.toJson(map);
    }

    @TypeConverter
    public static Map<String, String> stringToStringMap(String value) {
        if (value == null) return null;
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        return GSON.fromJson(value, type);
    }

    @TypeConverter
    public static String stringArrayToString(String[] array) {
        return array == null ? null : GSON.toJson(array);
    }

    @TypeConverter
    public static String[] stringToStringArray(String value) {
        if (value == null) return null;
        Type type = new TypeToken<String[]>() {}.getType();
        return GSON.fromJson(value, type);
    }
}
