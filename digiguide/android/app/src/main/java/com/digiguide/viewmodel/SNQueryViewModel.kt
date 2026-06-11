package com.digiguide.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digiguide.core.CoreBridge
import com.digiguide.db.AppDatabase
import com.digiguide.db.entity.QueryHistoryEntity
import com.digiguide.model.Brand
import com.digiguide.model.SNDecodeResult
import com.digiguide.model.SNDecodeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SN查询ViewModel
 */
class SNQueryViewModel : ViewModel() {

    // 输入状态
    private val _snInput = MutableStateFlow("")
    val snInput: StateFlow<String> = _snInput.asStateFlow()

    // 选定品牌
    private val _selectedBrand = MutableStateFlow<Brand?>(null)
    val selectedBrand: StateFlow<Brand?> = _selectedBrand.asStateFlow()

    // 购买日期（可选，用于更精确的保修计算）
    private val _purchaseDate = MutableStateFlow<String?>(null)
    val purchaseDate: StateFlow<String?> = _purchaseDate.asStateFlow()

    // 解码结果
    private val _decodeResult = MutableStateFlow<SNDecodeResult?>(null)
    val decodeResult: StateFlow<SNDecodeResult?> = _decodeResult.asStateFlow()
    val queryResult: SNDecodeResult? = _decodeResult.value

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val isQuerying: Boolean = _isLoading.value

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 查询历史
    private val _queryHistory = MutableStateFlow<List<QueryHistoryEntity>>(emptyList())
    val queryHistory: StateFlow<List<QueryHistoryEntity>> = _queryHistory.asStateFlow()

    // 格式提示
    private val _formatHint = MutableStateFlow<String?>(null)
    val formatHint: StateFlow<String?> = _formatHint.asStateFlow()

    // 表单验证
    val isFormValid: Boolean = _snInput.value.isNotEmpty()

    init {
        loadQueryHistory()
    }

    /**
     * 更新SN输入
     */
    fun updateSNInput(sn: String) {
        _snInput.value = sn.trim().uppercase()
        _errorMessage.value = null

        // 自动识别品牌
        if (sn.length >= 5) {
            val brand = identifyBrand(sn)
            _selectedBrand.value = brand
            if (brand != Brand.UNKNOWN) {
                _formatHint.value = CoreBridge.getFormatHint(brand)
            }
        }
    }

    /**
     * 选择品牌
     */
    fun selectBrand(brand: Brand) {
        _selectedBrand.value = brand
        _formatHint.value = CoreBridge.getFormatHint(brand)
    }

    /**
     * 设置购买日期
     */
    fun setPurchaseDate(date: String?) {
        _purchaseDate.value = date
    }

    /**
     * 执行查询（performQuery别名）
     */
    fun performQuery() {
        decode()
    }

    /**
     * 执行解码
     */
    fun decode() {
        val sn = _snInput.value
        if (sn.isEmpty()) {
            _errorMessage.value = "请输入序列号"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val brand = _selectedBrand.value
                val result = if (brand != null && brand != Brand.UNKNOWN) {
                    CoreBridge.decodeSN(sn, brand)
                } else {
                    CoreBridge.decodeSN(sn)
                }

                // 如果有购买日期，更新保修计算
                if (_purchaseDate.value != null && result.isSuccess()) {
                    // TODO: 根据购买日期重新计算保修状态
                }

                _decodeResult.value = result

                // 保存查询历史
                if (result.status == SNDecodeStatus.SUCCESS || result.status == SNDecodeStatus.PARTIAL) {
                    saveQueryHistory(result)
                }

                if (result.status == SNDecodeStatus.FAILED) {
                    _errorMessage.value = result.errorMessage
                }
            } catch (e: Exception) {
                _errorMessage.value = "解码失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 清除结果
     */
    fun clearResult() {
        _decodeResult.value = null
        _snInput.value = ""
        _selectedBrand.value = null
        _formatHint.value = null
        _errorMessage.value = null
    }

    /**
     * 加载查询历史
     */
    private fun loadQueryHistory() {
        viewModelScope.launch {
            try {
                val dao = AppDatabase.getDatabase(androidContext()).queryHistoryDao()
                dao.getRecentHistory(20).collect { history ->
                    _queryHistory.value = history
                }
            } catch (e: Exception) {
                // 使用空历史
            }
        }
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
            val dao = AppDatabase.getDatabase(androidContext()).queryHistoryDao()
            dao.insert(history)
        } catch (e: Exception) {
            // 忽略保存失败
        }
    }

    /**
     * 删除历史记录
     */
    fun deleteHistory(history: QueryHistoryEntity) {
        viewModelScope.launch {
            try {
                val dao = AppDatabase.getDatabase(androidContext()).queryHistoryDao()
                dao.delete(history)
            } catch (e: Exception) {
                // 忽略删除失败
            }
        }
    }

    /**
     * 清除所有历史
     */
    fun clearAllHistory() {
        viewModelScope.launch {
            try {
                val dao = AppDatabase.getDatabase(androidContext()).queryHistoryDao()
                dao.deleteAll()
            } catch (e: Exception) {
                // 忽略清除失败
            }
        }
    }

    /**
     * 从历史记录加载SN
     */
    fun loadFromHistory(history: QueryHistoryEntity) {
        _snInput.value = history.sn
        _selectedBrand.value = Brand.values().find { it.name == history.brand }
        decode()
    }

    /**
     * 简化品牌识别
     */
    private fun identifyBrand(sn: String): Brand {
        val cleanSn = sn.uppercase()

        // Apple: 12位
        if (cleanSn.length == 12) {
            val yearChars = setOf('C', 'D', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N',
                                   'P', 'Q', 'R', 'S', 'T', 'V', 'W', 'X', 'Y', 'Z',
                                   '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B')
            if (cleanSn[3] in yearChars) {
                return Brand.APPLE
            }
        }

        // Xiaomi: 15位纯数字IMEI
        if (cleanSn.length == 15 && cleanSn.all { it.isDigit() }) {
            return Brand.XIAOMI
        }

        return Brand.UNKNOWN
    }

    /**
     * 获取Android Context（需要在实际应用中实现）
     */
    private fun androidContext(): android.content.Context {
        return com.digiguide.DigiGuideApp.instance
    }
}