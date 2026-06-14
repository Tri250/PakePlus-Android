#include <jni.h>
#include <string>
#include <vector>
#include <optional>
#include <android/log.h>

#define LOG_TAG "BatteryHealthJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 引入核心库头文件
#include "battery_health.h"
#include "bugreport_parser.h"
#include "result_types.h"
#include "sn_decoder.h"

using namespace digiguide::core;

extern "C" {

// 解析 Bugreport 文件内容
JNIEXPORT jobject JNICALL
Java_com_batteryhealth_app_BatteryAnalyzer_nativeParseBugreport(
    JNIEnv* env,
    jobject thiz,
    jbyteArray content,
    jint contentLength) {

    LOGI("nativeParseBugreport called, contentLength=%d", contentLength);

    // 获取内容
    jbyte* data = env->GetByteArrayElements(content, nullptr);
    std::string text(reinterpret_cast<char*>(data), contentLength);
    env->ReleaseByteArrayElements(content, data, JNI_ABORT);

    // 解析
    BatteryRawData raw_data = BugreportParser::parseFromText(text);

    // 创建返回对象
    jclass resultClass = env->FindClass("com/batteryhealth/app/BatteryParseResult");
    if (resultClass == nullptr) {
        LOGE("Failed to find BatteryParseResult class");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject result = env->NewObject(resultClass, constructor);

    // 设置字段
    // brand
    if (raw_data.brand.has_value()) {
        jfieldID brandField = env->GetFieldID(resultClass, "brand", "Ljava/lang/String;");
        env->SetObjectField(result, brandField, env->NewStringUTF(raw_data.brand->c_str()));
    }

    // model
    if (raw_data.model.has_value()) {
        jfieldID modelField = env->GetFieldID(resultClass, "model", "Ljava/lang/String;");
        env->SetObjectField(result, modelField, env->NewStringUTF(raw_data.model->c_str()));
    }

    // designCapacityMah
    if (raw_data.design_capacity_mah.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "designCapacityMah", "I");
        env->SetIntField(result, field, raw_data.design_capacity_mah.value());
    }

    // currentCapacityMah
    if (raw_data.current_capacity_mah.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "currentCapacityMah", "I");
        env->SetIntField(result, field, raw_data.current_capacity_mah.value());
    }

    // cycleCount
    if (raw_data.cycle_count.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "cycleCount", "I");
        env->SetIntField(result, field, raw_data.cycle_count.value());
    }

    // temperatureCelsius
    if (raw_data.temperature_celsius.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "temperatureCelsius", "F");
        env->SetFloatField(result, field, raw_data.temperature_celsius.value());
    }

    // chargeCounterMah
    if (raw_data.charge_counter_mah.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "chargeCounterMah", "I");
        env->SetIntField(result, field, raw_data.charge_counter_mah.value());
    }

    // manufacturingDate
    if (raw_data.manufacturing_date.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "manufacturingDate", "Ljava/lang/String;");
        env->SetObjectField(result, field, env->NewStringUTF(raw_data.manufacturing_date->c_str()));
    }

    // screenOnTimeHours
    if (raw_data.screen_on_time_hours.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "screenOnTimeHours", "I");
        env->SetIntField(result, field, raw_data.screen_on_time_hours.value());
    }

    // chargeCount
    if (raw_data.charge_count.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "chargeCount", "I");
        env->SetIntField(result, field, raw_data.charge_count.value());
    }

    // hasData
    jfieldID hasDataField = env->GetFieldID(resultClass, "hasData", "Z");
    env->SetBooleanField(result, hasDataField, raw_data.hasCapacityData() || raw_data.hasCycleData());

    LOGI("Parse complete: hasData=%d", raw_data.hasCapacityData() || raw_data.hasCycleData());

    return result;
}

