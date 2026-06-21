package com.batteryhealth.app.data.database;

import androidx.room.TypeConverter;

import java.util.Date;

/**
 * Room数据库类型转换器
 */
public class Converters {

    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }
}
