#include "sn_decoder.h"
#include <iostream>
#include <cassert>
#include <string>

using namespace digiguide::core;

void testAppleSN() {
    std::cout << "Testing Apple SN decoder..." << std::endl;

    // 测试有效Apple SN
    SNDecodeResult result = SNDecoder::decode("C3LXK2XXXXX");
    assert(result.brand == Brand::APPLE);
    assert(result.status == SNDecodeStatus::SUCCESS);
    assert(result.factory_year.has_value());
    std::cout << "  Apple SN: " << result.raw_sn << std::endl;
    std::cout << "  Year: " << result.factory_year.value() << std::endl;
    std::cout << "  Week: " << result.factory_week.value() << std::endl;
    std::cout << "  Half year: " << result.half_year.value() << std::endl;

    // 测试短SN
    result = SNDecoder::decode("C3LX");
    assert(result.status == SNDecodeStatus::FAILED);

    std::cout << "Apple SN tests passed!" << std::endl;
}

void testSamsungSN() {
    std::cout << "Testing Samsung SN decoder..." << std::endl;

    // 测试Samsung SN（模拟格式）
    SNDecodeResult result = SNDecoder::decode("R5CR70H1N4", Brand::SAMSUNG);
    assert(result.brand == Brand::SAMSUNG);
    std::cout << "  Samsung SN: " << result.raw_sn << std::endl;
    if (result.factory_year.has_value()) {
        std::cout << "  Year: " << result.factory_year.value() << std::endl;
    }
    if (result.factory_month.has_value()) {
        std::cout << "  Month: " << result.factory_month.value() << std::endl;
    }

    std::cout << "Samsung SN tests passed!" << std::endl;
}

void testHuaweiSN() {
    std::cout << "Testing Huawei SN decoder..." << std::endl;

    // 测试Huawei SN（模拟格式）
    SNDecodeResult result = SNDecoder::decode("XXXXXX2315XXXXX", Brand::HUAWEI);
    assert(result.brand == Brand::HUAWEI);
    std::cout << "  Huawei SN: " << result.raw_sn << std::endl;
    if (result.factory_year.has_value()) {
        std::cout << "  Year: " << result.factory_year.value() << std::endl;
    }
    if (result.factory_week.has_value()) {
        std::cout << "  Week: " << result.factory_week.value() << std::endl;
    }

    std::cout << "Huawei SN tests passed!" << std::endl;
}

void testBrandIdentification() {
    std::cout << "Testing brand identification..." << std::endl;

    // Apple识别
    Brand brand = SNDecoder::identifyBrand("C3LXK2XXXXX");
    assert(brand == Brand::APPLE);

    // 未知品牌
    brand = SNDecoder::identifyBrand("UNKNOWN123456");
    assert(brand == Brand::UNKNOWN);

    std::cout << "Brand identification tests passed!" << std::endl;
}

void testFormatValidation() {
    std::cout << "Testing format validation..." << std::endl;

    // Apple格式验证
    assert(SNDecoder::validateFormat("C3LXK2XXXXX", Brand::APPLE));
    assert(!SNDecoder::validateFormat("C3LX", Brand::APPLE));

    // Samsung格式验证
    assert(SNDecoder::validateFormat("R5CR70H1N4", Brand::SAMSUNG));

    std::cout << "Format validation tests passed!" << std::endl;
}

void testFormatHint() {
    std::cout << "Testing format hints..." << std::endl;

    std::string hint = SNDecoder::getFormatHint(Brand::APPLE);
    std::cout << "  Apple hint: " << hint << std::endl;
    assert(hint.find("12位") != std::string::npos);

    hint = SNDecoder::getFormatHint(Brand::SAMSUNG);
    std::cout << "  Samsung hint: " << hint << std::endl;

    hint = SNDecoder::getFormatHint(Brand::HUAWEI);
    std::cout << "  Huawei hint: " << hint << std::endl;

    std::cout << "Format hint tests passed!" << std::endl;
}

void testProductionDateEstimate() {
    std::cout << "Testing production date estimate..." << std::endl;

    SNDecodeResult result = SNDecoder::decode("C3LXK2XXXXX");
    std::string date = result.getProductionDateEstimate();
    std::cout << "  Estimated date: " << date << std::endl;
    assert(!date.empty());

    std::cout << "Production date estimate tests passed!" << std::endl;
}

int main() {
    std::cout << "=== SN Decoder Unit Tests ===" << std::endl;

    testAppleSN();
    testSamsungSN();
    testHuaweiSN();
    testBrandIdentification();
    testFormatValidation();
    testFormatHint();
    testProductionDateEstimate();

    std::cout << "\n=== All SN Decoder tests passed! ===" << std::endl;
    return 0;
}