package com.digiguide.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digiguide.core.CoreBridge
import com.digiguide.db.AppDatabase
import com.digiguide.db.entity.BatteryReportEntity
import com.digiguide.model.BatteryHealthResult
import com.digiguide.model.BatteryRawData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 电池分析ViewModel
 */
class BatteryAnalysisViewModel : ViewModel() {

    // 文件大小限制（50MB）
    private const val MAX_FILE_SIZE_MB = 50
    private const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024

    // 原始数据
    private val _rawData = MutableStateFlow<BatteryRawData?>(null)
    val rawData: StateFlow<BatteryRawData?> = _rawData.asStateFlow()

    // 健康度结果
    private val _healthResult = MutableStateFlow<BatteryHealthResult?>(null)
    val healthResult: StateFlow<BatteryHealthResult?> = _healthResult.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 分析进度
    private val _analysisProgress = MutableStateFlow(0)
    val analysisProgress: StateFlow<Int> = _analysisProgress.asStateFlow()

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // bugreport文件路径
    private val _bugreportFilePath = MutableStateFlow<String?>(null)
    val bugreportFilePath: StateFlow<String?> = _bugreportFilePath.asStateFlow()

    // bugreport文本内容
    private val _bugreportContent = MutableStateFlow<String?>(null)
    val bugreportContent: StateFlow<String?> = _bugreportContent.asStateFlow()

    // 文件大小信息
    private val _fileSizeInfo = MutableStateFlow<String?>(null)
    val fileSizeInfo: StateFlow<String?> = _fileSizeInfo.asStateFlow()

    // 历史报告
    private val _reportHistory = MutableStateFlow<List<BatteryReportEntity>>(emptyList())
    val reportHistory: StateFlow<List<BatteryReportEntity>> = _reportHistory.asStateFlow()

    init {
        loadReportHistory()
    }

    /**
     * 设置bugreport文件
     * 包含文件大小验证
     */
    fun setBugreportFile(filePath: String) {
        // 验证文件大小
        val file = File(filePath)
        val fileSizeBytes = file.length()
        val fileSizeMB = fileSizeBytes / (1024 * 1024)

        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            _errorMessage.value = "文件过大（${fileSizeMB}MB），超过${MAX_FILE_SIZE_MB}MB限制。请使用较小的bugreport文件。"
            _bugreportFilePath.value = null
            _fileSizeInfo.value = null
            return
        }

