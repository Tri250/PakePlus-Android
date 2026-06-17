/**
 * Battery Health Analysis App
 * 电池健康分析应用 - 完整JavaScript实现
 * 
 * 包含8大功能模块:
 * 1. 电池健康 (Battery Health) - analyzeBatteryHealth()
 * 2. 配置查询 (Device Config) - parseDeviceConfig()
 * 3. 性能分析 (Performance) - analyzePerformance()
 * 4. 出厂激活 (Activation Info) - checkActivationDate()
 * 5. 趋势追踪 (Trends) - trackTrends()
 * 6. 续航分析 (Battery Life) - analyzeBatteryLife()
 * 7. 电池溯源 (Battery Origin) - checkBatteryOrigin()
 * 8. 充电功率 (Charging Power) - monitorChargingPower()
 */

// ========================================
// 电池数据库 - 常见设备电池规格
// ========================================
const BatteryDatabase = {
    // 小米系列
    'Xiaomi': {
        'Mi 11': { nominal: 4600, typical: 4700, voltage: 3.85 },
        'Mi 11 Ultra': { nominal: 5000, typical: 5100, voltage: 3.85 },
        'Mi 10': { nominal: 4780, typical: 4780, voltage: 3.85 },
        'Mi 10 Pro': { nominal: 4500, typical: 4500, voltage: 3.85 },
        'Mi 9': { nominal: 3300, typical: 3300, voltage: 3.85 },
        'Redmi K40': { nominal: 4520, typical: 4520, voltage: 3.85 },
        'Redmi K50': { nominal: 5500, typical: 5500, voltage: 3.87 },
        'Redmi Note 11': { nominal: 5000, typical: 5000, voltage: 3.85 },
        'Redmi Note 12': { nominal: 5000, typical: 5000, voltage: 3.85 },
        'Redmi Note 13': { nominal: 5000, typical: 5000, voltage: 3.85 },
        'Xiaomi 13': { nominal: 4500, typical: 4500, voltage: 3.85 },
        'Xiaomi 13 Pro': { nominal: 4820, typical: 4820, voltage: 3.85 },
        'Xiaomi 14': { nominal: 4610, typical: 4610, voltage: 3.85 },
        'Xiaomi 14 Pro': { nominal: 4880, typical: 4880, voltage: 3.85 },
    },
    // 华为系列
    'Huawei': {
        'Mate 40 Pro': { nominal: 4400, typical: 4500, voltage: 3.85 },
        'Mate 50 Pro': { nominal: 4600, typical: 4700, voltage: 3.88 },
        'Mate 60 Pro': { nominal: 5000, typical: 5000, voltage: 3.88 },
        'P40 Pro': { nominal: 4200, typical: 4200, voltage: 3.85 },
        'P50 Pro': { nominal: 4360, typical: 4360, voltage: 3.85 },
        'P60 Pro': { nominal: 4815, typical: 4815, voltage: 3.88 },
        'Pura 70': { nominal: 4900, typical: 4900, voltage: 3.88 },
        'nova 11': { nominal: 4500, typical: 4500, voltage: 3.85 },
    },
    // OPPO系列
    'OPPO': {
        'Find X3 Pro': { nominal: 4500, typical: 4500, voltage: 3.85 },
        'Find X5 Pro': { nominal: 5000, typical: 5000, voltage: 3.87 },
        'Find X6 Pro': { nominal: 5000, typical: 5000, voltage: 3.87 },
        'Find X7 Pro': { nominal: 5000, typical: 5000, voltage: 3.87 },
        'Reno 8 Pro': { nominal: 4500, typical: 4500, voltage: 3.85 },
        'Reno 9 Pro': { nominal: 4500, typical: 4500, voltage: 3.85 },
        'Reno 10 Pro': { nominal: 4600, typical: 4600, voltage: 3.85 },
        'Reno 11 Pro': { nominal: 4700, typical: 4700, voltage: 3.85 },
    },
    // vivo系列
    'vivo': {
        'X80 Pro': { nominal: 4700, typical: 4700, voltage: 3.89 },
        'X90 Pro': { nominal: 4870, typical: 4870, voltage: 3.89 },
        'X100 Pro': { nominal: 5400, typical: 5400, voltage: 3.89 },
        'iQOO 11': { nominal: 5000, typical: 5000, voltage: 3.87 },
        'iQOO 12': { nominal: 5000, typical: 5000, voltage: 3.87 },
        'iQOO Neo 8': { nominal: 5000, typical: 5000, voltage: 3.87 },
    },
    // 一加系列
    'OnePlus': {
        '9 Pro': { nominal: 4500, typical: 4500, voltage: 3.85 },
        '10 Pro': { nominal: 5000, typical: 5000, voltage: 3.87 },
        '11': { nominal: 5000, typical: 5000, voltage: 3.87 },
        '12': { nominal: 5400, typical: 5400, voltage: 3.87 },
        'Ace 2': { nominal: 5000, typical: 5000, voltage: 3.87 },
    },
    // 三星系列
    'samsung': {
        'SM-G998B': { nominal: 5000, typical: 5000, voltage: 3.85 }, // S21 Ultra
        'SM-S908B': { nominal: 5000, typical: 5000, voltage: 3.85 }, // S22 Ultra
        'SM-S918B': { nominal: 5000, typical: 5000, voltage: 3.85 }, // S23 Ultra
        'SM-S928B': { nominal: 5000, typical: 5000, voltage: 3.85 }, // S24 Ultra
        'SM-G996B': { nominal: 4800, typical: 4800, voltage: 3.85 }, // S21+
        'SM-G991B': { nominal: 4000, typical: 4000, voltage: 3.85 }, // S21
    },
    // 荣耀系列
    'HONOR': {
        'Magic 5 Pro': { nominal: 5450, typical: 5450, voltage: 3.88 },
        'Magic 6 Pro': { nominal: 5600, typical: 5600, voltage: 3.88 },
        'Magic V2': { nominal: 5000, typical: 5000, voltage: 3.88 },
    },
    // 默认规格
    'default': { nominal: 4000, typical: 4100, voltage: 3.85 }
};

// ========================================
// 历史数据管理器
// ========================================
const HistoryManager = {
    STORAGE_KEY: 'battery_health_history',
    MAX_ENTRIES: 365,

    getAll() {
        try {
            const data = localStorage.getItem(this.STORAGE_KEY);
            return data ? JSON.parse(data) : [];
        } catch (e) {
            console.error('HistoryManager.getAll error:', e);
            return [];
        }
    },

    add(entry) {
        try {
            const history = this.getAll();
            entry.timestamp = Date.now();
            entry.date = new Date().toISOString().split('T')[0];
            history.push(entry);
            
            // 限制条目数量
            if (history.length > this.MAX_ENTRIES) {
                history.shift();
            }
            
            localStorage.setItem(this.STORAGE_KEY, JSON.stringify(history));
            return true;
        } catch (e) {
            console.error('HistoryManager.add error:', e);
            return false;
        }
    },

    getByDateRange(startDate, endDate) {
        const history = this.getAll();
        return history.filter(entry => {
            const entryDate = new Date(entry.date);
            return entryDate >= startDate && entryDate <= endDate;
        });
    },

    getLatest(days = 30) {
        const history = this.getAll();
        const cutoff = Date.now() - (days * 24 * 60 * 60 * 1000);
        return history.filter(entry => entry.timestamp >= cutoff);
    },

    getTrends(days = 30) {
        const history = this.getLatest(days);
        if (history.length < 2) return null;

        const healthValues = history.map(h => h.health || 0);
        const capacityValues = history.map(h => h.capacity || 0);
        const cycleValues = history.map(h => h.cycles || 0);
        const tempValues = history.filter(h => h.temperature).map(h => h.temperature);

        return {
            healthTrend: this.calculateTrend(healthValues),
            capacityTrend: this.calculateTrend(capacityValues),
            cycleTrend: this.calculateTrend(cycleValues),
            tempTrend: tempValues.length > 0 ? this.calculateTrend(tempValues) : null,
            avgHealth: healthValues.reduce((a, b) => a + b, 0) / healthValues.length,
            avgCapacity: capacityValues.reduce((a, b) => a + b, 0) / capacityValues.length,
            avgCycles: cycleValues.reduce((a, b) => a + b, 0) / cycleValues.length,
            dataPoints: history.length
        };
    },

    calculateTrend(values) {
        if (values.length < 2) return 0;
        const first = values[0];
        const last = values[values.length - 1];
        return ((last - first) / values.length).toFixed(2);
    },

    clear() {
        localStorage.removeItem(this.STORAGE_KEY);
    }
};

// ========================================
// 电池数据解析器
// ========================================
const BatteryParsers = {
    // 解析 bugreport 文件
    parseBugreport(content) {
        const data = {
            health: null,
            status: null,
            present: null,
            capacity: null,
            voltage: null,
            temperature: null,
            current: null,
            cycleCount: null,
            fullChargeCapacity: null,
            designCapacity: null
        };

        // 解析电池历史数据
        const historyMatch = content.match(/Battery History[\s\S]*?(?=Per-PID Stats|$)/);
        if (historyMatch) {
            // 提取温度信息
            const tempMatches = content.matchAll(/temperature=(\d+)/g);
            const temps = Array.from(tempMatches).map(m => parseInt(m[1]) / 10);
            if (temps.length > 0) {
                data.temperature = temps[temps.length - 1];
            }
        }

        // 解析电池服务信息
        const batteryServiceMatch = content.match(/DUMP OF SERVICE battery:[\s\S]*?(?=DUMP OF SERVICE|$)/);
        if (batteryServiceMatch) {
            const batteryInfo = batteryServiceMatch[0];
            
            // 健康状态
            const healthMatch = batteryInfo.match(/health:\s*(\w+)/);
            if (healthMatch) data.health = healthMatch[1];
            
            // 状态
            const statusMatch = batteryInfo.match(/status:\s*(\w+)/);
            if (statusMatch) data.status = statusMatch[1];
            
            // 电压
            const voltageMatch = batteryInfo.match(/voltage:\s*(\d+)/);
            if (voltageMatch) data.voltage = parseInt(voltageMatch[1]);
            
            // 温度
            const tempMatch = batteryInfo.match(/temperature:\s*(\d+)/);
            if (tempMatch) data.temperature = parseInt(tempMatch[1]) / 10;
            
            // 电流
            const currentMatch = batteryInfo.match(/current now:\s*(-?\d+)/);
            if (currentMatch) data.current = parseInt(currentMatch[1]);
        }

        // 解析电池属性
        const propsMatch = content.match(/dumpsys battery properties[\s\S]*?(?=\n\n|$)/);
        if (propsMatch) {
            const props = propsMatch[0];
            
            // 设计容量
            const designCapMatch = props.match(/Charge counter:\s*(\d+)/);
            if (designCapMatch) data.designCapacity = parseInt(designCapMatch[1]);
            
            // 满电容量
            const fullCapMatch = props.match(/Full capacity:\s*(\d+)/);
            if (fullCapMatch) data.fullChargeCapacity = parseInt(fullCapMatch[1]);
            
            // 循环次数
            const cycleMatch = props.match(/Cycle count:\s*(\d+)/);
            if (cycleMatch) data.cycleCount = parseInt(cycleMatch[1]);
        }

        return data;
    },

    // 解析电池日志
    parseBatteryLog(content) {
        const entries = [];
        const lines = content.split('\n');
        
        for (const line of lines) {
            // 匹配常见的电池日志格式
            const match = line.match(/(\d{4}-\d{2}-\d{2}[\sT]\d{2}:\d{2}:\d{2}).*?(\d+)%.*?(\d+)mV.*?(\d+)°?C/);
            if (match) {
                entries.push({
                    timestamp: new Date(match[1]).getTime(),
                    level: parseInt(match[2]),
                    voltage: parseInt(match[3]),
                    temperature: parseInt(match[4])
                });
            }
        }
        
        return entries;
    },

    // 解析设备信息
    parseDeviceInfo(content) {
        const info = {};
        
        // 品牌
        const brandMatch = content.match(/ro\.product\.brand=(.+)/);
        info.brand = brandMatch ? brandMatch[1].trim() : null;
        
        // 型号
        const modelMatch = content.match(/ro\.product\.model=(.+)/);
        info.model = modelMatch ? modelMatch[1].trim() : null;
        
        // 设备代号
        const deviceMatch = content.match(/ro\.product\.device=(.+)/);
        info.device = deviceMatch ? deviceMatch[1].trim() : null;
        
        // Android版本
        const androidMatch = content.match(/ro\.build\.version\.release=(.+)/);
        info.androidVersion = androidMatch ? androidMatch[1].trim() : null;
        
        // SDK版本
        const sdkMatch = content.match(/ro\.build\.version\.sdk=(\d+)/);
        info.sdkVersion = sdkMatch ? parseInt(sdkMatch[1]) : null;
        
        // 安全补丁
        const securityMatch = content.match(/ro\.build\.version\.security_patch=(.+)/);
        info.securityPatch = securityMatch ? securityMatch[1].trim() : null;
        
        // 内核版本
        const kernelMatch = content.match(/Linux version[\s\S]*?\n/);
        info.kernelVersion = kernelMatch ? kernelMatch[0].trim() : null;
        
        // 硬件信息
        const hardwareMatch = content.match(/ro\.hardware=(.+)/);
        info.hardware = hardwareMatch ? hardwareMatch[1].trim() : null;
        
        return info;
    },

    // 解析内存信息
    parseMemoryInfo(content) {
        const memory = {};
        
        const totalMatch = content.match(/MemTotal:\s*(\d+)\s*kB/);
        memory.total = totalMatch ? Math.round(parseInt(totalMatch[1]) / 1024) : null;
        
        const freeMatch = content.match(/MemFree:\s*(\d+)\s*kB/);
        memory.free = freeMatch ? Math.round(parseInt(freeMatch[1]) / 1024) : null;
        
        const availableMatch = content.match(/MemAvailable:\s*(\d+)\s*kB/);
        memory.available = availableMatch ? Math.round(parseInt(availableMatch[1]) / 1024) : null;
        
        return memory;
    },

    // 解析存储信息
    parseStorageInfo(content) {
        const storage = {};
        
        const dataMatch = content.match(/\/data[\s\S]*?(\d+)\s+(\d+)\s+(\d+)/);
        if (dataMatch) {
            storage.total = Math.round(parseInt(dataMatch[1]) / 1024 / 1024);
            storage.used = Math.round(parseInt(dataMatch[2]) / 1024 / 1024);
            storage.free = Math.round(parseInt(dataMatch[3]) / 1024 / 1024);
        }
        
        return storage;
    },

    // 解析CPU信息
    parseCpuInfo(content) {
        const cpu = {};
        
        // CPU型号
        const modelMatch = content.match(/Hardware\s*:\s*(.+)/);
        cpu.model = modelMatch ? modelMatch[1].trim() : null;
        
        // CPU核心数
        const processorMatches = content.matchAll(/processor\s*:\s*\d+/g);
        cpu.cores = Array.from(processorMatches).length;
        
        // CPU频率
        const freqMatch = content.match(/cpu MHz\s*:\s*(\d+)/);
        cpu.frequency = freqMatch ? parseInt(freqMatch[1]) : null;
        
        return cpu;
    }
};

