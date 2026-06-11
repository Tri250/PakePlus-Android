package com.digiguide.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digiguide.db.AppDatabase
import com.digiguide.db.entity.BatteryReportEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 报告历史ViewModel
 */
class ReportViewModel : ViewModel() {

    // 报告历史列表
    private val _reportHistory = MutableStateFlow<List<BatteryReportEntity>>(emptyList())
    val reportHistory: StateFlow<List<BatteryReportEntity>> = _reportHistory.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 统计数据
    private val _statistics = MutableStateFlow(ReportStatistics())
    val statistics: StateFlow<ReportStatistics> = _statistics.asStateFlow()

    // 删除对话框状态
    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()

    // 选中的报告
    private val _selectedReport = MutableStateFlow<BatteryReportEntity?>(null)
    val selectedReport: StateFlow<BatteryReportEntity?> = _selectedReport.asStateFlow()

    // 清除全部对话框状态
    private val _showClearAllDialog = MutableStateFlow(false)
    val showClearAllDialog: StateFlow<Boolean> = _showClearAllDialog.asStateFlow()

    init {
        loadReports()
    }

    /**
     * 加载报告列表
     */
    private fun loadReports() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dao = AppDatabase.getDatabase(androidContext()).batteryReportDao()
                dao.getRecentReports(50).collect { reports ->
                    _reportHistory.value = reports
                    calculateStatistics(reports)
                }
            } catch (e: Exception) {
                // 加载失败，使用空列表
                _reportHistory.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 计算统计数据
     */
    private fun calculateStatistics(reports: List<BatteryReportEntity>) {
        if (reports.isEmpty()) {
            _statistics.value = ReportStatistics()
            return
        }

        val totalCount = reports.size
        val averageHealth = reports.map { it.healthPercentage }.average().toFloat()
        val averageCycles = reports.filter { it.cycleCount != null }.map { it.cycleCount!!.toFloat() }.average().toInt()

        // 计算等级分布
        val gradeDistribution = mutableMapOf<String, Int>()
        reports.forEach { report ->
            val count = gradeDistribution.getOrDefault(report.grade, 0)
            gradeDistribution[report.grade] = count + 1
        }

        _statistics.value = ReportStatistics(
            totalCount = totalCount,
            averageHealth = averageHealth,
            averageCycles = averageCycles,
            gradeDistribution = gradeDistribution
        )
    }

    /**
     * 刷新报告
     */
    fun refreshReports() {
        loadReports()
    }

    /**
     * 选择报告进行删除
     */
    fun selectReportForDelete(report: BatteryReportEntity) {
        _selectedReport.value = report
        _showDeleteDialog.value = true
    }

    /**
     * 切换删除对话框
     */
    fun toggleDeleteDialog(show: Boolean) {
        _showDeleteDialog.value = show
        if (!show) {
            _selectedReport.value = null
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
                // 删除失败
            }
        }
    }

    /**
     * 切换清除全部对话框
     */
    fun toggleClearAllDialog(show: Boolean) {
        _showClearAllDialog.value = show
    }

    /**
     * 清除所有报告
     */
    fun clearAllReports() {
        viewModelScope.launch {
            try {
                val dao = AppDatabase.getDatabase(androidContext()).batteryReportDao()
                dao.deleteAll()
                _reportHistory.value = emptyList()
                _statistics.value = ReportStatistics()
            } catch (e: Exception) {
                // 清除失败
            }
        }
    }

    /**
     * 获取Android Context
     */
    private fun androidContext(): android.content.Context {
        return com.digiguide.DigiGuideApp.instance
    }

    /**
     * 报告统计数据
     */
    data class ReportStatistics(
        val totalCount: Int = 0,
        val averageHealth: Float = 0f,
        val averageCycles: Int = 0,
        val gradeDistribution: Map<String, Int> = emptyMap()
    )
}