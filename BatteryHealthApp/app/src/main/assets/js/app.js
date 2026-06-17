/**
 * 电池健康度分析工具 - 主应用模块
 * iOS 风格交互增强版
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

    // 当前激活的标签页
    activeTab: 'analyze',

    // 标签页顺序（用于滑动方向判断）
    tabOrder: ['analyze', 'report', 'history', 'settings'],

    // 深色模式状态
    isDarkMode: false,

    // 动画帧 ID
    _ringAnimationId: null,

    /**
     * 初始化应用
     */
    init() {
        this.cacheElements();
        this.bindEvents();
        this.initDarkMode();
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

        // 初始化标签页状态
        this.switchTab('analyze', false);

        // 更新设置页信息
        this.updateSettingsInfo();
    },

    /**
     * 缓存 DOM 元素
     */
    cacheElements() {
        this.elements = {
            // 原有元素
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
            guideOverlay: document.getElementById('guide-overlay'),

            // 标签导航元素
            tabAnalyze: document.getElementById('tab-analyze'),
            tabReport: document.getElementById('tab-report'),
            tabHistory: document.getElementById('tab-history'),
            tabSettings: document.getElementById('tab-settings'),

            // 页面容器元素
            pageAnalyze: document.getElementById('page-analyze'),
            pageReport: document.getElementById('page-report'),
            pageHistory: document.getElementById('page-history'),
            pageSettings: document.getElementById('page-settings'),

            // 健康度环形图元素
            healthRingCircle: document.getElementById('health-ring-circle'),
            healthRingBg: document.getElementById('health-ring-bg'),
            healthRingText: document.getElementById('health-ring-text'),
            healthRingLabel: document.getElementById('health-ring-label'),

            // 设置页元素
            darkModeToggle: document.getElementById('dark-mode-toggle'),
            settingsCacheSize: document.getElementById('settings-cache-size'),
            settingsVersion: document.getElementById('settings-version')
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

        // 标签导航事件
        this.bindTabEvents();

        // 深色模式切换
        if (this.elements.darkModeToggle) {
            this.elements.darkModeToggle.addEventListener('click', () => this.toggleDarkMode());
        }

        // 清除缓存
        const clearCacheRow = document.getElementById('clear-cache-row');
        if (clearCacheRow) {
            clearCacheRow.addEventListener('click', () => this.clearCache());
        }

        // 按钮触觉反馈
        this.bindHapticFeedback();
    },

    /**
     * 绑定标签导航事件
     */
    bindTabEvents() {
        const tabMap = {
            'tab-analyze': 'analyze',
            'tab-report': 'report',
            'tab-history': 'history',
            'tab-settings': 'settings'
        };

        Object.keys(tabMap).forEach(tabId => {
            const tabEl = document.getElementById(tabId);
            if (tabEl) {
                tabEl.addEventListener('click', () => {
                    this.switchTab(tabMap[tabId]);
                });
            }
        });
    },

    /**
     * 绑定触觉反馈效果
     */
    bindHapticFeedback() {
        // 为所有按钮添加视觉反馈
        document.querySelectorAll('.btn, .share-btn, .clear-history-btn, .brand-quick-btn, .guide-btn').forEach(btn => {
            btn.addEventListener('touchstart', function() {
                this.style.transform = 'scale(0.96)';
                this.style.opacity = '0.85';
            }, { passive: true });

            btn.addEventListener('touchend', function() {
                this.style.transform = '';
                this.style.opacity = '';
            }, { passive: true });

            btn.addEventListener('touchcancel', function() {
                this.style.transform = '';
                this.style.opacity = '';
            }, { passive: true });
        });
    },

    // ========================================
    // 标签导航系统
    // ========================================

    /**
     * 切换标签页
     * @param {string} tabName - 标签名称 (analyze|report|history|settings)
     * @param {boolean} animate - 是否执行过渡动画
     */
    switchTab(tabName, animate = true) {
        const prevTab = this.activeTab;
        this.activeTab = tabName;

        // 更新标签按钮状态
        const tabButtons = {
            analyze: this.elements.tabAnalyze,
            report: this.elements.tabReport,
            history: this.elements.tabHistory,
            settings: this.elements.tabSettings
        };

        Object.keys(tabButtons).forEach(key => {
            const btn = tabButtons[key];
            if (btn) {
                if (key === tabName) {
                    btn.classList.add('active');
                    btn.setAttribute('aria-selected', 'true');
                } else {
                    btn.classList.remove('active');
                    btn.setAttribute('aria-selected', 'false');
                }
            }
        });

        // 更新页面可见性
        const pages = {
            analyze: this.elements.pageAnalyze,
            report: this.elements.pageReport,
            history: this.elements.pageHistory,
            settings: this.elements.pageSettings
        };

        Object.keys(pages).forEach(key => {
            const page = pages[key];
            if (!page) return;

            if (key === tabName) {
                page.classList.add('active');
                page.style.display = '';

                if (animate && prevTab !== tabName) {
                    // 计算滑动方向
                    const prevIndex = this.tabOrder.indexOf(prevTab);
                    const nextIndex = this.tabOrder.indexOf(tabName);
                    const direction = nextIndex > prevIndex ? 1 : -1;

                    // iOS 风格滑入动画
                    page.style.transform = `translateX(${direction * 30}%)`;
                    page.style.opacity = '0';
                    page.style.transition = animate ? 'transform 0.35s cubic-bezier(0.25, 0.1, 0.25, 1), opacity 0.35s cubic-bezier(0.25, 0.1, 0.25, 1)' : '';

                    // 强制重排后启动动画
                    void page.offsetHeight;
                    page.style.transform = 'translateX(0)';
                    page.style.opacity = '1';
                }

                // 滚动到顶部
                if (animate) {
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                }
            } else {
                page.classList.remove('active');
                // 不使用 display:none 以避免布局问题，用视觉隐藏
                if (animate && prevTab === key) {
                    // 旧页面淡出
                    page.style.opacity = '0';
                    page.style.transform = 'translateX(0)';
                    setTimeout(() => {
                        if (!page.classList.contains('active')) {
                            page.style.display = 'none';
                        }
                    }, 350);
                } else {
                    page.style.display = 'none';
                }
            }
        });
    },

    /**
     * 自动切换到报告标签页
     */
    switchToReport() {
        this.switchTab('report');
    },

    // ========================================
    // 深色模式
    // ========================================

    /**
     * 初始化深色模式
     */
    initDarkMode() {
        // 读取保存的偏好
        const savedMode = localStorage.getItem('battery_health_dark_mode');

        if (savedMode !== null) {
            this.isDarkMode = savedMode === 'true';
        } else {
            // 检测系统偏好
            if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
                this.isDarkMode = true;
            }
        }

        this.applyDarkMode(false);

        // 监听系统主题变化
        if (window.matchMedia) {
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
                // 仅在用户未手动设置时跟随系统
                if (localStorage.getItem('battery_health_dark_mode') === null) {
                    this.isDarkMode = e.matches;
                    this.applyDarkMode(true);
                }
            });
        }

        // 更新开关状态
        if (this.elements.darkModeToggle) {
            this.elements.darkModeToggle.checked = this.isDarkMode;
        }
    },

    /**
     * 切换深色模式
     */
    toggleDarkMode() {
        this.isDarkMode = !this.isDarkMode;
        this.applyDarkMode(true);
        localStorage.setItem('battery_health_dark_mode', String(this.isDarkMode));

        if (this.elements.darkModeToggle) {
            this.elements.darkModeToggle.checked = this.isDarkMode;
        }

        this.showToast(this.isDarkMode ? '已开启深色模式' : '已关闭深色模式', 'success');
    },

    /**
     * 应用深色模式
     * @param {boolean} animate - 是否使用过渡动画
     */
    applyDarkMode(animate) {
        const root = document.documentElement;

        if (animate) {
            root.style.transition = 'background-color 0.3s ease, color 0.3s ease';
            setTimeout(() => {
                root.style.transition = '';
            }, 300);
        }

        if (this.isDarkMode) {
            root.setAttribute('data-theme', 'dark');
        } else {
            root.removeAttribute('data-theme');
        }
    },

    // ========================================
    // 圆形进度环
    // ========================================

    /**
     * 动画圆形进度环
     * @param {number} percentage - 健康度百分比
     */
    animateRing(percentage) {
        const circle = this.elements.healthRingCircle;
        const textEl = this.elements.healthRingText;
        const labelEl = this.elements.healthRingLabel;

        if (!circle) return;

        // 取消之前的动画
        if (this._ringAnimationId) {
            cancelAnimationFrame(this._ringAnimationId);
        }

        const radius = circle.r ? circle.r.baseVal.value : 54;
        const circumference = 2 * Math.PI * radius;

        // 设置初始状态
        circle.style.strokeDasharray = circumference;
        circle.style.strokeDashoffset = circumference;

        // 根据健康度设置颜色
        let color;
        if (percentage > 80) {
            color = '#2ecc71'; // 绿色
        } else if (percentage >= 60) {
            color = '#f39c12'; // 橙色
        } else {
            color = '#e74c3c'; // 红色
        }

        circle.style.stroke = color;

        // 目标偏移量
        const targetOffset = circumference - (percentage / 100) * circumference;
        const duration = 1200; // 动画时长 ms
        const startTime = performance.now();

        const animateFrame = (currentTime) => {
            const elapsed = currentTime - startTime;
            const progress = Math.min(elapsed / duration, 1);

            // 使用缓动函数 easeOutCubic
            const eased = 1 - Math.pow(1 - progress, 3);

            // 更新环形进度
            const currentOffset = circumference - eased * (circumference - targetOffset);
            circle.style.strokeDashoffset = currentOffset;

            // 更新数字
            const currentValue = Math.round(eased * percentage);
            if (textEl) {
                textEl.textContent = currentValue + '%';
            }

            if (progress < 1) {
                this._ringAnimationId = requestAnimationFrame(animateFrame);
            } else {
                this._ringAnimationId = null;
            }
        };

        // 设置标签
        if (labelEl) {
            if (percentage > 80) {
                labelEl.textContent = '电池健康';
            } else if (percentage >= 60) {
                labelEl.textContent = '建议保养';
            } else {
                labelEl.textContent = '建议更换';
            }
        }

        this._ringAnimationId = requestAnimationFrame(animateFrame);
    },

    // ========================================
    // 数值计数动画
    // ========================================

    /**
     * 数值计数动画
     * @param {HTMLElement} element - 目标元素
     * @param {number} start - 起始值
     * @param {number} end - 结束值
     * @param {number} duration - 动画时长 ms
     * @param {string} suffix - 后缀（如 'mAh', '%', '次'）
     */
    animateValue(element, start, end, duration, suffix = '') {
        if (!element) return;

        const startTime = performance.now();
        const isFloat = !Number.isInteger(end);

        const updateValue = (currentTime) => {
            const elapsed = currentTime - startTime;
            const progress = Math.min(elapsed / duration, 1);

            // easeOutCubic
            const eased = 1 - Math.pow(1 - progress, 3);
            const currentValue = start + (end - start) * eased;

            if (isFloat) {
                element.textContent = currentValue.toFixed(1) + suffix;
            } else {
                element.textContent = Math.round(currentValue) + suffix;
            }

            if (progress < 1) {
                requestAnimationFrame(updateValue);
            }
        };

        requestAnimationFrame(updateValue);
    },

    // ========================================
    // 原有功能方法
    // ========================================

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

        // 验证文件类型 - 兼容File对象和Android URI对象
        const fileName = file.name || '';
        if (!fileName.toLowerCase().endsWith('.zip')) {
            if (errorEl) {
                errorEl.textContent = '请选择 ZIP 格式的文件';
                errorEl.classList.add('show');
            }
            this.currentFile = null;
            return false;
        }

        // 验证文件大小（最大 100MB）- 仅对File对象检查
        if (file.size && file.size > 100 * 1024 * 1024) {
            if (errorEl) {
                errorEl.textContent = '文件过大，请选择小于 100MB 的文件';
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
            this.elements.selectedFileName.textContent = fileName;
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
        const contentEl = document.getElementById(brand + '-content');
        if (contentEl) {
            contentEl.classList.add('active');
        }
    },

    /**
     * 更新进度条
     */
    updateProgress(percent, text) {
        if (this.elements.progressFill) {
            this.elements.progressFill.style.width = percent + '%';
        }
        if (this.elements.progressText) {
            this.elements.progressText.textContent = text || `解析中... ${percent}%`;
        }
    },

    /**
     * 显示状态消息
     */
    showStatus(message, isError = false) {
        const status = this.elements.status;
        if (!status) return;
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
        if (this.elements.status) {
            this.elements.status.classList.remove('show');
        }
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
        if (!fileName.toLowerCase().endsWith('.zip')) {
            console.warn('Invalid file type:', fileName);
            this.showStatus('请选择 ZIP 格式的文件', true);
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
        const originalIcon = btnIcon ? btnIcon.className : 'fas fa-calculator';
        if (btnIcon) {
            btnIcon.className = 'fas fa-spinner';
        }
        const btnSpan = calculateBtn.querySelector('span');
        const originalText = btnSpan ? btnSpan.textContent : '';
        if (btnSpan) {
            btnSpan.textContent = '正在分析...';
        }

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
            if (btnIcon) {
                btnIcon.className = originalIcon;
            }
            if (btnSpan) {
                btnSpan.textContent = originalText || '分析电池健康度';
            }
            setTimeout(() => {
                progressContainer.classList.remove('show');
            }, 1000);
        }
    },

    /**
     * 分析 ZIP 文件
     * 关键修复：使用临时文件方案，避免200MB大文件OOM
     * Android端将文件复制到cacheDir，返回file:// URL
     * JS端通过fetch流式读取
     */
    async analyzeZipFile(file, initialCapacity) {
        return new Promise((resolve, reject) => {
            // 检查是否是Android URI文件
            if (file.isAndroidUri && file.uri) {
                console.log('Processing Android URI file:', file.uri);

                // 使用Android接口读取文件内容
                if (window.AndroidFilePicker) {
                    // 检查是否有带进度的读取方法
                    if (typeof window.AndroidFilePicker.readFileContentWithProgress === 'function') {
                        // 使用带进度的读取方法（新版：复制到临时目录）
                        this.updateProgress(5, '正在准备读取文件...');

                        // 设置回调函数
                        this._fileReadResolve = resolve;
                        this._fileReadReject = reject;
                        this._currentInitialCapacity = initialCapacity;

                        // 调用Android带进度的读取方法（进度范围：5% - 50%）
                        window.AndroidFilePicker.readFileContentWithProgress(
                            file.uri,
                            'onFileReadComplete',
                            5,
                            50
                        );
                    } else {
                        // 回退到旧方法
                        this.updateProgress(10, '正在读取Android文件...');

                        setTimeout(() => {
                            try {
                                const base64Content = window.AndroidFilePicker.readFileContent(file.uri);

                                if (!base64Content) {
                                    reject(new Error('无法读取文件内容'));
                                    return;
                                }

                                this.updateProgress(50, '正在解压文件...');
                                this._processBase64Content(base64Content, initialCapacity, resolve, reject);

                            } catch (error) {
                                console.error('Failed to process Android file:', error);
                                reject(new Error('文件处理失败: ' + error.message));
                            }
                        }, 100);
                    }
                } else {
                    reject(new Error('Android文件接口不可用'));
                }
                return;
            }

            // 常规文件处理（浏览器环境）
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

            reader.onerror = () => {
                reject(new Error('文件读取失败'));
            };

            reader.readAsArrayBuffer(file);
        });
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
     * Android文件读取完成回调（新版本：接收文件信息对象）
     * 关键修复：使用fetch流式读取临时文件，避免OOM
     * @param {Object|string} fileInfo - 文件信息对象{url, size, name, path} 或 旧版本的Base64字符串
     */
    onFileReadComplete(fileInfo) {
        console.log('=== File read complete ===');
        console.log('File info:', fileInfo);

        // 兼容旧版本：Base64字符串
        if (typeof fileInfo === 'string') {
            console.warn('Legacy Base64 response, processing...');
            if (!fileInfo) {
                if (this._fileReadReject) {
                    this._fileReadReject(new Error('无法读取文件内容'));
                }
                return;
            }

            this.updateProgress(45, '正在解压文件...');

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
        if (!fileInfo || !fileInfo.url) {
            if (this._fileReadReject) {
                this._fileReadReject(new Error('文件信息无效'));
            }
            return;
        }

        this.updateProgress(55, '正在加载文件...');

        const fileUrl = fileInfo.url;
        const filePath = fileInfo.path;
        const fileSize = fileInfo.size;
        const initialCapacity = this._currentInitialCapacity;

        console.log('Fetching file from:', fileUrl, 'size:', fileSize);

        // 关键修复：使用WebViewAssetLoader的https://地址
        // 这个URL会被Android端shouldInterceptRequest拦截并返回本地文件
        // 绕过了file://无法通过fetch访问的限制
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

                // 清理临时文件（解析完成后）
                if (window.AndroidFilePicker && filePath) {
                    try {
                        window.AndroidFilePicker.deleteTempFile(filePath);
                    } catch (e) {
                        console.warn('Failed to delete temp file:', e);
                    }
                }

                // 解析ZIP
                return this.processZipBlob(blob, initialCapacity, this._fileReadResolve, this._fileReadReject);
            })
            .catch(error => {
                console.error('Failed to load file:', error);
                console.error('URL was:', fileUrl);
                // 清理临时文件
                if (window.AndroidFilePicker && filePath) {
                    try {
                        window.AndroidFilePicker.deleteTempFile(filePath);
                    } catch (e) {}
                }
                if (this._fileReadReject) {
                    this._fileReadReject(new Error('文件加载失败: ' + error.message + ' (URL: ' + fileUrl + ')'));
                }
            });
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
     * 显示结果 - iOS 风格增强版
     */
    displayResult(result, initialCapacity) {
        const healthPercentage = ((result.currentCapacity / initialCapacity) * 100).toFixed(1);
        const healthNum = parseFloat(healthPercentage);

        // 保存当前结果供分享/保存功能使用
        this.currentResult = {
            brand: result.brand,
            initialCapacity: initialCapacity,
            currentCapacity: result.currentCapacity,
            healthPercentage: healthPercentage,
            cycleCount: result.cycleCount,
            batteryTemp: result.batteryTemp,
            healthGrade: result.healthGrade,
            voltage: result.voltage,
            technology: result.technology
        };

        // ===== 数值计数动画 =====
        const initialCapEl = document.getElementById('initial-capacity-value');
        const currentCapEl = document.getElementById('current-capacity-value');
        const healthEl = document.getElementById('health-percentage');
        const cycleEl = document.getElementById('cycle-count');
        const tempEl = document.getElementById('battery-temp');

        // 初始容量动画
        this.animateValue(initialCapEl, 0, initialCapacity, 800, '');

        // 当前容量动画
        this.animateValue(currentCapEl, 0, result.currentCapacity, 800, '');

        // 健康度动画
        this.animateValue(healthEl, 0, healthNum, 1000, '%');

        // 循环次数
        if (result.cycleCount) {
            this.animateValue(cycleEl, 0, result.cycleCount, 800, '');
        } else {
            if (cycleEl) cycleEl.textContent = '未检测到';
        }

        // 电池温度
        if (result.batteryTemp) {
            this.animateValue(tempEl, 0, result.batteryTemp, 800, '');
        } else {
            if (tempEl) tempEl.textContent = '未检测到';
        }

        // ===== 圆形进度环动画 =====
        this.animateRing(healthNum);

        // ===== 更新电池进度条 =====
        const batteryLevel = document.getElementById('battery-level');
        if (batteryLevel) {
            batteryLevel.style.width = Math.min(healthNum, 100) + '%';
            batteryLevel.classList.add('animate');
        }

        // 设置健康状态
        const qualityIndicator = document.getElementById('quality-indicator');
        let qualityText = '';
        let qualityClass = '';

        if (healthNum >= 85) {
            qualityText = '电池状态良好';
            qualityClass = 'quality-good';
        } else if (healthNum >= 70) {
            qualityText = '电池状态一般';
            qualityClass = 'quality-fair';
        } else {
            qualityText = '电池状态较差，建议更换';
            qualityClass = 'quality-poor';
        }

        const iconClass = healthNum >= 85 ? 'check-circle' : healthNum >= 70 ? 'exclamation-circle' : 'times-circle';
        if (qualityIndicator) {
            qualityIndicator.innerHTML = `<span class="${qualityClass}"><i class="fas fa-${iconClass}"></i> ${qualityText}</span>`;
        }

        // 显示原始数据
        if (result.rawContent) {
            const originalText = document.getElementById('original-text');
            if (originalText) {
                originalText.textContent = result.rawContent;
            }
        }

        // 显示保养建议
        this.showMaintenanceAdvice(healthNum);

        // 显示健康等级评估
        this.showHealthGrade(result);

        // 更新趋势图表
        this.renderTrendChart();

        // ===== 交错卡片入场动画 =====
        const statsGrid = document.querySelector('.stats-grid');
        if (statsGrid) {
            statsGrid.classList.add('animate');
            // 为每个卡片添加延迟入场
            const cards = statsGrid.querySelectorAll('.stat-card');
            cards.forEach((card, index) => {
                card.style.opacity = '0';
                card.style.transform = 'translateY(20px)';
                card.style.transition = 'opacity 0.4s ease, transform 0.4s ease';
                setTimeout(() => {
                    card.style.opacity = '1';
                    card.style.transform = 'translateY(0)';
                }, 100 + index * 80);
            });
        }

        // 显示结果
        if (this.elements.result) {
            this.elements.result.classList.add('show');
            this.elements.result.classList.add('animate-expand');
        }

        // ===== 自动切换到报告标签页 =====
        setTimeout(() => {
            this.switchToReport();
        }, 600);

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
            if (gradeBadge) {
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
            }

            // 设置描述和预估健康度
            if (gradeDescription) gradeDescription.textContent = grade.description;
            if (gradeEstimated) gradeEstimated.textContent = `预估健康度范围：${grade.estimatedHealth}`;
            if (gradeCycleCount) gradeCycleCount.textContent = grade.cycleCount + ' 次';
            if (gradeEstimatedHealth) gradeEstimatedHealth.textContent = grade.estimatedHealth;
        } else {
            // 如果没有循环次数数据，显示提示
            if (gradeBadge) {
                gradeBadge.textContent = '?';
                gradeBadge.className = 'health-grade-badge grade-b';
            }
            if (gradeDescription) gradeDescription.textContent = '无法评估健康等级';
            if (gradeEstimated) gradeEstimated.textContent = '诊断文件未包含循环次数数据';
            if (gradeCycleCount) gradeCycleCount.textContent = '未检测到';
            if (gradeEstimatedHealth) gradeEstimatedHealth.textContent = '-';
        }
    },

    /**
     * 切换原始文本显示
     */
    toggleRawText() {
        const textDiv = document.getElementById('original-text');
        const btn = document.getElementById('toggle-text');

        if (!textDiv || !btn) return;

        if (textDiv.classList.contains('show')) {
            textDiv.classList.remove('show');
            btn.innerHTML = '<i class="fas fa-eye"></i> 显示详情';
        } else {
            textDiv.classList.add('show');
            btn.innerHTML = '<i class="fas fa-eye-slash"></i> 隐藏详情';
        }
    },

    /**
     * 渲染历史记录 - iOS 风格增强版
     */
    renderHistory() {
        const historyList = this.elements.historyList;
        if (!historyList) return;

        const history = HistoryManager.getAll();

        if (history.length === 0) {
            historyList.innerHTML = '<div class="history-empty" role="listitem">暂无历史记录</div>';
            return;
        }

        historyList.innerHTML = history.map((item, index) => `
            <div class="history-item" role="listitem" data-id="${item.id}"
                 onclick="BatteryHealthApp.viewHistoryItem(${item.id})"
                 style="opacity: 0; transform: translateY(10px); transition: opacity 0.3s ease ${index * 0.05}s, transform 0.3s ease ${index * 0.05}s;">
                <div class="info">
                    <div class="history-date">${this.formatHistoryDate(item.timestamp)}</div>
                    <div class="history-detail">${item.initialCapacity}mAh → ${item.currentCapacity}mAh ${item.cycleCount ? `· ${item.cycleCount}次循环` : ''}</div>
                </div>
                <div class="history-health ${HistoryManager.getHealthColor(item.healthPercentage)}">
                    ${item.healthPercentage}%
                </div>
                <button class="delete-btn" onclick="event.stopPropagation(); BatteryHealthApp.deleteHistoryItem(${item.id})" aria-label="删除此记录">
                    <i class="fas fa-times"></i>
                </button>
            </div>
        `).join('');

        // 触发入场动画
        requestAnimationFrame(() => {
            historyList.querySelectorAll('.history-item').forEach(item => {
                item.style.opacity = '1';
                item.style.transform = 'translateY(0)';
            });
        });
    },

    /**
     * 格式化历史日期（增强版）
     * @param {string} timestamp - ISO 时间戳
     * @returns {string} - 格式化后的日期
     */
    formatHistoryDate(timestamp) {
        const date = new Date(timestamp);
        const now = new Date();
        const diff = now - date;

        // 小于1分钟
        if (diff < 60000) {
            return '刚刚';
        }

        // 小于1小时
        if (diff < 3600000) {
            const minutes = Math.floor(diff / 60000);
            return `${minutes}分钟前`;
        }

        // 小于24小时
        if (diff < 86400000) {
            const hours = Math.floor(diff / 3600000);
            return `${hours}小时前`;
        }

        // 小于7天
        if (diff < 604800000) {
            const days = Math.floor(diff / 86400000);
            return `${days}天前`;
        }

        // 同一年
        if (date.getFullYear() === now.getFullYear()) {
            return date.toLocaleDateString('zh-CN', {
                month: 'long',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        }

        // 不同年份
        return date.toLocaleDateString('zh-CN', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    },

    /**
     * 查看历史记录详情
     * @param {number} id - 记录 ID
     */
    viewHistoryItem(id) {
        const history = HistoryManager.getAll();
        const item = history.find(h => h.id === id);
        if (!item) return;

        // 构建临时结果对象用于显示
        const tempResult = {
            brand: item.brand || 'unknown',
            currentCapacity: item.currentCapacity,
            cycleCount: item.cycleCount,
            batteryTemp: item.batteryTemp,
            healthGrade: item.cycleCount ? BatteryParsers.calculateHealthGrade(item.cycleCount) : null,
            rawContent: null,
            voltage: null,
            technology: null
        };

        // 显示结果
        this.displayResult(tempResult, item.initialCapacity);
    },

    /**
     * 删除单条历史记录
     */
    deleteHistoryItem(id) {
        if (confirm('确定删除此记录？')) {
            HistoryManager.delete(id);
            this.renderHistory();
            this.renderTrendChart();
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
        if (!resultEl) return;

        if (results.length === 0) {
            resultEl.innerHTML = '<p style="color: #7f8c8d;">未找到匹配的手机型号</p>';
            resultEl.classList.add('show');
            return;
        }

        resultEl.innerHTML = results.map(item => `
            <div style="padding: 10px; border-bottom: 1px solid var(--gray-light, #f0f4f8); cursor: pointer;"
                 onclick="BatteryHealthApp.selectCapacity(${item.capacity})">
                <strong>${item.brand} ${item.model}</strong>
                <span style="float: right; color: var(--primary, #3498db); font-weight: 600;">${item.capacity} mAh</span>
            </div>
        `).join('');

        resultEl.classList.add('show');
    },

    /**
     * 选择容量
     */
    selectCapacity(capacity) {
        if (this.elements.initialCapacityInput) {
            this.elements.initialCapacityInput.value = capacity;
        }
        if (this.elements.capacitySearchResult) {
            this.elements.capacitySearchResult.classList.remove('show');
        }
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
     * 渲染趋势图表 - SVG 折线图增强版
     */
    renderTrendChart() {
        const trendChart = this.elements.trendChart;
        if (!trendChart) return;

        const trend = HistoryManager.getTrend();

        if (trend.length < 2) {
            trendChart.innerHTML = '<div class="trend-empty">暂无足够数据显示趋势（需要至少2条记录）</div>';
            return;
        }

        // 使用最近的10条数据
        const data = trend.slice(-10);
        const maxHealth = Math.max(...data.map(t => t.health));
        const minHealth = Math.min(...data.map(t => t.health));
        const healthRange = maxHealth - minHealth || 10; // 避免除以0

        // SVG 参数
        const width = 280;
        const height = 100;
        const padding = { top: 15, right: 15, bottom: 25, left: 35 };
        const chartWidth = width - padding.left - padding.right;
        const chartHeight = height - padding.top - padding.bottom;

        // 计算 Y 轴范围（留出余量）
        const yMin = Math.max(0, minHealth - 5);
        const yMax = Math.min(100, maxHealth + 5);
        const yRange = yMax - yMin || 10;

        // 计算数据点坐标
        const points = data.map((item, i) => {
            const x = padding.left + (i / (data.length - 1)) * chartWidth;
            const y = padding.top + chartHeight - ((item.health - yMin) / yRange) * chartHeight;
            return { x, y, health: item.health, date: item.date };
        });

        // 生成折线路径
        const linePath = points.map((p, i) => {
            return (i === 0 ? 'M' : 'L') + p.x.toFixed(1) + ',' + p.y.toFixed(1);
        }).join(' ');

        // 生成面积路径
        const areaPath = linePath +
            ` L${points[points.length - 1].x.toFixed(1)},${(padding.top + chartHeight).toFixed(1)}` +
            ` L${points[0].x.toFixed(1)},${(padding.top + chartHeight).toFixed(1)} Z`;

        // 生成 Y 轴刻度
        const yTicks = [];
        const tickCount = 4;
        for (let i = 0; i <= tickCount; i++) {
            const value = yMin + (yRange / tickCount) * i;
            const y = padding.top + chartHeight - (i / tickCount) * chartHeight;
            yTicks.push({ value: Math.round(value), y });
        }

        // 构建 SVG
        const svg = `
            <svg viewBox="0 0 ${width} ${height}" style="width: 100%; height: auto; overflow: visible;">
                <!-- Y 轴刻度 -->
                ${yTicks.map(tick => `
                    <line x1="${padding.left}" y1="${tick.y}" x2="${width - padding.right}" y2="${tick.y}"
                          stroke="var(--gray-light, #e0e6ed)" stroke-width="0.5" stroke-dasharray="3,3"/>
                    <text x="${padding.left - 5}" y="${tick.y + 3}" text-anchor="end"
                          fill="var(--gray, #95a5a6)" font-size="8">${tick.value}%</text>
                `).join('')}

                <!-- 面积填充 -->
                <path d="${areaPath}" fill="url(#areaGradient)" opacity="0.3"/>

                <!-- 折线 -->
                <path d="${linePath}" fill="none" stroke="var(--primary, #3498db)" stroke-width="2"
                      stroke-linecap="round" stroke-linejoin="round"
                      style="stroke-dasharray: 1000; stroke-dashoffset: 1000; animation: svgLineDraw 1.5s ease forwards;"/>

                <!-- 数据点 -->
                ${points.map((p, i) => `
                    <circle cx="${p.x}" cy="${p.y}" r="3.5" fill="white"
                            stroke="var(--primary, #3498db)" stroke-width="2"
                            style="opacity: 0; animation: svgDotFadeIn 0.3s ease ${0.3 + i * 0.1}s forwards;"/>
                `).join('')}

                <!-- X 轴日期标签 -->
                ${points.filter((_, i) => data.length <= 6 || i % Math.ceil(data.length / 6) === 0 || i === points.length - 1).map(p => {
                    const shortDate = p.date.replace(/\//g, '/').replace(/20(\d{2})/, '$1');
                    return `<text x="${p.x}" y="${height - 5}" text-anchor="middle"
                                  fill="var(--gray, #95a5a6)" font-size="7">${shortDate}</text>`;
                }).join('')}

                <!-- 渐变定义 -->
                <defs>
                    <linearGradient id="areaGradient" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stop-color="var(--primary, #3498db)" stop-opacity="0.4"/>
                        <stop offset="100%" stop-color="var(--primary, #3498db)" stop-opacity="0.05"/>
                    </linearGradient>
                </defs>
            </svg>

            <style>
                @keyframes svgLineDraw {
                    to { stroke-dashoffset: 0; }
                }
                @keyframes svgDotFadeIn {
                    to { opacity: 1; }
                }
            </style>
        `;

        trendChart.innerHTML = svg;
    },

    /**
     * 更新趋势图表（别名，保持接口一致）
     */
    updateTrendChart() {
        this.renderTrendChart();
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
            if (this.elements.guideOverlay) {
                this.elements.guideOverlay.classList.add('show');
                this.elements.guideOverlay.setAttribute('aria-hidden', 'false');
            }
        }
    },

    /**
     * 关闭引导
     */
    closeGuide() {
        if (this.elements.guideOverlay) {
            this.elements.guideOverlay.classList.remove('show');
            this.elements.guideOverlay.setAttribute('aria-hidden', 'true');
        }
        localStorage.setItem('battery_health_visited', 'true');
    },

    /**
     * 显示 Toast 提示
     */
    showToast(message, type = 'info') {
        const toast = this.elements.toast;
        if (!toast) return;

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
        if (!maintenanceList) return;

        maintenanceList.innerHTML = advice.map((item, index) => `
            <div class="maintenance-item" style="opacity: 0; transform: translateX(-10px); transition: opacity 0.3s ease ${index * 0.1}s, transform 0.3s ease ${index * 0.1}s;">
                <div class="maintenance-icon"><i class="fas ${item.icon}"></i></div>
                <div class="maintenance-content">
                    <h4>${item.title}</h4>
                    <p>${item.text}</p>
                </div>
            </div>
        `).join('');

        // 触发入场动画
        requestAnimationFrame(() => {
            maintenanceList.querySelectorAll('.maintenance-item').forEach(item => {
                item.style.opacity = '1';
                item.style.transform = 'translateX(0)';
            });
        });
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
        a.download = `电池健康报告_${new Date().toISOString().slice(0, 10)}.txt`;
        a.click();
        URL.revokeObjectURL(url);

        this.showToast('报告已保存', 'success');
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
    },

    // ========================================
    // 设置页功能
    // ========================================

    /**
     * 更新设置页信息
     */
    updateSettingsInfo() {
        // 版本信息
        if (this.elements.settingsVersion) {
            this.elements.settingsVersion.textContent = 'v2.0.0';
        }

        // 缓存大小
        this.updateCacheSize();
    },

    /**
     * 更新缓存大小显示
     */
    updateCacheSize() {
        if (!this.elements.settingsCacheSize) return;

        try {
            let totalSize = 0;
            for (let i = 0; i < localStorage.length; i++) {
                const key = localStorage.key(i);
                const value = localStorage.getItem(key);
                if (value) {
                    totalSize += key.length + value.length;
                }
            }
            // 粗略估算：每个字符约2字节
            const sizeBytes = totalSize * 2;
            let sizeText;
            if (sizeBytes < 1024) {
                sizeText = sizeBytes + ' B';
            } else if (sizeBytes < 1024 * 1024) {
                sizeText = (sizeBytes / 1024).toFixed(1) + ' KB';
            } else {
                sizeText = (sizeBytes / (1024 * 1024)).toFixed(1) + ' MB';
            }
            this.elements.settingsCacheSize.textContent = sizeText;
        } catch (e) {
            this.elements.settingsCacheSize.textContent = '未知';
        }
    },

    /**
     * 清除缓存
     */
    clearCache() {
        if (confirm('确定要清除缓存吗？这将清除所有本地存储数据（包括历史记录）。')) {
            try {
                // 保留深色模式偏好
                const darkModePref = localStorage.getItem('battery_health_dark_mode');
                const visitedPref = localStorage.getItem('battery_health_visited');

                localStorage.clear();

                // 恢复保留的偏好
                if (darkModePref !== null) {
                    localStorage.setItem('battery_health_dark_mode', darkModePref);
                }
                if (visitedPref !== null) {
                    localStorage.setItem('battery_health_visited', visitedPref);
                }

                this.renderHistory();
                this.renderTrendChart();
                this.updateCacheSize();
                this.showToast('缓存已清除', 'success');
            } catch (e) {
                this.showToast('清除缓存失败', 'error');
            }
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
