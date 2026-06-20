package com.batteryhealth.app.data.api;

import android.content.Context;

import com.batteryhealth.app.utils.NetworkConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit 客户端单例
 * 提供统一的 HTTP 客户端配置与实例管理。
 */
public class ApiClient {
    private static Retrofit retrofit;

    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            Context appContext = context.getApplicationContext();
            if (!NetworkConfig.isBaseUrlConfigured(appContext)) {
                throw new IllegalStateException("后端服务地址未配置，请先设置合法地址");
            }

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(NetworkConfig.getBaseUrl(appContext))
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return retrofit;
    }

    public static void reset() {
        retrofit = null;
    }
}
