#include <jni.h>
#include <string>
#include <vector>
#include <optional>
#include <android/log.h>
#include <cmath>

#define LOG_TAG "BatteryHealthJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 引入核心库头文件
#include "battery_health.h"
#include "bugreport_parser.h"
#include "result_types.h"
#include "sn_decoder.h"

using namespace digiguide::core;

// ========== 辅助函数 ==========

// 安全获取字段ID，失败时返回null并记录错误
static jfieldID safeGetFieldID(JNIEnv* env, jclass cls, const char* name, const char* sig) {
    jfieldID fid = env->GetFieldID(cls, name, sig);
    if (fid == nullptr) {
        LOGE("GetFieldID failed: %s (%s)", name, sig);
    }
    return fid;
}

// 安全获取方法ID
static jmethodID safeGetMethodID(JNIEnv* env, jclass cls, const char* name, const char* sig) {
    jmethodID mid = env->GetMethodID(cls, name, sig);
    if (mid == nullptr) {
        LOGE("GetMethodID failed: %s (%s)", name, sig);
    }
    return mid;
}

// 安全检查float是否为有效数值（非NaN、非Infinity）
static bool isValidFloat(float v) {
    return !std::isnan(v) && !std::isinf(v);
}

// 安全检查double是否为有效数值
static bool isValidDouble(double v) {
    return !std::isnan(v) && !std::isinf(v);
}

// ========== JNI 函数 ==========

extern "C" {

// 解析 Bugreport 文件内容
JNIEXPORT jobject JNICALL
Java_com_batteryhealth_app_BatteryAnalyzer_nativeParseBugreport(
    JNIEnv* env,
    jobject thiz,
    jbyteArray content,
    jint contentLength) {

    LOGI("nativeParseBugreport called, contentLength=%d", contentLength);

    // 安全检查：content 不能为空
    if (content == nullptr || contentLength <= 0) {
        LOGE("nativeParseBugreport: invalid content or length");
        return nullptr;
    }

    // 安全获取字节数组
    jbyte* data = env->GetByteArrayElements(content, nullptr);
    if (data == nullptr) {
        LOGE("nativeParseBugreport: GetByteArrayElements returned null (OOM?)");
        return nullptr;
    }

    // 限制文本大小（50MB），防止OOM
    size_t safeLength = static_cast<size_t>(contentLength);
    const size_t MAX_TEXT_SIZE = 50 * 1024 * 1024;
    if (safeLength > MAX_TEXT_SIZE) {
        LOGW("Text too large (%zu bytes), truncating to %zu", safeLength, MAX_TEXT_SIZE);
        safeLength = MAX_TEXT_SIZE;
    }

    std::string text(reinterpret_cast<char*>(data), safeLength);
    env->ReleaseByteArrayElements(content, data, JNI_ABORT);

    // 解析
    BatteryRawData raw_data;
    try {
        raw_data = BugreportParser::parseFromText(text);
    } catch (const std::exception& e) {
        LOGE("BugreportParser::parseFromText threw: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("BugreportParser::parseFromText threw unknown exception");
        return nullptr;
    }

    // 创建返回对象
    jclass resultClass = env->FindClass("com/batteryhealth/app/BatteryParseResult");
    if (resultClass == nullptr) {
        LOGE("Failed to find BatteryParseResult class");
        return nullptr;
    }

    jmethodID constructor = safeGetMethodID(env, resultClass, "<init>", "()V");
    if (constructor == nullptr) return nullptr;

    jobject result = env->NewObject(resultClass, constructor);
    if (result == nullptr) {
        LOGE("Failed to create BatteryParseResult object");
        return nullptr;
    }

    // 设置字段 - 每个字段都检查 fieldID
    if (raw_data.brand.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "brand", "Ljava/lang/String;");
        if (fid != nullptr) {
            env->SetObjectField(result, fid, env->NewStringUTF(raw_data.brand->c_str()));
        }
    }

    if (raw_data.model.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "model", "Ljava/lang/String;");
        if (fid != nullptr) {
            env->SetObjectField(result, fid, env->NewStringUTF(raw_data.model->c_str()));
        }
    }

    if (raw_data.design_capacity_mah.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "designCapacityMah", "I");
        if (fid != nullptr) {
            env->SetIntField(result, fid, raw_data.design_capacity_mah.value());
        }
    }

    if (raw_data.current_capacity_mah.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "currentCapacityMah", "I");
        if (fid != nullptr) {
            env->SetIntField(result, fid, raw_data.current_capacity_mah.value());
        }
    }

    if (raw_data.cycle_count.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "cycleCount", "I");
        if (fid != nullptr) {
            env->SetIntField(result, fid, raw_data.cycle_count.value());
        }
    }

    if (raw_data.temperature_celsius.has_value() && isValidFloat(raw_data.temperature_celsius.value())) {
        jfieldID fid = safeGetFieldID(env, resultClass, "temperatureCelsius", "F");
        if (fid != nullptr) {
            env->SetFloatField(result, fid, raw_data.temperature_celsius.value());
        }
    }

    if (raw_data.charge_counter_mah.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "chargeCounterMah", "I");
        if (fid != nullptr) {
            env->SetIntField(result, fid, raw_data.charge_counter_mah.value());
        }
    }

    if (raw_data.manufacturing_date.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "manufacturingDate", "Ljava/lang/String;");
        if (fid != nullptr) {
            env->SetObjectField(result, fid, env->NewStringUTF(raw_data.manufacturing_date->c_str()));
        }
    }

    if (raw_data.screen_on_time_hours.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "screenOnTimeHours", "I");
        if (fid != nullptr) {
            env->SetIntField(result, fid, raw_data.screen_on_time_hours.value());
        }
    }

    if (raw_data.charge_count.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "chargeCount", "I");
        if (fid != nullptr) {
            env->SetIntField(result, fid, raw_data.charge_count.value());
        }
    }

    // hasData
    jfieldID hasDataField = safeGetFieldID(env, resultClass, "hasData", "Z");
    if (hasDataField != nullptr) {
        bool hasData = raw_data.hasCapacityData() || raw_data.hasCycleData() ||
                       (raw_data.design_capacity_mah.has_value() && raw_data.design_capacity_mah.value() > 0);
        env->SetBooleanField(result, hasDataField, hasData ? JNI_TRUE : JNI_FALSE);
    }

    LOGI("Parse complete: hasCap=%d, hasCycle=%d, hasDesign=%d",
        raw_data.hasCapacityData(), raw_data.hasCycleData(),
        raw_data.design_capacity_mah.has_value());

    return result;
}

