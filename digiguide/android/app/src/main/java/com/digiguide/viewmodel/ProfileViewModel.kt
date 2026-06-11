package com.digiguide.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digiguide.db.AppDatabase
import com.digiguide.db.entity.QueryHistoryEntity
import com.digiguide.db.entity.BatteryReportEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * 个人中心ViewModel
 */
class ProfileViewModel : ViewModel() {

    // 深色模式
    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    // 通知设置
    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    // 自动同步
    private val _autoSyncEnabled = MutableStateFlow(false)
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    // 缓存大小
    private val _cacheSize = MutableStateFlow("0 KB")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    // 应用版本
    private val _appVersion = MutableStateFlow("3.1.0")
    val appVersion: StateFlow<String> = _appVersion.asStateFlow()

    init {
        // 初始化时计算缓存大小
        // 实际应用中可以从SharedPreferences读取设置
    }

    /**
     * 设置深色模式
     */
    fun setDarkMode(enabled: Boolean) {
        _darkMode.value = enabled
        // 实际应用中应保存到SharedPreferences
    }

    /**
     * 设置通知开关
     */
    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
    }

    /**
     * 设置自动同步开关
     */
    fun setAutoSyncEnabled(enabled: Boolean) {
        _autoSyncEnabled.value = enabled
    }

    /**
     * 清除缓存
     */
    fun clearCache(context: Context) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 清除应用缓存目录
                    val cacheDir = context.cacheDir
                    if (cacheDir.exists()) {
                        deleteDirectory(cacheDir)
                    }
                }
                // 更新缓存大小显示
                calculateCacheSize(context)
            } catch (e: Exception) {
                // 清除失败
            }
        }
    }

    /**
     * 递归删除目录
     */
    private fun deleteDirectory(directory: File) {
        if (directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                deleteDirectory(file)
            }
        }
        directory.delete()
    }

    /**
     * 计算缓存大小
     */
    private fun calculateCacheSize(context: Context) {
        viewModelScope.launch {
            try {
                val cacheDir = context.cacheDir
                val sizeBytes = withContext(Dispatchers.IO) {
                    calculateDirectorySize(cacheDir)
                }
                _cacheSize.value = formatFileSize(sizeBytes)
            } catch (e: Exception) {
                _cacheSize.value = "0 KB"
            }
        }
    }

    /**
     * 递归计算目录大小
     */
    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        if (directory.exists()) {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    calculateDirectorySize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }

    /**
     * 格式化文件大小
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
     * 导出数据
     */
    fun exportData(context: Context) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(context)

                    // 创建导出目录
                    val exportDir = File(context.filesDir, "export")
                    if (!exportDir.exists()) {
                        exportDir.mkdirs()
                    }

                    // 生成导出文件名
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val exportFile = File(exportDir, "digiguide_export_$timestamp.json")

                    // 构建JSON数据
                    val json = JSONObject()

                    // 导出SN查询历史
                    val queryHistory = db.queryHistoryDao().getAllOnce()
                    val queryArray = JSONArray()
                    queryHistory.forEach { entity ->
                        val item = JSONObject()
                        item.put("sn", entity.sn)
                        item.put("brand", entity.brand)
                        item.put("factoryYear", entity.factoryYear)
                        item.put("factoryMonth", entity.factoryMonth)
                        item.put("status", entity.status)
                        item.put("queryTime", entity.queryTime)
                        queryArray.put(item)
                    }
                    json.put("queryHistory", queryArray)

                    // 导出电池报告
                    val batteryReports = db.batteryReportDao().getAllOnce()
                    val reportArray = JSONArray()
                    batteryReports.forEach { entity ->
                        val item = JSONObject()
                        item.put("id", entity.id)
                        item.put("brand", entity.brand)
                        item.put("model", entity.model)
                        item.put("sn", entity.sn)
                        item.put("designCapacityMah", entity.designCapacityMah)
                        item.put("currentCapacityMah", entity.currentCapacityMah)
                        item.put("cycleCount", entity.cycleCount)
                        item.put("healthPercentage", entity.healthPercentage)
                        item.put("grade", entity.grade)
                        item.put("reportTime", entity.reportTime)
                        item.put("diagnosisText", entity.diagnosisText)
                        reportArray.put(item)
                    }
                    json.put("batteryReports", reportArray)

                    // 添加导出信息
                    json.put("exportTime", timestamp)
                    json.put("appVersion", _appVersion.value)

                    // 写入文件
                    FileWriter(exportFile).use { writer ->
                        writer.write(json.toString())
                    }
                }
            } catch (e: Exception) {
                // 导出失败
            }
        }
    }

    /**
     * 提交反馈
     */
    fun submitFeedback(feedback: String) {
        viewModelScope.launch {
            try {
                // 实际应用中应发送到服务器或保存到本地
                // 这里仅记录反馈内容
                println("Feedback submitted: $feedback")
            } catch (e: Exception) {
                // 提交失败
            }
        }
    }
}