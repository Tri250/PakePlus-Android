package model

import "time"

// BatteryRawData 电池原始数据
type BatteryRawData struct {
    Brand             *string  `json:"brand,omitempty"`
    Model             *string  `json:"model,omitempty"`
    SN                *string  `json:"sn,omitempty"`
    DesignCapacityMah *int     `json:"designCapacityMah,omitempty"`
    CurrentCapacityMah *int    `json:"currentCapacityMah,omitempty"`
    ChargeCounterMah  *int     `json:"chargeCounterMah,omitempty"`
    CycleCount        *int     `json:"cycleCount,omitempty"`
    ManufacturingDate *string  `json:"manufacturingDate,omitempty"`
    TemperatureCelsius *float64 `json:"temperatureCelsius,omitempty"`
    ScreenOnTimeHours *int     `json:"screenOnTimeHours,omitempty"`
    ChargeCount       *int     `json:"chargeCount,omitempty"`
}

// ChargingEvent 充电事件
type ChargingEvent struct {
    Timestamp        int64   `json:"timestamp"`
    StartLevel       int     `json:"startLevel"`
    EndLevel         int     `json:"endLevel"`
    DurationMinutes  int     `json:"durationMinutes"`
    AvgPowerW        float64 `json:"avgPowerW"`
}

// AppPowerUsage 应用耗电
type AppPowerUsage struct {
    PackageName  string  `json:"packageName"`
    DisplayName  string  `json:"displayName"`
    PowerMah     float64 `json:"powerMah"`
    WakeupCount  int     `json:"wakeupCount"`
    IsSystem     bool    `json:"isSystem"`
}

// ConfidenceLevel 置信度级别
type ConfidenceLevel string

const (
    ConfidenceHigh   ConfidenceLevel = "HIGH"
    ConfidenceMedium ConfidenceLevel = "MEDIUM"
    ConfidenceLow    ConfidenceLevel = "LOW"
    ConfidenceNone   ConfidenceLevel = "NONE"
)

// HealthFactors 健康度因子
type HealthFactors struct {
    CapacityRetention *float64 `json:"capacityRetention,omitempty"`
    CycleDecay        *float64 `json:"cycleDecay,omitempty"`
    ResistanceGrowth  *float64 `json:"resistanceGrowth,omitempty"`
    ThermalAging      *float64 `json:"thermalAging,omitempty"`
    ChargingDamage    *float64 `json:"chargingDamage,omitempty"`
    AvailableFactors  int      `json:"availableFactors"`
}

// BatteryHealthResult 电池健康度结果
type BatteryHealthResult struct {
    HealthPercentage     float64        `json:"healthPercentage"`
    Grade                string         `json:"grade"`
    Factors              HealthFactors  `json:"factors"`
    DiagnosisText        string         `json:"diagnosisText"`
    Suggestions          []string       `json:"suggestions"`
    EstimatedResistanceMohm *float64    `json:"estimatedResistanceMohm,omitempty"`
    RemainingLifespanMonths *int        `json:"remainingLifespanMonths,omitempty"`
    Confidence           ConfidenceLevel `json:"confidence"`
}

// BatteryAnalysisResponse 电池分析响应
type BatteryAnalysisResponse struct {
    RawData      BatteryRawData      `json:"rawData"`
    HealthResult BatteryHealthResult `json:"healthResult"`
    ReportID     int64               `json:"reportId"`
    AnalysisTime time.Time           `json:"analysisTime"`
}

// BatteryReport 电池报告数据库模型
type BatteryReport struct {
    ID                 uint      `gorm:"primaryKey" json:"id"`
    Brand              *string   `json:"brand,omitempty"`
    Model              *string   `json:"model,omitempty"`
    SN                 *string   `json:"sn,omitempty"`
    DesignCapacityMah  *int      `json:"designCapacityMah,omitempty"`
    CurrentCapacityMah *int      `json:"currentCapacityMah,omitempty"`
    CycleCount         *int      `json:"cycleCount,omitempty"`
    ManufacturingDate  *string   `json:"manufacturingDate,omitempty"`
    TemperatureCelsius *float64  `json:"temperatureCelsius,omitempty"`
    HealthPercentage   float64   `json:"healthPercentage"`
    Grade              string    `json:"grade"`
    CapacityRetention  *float64  `json:"capacityRetention,omitempty"`
    CycleDecay         *float64  `json:"cycleDecay,omitempty"`
    DiagnosisText      *string   `json:"diagnosisText,omitempty"`
    Suggestions        *string   `json:"suggestions,omitempty"` // JSON格式存储
    ReportTime         time.Time `json:"reportTime"`
    RawBugreportPath   *string   `json:"rawBugreportPath,omitempty"`
}

// TableName 设置表名
func (BatteryReport) TableName() string {
    return "battery_reports"
}