// 计算电池健康度
JNIEXPORT jobject JNICALL
Java_com_batteryhealth_app_BatteryAnalyzer_nativeCalculateHealth(
    JNIEnv* env,
    jobject thiz,
    jobject parseResult) {

    LOGI("nativeCalculateHealth called");

    if (parseResult == nullptr) {
        LOGE("nativeCalculateHealth: parseResult is null");
        return nullptr;
    }

    // 从 parseResult 获取数据
    jclass resultClass = env->GetObjectClass(parseResult);
    if (resultClass == nullptr) {
        LOGE("nativeCalculateHealth: GetObjectClass failed");
        return nullptr;
    }

    BatteryRawData raw_data;

    // brand
    jfieldID brandField = safeGetFieldID(env, resultClass, "brand", "Ljava/lang/String;");
    if (brandField != nullptr) {
        jstring brandStr = (jstring)env->GetObjectField(parseResult, brandField);
        if (brandStr != nullptr) {
            const char* brandChars = env->GetStringUTFChars(brandStr, nullptr);
            if (brandChars != nullptr) {
                raw_data.brand = std::string(brandChars);
                env->ReleaseStringUTFChars(brandStr, brandChars);
            }
        }
    }

    // designCapacityMah
    jfieldID designCapField = safeGetFieldID(env, resultClass, "designCapacityMah", "I");
    if (designCapField != nullptr) {
        jint designCap = env->GetIntField(parseResult, designCapField);
        if (designCap > 0) {
            raw_data.design_capacity_mah = designCap;
        }
    }

    // currentCapacityMah
    jfieldID currentCapField = safeGetFieldID(env, resultClass, "currentCapacityMah", "I");
    if (currentCapField != nullptr) {
        jint currentCap = env->GetIntField(parseResult, currentCapField);
        if (currentCap > 0) {
            raw_data.current_capacity_mah = currentCap;
        }
    }

    // cycleCount
    jfieldID cycleField = safeGetFieldID(env, resultClass, "cycleCount", "I");
    if (cycleField != nullptr) {
        jint cycles = env->GetIntField(parseResult, cycleField);
        if (cycles > 0) {
            raw_data.cycle_count = cycles;
        }
    }

    // temperatureCelsius
    jfieldID tempField = safeGetFieldID(env, resultClass, "temperatureCelsius", "F");
    if (tempField != nullptr) {
        jfloat temp = env->GetFloatField(parseResult, tempField);
        if (isValidFloat(temp) && temp > 0) {
            raw_data.temperature_celsius = temp;
        }
    }

    // chargeCounterMah
    jfieldID ccField = safeGetFieldID(env, resultClass, "chargeCounterMah", "I");
    if (ccField != nullptr) {
        jint cc = env->GetIntField(parseResult, ccField);
        if (cc > 0) {
            raw_data.charge_counter_mah = cc;
        }
    }

    // 计算健康度
    BatteryHealthResult health_result;
    try {
        health_result = BatteryHealthCalculator::calculate(raw_data);
    } catch (const std::exception& e) {
        LOGE("BatteryHealthCalculator::calculate threw: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("BatteryHealthCalculator::calculate threw unknown exception");
        return nullptr;
    }

    // 确保 health_percentage 是有效数值
    if (!isValidFloat(health_result.health_percentage)) {
        LOGW("health_percentage is invalid (%f), defaulting to 0", health_result.health_percentage);
        health_result.health_percentage = 0.0f;
    }

    LOGI("Health calculated: percentage=%.1f, grade=%s",
         health_result.health_percentage, health_result.grade.c_str());

    // 创建返回对象
    jclass healthClass = env->FindClass("com/batteryhealth/app/BatteryHealthResult");
    if (healthClass == nullptr) {
        LOGE("Failed to find BatteryHealthResult class");
        return nullptr;
    }

    jmethodID constructor = safeGetMethodID(env, healthClass, "<init>", "()V");
    if (constructor == nullptr) return nullptr;

    jobject result = env->NewObject(healthClass, constructor);
    if (result == nullptr) {
        LOGE("Failed to create BatteryHealthResult object");
        return nullptr;
    }

    // healthPercentage
    jfieldID percentageField = safeGetFieldID(env, healthClass, "healthPercentage", "F");
    if (percentageField != nullptr) {
        env->SetFloatField(result, percentageField, health_result.health_percentage);
    }

    // grade
    jfieldID gradeField = safeGetFieldID(env, healthClass, "grade", "Ljava/lang/String;");
    if (gradeField != nullptr) {
        env->SetObjectField(result, gradeField, env->NewStringUTF(health_result.grade.c_str()));
    }

    // gradeColor
    jfieldID colorField = safeGetFieldID(env, healthClass, "gradeColor", "Ljava/lang/String;");
    if (colorField != nullptr) {
        env->SetObjectField(result, colorField, env->NewStringUTF(health_result.getGradeColor().c_str()));
    }

    // gradeDescription
    jfieldID descField = safeGetFieldID(env, healthClass, "gradeDescription", "Ljava/lang/String;");
    if (descField != nullptr) {
        env->SetObjectField(result, descField, env->NewStringUTF(health_result.getGradeDescription().c_str()));
    }

    // diagnosisText
    jfieldID diagField = safeGetFieldID(env, healthClass, "diagnosisText", "Ljava/lang/String;");
    if (diagField != nullptr) {
        env->SetObjectField(result, diagField, env->NewStringUTF(health_result.diagnosis_text.c_str()));
    }

    // confidence
    jfieldID confField = safeGetFieldID(env, healthClass, "confidence", "I");
    if (confField != nullptr) {
        int confValue = static_cast<int>(health_result.confidence);
        env->SetIntField(result, confField, confValue);
    }

    // estimatedResistanceMohm
    if (health_result.estimated_resistance_mohm.has_value() && isValidFloat(health_result.estimated_resistance_mohm.value())) {
        jfieldID resField = safeGetFieldID(env, healthClass, "estimatedResistanceMohm", "F");
        if (resField != nullptr) {
            env->SetFloatField(result, resField, health_result.estimated_resistance_mohm.value());
        }
    }

    // remainingLifespanMonths
    if (health_result.remaining_lifespan_months.has_value()) {
        jfieldID lifespanField = safeGetFieldID(env, healthClass, "remainingLifespanMonths", "I");
        if (lifespanField != nullptr) {
            env->SetIntField(result, lifespanField, health_result.remaining_lifespan_months.value());
        }
    }

    // suggestions (List<String>)
    jfieldID suggestionsField = safeGetFieldID(env, healthClass, "suggestions", "Ljava/util/List;");
    if (suggestionsField != nullptr) {
        jclass listClass = env->FindClass("java/util/ArrayList");
        if (listClass != nullptr) {
            jmethodID listConstructor = safeGetMethodID(env, listClass, "<init>", "()V");
            if (listConstructor != nullptr) {
                jobject suggestionsList = env->NewObject(listClass, listConstructor);
                if (suggestionsList != nullptr) {
                    jmethodID addMethod = safeGetMethodID(env, listClass, "add", "(Ljava/lang/Object;)Z");
                    if (addMethod != nullptr) {
                        for (const auto& suggestion : health_result.suggestions) {
                            jstring jSuggestion = env->NewStringUTF(suggestion.c_str());
                            if (jSuggestion != nullptr) {
                                env->CallBooleanMethod(suggestionsList, addMethod, jSuggestion);
                                env->DeleteLocalRef(jSuggestion);
                            }
                        }
                    }
                    env->SetObjectField(result, suggestionsField, suggestionsList);
                    env->DeleteLocalRef(suggestionsList);
                }
            }
            env->DeleteLocalRef(listClass);
        }
    }

    // factors
    jclass factorsClass = env->FindClass("com/batteryhealth/app/HealthFactors");
    if (factorsClass != nullptr) {
        jmethodID factorsConstructor = safeGetMethodID(env, factorsClass, "<init>", "()V");
        if (factorsConstructor != nullptr) {
            jobject factorsObj = env->NewObject(factorsClass, factorsConstructor);
            if (factorsObj != nullptr) {
                if (health_result.factors.capacity_retention.has_value() && isValidFloat(health_result.factors.capacity_retention.value())) {
                    jfieldID f = safeGetFieldID(env, factorsClass, "capacityRetention", "F");
                    if (f != nullptr) env->SetFloatField(factorsObj, f, health_result.factors.capacity_retention.value() * 100);
                }
                if (health_result.factors.cycle_decay.has_value() && isValidFloat(health_result.factors.cycle_decay.value())) {
                    jfieldID f = safeGetFieldID(env, factorsClass, "cycleDecay", "F");
                    if (f != nullptr) env->SetFloatField(factorsObj, f, health_result.factors.cycle_decay.value() * 100);
                }
                if (health_result.factors.resistance_growth.has_value() && isValidFloat(health_result.factors.resistance_growth.value())) {
                    jfieldID f = safeGetFieldID(env, factorsClass, "resistanceGrowth", "F");
                    if (f != nullptr) env->SetFloatField(factorsObj, f, health_result.factors.resistance_growth.value() * 100);
                }
                if (health_result.factors.thermal_aging.has_value() && isValidFloat(health_result.factors.thermal_aging.value())) {
                    jfieldID f = safeGetFieldID(env, factorsClass, "thermalAging", "F");
                    if (f != nullptr) env->SetFloatField(factorsObj, f, health_result.factors.thermal_aging.value() * 100);
                }
                if (health_result.factors.charging_damage.has_value() && isValidFloat(health_result.factors.charging_damage.value())) {
                    jfieldID f = safeGetFieldID(env, factorsClass, "chargingDamage", "F");
                    if (f != nullptr) env->SetFloatField(factorsObj, f, health_result.factors.charging_damage.value() * 100);
                }
                jfieldID availField = safeGetFieldID(env, factorsClass, "availableFactors", "I");
                if (availField != nullptr) {
                    env->SetIntField(factorsObj, availField, health_result.factors.available_factors);
                }

                jfieldID factorsField = safeGetFieldID(env, healthClass, "factors", "Lcom/batteryhealth/app/HealthFactors;");
                if (factorsField != nullptr) {
                    env->SetObjectField(result, factorsField, factorsObj);
                }
                env->DeleteLocalRef(factorsObj);
            }
        }
        env->DeleteLocalRef(factorsClass);
    }

    return result;
}

// 解析 ZIP 文件
JNIEXPORT jobject JNICALL
Java_com_batteryhealth_app_BatteryAnalyzer_nativeParseZipFile(
    JNIEnv* env,
    jobject thiz,
    jstring filePath) {

    LOGI("nativeParseZipFile called");

    if (filePath == nullptr) {
        LOGE("nativeParseZipFile: filePath is null");
        return nullptr;
    }

    const char* pathChars = env->GetStringUTFChars(filePath, nullptr);
    if (pathChars == nullptr) {
        LOGE("nativeParseZipFile: GetStringUTFChars returned null");
        return nullptr;
    }
    std::string path(pathChars);
    env->ReleaseStringUTFChars(filePath, pathChars);

    // 解析 ZIP
    BatteryRawData raw_data;
    try {
        raw_data = BugreportParser::parseFromZip(path);
    } catch (const std::exception& e) {
        LOGE("BugreportParser::parseFromZip threw: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("BugreportParser::parseFromZip threw unknown exception");
        return nullptr;
    }

    // 创建返回对象
    jclass resultClass = env->FindClass("com/batteryhealth/app/BatteryParseResult");
    if (resultClass == nullptr) {
        LOGE("Failed to find BatteryParseResult class");
        return nullptr;
    }

    jmethodID constructor = safeGetMethodID(env, resultClass, "<init>", "()V");
    if (constructor == nullptr) return nullptr;

    jobject result = env->NewObject(resultClass, constructor);
    if (result == nullptr) return nullptr;

    // 设置字段 (同 parseBugreport)
    if (raw_data.brand.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "brand", "Ljava/lang/String;");
        if (fid != nullptr) env->SetObjectField(result, fid, env->NewStringUTF(raw_data.brand->c_str()));
    }

    if (raw_data.model.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "model", "Ljava/lang/String;");
        if (fid != nullptr) env->SetObjectField(result, fid, env->NewStringUTF(raw_data.model->c_str()));
    }

    if (raw_data.design_capacity_mah.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "designCapacityMah", "I");
        if (fid != nullptr) env->SetIntField(result, fid, raw_data.design_capacity_mah.value());
    }

    if (raw_data.current_capacity_mah.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "currentCapacityMah", "I");
        if (fid != nullptr) env->SetIntField(result, fid, raw_data.current_capacity_mah.value());
    }

    if (raw_data.cycle_count.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "cycleCount", "I");
        if (fid != nullptr) env->SetIntField(result, fid, raw_data.cycle_count.value());
    }

    if (raw_data.temperature_celsius.has_value() && isValidFloat(raw_data.temperature_celsius.value())) {
        jfieldID fid = safeGetFieldID(env, resultClass, "temperatureCelsius", "F");
        if (fid != nullptr) env->SetFloatField(result, fid, raw_data.temperature_celsius.value());
    }

    if (raw_data.charge_counter_mah.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "chargeCounterMah", "I");
        if (fid != nullptr) env->SetIntField(result, fid, raw_data.charge_counter_mah.value());
    }

    if (raw_data.manufacturing_date.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "manufacturingDate", "Ljava/lang/String;");
        if (fid != nullptr) env->SetObjectField(result, fid, env->NewStringUTF(raw_data.manufacturing_date->c_str()));
    }

    if (raw_data.screen_on_time_hours.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "screenOnTimeHours", "I");
        if (fid != nullptr) env->SetIntField(result, fid, raw_data.screen_on_time_hours.value());
    }

    if (raw_data.charge_count.has_value()) {
        jfieldID fid = safeGetFieldID(env, resultClass, "chargeCount", "I");
        if (fid != nullptr) env->SetIntField(result, fid, raw_data.charge_count.value());
    }

    jfieldID hasDataField = safeGetFieldID(env, resultClass, "hasData", "Z");
    if (hasDataField != nullptr) {
        bool hasData = raw_data.hasCapacityData() || raw_data.hasCycleData() ||
                       (raw_data.design_capacity_mah.has_value() && raw_data.design_capacity_mah.value() > 0);
        env->SetBooleanField(result, hasDataField, hasData ? JNI_TRUE : JNI_FALSE);
    }

    return result;
}

