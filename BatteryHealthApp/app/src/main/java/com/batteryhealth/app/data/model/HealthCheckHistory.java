package com.batteryhealth.app.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 自检历史记录实体
 * 保存最近10次自检结果
 */
@Entity(tableName = "health_check_history", indices = {
    @Index(value = "timestamp")
})
public class HealthCheckHistory {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @ColumnInfo(name = "overall_score")
    private int overallScore;

    @ColumnInfo(name = "total_checks")
    private int totalChecks;

    @ColumnInfo(name = "critical_count")
    private int criticalCount;

    @ColumnInfo(name = "warning_count")
    private int warningCount;

    @ColumnInfo(name = "info_count")
    private int infoCount;

    @ColumnInfo(name = "good_count")
    private int goodCount;

    @ColumnInfo(name = "results_json")
    private String resultsJson;

    @ColumnInfo(name = "summary")
    private String summary;

    public HealthCheckHistory() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(int overallScore) {
        this.overallScore = overallScore;
    }

    public int getTotalChecks() {
        return totalChecks;
    }

    public void setTotalChecks(int totalChecks) {
        this.totalChecks = totalChecks;
    }

    public int getCriticalCount() {
        return criticalCount;
    }

    public void setCriticalCount(int criticalCount) {
        this.criticalCount = criticalCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public void setWarningCount(int warningCount) {
        this.warningCount = warningCount;
    }

    public int getInfoCount() {
        return infoCount;
    }

    public void setInfoCount(int infoCount) {
        this.infoCount = infoCount;
    }

    public int getGoodCount() {
        return goodCount;
    }

    public void setGoodCount(int goodCount) {
        this.goodCount = goodCount;
    }

    public String getResultsJson() {
        return resultsJson;
    }

    public void setResultsJson(String resultsJson) {
        this.resultsJson = resultsJson;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
