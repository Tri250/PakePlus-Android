package model

// Brand 品牌枚举
type Brand string

const (
    BrandXiaomi   Brand = "XIAOMI"
    BrandHuawei   Brand = "HUAWEI"
    BrandOPPO     Brand = "OPPO"
    BrandVivo     Brand = "VIVO"
    BrandApple    Brand = "APPLE"
    BrandHonor    Brand = "HONOR"
    BrandSamsung  Brand = "SAMSUNG"
    BrandLenovo   Brand = "LENOVO"
    BrandHP       Brand = "HP"
    BrandASUS     Brand = "ASUS"
    BrandDell     Brand = "DELL"
    BrandAppleMac Brand = "APPLE_MAC"
    BrandUnknown  Brand = "UNKNOWN"
)

// SNDecodeStatus 解码状态
type SNDecodeStatus string

const (
    StatusSuccess SNDecodeStatus = "SUCCESS"
    StatusPartial SNDecodeStatus = "PARTIAL"
    StatusFailed  SNDecodeStatus = "FAILED"
)

// SNDecodeResult SN解码结果
type SNDecodeResult struct {
    Brand          Brand          `json:"brand"`
    RawSN          string         `json:"rawSn"`
    FactoryYear    *int           `json:"factoryYear,omitempty"`
    FactoryMonth   *int           `json:"factoryMonth,omitempty"`
    FactoryWeek    *int           `json:"factoryWeek,omitempty"`
    HalfYear       *string        `json:"halfYear,omitempty"`
    Status         SNDecodeStatus `json:"status"`
    ErrorMessage   string         `json:"errorMessage,omitempty"`
    ProductionDate string         `json:"productionDate,omitempty"`
    WarrantyStatus string         `json:"warrantyStatus,omitempty"`
}

// SNQueryRequest SN查询请求
type SNQueryRequest struct {
    SN    string `json:"sn" binding:"required"`
    Brand Brand  `json:"brand,omitempty"`
}

// SNBatchQueryRequest 批量SN查询请求
type SNBatchQueryRequest struct {
    SNList []string `json:"snList" binding:"required"`
}

// WarrantyInfo 保修信息
type WarrantyInfo struct {
    SN               string  `json:"sn"`
    Brand            Brand   `json:"brand"`
    PurchaseDate     *string `json:"purchaseDate,omitempty"`
    WarrantyStartDate *string `json:"warrantyStartDate,omitempty"`
    WarrantyEndDate   *string `json:"warrantyEndDate,omitempty"`
    WarrantyStatus    string  `json:"warrantyStatus"`
    RemainingDays     *int    `json:"remainingDays,omitempty"`
}