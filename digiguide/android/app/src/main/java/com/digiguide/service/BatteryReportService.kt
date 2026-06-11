package com.digiguide.service

import com.digiguide.model.BatteryRawData
import com.digiguide.model.BatteryHealthResult
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * 电池报告服务接口
 */
interface BatteryReportService {

    /**
     * 上传bugreport文件分析
     */
    @Multipart
    @POST("battery/analyze")
    suspend fun analyzeBugreport(
        @Part file: MultipartBody.Part
    ): Response<BatteryAnalysisResponse>

    /**
     * 上传文本内容分析
     */
    @POST("battery/analyze/text")
    suspend fun analyzeBugreportText(
        @Body content: String
    ): Response<BatteryAnalysisResponse>

    /**
     * 获取电池健康度计算
     */
    @POST("battery/health")
    suspend fun calculateHealth(
        @Body rawData: BatteryRawData
    ): Response<BatteryHealthResult>

    /**
     * 获取历史报告
     */
    @GET("battery/reports")
    suspend fun getReports(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<List<BatteryReportResponse>>

    /**
     * 获取报告详情
     */
    @GET("battery/reports/{id}")
    suspend fun getReportDetail(@Path("id") id: Long): Response<BatteryReportResponse>

    /**
     * 删除报告
     */
    @DELETE("battery/reports/{id}")
    suspend fun deleteReport(@Path("id") id: Long): Response<Boolean>

    /**
     * 电池分析响应
     */
    data class BatteryAnalysisResponse(
        val rawData: BatteryRawData,
        val healthResult: BatteryHealthResult,
        val reportId: Long,
        val analysisTime: Long
    )

    /**
     * 电池报告响应
     */
    data class BatteryReportResponse(
        val id: Long,
        val brand: String?,
        val model: String?,
        val sn: String?,
        val designCapacityMah: Int?,
        val currentCapacityMah: Int?,
        val cycleCount: Int?,
        val healthPercentage: Float,
        val grade: String,
        val reportTime: Long,
        val diagnosisText: String?
    )
}