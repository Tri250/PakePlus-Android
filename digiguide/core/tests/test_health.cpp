#include "battery_health.h"
#include "bugreport_parser.h"
#include <iostream>
#include <cassert>
#include <cmath>

using namespace digiguide::core;

void testCapacityRetention() {
    std::cout << "Testing capacity retention calculation..." << std::endl;

    // 正常情况
    auto result = BatteryHealthCalculator::calculateCapacityRetention(4000, 4500);
    assert(result.has_value());
    float retention = result.value();
    std::cout << "  Retention (4000/4500): " << retention * 100 << "%" << std::endl;
    assert(std::abs(retention - 0.8889f) < 0.01f);

    // 容量等于设计容量
    result = BatteryHealthCalculator::calculateCapacityRetention(4500, 4500);
    assert(result.has_value());
    assert(std::abs(result.value() - 1.0f) < 0.01f);

    // 缺失数据
    result = BatteryHealthCalculator::calculateCapacityRetention(std::nullopt, 4500);
    assert(!result.has_value());

    result = BatteryHealthCalculator::calculateCapacityRetention(4000, std::nullopt);
    assert(!result.has_value());

    std::cout << "Capacity retention tests passed!" << std::endl;
}

void testCycleDecay() {
    std::cout << "Testing cycle decay calculation..." << std::endl;

    // 低循环次数
    auto result = BatteryHealthCalculator::calculateCycleDecay(50, BatteryType::LiPo);
    assert(result.has_value());
    std::cout << "  Decay (50 cycles): " << result.value() * 100 << "%" << std::endl;

    // 中等循环次数
    result = BatteryHealthCalculator::calculateCycleDecay(200, BatteryType::LiPo);
    assert(result.has_value());
    std::cout << "  Decay (200 cycles): " << result.value() * 100 << "%" << std::endl;

    // 高循环次数
    result = BatteryHealthCalculator::calculateCycleDecay(400, BatteryType::LiPo);
    assert(result.has_value());
    std::cout << "  Decay (400 cycles): " << result.value() * 100 << "%" << std::endl;

    // 缺失数据
    result = BatteryHealthCalculator::calculateCycleDecay(std::nullopt, BatteryType::LiPo);
    assert(!result.has_value());

    std::cout << "Cycle decay tests passed!" << std::endl;
}

void testHealthCalculation() {
    std::cout << "Testing full health calculation..." << std::endl;

    // 创建测试数据
    std::string bugreport =
        "ro.product.brand=Xiaomi\n"
        "ro.product.model=M2007J3SC\n"
        "DesignCapacity: 4500\n"
        "Min learned battery capacity: 4200 mAh\n"
        "battery cycle count: 150\n"
        "manufacturing_date: 2023-06-15\n"
        "battery temperature: 28.5 C\n";

    BatteryRawData raw_data = BugreportParser::parseFromText(bugreport);
    BatteryHealthResult result = BatteryHealthCalculator::calculate(raw_data);

    // 验证结果
    std::cout << "  Health percentage: " << result.health_percentage << "%" << std::endl;
    std::cout << "  Grade: " << result.grade << std::endl;
    std::cout << "  Confidence: " << result.factors.available_factors << " factors" << std::endl;

    assert(result.health_percentage > 0);
    assert(result.health_percentage <= 100);
    assert(!result.grade.empty());
    assert(result.factors.available_factors >= 2);

    // 验证因子
    assert(result.factors.capacity_retention.has_value());
    assert(result.factors.cycle_decay.has_value());

    std::cout << "Health calculation tests passed!" << std::endl;
}

void testGradeComputation() {
    std::cout << "Testing grade computation..." << std::endl;

    // 测试各等级阈值
    std::string grade;

    grade = HealthGradeDescriptions::getDescription("A+");
    std::cout << "  A+ description: " << grade << std::endl;
    assert(grade.find("极佳") != std::string::npos);

    grade = HealthGradeDescriptions::getDescription("A");
    std::cout << "  A description: " << grade << std::endl;
    assert(grade.find("良好") != std::string::npos);

    grade = HealthGradeDescriptions::getDescription("B");
    std::cout << "  B description: " << grade << std::endl;
    assert(grade.find("一般") != std::string::npos);

    grade = HealthGradeDescriptions::getDescription("F");
    std::cout << "  F description: " << grade << std::endl;
    assert(grade.find("极差") != std::string::npos);

    // 测试颜色
    std::string color = HealthGradeDescriptions::getColor("A+");
    std::cout << "  A+ color: " << color << std::endl;
    assert(color == "#4CAF50");

    color = HealthGradeDescriptions::getColor("F");
    std::cout << "  F color: " << color << std::endl;
    assert(color == "#9C27B0");

    std::cout << "Grade computation tests passed!" << std::endl;
}

