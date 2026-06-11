package com.digiguide.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 安全加密工具
 * 用于敏感数据的加密存储
 * 使用Android Keystore保护密钥
 */
object SecurityUtils {

    private const val KEY_ALIAS = "digiguide_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128

    /**
     * 初始化加密密钥
     * 仅在API 23+可用
     */
    fun initKey(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return  // API 23以下不支持Keystore
        }

        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )

                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()

                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            // 密钥初始化失败，使用普通存储
        }
    }

    /**
     * 加密数据
     */
    fun encrypt(data: String): EncryptedData? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return null  // API 23以下不支持
        }

        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv

            return EncryptedData(
                encryptedData = encryptedData,
                iv = iv
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 解密数据
     */
    fun decrypt(encryptedData: EncryptedData): String? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return null
        }

        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)

            val spec = GCMParameterSpec(TAG_LENGTH, encryptedData.iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedData = cipher.doFinal(encryptedData.encryptedData)
            return String(decryptedData, Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 加密数据结构
     */
    data class EncryptedData(
        val encryptedData: ByteArray,
        val iv: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EncryptedData

            if (!encryptedData.contentEquals(other.encryptedData)) return false
            if (!iv.contentEquals(other.iv)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = encryptedData.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            return result
        }
    }
}

/**
 * 隐私合规检查工具
 */
object PrivacyUtils {

    /**
     * 检查是否需要用户同意隐私政策
     */
    fun needsPrivacyConsent(context: Context): Boolean {
        val prefs = context.getSharedPreferences("privacy", Context.MODE_PRIVATE)
        return !prefs.getBoolean("consent_given", false)
    }

    /**
     * 记录用户已同意隐私政策
     */
    fun setPrivacyConsent(context: Context) {
        val prefs = context.getSharedPreferences("privacy", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("consent_given", true).apply()
    }

    /**
     * 获取隐私政策版本
     */
    fun getPrivacyPolicyVersion(context: Context): Int {
        val prefs = context.getSharedPreferences("privacy", Context.MODE_PRIVATE)
        return prefs.getInt("policy_version", 0)
    }

    /**
     * 设置隐私政策版本
     */
    fun setPrivacyPolicyVersion(context: Context, version: Int) {
        val prefs = context.getSharedPreferences("privacy", Context.MODE_PRIVATE)
        prefs.edit().putInt("policy_version", version).apply()
    }

    /**
     * 清除所有用户数据（用于用户请求删除数据）
     */
    fun clearAllUserData(context: Context) {
        // 清除SharedPreferences
        val prefs = context.getSharedPreferences("privacy", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        // 清除数据库
        com.digiguide.db.AppDatabase.getDatabase(context).queryHistoryDao().deleteAll()
        com.digiguide.db.AppDatabase.getDatabase(context).batteryReportDao().deleteAll()

        // 清除缓存
        context.cacheDir.deleteRecursively()
    }
}

/**
 * 数据验证工具
 * 用于输入验证和边界检查
 */
object ValidationUtils {

    /**
     * 验证SN格式
     */
    fun validateSN(sn: String): ValidationResult {
        if (sn.isEmpty()) {
            return ValidationResult.Error("序列号不能为空")
        }

        if (sn.length < 5) {
            return ValidationResult.Error("序列号长度不足")
        }

        if (sn.length > 20) {
            return ValidationResult.Error("序列号长度过长")
        }

        // 检查是否包含非法字符
        val validPattern = Regex("[A-Za-z0-9]+")
        if (!validPattern.matches(sn)) {
            return ValidationResult.Error("序列号包含非法字符")
        }

        return ValidationResult.Success
    }

    /**
     * 验证文件大小
     */
    fun validateFileSize(bytes: Long, maxSizeBytes: Long): ValidationResult {
        if (bytes <= 0) {
            return ValidationResult.Error("文件无效")
        }

        if (bytes > maxSizeBytes) {
            val maxSizeMB = maxSizeBytes / (1024 * 1024)
            return ValidationResult.Error("文件过大，超过${maxSizeMB}MB限制")
        }

        return ValidationResult.Success
    }

    /**
     * 验证健康度数值范围
     */
    fun validateHealthPercentage(percentage: Float): ValidationResult {
        if (percentage < 0 || percentage > 100) {
            return ValidationResult.Error("健康度数值超出范围")
        }

        return ValidationResult.Success
    }

    /**
     * 验证循环次数范围
     */
    fun validateCycleCount(cycles: Int): ValidationResult {
        if (cycles < 0) {
            return ValidationResult.Error("循环次数不能为负数")
        }

        if (cycles > 2000) {
            return ValidationResult.Warning("循环次数异常高，数据可能不准确")
        }

        return ValidationResult.Success
    }

    /**
     * 验证温度范围
     */
    fun validateTemperature(temp: Float): ValidationResult {
        if (temp < -20 || temp > 60) {
            return ValidationResult.Warning("温度值异常，数据可能不准确")
        }

        return ValidationResult.Success
    }

    /**
     * 验证结果
     */
    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Warning(val message: String) : ValidationResult()
        data class Error(val message: String) : ValidationResult()

        fun isSuccess(): Boolean = this is Success
        fun isWarning(): Boolean = this is Warning
        fun isError(): Boolean = this is Error
    }
}