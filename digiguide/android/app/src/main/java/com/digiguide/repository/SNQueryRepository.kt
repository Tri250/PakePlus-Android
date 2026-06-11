package com.digiguide.repository

import com.digiguide.core.CoreBridge
import com.digiguide.db.AppDatabase
import com.digiguide.db.entity.QueryHistoryEntity
import com.digiguide.db.entity.BatteryReportEntity
import com.digiguide.model.Brand
import com.digiguide.model.SNDecodeResult
import com.digiguide.model.BatteryRawData
import com.digiguide.model.BatteryHealthResult
import com.digiguide.service.ApiClient
import com.digiguide.service.SNQueryService
import com.digiguide.service.BatteryReportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * SN查询Repository
 * 协调本地Core引擎和远程API服务
 */
class SNQueryRepository(private val context: android.content.Context) {

    private val snQueryService: SNQueryService by lazy {
        ApiClient.init()
        ApiClient.createService(SNQueryService::class.java)
    }

    private val queryHistoryDao by lazy {
        AppDatabase.getDatabase(context).queryHistoryDao()
    }

    /**
     * 解码SN（优先本地，失败时尝试远程）
     */
    suspend fun decodeSN(sn: String, brand: Brand? = null): SNDecodeResult {
        return withContext(Dispatchers.IO) {
            // 优先使用本地Core引擎解码
            val localResult = if (brand != null && brand != Brand.UNKNOWN) {
                CoreBridge.decodeSN(sn, brand)
            } else {
                CoreBridge.decodeSN(sn)
            }

            // 如果本地解码成功，直接返回
            if (localResult.isSuccess()) {
                // 保存到本地历史
                saveQueryHistory(localResult)
                return@withContext localResult
            }

            // 本地解码失败或部分成功，尝试远程API（如果可用）
            try {
                val remoteResult = if (brand != null && brand != Brand.UNKNOWN) {
                    snQueryService.decodeSNWithBrand(sn, brand.name).body()
                } else {
                    snQueryService.decodeSN(sn).body()
                }

                if (remoteResult != null && remoteResult.isSuccess()) {
                    // 保存到本地历史
                    saveQueryHistory(remoteResult)
                    return@withContext remoteResult
                }
            } catch (e: Exception) {
                // 远程API不可用，返回本地结果
            }

            // 返回本地结果（即使是部分成功或失败）
            if (localResult.status != com.digiguide.model.SNDecodeStatus.FAILED) {
                saveQueryHistory(localResult)
            }
            return@withContext localResult
        }
    }

    /**
     * 验证SN格式
     */
    suspend fun validateFormat(sn: String, brand: Brand): Boolean {
        return withContext(Dispatchers.IO) {
            CoreBridge.validateSNFormat(sn, brand)
        }
    }

    /**
     * 获取格式提示
     */
    fun getFormatHint(brand: Brand): String {
        return CoreBridge.getFormatHint(brand)
    }

    /**
     * 保存查询历史
     */
    private suspend fun saveQueryHistory(result: SNDecodeResult) {
        try {
            val history = QueryHistoryEntity(
                sn = result.rawSn,
                brand = result.brand.name,
                factoryYear = result.factoryYear,
                factoryMonth = result.factoryMonth,
                factoryWeek = result.factoryWeek,
                halfYear = result.halfYear,
                status = result.status.name,
                errorMessage = result.errorMessage,
                deviceModel = null
            )
            queryHistoryDao.insert(history)
        } catch (e: Exception) {
            // 保存失败不影响主流程
        }
    }

    /**
     * 获取查询历史
     */
    suspend fun getQueryHistory(limit: Int = 20): List<QueryHistoryEntity> {
        return withContext(Dispatchers.IO) {
            queryHistoryDao.getAllOnce()
        }
    }

    /**
     * 清除查询历史
     */
    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            queryHistoryDao.deleteAll()
        }
    }
}

/**
 * 电池分析Repository
 */
class BatteryRepository(private val context: android.content.Context) {

    private val batteryService: BatteryReportService by lazy {
        ApiClient.init()
        ApiClient.createService(BatteryReportService::class.java)
    }

    private val reportDao by lazy {
        AppDatabase.getDatabase(context).batteryReportDao()
    }

    /**
     * 分析bugreport（本地处理）
     */
    suspend fun analyzeBugreport(content: String): Pair<BatteryRawData, BatteryHealthResult> {
        return withContext(Dispatchers.IO) {
            // 使用本地Core引擎解析
            val rawData = CoreBridge.parseBugreport(content)
            val healthResult = CoreBridge.calculateBatteryHealth(rawData)

            // 保存报告到本地
            saveBatteryReport(rawData, healthResult, null)

            return@withContext Pair(rawData, healthResult)
        }
    }

    /**
     * 分析bugreport文件
     */
    suspend fun analyzeBugreportFile(filePath: String, content: String): Pair<BatteryRawData, BatteryHealthResult> {
        return withContext(Dispatchers.IO) {
            val rawData = CoreBridge.parseBugreport(content)
            val healthResult = CoreBridge.calculateBatteryHealth(rawData)

            saveBatteryReport(rawData, healthResult, filePath)

            return@withContext Pair(rawData, healthResult)
        }
    }

    /**
     * 保存电池报告
     */
    private suspend fun saveBatteryReport(
        rawData: BatteryRawData,
        healthResult: BatteryHealthResult,
        filePath: String?
    ) {
        try {
            val report = BatteryReportEntity(
                brand = rawData.brand,
                model = rawData.model,
                sn = rawData.sn,
                designCapacityMah = rawData.designCapacityMah,
                currentCapacityMah = rawData.currentCapacityMah,
                cycleCount = rawData.cycleCount,
                manufacturingDate = rawData.manufacturingDate,
                temperatureCelsius = rawData.temperatureCelsius,
                healthPercentage = healthResult.healthPercentage,
                grade = healthResult.grade,
                capacityRetention = healthResult.capacityRetention,
                cycleDecay = healthResult.cycleDecay,
                diagnosisText = healthResult.diagnosisText,
                suggestions = healthResult.suggestions.joinToString(";"),
                rawBugreportPath = filePath
            )
            reportDao.insert(report)
        } catch (e: Exception) {
            // 保存失败不影响主流程
        }
    }

    /**
     * 获取报告历史
     */
    suspend fun getReportHistory(limit: Int = 20): List<BatteryReportEntity> {
        return withContext(Dispatchers.IO) {
            reportDao.getAllOnce()
        }
    }

    /**
     * 获取报告统计
     */
    suspend fun getStatistics(): BatteryStatistics {
        return withContext(Dispatchers.IO) {
            val count = reportDao.getCount()
            val avgHealth = reportDao.getAverageHealth() ?: 0f
            val avgCycles = reportDao.getAverageCycleCount() ?: 0f

            BatteryStatistics(
                totalCount = count,
                averageHealth = avgHealth,
                averageCycles = avgCycles.toInt()
            )
        }
    }

    /**
     * 删除报告
     */
    suspend fun deleteReport(report: BatteryReportEntity) {
        withContext(Dispatchers.IO) {
            reportDao.delete(report)
        }
    }

    /**
     * 清除所有报告
     */
    suspend fun clearAllReports() {
        withContext(Dispatchers.IO) {
            reportDao.deleteAll()
        }
    }

    /**
     * 电池统计数据
     */
    data class BatteryStatistics(
        val totalCount: Int = 0,
        val averageHealth: Float = 0f,
        val averageCycles: Int = 0
    )
}