// 获取解析摘要
JNIEXPORT jstring JNICALL
Java_com_batteryhealth_app_BatteryAnalyzer_nativeGetParseSummary(
    JNIEnv* env,
    jobject thiz,
    jobject parseResult) {

    if (parseResult == nullptr) {
        return env->NewStringUTF("无效输入");
    }

    jclass resultClass = env->GetObjectClass(parseResult);
    if (resultClass == nullptr) {
        return env->NewStringUTF("获取类信息失败");
    }

    BatteryRawData raw_data;

    jfieldID designCapField = safeGetFieldID(env, resultClass, "designCapacityMah", "I");
    if (designCapField != nullptr) {
        jint designCap = env->GetIntField(parseResult, designCapField);
        if (designCap > 0) raw_data.design_capacity_mah = designCap;
    }

    jfieldID currentCapField = safeGetFieldID(env, resultClass, "currentCapacityMah", "I");
    if (currentCapField != nullptr) {
        jint currentCap = env->GetIntField(parseResult, currentCapField);
        if (currentCap > 0) raw_data.current_capacity_mah = currentCap;
    }

    jfieldID cycleField = safeGetFieldID(env, resultClass, "cycleCount", "I");
    if (cycleField != nullptr) {
        jint cycles = env->GetIntField(parseResult, cycleField);
        if (cycles > 0) raw_data.cycle_count = cycles;
    }

    jfieldID tempField = safeGetFieldID(env, resultClass, "temperatureCelsius", "F");
    if (tempField != nullptr) {
        jfloat temp = env->GetFloatField(parseResult, tempField);
        if (isValidFloat(temp) && temp > 0) raw_data.temperature_celsius = temp;
    }

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
