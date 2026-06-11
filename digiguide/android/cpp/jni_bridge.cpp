#include <jni.h>
#include <string>
#include <optional>
#include <vector>
#include <map>

// 引用C++ Core引擎头文件
#include "sn_decoder.h"
#include "bugreport_parser.h"
#include "battery_health.h"
#include "result_types.h"

using namespace digiguide::core;

extern "C" {

// ========== SN解码JNI接口 ==========

JNIEXPORT jobject JNICALL
Java_com_digiguide_core_CoreBridge_nativeDecodeSN(
    JNIEnv* env,
    jclass clazz,
    jstring sn) {

    const char* sn_str = env->GetStringUTFChars(sn, nullptr);
    std::string sn_cpp(sn_str);
    env->ReleaseStringUTFChars(sn, sn_str);

    SNDecodeResult result = SNDecoder::decode(sn_cpp);

    // 创建Java SNDecodeResult对象
    jclass resultClass = env->FindClass("com/digiguide/model/SNDecodeResult");
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject resultObj = env->NewObject(resultClass, constructor);

    // 设置字段
    jfieldID brandField = env->GetFieldID(resultClass, "brand", "Lcom/digiguide/model/Brand;");
    jfieldID rawSnField = env->GetFieldID(resultClass, "rawSn", "Ljava/lang/String;");
    jfieldID factoryYearField = env->GetFieldID(resultClass, "factoryYear", "Ljava/lang/Integer;");
    jfieldID factoryMonthField = env->GetFieldID(resultClass, "factoryMonth", "Ljava/lang/Integer;");
    jfieldID factoryWeekField = env->GetFieldID(resultClass, "factoryWeek", "Ljava/lang/Integer;");
    jfieldID halfYearField = env->GetFieldID(resultClass, "halfYear", "Ljava/lang/String;");
    jfieldID statusField = env->GetFieldID(resultClass, "status", "Lcom/digiguide/model/SNDecodeStatus;");
    jfieldID errorMessageField = env->GetFieldID(resultClass, "errorMessage", "Ljava/lang/String;");

    // 设置品牌
    jclass brandClass = env->FindClass("com/digiguide/model/Brand");
    jmethodID valuesMethod = env->GetStaticMethodID(brandClass, "values", "()[Lcom/digiguide/model/Brand;");
    jobjectArray brands = (jobjectArray)env->CallStaticObjectMethod(brandClass, valuesMethod);
    jobject brandObj = env->GetObjectArrayElement(brands, static_cast<int>(result.brand));
    env->SetObjectField(resultObj, brandField, brandObj);

    // 设置原始SN
    env->SetObjectField(resultObj, rawSnField, env->NewStringUTF(result.raw_sn.c_str()));

    // 设置年份
    if (result.factory_year.has_value()) {
        jclass integerClass = env->FindClass("java/lang/Integer");
        jmethodID valueOfMethod = env->GetStaticMethodID(integerClass, "valueOf", "(I)Ljava/lang/Integer;");
        jobject yearObj = env->CallStaticObjectMethod(integerClass, valueOfMethod, result.factory_year.value());
        env->SetObjectField(resultObj, factoryYearField, yearObj);
    }

    // 设置月份
    if (result.factory_month.has_value()) {
        jclass integerClass = env->FindClass("java/lang/Integer");
        jmethodID valueOfMethod = env->GetStaticMethodID(integerClass, "valueOf", "(I)Ljava/lang/Integer;");
        jobject monthObj = env->CallStaticObjectMethod(integerClass, valueOfMethod, result.factory_month.value());
        env->SetObjectField(resultObj, factoryMonthField, monthObj);
    }

    // 设置周次
    if (result.factory_week.has_value()) {
        jclass integerClass = env->FindClass("java/lang/Integer");
        jmethodID valueOfMethod = env->GetStaticMethodID(integerClass, "valueOf", "(I)Ljava/lang/Integer;");
        jobject weekObj = env->CallStaticObjectMethod(integerClass, valueOfMethod, result.factory_week.value());
        env->SetObjectField(resultObj, factoryWeekField, weekObj);
    }

    // 设置半年
    if (result.half_year.has_value()) {
        env->SetObjectField(resultObj, halfYearField, env->NewStringUTF(result.half_year.value().c_str()));
    }

    // 设置状态
    jclass statusClass = env->FindClass("com/digiguide/model/SNDecodeStatus");
    jmethodID statusValuesMethod = env->GetStaticMethodID(statusClass, "values", "()[Lcom/digiguide/model/SNDecodeStatus;");
    jobjectArray statuses = (jobjectArray)env->CallStaticObjectMethod(statusClass, statusValuesMethod);
    jobject statusObj = env->GetObjectArrayElement(statuses, static_cast<int>(result.status));
    env->SetObjectField(resultObj, statusField, statusObj);

    // 设置错误信息
    env->SetObjectField(resultObj, errorMessageField, env->NewStringUTF(result.error_message.c_str()));

    return resultObj;
}

JNIEXPORT jobject JNICALL
Java_com_digiguide_core_CoreBridge_nativeDecodeSNWithBrand(
    JNIEnv* env,
    jclass clazz,
    jstring sn,
    jint brandOrdinal) {

    const char* sn_str = env->GetStringUTFChars(sn, nullptr);
    std::string sn_cpp(sn_str);
    env->ReleaseStringUTFChars(sn, sn_str);

    Brand brand = static_cast<Brand>(brandOrdinal);
    SNDecodeResult result = SNDecoder::decode(sn_cpp, brand);

    // 使用与nativeDecodeSN相同的逻辑创建返回对象
    // ... (简化实现，实际应复用代码)
    jclass resultClass = env->FindClass("com/digiguide/model/SNDecodeResult");
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject resultObj = env->NewObject(resultClass, constructor);

    return resultObj;
}

JNIEXPORT jboolean JNICALL
Java_com_digiguide_core_CoreBridge_nativeValidateFormat(
    JNIEnv* env,
    jclass clazz,
    jstring sn,
    jint brandOrdinal) {

    const char* sn_str = env->GetStringUTFChars(sn, nullptr);
    std::string sn_cpp(sn_str);
    env->ReleaseStringUTFChars(sn, sn_str);

    Brand brand = static_cast<Brand>(brandOrdinal);
    bool valid = SNDecoder::validateFormat(sn_cpp, brand);

    return valid ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_digiguide_core_CoreBridge_nativeGetFormatHint(
    JNIEnv* env,
    jclass clazz,
    jint brandOrdinal) {

    Brand brand = static_cast<Brand>(brandOrdinal);
    std::string hint = SNDecoder::getFormatHint(brand);

    return env->NewStringUTF(hint.c_str());
}

// ========== Bugreport解析JNI接口 ==========

JNIEXPORT jobject JNICALL
Java_com_digiguide_core_CoreBridge_nativeParseBugreport(
    JNIEnv* env,
    jclass clazz,
    jstring text) {

    const char* text_str = env->GetStringUTFChars(text, nullptr);
    std::string text_cpp(text_str);
    env->ReleaseStringUTFChars(text, text_str);

    BatteryRawData data = BugreportParser::parseFromText(text_cpp);

    // 创建Java BatteryRawData对象
    jclass dataClass = env->FindClass("com/digiguide/model/BatteryRawData");
    jmethodID constructor = env->GetMethodID(dataClass, "<init>", "()V");
    jobject dataObj = env->NewObject(dataClass, constructor);

    // 设置字段
    jfieldID brandField = env->GetFieldID(dataClass, "brand", "Ljava/lang/String;");
    jfieldID modelField = env->GetFieldID(dataClass, "model", "Ljava/lang/String;");
    jfieldID designCapacityField = env->GetFieldID(dataClass, "designCapacityMah", "Ljava/lang/Integer;");
    jfieldID currentCapacityField = env->GetFieldID(dataClass, "currentCapacityMah", "Ljava/lang/Integer;");
    jfieldID cycleCountField = env->GetFieldID(dataClass, "cycleCount", "Ljava/lang/Integer;");
    jfieldID temperatureField = env->GetFieldID(dataClass, "temperatureCelsius", "Ljava/lang/Float;");

    // 设置品牌
    if (data.brand.has_value()) {
        env->SetObjectField(dataObj, brandField, env->NewStringUTF(data.brand.value().c_str()));
    }

    // 设置型号
    if (data.model.has_value()) {
        env->SetObjectField(dataObj, modelField, env->NewStringUTF(data.model.value().c_str()));
    }

    // 设置设计容量
    if (data.design_capacity_mah.has_value()) {
        jclass integerClass = env->FindClass("java/lang/Integer");
        jmethodID valueOfMethod = env->GetStaticMethodID(integerClass, "valueOf", "(I)Ljava/lang/Integer;");
        jobject capacityObj = env->CallStaticObjectMethod(integerClass, valueOfMethod, data.design_capacity_mah.value());
        env->SetObjectField(dataObj, designCapacityField, capacityObj);
    }

    // 设置当前容量
    if (data.current_capacity_mah.has_value()) {
        jclass integerClass = env->FindClass("java/lang/Integer");
        jmethodID valueOfMethod = env->GetStaticMethodID(integerClass, "valueOf", "(I)Ljava/lang/Integer;");
        jobject capacityObj = env->CallStaticObjectMethod(integerClass, valueOfMethod, data.current_capacity_mah.value());
        env->SetObjectField(dataObj, currentCapacityField, capacityObj);
    }

    // 设置循环次数
    if (data.cycle_count.has_value()) {
        jclass integerClass = env->FindClass("java/lang/Integer");
        jmethodID valueOfMethod = env->GetStaticMethodID(integerClass, "valueOf", "(I)Ljava/lang/Integer;");
        jobject cycleObj = env->CallStaticObjectMethod(integerClass, valueOfMethod, data.cycle_count.value());
        env->SetObjectField(dataObj, cycleCountField, cycleObj);
    }

    // 设置温度
    if (data.temperature_celsius.has_value()) {
        jclass floatClass = env->FindClass("java/lang/Float");
        jmethodID valueOfMethod = env->GetStaticMethodID(floatClass, "valueOf", "(F)Ljava/lang/Float;");
        jobject tempObj = env->CallStaticObjectMethod(floatClass, valueOfMethod, data.temperature_celsius.value());
        env->SetObjectField(dataObj, temperatureField, tempObj);
    }

    return dataObj;
}

// ========== 健康度计算JNI接口 ==========

JNIEXPORT jobject JNICALL
Java_com_digiguide_core_CoreBridge_nativeCalculateHealth(
    JNIEnv* env,
    jclass clazz,
    jobject rawDataObj) {

    // 从Java对象提取数据
    jclass dataClass = env->FindClass("com/digiguide/model/BatteryRawData");

    BatteryRawData raw_data;

    // 获取品牌
    jfieldID brandField = env->GetFieldID(dataClass, "brand", "Ljava/lang/String;");
    jstring brandStr = (jstring)env->GetObjectField(rawDataObj, brandField);
    if (brandStr != nullptr) {
        const char* brand_cstr = env->GetStringUTFChars(brandStr, nullptr);
        raw_data.brand = std::string(brand_cstr);
        env->ReleaseStringUTFChars(brandStr, brand_cstr);
    }

    // 获取设计容量
    jfieldID designCapacityField = env->GetFieldID(dataClass, "designCapacityMah", "Ljava/lang/Integer;");
    jobject designCapacityObj = env->GetObjectField(rawDataObj, designCapacityField);
    if (designCapacityObj != nullptr) {
        jclass integerClass = env->FindClass("java/lang/Integer");
        jmethodID intValueMethod = env->GetMethodID(integerClass, "intValue", "()I");
        raw_data.design_capacity_mah = env->CallIntMethod(designCapacityObj, intValueMethod);
    }

    // 获取当前容量
    jfieldID currentCapacityField = env->GetFieldID(dataClass, "currentCapacityMah", "Ljava/lang/Integer;");
    jobject currentCapacityObj = env->GetObjectField(rawDataObj, currentCapacityField);
    if (currentCapacityObj != nullptr) {
        jclass integerClass = env->FindClass("java/lang/Integer");
        jmethodID intValueMethod = env->GetMethodID(integerClass, "intValue", "()I");
        raw_data.current_capacity_mah = env->CallIntMethod(currentCapacityObj, intValueMethod);
    }

    // 获取循环次数
    jfieldID cycleCountField = env->GetFieldID(dataClass, "cycleCount", "Ljava/lang/Integer;");
    jobject cycleCountObj = env->GetObjectField(rawDataObj, cycleCountField);
    if (cycleCountObj != nullptr) {
        jclass integerClass = env->FindClass("java/lang/Integer");
        jmethodID intValueMethod = env->GetMethodID(integerClass, "intValue", "()I");
        raw_data.cycle_count = env->CallIntMethod(cycleCountObj, intValueMethod);
    }

    // 获取温度
    jfieldID temperatureField = env->GetFieldID(dataClass, "temperatureCelsius", "Ljava/lang/Float;");
    jobject temperatureObj = env->GetObjectField(rawDataObj, temperatureField);
    if (temperatureObj != nullptr) {
        jclass floatClass = env->FindClass("java/lang/Float");
        jmethodID floatValueMethod = env->GetMethodID(floatClass, "floatValue", "()F");
        raw_data.temperature_celsius = env->CallFloatMethod(temperatureObj, floatValueMethod);
    }

    // 计算健康度
    BatteryHealthResult result = BatteryHealthCalculator::calculate(raw_data);

    // 创建Java BatteryHealthResult对象
    jclass resultClass = env->FindClass("com/digiguide/model/BatteryHealthResult");
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "()V");
    jobject resultObj = env->NewObject(resultClass, constructor);

    // 设置字段
    jfieldID healthPercentageField = env->GetFieldID(resultClass, "healthPercentage", "F");
    jfieldID gradeField = env->GetFieldID(resultClass, "grade", "Ljava/lang/String;");
    jfieldID capacityRetentionField = env->GetFieldID(resultClass, "capacityRetention", "Ljava/lang/Float;");
    jfieldID cycleDecayField = env->GetFieldID(resultClass, "cycleDecay", "Ljava/lang/Float;");
    jfieldID diagnosisTextField = env->GetFieldID(resultClass, "diagnosisText", "Ljava/lang/String;");

    // 设置健康度百分比
    env->SetFloatField(resultObj, healthPercentageField, result.health_percentage);

    // 设置等级
    env->SetObjectField(resultObj, gradeField, env->NewStringUTF(result.grade.c_str()));

    // 设置容量保持率
    if (result.factors.capacity_retention.has_value()) {
        jclass floatClass = env->FindClass("java/lang/Float");
        jmethodID valueOfMethod = env->GetStaticMethodID(floatClass, "valueOf", "(F)Ljava/lang/Float;");
        jobject retentionObj = env->CallStaticObjectMethod(floatClass, valueOfMethod, result.factors.capacity_retention.value());
        env->SetObjectField(resultObj, capacityRetentionField, retentionObj);
    }

    // 设置循环衰减
    if (result.factors.cycle_decay.has_value()) {
        jclass floatClass = env->FindClass("java/lang/Float");
        jmethodID valueOfMethod = env->GetStaticMethodID(floatClass, "valueOf", "(F)Ljava/lang/Float;");
        jobject decayObj = env->CallStaticObjectMethod(floatClass, valueOfMethod, result.factors.cycle_decay.value());
        env->SetObjectField(resultObj, cycleDecayField, decayObj);
    }

    // 设置诊断文字
    env->SetObjectField(resultObj, diagnosisTextField, env->NewStringUTF(result.diagnosis_text.c_str()));

    return resultObj;
}

} // extern "C"