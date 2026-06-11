package com.digiguide.service

import com.digiguide.model.SNDecodeResult
import com.digiguide.model.Brand
import retrofit2.Response
import retrofit2.http.*

/**
 * SN查询服务接口
 */
interface SNQueryService {

    /**
     * 自动识别品牌并解码SN
     */
    @GET("sn/decode")
    suspend fun decodeSN(@Query("sn") sn: String): Response<SNDecodeResult>

    /**
     * 指定品牌解码SN
     */
    @GET("sn/decode")
    suspend fun decodeSNWithBrand(
        @Query("sn") sn: String,
        @Query("brand") brand: String
    ): Response<SNDecodeResult>

    /**
     * 验证SN格式
     */
    @GET("sn/validate")
    suspend fun validateFormat(
        @Query("sn") sn: String,
        @Query("brand") brand: String
    ): Response<Boolean>

    /**
     * 获取品牌格式说明
     */
    @GET("sn/format/{brand}")
    suspend fun getFormatHint(@Path("brand") brand: String): Response<String>

    /**
     * 查询设备保修信息
     */
    @GET("sn/warranty")
    suspend fun getWarrantyInfo(@Query("sn") sn: String): Response<WarrantyInfo>

    /**
     * 批量查询SN
     */
    @POST("sn/batch")
    suspend fun batchDecode(@Body snList: List<String>): Response<List<SNDecodeResult>>

    /**
     * 保修信息
     */
    data class WarrantyInfo(
        val sn: String,
        val brand: String,
        val purchaseDate: String?,
        val warrantyStartDate: String?,
        val warrantyEndDate: String?,
        val warrantyStatus: String,
        val remainingDays: Int?
    )
}