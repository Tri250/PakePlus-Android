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
        // 历史记录搜索
        const historySearch = document.getElementById('history-search');
        if (historySearch) {
            historySearch.addEventListener('input', this.debounce(() => this.renderHistory(), 300));
        }
        const historyBrandFilter = document.getElementById('history-brand-filter');
        if (historyBrandFilter) {
            historyBrandFilter.addEventListener('change', () => this.renderHistory());
        }
        this.renderTrendChart();
        this.initBatteryDatabase();
        // 恢复上次输入的容量
        const savedCapacity = localStorage.getItem('battery_health_last_capacity');
        if (savedCapacity && this.elements.initialCapacityInput) {
            this.elements.initialCapacityInput.value = savedCapacity;
        }
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
        
        // 文件选择事件
        fileInput.addEventListener('change', (e) => this.handleFileSelect(e));
        
        // 注意：dropArea的click事件由Android端injectFilePickerScript注入统一处理
        // 避免重复触发文件选择器
        if (!window.AndroidFilePicker) {
            // 仅在Android接口不可用时才使用web方式
            dropArea.addEventListener('click', (e) => {
                e.preventDefault();
                fileInput.click();
            });
        }
        
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
     * 同时支持File对象和Android URI对象
     */
    validateAndSetFile(file) {
        const errorEl = this.elements.fileValidationError;
        
        // 输入验证：确保 file 对象存在
        if (!file) {
            if (errorEl) {
                errorEl.textContent = '请选择有效的文件';
                errorEl.classList.add('show');
            }
            this.currentFile = null;
            return false;
        }
        
        // 验证文件类型 - 兼容File对象和Android URI对象
        const fileName = file.name || '';
        if (!fileName || typeof fileName !== 'string') {
            if (errorEl) {
                errorEl.textContent = '无法识别文件名';
                errorEl.classList.add('show');
            }
            this.currentFile = null;
            return false;
        }
        
        // 净化文件名（防止 XSS）
        const sanitizedFileName = fileName.replace(/[<>'"&]/g, '');
        
        if (!sanitizedFileName.toLowerCase().endsWith('.zip')) {
            if (errorEl) {
                errorEl.textContent = '请选择 ZIP 格式的文件';
                errorEl.classList.add('show');
            }
            this.currentFile = null;
            return false;
        }
        
        // 验证文件大小（最大 500MB）- 仅对File对象检查
        // 原生解析使用 ZipInputStream 流式扫描，500MB 也不卡
        if (file.size && typeof file.size === 'number' && file.size > 500 * 1024 * 1024) {
            if (errorEl) {
                errorEl.textContent = '文件过大，请选择小于 500MB 的文件';
                errorEl.classList.add('show');
            }
            this.currentFile = null;
            return false;
        }
        
        // 验证通过
        if (errorEl) {
            errorEl.classList.remove('show');
        }
        this.currentFile = file;
        if (this.elements.selectedFileName) {
            // 使用 textContent 防止 XSS
            this.elements.selectedFileName.textContent = sanitizedFileName;
        }
        // 显示文件大小
        const fileSizeEl = document.getElementById('selected-file-size');
        if (fileSizeEl && file.size && typeof file.size === 'number' && file.size > 0) {
            const sizeMB = (file.size / (1024 * 1024)).toFixed(1);
            fileSizeEl.textContent = '(' + sizeMB + 'MB)';
        }
        if (this.elements.fileNameDisplay) {
            this.elements.fileNameDisplay.style.display = 'flex';
        }
        
        console.log('File validated and set:', this.currentFile);
        return true;
    },
    
    /**
     * 验证容量输入
     */
    validateCapacity() {
        const inputValue = this.elements.initialCapacityInput.value;
        const value = parseInt(inputValue);
        const input = this.elements.initialCapacityInput;
        
        // 输入验证：确保是有效数字
        if (inputValue && (isNaN(value) || value < 1000 || value > 10000)) {
            input.classList.add('error');
            return false;
        } else {
            input.classList.remove('error');
            // 保存容量到localStorage（仅保存有效值）
            if (value && value >= 500 && value <= 30000) {
                try {
                    localStorage.setItem('battery_health_last_capacity', String(value));
                } catch (e) {
                    console.warn('Failed to save capacity to localStorage:', e);
                }
            }
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
        
        console.log('=== handleAnalyze called ===');
        console.log('currentFile:', this.currentFile);
        
        // 验证文件
        if (!this.currentFile) {
            console.warn('No file selected');
            this.showStatus('请选择诊断文件', true);
            return;
        }
        
        // 验证文件类型 - 兼容File对象和Android URI对象
        const fileName = this.currentFile.name || '';
        if (!fileName || typeof fileName !== 'string' || !fileName.toLowerCase().endsWith('.zip')) {
            console.warn('Invalid file type:', fileName);
            this.showStatus('请选择 ZIP 格式的文件', true);
            return;
        }
        
        // 验证容量
        const capacityInputValue = initialCapacityInput.value;
        const initialCapacity = parseInt(capacityInputValue);
        if (!capacityInputValue || isNaN(initialCapacity) || initialCapacity <= 0) {
            this.showStatus('请输入有效的初始电池容量', true);
            initialCapacityInput.classList.add('error');
            return;
        }
        
        // 容量范围验证
        if (initialCapacity < 500 || initialCapacity > 30000) {
            this.showStatus('电池容量应在 500-30000 mAh 范围内', true);
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
            this.updateProgress(10, '第1步/4步：正在读取文件...');
            // 大文件警告
            if (this.currentFile.size && typeof this.currentFile.size === 'number' && this.currentFile.size > 200 * 1024 * 1024) {
                const sizeMB = (this.currentFile.size / (1024 * 1024)).toFixed(0);
                this.updateProgress(8, '大文件(' + sizeMB + 'MB)，解析可能需要较长时间...');
                await new Promise(r => setTimeout(r, 100));
            }
            const result = await this.analyzeZipFile(this.currentFile, initialCapacity);
            
            this.updateProgress(95, '第4步/4步：正在生成报告...');
            this.updateProgress(100, '分析完成');
            this.displayResult(result, initialCapacity);
            
            // 保存到历史记录
            // v2.1.9 修复：使用 designCapacity 计算健康度
            const designCapForHistory = result.designCapacity > 0 ? result.designCapacity : initialCapacity;
            const healthPctForHistory = designCapForHistory > 0
                ? ((result.currentCapacity / designCapForHistory) * 100).toFixed(1)
                : '0';
            HistoryManager.add({
                brand: result.brand || 'unknown',
                initialCapacity: initialCapacity,
                designCapacity: result.designCapacity,
                currentCapacity: result.currentCapacity,
                healthPercentage: healthPctForHistory,
                cycleCount: result.cycleCount,
                batteryTemp: result.batteryTemp
            });
            
            // 刷新历史记录
            this.renderHistory();
            
        } catch (error) {
            this.showStatus('分析失败: ' + (error.message || '未知错误'), true);
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
     * v2.1.7 关键重构：完全使用 Android 原生 ZIP 流式解析，避免 200MB+ 大文件 OOM 闪退
     * 不再读取/传输整个文件到 JS，只接收小型 JSON 结果
     */
    async analyzeZipFile(file, initialCapacity) {
        return new Promise((resolve, reject) => {
            // 检查是否是Android URI文件
            if (file.isAndroidUri && file.uri) {
                console.log('Processing Android URI file via native parser:', file.uri);

                if (window.AndroidFilePicker) {
                    // 关键路径：使用 Android 原生 ZipInputStream 流式解析
                    // 内存占用 O(1)，不传输大文件到 JS
                    if (typeof window.AndroidFilePicker.analyzeZipNative === 'function') {
                        this.updateProgress(5, '正在启动原生解析引擎...');

                        this._fileReadResolve = resolve;
                        this._fileReadReject = reject;
                        this._currentInitialCapacity = initialCapacity;

                        window.AndroidFilePicker.analyzeZipNative(
                            file.uri,
                            'onNativeAnalyzeComplete',
                            'onNativeAnalyzeError'
                        );
                        return;
                    }

                    // 旧回退路径：使用 Base64 传输（小文件可用，>50MB 极易 OOM）
                    if (typeof window.AndroidFilePicker.readFileAsBase64 === 'function') {
                        this.updateProgress(5, '正在准备读取文件...');
                        this._fileReadResolve = resolve;
                        this._fileReadReject = reject;
                        this._currentInitialCapacity = initialCapacity;
                        window.AndroidFilePicker.readFileAsBase64(
                            file.uri, 'onFileReadComplete', 'onFileReadError', 5, 50
                        );
                        return;
                    }

                    if (typeof window.AndroidFilePicker.readFileContentWithProgress === 'function') {
                        this.updateProgress(5, '正在准备读取文件...');
                        this._fileReadResolve = resolve;
                        this._fileReadReject = reject;
                        this._currentInitialCapacity = initialCapacity;
                        window.AndroidFilePicker.readFileContentWithProgress(
                            file.uri, 'onFileReadComplete', 5, 50
                        );
                        return;
                    }

                    reject(new Error('Android接口不可用'));
                } else {
                    reject(new Error('Android文件接口不可用'));
                }
                return;
            }

            // 常规文件处理（浏览器环境，用于调试）
            const reader = new FileReader();
            reader.onprogress = (e) => {
                if (e.lengthComputable) {
                    const percent = Math.round((e.loaded / e.total) * 30) + 10;
                    this.updateProgress(percent, `正在读取文件... ${(e.loaded / 1024 / 1024).toFixed(1)}MB / ${(e.total / 1024 / 1024).toFixed(1)}MB`);
                }
            };
            reader.onload = async (e) => {
                try {
                    const blob = new Blob([e.target.result]);
                    this.processZipBlob(blob, initialCapacity, resolve, reject);
                } catch (error) {
                    reject(error);
                }
            };
            reader.onerror = () => reject(new Error('文件读取失败'));
            reader.readAsArrayBuffer(file);
        });
    },

    /**
     * 原生解析进度回调（Java → JS）
     * 由 MainActivity.analyzeZipNative 通过 evaluateJavascript 调用
     * @param {number} processed - 已处理的电池相关 entry 数
     * @param {number} total - 已扫描的总 entry 数
     * @param {string} currentName - 当前 entry 文件名
     * @param {number} bestCurrent - 当前最佳容量匹配
     * @param {number} bestCycle - 当前最佳循环次数匹配
     */
    onNativeProgress(processed, total, currentName, bestCurrent, bestCycle) {
        // 5% ~ 90% 进度区间
        const percent = total > 0
            ? Math.min(90, 5 + Math.round((processed / Math.min(total, 20)) * 85))
            : Math.min(90, 5 + processed * 4);

        let msg = `正在解析 (${processed}/${total || '?'}): ${currentName || ''}`;
        if (bestCurrent > 0) {
            msg += ` · 已识别 ${bestCurrent}mAh`;
        }
        if (bestCycle > 0) {
            msg += ` · ${bestCycle} 次循环`;
        }
        this.updateProgress(percent, msg);
        console.log('[NativeProgress]', percent + '%', msg);
    },

    /**
     * 原生解析完成回调（Java → JS）
     * 接收一个小型 JSON 字符串作为参数
     */
    onNativeAnalyzeComplete(jsonString) {
        console.log('=== Native analyze complete ===');
        try {
            const result = typeof jsonString === 'string' ? JSON.parse(jsonString) : jsonString;
            console.log('Parsed result:', result);

            this.updateProgress(95, '正在生成报告...');

            // 归一化字段
            if (typeof result.currentCapacity !== 'number') result.currentCapacity = 0;
            if (typeof result.designCapacity !== 'number') result.designCapacity = 0;
            if (typeof result.cycleCount !== 'number') result.cycleCount = 0;
            if (typeof result.batteryTemp !== 'number') result.batteryTemp = 0;
            if (typeof result.chargeCounter !== 'number') result.chargeCounter = 0;
            if (typeof result.voltage !== 'number') result.voltage = 0;
            if (!result.brand) result.brand = 'generic';

            if (!result.currentCapacity && !result.cycleCount && !result.batteryTemp) {
                // 有 debugInfo 就显示它，帮助用户理解问题
                const debugMsg = result.debugInfo || '未找到有效的电池数据';
                console.warn('No valid battery data found. Debug:', debugMsg);
                if (this._fileReadReject) {
                    this._fileReadReject(new Error('未在文件中找到电池健康度信息。\n\n诊断信息：' + debugMsg.replace(/\n/g, ' ')));
                }
                return;
            }

            this.updateProgress(100, '分析完成');
            if (this._fileReadResolve) {
                this._fileReadResolve(result);
            }
        } catch (error) {
            console.error('Failed to parse native result:', error, jsonString);
            if (this._fileReadReject) {
                this._fileReadReject(new Error('解析结果失败: ' + error.message));
            }
        }
    },

    /**
     * 原生解析错误回调（Java → JS）
     */
    onNativeAnalyzeError(errorMessage) {
        console.error('Native analyze error:', errorMessage);
        if (this._fileReadReject) {
            const cnMsg = {
                'OutOfMemoryError': '内存不足，请关闭其他应用后重试',
                'FileNotFoundException': '文件不存在，请重新选择',
                'IOException': '文件读取失败，请重新选择',
                'SecurityException': '没有文件访问权限',
                'NullPointerException': '解析异常，请尝试重新分析'
            }[errorMessage] || errorMessage || '解析失败，请重试';
            this._fileReadReject(new Error(cnMsg));
        }
    },

    /**
     * 处理Base64编码的文件内容（向后兼容）
     */
    _processBase64Content(base64Content, initialCapacity, resolve, reject) {
        try {
            // 将Base64转换为Blob
            const binaryString = atob(base64Content.trim());
            const bytes = new Uint8Array(binaryString.length);
            for (let i = 0; i < binaryString.length; i++) {
                bytes[i] = binaryString.charCodeAt(i);
            }
            const blob = new Blob([bytes], { type: 'application/zip' });

            // 解压ZIP文件
            this.processZipBlob(blob, initialCapacity, resolve, reject);
        } catch (error) {
            console.error('Failed to process Base64 content:', error);
            reject(new Error('文件处理失败: ' + error.message));
        }
    },

    /**
     * Android文件读取完成回调
     * 关键修复：直接接收 Base64 数据，避免 WebViewAssetLoader fetch 失败问题
     * @param {Object|string} fileInfo - 文件信息对象 {data, size, name, path} 或 旧版本的Base64字符串
     */
    onFileReadComplete(fileInfo) {
        console.log('=== File read complete ===');
        console.log('File info type:', typeof fileInfo);

        // 旧版本：直接是 Base64 字符串
        if (typeof fileInfo === 'string') {
            console.warn('Legacy Base64 response, processing...');
            if (!fileInfo) {
                if (this._fileReadReject) {
                    this._fileReadReject(new Error('无法读取文件内容'));
                }
                return;
            }

            this.updateProgress(55, '正在解压文件...');

            try {
                this._processBase64Content(
                    fileInfo,
                    this._currentInitialCapacity,
                    this._fileReadResolve,
                    this._fileReadReject
                );
            } catch (error) {
                console.error('Failed to process file:', error);
                if (this._fileReadReject) {
                    this._fileReadReject(error);
                }
            }
            return;
        }

        // 新版本：文件信息对象
        if (!fileInfo || (!fileInfo.data && !fileInfo.url)) {
            if (this._fileReadReject) {
                this._fileReadReject(new Error('文件信息无效'));
            }
            return;
        }

        const initialCapacity = this._currentInitialCapacity;

        // 关键修复：直接处理 Base64 数据（避免 WebViewAssetLoader URL 拦截在某些设备上失败）
        if (fileInfo.data) {
            console.log('Processing Base64 data, size:', fileInfo.size);
            this.updateProgress(60, '正在解码文件...');

            try {
                // Base64 转 ArrayBuffer
                const base64Data = fileInfo.data;
                const binaryString = atob(base64Data);
                const bytes = new Uint8Array(binaryString.length);
                for (let i = 0; i < binaryString.length; i++) {
                    bytes[i] = binaryString.charCodeAt(i);
                }
                const blob = new Blob([bytes], { type: 'application/zip' });
                console.log('Base64 decoded, blob size:', blob.size);

                this.updateProgress(70, '正在解压文件...');

                // 清理临时文件
                if (window.AndroidFilePicker && fileInfo.path) {
                    try {
                        window.AndroidFilePicker.deleteTempFile(fileInfo.path);
                    } catch (e) {
                        console.warn('Failed to delete temp file:', e);
                    }
                }

                // 解析 ZIP
                this.processZipBlob(blob, initialCapacity, this._fileReadResolve, this._fileReadReject);
            } catch (error) {
                console.error('Failed to decode base64:', error);
                if (window.AndroidFilePicker && fileInfo.path) {
                    try {
                        window.AndroidFilePicker.deleteTempFile(fileInfo.path);
                    } catch (e) {}
                }
                if (this._fileReadReject) {
                    this._fileReadReject(new Error('文件解码失败: ' + error.message));
                }
            }
            return;
        }

        // 兼容旧版本 URL 方式（保留作为降级）
        if (fileInfo.url) {
            console.warn('Falling back to URL fetch:', fileInfo.url);
            this.updateProgress(55, '正在加载文件...');

            const fileUrl = fileInfo.url;
            const filePath = fileInfo.path;

            fetch(fileUrl, {
                method: 'GET',
                cache: 'no-store',
                credentials: 'omit'
            })
                .then(response => {
                    if (!response.ok) {
                        throw new Error('文件读取失败: ' + response.status);
                    }
                    return response.blob();
                })
                .then(blob => {
                    console.log('File loaded, size:', blob.size);
                    this.updateProgress(60, '正在解压文件...');

                    if (window.AndroidFilePicker && filePath) {
                        try {
                            window.AndroidFilePicker.deleteTempFile(filePath);
                        } catch (e) {
                            console.warn('Failed to delete temp file:', e);
                        }
                    }

                    return this.processZipBlob(blob, initialCapacity, this._fileReadResolve, this._fileReadReject);
                })
                .catch(error => {
                    console.error('Failed to load file:', error);
                    if (window.AndroidFilePicker && filePath) {
                        try {
                            window.AndroidFilePicker.deleteTempFile(filePath);
                        } catch (e) {}
                    }
                    if (this._fileReadReject) {
                        this._fileReadReject(new Error('文件加载失败: ' + error.message));
                    }
                });
        }
    },

    /**
     * Android文件读取错误回调
     */
    onFileReadError(errorMessage) {
        console.error('File read error:', errorMessage);
        if (this._fileReadReject) {
            this._fileReadReject(new Error(errorMessage || '文件读取失败'));
        }
    },
    
    /**
     * 处理ZIP Blob文件
     */
    async processZipBlob(blob, initialCapacity, resolve, reject) {
        try {
            this.updateProgress(40, '正在解压文件...');
            
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
            console.error('processZipBlob error:', error);
            reject(error);
        }
    },
    
    /**
     * 显示结果
     * 融合新算法：使用综合评级算法 calculateBatteryGrade
     * v2.1.9 修复：使用从文件解析的 designCapacity 计算健康度，而非用户输入的 initialCapacity
     */
    displayResult(result, initialCapacity) {
        // 优先使用从文件解析的设计容量，如果没有则使用用户输入的初始容量
        const designCap = result.designCapacity > 0 ? result.designCapacity : initialCapacity;
        const healthPercentage = designCap > 0
            ? parseFloat(((result.currentCapacity / designCap) * 100).toFixed(1))
            : 0;

        // 使用新的综合评级算法
        const batteryGrade = BatteryParsers.calculateBatteryGrade(
            healthPercentage,
            result.cycleCount || 0,
            result.batteryTemp || null
        );

        // 获取温度状态
        const tempStatus = BatteryParsers.getTemperatureStatus(result.batteryTemp || null);

        // 保存当前结果供分享/保存功能使用
        this.currentResult = {
            brand: result.brand,
            initialCapacity: initialCapacity,
            designCapacity: result.designCapacity,
            currentCapacity: result.currentCapacity,
            healthPercentage: healthPercentage,
            cycleCount: result.cycleCount,
            batteryTemp: result.batteryTemp,
            healthGrade: batteryGrade,
            tempStatus: tempStatus,
            voltage: result.voltage,
            technology: result.technology
        };

        // 更新数值 - 添加动画效果
        const statValues = document.querySelectorAll('.stat-value');
        statValues.forEach(el => el.classList.add('animate'));

        const initialCapEl = document.getElementById('initial-capacity-value');
        const currentCapEl = document.getElementById('current-capacity-value');
        const healthPctEl = document.getElementById('health-percentage');
        const cycleCountEl = document.getElementById('cycle-count');
        const batteryTempEl = document.getElementById('battery-temp');

        // 显示设计容量（从文件解析）而非用户输入的初始容量
        if (initialCapEl) initialCapEl.textContent = designCap > 0 ? designCap : initialCapacity;
        if (currentCapEl) currentCapEl.textContent = result.currentCapacity > 0 ? result.currentCapacity : '未检测到';
        if (healthPctEl) healthPctEl.textContent = result.currentCapacity > 0 ? healthPercentage : '0';
        if (cycleCountEl) cycleCountEl.textContent = result.cycleCount > 0 ? result.cycleCount : '未检测到';
        if (batteryTempEl) {
            if (result.batteryTemp && result.batteryTemp > -30 && result.batteryTemp < 80) {
                batteryTempEl.textContent = result.batteryTemp.toFixed(1) + '°C';
            } else {
                batteryTempEl.textContent = '未检测到';
            }
        }

        // 电池电压
        const voltageEl = document.getElementById('battery-voltage');
        if (voltageEl) {
            voltageEl.textContent = result.voltage > 0 ? result.voltage : '未检测到';
        }

        // 电池技术
        const techEl = document.getElementById('battery-technology');
        if (techEl) {
            techEl.textContent = result.technology || '未检测到';
        }

        // 置信度
        const confEl = document.getElementById('confidence-value');
        if (confEl) {
            confEl.textContent = result.confidence > 0 ? Math.round(result.confidence * 100) : '-';
        }

        // 移除动画类
        setTimeout(() => {
            statValues.forEach(el => el.classList.remove('animate'));
        }, 300);

        // 更新电池进度条
        const batteryLevel = document.getElementById('battery-level');
        if (batteryLevel) {
            batteryLevel.style.width = Math.min(healthPercentage, 100) + '%';
            batteryLevel.classList.add('animate');
        }

        // 设置健康状态（使用新的评级）
        const qualityIndicator = document.getElementById('quality-indicator');
        if (qualityIndicator) {
            let qualityText = batteryGrade.gradeDesc.split(' - ')[1] || '';
            let qualityClass = batteryGrade.gradeClass;
            const iconClass = batteryGrade.grade <= 'B' ? 'check-circle' : batteryGrade.grade === 'C' ? 'exclamation-circle' : 'times-circle';
            qualityIndicator.innerHTML = `<span class="${qualityClass}"><i class="fas fa-${iconClass}"></i> ${qualityText}</span>`;
        }

        // 显示原始数据
        const originalTextEl = document.getElementById('original-text');
        if (originalTextEl) {
            let debugContent = '';
            if (result.dataSource) {
                debugContent += '【数据来源】' + result.dataSource + '\n\n';
            }
            if (result.capacitySource) {
                debugContent += '【容量来源】' + result.capacitySource + ' → ' + result.currentCapacity + 'mAh\n';
            }
            if (result.cycleSource) {
                debugContent += '【循环来源】' + result.cycleSource + ' → ' + result.cycleCount + '\n';
            }
            if (result.tempSource) {
                debugContent += '【温度来源】' + result.tempSource + ' → ' + result.batteryTemp + '°C\n';
            }
            debugContent += '\n';
            if (result.kvMapDump) {
                debugContent += '【提取字段】' + result.kvMapDump + '\n\n';
            }
            if (result.debugInfo) {
                debugContent += '【调试信息】' + result.debugInfo + '\n\n';
            }
            if (result.rawContent) {
                debugContent += '【原始内容】\n' + result.rawContent;
            }
            originalTextEl.textContent = debugContent || '无调试信息';
        }

        // 显示保养建议（使用新的 MAINTENANCE_ADVICE）
        this.showMaintenanceAdviceNew(batteryGrade.grade);

        // 显示健康等级评估（使用新的评级）
        this.showHealthGradeNew(batteryGrade, tempStatus);

        // v2.1.17 新增：显示设备信息
        this.displayDeviceInfo(result);

        // 更新趋势图表
        this.renderTrendChart();

        // 添加统计卡片动画
        const statsGrid = document.querySelector('.stats-grid');
        if (statsGrid) {
            statsGrid.classList.add('animate');
        }

        // 显示结果
        this.elements.result.classList.add('show');
        this.elements.result.classList.add('animate-expand');
        this.elements.result.scrollIntoView({ behavior: 'smooth' });

        // 显示成功提示
        this.showToast('分析完成！', 'success');
    },
    
    /**
     * 显示健康等级评估
     * @param {Object} result - 解析结果
     */
    showHealthGrade(result) {
        const gradeBadge = document.getElementById('health-grade-badge');
        const gradeDescription = document.getElementById('health-grade-description');
        const gradeEstimated = document.getElementById('health-grade-estimated');
        const gradeCycleCount = document.getElementById('grade-cycle-count');
        const gradeEstimatedHealth = document.getElementById('grade-estimated-health');
        
        if (result.healthGrade) {
            const grade = result.healthGrade;
            
            // 设置等级徽章
            gradeBadge.textContent = grade.grade;
            gradeBadge.className = 'health-grade-badge';
            
            // 根据等级添加对应的样式类
            const gradeClassMap = {
                'A+': 'grade-a-plus',
                'A': 'grade-a',
                'B+': 'grade-b-plus',
                'B': 'grade-b',
                'C': 'grade-c',
                'D': 'grade-d',
                'E': 'grade-e'
            };
            gradeBadge.classList.add(gradeClassMap[grade.grade] || 'grade-b');
            
            // 设置描述和预估健康度
            gradeDescription.textContent = grade.description;
            gradeEstimated.textContent = `预估健康度范围：${grade.estimatedHealth}`;
            gradeCycleCount.textContent = grade.cycleCount + ' 次';
            gradeEstimatedHealth.textContent = grade.estimatedHealth;
        } else {
            // 如果没有循环次数数据，显示提示
            gradeBadge.textContent = '?';
            gradeBadge.className = 'health-grade-badge grade-b';
            gradeDescription.textContent = '无法评估健康等级';
            gradeEstimated.textContent = '诊断文件未包含循环次数数据';
            gradeCycleCount.textContent = '未检测到';
            gradeEstimatedHealth.textContent = '-';
        }
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
        let history = HistoryManager.getAll();
        
        // 搜索过滤
        const searchInput = document.getElementById('history-search');
        const brandFilter = document.getElementById('history-brand-filter');
        const searchKeyword = searchInput ? (searchInput.value || '').trim().toLowerCase() : '';
        const filterBrand = brandFilter ? (brandFilter.value || '') : '';
        
        if (searchKeyword || filterBrand) {
            history = history.filter(item => {
                let match = true;
                if (searchKeyword) {
                    const searchStr = ((item.brand || 'unknown') + ' ' + (item.initialCapacity || 0) + 'mAh ' + 
                        HistoryManager.formatDate(item.timestamp || new Date().toISOString())).toLowerCase();
                    match = searchStr.includes(searchKeyword);
                }
                if (filterBrand) {
                    match = match && (item.brand || '') === filterBrand;
                }
                return match;
            });
        }
        
        // 清空历史列表
        historyList.innerHTML = '';
        
        if (history.length === 0) {
            const emptyDiv = document.createElement('div');
            emptyDiv.className = 'history-empty';
            emptyDiv.setAttribute('role', 'listitem');
            emptyDiv.textContent = searchKeyword || filterBrand ? '没有匹配的记录' : '暂无历史记录';
            historyList.appendChild(emptyDiv);
            return;
        }
        
        // 使用 DOM API 创建元素，避免 XSS
        history.forEach(item => {
            const itemDiv = document.createElement('div');
            itemDiv.className = 'history-item';
            itemDiv.setAttribute('role', 'listitem');
            itemDiv.setAttribute('data-id', item.id || '');
            
            const infoDiv = document.createElement('div');
            infoDiv.className = 'info';
            
            const dateDiv = document.createElement('div');
            dateDiv.className = 'history-date';
            dateDiv.textContent = HistoryManager.formatDate(item.timestamp || new Date().toISOString());
            infoDiv.appendChild(dateDiv);
            
            const detailDiv = document.createElement('div');
            detailDiv.className = 'history-detail';
            const designCap = item.designCapacity || item.initialCapacity || 0;
            const currentCap = item.currentCapacity || 0;
            const cycles = item.cycleCount || 0;
            detailDiv.textContent = designCap + 'mAh → ' + currentCap + 'mAh' + (cycles ? ' · ' + cycles + '次循环' : '');
            infoDiv.appendChild(detailDiv);
            
            itemDiv.appendChild(infoDiv);
            
            const healthDiv = document.createElement('div');
            healthDiv.className = 'history-health ' + HistoryManager.getHealthColor(item.healthPercentage || 0);
            healthDiv.textContent = (item.healthPercentage || '0') + '%';
            itemDiv.appendChild(healthDiv);
            
            const deleteBtn = document.createElement('button');
            deleteBtn.className = 'delete-btn';
            deleteBtn.setAttribute('aria-label', '删除此记录');
            deleteBtn.onclick = () => {
                if (confirm('确定删除此记录？')) {
                    HistoryManager.delete(item.id);
                    this.renderHistory();
                }
            };
            
            const deleteIcon = document.createElement('i');
            deleteIcon.className = 'fas fa-times';
            deleteBtn.appendChild(deleteIcon);
            itemDiv.appendChild(deleteBtn);
            
            historyList.appendChild(itemDiv);
        });
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
        
        // 清空结果
        resultEl.innerHTML = '';
        
        if (results.length === 0) {
            const noResultP = document.createElement('p');
            noResultP.style.color = '#7f8c8d';
            noResultP.textContent = '未找到匹配的手机型号';
            resultEl.appendChild(noResultP);
            resultEl.classList.add('show');
            return;
        }
        
        // 使用 DOM API 创建元素，避免 XSS
        results.forEach(item => {
            const resultDiv = document.createElement('div');
            resultDiv.style.padding = '10px';
            resultDiv.style.borderBottom = '1px solid #f0f4f8';
            resultDiv.style.cursor = 'pointer';
            
            const brandStrong = document.createElement('strong');
            brandStrong.textContent = (item.brand || '') + ' ' + (item.model || '');
            resultDiv.appendChild(brandStrong);
            
            const capacitySpan = document.createElement('span');
            capacitySpan.style.float = 'right';
            capacitySpan.style.color = 'var(--primary)';
            capacitySpan.style.fontWeight = '600';
            capacitySpan.textContent = (item.capacity || 0) + ' mAh';
            resultDiv.appendChild(capacitySpan);
            
            resultDiv.onclick = () => {
                if (item.capacity && typeof item.capacity === 'number') {
                    this.selectCapacity(item.capacity);
                }
            };
            
            resultEl.appendChild(resultDiv);
        });
        
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
        if (confirm('确定要清空所有历史记录吗？此操作不可恢复！')) {
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
        
        const recentTrend = trend.slice(-10);
        const maxHealth = Math.max(...recentTrend.map(t => t.health));
        const minHealth = Math.min(...recentTrend.map(t => t.health));
        
        trendChart.innerHTML = '<div style="display:flex;align-items:flex-end;gap:4px;height:100px;padding:8px 0;">' +
            recentTrend.map((item, idx) => {
                const height = Math.max(10, Math.round((item.health / Math.max(maxHealth, 100)) * 80));
                const isLast = idx === recentTrend.length - 1;
                return '<div style="flex:1;display:flex;flex-direction:column;align-items:center;gap:2px;">' +
                    '<div style="font-size:10px;color:var(--gray);">' + item.health + '%</div>' +
                    '<div style="width:100%;height:' + height + 'px;border-radius:4px 4px 0 0;' +
                    'background:' + (isLast ? 'var(--primary)' : 'var(--primary-light, rgba(0,0,0,0.1))') + ';' +
                    'transition:height 0.3s;min-height:8px;"></div>' +
                    '<div style="font-size:9px;color:var(--gray);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:40px;">' + 
                    item.date.replace(/\//g, '-').substring(5) + '</div>' +
                    '</div>';
            }).join('') +
            '</div>';
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
     * 显示健康等级评估（新方法）
     * @param {Object} batteryGrade - 评级结果
     * @param {Object} tempStatus - 温度状态
     */
    showHealthGradeNew(batteryGrade, tempStatus) {
        const gradeBadge = document.getElementById('health-grade-badge');
        const gradeDescription = document.getElementById('health-grade-description');
        const gradeEstimated = document.getElementById('health-grade-estimated');
        const gradeCycleCount = document.getElementById('grade-cycle-count');
        const gradeEstimatedHealth = document.getElementById('grade-estimated-health');

        if (!gradeBadge) return;

        // 设置等级徽章
        gradeBadge.textContent = batteryGrade.grade;
        gradeBadge.className = 'health-grade-badge ' + batteryGrade.gradeClass;

        // 设置描述
        if (gradeDescription) gradeDescription.textContent = batteryGrade.gradeDesc;

        // 设置综合评分
        if (gradeEstimated) {
            gradeEstimated.textContent = `综合评分：${batteryGrade.score}分`;
        }

        // 设置循环次数
        if (gradeCycleCount) {
            const cycleCount = this.currentResult?.cycleCount || '未检测到';
            gradeCycleCount.textContent = cycleCount + (cycleCount !== '未检测到' ? ' 次' : '');
        }

        // 设置健康度
        if (gradeEstimatedHealth) {
            gradeEstimatedHealth.textContent = this.currentResult?.healthPercentage + '%' || '-';
        }

        // 添加温度状态显示（如果存在元素）
        const tempStatusEl = document.getElementById('temp-status');
        if (tempStatusEl && tempStatus) {
            tempStatusEl.innerHTML = `<i class="fas ${tempStatus.icon}"></i> ${tempStatus.label}`;
            tempStatusEl.className = tempStatus.className;
        }
    },

    /**
     * 显示保养建议（新方法）
     * @param {string} grade - 评级等级 A/B/C/D/E
     */
    showMaintenanceAdviceNew(grade) {
        const maintenanceList = this.elements.maintenanceList;
        if (!maintenanceList) return;

        const advice = MAINTENANCE_ADVICE[grade] || MAINTENANCE_ADVICE['C'];

        let html = `<div class="maintenance-banner">${advice.banner}</div>`;

        // 充电建议
        html += '<div class="maintenance-section"><h4><i class="fas fa-plug"></i> 充电建议</h4>';
        advice.charging.forEach(item => {
            const itemClass = item.class || '';
            html += `
                <div class="maintenance-item ${itemClass}">
                    <div class="maintenance-icon"><i class="fas fa-${item.icon}"></i></div>
                    <div class="maintenance-content"><p>${item.text}</p></div>
                </div>
            `;
        });
        html += '</div>';

        // 使用建议
        html += '<div class="maintenance-section"><h4><i class="fas fa-mobile-alt"></i> 使用建议</h4>';
        advice.usage.forEach(item => {
            const itemClass = item.class || '';
            html += `
                <div class="maintenance-item ${itemClass}">
                    <div class="maintenance-icon"><i class="fas fa-${item.icon}"></i></div>
                    <div class="maintenance-content"><p>${item.text}</p></div>
                </div>
            `;
        });
        html += '</div>';

        // 更换建议
        html += '<div class="maintenance-section"><h4><i class="fas fa-tools"></i> 更换建议</h4>';
        advice.replace.forEach(item => {
            const itemClass = item.class || '';
            html += `
                <div class="maintenance-item ${itemClass}">
                    <div class="maintenance-icon"><i class="fas fa-${item.icon}"></i></div>
                    <div class="maintenance-content"><p>${item.text}</p></div>
                </div>
            `;
        });
        html += '</div>';

        maintenanceList.innerHTML = html;
    },

    /**
     * 分享报告
     */
    shareReport() {
        if (!this.currentResult) {
            this.showToast('请先完成电池健康度分析', 'error');
            return;
        }
        
        const r = this.currentResult;
        const text = '🔋 电池健康度报告\n' +
            '━━━━━━━━━━━━━\n' +
            '设计容量: ' + (r.designCapacity || r.initialCapacity) + ' mAh\n' +
            '当前容量: ' + r.currentCapacity + ' mAh\n' +
            '健康度: ' + r.healthPercentage + '%\n' +
            '循环次数: ' + (r.cycleCount || '未检测到') + (r.cycleCount ? ' 次' : '') + '\n' +
            '电池温度: ' + (r.batteryTemp || '未检测到') + (r.batteryTemp ? '°C' : '') + '\n' +
            '健康等级: ' + (r.healthGrade ? r.healthGrade.grade : '-') + '\n' +
            '━━━━━━━━━━━━━\n' +
            '来自「电池健康度分析工具」';
        
        if (navigator.share) {
            navigator.share({
                title: '电池健康度报告',
                text: text
            }).then(() => this.showToast('分享成功', 'success'))
              .catch(() => {});
        } else {
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
        
        const r = this.currentResult;
        const report = '电池健康度分析报告\n' +
            '========================\n\n' +
            '分析时间: ' + new Date().toLocaleString('zh-CN') + '\n' +
            '品牌识别: ' + (r.brand || '未知') + '\n\n' +
            '电池数据:\n' +
            '- 设计容量: ' + (r.designCapacity || r.initialCapacity) + ' mAh\n' +
            '- 当前容量: ' + r.currentCapacity + ' mAh\n' +
            '- 健康度: ' + r.healthPercentage + '%\n' +
            '- 循环次数: ' + (r.cycleCount || '未检测到') + (r.cycleCount ? ' 次' : '') + '\n' +
            '- 电池温度: ' + (r.batteryTemp || '未检测到') + (r.batteryTemp ? ' °C' : '') + '\n' +
            '- 电池电压: ' + (r.voltage || '未检测到') + (r.voltage ? ' mV' : '') + '\n' +
            '- 电池技术: ' + (r.technology || '未检测到') + '\n\n' +
            '健康评估:\n' +
            '- 健康等级: ' + (r.healthGrade ? r.healthGrade.grade : '-') + '\n' +
            '- 综合评分: ' + (r.healthGrade ? r.healthGrade.score + '分' : '-') + '\n' +
            '- 评估说明: ' + (r.healthGrade ? r.healthGrade.gradeDesc : '-') + '\n\n' +
            '========================\n' +
            '本报告由电池健康度分析工具生成\n' +
            '抖音：带娃的小陈工';
        
        const blob = new Blob([report], { type: 'text/plain;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = '电池健康报告_' + new Date().toISOString().slice(0,10) + '.txt';
        a.click();
        URL.revokeObjectURL(url);
        
        this.showToast('报告已保存', 'success');
    },
    
    // ============= v2.1.17 新增：设备信息功能 =============
    
    /**
     * 显示设备信息（IMEI/SN/型号）
     */
    displayDeviceInfo(result) {
        const deviceSection = document.getElementById('device-info-section');
        if (!deviceSection) return;

        // 更新设备信息显示
        const modelEl = document.getElementById('device-model');
        const imei1El = document.getElementById('device-imei1');
        const imei2El = document.getElementById('device-imei2');
        const imei2Item = document.getElementById('imei2-item');
        const snEl = document.getElementById('device-sn');
        const sourceEl = document.getElementById('device-source');
        const hintEl = document.getElementById('device-info-hint');

        // 设备型号
        if (modelEl) {
            modelEl.textContent = result.deviceModel || result.brand || '-';
        }

        // IMEI 1
        if (imei1El) {
            imei1El.textContent = result.imei1 || '-';
            imei1El.style.cursor = result.imei1 ? 'pointer' : 'default';
        }

        // IMEI 2（双卡机型）
        if (imei2El && imei2Item) {
            if (result.imei2) {
                imei2El.textContent = result.imei2;
                imei2Item.style.display = 'block';
            } else {
                imei2Item.style.display = 'none';
            }
        }

        // 序列号 SN
        if (snEl) {
            snEl.textContent = result.serialNumber || '-';
            snEl.style.cursor = result.serialNumber ? 'pointer' : 'default';
        }

        // 数据来源
        if (sourceEl) {
            sourceEl.textContent = result.deviceSource || '未检测到';
        }

        // 显示提示
        const hasDeviceInfo = result.imei1 || result.serialNumber;
        if (hintEl) {
            hintEl.style.display = hasDeviceInfo ? 'block' : 'none';
        }

        // 保存设备信息到 currentResult
        if (this.currentResult) {
            this.currentResult.imei1 = result.imei1;
            this.currentResult.imei2 = result.imei2;
            this.currentResult.serialNumber = result.serialNumber;
            this.currentResult.deviceModel = result.deviceModel;
            this.currentResult.deviceSource = result.deviceSource;
        }

        Log.d(TAG, 'Device info displayed: imei1=' + result.imei1 + ', sn=' + result.serialNumber);
    },

    /**
     * 查询激活日期 - 跳转品牌官网
     */
    queryActivationDate() {
        const brand = this.currentResult?.brand || 'generic';
        const imei = this.currentResult?.imei1 || '';
        const sn = this.currentResult?.serialNumber || '';

        // 各品牌官网激活日期查询链接
        const queryUrls = {
            'huawei': 'https://consumer.huawei.com/cn/support/warranty-query/',
            'xiaomi': 'https://www.mi.com/verify',
            'oppo': 'https://support.oppo.com/cn/check/',
            'vivo': 'https://www.vivo.com.cn/service/authenticityCheck/index',
            'samsung': 'https://www.samsung.com/cn/support/warranty/',
            'apple': 'https://checkcoverage.apple.com/cn/zh/',
            'oneplus': 'https://support.oneplus.com/cn/check/',
            'realme': 'https://www.realme.com/cn/support/warranty-check',
            'iqoo': 'https://www.vivo.com.cn/service/authenticityCheck/index',
            'meizu': 'https://service.meizu.com/cn/warranty.html',
            'nubia': 'https://www.nubia.com/service/warranty',
            'zte': 'https://www.zte.com.cn/service/warranty',
            'motorola': 'https://support.motorola.com/cn/warranty',
            'honor': 'https://consumer.huawei.com/cn/support/warranty-query/',
            'generic': 'https://www.baidu.com/s?wd=手机激活日期查询'
        };

        // 获取对应品牌的查询链接
        const brandKey = brand.toLowerCase();
        const url = queryUrls[brandKey] || queryUrls['generic'];

        // 构建提示信息
        let tipInfo = '';
        if (imei) {
            tipInfo = 'IMEI: ' + imei;
        } else if (sn) {
            tipInfo = 'SN: ' + sn;
        } else {
            tipInfo = '未检测到 IMEI/SN，请在官网手动输入';
        }

        // 显示提示
        this.showToast('即将跳转官网查询\n' + tipInfo, 'info', 3000);

        // 延迟跳转，让用户看到提示
        setTimeout(() => {
            // 在 Android 端使用 WebView 打开外部链接
            if (typeof AndroidInterface !== 'undefined' && AndroidInterface.openExternalUrl) {
                AndroidInterface.openExternalUrl(url);
            } else {
                // 非 Android 环境使用 window.open
                window.open(url, '_blank');
            }
        }, 1500);
    },

    /**
     * 复制到剪贴板
     */
    copyToClipboard(text) {
        if (!text || text === '-' || text === '未检测到') {
            this.showToast('无可复制内容', 'error');
            return;
        }

        // 使用 Clipboard API
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text)
                .then(() => {
                    this.showToast('已复制: ' + text, 'success');
                })
                .catch(() => {
                    this.fallbackCopy(text);
                });
        } else {
            this.fallbackCopy(text);
        }
    },

    /**
     * 兜底复制方法
     */
    fallbackCopy(text) {
        // 创建临时 textarea
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.style.position = 'fixed';
        textarea.style.left = '-9999px';
        document.body.appendChild(textarea);
        textarea.select();
        try {
            document.execCommand('copy');
            this.showToast('已复制: ' + text, 'success');
        } catch (e) {
            this.showToast('复制失败，请手动复制', 'error');
        }
        document.body.removeChild(textarea);
    },

    /**
     * 复制全部设备信息
     */
    copyAllDeviceInfo() {
        const r = this.currentResult;
        if (!r) {
            this.showToast('请先完成分析', 'error');
            return;
        }

        const info = [
            '设备型号: ' + (r.deviceModel || r.brand || '-'),
            'IMEI 1: ' + (r.imei1 || '-'),
            'IMEI 2: ' + (r.imei2 || '-'),
            '序列号 SN: ' + (r.serialNumber || '-'),
            '',
            '电池健康度: ' + (r.healthPercentage || '-') + '%',
            '设计容量: ' + (r.designCapacity || r.initialCapacity || '-') + 'mAh',
            '当前容量: ' + (r.currentCapacity || '-') + 'mAh',
            '循环次数: ' + (r.cycleCount || '-') + '次'
        ].join('\n');

        this.copyToClipboard(info);
    },
    
    /**
     * 导出报告为图片
     */
    exportAsImage() {
        if (!this.currentResult) {
            this.showToast('请先完成电池健康度分析', 'error');
            return;
        }
        
        const r = this.currentResult;
        const canvas = document.createElement('canvas');
        canvas.width = 800;
        canvas.height = 600;
        const ctx = canvas.getContext('2d');
        
        // 背景
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, 800, 600);
        
        // 标题
        ctx.fillStyle = '#2c3e50';
        ctx.font = 'bold 28px sans-serif';
        ctx.fillText('电池健康度报告', 40, 50);
        
        // 分隔线
        ctx.fillStyle = '#3498db';
        ctx.fillRect(40, 65, 120, 4);
        
        // 数据
        ctx.font = '18px sans-serif';
        ctx.fillStyle = '#555';
        const lines = [
            '设计容量: ' + (r.designCapacity || r.initialCapacity) + ' mAh',
            '当前容量: ' + r.currentCapacity + ' mAh',
            '健康度: ' + r.healthPercentage + '%',
            '循环次数: ' + (r.cycleCount || '未检测到') + (r.cycleCount ? ' 次' : ''),
            '电池温度: ' + (r.batteryTemp || '未检测到') + (r.batteryTemp ? ' °C' : ''),
            '电池电压: ' + (r.voltage || '未检测到') + (r.voltage ? ' mV' : ''),
            '电池技术: ' + (r.technology || '未检测到'),
            '品牌: ' + (r.brand || '未知'),
            '',
            '健康等级: ' + (r.healthGrade ? r.healthGrade.grade : '-'),
            '综合评分: ' + (r.healthGrade ? r.healthGrade.score + '分' : '-')
        ];
        
        lines.forEach((line, i) => {
            ctx.fillText(line, 60, 110 + i * 36);
        });
        
        // 健康度进度条
        const barY = 110 + lines.length * 36 + 20;
        ctx.fillStyle = '#ecf0f1';
        ctx.fillRect(60, barY, 680, 24);
        const healthWidth = Math.min(r.healthPercentage, 100) / 100 * 680;
        ctx.fillStyle = r.healthPercentage >= 80 ? '#2ecc71' : r.healthPercentage >= 60 ? '#f39c12' : '#e74c3c';
        ctx.fillRect(60, barY, healthWidth, 24);
        
        // 水印
        ctx.fillStyle = '#bdc3c7';
        ctx.font = '12px sans-serif';
        ctx.fillText('电池健康度分析工具 · ' + new Date().toLocaleDateString('zh-CN'), 40, 580);
        
        // 下载
        const link = document.createElement('a');
        link.download = '电池健康报告_' + new Date().toISOString().slice(0,10) + '.png';
        link.href = canvas.toDataURL('image/png');
        link.click();
        
        this.showToast('报告图片已保存', 'success');
    },
    
    /**
     * Android文件选择处理方法
     * 关键修复：接收Android传递的文件URI并处理
     * 同时兼容window.BatteryHealthApp调用方式
     */
    handleAndroidFileSelected(uriString, fileName) {
        console.log('=== Android file selected ===');
        console.log('URI:', uriString);
        console.log('Name:', fileName);
        
        try {
            // 验证参数
            if (!uriString || !fileName) {
                console.error('Invalid file selected: missing URI or name');
                this.showStatus('文件选择失败：参数错误', true);
                return;
            }
            
            // 显示文件名
            if (this.elements.selectedFileName) {
                this.elements.selectedFileName.textContent = fileName;
            }
            const fileSizeEl = document.getElementById('selected-file-size');
            if (fileSizeEl) fileSizeEl.textContent = '';
            if (this.elements.fileNameDisplay) {
                this.elements.fileNameDisplay.style.display = 'flex';
            }
            if (this.elements.fileValidationError) {
                this.elements.fileValidationError.classList.remove('show');
            }
            
            // 验证文件类型
            if (!fileName.toLowerCase().endsWith('.zip')) {
                console.warn('File is not a ZIP file:', fileName);
                if (this.elements.fileValidationError) {
                    this.elements.fileValidationError.textContent = '请选择 ZIP 格式的文件';
                    this.elements.fileValidationError.classList.add('show');
                }
                this.currentFile = null;
                return;
            }
            
            // 创建一个模拟File对象用于后续处理
            // 关键修复：必须正确设置currentFile，handleAnalyze才能正常分析
            this.currentFile = {
                name: fileName,
                uri: uriString,
                isAndroidUri: true,
                size: -1 // 异步获取
            };
            
            this.showToast('已选择文件: ' + fileName, 'success');
            console.log('currentFile set successfully:', this.currentFile);
        } catch (error) {
            console.error('handleAndroidFileSelected error:', error);
            this.showStatus('文件处理失败: ' + error.message, true);
        }
    },
    
    /**
     * 内存警告处理
     * 当Android系统内存紧张时调用
     */
    onMemoryWarning() {
        console.warn('Memory warning received from Android');
        this.showToast('内存紧张，正在优化...', 'warning');
        
        // 如果正在处理大文件，显示警告
        if (this.currentFile && this.currentFile.isAndroidUri) {
            this.showStatus('内存紧张，处理可能较慢，请耐心等待', true);
        }
    }
};

// 初始化应用
document.addEventListener('DOMContentLoaded', () => {
    BatteryHealthApp.init();
});

// 关键修复：将BatteryHealthApp显式附加到window对象
// 因为const声明的变量不会自动成为window属性
// Android端JavaScript接口需要通过window.BatteryHealthApp访问
if (typeof window !== 'undefined') {
    window.BatteryHealthApp = BatteryHealthApp;
    console.log('BatteryHealthApp exposed to window successfully');
}

// 导出模块
if (typeof module !== 'undefined' && module.exports) {
    module.exports = BatteryHealthApp;
}
