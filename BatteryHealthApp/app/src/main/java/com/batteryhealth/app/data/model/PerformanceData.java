package com.batteryhealth.app.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;

/**
 * 性能数据实体类
 *
 * 存储应用性能分析数据
 */
@Entity(tableName = "performance_data",
        indices = {
                @Index(value = {"timestamp"}),
                @Index(value = {"app_package"}),
                @Index(value = {"has_issue"})
        })
public class PerformanceData {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    // CPU信息
    @ColumnInfo(name = "cpu_usage")
    private float cpuUsage; // CPU使用率

    @ColumnInfo(name = "cpu_freq_max")
    private int cpuFreqMax; // CPU最大频率 (MHz)

    @ColumnInfo(name = "cpu_freq_current")
    private int cpuFreqCurrent; // CPU当前频率 (MHz)

    // 内存信息
    @ColumnInfo(name = "memory_total")
    private long memoryTotal; // 总内存 (MB)

    @ColumnInfo(name = "memory_used")
    private long memoryUsed; // 已用内存 (MB)

    @ColumnInfo(name = "memory_free")
    private long memoryFree; // 空闲内存 (MB)

    // 应用性能
    @ColumnInfo(name = "app_package")
    private String appPackage; // 应用包名

    @ColumnInfo(name = "app_name")
    private String appName; // 应用名称

    @ColumnInfo(name = "app_memory")
    private long appMemory; // 应用内存使用 (MB)

    @ColumnInfo(name = "app_cpu_time")
    private long appCpuTime; // 应用CPU时间

    // 卡顿数据
    @ColumnInfo(name = "frame_drop_count")
    private int frameDropCount; // 掉帧次数

    @ColumnInfo(name = "frame_total")
    private int frameTotal; // 总帧数

    @ColumnInfo(name = "fps")
    private float fps; // 帧率

    // 性能评分
    @ColumnInfo(name = "performance_score")
    private int performanceScore; // 性能评分 (0-100)

    // 性能隐患
    @ColumnInfo(name = "has_issue")
    private boolean hasIssue; // 是否存在性能问题

    @ColumnInfo(name = "issue_type")
    private String issueType; // 问题类型

    @ColumnInfo(name = "issue_description")
    private String issueDescription; // 问题描述

    public PerformanceData() {
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
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

    public float getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(float cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public int getCpuFreqMax() {
        return cpuFreqMax;
    }

    public void setCpuFreqMax(int cpuFreqMax) {
        this.cpuFreqMax = cpuFreqMax;
    }

    public int getCpuFreqCurrent() {
        return cpuFreqCurrent;
    }

    public void setCpuFreqCurrent(int cpuFreqCurrent) {
        this.cpuFreqCurrent = cpuFreqCurrent;
    }

    public long getMemoryTotal() {
        return memoryTotal;
    }

    public void setMemoryTotal(long memoryTotal) {
        this.memoryTotal = memoryTotal;
    }

    public long getMemoryUsed() {
        return memoryUsed;
    }

    public void setMemoryUsed(long memoryUsed) {
        this.memoryUsed = memoryUsed;
    }

    public long getMemoryFree() {
        return memoryFree;
    }

    public void setMemoryFree(long memoryFree) {
        this.memoryFree = memoryFree;
    }

    public String getAppPackage() {
        return appPackage;
    }

    public void setAppPackage(String appPackage) {
        this.appPackage = appPackage;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public long getAppMemory() {
        return appMemory;
    }

    public void setAppMemory(long appMemory) {
        this.appMemory = appMemory;
    }

    public long getAppCpuTime() {
        return appCpuTime;
    }

    public void setAppCpuTime(long appCpuTime) {
        this.appCpuTime = appCpuTime;
    }

    public int getFrameDropCount() {
        return frameDropCount;
    }

    public void setFrameDropCount(int frameDropCount) {
        this.frameDropCount = frameDropCount;
    }

    public int getFrameTotal() {
        return frameTotal;
    }

    public void setFrameTotal(int frameTotal) {
        this.frameTotal = frameTotal;
    }

    public float getFps() {
        return fps;
    }

    public void setFps(float fps) {
        this.fps = fps;
    }

    public int getPerformanceScore() {
        return performanceScore;
    }

    public void setPerformanceScore(int performanceScore) {
        this.performanceScore = performanceScore;
    }

    public boolean isHasIssue() {
        return hasIssue;
    }

    public void setHasIssue(boolean hasIssue) {
        this.hasIssue = hasIssue;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getIssueDescription() {
        return issueDescription;
    }

    public void setIssueDescription(String issueDescription) {
        this.issueDescription = issueDescription;
    }

    /**
     * 计算内存使用率
     */
    public float getMemoryUsagePercent() {
        if (memoryTotal <= 0) return 0;
        return (memoryUsed * 100.0f) / memoryTotal;
    }

    /**
     * 计算掉帧率
     */
    public float getFrameDropRate() {
        if (frameTotal <= 0) return 0;
        return (frameDropCount * 100.0f) / frameTotal;
    }

    /**
     * 获取性能等级
     */
    public String getPerformanceLevel() {
        if (performanceScore >= 90) {
            return "优秀";
        } else if (performanceScore >= 80) {
            return "良好";
        } else if (performanceScore >= 60) {
            return "一般";
        } else {
            return "较差";
        }
    }
}
