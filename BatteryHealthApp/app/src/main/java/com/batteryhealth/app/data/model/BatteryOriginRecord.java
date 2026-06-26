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
    private long id;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @ColumnInfo(name = "is_original")
    private boolean isOriginal;

    @ColumnInfo(name = "confidence")
    private int confidence;

    @ColumnInfo(name = "conclusion")
    private String conclusion;

    @ColumnInfo(name = "manufacturer")
    private String manufacturer;

    @ColumnInfo(name = "manufacture_date")
    private String manufactureDate;

    @ColumnInfo(name = "serial_number")
    private String serialNumber;

    @ColumnInfo(name = "oem_info")
    private String oemInfo;

    @ColumnInfo(name = "technology")
    private String technology;

    @ColumnInfo(name = "health_status")
    private String healthStatus;

    @ColumnInfo(name = "cycle_count")
    private String cycleCount;

    @ColumnInfo(name = "design_capacity")
    private int designCapacity;

    @ColumnInfo(name = "current_capacity")
    private int currentCapacity;

    @ColumnInfo(name = "battery_info_raw")
    private String batteryInfoRaw;

    @ColumnInfo(name = "device_brand")
    private String deviceBrand;

    @ColumnInfo(name = "device_model")
    private String deviceModel;

    @ColumnInfo(name = "detection_methods_json")
    private String detectionMethodsJson;

    @ColumnInfo(name = "source_tag")
    private String sourceTag;

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isOriginal() { return isOriginal; }
    public void setOriginal(boolean original) { isOriginal = original; }

    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }

    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getManufactureDate() { return manufactureDate; }
    public void setManufactureDate(String manufactureDate) { this.manufactureDate = manufactureDate; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getOemInfo() { return oemInfo; }
    public void setOemInfo(String oemInfo) { this.oemInfo = oemInfo; }

    public String getTechnology() { return technology; }
    public void setTechnology(String technology) { this.technology = technology; }

    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }

    public String getCycleCount() { return cycleCount; }
    public void setCycleCount(String cycleCount) { this.cycleCount = cycleCount; }

    public int getDesignCapacity() { return designCapacity; }
    public void setDesignCapacity(int designCapacity) { this.designCapacity = designCapacity; }

    public int getCurrentCapacity() { return currentCapacity; }
    public void setCurrentCapacity(int currentCapacity) { this.currentCapacity = currentCapacity; }

    public String getBatteryInfoRaw() { return batteryInfoRaw; }
    public void setBatteryInfoRaw(String batteryInfoRaw) { this.batteryInfoRaw = batteryInfoRaw; }

    public String getDeviceBrand() { return deviceBrand; }
    public void setDeviceBrand(String deviceBrand) { this.deviceBrand = deviceBrand; }

    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }

    public String getDetectionMethodsJson() { return detectionMethodsJson; }
    public void setDetectionMethodsJson(String detectionMethodsJson) { this.detectionMethodsJson = detectionMethodsJson; }

    public String getSourceTag() { return sourceTag; }
    public void setSourceTag(String sourceTag) { this.sourceTag = sourceTag; }
}
