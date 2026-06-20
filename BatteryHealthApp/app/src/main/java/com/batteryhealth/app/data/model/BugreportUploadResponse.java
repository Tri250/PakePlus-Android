package com.batteryhealth.app.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Bugreport 上传响应
 */
public class BugreportUploadResponse {
    @SerializedName("code")
    private int code;

    @SerializedName("message")
    private String message;

    @SerializedName("data")
    private BatteryHealthReport data;

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public BatteryHealthReport getData() {
        return data;
    }

    public boolean isSuccess() {
        return code == 0 || code == 200;
    }
}