        _bugreportFilePath.value = filePath
        _errorMessage.value = null
        _fileSizeInfo.value = "文件大小: ${fileSizeMB}MB (${formatFileSize(fileSizeBytes)})"
    }

    /**
     * 设置bugreport文本内容
     */
    fun setBugreportContent(content: String) {
        // 验证内容大小
        val contentSizeBytes = content.toByteArray().size
        if (contentSizeBytes > MAX_FILE_SIZE_BYTES) {
            _errorMessage.value = "内容过大，超过${MAX_FILE_SIZE_MB}MB限制。"
            _bugreportContent.value = null
            return
        }

        _bugreportContent.value = content
        _errorMessage.value = null
    }

    /**
     * 开始分析
     * 使用异步解析 + 进度回调
     */
    fun startAnalysis() {
        val filePath = _bugreportFilePath.value
        val content = _bugreportContent.value

        if (filePath == null && content == null) {
            _errorMessage.value = "请选择bugreport文件或输入文本内容"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _analysisProgress.value = 0
            _errorMessage.value = null

            try {
                // 步骤1: 解析bugreport（在IO线程执行）
                _analysisProgress.value = 10
                updateProgress("正在读取文件...")

                val rawData = withContext(Dispatchers.IO) {
                    _analysisProgress.value = 20
                    updateProgress("正在解析bugreport...")

                    if (content != null) {
                        CoreBridge.parseBugreport(content)
                    } else {
                        // 从文件读取（大文件分块读取）
                        val fileContent = readLargeFileSafely(filePath!!)
                        CoreBridge.parseBugreport(fileContent)
                    }
                }

                _analysisProgress.value = 50
                updateProgress("正在提取电池数据...")

                // 验证解析结果
                if (rawData.getAvailableDataCount() == 0) {
                    _errorMessage.value = "无法从bugreport中提取有效数据。请确保文件格式正确，或尝试重新生成bugreport。"
                    _isLoading.value = false
                    return
                }

                _rawData.value = rawData

                // 步骤2: 计算健康度
                _analysisProgress.value = 70
                updateProgress("正在计算健康度...")

                val healthResult = withContext(Dispatchers.IO) {
                    CoreBridge.calculateBatteryHealth(rawData)
                }
                _healthResult.value = healthResult

                // 步骤3: 保存报告（本地存储，不上传）
                _analysisProgress.value = 90
                updateProgress("正在保存报告...")

                saveBatteryReport(rawData, healthResult)

                // 完成
                _analysisProgress.value = 100
                updateProgress("分析完成")

            } catch (e: OutOfMemoryError) {
                _errorMessage.value = "内存不足，文件过大。请尝试使用较小的bugreport文件。"
            } catch (e: Exception) {
                _errorMessage.value = "分析失败: ${e.message}\n请检查bugreport格式是否正确。"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 安全读取大文件（分块读取）
     */
    private fun readLargeFileSafely(filePath: String): String {
        val file = File(filePath)
        val bufferSize = 1024 * 1024 // 1MB buffer

        return file.bufferedReader(bufferSize).use { reader ->
            reader.readText()
        }
    }

    /**
     * 更新进度提示
     */
    private fun updateProgress(message: String) {
        // 可以用于显示更详细的进度信息
    }

    /**
     * 格式化文件大小显示
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    /**
     * 清除分析结果
     */
    fun clearAnalysis() {
        _rawData.value = null
        _healthResult.value = null
        _bugreportFilePath.value = null
        _bugreportContent.value = null
        _analysisProgress.value = 0
        _errorMessage.value = null
    }

    /**
     * 加载报告历史
     */
    private fun loadReportHistory() {
        viewModelScope.launch {
            try {
                val dao = AppDatabase.getDatabase(androidContext()).batteryReportDao()
                dao.getRecentReports(20).collect { reports ->
                    _reportHistory.value = reports
                }
            } catch (e: Exception) {
                // 使用空历史
            }
        }
    }

    /**
     * 保存电池报告
     */
    private suspend fun saveBatteryReport(
        rawData: BatteryRawData,
        healthResult: BatteryHealthResult
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
                rawBugreportPath = _bugreportFilePath.value
            )
            val dao = AppDatabase.getDatabase(androidContext()).batteryReportDao()
            dao.insert(report)
        } catch (e: Exception) {
            // 忽略保存失败
        }
    }

    /**
     * 删除报告
     */
    fun deleteReport(report: BatteryReportEntity) {
        viewModelScope.launch {
            try {
                val dao = AppDatabase.getDatabase(androidContext()).batteryReportDao()
                dao.delete(report)
            } catch (e: Exception) {
                // 忽略删除失败
            }
        }
    }

    /**
     * 清除所有报告
     */
    fun clearAllReports() {
        viewModelScope.launch {
            try {
                val dao = AppDatabase.getDatabase(androidContext()).batteryReportDao()
                dao.deleteAll()
            } catch (e: Exception) {
                // 忽略清除失败
            }
        }
    }

    /**
     * 查看历史报告详情
     */
    fun viewReportDetail(report: BatteryReportEntity) {
        // 构建历史报告的健康度结果
        val healthResult = BatteryHealthResult(
            healthPercentage = report.healthPercentage,
            grade = report.grade,
            capacityRetention = report.capacityRetention,
            cycleDecay = report.cycleDecay,
            diagnosisText = report.diagnosisText ?: "",
            suggestions = report.suggestions?.split(";") ?: emptyList()
        )
        _healthResult.value = healthResult

        // 构建原始数据
        val rawData = BatteryRawData(
            brand = report.brand,
            model = report.model,
            sn = report.sn,
            designCapacityMah = report.designCapacityMah,
            currentCapacityMah = report.currentCapacityMah,
            cycleCount = report.cycleCount,
            manufacturingDate = report.manufacturingDate,
            temperatureCelsius = report.temperatureCelsius
        )
        _rawData.value = rawData
    }

    /**
     * 获取Android Context
     */
    private fun androidContext(): android.content.Context {
        return com.digiguide.DigiGuideApp.instance
    }
}