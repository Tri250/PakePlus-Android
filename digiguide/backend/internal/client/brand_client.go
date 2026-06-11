package client

import (
    "digiguide/backend/internal/model"
    "github.com/go-resty/resty/v2"
)

var httpClient = resty.New()

// QueryOPPO 查询OPPO官方API
func QueryOPPO(sn string) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand:         model.BrandOPPO,
        RawSN:         sn,
        Status:        model.StatusPartial,
        ErrorMessage:  "OPPO SN需要官方API查询",
    }

    // TODO: 实现OPPO官方API调用
    // resp, err := httpClient.R().
    //     SetQueryParam("sn", sn).
    //     Get("https://api.oppo.com/v1/sn/query")

    return result
}

// QueryLenovo 查询联想官方API
func QueryLenovo(sn string) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand:         model.BrandLenovo,
        RawSN:         sn,
        Status:        model.StatusPartial,
        ErrorMessage:  "Lenovo SN需要官方API查询",
    }

    // TODO: 实现联想官方API调用

    return result
}

// QueryHP 查询惠普官方API
func QueryHP(sn string) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand:         model.BrandHP,
        RawSN:         sn,
        Status:        model.StatusPartial,
        ErrorMessage:  "HP SN需要官方API查询",
    }

    // TODO: 实现惠普官方API调用

    return result
}

// QueryDell 查询戴尔官方API
func QueryDell(sn string) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand:         model.BrandDell,
        RawSN:         sn,
        Status:        model.StatusPartial,
        ErrorMessage:  "Dell SN需要官方API查询",
    }

    // TODO: 实现戴尔官方API调用

    return result
}

// QueryApple 查询苹果官方API
func QueryApple(sn string) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand:         model.BrandApple,
        RawSN:         sn,
        Status:        model.StatusPartial,
        ErrorMessage:  "Apple SN需要官方API查询",
    }

    // TODO: 实现苹果官方API调用
    // https://checkcoverage.apple.com

    return result
}

// QuerySamsung 查询三星官方API
func QuerySamsung(sn string) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand:         model.BrandSamsung,
        RawSN:         sn,
        Status:        model.StatusPartial,
        ErrorMessage:  "Samsung SN需要官方API查询",
    }

    // TODO: 实现三星官方API调用

    return result
}

// QueryHuawei 查询华为官方API
func QueryHuawei(sn string) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand:         model.BrandHuawei,
        RawSN:         sn,
        Status:        model.StatusPartial,
        ErrorMessage:  "Huawei SN需要官方API查询",
    }

    // TODO: 实现华为官方API调用

    return result
}

// QueryXiaomi 查询小米官方API
func QueryXiaomi(sn string) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand:         model.BrandXiaomi,
        RawSN:         sn,
        Status:        model.StatusPartial,
        ErrorMessage:  "Xiaomi SN需要官方API查询",
    }

    // TODO: 实现小米官方API调用

    return result
}

// QueryVivo 查询vivo官方API
func QueryVivo(sn string) model.SNDecodeResult {
    result := model.SNDecodeResult{
        Brand:         model.BrandVivo,
        RawSN:         sn,
        Status:        model.StatusPartial,
        ErrorMessage:  "vivo SN需要官方API查询",
    }

    // TODO: 实现vivo官方API调用

    return result
}