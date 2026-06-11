package com.digiguide.service

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * API客户端配置
 */
object ApiClient {

    private const val BASE_URL = "https://api.digiguide.com/v1/"
    private const val TIMEOUT_SECONDS = 30L

    private lateinit var retrofit: Retrofit
    private lateinit var okHttpClient: OkHttpClient

    private var isInitialized = false

    fun init() {
        if (isInitialized) return

        // 创建日志拦截器
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // 创建OkHttpClient
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        // 创建Retrofit
        retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        isInitialized = true
    }

    /**
     * 获取Retrofit实例
     */
    fun getRetrofit(): Retrofit {
        if (!isInitialized) init()
        return retrofit
    }

    /**
     * 获取OkHttpClient实例
     */
    fun getOkHttpClient(): OkHttpClient {
        if (!isInitialized) init()
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
        retrofit = Retrofit.Builder()
            .baseUrl(newBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}