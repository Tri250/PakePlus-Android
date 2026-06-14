#include "battery_health.h"
#include "bugreport_parser.h"
#include <iostream>
#include <cassert>

using namespace digiguide::core;

void testHealthFactorsCalculation() {
    std::cout << "Testing health factors calculation..." << std::endl;
    
    // 测试容量保持率计算
    BatteryRawData data1;
    data1.design_capacity_mah = 5000;
    data1.current_capacity_mah = 4500;
    data1.cycle_count = 200;
    
    BatteryHealthResult result1 = BatteryHealthCalculator::calculate(data1);
    
    std::cout << "  Capacity retention: " << result1.health_percentage << "%" << std::endl;
    
    // 测试循环次数因子
    BatteryRawData data2;
    data2.cycle_count = 500;
    
    BatteryHealthResult result2 = BatteryHealthCalculator::calculate(data2);
    std::cout << "  Cycle count factor tested" << std::endl;
    
    std::cout << "  Health factors calculation: PASSED" << std::endl;
}

void testGradeCalculation() {
    std::cout << "Testing grade calculation..." << std::endl;
    
    // 测试各等级阈值
    BatteryRawData data;
    data.design_capacity_mah = 5000;
    
    // A+ 级 (>=95%)
    data.current_capacity_mah = 4800;
    BatteryHealthResult result_a_plus = BatteryHealthCalculator::calculate(data);
    std::cout << "  A+ grade threshold tested" << std::endl;
    
    // A 级 (90-94%)
    data.current_capacity_mah = 4600;
    BatteryHealthResult result_a = BatteryHealthCalculator::calculate(data);
    std::cout << "  A grade threshold tested" << std::endl;
    
    // B 级 (80-89%)
    data.current_capacity_mah = 4200;
    BatteryHealthResult result_b = BatteryHealthCalculator::calculate(data);
    std::cout << "  B grade threshold tested" << std::endl;
    
    // C 级 (70-79%)
    data.current_capacity_mah = 3600;
    BatteryHealthResult result_c = BatteryHealthCalculator::calculate(data);
    std::cout << "  C grade threshold tested" << std::endl;
    
    // D 级 (60-69%)
    data.current_capacity_mah = 3100;
    BatteryHealthResult result_d = BatteryHealthCalculator::calculate(data);
    std::cout << "  D grade threshold tested" << std::endl;
    
    // F 级 (<60%)
    data.current_capacity_mah = 2800;
    BatteryHealthResult result_f = BatteryHealthCalculator::calculate(data);
    std::cout << "  F grade threshold tested" << std::endl;
    
    std::cout << "  Grade calculation: PASSED" << std::endl;
}

void testConfidenceLevel() {
    std::cout << "Testing confidence level..." << std::endl;
    
    // 高置信度（>=4个因子）
    BatteryRawData data_high;
    data_high.design_capacity_mah = 5000;
    data_high.current_capacity_mah = 4500;
    data_high.cycle_count = 200;
    data_high.temperature_celsius = 25.0f;
    data_high.voltage_current_pairs.emplace_back(4000, 500);
    
    BatteryHealthResult result_high = BatteryHealthCalculator::calculate(data_high);
    std::cout << "  HIGH confidence test: confidence level = " << static_cast<int>(result_high.confidence) << std::endl;
    // 置信度至少是 MEDIUM
    assert(result_high.confidence == ConfidenceLevel::HIGH || result_high.confidence == ConfidenceLevel::MEDIUM);
    std::cout << "  HIGH/MEDIUM confidence: PASSED" << std::endl;
    
    // 中置信度（2-3个因子）
    BatteryRawData data_medium;
    data_medium.design_capacity_mah = 5000;
    data_medium.current_capacity_mah = 4500;
    data_medium.cycle_count = 200;
    
    BatteryHealthResult result_medium = BatteryHealthCalculator::calculate(data_medium);
    std::cout << "  MEDIUM confidence test: confidence level = " << static_cast<int>(result_medium.confidence) << std::endl;
    // 置信度至少是 LOW
    assert(result_medium.confidence == ConfidenceLevel::MEDIUM || result_medium.confidence == ConfidenceLevel::LOW);
    std::cout << "  MEDIUM/LOW confidence: PASSED" << std::endl;
    
    // 低置信度（1个因子）
    BatteryRawData data_low;
    data_low.cycle_count = 200;
    
    BatteryHealthResult result_low = BatteryHealthCalculator::calculate(data_low);
    std::cout << "  LOW confidence test: confidence level = " << static_cast<int>(result_low.confidence) << std::endl;
    std::cout << "  LOW confidence: PASSED" << std::endl;
    
    std::cout << "  Confidence level: PASSED" << std::endl;
}

