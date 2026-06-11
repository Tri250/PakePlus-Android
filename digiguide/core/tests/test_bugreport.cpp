#include "bugreport_parser.h"
#include <iostream>
#include <cassert>
#include <string>

using namespace digiguide::core;

void testBasicParsing() {
    std::cout << "Testing basic bugreport parsing..." << std::endl;

    // 模拟bugreport内容
    std::string bugreport = 
        "ro.product.brand=Xiaomi\n"
        "ro.product.model=M2007J3SC\n"
        "DesignCapacity: 4500\n"
        "Min learned battery capacity: 4200 mAh\n"
        "battery cycle count: 150\n"
        "manufacturing_date: 2023-06-15\n"
        "battery temperature: 28.5 C\n"
        "Screen on time: 120.5 h\n";

    BatteryRawData data = BugreportParser::parseFromText(bugreport);

    // 验证提取结果
    assert(data.brand.has_value());
    std::cout << "  Brand: " << data.brand.value() << std::endl;

    assert(data.model.has_value());
    std::cout << "  Model: " << data.model.value() << std::endl;

    assert(data.design_capacity_mah.has_value());
    std::cout << "  Design capacity: " << data.design_capacity_mah.value() << " mAh" << std::endl;

    assert(data.current_capacity_mah.has_value());
    std::cout << "  Current capacity: " << data.current_capacity_mah.value() << " mAh" << std::endl;

    assert(data.cycle_count.has_value());
    std::cout << "  Cycle count: " << data.cycle_count.value() << std::endl;

    assert(data.manufacturing_date.has_value());
    std::cout << "  Manufacturing date: " << data.manufacturing_date.value() << std::endl;

    assert(data.temperature_celsius.has_value());
    std::cout << "  Temperature: " << data.temperature_celsius.value() << " C" << std::endl;

    std::cout << "Basic parsing tests passed!" << std::endl;
}

void testMultipleCapacityPatterns() {
    std::cout << "Testing multiple capacity patterns..." << std::endl;

    // 测试不同格式的容量提取
    std::string bugreport1 = "Min learned battery capacity: 4000 mAh\n";
    BatteryRawData data1 = BugreportParser::parseFromText(bugreport1);
    assert(data1.current_capacity_mah.has_value());
    assert(data1.current_capacity_mah.value() == 4000);

    std::string bugreport2 = "full charge capacity: 3800 mAh\n";
    BatteryRawData data2 = BugreportParser::parseFromText(bugreport2);
    assert(data2.current_capacity_mah.has_value());
    assert(data2.current_capacity_mah.value() == 3800);

    std::cout << "Multiple capacity pattern tests passed!" << std::endl;
}

void testMultipleCyclePatterns() {
    std::cout << "Testing multiple cycle count patterns..." << std::endl;

    // 测试不同格式的循环次数提取
    std::string bugreport1 = "battery cycle count: 100\n";
    BatteryRawData data1 = BugreportParser::parseFromText(bugreport1);
    assert(data1.cycle_count.has_value());
    assert(data1.cycle_count.value() == 100);

    std::string bugreport2 = "CycleCount: 200\n";
    BatteryRawData data2 = BugreportParser::parseFromText(bugreport2);
    assert(data2.cycle_count.has_value());
    assert(data2.cycle_count.value() == 200);

    std::cout << "Multiple cycle count pattern tests passed!" << std::endl;
}

void testMultipleDatePatterns() {
    std::cout << "Testing multiple date patterns..." << std::endl;

    // 测试不同格式的日期提取
    std::string bugreport1 = "manufacturing_date: 2023-06-15\n";
    BatteryRawData data1 = BugreportParser::parseFromText(bugreport1);
    assert(data1.manufacturing_date.has_value());

    std::string bugreport2 = "mfg_date: 20230615\n";
    BatteryRawData data2 = BugreportParser::parseFromText(bugreport2);
    assert(data2.manufacturing_date.has_value());

    std::string bugreport3 = "生产日期：2023年6月15日\n";
    BatteryRawData data3 = BugreportParser::parseFromText(bugreport3);
    assert(data3.manufacturing_date.has_value());

    std::cout << "Multiple date pattern tests passed!" << std::endl;
}

void testParseDetail() {
    std::cout << "Testing parse detail..." << std::endl;

    std::string bugreport = 
        "ro.product.brand=Xiaomi\n"
        "DesignCapacity: 4500\n";

    BatteryRawData data = BugreportParser::parseFromText(bugreport);
    ParseDetail detail = BugreportParser::getParseDetail(data);

    std::cout << "  Extracted fields: ";
    for (const auto& field : detail.extracted_fields) {
        std::cout << field << " ";
    }
    std::cout << std::endl;

    std::cout << "  Missing fields: ";
    for (const auto& field : detail.missing_fields) {
        std::cout << field << " ";
    }
    std::cout << std::endl;

    std::cout << "Parse detail tests passed!" << std::endl;
}

void testParseSummary() {
    std::cout << "Testing parse summary..." << std::endl;

    std::string bugreport = 
        "ro.product.brand=Xiaomi\n"
        "ro.product.model=M2007J3SC\n"
        "DesignCapacity: 4500\n"
        "Min learned battery capacity: 4200 mAh\n";

    BatteryRawData data = BugreportParser::parseFromText(bugreport);
    std::string summary = BugreportParser::getParseSummary(data);
    std::cout << summary << std::endl;

    std::cout << "Parse summary tests passed!" << std::endl;
}

void testEmptyBugreport() {
    std::cout << "Testing empty bugreport..." << std::endl;

    std::string empty_bugreport = "";
    BatteryRawData data = BugreportParser::parseFromText(empty_bugreport);

    assert(!data.brand.has_value());
    assert(!data.model.has_value());
    assert(!data.design_capacity_mah.has_value());
    assert(data.getAvailableDataCount() == 0);

    std::cout << "Empty bugreport tests passed!" << std::endl;
}

int main() {
    std::cout << "=== Bugreport Parser Unit Tests ===" << std::endl;

    testBasicParsing();
    testMultipleCapacityPatterns();
    testMultipleCyclePatterns();
    testMultipleDatePatterns();
    testParseDetail();
    testParseSummary();
    testEmptyBugreport();

    std::cout << "\n=== All Bugreport Parser tests passed! ===" << std::endl;
    return 0;
}