void testSuggestions() {
    std::cout << "Testing suggestions generation..." << std::endl;

    // 创建低健康度数据
    std::string bugreport =
        "ro.product.brand=Xiaomi\n"
        "DesignCapacity: 4500\n"
        "Min learned battery capacity: 3500 mAh\n"
        "battery cycle count: 450\n"
        "battery temperature: 42.0 C\n";

    BatteryRawData raw_data = BugreportParser::parseFromText(bugreport);
    BatteryHealthResult result = BatteryHealthCalculator::calculate(raw_data);

    std::cout << "  Health: " << result.health_percentage << "%" << std::endl;
    std::cout << "  Grade: " << result.grade << std::endl;

    // 验证建议
    std::cout << "  Suggestions:" << std::endl;
    for (const auto& suggestion : result.suggestions) {
        std::cout << "    - " << suggestion << std::endl;
    }

    assert(!result.suggestions.empty());

    std::cout << "Suggestions tests passed!" << std::endl;
}

void testConfidenceLevel() {
    std::cout << "Testing confidence level..." << std::endl;

    // 高置信度（多个因子）
    std::string bugreport1 =
        "DesignCapacity: 4500\n"
        "Min learned battery capacity: 4200 mAh\n"
        "battery cycle count: 150\n"
        "battery temperature: 28.5 C\n";

    BatteryRawData raw1 = BugreportParser::parseFromText(bugreport1);
    BatteryHealthResult result1 = BatteryHealthCalculator::calculate(raw1);

    std::cout << "  High confidence factors: " << result1.factors.available_factors << std::endl;
    assert(result1.factors.available_factors >= 3);

    // 低置信度（少量因子）
    std::string bugreport2 = "DesignCapacity: 4500\n";
    BatteryRawData raw2 = BugreportParser::parseFromText(bugreport2);
    BatteryHealthResult result2 = BatteryHealthCalculator::calculate(raw2);

    std::cout << "  Low confidence factors: " << result2.factors.available_factors << std::endl;
    assert(result2.factors.available_factors < 2);

    std::cout << "Confidence level tests passed!" << std::endl;
}

void testDiagnosisText() {
    std::cout << "Testing diagnosis text generation..." << std::endl;

    std::string bugreport =
        "ro.product.brand=Xiaomi\n"
        "DesignCapacity: 4500\n"
        "Min learned battery capacity: 4200 mAh\n"
        "battery cycle count: 150\n";

    BatteryRawData raw_data = BugreportParser::parseFromText(bugreport);
    BatteryHealthResult result = BatteryHealthCalculator::calculate(raw_data);

    std::cout << "Diagnosis text:" << std::endl;
    std::cout << result.diagnosis_text << std::endl;

    assert(!result.diagnosis_text.empty());
    assert(result.diagnosis_text.find("容量保持率") != std::string::npos);

    std::cout << "Diagnosis text tests passed!" << std::endl;
}

void testRemainingLifespan() {
    std::cout << "Testing remaining lifespan estimation..." << std::endl;

    std::string bugreport =
        "DesignCapacity: 4500\n"
        "Min learned battery capacity: 4200 mAh\n"
        "battery cycle count: 150\n";

    BatteryRawData raw_data = BugreportParser::parseFromText(bugreport);
    BatteryHealthResult result = BatteryHealthCalculator::calculate(raw_data);

    if (result.remaining_lifespan_months.has_value()) {
        std::cout << "  Remaining lifespan: " << result.remaining_lifespan_months.value() << " months" << std::endl;
    }

    std::cout << "Remaining lifespan tests passed!" << std::endl;
}

void testEdgeCases() {
    std::cout << "Testing edge cases..." << std::endl;

    // 空数据
    BatteryRawData empty_data;
    BatteryHealthResult empty_result = BatteryHealthCalculator::calculate(empty_data);
    std::cout << "  Empty data health: " << empty_result.health_percentage << "%" << std::endl;
    assert(empty_result.health_percentage == 0);
    assert(empty_result.factors.available_factors == 0);

    // 容量超过设计容量（新电池可能略高）
    std::string bugreport = "DesignCapacity: 4500\nMin learned battery capacity: 4600 mAh\n";
    BatteryRawData raw = BugreportParser::parseFromText(bugreport);
    BatteryHealthResult result = BatteryHealthCalculator::calculate(raw);
    std::cout << "  Over-capacity health: " << result.health_percentage << "%" << std::endl;
    assert(result.health_percentage > 90);

    std::cout << "Edge case tests passed!" << std::endl;
}

int main() {
    std::cout << "=== Battery Health Calculator Unit Tests ===" << std::endl;

    testCapacityRetention();
    testCycleDecay();
    testHealthCalculation();
    testGradeComputation();
    testSuggestions();
    testConfidenceLevel();
    testDiagnosisText();
    testRemainingLifespan();
    testEdgeCases();

    std::cout << "\n=== All Battery Health tests passed! ===" << std::endl;
    return 0;
}