// 计算电池健康度
JNIEXPORT jobject JNICALL
Java_com_batteryhealth_app_BatteryAnalyzer_nativeCalculateHealth(
    JNIEnv* env,
    jobject thiz,
    jobject parseResult) {

    LOGI("nativeCalculateHealth called");

    // 从 parseResult 获取数据
    jclass resultClass = env->GetObjectClass(parseResult);

    BatteryRawData raw_data;

    // brand
    jfieldID brandField = env->GetFieldID(resultClass, "brand", "Ljava/lang/String;");
    jstring brandStr = (jstring)env->GetObjectField(parseResult, brandField);
    if (brandStr != nullptr) {
        const char* brandChars = env->GetStringUTFChars(brandStr, nullptr);
        raw_data.brand = std::string(brandChars);
        env->ReleaseStringUTFChars(brandStr, brandChars);
    }

    // designCapacityMah
    jfieldID designCapField = env->GetFieldID(resultClass, "designCapacityMah", "I");
    jint designCap = env->GetIntField(parseResult, designCapField);
    if (designCap > 0) {
        raw_data.design_capacity_mah = designCap;
    }

    // currentCapacityMah
    jfieldID currentCapField = env->GetFieldID(resultClass, "currentCapacityMah", "I");
    jint currentCap = env->GetIntField(parseResult, currentCapField);
    if (currentCap > 0) {
        raw_data.current_capacity_mah = currentCap;
    }

    // cycleCount
    jfieldID cycleField = env->GetFieldID(resultClass, "cycleCount", "I");
    jint cycles = env->GetIntField(parseResult, cycleField);
    if (cycles > 0) {
        raw_data.cycle_count = cycles;
    }

    // temperatureCelsius
    jfieldID tempField = env->GetFieldID(resultClass, "temperatureCelsius", "F");
    jfloat temp = env->GetFloatField(parseResult, tempField);
    if (temp > 0) {
        raw_data.temperature_celsius = temp;
    }

    // chargeCounterMah
    jfieldID ccField = env->GetFieldID(resultClass, "chargeCounterMah", "I");
    jint cc = env->GetIntField(parseResult, ccField);
    if (cc > 0) {
        raw_data.charge_counter_mah = cc;
    }

    // 计算健康度
    BatteryHealthResult health_result = BatteryHealthCalculator::calculate(raw_data);

    LOGI("Health calculated: percentage=%.1f, grade=%s",
         health_result.health_percentage, health_result.grade.c_str());

    // 创建返回对象
    jclass healthClass = env->FindClass("com/batteryhealth/app/BatteryHealthResult");
    if (healthClass == nullptr) {
        LOGE("Failed to find BatteryHealthResult class");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(healthClass, "<init>", "()V");
    jobject result = env->NewObject(healthClass, constructor);

    // healthPercentage
    jfieldID percentageField = env->GetFieldID(healthClass, "healthPercentage", "F");
    env->SetFloatField(result, percentageField, health_result.health_percentage);

    // grade
    jfieldID gradeField = env->GetFieldID(healthClass, "grade", "Ljava/lang/String;");
    env->SetObjectField(result, gradeField, env->NewStringUTF(health_result.grade.c_str()));

    // gradeColor
    jfieldID colorField = env->GetFieldID(healthClass, "gradeColor", "Ljava/lang/String;");
    env->SetObjectField(result, colorField, env->NewStringUTF(health_result.getGradeColor().c_str()));

    // gradeDescription
    jfieldID descField = env->GetFieldID(healthClass, "gradeDescription", "Ljava/lang/String;");
    env->SetObjectField(result, descField, env->NewStringUTF(health_result.getGradeDescription().c_str()));

    // diagnosisText
    jfieldID diagField = env->GetFieldID(healthClass, "diagnosisText", "Ljava/lang/String;");
    env->SetObjectField(result, diagField, env->NewStringUTF(health_result.diagnosis_text.c_str()));

    // confidence
    jfieldID confField = env->GetFieldID(healthClass, "confidence", "I");
    int confValue = static_cast<int>(health_result.confidence);
    env->SetIntField(result, confField, confValue);

    // estimatedResistanceMohm
    if (health_result.estimated_resistance_mohm.has_value()) {
        jfieldID resField = env->GetFieldID(healthClass, "estimatedResistanceMohm", "F");
        env->SetFloatField(result, resField, health_result.estimated_resistance_mohm.value());
    }

    // remainingLifespanMonths
    if (health_result.remaining_lifespan_months.has_value()) {
        jfieldID lifespanField = env->GetFieldID(healthClass, "remainingLifespanMonths", "I");
        env->SetIntField(result, lifespanField, health_result.remaining_lifespan_months.value());
    }

    // suggestions (List<String>)
    jfieldID suggestionsField = env->GetFieldID(healthClass, "suggestions", "Ljava/util/List;");
    jclass listClass = env->FindClass("java/util/ArrayList");
    jmethodID listConstructor = env->GetMethodID(listClass, "<init>", "()V");
    jobject suggestionsList = env->NewObject(listClass, listConstructor);
    jmethodID addMethod = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");

    for (const auto& suggestion : health_result.suggestions) {
        env->CallBooleanMethod(suggestionsList, addMethod, env->NewStringUTF(suggestion.c_str()));
    }
    env->SetObjectField(result, suggestionsField, suggestionsList);

    // factors
    jclass factorsClass = env->FindClass("com/batteryhealth/app/HealthFactors");
    jmethodID factorsConstructor = env->GetMethodID(factorsClass, "<init>", "()V");
    jobject factorsObj = env->NewObject(factorsClass, factorsConstructor);

    if (health_result.factors.capacity_retention.has_value()) {
        jfieldID f = env->GetFieldID(factorsClass, "capacityRetention", "F");
        env->SetFloatField(factorsObj, f, health_result.factors.capacity_retention.value() * 100);
    }
    if (health_result.factors.cycle_decay.has_value()) {
        jfieldID f = env->GetFieldID(factorsClass, "cycleDecay", "F");
        env->SetFloatField(factorsObj, f, health_result.factors.cycle_decay.value() * 100);
    }
    if (health_result.factors.resistance_growth.has_value()) {
        jfieldID f = env->GetFieldID(factorsClass, "resistanceGrowth", "F");
        env->SetFloatField(factorsObj, f, health_result.factors.resistance_growth.value() * 100);
    }
    if (health_result.factors.thermal_aging.has_value()) {
        jfieldID f = env->GetFieldID(factorsClass, "thermalAging", "F");
        env->SetFloatField(factorsObj, f, health_result.factors.thermal_aging.value() * 100);
    }
    if (health_result.factors.charging_damage.has_value()) {
        jfieldID f = env->GetFieldID(factorsClass, "chargingDamage", "F");
        env->SetFloatField(factorsObj, f, health_result.factors.charging_damage.value() * 100);
    }
    jfieldID availField = env->GetFieldID(factorsClass, "availableFactors", "I");
    env->SetIntField(factorsObj, availField, health_result.factors.available_factors);

    jfieldID factorsField = env->GetFieldID(healthClass, "factors", "Lcom/batteryhealth/app/HealthFactors;");
    env->SetObjectField(result, factorsField, factorsObj);

    return result;
}

// 解析 ZIP 文件
JNIEXPORT jobject JNICALL
Java_com_batteryhealth_app_BatteryAnalyzer_nativeParseZipFile(
    JNIEnv* env,
    jobject thiz,
    jstring filePath) {

    LOGI("nativeParseZipFile called");

    const char* pathChars = env->GetStringUTFChars(filePath, nullptr);
    std::string path(pathChars);
    env->ReleaseStringUTFChars(filePath, pathChars);

    // 解析 ZIP
    BatteryRawData raw_data = BugreportParser::parseFromZip(path);

    // 创建返回对象 (复用 parseBugreport 的逻辑)
    jclass resultClass = env->FindClass("com/batteryhealth/app/BatteryParseResult");
    if (resultClass == nullptr) {
        LOGE("Failed to find BatteryParseResult class");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject result = env->NewObject(resultClass, constructor);

    // 设置字段 (同 parseBugreport)
    if (raw_data.brand.has_value()) {
        jfieldID brandField = env->GetFieldID(resultClass, "brand", "Ljava/lang/String;");
        env->SetObjectField(result, brandField, env->NewStringUTF(raw_data.brand->c_str()));
    }

    if (raw_data.model.has_value()) {
        jfieldID modelField = env->GetFieldID(resultClass, "model", "Ljava/lang/String;");
        env->SetObjectField(result, modelField, env->NewStringUTF(raw_data.model->c_str()));
    }

    if (raw_data.design_capacity_mah.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "designCapacityMah", "I");
        env->SetIntField(result, field, raw_data.design_capacity_mah.value());
    }

    if (raw_data.current_capacity_mah.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "currentCapacityMah", "I");
        env->SetIntField(result, field, raw_data.current_capacity_mah.value());
    }

    if (raw_data.cycle_count.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "cycleCount", "I");
        env->SetIntField(result, field, raw_data.cycle_count.value());
    }

    if (raw_data.temperature_celsius.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "temperatureCelsius", "F");
        env->SetFloatField(result, field, raw_data.temperature_celsius.value());
    }

    if (raw_data.charge_counter_mah.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "chargeCounterMah", "I");
        env->SetIntField(result, field, raw_data.charge_counter_mah.value());
    }

    if (raw_data.manufacturing_date.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "manufacturingDate", "Ljava/lang/String;");
        env->SetObjectField(result, field, env->NewStringUTF(raw_data.manufacturing_date->c_str()));
    }

    if (raw_data.screen_on_time_hours.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "screenOnTimeHours", "I");
        env->SetIntField(result, field, raw_data.screen_on_time_hours.value());
    }

    if (raw_data.charge_count.has_value()) {
        jfieldID field = env->GetFieldID(resultClass, "chargeCount", "I");
        env->SetIntField(result, field, raw_data.charge_count.value());
    }

    jfieldID hasDataField = env->GetFieldID(resultClass, "hasData", "Z");
    env->SetBooleanField(result, hasDataField, raw_data.hasCapacityData() || raw_data.hasCycleData());

    LOGI("ZIP parse complete: hasData=%d", raw_data.hasCapacityData() || raw_data.hasCycleData());

    return result;
}