// ========================================
// 主应用对象
// ========================================
const BatteryHealthApp = {
    // 状态
    state: {
        currentData: {
            battery: {
                capacity: 0,
                cycles: 0,
                temperature: 0,
                voltage: 0,
                health: 100,
                designCapacity: 0,
                fullChargeCapacity: 0,
                status: 'Unknown',
                current: 0
            },
            device: {
                brand: 'Unknown',
                model: 'Unknown',
                androidVersion: 'Unknown',
                ram: 0,
                storage: 0,
                cpu: 'Unknown',
                screenSize: 'Unknown',
                screenResolution: 'Unknown'
            },
            performance: {
                memoryUsage: 0,
                cpuUsage: 0,
                lagEvents: [],
                score: 100,
                bottlenecks: []
            },
            activation: {
                manufactureDate: null,
                activationDate: null,
                warrantyStatus: 'Unknown',
                warrantyEndDate: null
            },
            charging: {
                power: 0,
                voltage: 0,
                current: 0,
                isFastCharging: false,
                chargerType: 'Unknown',
                timeToFull: 0
            },
            origin: {
                isOriginal: true,
                manufacturer: 'Unknown',
                productionDate: null,
                confidence: 0
            }
        },
        deviceInfo: null,
        isAnalyzing: false,
        currentTab: 'overview',
        chargingHistory: [],
        realTimeData: {
            voltage: 0,
            current: 0,
            power: 0,
            isCharging: false
        },
        monitoringInterval: null,
        chartInstances: {}
    },

    // 缓存DOM元素
    elements: {},

    // ========================================
    // 初始化
    // ========================================
    init() {
        this.cacheElements();
        this.bindEvents();
        this.updateTime();
        this.loadInitialData();
        
        // 每秒更新时间
        setInterval(() => this.updateTime(), 1000);
        
        // 启动实时监测
        this.startRealTimeMonitoring();
        
        // 隐藏启动画面
        setTimeout(() => {
            const splash = document.getElementById('splash-screen');
            const app = document.getElementById('app');
            if (splash) splash.classList.add('hidden');
            if (app) app.classList.remove('hidden');
        }, 2000);
    },

    cacheElements() {
        // 导航
        this.elements.navBar = document.getElementById('nav-bar');
        this.elements.pageTitle = document.getElementById('page-title');
        this.elements.btnImport = document.getElementById('btn-import');
        this.elements.btnShare = document.getElementById('btn-share');
        
        // 标签页
        this.elements.tabBtns = document.querySelectorAll('.tab-btn');
        this.elements.tabContents = document.querySelectorAll('.tab-content');
        
        // 概览页
        this.elements.healthRing = document.getElementById('health-ring');
        this.elements.healthPercentage = document.getElementById('health-percentage');
        this.elements.healthGrade = document.querySelector('.grade-badge');
        this.elements.currentCapacity = document.getElementById('current-capacity');
        this.elements.cycleCount = document.getElementById('cycle-count');
        this.elements.temperature = document.getElementById('temperature');
        this.elements.voltage = document.getElementById('voltage');
        this.elements.deviceModel = document.getElementById('device-model');
        this.elements.deviceBrand = document.getElementById('device-brand');
        this.elements.deviceAndroid = document.getElementById('device-android');
        this.elements.batteryStatusText = document.getElementById('battery-status-text');
        this.elements.recommendationsList = document.getElementById('recommendations-list');
        
        // 模态框
        this.elements.fileModal = document.getElementById('file-modal');
        this.elements.fileDropZone = document.getElementById('file-drop-zone');
        this.elements.fileInput = document.getElementById('file-input');
        this.elements.fileProgress = document.getElementById('file-progress');
        this.elements.progressFill = document.getElementById('progress-fill');
        this.elements.progressText = document.getElementById('progress-text');
        
        // 报告模态框
        this.elements.reportModal = document.getElementById('report-modal');
        this.elements.reportTitle = document.getElementById('report-title');
        this.elements.reportContent = document.getElementById('report-content');
        
        // Toast
        this.elements.toast = document.getElementById('toast');
        
        // 加载
        this.elements.loadingOverlay = document.getElementById('loading-overlay');
    },

    bindEvents() {
        // 标签切换
        this.elements.tabBtns.forEach(btn => {
            btn.addEventListener('click', (e) => {
                const tab = e.currentTarget.dataset.tab;
                this.switchTab(tab);
            });
        });
        
        // 导入按钮
        if (this.elements.btnImport) {
            this.elements.btnImport.addEventListener('click', () => {
                this.openFileModal();
            });
        }
        
        // 分享按钮
        if (this.elements.btnShare) {
            this.elements.btnShare.addEventListener('click', () => {
                this.shareReport();
            });
        }
        
        // 文件选择
        if (this.elements.fileDropZone) {
            this.elements.fileDropZone.addEventListener('click', () => {
                if (this.elements.fileInput) {
                    this.elements.fileInput.click();
                }
            });
        }
        
        if (this.elements.fileInput) {
            this.elements.fileInput.addEventListener('change', (e) => {
                if (e.target.files.length > 0) {
                    this.handleFile(e.target.files[0]);
                }
            });
        }
        
        // 拖拽上传
        if (this.elements.fileDropZone) {
            this.elements.fileDropZone.addEventListener('dragover', (e) => {
                e.preventDefault();
                this.elements.fileDropZone.classList.add('dragover');
            });
            
            this.elements.fileDropZone.addEventListener('dragleave', () => {
                this.elements.fileDropZone.classList.remove('dragover');
            });
            
            this.elements.fileDropZone.addEventListener('drop', (e) => {
                e.preventDefault();
                this.elements.fileDropZone.classList.remove('dragover');
                if (e.dataTransfer.files.length > 0) {
                    this.handleFile(e.dataTransfer.files[0]);
                }
            });
        }
        
        // 关闭模态框
        const modalClose = document.getElementById('modal-close');
        if (modalClose) {
            modalClose.addEventListener('click', () => {
                this.closeFileModal();
            });
        }
        
        const reportModalClose = document.getElementById('report-modal-close');
        if (reportModalClose) {
            reportModalClose.addEventListener('click', () => {
                this.closeReportModal();
            });
        }
        
        const btnCloseReport = document.getElementById('btn-close-report');
        if (btnCloseReport) {
            btnCloseReport.addEventListener('click', () => {
                this.closeReportModal();
            });
        }
        
        const btnExportReport = document.getElementById('btn-export-report');
        if (btnExportReport) {
            btnExportReport.addEventListener('click', () => {
                this.exportReport();
            });
        }
        
        // 报告生成按钮
        const btnWeeklyReport = document.getElementById('btn-weekly-report');
        if (btnWeeklyReport) {
            btnWeeklyReport.addEventListener('click', () => {
                this.generateWeeklyReport();
            });
        }
        
        const btnMonthlyReport = document.getElementById('btn-monthly-report');
        if (btnMonthlyReport) {
            btnMonthlyReport.addEventListener('click', () => {
                this.generateMonthlyReport();
            });
        }
        
        // 图表周期切换
        document.querySelectorAll('.chart-tab').forEach(tab => {
            tab.addEventListener('click', (e) => {
                document.querySelectorAll('.chart-tab').forEach(t => t.classList.remove('active'));
                e.target.classList.add('active');
                this.renderTrendChart(e.target.dataset.period);
            });
        });
        
        // Android接口
        if (window.AndroidFilePicker) {
            window.onFileSelected = (path) => {
                this.handleAndroidFile(path);
            };
        }
    },

    // ========================================
    // 1. 电池健康分析模块
    // ========================================
    analyzeBatteryHealth() {
        const data = this.state.currentData.battery;
        const deviceInfo = this.state.deviceInfo;
        
        // 获取电池规格
        const specs = this.getBatterySpecs(deviceInfo);
        
        // 计算健康度
        let healthPercentage = 100;
        if (data.fullChargeCapacity && data.designCapacity && data.designCapacity > 0) {
            healthPercentage = Math.round((data.fullChargeCapacity / data.designCapacity) * 100);
        } else if (data.cycles > 0) {
            // 根据循环次数估算
            healthPercentage = Math.max(0, Math.round(100 - (data.cycles * 0.05)));
        } else if (data.capacity > 0 && specs.typical > 0) {
            healthPercentage = Math.round((data.capacity / specs.typical) * 100);
        }
        
        // 温度健康因子
        let tempFactor = 1.0;
        if (data.temperature > 45) {
            tempFactor = 0.95;
        } else if (data.temperature > 40) {
            tempFactor = 0.98;
        }
        healthPercentage = Math.round(healthPercentage * tempFactor);
        
        // 确保健康度在合理范围内
        healthPercentage = Math.max(0, Math.min(100, healthPercentage));
        
        // 计算健康等级
        const grade = this.getHealthGrade(healthPercentage);
        
        // 生成维护建议
        const recommendations = this.generateHealthRecommendations(healthPercentage, data);
        
        // 更新数据
        this.state.currentData.battery.health = healthPercentage;
        
        // 保存到历史
        HistoryManager.add({
            health: healthPercentage,
            capacity: data.fullChargeCapacity || data.capacity,
            cycles: data.cycles,
            temperature: data.temperature,
            voltage: data.voltage
        });
        
        return {
            healthPercentage,
            grade: grade.grade,
            gradeClass: grade.class,
            capacity: data.fullChargeCapacity || data.capacity,
            designCapacity: data.designCapacity || specs.typical,
            cycles: data.cycles,
            temperature: data.temperature,
            voltage: data.voltage,
            recommendations,
            estimatedLife: this.estimateRemainingLife(healthPercentage),
            capacityLoss: (100 - healthPercentage).toFixed(1)
        };
    },

    generateHealthRecommendations(health, data) {
        const recommendations = [];
        
        if (health >= 95) {
            recommendations.push({ icon: '✅', text: '电池状态极佳，继续保持良好的充电习惯', priority: 'high' });
            recommendations.push({ icon: '💡', text: '建议每月进行一次完整的充放电循环', priority: 'medium' });
        } else if (health >= 80) {
            recommendations.push({ icon: '💡', text: '电池状态良好，注意避免过度充电', priority: 'high' });
            recommendations.push({ icon: '🔋', text: '建议在电量20%-80%之间使用', priority: 'high' });
            recommendations.push({ icon: '🌡️', text: '避免在高温环境下长时间使用设备', priority: 'medium' });
        } else if (health >= 60) {
            recommendations.push({ icon: '⚠️', text: '电池健康度一般，建议关注电池状态', priority: 'high' });
            recommendations.push({ icon: '🔌', text: '减少边充边用的频率', priority: 'high' });
            recommendations.push({ icon: '🔋', text: '考虑更换电池以获得更好的续航', priority: 'medium' });
        } else {
            recommendations.push({ icon: '🚨', text: '电池健康度较低，建议尽快更换电池', priority: 'high' });
            recommendations.push({ icon: '💾', text: '备份重要数据以防意外关机', priority: 'high' });
            recommendations.push({ icon: '🔌', text: '避免长时间外出时依赖此设备', priority: 'medium' });
        }
        
        if (data.temperature > 40) {
            recommendations.push({ icon: '🌡️', text: '电池温度过高，请暂停使用并让设备降温', priority: 'high' });
        } else if (data.temperature > 35) {
            recommendations.push({ icon: '🌡️', text: '电池温度偏高，建议暂停高负载应用', priority: 'medium' });
        }
        
        if (data.cycles > 500) {
            recommendations.push({ icon: '🔄', text: '循环次数较多，电池老化属正常现象', priority: 'low' });
        }
        
        return recommendations;
    },

    // ========================================
    // 2. 设备配置查询模块
    // ========================================
    parseDeviceConfig() {
        const info = this.state.deviceInfo || {};
        
        // 解析设备信息
        const brand = info.brand || 'Unknown';
        const model = info.model || 'Unknown';
        const androidVersion = info.androidVersion || 'Unknown';
        const sdkVersion = info.sdkVersion || 0;
        
        // 获取硬件规格
        const specs = this.getBatterySpecs(info);
        
        // 估算RAM和存储（根据设备型号）
        const { ram, storage, cpu } = this.estimateDeviceSpecs(brand, model);
        
        // 屏幕信息
        const screenInfo = this.getScreenInfo();
        
        // 更新状态
        this.state.currentData.device = {
            brand,
            model,
            androidVersion,
            ram,
            storage,
            cpu,
            screenSize: screenInfo.size,
            screenResolution: screenInfo.resolution,
            sdkVersion,
            device: info.device || 'Unknown',
            hardware: info.hardware || 'Unknown',
            kernelVersion: info.kernelVersion || 'Unknown',
            securityPatch: info.securityPatch || 'Unknown'
        };
        
        return {
            brand,
            model,
            androidVersion,
            sdkVersion,
            ram,
            storage,
            cpu,
            screenSize: screenInfo.size,
            screenResolution: screenInfo.resolution,
            batterySpecs: specs,
            device: info.device,
            hardware: info.hardware,
            kernelVersion: info.kernelVersion,
            securityPatch: info.securityPatch
        };
    },

    estimateDeviceSpecs(brand, model) {
        let ram = 8;
        let storage = 128;
        let cpu = 'Unknown';
        
        // 根据品牌和型号估算
        const highEndModels = ['Pro', 'Ultra', 'Max', 'Plus', 'Fold'];
        const isHighEnd = highEndModels.some(m => model && model.includes(m));
        
        if (isHighEnd) {
            ram = 12;
            storage = 256;
        }
        
        // 品牌特定估算
        if (brand === 'Xiaomi' || brand === 'Redmi') {
            cpu = model && model.includes('Pro') ? 'Snapdragon 8 Gen 2' : 'Snapdragon 7 Gen 1';
        } else if (brand === 'Huawei') {
            cpu = 'Kirin 9000';
        } else if (brand === 'OPPO' || brand === 'OnePlus') {
            cpu = 'Snapdragon 8 Gen 2';
        } else if (brand === 'vivo') {
            cpu = model && model.includes('iQOO') ? 'Snapdragon 8 Gen 2' : 'MediaTek Dimensity';
        } else if (brand === 'samsung') {
            cpu = 'Exynos 2200 / Snapdragon 8 Gen 2';
        }
        
        return { ram, storage, cpu };
    },

    getScreenInfo() {
        const width = window.screen.width;
        const height = window.screen.height;
        const pixelRatio = window.devicePixelRatio || 1;
        
        // 估算屏幕尺寸（基于分辨率）
        let size = '6.5"';
        const pixelCount = width * height * pixelRatio * pixelRatio;
        
        if (pixelCount > 3000000) {
            size = '6.7"';
        } else if (pixelCount > 2500000) {
            size = '6.5"';
        } else if (pixelCount > 2000000) {
            size = '6.1"';
        } else {
            size = '5.5"';
        }
        
        return {
            size,
            resolution: `${width * pixelRatio} x ${height * pixelRatio}`,
            width,
            height,
            pixelRatio
        };
    },

    // ========================================
    // 3. 性能分析模块
    // ========================================
    analyzePerformance() {
        const data = this.state.currentData;
        
        // 模拟性能数据采集
        const memoryUsage = this.getMemoryUsage();
        const cpuUsage = this.getCpuUsage();
        
        // 检测卡顿事件
        const lagEvents = this.detectLagEvents();
        
        // 识别性能瓶颈
        const bottlenecks = this.identifyBottlenecks(memoryUsage, cpuUsage, lagEvents);
        
        // 计算性能分数
        const score = this.calculatePerformanceScore(memoryUsage, cpuUsage, lagEvents);
        
        // 生成优化建议
        const optimizations = this.generateOptimizationSuggestions(bottlenecks);
        
        // 更新状态
        this.state.currentData.performance = {
            memoryUsage,
            cpuUsage,
            lagEvents,
            score,
            bottlenecks
        };
        
        return {
            memoryUsage,
            cpuUsage,
            lagEvents,
            score,
            bottlenecks,
            optimizations,
            status: score >= 80 ? '良好' : score >= 60 ? '一般' : '需优化'
        };
    },

    getMemoryUsage() {
        // 获取内存使用情况
        if (performance && performance.memory) {
            const used = performance.memory.usedJSHeapSize;
            const total = performance.memory.totalJSHeapSize;
            return Math.round((used / total) * 100);
        }
        // 模拟数据
        return Math.floor(Math.random() * 30) + 50;
    },

    getCpuUsage() {
        // 模拟CPU使用率
        return Math.floor(Math.random() * 40) + 20;
    },

    detectLagEvents() {
        const apps = [
            { name: '微信', package: 'com.tencent.mm', icon: '💬' },
            { name: '抖音', package: 'com.ss.android.ugc.aweme', icon: '🎵' },
            { name: '淘宝', package: 'com.taobao.taobao', icon: '🛒' },
            { name: '王者荣耀', package: 'com.tencent.tmgp.sgame', icon: '🎮' },
            { name: '京东', package: 'com.jingdong.app.mall', icon: '📦' },
            { name: '支付宝', package: 'com.eg.android.AlipayGphone', icon: '💰' }
        ];
        
        return apps.map(app => ({
            ...app,
            lagCount: Math.floor(Math.random() * 8),
            avgLagDuration: (Math.random() * 2 + 0.5).toFixed(1),
            lastLagTime: `${Math.floor(Math.random() * 24)}小时前`
        })).filter(app => app.lagCount > 0).sort((a, b) => b.lagCount - a.lagCount);
    },

    identifyBottlenecks(memoryUsage, cpuUsage, lagEvents) {
        const bottlenecks = [];
        
        if (memoryUsage > 80) {
            bottlenecks.push({
                type: 'memory',
                name: '内存压力',
                description: '可用内存不足，建议关闭后台应用',
                severity: memoryUsage > 90 ? 'high' : 'medium',
                impact: '应用切换缓慢，可能出现闪退'
            });
        }
        
        if (cpuUsage > 70) {
            bottlenecks.push({
                type: 'cpu',
                name: 'CPU 高负载',
                description: '处理器负载较高，可能存在后台任务',
                severity: cpuUsage > 85 ? 'high' : 'medium',
                impact: '设备发热，续航下降'
            });
        }
        
        if (lagEvents.length > 3) {
            bottlenecks.push({
                type: 'ui',
                name: '界面卡顿',
                description: `检测到 ${lagEvents.length} 个应用出现卡顿`,
                severity: lagEvents.length > 5 ? 'high' : 'medium',
                impact: '用户体验下降'
            });
        }
        
        // 存储空间检查
        const storageUsage = this.getStorageUsage();
        if (storageUsage > 85) {
            bottlenecks.push({
                type: 'storage',
                name: '存储空间不足',
                description: `存储使用率达 ${storageUsage}%`,
                severity: storageUsage > 95 ? 'high' : 'medium',
                impact: '无法安装应用，系统运行缓慢'
            });
        }
        
        return bottlenecks;
    },

    getStorageUsage() {
        // 模拟存储使用率
        return Math.floor(Math.random() * 30) + 50;
    },

    calculatePerformanceScore(memoryUsage, cpuUsage, lagEvents) {
        let score = 100;
        
        // 内存扣分
        score -= Math.max(0, (memoryUsage - 50) * 0.5);
        
        // CPU扣分
        score -= Math.max(0, (cpuUsage - 40) * 0.5);
        
        // 卡顿扣分
        score -= lagEvents.length * 3;
        
        return Math.max(0, Math.round(score));
    },

    // ========================================
    // 4. 激活日期查询模块
    // ========================================
    checkActivationDate() {
        const deviceInfo = this.state.deviceInfo;
        const batteryData = this.state.currentData.battery;
        
        // 估算生产日期（基于循环次数和当前时间）
        const manufactureDate = this.estimateManufactureDate(batteryData);
        
        // 估算激活日期（生产日期后1-3个月）
        const activationDate = this.estimateActivationDate(manufactureDate);
        
        // 计算保修状态
        const warrantyInfo = this.calculateWarrantyStatus(activationDate);
        
        // 服务资格
        const serviceEligibility = this.checkServiceEligibility(warrantyInfo, batteryData);
        
        // 更新状态
        this.state.currentData.activation = {
            manufactureDate,
            activationDate,
            warrantyStatus: warrantyInfo.status,
            warrantyEndDate: warrantyInfo.endDate
        };
        
        return {
            manufactureDate,
            activationDate,
            warrantyStatus: warrantyInfo.status,
            warrantyEndDate: warrantyInfo.endDate,
            daysRemaining: warrantyInfo.daysRemaining,
            serviceEligibility,
            isExpired: warrantyInfo.status === 'expired'
        };
    },

    estimateManufactureDate(batteryData) {
        const now = new Date();
        
        // 基于循环次数估算
        if (batteryData.cycles > 0) {
            // 假设平均每天0.5-1个循环
            const daysAgo = Math.round(batteryData.cycles / 0.7);
            const date = new Date(now);
            date.setDate(date.getDate() - daysAgo);
            
            // 再往前推1-3个月作为生产日期
            date.setMonth(date.getMonth() - Math.floor(Math.random() * 2) - 1);
            return date;
        }
        
        // 默认估算（6-18个月前）
        const monthsAgo = Math.floor(Math.random() * 12) + 6;
        const date = new Date(now);
        date.setMonth(date.getMonth() - monthsAgo);
        return date;
    },

    estimateActivationDate(manufactureDate) {
        const date = new Date(manufactureDate);
        // 激活日期通常在生产日期后1-3个月
        date.setMonth(date.getMonth() + Math.floor(Math.random() * 2) + 1);
        return date;
    },

    calculateWarrantyStatus(activationDate) {
        const now = new Date();
        const warrantyEnd = new Date(activationDate);
        warrantyEnd.setFullYear(warrantyEnd.getFullYear() + 1);
        
        const daysRemaining = Math.floor((warrantyEnd - now) / (1000 * 60 * 60 * 24));
        
        let status = 'active';
        if (daysRemaining < 0) {
            status = 'expired';
        } else if (daysRemaining < 30) {
            status = 'expiring';
        }
        
        return {
            status,
            endDate: warrantyEnd,
            daysRemaining: Math.max(0, daysRemaining)
        };
    },

    checkServiceEligibility(warrantyInfo, batteryData) {
        const eligibility = {
            canService: true,
            type: 'paid',
            reasons: []
        };
        
        if (warrantyInfo.status === 'active') {
            eligibility.type = 'warranty';
            eligibility.reasons.push('在保修期内');
        }
        
        if (batteryData.health < 80) {
            eligibility.reasons.push('电池健康度低于80%');
        }
        
        if (batteryData.cycles > 500) {
            eligibility.reasons.push('循环次数超过500次');
        }
        
        return eligibility;
    },

    // ========================================
    // 5. 趋势追踪模块
    // ========================================
    trackTrends(period = 30) {
        const history = HistoryManager.getLatest(period);
        
        if (history.length < 2) {
            return {
                hasData: false,
                message: '数据不足，需要至少2个数据点'
            };
        }
        
        // 计算各项趋势
        const healthTrend = this.calculateMetricTrend(history, 'health');
        const capacityTrend = this.calculateMetricTrend(history, 'capacity');
        const cycleTrend = this.calculateMetricTrend(history, 'cycles');
        const tempTrend = this.calculateMetricTrend(history, 'temperature');
        
        // 计算预测
        const predictions = this.predictFutureTrends(history, healthTrend);
        
        // 生成图表数据
        const chartData = this.prepareChartData(history);
        
        return {
            hasData: true,
            period,
            dataPoints: history.length,
            trends: {
                health: healthTrend,
                capacity: capacityTrend,
                cycles: cycleTrend,
                temperature: tempTrend
            },
            predictions,
            chartData,
            summary: this.generateTrendSummary(healthTrend, capacityTrend, predictions)
        };
    },

    calculateMetricTrend(history, metric) {
        const values = history.map(h => h[metric]).filter(v => v !== undefined && v !== null);
        
        if (values.length < 2) return null;
        
        const first = values[0];
        const last = values[values.length - 1];
        const change = last - first;
        const percentChange = ((change / first) * 100).toFixed(2);
        const avg = (values.reduce((a, b) => a + b, 0) / values.length).toFixed(2);
        const min = Math.min(...values);
        const max = Math.max(...values);
        
        return {
            change,
            percentChange,
            average: parseFloat(avg),
            min,
            max,
            direction: change > 0 ? 'up' : change < 0 ? 'down' : 'stable',
            rate: (change / values.length).toFixed(3)
        };
    },

    predictFutureTrends(history, healthTrend) {
        if (!healthTrend) return null;
        
        const currentHealth = history[history.length - 1].health || 100;
        const degradationRate = Math.abs(parseFloat(healthTrend.rate));
        
        // 预测达到80%的时间
        const daysTo80 = degradationRate > 0 
            ? Math.ceil((currentHealth - 80) / degradationRate)
            : Infinity;
        
        // 预测达到60%的时间
        const daysTo60 = degradationRate > 0
            ? Math.ceil((currentHealth - 60) / degradationRate)
            : Infinity;
        
        return {
            daysTo80: daysTo80 > 0 ? daysTo80 : 0,
            daysTo60: daysTo60 > 0 ? daysTo60 : 0,
            estimatedReplacement: daysTo80 < 365 ? '建议一年内更换' : '电池状态良好',
            degradationRate: degradationRate.toFixed(2)
        };
    },

    prepareChartData(history) {
        return {
            labels: history.map(h => {
                const date = new Date(h.timestamp);
                return `${date.getMonth() + 1}/${date.getDate()}`;
            }),
            health: history.map(h => h.health || 0),
            capacity: history.map(h => h.capacity ? Math.round(h.capacity / 1000) : 0),
            cycles: history.map(h => h.cycles || 0),
            temperature: history.map(h => h.temperature || 0)
        };
    },

    generateTrendSummary(healthTrend, capacityTrend, predictions) {
        const summaries = [];
        
        if (healthTrend) {
            if (healthTrend.direction === 'down') {
                summaries.push(`电池健康度下降 ${Math.abs(healthTrend.percentChange)}%`);
            } else if (healthTrend.direction === 'up') {
                summaries.push(`电池健康度上升 ${healthTrend.percentChange}%`);
            } else {
                summaries.push('电池健康度保持稳定');
            }
        }
        
        if (predictions && predictions.daysTo80 < 180) {
            summaries.push(`预计 ${predictions.daysTo80} 天后需要更换电池`);
        }
        
        return summaries;
    },

    // ========================================
    // 6. 续航分析模块
    // ========================================
    analyzeBatteryLife() {
        const data = this.state.currentData.battery;
        const deviceInfo = this.state.currentData.device;
        
        // 计算屏幕使用时间
        const screenOnTime = this.calculateScreenOnTime(data);
        
        // 计算待机时间
        const standbyTime = this.calculateStandbyTime(data);
        
        // 应用耗电排行
        const powerRanking = this.generatePowerRanking();
        
        // 功耗分解
        const powerBreakdown = this.analyzePowerBreakdown();
        
        // 估算电池寿命
        const estimatedLife = this.estimateBatteryLife(data, screenOnTime);
        
        // 检测智能手表支持
        const smartwatchSupport = this.detectSmartwatchSupport();
        
        return {
            screenOnTime,
            standbyTime,
            powerRanking,
            powerBreakdown,
            estimatedLife,
            smartwatchSupport,
            health: data.health,
            recommendations: this.generateBatteryLifeRecommendations(powerBreakdown)
        };
    },

    calculateScreenOnTime(batteryData) {
        // 基于电池健康度和容量估算屏幕使用时间
        const baseHours = 6;
        const healthFactor = (batteryData.health || 100) / 100;
        const capacityFactor = batteryData.capacity / 4000;
        
        const estimatedHours = baseHours * healthFactor * Math.min(capacityFactor, 1.5);
        return {
            hours: estimatedHours.toFixed(1),
            minutes: Math.round(estimatedHours * 60),
            level: estimatedHours > 5 ? 'good' : estimatedHours > 3 ? 'average' : 'poor'
        };
    },

    calculateStandbyTime(batteryData) {
        const baseHours = 48;
        const healthFactor = (batteryData.health || 100) / 100;
        
        const estimatedHours = baseHours * healthFactor;
        return {
            hours: Math.round(estimatedHours),
            days: (estimatedHours / 24).toFixed(1),
            level: estimatedHours > 36 ? 'good' : estimatedHours > 24 ? 'average' : 'poor'
        };
    },

    generatePowerRanking() {
        const apps = [
            { name: '屏幕', package: 'system.screen', type: 'system', baseConsumption: 35 },
            { name: '微信', package: 'com.tencent.mm', type: 'app', baseConsumption: 18 },
            { name: '抖音', package: 'com.ss.android.ugc.aweme', type: 'app', baseConsumption: 15 },
            { name: '系统', package: 'android.system', type: 'system', baseConsumption: 12 },
            { name: '淘宝', package: 'com.taobao.taobao', type: 'app', baseConsumption: 8 },
            { name: '王者荣耀', package: 'com.tencent.tmgp.sgame', type: 'app', baseConsumption: 6 },
            { name: '其他应用', package: 'others', type: 'app', baseConsumption: 6 }
        ];
        
        // 添加使用时间和随机变化
        return apps.map((app, index) => {
            const variation = (Math.random() - 0.5) * 4;
            const consumption = Math.max(1, app.baseConsumption + variation);
            const usageTime = app.type === 'system' && app.name === '屏幕' 
                ? '4小时30分'
                : app.type === 'system'
                ? '持续运行'
                : `${Math.floor(Math.random() * 3) + 1}小时${Math.floor(Math.random() * 60)}分`;
            
            return {
                ...app,
                consumption: Math.round(consumption),
                usageTime,
                rank: index + 1
            };
        }).sort((a, b) => b.consumption - a.consumption);
    },

    analyzePowerBreakdown() {
        return {
            screen: { percentage: 35, description: '屏幕显示' },
            cpu: { percentage: 20, description: '处理器' },
            network: { percentage: 15, description: '网络连接' },
            apps: { percentage: 20, description: '应用程序' },
            system: { percentage: 10, description: '系统服务' }
        };
    },

    estimateBatteryLife(batteryData, screenOnTime) {
        const currentLevel = batteryData.capacity || 50;
        const hoursPerPercent = parseFloat(screenOnTime.hours) / 100;
        const remainingHours = currentLevel * hoursPerPercent;
        
        return {
            remainingHours: remainingHours.toFixed(1),
            remainingMinutes: Math.round(remainingHours * 60),
            canLastUntil: this.calculateLastUntil(remainingHours),
            usageScenario: {
                light: (remainingHours * 1.5).toFixed(1),
                normal: remainingHours.toFixed(1),
                heavy: (remainingHours * 0.6).toFixed(1)
            }
        };
    },

    calculateLastUntil(hours) {
        const now = new Date();
        const until = new Date(now.getTime() + hours * 60 * 60 * 1000);
        return `${until.getHours()}:${String(until.getMinutes()).padStart(2, '0')}`;
    },

    detectSmartwatchSupport() {
        const brands = ['Xiaomi', 'Huawei', 'OPPO', 'vivo', 'OnePlus', 'samsung'];
        const brand = this.state.currentData.device.brand;
        
        const watchSupport = {
            supported: brands.includes(brand),
            brand: brand,
            compatibleModels: this.getCompatibleSmartwatches(brand),
            batteryOptimization: true
        };
        
        return watchSupport;
    },

    getCompatibleSmartwatches(brand) {
        const watchMap = {
            'Xiaomi': ['Mi Watch', 'Redmi Watch', 'Xiaomi Watch S1'],
            'Huawei': ['Huawei Watch GT 4', 'Huawei Watch 4', 'Huawei Band 8'],
            'OPPO': ['OPPO Watch 4 Pro', 'OPPO Watch 3', 'OPPO Band 2'],
            'vivo': ['vivo WATCH 3', 'vivo WATCH 2'],
            'OnePlus': ['OnePlus Watch', 'OnePlus Watch 2'],
            'samsung': ['Galaxy Watch 6', 'Galaxy Watch 5', 'Galaxy Fit 3']
        };
        
        return watchMap[brand] || ['通用智能手表'];
    },

    generateBatteryLifeRecommendations(powerBreakdown) {
        const recommendations = [];
        
        if (powerBreakdown.screen.percentage > 40) {
            recommendations.push({
                icon: '🔆',
                title: '降低屏幕亮度',
                description: '屏幕是最大耗电项，适当降低亮度可显著延长续航',
                impact: 'high'
            });
        }
        
        if (powerBreakdown.network.percentage > 20) {
            recommendations.push({
                icon: '📶',
                title: '优化网络设置',
                description: '在信号弱的地方关闭移动数据或启用飞行模式',
                impact: 'medium'
            });
        }
        
        recommendations.push({
            icon: '🚀',
            title: '限制后台应用',
            description: '关闭不必要的后台应用刷新',
            impact: 'high'
        });
        
        return recommendations;
    },

    // ========================================
    // 7. 电池溯源模块
    // ========================================
    checkBatteryOrigin() {
        const data = this.state.currentData.battery;
        const deviceInfo = this.state.deviceInfo;
        
        if (!data || !deviceInfo) {
            return {
                isOriginal: false,
                manufacturer: 'Unknown',
                confidence: 0,
                message: '数据不足，无法分析'
            };
        }
        
        // 分析电池是否为原装
        const originAnalysis = this.analyzeOriginAuthenticity(data, deviceInfo);
        
        // 获取制造商信息
        const manufacturer = this.identifyBatteryManufacturer(data, deviceInfo);
        
        // 估算生产日期
        const productionDate = this.estimateProductionDate(data);
        
        // 计算真实性分数
        const authenticityScore = this.calculateAuthenticityScore(originAnalysis, manufacturer);
        
        // 更新状态
        this.state.currentData.origin = {
            isOriginal: originAnalysis.isOriginal,
            manufacturer: manufacturer.name,
            productionDate,
            confidence: authenticityScore
        };
        
        return {
            isOriginal: originAnalysis.isOriginal,
            manufacturer: manufacturer,
            productionDate,
            authenticityScore,
            confidence: this.getConfidenceLevel(authenticityScore),
            analysisDetails: originAnalysis.details,
            warnings: originAnalysis.warnings
        };
    },

    analyzeOriginAuthenticity(batteryData, deviceInfo) {
        const details = [];
        const warnings = [];
        let isOriginal = true;
        
        // 检查电池规格是否匹配
        const specs = this.getBatterySpecs(deviceInfo);
        const capacityMatch = batteryData.designCapacity 
            ? Math.abs(batteryData.designCapacity - specs.typical) < 500
            : true;
        
        if (!capacityMatch) {
            isOriginal = false;
            warnings.push('电池容量与设备规格不匹配');
        }
        
        details.push({
            check: '容量匹配',
            passed: capacityMatch,
            message: capacityMatch ? '容量规格正常' : '容量规格异常'
        });
        
        // 检查电压范围
        const voltageNormal = !batteryData.voltage || (batteryData.voltage >= 3500 && batteryData.voltage <= 4500);
        if (!voltageNormal) {
            warnings.push('电池电压异常');
        }
        
        details.push({
            check: '电压检测',
            passed: voltageNormal,
            message: voltageNormal ? '电压范围正常' : '电压超出正常范围'
        });
        
        // 检查循环次数合理性
        const cyclesReasonable = !batteryData.cycles || batteryData.cycles < 2000;
        if (!cyclesReasonable) {
            warnings.push('循环次数异常偏高');
        }
        
        details.push({
            check: '循环次数',
            passed: cyclesReasonable,
            message: cyclesReasonable ? '循环次数正常' : '循环次数异常'
        });
        
        // 基于健康度判断
        const healthReasonable = batteryData.health > 50;
        if (!healthReasonable) {
            warnings.push('电池健康度异常，可能为非原装电池');
            isOriginal = false;
        }
        
        return {
            isOriginal: isOriginal && warnings.length === 0,
            details,
            warnings
        };
    },

    identifyBatteryManufacturer(batteryData, deviceInfo) {
        const manufacturers = {
            'ATL': { name: 'ATL (新能源)', country: '中国', quality: 'premium' },
            'Samsung SDI': { name: 'Samsung SDI', country: '韩国', quality: 'premium' },
            'LG Chem': { name: 'LG Chem', country: '韩国', quality: 'premium' },
            'BYD': { name: 'BYD (比亚迪)', country: '中国', quality: 'high' },
            'Coslight': { name: 'Coslight (光宇)', country: '中国', quality: 'high' },
            'Sunwoda': { name: 'Sunwoda (欣旺达)', country: '中国', quality: 'high' },
            'Desay': { name: 'Desay (德赛)', country: '中国', quality: 'high' }
        };
        
        // 基于设备品牌推断制造商
        const brand = deviceInfo?.brand;
        let manufacturer = manufacturers['ATL']; // 默认
        
        if (brand === 'samsung') {
            manufacturer = manufacturers['Samsung SDI'];
        } else if (brand === 'Huawei') {
            manufacturer = manufacturers['ATL'];
        } else if (brand === 'Xiaomi' || brand === 'Redmi') {
            manufacturer = manufacturers['Sunwoda'];
        } else if (brand === 'OPPO' || brand === 'OnePlus') {
            manufacturer = manufacturers['ATL'];
        }
        
        return manufacturer;
    },

    estimateProductionDate(batteryData) {
        // 基于循环次数和当前时间估算
        if (batteryData.cycles > 0) {
            const daysAgo = Math.round(batteryData.cycles / 0.7);
            const date = new Date();
            date.setDate(date.getDate() - daysAgo);
            // 生产日期通常比首次使用早1-3个月
            date.setMonth(date.getMonth() - Math.floor(Math.random() * 2) - 1);
            return date;
        }
        
        // 默认6-24个月前
        const monthsAgo = Math.floor(Math.random() * 18) + 6;
        const date = new Date();
        date.setMonth(date.getMonth() - monthsAgo);
        return date;
    },

    calculateAuthenticityScore(originAnalysis, manufacturer) {
        let score = 100;
        
        // 根据警告扣分
        score -= originAnalysis.warnings.length * 15;
        
        // 根据检查项扣分
        originAnalysis.details.forEach(detail => {
            if (!detail.passed) {
                score -= 10;
            }
        });
        
        // 制造商质量加分
        if (manufacturer.quality === 'premium') {
            score += 5;
        }
        
        return Math.max(0, Math.min(100, score));
    },

    getConfidenceLevel(score) {
        if (score >= 90) return '极高';
        if (score >= 75) return '高';
        if (score >= 60) return '中等';
        if (score >= 40) return '低';
        return '极低';
    },

    // ========================================
    // 8. 充电功率监测模块
    // ========================================
    monitorChargingPower() {
        const data = this.state.currentData.battery;
        
        // 计算实时充电功率
        const power = this.calculateChargingPower(data);
        
        // 判断充电速度
        const chargingSpeed = this.calculateChargingSpeed(power);
        
        // 估算充满时间
        const timeEstimates = this.estimateTimeToFull(data, power);
        
        // 检测充电器信息
        const chargerInfo = this.detectChargerInfo(power);
        
        // 更新充电历史
        this.updateChargingHistory(power);
        
        // 生成充电曲线数据
        const chargingCurve = this.generateChargingCurve(data, power);
        
        // 更新状态
        this.state.currentData.charging = {
            power: power.wattage,
            voltage: power.voltage,
            current: power.current,
            isFastCharging: chargingSpeed.isFast,
            chargerType: chargerInfo.type,
            timeToFull: timeEstimates.to100
        };
        
        return {
            power: power.wattage,
            voltage: power.voltage,
            current: power.current,
            chargingSpeed,
            timeEstimates,
            chargerInfo,
            chargingCurve,
            isCharging: data.status === 'Charging' || data.status === 'Full',
            status: this.getChargingStatusText(data.status)
        };
    },

    calculateChargingPower(batteryData) {
        let voltage = batteryData.voltage || 4000;
        let current = Math.abs(batteryData.current || 0);
        
        // 如果没有电流数据，根据状态估算
        if (current === 0 && (batteryData.status === 'Charging' || batteryData.status === 'Full')) {
            current = 2000; // 默认2A
        }
        
        // 计算功率 (W = V * A / 1000)
        const wattage = (voltage * current) / 1000000;
        
        return {
            voltage: voltage / 1000, // 转换为V
            current,
            wattage: parseFloat(wattage.toFixed(2))
        };
    },

    calculateChargingSpeed(power) {
        const wattage = power.wattage;
        
        let level = 'slow';
        let description = '慢速充电';
        let isFast = false;
        let icon = '🐢';
        
        if (wattage >= 60) {
            level = 'ultra';
            description = '超快充电';
            isFast = true;
            icon = '🔥';
        } else if (wattage >= 30) {
            level = 'fast';
            description = '快速充电';
            isFast = true;
            icon = '⚡';
        } else if (wattage >= 15) {
            level = 'normal';
            description = '正常充电';
            isFast = false;
            icon = '🔌';
        } else if (wattage >= 5) {
            level = 'slow';
            description = '慢速充电';
            isFast = false;
            icon = '🐌';
        }
        
        return {
            level,
            description,
            isFast,
            icon,
            wattage,
            percentage: Math.min(100, (wattage / 65) * 100)
        };
    },

    estimateTimeToFull(batteryData, power) {
        const currentLevel = batteryData.capacity || 50;
        const batteryCapacity = batteryData.designCapacity || 4000;
        const wattage = power.wattage;
        
        if (wattage <= 0) {
            return { to50: '--', to80: '--', to100: '--' };
        }
        
        // 估算充电效率（随电量增加而降低）
        const getEfficiency = (level) => {
            if (level < 50) return 0.9;
            if (level < 80) return 0.75;
            return 0.5;
        };
        
        // 计算充电时间（分钟）
        const calculateTime = (fromLevel, toLevel) => {
            const avgEfficiency = (getEfficiency(fromLevel) + getEfficiency(toLevel)) / 2;
            const energyNeeded = (batteryCapacity * (toLevel - fromLevel) / 100) / avgEfficiency;
            const hours = energyNeeded / (wattage * 1000);
            return Math.round(hours * 60);
        };
        
        return {
            to50: currentLevel < 50 ? `${calculateTime(currentLevel, 50)}分钟` : '已完成',
            to80: currentLevel < 80 ? `${calculateTime(Math.min(currentLevel, 50), 80)}分钟` : '已完成',
            to100: currentLevel < 100 ? `${calculateTime(Math.min(currentLevel, 80), 100)}分钟` : '已完成`,
            totalMinutes: currentLevel < 100 ? calculateTime(currentLevel, 100) : 0
        };
    },

    detectChargerInfo(power) {
        const wattage = power.wattage;
        
        let type = '标准充电器';
        let maxOutput = '5W';
        let protocol = 'USB BC 1.2';
        let supportsPPS = false;
        
        if (wattage >= 100) {
            type = '超快闪充充电器';
            maxOutput = '120W+';
            protocol = '私有快充协议';
            supportsPPS = true;
        } else if (wattage >= 65) {
            type = '超级闪充充电器';
            maxOutput = '65W-100W';
            protocol = 'USB PD / 私有协议';
            supportsPPS = true;
        } else if (wattage >= 30) {
            type = '快速充电器';
            maxOutput = '30W-65W';
            protocol = 'USB PD / QC 4.0';
            supportsPPS = true;
        } else if (wattage >= 18) {
            type = '快充充电器';
            maxOutput = '18W-30W';
            protocol = 'QC 3.0 / USB PD';
        } else if (wattage >= 10) {
            type = '普通充电器';
            maxOutput = '10W-18W';
            protocol = 'QC 2.0 / Apple 2.4A';
        }
        
        return {
            type,
            maxOutput,
            protocol,
            supportsPPS,
            detectedPower: `${wattage}W`
        };
    },

    updateChargingHistory(power) {
        const now = Date.now();
        this.state.chargingHistory.push({
            timestamp: now,
            power: power.wattage,
            voltage: power.voltage,
            current: power.current
        });
        
        // 只保留最近100条记录
        if (this.state.chargingHistory.length > 100) {
            this.state.chargingHistory.shift();
        }
    },

    generateChargingCurve(batteryData, power) {
        const currentLevel = batteryData.capacity || 50;
        const points = [];
        
        // 生成从当前电量到100%的预估充电曲线
        for (let level = currentLevel; level <= 100; level += 5) {
            // 充电功率随电量增加而降低
            let efficiency = 1.0;
            if (level > 80) {
                efficiency = 0.5;
            } else if (level > 50) {
                efficiency = 0.8;
            }
            
            const estimatedPower = power.wattage * efficiency;
            points.push({
                level,
                power: parseFloat(estimatedPower.toFixed(1)),
                time: this.estimateTimeToLevel(currentLevel, level, batteryData.designCapacity || 4000, power.wattage)
            });
        }
        
        return points;
    },

    estimateTimeToLevel(from, to, capacity, wattage) {
        if (wattage <= 0) return 0;
        const energyNeeded = (capacity * (to - from) / 100);
        const hours = energyNeeded / (wattage * 1000 * 0.8); // 考虑80%效率
        return Math.round(hours * 60);
    },

    getChargingStatusText(status) {
        const statusMap = {
            'Charging': '正在充电',
            'Discharging': '正在放电',
            'Full': '已充满',
            'Not charging': '未充电',
            'Unknown': '未知状态'
        };
        return statusMap[status] || status;
    },

    // ========================================
    // 报告生成方法
    // ========================================
    generateWeeklyReport() {
        const history = HistoryManager.getLatest(7);
        const report = this.generateReport('周报', history, 7);
        this.showReportModal(report);
        return report;
    },

    generateMonthlyReport() {
        const history = HistoryManager.getLatest(30);
        const report = this.generateReport('月报', history, 30);
        this.showReportModal(report);
        return report;
    },

    generateReport(title, history, days) {
        const healthData = this.analyzeBatteryHealth();
        const deviceConfig = this.parseDeviceConfig();
        const performance = this.analyzePerformance();
        const activation = this.checkActivationDate();
        const trends = this.trackTrends(days);
        const batteryLife = this.analyzeBatteryLife();
        const origin = this.checkBatteryOrigin();
        const charging = this.monitorChargingPower();
        
        const report = {
            title: `电池健康${title}`,
            generatedAt: new Date().toISOString(),
            period: days,
            summary: {
                healthPercentage: healthData.healthPercentage,
                grade: healthData.grade,
                deviceModel: deviceConfig.model,
                deviceBrand: deviceConfig.brand
            },
            batteryHealth: healthData,
            deviceConfig,
            performance,
            activation,
            trends,
            batteryLife,
            origin,
            charging,
            history: {
                dataPoints: history.length,
                avgHealth: history.length > 0 
                    ? (history.reduce((a, b) => a + (b.health || 0), 0) / history.length).toFixed(1)
                    : 0
            }
        };
        
        // 保存报告到本地存储
        this.saveReport(report);
        
        return report;
    },

    saveReport(report) {
        const reports = JSON.parse(localStorage.getItem('battery_reports') || '[]');
        reports.push(report);
        
        // 只保留最近20份报告
        if (reports.length > 20) {
            reports.shift();
        }
        
        localStorage.setItem('battery_reports', JSON.stringify(reports));
    },

    showReportModal(report) {
        if (!this.elements.reportTitle || !this.elements.reportContent) return;
        
        const healthData = report.batteryHealth;
        const trends = report.trends;
        
        let html = `
            <div class="report-section">
                <h4>电池健康概况</h4>
                <div class="report-grid">
                    <div class="report-item">
                        <span class="report-item-label">当前健康度</span>
                        <span class="report-item-value">${healthData.healthPercentage}%</span>
                    </div>
                    <div class="report-item">
                        <span class="report-item-label">健康等级</span>
                        <span class="report-item-value">${healthData.grade}</span>
                    </div>
                    <div class="report-item">
                        <span class="report-item-label">循环次数</span>
                        <span class="report-item-value">${healthData.cycles || '--'}</span>
                    </div>
                    <div class="report-item">
                        <span class="report-item-label">当前容量</span>
                        <span class="report-item-value">${healthData.capacity ? Math.round(healthData.capacity / 1000) + 'mAh' : '--'}</span>
                    </div>
                </div>
            </div>
        `;
        
        if (trends && trends.hasData) {
            const healthTrend = trends.trends.health;
            if (healthTrend) {
                html += `
                    <div class="report-section">
                        <h4>趋势分析（最近${report.period}天）</h4>
                        <div class="report-grid">
                            <div class="report-item">
                                <span class="report-item-label">健康度变化</span>
                                <span class="report-item-value" style="color: ${healthTrend.direction === 'down' ? 'var(--ios-red)' : 'var(--ios-green)'}">${healthTrend.change > 0 ? '+' : ''}${healthTrend.change.toFixed(1)}%</span>
                            </div>
                            <div class="report-item">
                                <span class="report-item-label">数据点数</span>
                                <span class="report-item-value">${trends.dataPoints}</span>
                            </div>
                            <div class="report-item">
                                <span class="report-item-label">平均健康度</span>
                                <span class="report-item-value">${healthTrend.average}%</span>
                            </div>
                            <div class="report-item">
                                <span class="report-item-label">健康度范围</span>
                                <span class="report-item-value">${healthTrend.min}% - ${healthTrend.max}%</span>
                            </div>
                        </div>
                    </div>
                `;
            }
        }
        
        html += `
            <div class="report-section">
                <h4>维护建议</h4>
                <ul style="padding-left: 20px; line-height: 1.8; color: var(--label);">
                    ${this.getReportRecommendationsHtml(healthData.healthPercentage)}
                </ul>
            </div>
        `;
        
        this.elements.reportTitle.textContent = report.title;
        this.elements.reportContent.innerHTML = html;
        this.elements.reportModal.classList.add('active');
    },

    getReportRecommendationsHtml(health) {
        const recommendations = [];
        
        if (health >= 95) {
            recommendations.push('电池状态极佳，继续保持良好的充电习惯');
            recommendations.push('建议每月进行一次完整的充放电循环');
        } else if (health >= 80) {
            recommendations.push('电池状态良好，注意避免过度充电');
            recommendations.push('建议在电量20%-80%之间使用');
            recommendations.push('避免在高温环境下长时间使用设备');
        } else if (health >= 60) {
            recommendations.push('电池健康度一般，建议关注电池状态');
            recommendations.push('减少边充边用的频率');
            recommendations.push('考虑更换电池以获得更好的续航');
        } else {
            recommendations.push('电池健康度较低，建议尽快更换电池');
            recommendations.push('避免长时间外出时依赖此设备');
            recommendations.push('备份重要数据以防意外关机');
        }
        
        return recommendations.map(r => `<li>${r}</li>`).join('');
    },

    // ========================================
    // 图表渲染方法
    // ========================================
    renderCharts() {
        this.renderHealthCharts();
        this.renderPerformanceCharts();
        this.renderChargingCharts();
    },

    renderHealthCharts() {
        this.renderTrendChart('week');
        this.renderCapacityChart();
        this.renderCycleChart();
        this.renderTempChart();
    },

    renderTrendChart(period) {
        const canvas = document.getElementById('trend-chart');
        if (!canvas) return;
        
        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        const rect = canvas.getBoundingClientRect();
        
        canvas.width = rect.width * dpr;
        canvas.height = rect.height * dpr;
        ctx.scale(dpr, dpr);
        
        // 获取历史数据
        const days = period === 'week' ? 7 : period === 'month' ? 30 : 365;
        const history = HistoryManager.getLatest(days);
        
        if (history.length < 2) {
            this.drawNoData(ctx, rect.width, rect.height);
            return;
        }
        
        // 准备数据
        const labels = history.map(h => {
            const date = new Date(h.timestamp);
            return period === 'year' 
                ? `${date.getMonth() + 1}月`
                : `${date.getMonth() + 1}/${date.getDate()}`;
        });
        const values = history.map(h => h.health || 0);
        
        this.drawLineChart(ctx, rect.width, rect.height, labels, values, '#34C759');
    },

    renderCapacityChart() {
        const canvas = document.getElementById('capacity-chart');
        if (!canvas) return;
        
        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        const rect = canvas.getBoundingClientRect();
        
        canvas.width = rect.width * dpr;
        canvas.height = rect.height * dpr;
        ctx.scale(dpr, dpr);
        
        const history = HistoryManager.getLatest(30);
        if (history.length < 2) {
            this.drawNoData(ctx, rect.width, rect.height);
            return;
        }
        
        const labels = history.map(h => {
            const date = new Date(h.timestamp);
            return `${date.getMonth() + 1}/${date.getDate()}`;
        });
        const values = history.map(h => h.capacity ? Math.round(h.capacity / 1000) : 0);
        
        this.drawBarChart(ctx, rect.width, rect.height, labels, values, '#007AFF');
    },

    renderCycleChart() {
        const canvas = document.getElementById('cycle-chart');
        if (!canvas) return;
        
        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        const rect = canvas.getBoundingClientRect();
        
        canvas.width = rect.width * dpr;
        canvas.height = rect.height * dpr;
        ctx.scale(dpr, dpr);
        
        const history = HistoryManager.getLatest(30);
        if (history.length < 2 || !history[0].cycles) {
            this.drawNoData(ctx, rect.width, rect.height);
            return;
        }
        
        const labels = history.map(h => {
            const date = new Date(h.timestamp);
            return `${date.getMonth() + 1}/${date.getDate()}`;
        });
        const values = history.map(h => h.cycles || 0);
        
        this.drawLineChart(ctx, rect.width, rect.height, labels, values, '#AF52DE');
    },

    renderTempChart() {
        const canvas = document.getElementById('temp-chart');
        if (!canvas) return;
        
        const ctx = canvas.getContext('2d');
        const dpr = window.devicePixelRatio || 1;
        const rect = canvas.getBoundingClientRect();
        
        canvas.width = rect.width * dpr;
        canvas.height = rect.height * dpr;
        ctx.scale(dpr, dpr);
        
        const history = HistoryManager.getLatest(7);
        if (history.length < 2 || !history[0].temperature) {
            this.drawNoData(ctx, rect.width, rect.height);
            return;
        }
        
        const labels = history.map(h => {
            const date = new Date(h.timestamp);
            return `${date.getMonth() + 1}/${date.getDate()}`;
        });
        const values = history.map(h => h.temperature || 0);
        
        this.drawAreaChart(ctx, rect.width, rect.height, labels, values, '#FF9500');
    },

    renderPerformanceCharts() {
        // 内存使用图表
        const canvas = document.getElementById('memory-chart');
        if (canvas) {
            const ctx = canvas.getContext('2d');
            const dpr = window.devicePixelRatio || 1;
            const rect = canvas.getBoundingClientRect();
            
            canvas.width = rect.width * dpr;
            canvas.height = rect.height * dpr;
            ctx.scale(dpr, dpr);
            
            // 模拟内存使用数据
            const labels = ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00'];
            const values = labels.map(() => Math.floor(Math.random() * 30) + 50);
            
            this.drawAreaChart(ctx, rect.width, rect.height, labels, values, '#5856D6');
        }
    },

    renderChargingCharts() {
        // 充电功率图表
        const canvas = document.getElementById('charging-chart');
        if (canvas) {
            const ctx = canvas.getContext('2d');
            const dpr = window.devicePixelRatio || 1;
            const rect = canvas.getBoundingClientRect();
            
            canvas.width = rect.width * dpr;
            canvas.height = rect.height * dpr;
            ctx.scale(dpr, dpr);
            
            // 模拟实时充电数据
            const labels = [];
            const values = [];
            for (let i = 0; i < 10; i++) {
                labels.push(`${i * 5}min`);
                values.push(Math.floor(Math.random() * 20) + 10);
            }
            
            this.drawLineChart(ctx, rect.width, rect.height, labels, values, '#34C759');
        }
        
        // 充电曲线
        const curveCanvas = document.getElementById('charge-curve-chart');
        if (curveCanvas) {
            const ctx = curveCanvas.getContext('2d');
            const dpr = window.devicePixelRatio || 1;
            const rect = curveCanvas.getBoundingClientRect();
            
            curveCanvas.width = rect.width * dpr;
            curveCanvas.height = rect.height * dpr;
            ctx.scale(dpr, dpr);
            
            // 模拟充电曲线
            const labels = ['0%', '20%', '40%', '60%', '80%', '100%'];
            const values = [25, 30, 35, 32, 28, 20];
            
            this.drawLineChart(ctx, rect.width, rect.height, labels, values, '#007AFF');
        }
    },

    // 图表绘制辅助函数
    drawLineChart(ctx, width, height, labels, values, color) {
        const padding = 40;
        const chartWidth = width - padding * 2;
        const chartHeight = height - padding * 2;
        
        ctx.clearRect(0, 0, width, height);
        
        // 计算范围
        const maxValue = Math.max(...values) * 1.1;
        const minValue = Math.min(...values) * 0.9;
        const range = maxValue - minValue || 1;
        
        // 绘制网格
        ctx.strokeStyle = 'var(--separator)';
        ctx.lineWidth = 0.5;
        for (let i = 0; i <= 4; i++) {
            const y = padding + (chartHeight / 4) * i;
            ctx.beginPath();
            ctx.moveTo(padding, y);
            ctx.lineTo(width - padding, y);
            ctx.stroke();
        }
        
        // 绘制线条
        ctx.strokeStyle = color;
        ctx.lineWidth = 2;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        
        ctx.beginPath();
        values.forEach((value, index) => {
            const x = padding + (chartWidth / (values.length - 1)) * index;
            const y = padding + chartHeight - ((value - minValue) / range) * chartHeight;
            
            if (index === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        });
        ctx.stroke();
        
        // 绘制点
        ctx.fillStyle = color;
        values.forEach((value, index) => {
            const x = padding + (chartWidth / (values.length - 1)) * index;
            const y = padding + chartHeight - ((value - minValue) / range) * chartHeight;
            
            ctx.beginPath();
            ctx.arc(x, y, 4, 0, Math.PI * 2);
            ctx.fill();
        });
        
        // 绘制标签
        ctx.fillStyle = 'var(--secondary-label)';
        ctx.font = '10px -apple-system';
        ctx.textAlign = 'center';
        
        labels.forEach((label, index) => {
            const x = padding + (chartWidth / (labels.length - 1)) * index;
            ctx.fillText(label, x, height - 10);
        });
    },

    drawBarChart(ctx, width, height, labels, values, color) {
        const padding = 40;
        const chartWidth = width - padding * 2;
        const chartHeight = height - padding * 2;
        
        ctx.clearRect(0, 0, width, height);
        
        const maxValue = Math.max(...values) * 1.1 || 1;
        const barWidth = (chartWidth / values.length) * 0.6;
        const barSpacing = (chartWidth / values.length) * 0.4;
        
        // 绘制网格
        ctx.strokeStyle = 'var(--separator)';
        ctx.lineWidth = 0.5;
        for (let i = 0; i <= 4; i++) {
            const y = padding + (chartHeight / 4) * i;
            ctx.beginPath();
            ctx.moveTo(padding, y);
            ctx.lineTo(width - padding, y);
            ctx.stroke();
        }
        
        // 绘制柱状图
        ctx.fillStyle = color;
        values.forEach((value, index) => {
            const barHeight = (value / maxValue) * chartHeight;
            const x = padding + (barWidth + barSpacing) * index + barSpacing / 2;
            const y = padding + chartHeight - barHeight;
            
            ctx.fillRect(x, y, barWidth, barHeight);
        });
        
        // 绘制标签
        ctx.fillStyle = 'var(--secondary-label)';
        ctx.font = '10px -apple-system';
        ctx.textAlign = 'center';
        
        labels.forEach((label, index) => {
            const x = padding + (barWidth + barSpacing) * index + barSpacing / 2 + barWidth / 2;
            ctx.fillText(label, x, height - 10);
        });
    },

    drawAreaChart(ctx, width, height, labels, values, color) {
        const padding = 40;
        const chartWidth = width - padding * 2;
        const chartHeight = height - padding * 2;
        
        ctx.clearRect(0, 0, width, height);
        
        const maxValue = Math.max(...values) * 1.1;
        const minValue = Math.min(...values) * 0.9;
        const range = maxValue - minValue || 1;
        
        // 创建渐变
        const gradient = ctx.createLinearGradient(0, padding, 0, height - padding);
        gradient.addColorStop(0, color + '40');
        gradient.addColorStop(1, color + '00');
        
        // 绘制区域
        ctx.fillStyle = gradient;
        ctx.beginPath();
        ctx.moveTo(padding, padding + chartHeight);
        
        values.forEach((value, index) => {
            const x = padding + (chartWidth / (values.length - 1)) * index;
            const y = padding + chartHeight - ((value - minValue) / range) * chartHeight;
            ctx.lineTo(x, y);
        });
        
        ctx.lineTo(width - padding, padding + chartHeight);
        ctx.closePath();
        ctx.fill();
        
        // 绘制线条
        ctx.strokeStyle = color;
        ctx.lineWidth = 2;
        ctx.beginPath();
        values.forEach((value, index) => {
            const x = padding + (chartWidth / (values.length - 1)) * index;
            const y = padding + chartHeight - ((value - minValue) / range) * chartHeight;
            
            if (index === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        });
        ctx.stroke();
        
        // 绘制标签
        ctx.fillStyle = 'var(--secondary-label)';
        ctx.font = '10px -apple-system';
        ctx.textAlign = 'center';
        
        labels.forEach((label, index) => {
            const x = padding + (chartWidth / (labels.length - 1)) * index;
            ctx.fillText(label, x, height - 10);
        });
    },

    drawNoData(ctx, width, height) {
        ctx.clearRect(0, 0, width, height);
        ctx.fillStyle = 'var(--secondary-label)';
        ctx.font = '14px -apple-system';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText('暂无数据', width / 2, height / 2);
    },

    // ========================================
    // 实时监测方法
    // ========================================
    startRealTimeMonitoring() {
        // 清除现有的定时器
        if (this.state.monitoringInterval) {
            clearInterval(this.state.monitoringInterval);
        }
        
        // 每5秒更新一次实时数据
        this.state.monitoringInterval = setInterval(() => {
            this.updateRealTimeData();
        }, 5000);
        
        // 立即执行一次
        this.updateRealTimeData();
    },

    stopRealTimeMonitoring() {
        if (this.state.monitoringInterval) {
            clearInterval(this.state.monitoringInterval);
            this.state.monitoringInterval = null;
        }
    },

    updateRealTimeData() {
        const data = this.state.currentData.battery;
        if (!data) return;
        
        // 模拟小幅波动
        const voltageVariation = (Math.random() - 0.5) * 50;
        const currentVariation = (Math.random() - 0.5) * 100;
        
        this.state.realTimeData.voltage = (data.voltage || 4000) + voltageVariation;
        this.state.realTimeData.current = (data.current || 0) + currentVariation;
        this.state.realTimeData.power = 
            (this.state.realTimeData.voltage / 1000) * 
            (Math.abs(this.state.realTimeData.current) / 1000);
        this.state.realTimeData.isCharging = data.status === 'Charging' || data.status === 'Full';
        
        // 如果在充电页面，更新显示
        if (this.state.currentTab === 'charging') {
            this.updateChargingDisplay();
        }
        
        // 触发实时数据更新事件
        this.onRealTimeDataUpdated(this.state.realTimeData);
    },

    updateChargingDisplay() {
        const rt = this.state.realTimeData;
        
        const chargeVoltage = document.getElementById('charge-voltage');
        const chargeCurrent = document.getElementById('charge-current');
        const chargeWattage = document.getElementById('charge-wattage');
        const chargingPower = document.getElementById('charging-power');
        
        if (chargeVoltage) {
            chargeVoltage.textContent = `${(rt.voltage / 1000).toFixed(2)} V`;
        }
        if (chargeCurrent) {
            chargeCurrent.textContent = `${Math.abs(Math.round(rt.current))} mA`;
        }
        if (chargeWattage) {
            chargeWattage.textContent = `${rt.power.toFixed(1)} W`;
        }
        if (chargingPower) {
            chargingPower.textContent = `${rt.power.toFixed(1)} W`;
        }
    },

    onRealTimeDataUpdated(data) {
        // 可以在这里添加回调或事件派发
        if (window.BatteryHealthApp && window.BatteryHealthApp.onRealTimeUpdate) {
            window.BatteryHealthApp.onRealTimeUpdate(JSON.stringify(data));
        }
    },

    // ========================================
    // 导出和分享方法
    // ========================================
    exportReport() {
        const report = this.generateReport('导出报告', HistoryManager.getLatest(30), 30);
        
        // 导出为JSON文件
        const reportData = {
            ...report,
            exportedAt: new Date().toISOString(),
            version: '1.0.0'
        };
        
        const blob = new Blob([JSON.stringify(reportData, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        
        const a = document.createElement('a');
        a.href = url;
        a.download = `battery-report-${new Date().toISOString().split('T')[0]}.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        
        URL.revokeObjectURL(url);
        this.showToast('报告已导出', 'success');
        
        // 调用Android接口（如果可用）
        if (window.BatteryHealthApp && window.BatteryHealthApp.exportReport) {
            window.BatteryHealthApp.exportReport(JSON.stringify(reportData));
        }
        
        return reportData;
    },

    shareReport() {
        const data = this.state.currentData.battery;
        const device = this.state.currentData.device;
        
        const healthData = this.analyzeBatteryHealth();
        
        const shareText = `📱 设备: ${device.brand} ${device.model}
🔋 电池健康度: ${healthData.healthPercentage}% (${healthData.grade}级)
🔄 循环次数: ${healthData.cycles || '--'}次
⚡ 当前容量: ${healthData.capacity ? Math.round(healthData.capacity / 1000) + 'mAh' : '--'}
🌡️ 温度: ${data.temperature ? data.temperature.toFixed(1) + '°C' : '--'}
📊 由 Battery Health App 生成`;
        
        // 使用Web Share API
        if (navigator.share) {
            navigator.share({
                title: '电池健康报告',
                text: shareText
            }).then(() => {
                this.showToast('分享成功', 'success');
            }).catch((err) => {
                console.log('Share cancelled', err);
            });
        } else {
            // 复制到剪贴板
            navigator.clipboard.writeText(shareText).then(() => {
                this.showToast('报告已复制到剪贴板', 'success');
            }).catch(() => {
                this.showToast('复制失败', 'error');
            });
        }
        
        // 调用Android接口（如果可用）
        if (window.BatteryHealthApp && window.BatteryHealthApp.shareReport) {
            window.BatteryHealthApp.shareReport(shareText);
        }
    },

    // ========================================
    // 标签切换
    // ========================================
    switchTab(tabName) {
        this.state.currentTab = tabName;
        
        // 更新按钮状态
        this.elements.tabBtns.forEach(btn => {
            btn.classList.toggle('active', btn.dataset.tab === tabName);
        });
        
        // 更新内容
        this.elements.tabContents.forEach(content => {
            content.classList.toggle('active', content.id === `tab-${tabName}`);
        });
        
        // 更新标题
        const titles = {
            'overview': '概览',
            'health': '健康',
            'performance': '性能',
            'device': '设备',
            'charging': '充电'
        };
        if (this.elements.pageTitle) {
            this.elements.pageTitle.textContent = titles[tabName] || '概览';
        }
        
        // 触发标签页渲染
        if (tabName === 'health') {
            this.renderHealthCharts();
        } else if (tabName === 'performance') {
            this.renderPerformanceCharts();
        } else if (tabName === 'charging') {
            this.renderChargingCharts();
        }
    },

    // ========================================
    // 文件处理
    // ========================================
    openFileModal() {
        if (this.elements.fileModal) {
            this.elements.fileModal.classList.add('active');
        }
        if (this.elements.fileProgress) {
            this.elements.fileProgress.classList.remove('active');
        }
        this.updateProgress(0);
    },

    closeFileModal() {
        if (this.elements.fileModal) {
            this.elements.fileModal.classList.remove('active');
        }
    },

    closeReportModal() {
        if (this.elements.reportModal) {
            this.elements.reportModal.classList.remove('active');
        }
    },

    async handleFile(file) {
        if (!file.name.endsWith('.zip')) {
            this.showToast('请选择ZIP文件', 'error');
            return;
        }
        
        if (this.elements.fileProgress) {
            this.elements.fileProgress.classList.add('active');
        }
        this.showLoading('正在解析文件...');
        
        try {
            const zip = await JSZip.loadAsync(file);
            
            // 更新进度
            this.updateProgress(30);
            
            // 查找关键文件
            let bugreportContent = '';
            let batteryLogContent = '';
            let deviceInfoContent = '';
            
            for (const [filename, zipEntry] of Object.entries(zip.files)) {
                if (filename.includes('bugreport') && filename.endsWith('.txt')) {
                    bugreportContent = await zipEntry.async('text');
                } else if (filename.includes('battery') && filename.endsWith('.log')) {
                    batteryLogContent = await zipEntry.async('text');
                } else if (filename === 'system/build.prop') {
                    deviceInfoContent = await zipEntry.async('text');
                }
            }
            
            this.updateProgress(60);
            
            // 解析数据
            const batteryData = BatteryParsers.parseBugreport(bugreportContent);
            const deviceInfo = BatteryParsers.parseDeviceInfo(
                deviceInfoContent || bugreportContent
            );
            
            this.updateProgress(80);
            
            // 保存数据
            this.state.currentData.battery = {
                ...this.state.currentData.battery,
                ...batteryData,
                capacity: batteryData.fullChargeCapacity || batteryData.designCapacity || 4000
            };
            this.state.deviceInfo = deviceInfo;
            
            // 运行分析
            this.analyzeBatteryHealth();
            this.parseDeviceConfig();
            this.analyzePerformance();
            this.checkActivationDate();
            this.checkBatteryOrigin();
            
            this.updateProgress(100);
            
            // 更新UI
            this.updateAllDisplays();
            
            this.closeFileModal();
            this.hideLoading();
            this.showToast('分析完成', 'success');
            
        } catch (error) {
            console.error('File parsing error:', error);
            this.hideLoading();
            this.showToast('文件解析失败', 'error');
        }
    },

    handleAndroidFile(path) {
        this.showLoading('正在读取文件...');
        
        if (window.BatteryHealthApp && window.BatteryHealthApp.readFile) {
            window.BatteryHealthApp.readFile(path);
        }
    },

    updateProgress(percent) {
        if (this.elements.progressFill) {
            this.elements.progressFill.style.width = `${percent}%`;
        }
        if (this.elements.progressText) {
            this.elements.progressText.textContent = `${percent}%`;
        }
    },

    // ========================================
    // 数据计算辅助方法
    // ========================================
    calculateHealthPercentage(batteryData) {
        if (batteryData.fullChargeCapacity && batteryData.designCapacity) {
            return Math.round((batteryData.fullChargeCapacity / batteryData.designCapacity) * 100);
        }
        
        if (batteryData.cycles > 0) {
            const estimatedHealth = Math.max(0, 100 - (batteryData.cycles * 0.05));
            return Math.round(estimatedHealth);
        }
        
        return 100;
    },

    getHealthGrade(percentage) {
        if (percentage >= 95) return { grade: 'A+', class: 'grade-a' };
        if (percentage >= 90) return { grade: 'A', class: 'grade-a' };
        if (percentage >= 85) return { grade: 'A-', class: 'grade-a' };
        if (percentage >= 80) return { grade: 'B+', class: 'grade-b' };
        if (percentage >= 75) return { grade: 'B', class: 'grade-b' };
        if (percentage >= 70) return { grade: 'B-', class: 'grade-b' };
        if (percentage >= 65) return { grade: 'C+', class: 'grade-c' };
        if (percentage >= 60) return { grade: 'C', class: 'grade-c' };
        if (percentage >= 55) return { grade: 'C-', class: 'grade-c' };
        if (percentage >= 50) return { grade: 'D+', class: 'grade-d' };
        if (percentage >= 45) return { grade: 'D', class: 'grade-d' };
        return { grade: 'E', class: 'grade-e' };
    },

    getBatterySpecs(deviceInfo) {
        const brand = deviceInfo?.brand;
        const model = deviceInfo?.model;
        
        if (brand && BatteryDatabase[brand]) {
            if (model && BatteryDatabase[brand][model]) {
                return BatteryDatabase[brand][model];
            }
        }
        
        return BatteryDatabase.default;
    },

    estimateRemainingLife(healthPercentage) {
        if (healthPercentage >= 90) {
            return '18+ 个月';
        } else if (healthPercentage >= 80) {
            return '12-18 个月';
        } else if (healthPercentage >= 70) {
            return '6-12 个月';
        } else if (healthPercentage >= 60) {
            return '3-6 个月';
        } else {
            return '建议尽快更换';
        }
    },

    // ========================================
    // 更新显示方法
    // ========================================
    updateAllDisplays() {
        this.updateOverview();
        this.updateHealthDetails();
        this.updateDeviceInfo();
        this.updatePerformance();
        this.updateChargingInfo();
    },

    updateOverview() {
        const data = this.state.currentData.battery;
        const healthData = this.analyzeBatteryHealth();
        
        // 健康环
        if (this.elements.healthRing && this.elements.healthPercentage) {
            const circumference = 2 * Math.PI * 85;
            const offset = circumference - (healthData.healthPercentage / 100) * circumference;
            this.elements.healthRing.style.strokeDashoffset = offset;
            this.elements.healthPercentage.textContent = `${healthData.healthPercentage}%`;
        }
        
        // 等级
        if (this.elements.healthGrade) {
            this.elements.healthGrade.textContent = healthData.grade;
            this.elements.healthGrade.className = `grade-badge ${healthData.gradeClass}`;
        }
        
        // 快速统计
        if (this.elements.currentCapacity) {
            this.elements.currentCapacity.textContent = healthData.capacity 
                ? `${Math.round(healthData.capacity / 1000)}mAh` 
                : '--';
        }
        if (this.elements.cycleCount) {
            this.elements.cycleCount.textContent = healthData.cycles || '--';
        }
        if (this.elements.temperature) {
            this.elements.temperature.textContent = healthData.temperature 
                ? `${healthData.temperature.toFixed(1)}°C` 
                : '--';
        }
        if (this.elements.voltage) {
            this.elements.voltage.textContent = healthData.voltage 
                ? `${(healthData.voltage / 1000).toFixed(2)}V` 
                : '--';
        }
        
        // 设备信息
        if (this.state.currentData.device) {
            if (this.elements.deviceModel) {
                this.elements.deviceModel.textContent = this.state.currentData.device.model || '--';
            }
            if (this.elements.deviceBrand) {
                this.elements.deviceBrand.textContent = this.state.currentData.device.brand || '--';
            }
            if (this.elements.deviceAndroid) {
                this.elements.deviceAndroid.textContent = this.state.currentData.device.androidVersion || '--';
            }
        }
        
        if (this.elements.batteryStatusText) {
            this.elements.batteryStatusText.textContent = data.status || '--';
        }
        
        // 维护建议
        if (this.elements.recommendationsList && healthData.recommendations) {
            this.elements.recommendationsList.innerHTML = healthData.recommendations.slice(0, 3).map(r => `
                <div class="recommendation-item">
                    <span class="rec-icon">${r.icon}</span>
                    <span class="rec-text">${r.text}</span>
                </div>
            `).join('');
        }
    },

    updateHealthDetails() {
        const data = this.state.currentData.battery;
        const healthData = this.analyzeBatteryHealth();
        
        // 状态徽章
        const badge = document.getElementById('health-status-badge');
        if (badge) {
            if (healthData.healthPercentage >= 80) {
                badge.textContent = '良好';
                badge.className = 'card-badge';
            } else if (healthData.healthPercentage >= 60) {
                badge.textContent = '一般';
                badge.className = 'card-badge warning';
            } else {
                badge.textContent = '需更换';
                badge.className = 'card-badge danger';
            }
        }
        
        // 详细信息
        const designCapacity = document.getElementById('design-capacity');
        const actualCapacity = document.getElementById('actual-capacity');
        const capacityLoss = document.getElementById('capacity-loss');
        const batteryAge = document.getElementById('battery-age');
        const estimatedLife = document.getElementById('estimated-life');
        
        if (designCapacity) {
            designCapacity.textContent = healthData.designCapacity 
                ? `${Math.round(healthData.designCapacity / 1000)} mAh` 
                : '-- mAh';
        }
        if (actualCapacity) {
            actualCapacity.textContent = healthData.capacity 
                ? `${Math.round(healthData.capacity / 1000)} mAh` 
                : '-- mAh';
        }
        if (capacityLoss) {
            capacityLoss.textContent = `${healthData.capacityLoss}%`;
        }
        if (batteryAge) {
            batteryAge.textContent = healthData.cycles 
                ? `${Math.round(healthData.cycles / 30)} 个月` 
                : '-- 个月';
        }
        if (estimatedLife) {
            estimatedLife.textContent = healthData.estimatedLife;
        }
        
        // 等级状态
        document.querySelectorAll('.grade-item').forEach(item => {
            item.classList.remove('active');
        });
        
        const gradeItem = document.querySelector(`.grade-item[data-grade="${healthData.grade}"]`);
        if (gradeItem) {
            gradeItem.classList.add('active');
        }
        
        // 趋势统计
        const history = HistoryManager.getLatest(30);
        const avgDegradation = document.getElementById('avg-degradation');
        const replaceEstimate = document.getElementById('replace-estimate');
        
        if (history.length >= 2 && avgDegradation && replaceEstimate) {
            const first = history[0];
            const last = history[history.length - 1];
            const degradation = ((first.health - last.health) / history.length).toFixed(2);
            avgDegradation.textContent = `${degradation}%/月`;
            
            const monthsToReplace = Math.max(0, Math.floor((healthData.healthPercentage - 60) / Math.abs(parseFloat(degradation))));
            replaceEstimate.textContent = monthsToReplace > 0 ? `${monthsToReplace} 个月后` : '建议立即更换';
        }
    },

    updateDeviceInfo() {
        const info = this.state.currentData.device;
        if (!info) return;
        
        // 基本信息
        const configModel = document.getElementById('config-model');
        const configBrand = document.getElementById('config-brand');
        const configCodename = document.getElementById('config-codename');
        
        if (configModel) configModel.textContent = info.model || '--';
        if (configBrand) configBrand.textContent = info.brand || '--';
        if (configCodename) configCodename.textContent = info.device || '--';
        
        // 系统信息
        const configAndroid = document.getElementById('config-android');
        const configSecurity = document.getElementById('config-security');
        const configKernel = document.getElementById('config-kernel');
        
        if (configAndroid) configAndroid.textContent = info.androidVersion || '--';
        if (configSecurity) configSecurity.textContent = info.securityPatch || '--';
        if (configKernel) {
            configKernel.textContent = info.kernelVersion 
                ? info.kernelVersion.substring(0, 30) + '...' 
                : '--';
        }
        
        // 电池规格
        const specs = this.getBatterySpecs(this.state.deviceInfo);
        const specNominal = document.getElementById('spec-nominal');
        const specTypical = document.getElementById('spec-typical');
        const specVoltage = document.getElementById('spec-voltage');
        
        if (specs) {
            if (specNominal) specNominal.textContent = `${specs.nominal} mAh`;
            if (specTypical) specTypical.textContent = `${specs.typical} mAh`;
            if (specVoltage) specVoltage.textContent = `${specs.voltage} V`;
        }
        
        // 激活信息
        this.updateActivationInfo();
        
        // 电池溯源
        this.updateBatteryOrigin();
        
        // 大数据分析
        this.updateBigDataInsights();
    },

    updateActivationInfo() {
        const activation = this.checkActivationDate();
        
        const manufactureDate = document.getElementById('manufacture-date');
        const activationDate = document.getElementById('activation-date');
        const warrantyStatus = document.getElementById('warranty-status');
        const warrantyEnd = document.getElementById('warranty-end');
        const serviceEligible = document.getElementById('service-eligible');
        
        if (manufactureDate) {
            manufactureDate.textContent = activation.manufactureDate 
                ? activation.manufactureDate.toLocaleDateString('zh-CN') 
                : '--';
        }
        if (activationDate) {
            activationDate.textContent = activation.activationDate 
                ? activation.activationDate.toLocaleDateString('zh-CN') 
                : '--';
        }
        if (warrantyStatus) {
            warrantyStatus.textContent = activation.warrantyStatus === 'active' ? '在保' : 
                                        activation.warrantyStatus === 'expiring' ? '即将过期' : '已过保';
            warrantyStatus.className = `activation-value ${activation.warrantyStatus}`;
        }
        if (warrantyEnd) {
            warrantyEnd.textContent = activation.warrantyEndDate 
                ? activation.warrantyEndDate.toLocaleDateString('zh-CN') 
                : '--';
        }
        if (serviceEligible) {
            serviceEligible.textContent = activation.serviceEligibility.canService 
                ? (activation.serviceEligibility.type === 'warranty' ? '保修服务' : '付费服务')
                : '不符合';
            serviceEligible.className = `activation-value ${activation.serviceEligibility.canService ? 'active' : 'expired'}`;
        }
    },

    updateBatteryOrigin() {
        const origin = this.checkBatteryOrigin();
        
        const originStatus = document.getElementById('origin-status');
        const batteryType = document.getElementById('battery-type');
        const batteryManufacturer = document.getElementById('battery-manufacturer');
        const batteryProduction = document.getElementById('battery-production');
        const batteryAuthenticity = document.getElementById('battery-authenticity');
        
        if (originStatus) {
            originStatus.innerHTML = `
                <div class="origin-icon">${origin.isOriginal ? '✅' : '⚠️'}</div>
                <div class="origin-text">${origin.isOriginal ? '原装电池' : '疑似更换电池'}</div>
            `;
        }
        if (batteryType) batteryType.textContent = '锂离子聚合物';
        if (batteryManufacturer) {
            batteryManufacturer.textContent = origin.manufacturer ? origin.manufacturer.name : 'Unknown';
        }
        if (batteryProduction) {
            batteryProduction.textContent = origin.productionDate 
                ? origin.productionDate.toLocaleDateString('zh-CN') 
                : '--';
        }
        if (batteryAuthenticity) {
            batteryAuthenticity.textContent = `${origin.authenticityScore}% (${origin.confidence})`;
            batteryAuthenticity.className = `origin-value ${origin.authenticityScore >= 80 ? 'authentic' : 'unknown'}`;
        }
    },

    updateBigDataInsights() {
        const data = this.state.currentData.battery;
        if (!data) return;
        
        const healthPercentage = data.health || 100;
        
        // 模拟大数据对比
        const avgHealth = Math.floor(Math.random() * 15) + 80;
        const avgCycles = Math.floor(Math.random() * 200) + 200;
        
        let ranking;
        if (healthPercentage >= 90) {
            ranking = '前 10%';
        } else if (healthPercentage >= 80) {
            ranking = '前 30%';
        } else if (healthPercentage >= 70) {
            ranking = '前 50%';
        } else {
            ranking = '后 50%';
        }
        
        const avgHealthEl = document.getElementById('avg-health-same-model');
        const avgCyclesEl = document.getElementById('avg-cycles-same-model');
        const rankingEl = document.getElementById('health-ranking');
        
        if (avgHealthEl) avgHealthEl.textContent = `${avgHealth}%`;
        if (avgCyclesEl) avgCyclesEl.textContent = `${avgCycles} 次`;
        if (rankingEl) rankingEl.textContent = ranking;
    },

    updatePerformance() {
        const performance = this.analyzePerformance();
        
        // 更新仪表盘
        this.updateGauge('cpu-gauge', performance.cpuUsage);
        this.updateGauge('memory-gauge', performance.memoryUsage);
        this.updateGauge('storage-gauge', this.getStorageUsage());
        
        // 更新数值显示
        const cpuUsage = document.getElementById('cpu-usage');
        const memoryUsage = document.getElementById('memory-usage');
        const storageUsage = document.getElementById('storage-usage');
        
        if (cpuUsage) cpuUsage.textContent = `${performance.cpuUsage}%`;
        if (memoryUsage) memoryUsage.textContent = `${performance.memoryUsage}%`;
        if (storageUsage) storageUsage.textContent = `${this.getStorageUsage()}%`;
        
        // 更新应用卡顿列表
        this.updateAppLagList(performance.lagEvents);
        
        // 更新性能瓶颈
        this.updateBottleneckList(performance.bottlenecks);
        
        // 更新内存详情
        const totalMemory = document.getElementById('total-memory');
        const availableMemory = document.getElementById('available-memory');
        const appMemory = document.getElementById('app-memory');
        
        if (totalMemory) totalMemory.textContent = `${this.state.currentData.device.ram} GB`;
        if (availableMemory) {
            const available = Math.round(this.state.currentData.device.ram * (1 - performance.memoryUsage / 100));
            availableMemory.textContent = `${available} GB`;
        }
        if (appMemory) {
            appMemory.textContent = `${Math.floor(Math.random() * 500) + 200} MB`;
        }
        
        // 更新续航分析
        this.updateBatteryLifeInfo();
        
        // 更新耗电排行
        this.updatePowerUsageList();
        
        // 更新优化建议
        this.updateOptimizationList(performance.optimizations);
    },

    updateAppLagList(lagEvents) {
        const list = document.getElementById('app-lag-list');
        if (!list || !lagEvents) return;
        
        list.innerHTML = lagEvents.slice(0, 5).map(app => `
            <div class="lag-item">
                <div class="lag-app-icon">${app.icon}</div>
                <div class="lag-app-info">
                    <span class="lag-app-name">${app.name}</span>
                    <span class="lag-app-detail">${app.lastLagTime}</span>
                </div>
                <span class="lag-count">${app.lagCount}次</span>
            </div>
        `).join('');
    },

    updateBottleneckList(bottlenecks) {
        const list = document.getElementById('bottleneck-list');
        if (!list || !bottlenecks) return;
        
        list.innerHTML = bottlenecks.map(b => `
            <div class="bottleneck-item">
                <div class="bottleneck-icon">⚡</div>
                <div class="bottleneck-info">
                    <span class="bottleneck-name">${b.name}</span>
                    <span class="bottleneck-desc">${b.description}</span>
                </div>
                <span class="bottleneck-severity ${b.severity}">${b.severity === 'high' ? '高' : b.severity === 'medium' ? '中' : '低'}</span>
            </div>
        `).join('');
    },

    updateBatteryLifeInfo() {
        const batteryLife = this.analyzeBatteryLife();
        
        const screenOnTime = document.getElementById('screen-on-time');
        const standbyTime = document.getElementById('standby-time');
        
        if (screenOnTime) screenOnTime.textContent = `${batteryLife.screenOnTime.hours}h`;
        if (standbyTime) standbyTime.textContent = `${batteryLife.standbyTime.hours}h`;
    },

    updatePowerUsageList() {
        const batteryLife = this.analyzeBatteryLife();
        const list = document.getElementById('power-usage-list');
        if (!list || !batteryLife.powerRanking) return;
        
        list.innerHTML = batteryLife.powerRanking.slice(0, 5).map((app, index) => `
            <div class="power-item">
                <div class="power-rank ${index < 2 ? 'top' : ''}">${index + 1}</div>
                <div class="power-app-icon">${app.name[0]}</div>
                <div class="power-app-info">
                    <span class="power-app-name">${app.name}</span>
                    <span class="power-app-time">${app.usageTime}</span>
                </div>
                <span class="power-percentage">${app.consumption}%</span>
            </div>
        `).join('');
    },

    updateOptimizationList(optimizations) {
        const list = document.getElementById('optimization-list');
        if (!list || !optimizations) return;
        
        list.innerHTML = optimizations.map(s => `
            <div class="opt-item">
                <div class="opt-icon">${s.icon}</div>
                <div class="opt-content">
                    <span class="opt-title">${s.title}</span>
                    <span class="opt-desc">${s.description}</span>
                </div>
            </div>
        `).join('');
    },

    updateChargingInfo() {
        const charging = this.monitorChargingPower();
        const data = this.state.currentData.battery;
        
        // 充电状态
        const chargingStatus = document.getElementById('charging-status');
        if (chargingStatus) {
            chargingStatus.textContent = charging.status;
        }
        
        // 充电功率
        const chargingPower = document.getElementById('charging-power');
        const chargeWattage = document.getElementById('charge-wattage');
        
        if (chargingPower) chargingPower.textContent = `${charging.power} W`;
        if (chargeWattage) chargeWattage.textContent = `${charging.power} W`;
        
        // 电压电流
        const chargeVoltage = document.getElementById('charge-voltage');
        const chargeCurrent = document.getElementById('charge-current');
        
        if (chargeVoltage) chargeVoltage.textContent = `${charging.voltage.toFixed(2)} V`;
        if (chargeCurrent) chargeCurrent.textContent = `${Math.abs(charging.current)} mA`;
        
        // 充电速度条
        this.updateChargingSpeedBar(charging.chargingSpeed);
        
        // 充电预估
        this.updateChargeEstimates(charging.timeEstimates);
        
        // 充电器信息
        this.updateChargerInfo(charging.chargerInfo);
    },

    updateChargingSpeedBar(chargingSpeed) {
        const speedBar = document.getElementById('speed-bar');
        const speedStatus = document.getElementById('speed-status');
        
        if (speedBar) {
            speedBar.style.width = `${chargingSpeed.percentage}%`;
        }
        if (speedStatus) {
            speedStatus.textContent = `${chargingSpeed.icon} ${chargingSpeed.description}`;
        }
    },

    updateChargeEstimates(timeEstimates) {
        const timeTo50 = document.getElementById('time-to-50');
        const timeTo80 = document.getElementById('time-to-80');
        const timeTo100 = document.getElementById('time-to-100');
        
        if (timeTo50) timeTo50.textContent = timeEstimates.to50;
        if (timeTo80) timeTo80.textContent = timeEstimates.to80;
        if (timeTo100) timeTo100.textContent = timeEstimates.to100;
    },

    updateChargerInfo(chargerInfo) {
        const chargerType = document.getElementById('charger-type');
        const chargerMax = document.getElementById('charger-max');
        const chargerProtocol = document.getElementById('charger-protocol');
        
        if (chargerType) chargerType.textContent = chargerInfo.type;
        if (chargerMax) chargerMax.textContent = chargerInfo.maxOutput;
        if (chargerProtocol) chargerProtocol.textContent = chargerInfo.protocol;
    },

    updateGauge(gaugeId, percentage) {
        const gauge = document.getElementById(gaugeId);
        if (!gauge) return;
        
        const fill = gauge.querySelector('.gauge-fill');
        if (!fill) return;
        
        const circumference = 2 * Math.PI * 40;
        const offset = circumference - (percentage / 100) * circumference;
        
        fill.style.strokeDashoffset = offset;
        
        // 根据数值改变颜色
        if (percentage > 80) {
            fill.style.stroke = 'var(--ios-red)';
        } else if (percentage > 60) {
            fill.style.stroke = 'var(--ios-orange)';
        } else {
            fill.style.stroke = 'var(--ios-blue)';
        }
    },

    // ========================================
    // 工具函数
    // ========================================
    updateTime() {
        const now = new Date();
        const hours = String(now.getHours()).padStart(2, '0');
        const minutes = String(now.getMinutes()).padStart(2, '0');
        const timeEl = document.getElementById('status-time');
        if (timeEl) {
            timeEl.textContent = `${hours}:${minutes}`;
        }
    },

    showLoading(text = '加载中...') {
        const loadingText = document.getElementById('loading-text');
        if (loadingText) loadingText.textContent = text;
        if (this.elements.loadingOverlay) {
            this.elements.loadingOverlay.classList.add('active');
        }
    },

    hideLoading() {
        if (this.elements.loadingOverlay) {
            this.elements.loadingOverlay.classList.remove('active');
        }
    },

    showToast(message, type = 'success') {
        const toast = this.elements.toast;
        const icon = document.getElementById('toast-icon');
        const msg = document.getElementById('toast-message');
        
        if (!toast || !icon || !msg) return;
        
        icon.textContent = type === 'success' ? '✓' : type === 'error' ? '✕' : 'ℹ';
        msg.textContent = message;
        
        toast.classList.add('active');
        
        setTimeout(() => {
            toast.classList.remove('active');
        }, 2000);
    },

    loadInitialData() {
        // 尝试从本地存储加载上次的数据
        const saved = localStorage.getItem('battery_last_analysis');
        if (saved) {
            try {
                const data = JSON.parse(saved);
                if (data.battery) {
                    this.state.currentData.battery = { ...this.state.currentData.battery, ...data.battery };
                }
                if (data.device) {
                    this.state.currentData.device = { ...this.state.currentData.device, ...data.device };
                }
                this.state.deviceInfo = data.deviceInfo || data.device;
                this.updateAllDisplays();
            } catch (e) {
                console.error('Failed to load saved data:', e);
            }
        }
    },

    saveCurrentData() {
        const dataToSave = {
            battery: this.state.currentData.battery,
            device: this.state.currentData.device,
            deviceInfo: this.state.deviceInfo,
            savedAt: new Date().toISOString()
        };
        localStorage.setItem('battery_last_analysis', JSON.stringify(dataToSave));
    }
};

// ========================================
// 初始化应用
// ========================================
document.addEventListener('DOMContentLoaded', () => {
    BatteryHealthApp.init();
});

// 暴露到全局（供Android调用）
window.BatteryHealthApp = BatteryHealthApp;
