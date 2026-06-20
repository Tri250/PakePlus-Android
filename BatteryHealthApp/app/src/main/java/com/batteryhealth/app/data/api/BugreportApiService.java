package com.batteryhealth.app.data.api;

import com.batteryhealth.app.data.model.BugreportUploadResponse;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

/**
 * Bugreport 上传接口
 * 对应 digiguide /v1/battery/upload 端点。
 */
public interface BugreportApiService {

    @Multipart
    @POST("v1/battery/upload")
    Call<BugreportUploadResponse> uploadBugreport(
            @Part MultipartBody.Part file,
            @Part("brand") RequestBody brand,
            @Part("model") RequestBody model
    );
}
