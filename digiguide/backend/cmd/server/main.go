package main

import (
    "log"
    "digiguide/backend/internal/handler"
    "digiguide/backend/internal/middleware"
    "github.com/gin-gonic/gin"
)

func main() {
    // 设置Gin模式
    gin.SetMode(gin.ReleaseMode)

    // 创建路由
    r := gin.New()

    // 使用中间件
    r.Use(middleware.Logging())
    r.Use(middleware.RateLimit())
    r.Use(gin.Recovery())

    // API路由组
    api := r.Group("/v1")
    {
        // SN查询路由
        snGroup := api.Group("/sn")
        {
            snGroup.GET("/decode", handler.DecodeSN)
            snGroup.GET("/validate", handler.ValidateSNFormat)
            snGroup.GET("/format/:brand", handler.GetFormatHint)
            snGroup.GET("/warranty", handler.GetWarrantyInfo)
            snGroup.POST("/batch", handler.BatchDecodeSN)
        }

        // 电池报告路由
        batteryGroup := api.Group("/battery")
        {
            batteryGroup.POST("/analyze", handler.AnalyzeBugreport)
            batteryGroup.POST("/analyze/text", handler.AnalyzeBugreportText)
            batteryGroup.POST("/health", handler.CalculateHealth)
            batteryGroup.GET("/reports", handler.GetReports)
            batteryGroup.GET("/reports/:id", handler.GetReportDetail)
            batteryGroup.DELETE("/reports/:id", handler.DeleteReport)
        }

        // 品牌路由
        brandGroup := api.Group("/brand")
        {
            brandGroup.GET("/list", handler.GetBrandList)
            brandGroup.GET("/:brand/info", handler.GetBrandInfo)
        }
    }

    // 健康检查
    r.GET("/health", func(c *gin.Context) {
        c.JSON(200, gin.H{
            "status": "ok",
            "version": "3.1.0",
        })
    })

    // 启动服务
    log.Println("Starting DigiGuide API server on :8080")
    if err := r.Run(":8080"); err != nil {
        log.Fatal("Failed to start server:", err)
    }
}