/**
 * 历史记录管理模块
 * 使用 localStorage 保存分析历史
 */

const HistoryManager = {
    STORAGE_KEY: 'battery_health_history',
    MAX_HISTORY_ITEMS: 20,

    /**
     * 获取所有历史记录
     * @returns {Array} - 历史记录数组
     */
    getAll() {
        try {
            const data = localStorage.getItem(this.STORAGE_KEY);
            if (!data) return [];
            const parsed = JSON.parse(data);
            // 数据完整性验证
            if (!Array.isArray(parsed)) {
                console.warn('History data is not an array, resetting');
                return [];
            }
            return parsed;
        } catch (e) {
            console.error('读取历史记录失败:', e);
            // 数据损坏时返回空数组
            return [];
        }
    },

    /**
     * 生成唯一 ID（防止碰撞）
     * @returns {string} - 唯一 ID
     */
    generateUniqueId() {
        // 使用时间戳 + 随机数 + 计数器确保唯一性
        const timestamp = Date.now();
        const random = Math.floor(Math.random() * 10000);
        const counter = (this._idCounter || 0) + 1;
        this._idCounter = counter;
        return `${timestamp}-${random}-${counter}`;
    },

    /**
     * 验证并净化输入数据
     * @param {Object} record - 记录对象
     * @returns {Object|null} - 净化后的记录或 null（如果无效）
     */
    sanitizeRecord(record) {
        if (!record || typeof record !== 'object') {
            return null;
        }
        
        // 净化字符串字段
        const sanitized = {
            brand: this.sanitizeString(record.brand) || 'unknown',
            initialCapacity: this.validateNumber(record.initialCapacity, 500, 30000) || 0,
            currentCapacity: this.validateNumber(record.currentCapacity, 0, 30000) || 0,
            healthPercentage: this.validateNumber(record.healthPercentage, 0, 100) || '0',
            cycleCount: this.validateNumber(record.cycleCount, 0, 10000) || 0,
            batteryTemp: this.validateNumber(record.batteryTemp, -30, 80) || 0,
            note: this.sanitizeString(record.note) || ''
        };
        
        // 验证设计容量
        if (record.designCapacity) {
            sanitized.designCapacity = this.validateNumber(record.designCapacity, 500, 30000) || 0;
        }
        
        return sanitized;
    },

    /**
     * 净化字符串（防止 XSS）
     * @param {string} str - 输入字符串
     * @returns {string} - 净化后的字符串
     */
    sanitizeString(str) {
        if (!str || typeof str !== 'string') return '';
        // 移除危险字符
        return str.replace(/[<>'"&]/g, '').substring(0, 100);
    },

    /**
     * 验证数字范围
     * @param {any} value - 输入值
     * @param {number} min - 最小值
     * @param {number} max - 最大值
     * @returns {number|null} - 有效数字或 null
     */
    validateNumber(value, min, max) {
        const num = parseFloat(value);
        if (isNaN(num)) return null;
        if (num < min || num > max) return null;
        return num;
    },

    /**
     * 添加历史记录
     * @param {Object} record - 记录对象
     */
    add(record) {
        try {
            // 验证并净化输入
            const sanitized = this.sanitizeRecord(record);
            if (!sanitized) {
                console.warn('Invalid record, skipping');
                return null;
            }
            
            const history = this.getAll();
            
            // 创建新记录
            const newRecord = {
                id: this.generateUniqueId(),
                timestamp: new Date().toISOString(),
                ...sanitized
            };

            // 添加到开头
            history.unshift(newRecord);

            // 限制数量
            while (history.length > this.MAX_HISTORY_ITEMS) {
                history.pop();
            }

            localStorage.setItem(this.STORAGE_KEY, JSON.stringify(history));
            return newRecord;
        } catch (e) {
            console.error('保存历史记录失败:', e);
            return null;
        }
    },

    /**
     * 删除单条记录
     * @param {string} id - 记录ID
     */
    delete(id) {
        try {
            // 验证 ID
            if (!id || typeof id !== 'string') {
                console.warn('Invalid ID for deletion');
                return false;
            }
            
            const history = this.getAll();
            const filtered = history.filter(item => item.id !== id);
            
            // 验证删除是否成功
            if (filtered.length === history.length) {
                console.warn('No record found with ID:', id);
                return false;
            }
            
            localStorage.setItem(this.STORAGE_KEY, JSON.stringify(filtered));
            return true;
        } catch (e) {
            console.error('删除历史记录失败:', e);
            return false;
        }
    },

    /**
     * 清空所有历史记录
     */
    clear() {
        try {
            localStorage.removeItem(this.STORAGE_KEY);
            return true;
        } catch (e) {
            console.error('清空历史记录失败:', e);
            return false;
        }
    },

    /**
     * 获取健康度趋势数据
     * @returns {Array} - 趋势数据
     */
    getTrend() {
        const history = this.getAll();
        return history
            .filter(item => item.healthPercentage && !isNaN(parseFloat(item.healthPercentage)))
            .map(item => ({
                date: new Date(item.timestamp || new Date().toISOString()).toLocaleDateString('zh-CN'),
                health: parseFloat(item.healthPercentage || 0)
            }))
            .reverse();
    },

    /**
     * 格式化日期显示
     * @param {string} timestamp - ISO时间戳
     * @returns {string} - 格式化后的日期
     */
    formatDate(timestamp) {
        try {
            const date = new Date(timestamp);
            if (isNaN(date.getTime())) {
                return '未知时间';
            }
            
            const now = new Date();
            const diff = now - date;
            
            // 小于1小时
            if (diff < 3600000) {
                const minutes = Math.floor(diff / 60000);
                return minutes < 1 ? '刚刚' : `${minutes}分钟前`;
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
            
            return date.toLocaleDateString('zh-CN', {
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch (e) {
            console.warn('Invalid timestamp:', timestamp);
            return '未知时间';
        }
    },

    /**
     * 获取健康度颜色
     * @param {number|string} health - 健康度百分比
     * @returns {string} - 颜色类名
     */
    getHealthColor(health) {
        const healthNum = parseFloat(health);
        if (isNaN(healthNum)) return 'status-normal';
        if (healthNum >= 85) return 'status-good';
        if (healthNum >= 70) return 'status-normal';
        if (healthNum >= 60) return 'status-warning';
        return 'status-danger';
    }
};

// 导出模块
if (typeof module !== 'undefined' && module.exports) {
    module.exports = HistoryManager;
}
