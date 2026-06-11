package handler

import (
    "net/http"
    "digiguide/backend/internal/model"
    "digiguide/backend/internal/service"
    "github.com/gin-gonic/gin"
)

// AnalyzeBugreport 上传bugreport文件分析
func AnalyzeBugreport(c *gin.Context) {
    file, err := c.FormFile("file")
    if err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "文件上传失败"})
        return
    }

    // 保存文件并分析
    result, err := service.AnalyzeBugreportFile(file)
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
        return
    }

    c.JSON(http.StatusOK, result)
}

// AnalyzeBugreportText 分析bugreport文本内容
func AnalyzeBugreportText(c *gin.Context) {
    var content string
    if err := c.ShouldBindJSON(&content); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
        return
    }

    result := service.AnalyzeBugreportText(content)
    c.JSON(http.StatusOK, result)
}

// CalculateHealth 计算电池健康度
func CalculateHealth(c *gin.Context) {
    var rawData model.BatteryRawData
    if err := c.ShouldBindJSON(&rawData); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
        return
    }

    result := service.CalculateBatteryHealth(rawData)
    c.JSON(http.StatusOK, result)
}

// GetReports 获取报告列表
func GetReports(c *gin.Context) {
    page := c.DefaultQuery("page", "1")
    limit := c.DefaultQuery("limit", "20")

    reports := service.GetReports(page, limit)
    c.JSON(http.StatusOK, reports)
}

// GetReportDetail 获取报告详情
func GetReportDetail(c *gin.Context) {
    idStr := c.Param("id")

    report, err := service.GetReportDetail(idStr)
    if err != nil {
        c.JSON(http.StatusNotFound, gin.H{"error": "报告不存在"})
        return
    }

    c.JSON(http.StatusOK, report)
}

// DeleteReport 删除报告
func DeleteReport(c *gin.Context) {
    idStr := c.Param("id")

    err := service.DeleteReport(idStr)
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
        return
    }

    c.JSON(http.StatusOK, gin.H{"success": true})
}