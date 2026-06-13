/**
 * 电池健康度分析工具 - 主应用模块
 */

const BatteryHealthApp = {
    // DOM 元素缓存
    elements: {},
    
    // 当前选中的文件
    currentFile: null,
    
    // 解析进度
    progress: 0,
    
    /**
     * 初始化应用
     */
    init() {
        this.cacheElements();
        this.bindEvents();
        this.renderHistory();
        this.initBatteryDatabase();
        
        // 禁用 zip.js web worker（在 WebView 中可能有问题）
        if (typeof zip !== 'undefined') {
            zip.useWebWorkers = false;
        }
    },
    
    /**
     * 缓存 DOM 元素
     */
    cacheElements() {
        this.elements = {
            dropArea: document.getElementById('drop-area'),
            fileInput: document.getElementById('zip-file'),
            fileNameDisplay: document.getElementById('file-name-display'),
            selectedFileName: document.getElementById('selected-file-name'),
            fileValidationError: document.getElementById('file-validation-error'),
            initialCapacityInput: document.getElementById('initial-capacity'),
            calculateBtn: document.getElementById('calculate-btn'),
            status: document.getElementById('status'),
            progressContainer: document.getElementById('progress-container'),
            progressFill: document.getElementById('progress-fill'),
            progressText: document.getElementById('progress-text'),
            result: document.getElementById('result'),
            historyList: document.getElementById('history-list'),
            capacitySearchInput: document.getElementById('capacity-search-input'),
            capacitySearchResult: document.getElementById('capacity-search-result')
        };
    },
    
    /**
     * 绑定事件
     */
    bindEvents() {
        const { dropArea, fileInput, calculateBtn } = this.elements;
        
        // 拖放事件
        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
            dropArea.addEventListener(eventName, (e) => this.preventDefaults(e), false);
        });
        
        ['dragenter', 'dragover'].forEach(eventName => {
            dropArea.addEventListener(eventName, () => this.highlight(), false);
        });
        
        ['dragleave', 'drop'].forEach(eventName => {
            dropArea.addEventListener(eventName, () => this.unhighlight(), false);
        });
        
        dropArea.addEventListener('drop', (e) => this.handleDrop(e), false);
        dropArea.addEventListener('click', () => fileInput.click());
        
        // 文件选择事件
        fileInput.addEventListener('change', (e) => this.handleFileSelect(e));
        
        // 分析按钮事件
        calculateBtn.addEventListener('click', () => this.handleAnalyze());
        
        // 品牌标签切换
        document.querySelectorAll('.brand-tab').forEach(tab => {
            tab.addEventListener('click', () => this.switchBrandTab(tab));
        });
        
        // 原始文本切换
        const toggleBtn = document.getElementById('toggle-text');
        if (toggleBtn) {
            toggleBtn.addEventListener('click', () => this.toggleRawText());
        }
        
        // 电池容量搜索
        const capacitySearchBtn = document.getElementById('capacity-search-btn');
        if (capacitySearchBtn) {
            capacitySearchBtn.addEventListener('click', () => this.searchCapacity());
        }
        
        // 输入防抖
        this.elements.initialCapacityInput.addEventListener('input', 
            this.debounce(() => this.validateCapacity(), 300)
        );
    },
    
    /**
     * 阻止默认事件
     */
    preventDefaults(e) {
        e.preventDefault();
        e.stopPropagation();
    },
    
    /**
     * 高亮拖放区域
     */
    highlight() {
        this.elements.dropArea.classList.add('drag-over');
    },
    
    /**
     * 取消高亮
     */
    unhighlight() {
        this.elements.dropArea.classList.remove('drag-over');
    },
    
    /**
     * 处理拖放
     */
    handleDrop(e) {
        const dt = e.dataTransfer;
        const files = dt.files;
        if (files.length > 0) {
            this.validateAndSetFile(files[0]);
        }
    },
    
    /**
     * 处理文件选择
     */
    handleFileSelect(e) {
        if (e.target.files.length > 0) {
            this.validateAndSetFile(e.target.files[0]);
        }
    },
    
    /**
     * 验证并设置文件
     */
    validateAndSetFile(file) {
        const errorEl = this.elements.fileValidationError;
        
        // 验证文件类型
        if (!file.name.toLowerCase().endsWith('.zip')) {
            errorEl.textContent = '请选择 ZIP 格式的文件';
            errorEl.classList.add('show');
            this.currentFile = null;
            return false;
        }
        
        // 验证文件大小（最大 100MB）
        if (file.size > 100 * 1024 * 1024) {
            errorEl.textContent = '文件过大，请选择小于 100MB 的文件';
            errorEl.classList.add('show');
            this.currentFile = null;
            return false;
        }
        
        // 验证通过
        errorEl.classList.remove('show');
        this.currentFile = file;
        this.elements.selectedFileName.textContent = file.name;
        this.elements.fileNameDisplay.style.display = 'flex';
        
        return true;
    },
    
    /**
     * 验证容量输入
     */
    validateCapacity() {
        const value = parseInt(this.elements.initialCapacityInput.value);
        const input = this.elements.initialCapacityInput;
        
        if (value && (value < 1000 || value > 10000)) {
            input.classList.add('error');
            return false;
        } else {
            input.classList.remove('error');
            return true;
        }
    },
    
    /**
     * 防抖函数
     */
    debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    },
    
    /**
     * 切换品牌标签
     */
    switchBrandTab(tab) {
        document.querySelectorAll('.brand-tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.brand-content').forEach(c => c.classList.remove('active'));
        
        tab.classList.add('active');
        const brand = tab.getAttribute('data-brand');
        document.getElementById(brand + '-content').classList.add('active');
    },
    
    /**
     * 更新进度条
     */
    updateProgress(percent, text) {
        this.elements.progressFill.style.width = percent + '%';
        this.elements.progressText.textContent = text || `解析中... ${percent}%`;
    },
    
    /**
     * 显示状态消息
     */
    showStatus(message, isError = false) {
        const status = this.elements.status;
        status.textContent = message;
        status.classList.remove('show', 'error');
        status.classList.add('show');
        if (isError) {
            status.classList.add('error');
        }
    },
    
    /**
     * 隐藏状态
     */
    hideStatus() {
        this.elements.status.classList.remove('show');
    },
    
    /**
     * 处理分析
     */
    async handleAnalyze() {
        const { calculateBtn, initialCapacityInput, progressContainer } = this.elements;
        
        // 验证文件
        if (!this.currentFile) {
            this.showStatus('请选择诊断文件', true);
            return;
        }
        
        // 验证容量
        const initialCapacity = parseInt(initialCapacityInput.value);
        if (!initialCapacity || initialCapacity <= 0) {
            this.showStatus('请输入有效的初始电池容量', true);
            initialCapacityInput.classList.add('error');
            return;
        }
        
        // 设置加载状态
        calculateBtn.disabled = true;
        calculateBtn.classList.add('loading');
        progressContainer.classList.add('show');
        this.hideStatus();
        
        try {
            this.updateProgress(10, '正在读取文件...');
            const result = await this.analyzeZipFile(this.currentFile, initialCapacity);
            
            this.updateProgress(100, '分析完成');
            this.displayResult(result, initialCapacity);
            
            // 保存到历史记录
            HistoryManager.add({
                brand: result.brand || 'unknown',
                initialCapacity: initialCapacity,
                currentCapacity: result.currentCapacity,
                healthPercentage: ((result.currentCapacity / initialCapacity) * 100).toFixed(1),
                cycleCount: result.cycleCount,
                batteryTemp: result.batteryTemp
            });
            
            // 刷新历史记录
            this.renderHistory();
            
        } catch (error) {
            this.showStatus('分析失败: ' + error.message, true);
            console.error(error);
        } finally {
            calculateBtn.disabled = false;
            calculateBtn.classList.remove('loading');
            setTimeout(() => {
                progressContainer.classList.remove('show');
            }, 1000);
        }
    },
    
    /**
     * 分析 ZIP 文件
     */
    async analyzeZipFile(file, initialCapacity) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            
            reader.onprogress = (e) => {
                if (e.lengthComputable) {
                    const percent = Math.round((e.loaded / e.total) * 30) + 10;
                    this.updateProgress(percent, '正在读取文件...');
                }
            };
            
            reader.onload = async (e) => {
                try {
                    this.updateProgress(40, '正在解压文件...');
                    
                    const blob = new Blob([e.target.result]);
                    const zipReader = new zip.ZipReader(new zip.BlobReader(blob));
                    const entries = await zipReader.getEntries();
                    
                    this.updateProgress(60, '正在查找电池信息...');
                    
                    // 检测品牌
                    let detectedBrand = 'generic';
                    let batteryInfo = null;
                    
                    // 查找可能包含电池信息的文件
                    for (let i = 0; i < entries.length; i++) {
                        const entry = entries[i];
                        const filename = entry.filename.toLowerCase();
                        
                        // 更新进度
                        const progress = 60 + Math.round((i / entries.length) * 20);
                        this.updateProgress(progress, `正在分析: ${entry.filename}...`);
                        
                        // 查找电池相关文件
                        if (filename.includes('battery') || 
                            filename.includes('dumpstate') ||
                            filename.includes('bugreport') ||
                            filename.endsWith('.txt')) {
                            
                            try {
                                const content = await entry.getData(new zip.TextWriter());
                                
                                // 检测品牌
                                if (detectedBrand === 'generic') {
                                    detectedBrand = BatteryParsers.detectBrand(entries, content);
                                }
                                
                                // 解析电池信息
                                const info = BatteryParsers.parse(content, detectedBrand);
                                if (info && info.currentCapacity) {
                                    batteryInfo = info;
                                    break;
                                }
                            } catch (err) {
                                console.log('Error reading entry:', entry.filename, err);
                            }
                        }
                    }
                    
                    await zipReader.close();
                    
                    this.updateProgress(85, '正在计算健康度...');
                    
                    if (batteryInfo) {
                        batteryInfo.brand = detectedBrand;
                        resolve(batteryInfo);
                    } else {
                        reject(new Error('未找到电池健康度信息。请确保上传的是正确的安卓手机诊断文件。'));
                    }
                    
                } catch (error) {
                    reject(error);
                }
            };
            
            reader.onerror = () => reject(new Error('文件读取失败'));
            reader.readAsArrayBuffer(file);
        });
    },
    
    /**
     * 显示结果
     */
    displayResult(result, initialCapacity) {
        const healthPercentage = ((result.currentCapacity / initialCapacity) * 100).toFixed(1);
        
        // 更新数值
        document.getElementById('initial-capacity-value').textContent = initialCapacity;
        document.getElementById('current-capacity-value').textContent = result.currentCapacity;
        document.getElementById('health-percentage').textContent = healthPercentage;
        document.getElementById('cycle-count').textContent = result.cycleCount || '未检测到';
        document.getElementById('battery-temp').textContent = result.batteryTemp ? result.batteryTemp.toFixed(1) : '未检测到';
        
        // 更新电池进度条
        const batteryLevel = document.getElementById('battery-level');
        batteryLevel.style.width = Math.min(healthPercentage, 100) + '%';
        
        // 设置健康状态
        const qualityIndicator = document.getElementById('quality-indicator');
        let qualityText = '';
        let qualityClass = '';
        
        if (healthPercentage >= 85) {
            qualityText = '电池状态良好';
            qualityClass = 'quality-good';
        } else if (healthPercentage >= 70) {
            qualityText = '电池状态一般';
            qualityClass = 'quality-fair';
        } else {
            qualityText = '电池状态较差，建议更换';
            qualityClass = 'quality-poor';
        }
        
        const iconClass = healthPercentage >= 85 ? 'check-circle' : healthPercentage >= 70 ? 'exclamation-circle' : 'times-circle';
        qualityIndicator.innerHTML = `<span class="${qualityClass}"><i class="fas fa-${iconClass}"></i> ${qualityText}</span>`;
        
        // 显示原始数据
        if (result.rawContent) {
            document.getElementById('original-text').textContent = result.rawContent;
        }
        
        // 显示结果
        this.elements.result.classList.add('show');
        this.elements.result.scrollIntoView({ behavior: 'smooth' });
    },
    
    /**
     * 切换原始文本显示
     */
    toggleRawText() {
        const textDiv = document.getElementById('original-text');
        const btn = document.getElementById('toggle-text');
        
        if (textDiv.classList.contains('show')) {
            textDiv.classList.remove('show');
            btn.innerHTML = '<i class="fas fa-eye"></i> 显示详情';
        } else {
            textDiv.classList.add('show');
            btn.innerHTML = '<i class="fas fa-eye-slash"></i> 隐藏详情';
        }
    },
    
    /**
     * 渲染历史记录
     */
    renderHistory() {
        const historyList = this.elements.historyList;
        const history = HistoryManager.getAll();
        
        if (history.length === 0) {
            historyList.innerHTML = '<div class="history-empty">暂无历史记录</div>';
            return;
        }
        
        historyList.innerHTML = history.map(item => `
            <div class="history-item" data-id="${item.id}">
                <div>
                    <div class="history-date">${HistoryManager.formatDate(item.timestamp)}</div>
                    <div style="font-size: 0.85rem; color: #7f8c8d;">
                        ${item.initialCapacity}mAh → ${item.currentCapacity}mAh
                        ${item.cycleCount ? `· ${item.cycleCount}次循环` : ''}
                    </div>
                </div>
                <div class="history-health ${HistoryManager.getHealthColor(item.healthPercentage)}">
                    ${item.healthPercentage}%
                </div>
            </div>
        `).join('');
    },
    
    /**
     * 初始化电池数据库
     */
    initBatteryDatabase() {
        const searchInput = this.elements.capacitySearchInput;
        if (!searchInput) return;
        
        // 添加搜索建议
        searchInput.addEventListener('input', this.debounce(() => {
            const keyword = searchInput.value.trim();
            if (keyword.length < 2) return;
            
            const results = BatteryDatabase.search(keyword);
            this.showCapacitySearchResults(results);
        }, 300));
    },
    
    /**
     * 搜索电池容量
     */
    searchCapacity() {
        const keyword = this.elements.capacitySearchInput.value.trim();
        if (keyword.length < 2) {
            alert('请输入至少2个字符');
            return;
        }
        
        const results = BatteryDatabase.search(keyword);
        this.showCapacitySearchResults(results);
    },
    
    /**
     * 显示容量搜索结果
     */
    showCapacitySearchResults(results) {
        const resultEl = this.elements.capacitySearchResult;
        
        if (results.length === 0) {
            resultEl.innerHTML = '<p style="color: #7f8c8d;">未找到匹配的手机型号</p>';
            resultEl.classList.add('show');
            return;
        }
        
        resultEl.innerHTML = results.map(item => `
            <div style="padding: 10px; border-bottom: 1px solid #f0f4f8; cursor: pointer;" 
                 onclick="BatteryHealthApp.selectCapacity(${item.capacity})">
                <strong>${item.brand} ${item.model}</strong>
                <span style="float: right; color: var(--primary); font-weight: 600;">${item.capacity} mAh</span>
            </div>
        `).join('');
        
        resultEl.classList.add('show');
    },
    
    /**
     * 选择容量
     */
    selectCapacity(capacity) {
        this.elements.initialCapacityInput.value = capacity;
        this.elements.capacitySearchResult.classList.remove('show');
        this.validateCapacity();
    },
    
    /**
     * 清空历史记录
     */
    clearHistory() {
        if (confirm('确定要清空所有历史记录吗？')) {
            HistoryManager.clear();
            this.renderHistory();
        }
    }
};

// 初始化应用
document.addEventListener('DOMContentLoaded', () => {
    BatteryHealthApp.init();
});

// 导出模块
if (typeof module !== 'undefined' && module.exports) {
    module.exports = BatteryHealthApp;
}
