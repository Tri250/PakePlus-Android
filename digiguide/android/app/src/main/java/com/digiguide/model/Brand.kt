package com.digiguide.model

/**
 * 品牌枚举
 */
enum class Brand {
    XIAOMI,
    HUAWEI,
    OPPO,
    VIVO,
    APPLE,
    HONOR,
    SAMSUNG,
    LENOVO,
    HP,
    ASUS,
    DELL,
    APPLE_MAC,
    UNKNOWN;

    fun toChinese(): String {
        return when (this) {
            XIAOMI -> "小米"
            HUAWEI -> "华为"
            OPPO -> "OPPO"
            VIVO -> "vivo"
            APPLE -> "苹果"
            HONOR -> "荣耀"
            SAMSUNG -> "三星"
            LENOVO -> "联想"
            HP -> "惠普"
            ASUS -> "华硕"
            DELL -> "戴尔"
            APPLE_MAC -> "苹果电脑"
            UNKNOWN -> "未知"
        }
    }

    fun toEnglish(): String {
        return name
    }
}