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
    
    // 当前分析结果
    currentResult: null,
    
    /**
     * 初始化应用
     */
    init() {
        this.cacheElements();
        this.bindEvents();
        this.renderHistory();
        this.renderTrendChart();
        this.initBatteryDatabase();
        this.checkFirstVisit();
        
        // 禁用 zip.js web worker（在 WebView 中可能有问题）
        if (typeof zip !== 'undefined') {
            zip.useWebWorkers = false;
        }
        
        // 更新数据库统计
        this.updateDatabaseStats();
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
            trendChart: document.getElementById('trend-chart'),
            capacitySearchInput: document.getElementById('capacity-search-input'),
            capacitySearchResult: document.getElementById('capacity-search-result'),
            maintenanceList: document.getElementById('maintenance-list'),
            toast: document.getElementById('toast'),
            guideOverlay: document.getElementById('guide-overlay')
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
        
        // 快捷品牌选择按钮
        document.querySelectorAll('.brand-quick-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const brand = btn.getAttribute('data-brand');
                this.elements.capacitySearchInput.value = brand;
                this.searchCapacity();
                
                // 更新按钮状态
                document.querySelectorAll('.brand-quick-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
            });
        });
        
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
        
        // 设置加载状态 - 显示旋转图标
        calculateBtn.disabled = true;
        calculateBtn.classList.add('loading');
        const btnIcon = calculateBtn.querySelector('i');
        const originalIcon = btnIcon.className;
        btnIcon.className = 'fas fa-spinner';
        calculateBtn.querySelector('span').textContent = '正在分析...';
        
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
            btnIcon.className = originalIcon;
            calculateBtn.querySelector('span').textContent = '分析电池健康度';
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
        
        // 保存当前结果供分享/保存功能使用
        this.currentResult = {
            brand: result.brand,
            initialCapacity: initialCapacity,
            currentCapacity: result.currentCapacity,
            healthPercentage: healthPercentage,
            cycleCount: result.cycleCount,
            batteryTemp: result.batteryTemp
        };
        
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
        
        // 显示保养建议
        this.showMaintenanceAdvice(parseFloat(healthPercentage));
        
        // 更新趋势图表
        this.renderTrendChart();
        
        // 显示结果
        this.elements.result.classList.add('show');
        this.elements.result.scrollIntoView({ behavior: 'smooth' });
        
        // 显示成功提示
        this.showToast('分析完成！', 'success');
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
            historyList.innerHTML = '<div class="history-empty" role="listitem">暂无历史记录</div>';
            return;
        }
        
        historyList.innerHTML = history.map(item => `
            <div class="history-item" role="listitem" data-id="${item.id}">
                <div class="info">
                    <div class="history-date">${HistoryManager.formatDate(item.timestamp)}</div>
                    <div class="history-detail">${item.initialCapacity}mAh → ${item.currentCapacity}mAh ${item.cycleCount ? `· ${item.cycleCount}次循环` : ''}</div>
                </div>
                <div class="history-health ${HistoryManager.getHealthColor(item.healthPercentage)}">
                    ${item.healthPercentage}%
                </div>
                <button class="delete-btn" onclick="BatteryHealthApp.deleteHistoryItem(${item.id})" aria-label="删除此记录">
                    <i class="fas fa-times"></i>
                </button>
            </div>
        `).join('');
    },
    
    /**
     * 删除单条历史记录
     */
    deleteHistoryItem(id) {
        if (confirm('确定删除此记录？')) {
            HistoryManager.delete(id);
            this.renderHistory();
        }
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
            this.renderTrendChart();
            this.showToast('历史记录已清空', 'success');
        }
    },
    
    /**
     * 渲染趋势图表
     */
    renderTrendChart() {
        const trendChart = this.elements.trendChart;
        const trend = HistoryManager.getTrend();
        
        if (trend.length < 2) {
            trendChart.innerHTML = '<div class="trend-empty">暂无足够数据显示趋势（需要至少2条记录）</div>';
            return;
        }
        
        // 计算最大值用于比例
        const maxHealth = Math.max(...trend.map(t => t.health));
        
        trendChart.innerHTML = trend.slice(-10).map(item => {
            const height = Math.round((item.health / maxHealth) * 80);
            return `<div class="trend-bar" style="height: ${height}px" data-value="${item.health}%"></div>`;
        }).join('');
    },
    
    /**
     * 更新数据库统计
     */
    updateDatabaseStats() {
        const stats = BatteryDatabase.getStats();
        const modelCount = document.getElementById('db-model-count');
        const brandCount = document.getElementById('db-brand-count');
        
        if (modelCount) modelCount.textContent = stats.totalModels;
        if (brandCount) brandCount.textContent = stats.totalBrands;
    },
    
    /**
     * 检查首次访问
     */
    checkFirstVisit() {
        const hasVisited = localStorage.getItem('battery_health_visited');
        if (!hasVisited) {
            this.elements.guideOverlay.classList.add('show');
            this.elements.guideOverlay.setAttribute('aria-hidden', 'false');
        }
    },
    
    /**
     * 关闭引导
     */
    closeGuide() {
        this.elements.guideOverlay.classList.remove('show');
        this.elements.guideOverlay.setAttribute('aria-hidden', 'true');
        localStorage.setItem('battery_health_visited', 'true');
    },
    
    /**
     * 显示 Toast 提示
     */
    showToast(message, type = 'info') {
        const toast = this.elements.toast;
        toast.textContent = message;
        toast.className = `toast show ${type}`;
        
        setTimeout(() => {
            toast.classList.remove('show');
        }, 3000);
    },
    
    /**
     * 生成保养建议
     */
    generateMaintenanceAdvice(healthPercentage) {
        const advice = [];
        
        if (healthPercentage >= 90) {
            advice.push({
                icon: 'fa-check-circle',
                title: '电池状态优秀',
                text: '继续保持良好的充电习惯，避免过度放电和高温环境'
            });
            advice.push({
                icon: 'fa-battery-full',
                title: '日常保养',
                text: '建议每月进行一次完整的充放电循环（0%-100%）'
            });
        } else if (healthPercentage >= 80) {
            advice.push({
                icon: 'fa-info-circle',
                title: '电池正常衰减',
                text: '这是正常的电池老化，无需过度担心'
            });
            advice.push({
                icon: 'fa-plug',
                title: '充电建议',
                text: '避免长时间充电，充到80%-90%即可拔掉'
            });
            advice.push({
                icon: 'fa-temperature-low',
                title: '温度控制',
                text: '充电时避免高温环境，不要边充边玩大型游戏'
            });
        } else if (healthPercentage >= 70) {
            advice.push({
                icon: 'fa-exclamation-triangle',
                title: '电池轻度老化',
                text: '续航开始下降，建议关注电池状态变化'
            });
            advice.push({
                icon: 'fa-sync',
                title: '校准建议',
                text: '可尝试电池校准：充满电后继续充30分钟，然后用到自动关机'
            });
            advice.push({
                icon: 'fa-clock',
                title: '更换时机',
                text: '建议在健康度低于70%时考虑更换电池'
            });
        } else {
            advice.push({
                icon: 'fa-times-circle',
                title: '电池严重老化',
                text: '电池状态较差，建议尽快更换以保证正常使用'
            });
            advice.push({
                icon: 'fa-tools',
                title: '更换建议',
                text: '建议到官方售后或正规维修店更换原装电池'
            });
            advice.push({
                icon: 'fa-shield-alt',
                title: '安全提醒',
                text: '如发现鼓包、异味等情况，请立即停止使用并更换'
            });
        }
        
        return advice;
    },
    
    /**
     * 显示保养建议
     */
    showMaintenanceAdvice(healthPercentage) {
        const advice = this.generateMaintenanceAdvice(healthPercentage);
        const maintenanceList = this.elements.maintenanceList;
        
        maintenanceList.innerHTML = advice.map(item => `
            <div class="maintenance-item">
                <div class="maintenance-icon"><i class="fas ${item.icon}"></i></div>
                <div class="maintenance-content">
                    <h4>${item.title}</h4>
                    <p>${item.text}</p>
                </div>
            </div>
        `).join('');
    },
    
    /**
     * 分享报告
     */
    shareReport() {
        if (!this.currentResult) {
            this.showToast('请先完成电池健康度分析', 'error');
            return;
        }
        
        // 尝试使用 Web Share API
        if (navigator.share) {
            const shareData = {
                title: '电池健康度报告',
                text: `我的电池健康度为 ${this.currentResult.healthPercentage}%，当前容量 ${this.currentResult.currentCapacity}mAh`,
                url: window.location.href
            };
            
            navigator.share(shareData)
                .then(() => this.showToast('分享成功', 'success'))
                .catch(err => this.showToast('分享失败', 'error'));
        } else {
            // 复制到剪贴板
            const text = `电池健康度报告\n健康度: ${this.currentResult.healthPercentage}%\n当前容量: ${this.currentResult.currentCapacity}mAh\n初始容量: ${this.currentResult.initialCapacity}mAh`;
            
            navigator.clipboard.writeText(text)
                .then(() => this.showToast('报告已复制到剪贴板', 'success'))
                .catch(() => this.showToast('复制失败，请手动复制', 'error'));
        }
    },
    
    /**
     * 保存报告
     */
    saveReport() {
        if (!this.currentResult) {
            this.showToast('请先完成电池健康度分析', 'error');
            return;
        }
        
        // 生成报告文本
        const report = `
电池健康度分析报告
========================

分析时间: ${new Date().toLocaleString('zh-CN')}
品牌识别: ${this.currentResult.brand || '未知'}

电池数据:
- 初始容量: ${this.currentResult.initialCapacity} mAh
- 当前容量: ${this.currentResult.currentCapacity} mAh
- 健康度: ${this.currentResult.healthPercentage}%
- 循环次数: ${this.currentResult.cycleCount || '未检测到'}
- 电池温度: ${this.currentResult.batteryTemp || '未检测到'} °C

健康状态: ${this.currentResult.healthPercentage >= 85 ? '良好' : this.currentResult.healthPercentage >= 70 ? '一般' : '较差'}

========================
本报告由电池健康度分析工具生成
        `.trim();
        
        // 创建下载
        const blob = new Blob([report], { type: 'text/plain;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `电池健康报告_${new Date().toISOString().slice(0,10)}.txt`;
        a.click();
        URL.revokeObjectURL(url);
        
        this.showToast('报告已保存', 'success');
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
