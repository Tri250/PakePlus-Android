package com.batteryhealth.app.data.model;

/**
 * 多维度健康雷达图数据
 * 5个维度：循环健康/温度健康/充电习惯健康/容量健康/电压健康
 */
public class HealthRadarData {

    private float cycleHealth;
    private float temperatureHealth;
    private float chargingHabitHealth;
    private float capacityHealth;
    private float voltageHealth;

    private String cycleHealthDesc;
    private String temperatureHealthDesc;
    private String chargingHabitHealthDesc;
    private String capacityHealthDesc;
    private String voltageHealthDesc;

    private float overallScore;
    private String overallGrade;

    public HealthRadarData() {
        this.cycleHealth = 0f;
        this.temperatureHealth = 0f;
        this.chargingHabitHealth = 0f;
        this.capacityHealth = 0f;
        this.voltageHealth = 0f;
    }

    public float getCycleHealth() {
        return cycleHealth;
    }

    public void setCycleHealth(float cycleHealth) {
        this.cycleHealth = cycleHealth;
    }

    public float getTemperatureHealth() {
        return temperatureHealth;
    }

    public void setTemperatureHealth(float temperatureHealth) {
        this.temperatureHealth = temperatureHealth;
    }

    public float getChargingHabitHealth() {
        return chargingHabitHealth;
    }

    public void setChargingHabitHealth(float chargingHabitHealth) {
        this.chargingHabitHealth = chargingHabitHealth;
    }

    public float getCapacityHealth() {
        return capacityHealth;
    }

    public void setCapacityHealth(float capacityHealth) {
        this.capacityHealth = capacityHealth;
    }

    public float getVoltageHealth() {
        return voltageHealth;
    }

    public void setVoltageHealth(float voltageHealth) {
        this.voltageHealth = voltageHealth;
    }

    public String getCycleHealthDesc() {
        return cycleHealthDesc;
    }

    public void setCycleHealthDesc(String cycleHealthDesc) {
        this.cycleHealthDesc = cycleHealthDesc;
    }

    public String getTemperatureHealthDesc() {
        return temperatureHealthDesc;
    }

    public void setTemperatureHealthDesc(String temperatureHealthDesc) {
        this.temperatureHealthDesc = temperatureHealthDesc;
    }

    public String getChargingHabitHealthDesc() {
        return chargingHabitHealthDesc;
    }

    public void setChargingHabitHealthDesc(String chargingHabitHealthDesc) {
        this.chargingHabitHealthDesc = chargingHabitHealthDesc;
    }

    public String getCapacityHealthDesc() {
        return capacityHealthDesc;
    }

    public void setCapacityHealthDesc(String capacityHealthDesc) {
        this.capacityHealthDesc = capacityHealthDesc;
    }

    public String getVoltageHealthDesc() {
        return voltageHealthDesc;
    }

    public void setVoltageHealthDesc(String voltageHealthDesc) {
        this.voltageHealthDesc = voltageHealthDesc;
    }

    public float getOverallScore() {
        return overallScore;
    }

    public void calculateOverallScore() {
        this.overallScore = (cycleHealth + temperatureHealth + chargingHabitHealth
                + capacityHealth + voltageHealth) / 5f;
    }

    public String getOverallGrade() {
        if (overallScore >= 90) return "A+";
        if (overallScore >= 80) return "A";
        if (overallScore >= 70) return "B";
        if (overallScore >= 60) return "C";
        return "D";
    }

    public void setOverallGrade(String overallGrade) {
        this.overallGrade = overallGrade;
    }
}
