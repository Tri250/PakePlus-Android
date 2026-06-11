package handler

import (
    "net/http"
    "digiguide/backend/internal/model"
    "digiguide/backend/internal/service"
    "github.com/gin-gonic/gin"
)

// DecodeSN 解码SN序列号
func DecodeSN(c *gin.Context) {
    sn := c.Query("sn")
    if sn == "" {
        c.JSON(http.StatusBadRequest, gin.H{"error": "SN参数必填"})
        return
    }

    brandStr := c.Query("brand")
    var brand model.Brand
    if brandStr != "" {
        brand = model.Brand(brandStr)
    }

    result := service.DecodeSN(sn, brand)
    c.JSON(http.StatusOK, result)
}

// ValidateSNFormat 验证SN格式
func ValidateSNFormat(c *gin.Context) {
    sn := c.Query("sn")
    brandStr := c.Query("brand")

    if sn == "" || brandStr == "" {
        c.JSON(http.StatusBadRequest, gin.H{"error": "SN和brand参数必填"})
        return
    }

    brand := model.Brand(brandStr)
    valid := service.ValidateSNFormat(sn, brand)

    c.JSON(http.StatusOK, gin.H{"valid": valid})
}

// GetFormatHint 获取品牌SN格式说明
func GetFormatHint(c *gin.Context) {
    brandStr := c.Param("brand")
    brand := model.Brand(brandStr)

    hint := service.GetFormatHint(brand)
    c.JSON(http.StatusOK, gin.H{"hint": hint})
}

// GetWarrantyInfo 获取保修信息
func GetWarrantyInfo(c *gin.Context) {
    sn := c.Query("sn")
    if sn == "" {
        c.JSON(http.StatusBadRequest, gin.H{"error": "SN参数必填"})
        return
    }

    info := service.GetWarrantyInfo(sn)
    c.JSON(http.StatusOK, info)
}

// BatchDecodeSN 批量解码SN
func BatchDecodeSN(c *gin.Context) {
    var req model.SNBatchQueryRequest
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
        return
    }

    results := service.BatchDecodeSN(req.SNList)
    c.JSON(http.StatusOK, results)
}

// GetBrandList 获取品牌列表
func GetBrandList(c *gin.Context) {
    brands := []model.Brand{
        model.BrandApple,
        model.BrandSamsung,
        model.BrandHuawei,
        model.BrandHonor,
        model.BrandXiaomi,
        model.BrandOPPO,
        model.BrandVivo,
        model.BrandLenovo,
        model.BrandHP,
        model.BrandASUS,
        model.BrandDell,
    }

    brandInfos := make([]gin.H, 0)
    for _, brand := range brands {
        brandInfos = append(brandInfos, gin.H{
            "name":   brand,
            "chinese": service.GetBrandChinese(brand),
        })
    }

    c.JSON(http.StatusOK, gin.H{"brands": brandInfos})
}

// GetBrandInfo 获取品牌信息
func GetBrandInfo(c *gin.Context) {
    brandStr := c.Param("brand")
    brand := model.Brand(brandStr)

    info := gin.H{
        "name":       brand,
        "chinese":    service.GetBrandChinese(brand),
        "formatHint": service.GetFormatHint(brand),
        "warrantyMonths": service.GetWarrantyMonths(brand),
    }

    c.JSON(http.StatusOK, info)
}