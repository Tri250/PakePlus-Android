#include "battery_health.h"
#include "bugreport_parser.h"
#include <iostream>
#include <cassert>
#include <cmath>

using namespace digiguide::core;

void testCapacityRetention() {
    std::cout << "Testing capacity retention calculation..." << std::endl;

    auto perfect = BatteryHealthCalculator::calculateCapacityRetention(4500, 4500);
    assert(perfect.has_value());
    assert(std::fabs(perfect.value() - 1.0f) < 0.001f);

    auto degraded = BatteryHealthCalculator::calculateCapacityRetention(3600, 4500);
    assert(degraded.has_value());
    assert(std::fabs(degraded.value() - 0.8f) < 0.001f);

    auto missing = BatteryHealthCalculator::calculateCapacityRetention(std::nullopt, 4500);
    assert(!missing.has_value());

    std::cout << "Capacity retention tests passed!" << std::endl;
}

void testCycleDecay() {
    std::cout << "Testing cycle decay calculation..." << std::endl;

    auto low = BatteryHealthCalculator::calculateCycleDecay(100, BatteryType::LiPo);
    assert(low.has_value());
    assert(low.value() > 0.95f);

    auto high = BatteryHealthCalculator::calculateCycleDecay(1000, BatteryType::LiPo);
    assert(high.has_value());
    assert(high.value() < low.value());

    auto missing = BatteryHealthCalculator::calculateCycleDecay(std::nullopt, BatteryType::LiPo);
    assert(!missing.has_value());

    std::cout << "Cycle decay tests passed!" << std::endl;
}

void testComprehensiveHealth() {
    std::cout << "Testing comprehensive health calculation..." << std::endl;

    BatteryRawData data;
    data.design_capacity_mah = 4500;
    data.current_capacity_mah = 4000;
    data.cycle_count = 300;
    data.temperature_celsius = 35.0f;

    BatteryHealthResult result = BatteryHealthCalculator::calculate(data);
    assert(result.health_percentage >= 0.0f && result.health_percentage <= 100.0f);
    assert(!result.grade.empty());
    assert(result.factors.available_factors >= 2);

    std::cout << "  Health: " << result.health_percentage << "%" << std::endl;
    std::cout << "  Grade: " << result.grade << std::endl;
    std::cout << "  Confidence: " << static_cast<int>(result.confidence) << std::endl;

    std::cout << "Comprehensive health tests passed!" << std::endl;
}

void testNoData() {
    std::cout << "Testing no-data fallback..." << std::endl;

    BatteryRawData data;
    BatteryHealthResult result = BatteryHealthCalculator::calculate(data);
    assert(result.health_percentage >= 0.0f && result.health_percentage <= 100.0f);
    assert(result.confidence == ConfidenceLevel::NONE || result.confidence == ConfidenceLevel::LOW);

    std::cout << "No-data fallback tests passed!" << std::endl;
}

int main() {
    std::cout << "Battery health calculator tests" << std::endl;
    std::cout << "===============================" << std::endl;

    try {
        testCapacityRetention();
        testCycleDecay();
        testComprehensiveHealth();
        testNoData();
    } catch (const std::exception& e) {
        std::cerr << "Test failed with exception: " << e.what() << std::endl;
        return 1;
    }

    std::cout << "All battery health tests passed!" << std::endl;
    return 0;
}