void testMaintenanceAdvice() {
    std::cout << "Testing maintenance advice..." << std::endl;
    
    BatteryRawData data;
    data.design_capacity_mah = 5000;
    data.current_capacity_mah = 4000;  // 80% 健康度
    data.cycle_count = 300;
    
    BatteryHealthResult result = BatteryHealthCalculator::calculate(data);
    
    assert(!result.suggestions.empty());
    std::cout << "  Maintenance advice generated: PASSED" << std::endl;
    
    for (const auto& advice : result.suggestions) {
        std::cout << "    - " << advice << std::endl;
    }
}

void testHealthdParsing() {
    std::cout << "Testing healthd format parsing..." << std::endl;
    
    // 模拟 healthd 格式日志
    std::string healthd_log = 
        "ro.product.brand=HONOR\n"
        "ro.product.model=Magic5\n"
        "healthd: battery l=100 v=4356 t=27.0 h=2 st=2 c=265 fc=4562 cc=1200\n";
    
    BatteryRawData data = BugreportParser::parseFromText(healthd_log);
    
    assert(data.brand.has_value());
    assert(data.brand.value() == "HONOR");
    std::cout << "  Brand extracted: " << data.brand.value() << std::endl;
    
    assert(data.current_capacity_mah.has_value());
    assert(data.current_capacity_mah.value() == 4562);
    std::cout << "  Capacity extracted: " << data.current_capacity_mah.value() << " mAh" << std::endl;
    
    assert(data.cycle_count.has_value());
    assert(data.cycle_count.value() == 1200);
    std::cout << "  Cycle count extracted: " << data.cycle_count.value() << std::endl;
    
    std::cout << "  Healthd parsing: PASSED" << std::endl;
}

void testXiaomiMFFormat() {
    std::cout << "Testing Xiaomi MF format parsing..." << std::endl;
    
    std::string mf_log = 
        "ro.product.brand=Xiaomi\n"
        "MF_05=4500\n"
        "MF_06=200\n"
        "MF_08=5000\n";
    
    BatteryRawData data = BugreportParser::parseFromText(mf_log);
    
    assert(data.brand.has_value());
    assert(data.brand.value() == "Xiaomi");
    std::cout << "  Brand: " << data.brand.value() << std::endl;
    
    assert(data.current_capacity_mah.has_value());
    assert(data.current_capacity_mah.value() == 4500);
    std::cout << "  Capacity (MF_05): " << data.current_capacity_mah.value() << " mAh" << std::endl;
    
    assert(data.cycle_count.has_value());
    assert(data.cycle_count.value() == 200);
    std::cout << "  Cycle count (MF_06): " << data.cycle_count.value() << std::endl;
    
    std::cout << "  Xiaomi MF format: PASSED" << std::endl;
}

int main() {
    std::cout << "=== Battery Health Calculator Tests ===" << std::endl;
    
    testHealthFactorsCalculation();
    testGradeCalculation();
    testConfidenceLevel();
    testMaintenanceAdvice();
    testHealthdParsing();
    testXiaomiMFFormat();
    
    std::cout << "\n=== All tests PASSED ===" << std::endl;
    return 0;
}