// 获取解析摘要
JNIEXPORT jstring JNICALL
Java_com_batteryhealth_app_BatteryAnalyzer_nativeGetParseSummary(
    JNIEnv* env,
    jobject thiz,
    jobject parseResult) {

    // 从 parseResult 构建 BatteryRawData
    jclass resultClass = env->GetObjectClass(parseResult);
    BatteryRawData raw_data;

    jfieldID designCapField = env->GetFieldID(resultClass, "designCapacityMah", "I");
    jint designCap = env->GetIntField(parseResult, designCapField);
    if (designCap > 0) raw_data.design_capacity_mah = designCap;

    jfieldID currentCapField = env->GetFieldID(resultClass, "currentCapacityMah", "I");
    jint currentCap = env->GetIntField(parseResult, currentCapField);
    if (currentCap > 0) raw_data.current_capacity_mah = currentCap;

    jfieldID cycleField = env->GetFieldID(resultClass, "cycleCount", "I");
    jint cycles = env->GetIntField(parseResult, cycleField);
    if (cycles > 0) raw_data.cycle_count = cycles;

    jfieldID tempField = env->GetFieldID(resultClass, "temperatureCelsius", "F");
    jfloat temp = env->GetFloatField(parseResult, tempField);
    if (temp > 0) raw_data.temperature_celsius = temp;

    std::string summary = BugreportParser::getParseSummary(raw_data);
    return env->NewStringUTF(summary.c_str());
}

// 初始化 native 库
JNIEXPORT void JNICALL
Java_com_batteryhealth_app_BatteryAnalyzer_nativeInit(
    JNIEnv* env,
    jobject thiz) {
    LOGI("BatteryHealth native library initialized");
}

} // extern "C"