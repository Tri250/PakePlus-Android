package com.digiguide.service

import com.digiguide.model.BatteryRawData
import com.digiguide.model.BatteryHealthResult
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * API客户端配置 - 完善实现
 * 包含网络状态监控、降级策略、超时处理
 */
object ApiClient {

    // API基础URL（可配置）
    private var baseUrl = "https://api.digiguide.com/v1/"
    private const val DEFAULT_TIMEOUT_SECONDS = 30L
    private const val MAX_RETRIES = 3

    private lateinit var retrofit: Retrofit
    private lateinit var okHttpClient: OkHttpClient

    private val isInitialized = AtomicBoolean(false)
    private val isNetworkAvailable = AtomicBoolean(true)
    private val isApiAvailable = AtomicBoolean(true)

    // API状态监控
    private var lastSuccessTime = 0L
    private var consecutiveFailures = 0
    private const val FAILURE_THRESHOLD = 3
    private const val COOLDOWN_MS = 60_000L // 1分钟冷却

    fun init() {
        if (isInitialized.get()) return

        // 创建日志拦截器
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // 创建状态监控拦截器
        val statusInterceptor = Interceptor { chain ->
            val request = chain.request()

            // 检查是否处于冷却期
            if (!isApiAvailable.get()) {
                val cooldownRemaining = COOLDOWN_MS - (System.currentTimeMillis() - lastSuccessTime)
                if (cooldownRemaining > 0) {
                    // API不可用，返回模拟响应或抛出异常
                    throw ApiUnavailableException("API暂时不可用，请稍后重试")
                } else {
                    // 冷却期结束，重试
                    isApiAvailable.set(true)
                    consecutiveFailures = 0
                }
            }

            try {
                val response = chain.proceed(request)

                if (response.isSuccessful) {
                    lastSuccessTime = System.currentTimeMillis()
                    consecutiveFailures = 0
                    isApiAvailable.set(true)
                } else {
                    consecutiveFailures++
                    if (consecutiveFailures >= FAILURE_THRESHOLD) {
                        isApiAvailable.set(false)
                    }
                }

                response
            } catch (e: Exception) {
                consecutiveFailures++
                if (consecutiveFailures >= FAILURE_THRESHOLD) {
                    isApiAvailable.set(false)
                }
                throw e
            }
        }

        // 创建重试拦截器
        val retryInterceptor = Interceptor { chain ->
            var request = chain.request()
            var response: okhttp3.Response? = null
            var exception: Exception? = null

            for (i in 0 until MAX_RETRIES) {
                try {
                    response = chain.proceed(request)
                    if (response.isSuccessful) {
                        return response
                    }
                    // 非2xx响应不重试
                    break
                } catch (e: Exception) {
                    exception = e
                    if (i < MAX_RETRIES - 1) {
                        // 等待后重试
                        Thread.sleep(1000L * (i + 1))
                    }
                }
            }

            // 返回最后的响应或抛出异常
            response ?: throw exception ?: RuntimeException("Unknown error")
        }

        // 创建OkHttpClient
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(statusInterceptor)
            .addInterceptor(retryInterceptor)
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        // 创建Retrofit
        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        isInitialized.set(true)
    }

    /**
     * 获取Retrofit实例
     */
    fun getRetrofit(): Retrofit {
        if (!isInitialized.get()) init()
        return retrofit
    }

    /**
     * 获取OkHttpClient实例
     */
    fun getOkHttpClient(): OkHttpClient {
        if (!isInitialized.get()) init()
        return okHttpClient
    }

    /**
     * 创建API服务
     */
    fun <T> createService(serviceClass: Class<T>): T {
        return getRetrofit().create(serviceClass)
    }

    /**
     * 更新Base URL
     */
    fun updateBaseUrl(newBaseUrl: String) {
        baseUrl = newBaseUrl
        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * 检查API是否可用
     */
    fun isApiAvailable(): Boolean {
        return isApiAvailable.get()
    }

    /**
     * 检查网络是否可用
     */
    fun isNetworkAvailable(): Boolean {
        return isNetworkAvailable.get()
    }

    /**
     * 设置网络状态
     */
    fun setNetworkAvailable(available: Boolean) {
        isNetworkAvailable.set(available)
    }

    /**
     * 获取API状态信息
     */
    fun getApiStatus(): ApiStatus {
        return ApiStatus(
            isAvailable = isApiAvailable.get(),
            lastSuccessTime = lastSuccessTime,
            consecutiveFailures = consecutiveFailures,
            cooldownRemaining = if (!isApiAvailable.get()) {
                COOLDOWN_MS - (System.currentTimeMillis() - lastSuccessTime)
            } else 0L
        )
    }

    /**
     * 重置API状态
     */
    fun resetApiStatus() {
        isApiAvailable.set(true)
        consecutiveFailures = 0
        lastSuccessTime = System.currentTimeMillis()
    }

    /**
     * API状态信息
     */
    data class ApiStatus(
        val isAvailable: Boolean,
        val lastSuccessTime: Long,
        val consecutiveFailures: Int,
        val cooldownRemaining: Long
    )

    /**
     * API不可用异常
     */
    class ApiUnavailableException(message: String) : Exception(message)
}