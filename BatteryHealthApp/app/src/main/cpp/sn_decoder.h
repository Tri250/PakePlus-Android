#pragma once

#include "result_types.h"
#include <string>
#include <optional>

namespace digiguide::core {

// SN解码结果
struct SNDecodeResult {
    Brand brand;
    std::string raw_sn;

    // 生产日期信息
    std::optional<int> factory_year;
    std::optional<int> factory_month;
    std::optional<int> factory_week;        // 周次（华为/Samsung）
    std::optional<std::string> half_year;   // 半年代码（Apple）

    // 状态信息
    SNDecodeStatus status;
    std::string error_message;

    // 辅助方法
    std::string getProductionDateEstimate() const;
    std::string getBrandName() const { return brandToString(brand); }
    std::string getBrandChinese() const { return brandToChinese(brand); }
};

// SN解码器类
class SNDecoder {
public:
    // 自动识别品牌并解码
    static SNDecodeResult decode(const std::string& sn);

    // 指定品牌解码
    static SNDecodeResult decode(const std::string& sn, Brand brand);

    // 验证SN格式合法性
    static bool validateFormat(const std::string& sn, Brand brand);

    // 获取品牌SN格式说明
    static std::string getFormatHint(Brand brand);

    // 自动识别品牌（仅识别，不解码）
    static Brand identifyBrand(const std::string& sn);

private:
    // 各品牌解码器
    static SNDecodeResult decodeApple(const std::string& sn);
    static SNDecodeResult decodeSamsung(const std::string& sn);
    static SNDecodeResult decodeHuawei(const std::string& sn);
    static SNDecodeResult decodeHonor(const std::string& sn);
    static SNDecodeResult decodeXiaomi(const std::string& sn);
    static SNDecodeResult decodeOPPO(const std::string& sn);
    static SNDecodeResult decodeVivo(const std::string& sn);
    static SNDecodeResult decodeLenovo(const std::string& sn);
    static SNDecodeResult decodeHP(const std::string& sn);
    static SNDecodeResult decodeASUS(const std::string& sn);
    static SNDecodeResult decodeDell(const std::string& sn);
};

} // namespace digiguide::core