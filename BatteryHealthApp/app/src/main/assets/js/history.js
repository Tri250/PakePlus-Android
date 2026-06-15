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
            return data ? JSON.parse(data) : [];
        } catch (e) {
            console.error('读取历史记录失败:', e);
            return [];
        }
    },

    /**
     * 添加历史记录
     * @param {Object} record - 记录对象
     */
    add(record) {
        try {
            const history = this.getAll();
            
            // 创建新记录
            const newRecord = {
                id: Date.now(),
                timestamp: new Date().toISOString(),
                brand: record.brand || 'unknown',
                initialCapacity: record.initialCapacity,
                currentCapacity: record.currentCapacity,
                healthPercentage: record.healthPercentage,
                cycleCount: record.cycleCount,
                batteryTemp: record.batteryTemp,
                note: record.note || ''
            };

            // 添加到开头
            history.unshift(newRecord);

            // 限制数量
            if (history.length > this.MAX_HISTORY_ITEMS) {
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
     * @param {number} id - 记录ID
     */
    delete(id) {
        try {
            const history = this.getAll();
            const filtered = history.filter(item => item.id !== id);
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
                date: new Date(item.timestamp).toLocaleDateString('zh-CN'),
                health: parseFloat(item.healthPercentage)
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
            if (diff < 3600000 && diff >= 0) {
                const minutes = Math.floor(diff / 60000);
                return minutes < 1 ? '刚刚' : `${minutes}分钟前`;
            }
            
            // 小于24小时
            if (diff < 86400000 && diff >= 0) {
                const hours = Math.floor(diff / 3600000);
                return `${hours}小时前`;
            }
            
            // 小于7天
            if (diff < 604800000 && diff >= 0) {
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
            console.warn('formatDate error:', e);
            return '未知时间';
        }
    },

    /**
     * 获取健康度颜色
     * @param {number} health - 健康度百分比
     * @returns {string} - 颜色类名
     */
    getHealthColor(health) {
        if (health >= 85) return 'status-good';
        if (health >= 70) return 'status-normal';
        if (health >= 60) return 'status-warning';
        return 'status-danger';
    }
};

// 导出模块
if (typeof module !== 'undefined' && module.exports) {
    module.exports = HistoryManager;
}
