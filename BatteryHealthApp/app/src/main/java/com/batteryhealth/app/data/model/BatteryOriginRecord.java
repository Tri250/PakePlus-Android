package com.batteryhealth.app.data.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

/**
 * 电池来源检测记录，用于持久化存储每次检测的结果。
 */
@Entity(tableName = "battery_origin_record", indices = {
    @Index(value = "timestamp")
})
public class BatteryOriginRecord {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "is_original")
    public boolean isOriginal;

    @ColumnInfo(name = "confidence")
    public int confidence;

    @ColumnInfo(name = "conclusion")
    public String conclusion;

    @ColumnInfo(name = "manufacturer")
    public String manufacturer;

    @ColumnInfo(name = "manufacture_date")
    public String manufactureDate;

    @ColumnInfo(name = "serial_number")
    public String serialNumber;

    @ColumnInfo(name = "oem_info")
    public String oemInfo;

    @ColumnInfo(name = "technology")
    public String technology;

    @ColumnInfo(name = "health_status")
    public String healthStatus;

    @ColumnInfo(name = "cycle_count")
    public String cycleCount;

    @ColumnInfo(name = "design_capacity")
    public int designCapacity;

    @ColumnInfo(name = "current_capacity")
    public int currentCapacity;

    @ColumnInfo(name = "battery_info_raw")
    public String batteryInfoRaw;

    @ColumnInfo(name = "device_brand")
    public String deviceBrand;

    @ColumnInfo(name = "device_model")
    public String deviceModel;

    @ColumnInfo(name = "detection_methods_json")
    public String detectionMethodsJson;

    @ColumnInfo(name = "source_tag")
    public String sourceTag;

    @ColumnInfo(name = "chemistry_type")
    public String chemistryType;

    @ColumnInfo(name = "chemistry_name")
    public String chemistryName;

    @ColumnInfo(name = "serial_format_valid")
    public boolean serialFormatValid;

    @ColumnInfo(name = "manufacturer_from_serial")
    public String manufacturerFromSerial;

    @ColumnInfo(name = "production_date_precise")
    public String productionDatePrecise;

    @ColumnInfo(name = "confidence_breakdown_json")
    public String confidenceBreakdownJson;

    @ColumnInfo(name = "detection_failed")
    public boolean detectionFailed;

    @ColumnInfo(name = "failure_reason")
    public String failureReason;

    @ColumnInfo(name = "failure_guide")
    public String failureGuide;
}
