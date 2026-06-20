package com.batteryhealth.app.data.repository;

import android.content.Context;

import com.batteryhealth.app.data.api.ApiClient;

import java.util.Locale;
import com.batteryhealth.app.data.api.BugreportApiService;
import com.batteryhealth.app.data.model.BugreportUploadResponse;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Callback;

/**
 * Bugreport 上传仓库
 * 封装文件选择后的实际上传逻辑。
 */
public class BugreportRepository {
    private final BugreportApiService apiService;

    public BugreportRepository(Context context) {
        this.apiService = ApiClient.getClient(context).create(BugreportApiService.class);
    }

    public void uploadBugreport(File file, String brand, String model,
                                Callback<BugreportUploadResponse> callback) {
        String contentType = getContentType(file);
        RequestBody requestFile = RequestBody.create(MediaType.parse(contentType), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);
        RequestBody brandBody = RequestBody.create(MediaType.parse("text/plain"), brand != null ? brand : "");
        RequestBody modelBody = RequestBody.create(MediaType.parse("text/plain"), model != null ? model : "");

        apiService.uploadBugreport(body, brandBody, modelBody).enqueue(callback);
    }

    private String getContentType(File file) {
        if (file == null) return "application/octet-stream";
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) {
            return "application/zip";
        } else if (name.endsWith(".txt")) {
            return "text/plain";
        } else if (name.endsWith(".gz") || name.endsWith(".tar.gz")) {
            return "application/gzip";
        }
        return "application/octet-stream";
    }
}
