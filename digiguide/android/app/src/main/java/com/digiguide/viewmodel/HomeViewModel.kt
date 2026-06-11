package com.digiguide.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digiguide.db.AppDatabase
import com.digiguide.db.entity.QueryHistoryEntity
import com.digiguide.db.entity.BatteryReportEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 首页ViewModel
 */
class HomeViewModel : ViewModel() {

    private val _snQueryCount = MutableStateFlow(0)
    val snQueryCount: StateFlow<Int> = _snQueryCount.asStateFlow()

    private val _batteryAnalysisCount = MutableStateFlow(0)
    val batteryAnalysisCount: StateFlow<Int> = _batteryAnalysisCount.asStateFlow()

    private val _reportCount = MutableStateFlow(0)
    val reportCount: StateFlow<Int> = _reportCount.asStateFlow()

    private val _averageHealth = MutableStateFlow(0f)
    val averageHealth: StateFlow<Float> = _averageHealth.asStateFlow()

    init {
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            try {
                val context = com.digiguide.DigiGuideApp.instance
                val db = AppDatabase.getDatabase(context)

                // 加载SN查询次数
                val queryDao = db.queryHistoryDao()
                queryDao.getCount().let { count ->
                    _snQueryCount.value = count
                }

                // 加载电池分析次数和报告数
                val reportDao = db.batteryReportDao()
                reportDao.getCount().let { count ->
                    _batteryAnalysisCount.value = count
                    _reportCount.value = count
                }

                // 加载平均健康度
                reportDao.getAverageHealth()?.let { avg ->
                    _averageHealth.value = avg
                }
            } catch (e: Exception) {
                // 数据库未初始化或查询失败，使用默认值
                _snQueryCount.value = 0
                _batteryAnalysisCount.value = 0
                _reportCount.value = 0
                _averageHealth.value = 0f
            }
        }
    }

    fun refreshStatistics() {
        loadStatistics()
    }
}