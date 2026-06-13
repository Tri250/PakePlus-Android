/**
 * 电池信息解析器模块
 * 支持多品牌诊断文件解析
 */

const BatteryParsers = {
    /**
     * 检测文件品牌类型
     * @param {Array} entries - ZIP文件条目
     * @param {string} content - 文件内容
     * @returns {string} - 品牌名称
     */
    detectBrand(entries, content) {
        const entryNames = entries.map(e => e.filename.toLowerCase());
        const contentLower = content.toLowerCase();
        
        // 根据文件路径特征判断
        if (entryNames.some(name => name.includes('miui') || name.includes('xiaomi'))) {
            return 'xiaomi';
        }
        if (entryNames.some(name => name.includes('vivo') || name.includes('funtouch') || name.includes('origin'))) {
            return 'vivo';
        }
        if (entryNames.some(name => name.includes('coloros') || name.includes('oppo'))) {
            return 'oppo';
        }
        if (entryNames.some(name => name.includes('harmony') || name.includes('emui') || name.includes('hmos'))) {
            return 'huawei';
        }
        
        // 根据内容特征判断
        if (contentLower.includes('miui') || contentLower.includes('xiaomi')) {
            return 'xiaomi';
        }
        if (contentLower.includes('funtouch') || contentLower.includes('originos')) {
            return 'vivo';
        }
        if (contentLower.includes('coloros') || contentLower.includes('realmeui')) {
            return 'oppo';
        }
        if (contentLower.includes('harmonyos') || contentLower.includes('emui')) {
            return 'huawei';
        }
        
        return 'generic';
    },

    /**
     * 通用解析器
     * @param {string} content - 文件内容
     * @returns {Object|null} - 解析结果
     */
    parseGeneric(content) {
        const result = {
            currentCapacity: null,
            chargeCounter: null,
            currentNow: null,
            capacity: null,
            health: null,
            cycleCount: null,
            batteryTemp: null,
            voltage: null,
            technology: null,
            confidence: 0
        };

        // 提取 charge_counter (充电计数器，单位微安时)
        const chargeCounterPatterns = [
            /charge[_-]?counter[:\s]+(\d+)/i,
            /charge[_-]?counter[:\s]+(\d+)\s*uah/i,
            /charge[_-]?counter[:\s]+(\d+)\s*ma/i
        ];
        for (const pattern of chargeCounterPatterns) {
            const match = content.match(pattern);
            if (match) {
                result.chargeCounter = parseInt(match[1]);
                break;
            }
        }

        // 提取 current_now (当前电流)
        const currentNowMatch = content.match(/current[_-]?now[:\s]+(-?\d+)/i);
        if (currentNowMatch) {
            result.currentNow = parseInt(currentNowMatch[1]);
        }

        // 提取 capacity (电池容量百分比)
        const capacityMatch = content.match(/capacity[:\s]+(\d+)(?!\s*%)/i);
        if (capacityMatch) {
            result.capacity = parseInt(capacityMatch[1]);
        }

        // 提取 health (健康状态)
        const healthMatch = content.match(/health[:\s]+(\d+)/i);
        if (healthMatch) {
            result.health = parseInt(healthMatch[1]);
        }

        // 提取 cycle_count (充电循环次数)
        const cycleCountPatterns = [
            /cycle[_-]?count[:\s]+(\d+)/i,
            /charge[_-]?cycle[:\s]+(\d+)/i,
            /battery[_-]?cycle[:\s]+(\d+)/i,
            /cc[:\s]+(\d+)/i
        ];
        for (const pattern of cycleCountPatterns) {
            const match = content.match(pattern);
            if (match) {
                result.cycleCount = parseInt(match[1]);
                break;
            }
        }

        // 提取电池温度
        const tempPatterns = [
            /temperature[:\s]+(-?\d+\.?\d*)/i,
            /battery[_-]?temp[:\s]+(-?\d+\.?\d*)/i,
            /temp[:\s]+(-?\d+\.?\d*)(?!\s*°)/i
        ];
        for (const pattern of tempPatterns) {
            const match = content.match(pattern);
            if (match) {
                let temp = parseFloat(match[1]);
                if (temp > 100 && temp < 1000) {
                    temp = temp / 10;
                }
                result.batteryTemp = temp;
                break;
            }
        }

        // 提取电压
        const voltageMatch = content.match(/voltage[:\s]+(\d+)/i);
        if (voltageMatch) {
            let voltage = parseInt(voltageMatch[1]);
            if (voltage > 10000) {
                voltage = voltage / 1000;
            } else if (voltage > 1000) {
                voltage = voltage / 100;
            }
            result.voltage = voltage;
        }

        // 提取电池技术类型
        const techMatch = content.match(/technology[:\s]+(\w+)/i);
        if (techMatch) {
            result.technology = techMatch[1];
        }

        // 计算当前容量
        if (result.chargeCounter) {
            result.currentCapacity = Math.round(result.chargeCounter / 1000);
            result.confidence = 0.9;
        } else {
            // 尝试其他方式提取容量
            const capacityMention = content.match(/(?:full[_-]?charge[_-]?capacity|fcc)[:\s]+(\d+)/i);
            if (capacityMention) {
                result.currentCapacity = parseInt(capacityMention[1]);
                result.confidence = 0.7;
            }
        }

        return result.currentCapacity ? result : null;
    },

    /**
     * 小米专用解析器
     * @param {string} content - 文件内容
     * @returns {Object|null} - 解析结果
     */
    parseXiaomi(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;

        // 小米特有的解析逻辑
        // 小米的 charge_counter 通常在 dumpstate.txt 中
        const batterySection = content.match(/Battery Service[\s\S]*?(?=\n\n[A-Z]|\n[A-Z][a-z]+:)/i);
        if (batterySection) {
            const section = batterySection[0];
            
            // 小米特有的字段
            const miChargeMatch = section.match(/Charge counter:\s*(\d+)/i);
            if (miChargeMatch && !result.chargeCounter) {
                result.chargeCounter = parseInt(miChargeMatch[1]);
                result.currentCapacity = Math.round(result.chargeCounter / 1000);
            }

            // 小米的循环次数可能在不同位置
            const miCycleMatch = section.match(/Cycle count:\s*(\d+)/i) || 
                                content.match(/Battery cycle count:\s*(\d+)/i);
            if (miCycleMatch && !result.cycleCount) {
                result.cycleCount = parseInt(miCycleMatch[1]);
            }
        }

        result.brand = 'xiaomi';
        return result;
    },

    /**
     * vivo专用解析器
     * @param {string} content - 文件内容
     * @returns {Object|null} - 解析结果
     */
    parseVivo(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;

        // vivo特有的解析逻辑
        // vivo的日志格式可能不同
        const vivoPatterns = {
            cycleCount: /充电循环次数[:\s]+(\d+)/i,
            batteryTemp: /电池温度[:\s]+(\d+\.?\d*)/i,
            currentCapacity: /当前容量[:\s]+(\d+)/i
        };

        for (const [key, pattern] of Object.entries(vivoPatterns)) {
            const match = content.match(pattern);
            if (match && !result[key]) {
                result[key] = parseFloat(match[1]);
            }
        }

        result.brand = 'vivo';
        return result;
    },

    /**
     * OPPO专用解析器
     * @param {string} content - 文件内容
     * @returns {Object|null} - 解析结果
     */
    parseOPPO(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;

        // OPPO特有的解析逻辑
        // ColorOS的日志格式
        const oppoSection = content.match(/Battery Information[\s\S]*?(?=\n\n|\n[A-Z])/i);
        if (oppoSection) {
            const section = oppoSection[0];
            
            const oppoPatterns = {
                cycleCount: /Cycle Count[:\s]+(\d+)/i,
                batteryTemp: /Temperature[:\s]+(\d+\.?\d*)/i
            };

            for (const [key, pattern] of Object.entries(oppoPatterns)) {
                const match = section.match(pattern);
                if (match && !result[key]) {
                    result[key] = parseFloat(match[1]);
                }
            }
        }

        result.brand = 'oppo';
        return result;
    },

    /**
     * 华为专用解析器
     * @param {string} content - 文件内容
     * @returns {Object|null} - 解析结果
     */
    parseHuawei(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;

        // 华为特有的解析逻辑
        // HarmonyOS/EMUI的日志格式
        const huaweiSection = content.match(/Battery Stats[\s\S]*?(?=\n\n|\n[A-Z])/i);
        if (huaweiSection) {
            const section = huaweiSection[0];
            
            // 华为可能使用不同的字段名
            const hwPatterns = {
                cycleCount: /Charge cycles?[:\s]+(\d+)/i,
                batteryTemp: /Battery temp[:\s]+(\d+\.?\d*)/i
            };

            for (const [key, pattern] of Object.entries(hwPatterns)) {
                const match = section.match(pattern);
                if (match && !result[key]) {
                    result[key] = parseFloat(match[1]);
                }
            }
        }

        result.brand = 'huawei';
        return result;
    },

    /**
     * 主解析函数
     * @param {string} content - 文件内容
     * @param {string} brand - 品牌
     * @returns {Object|null} - 解析结果
     */
    parse(content, brand = 'generic') {
        switch (brand) {
            case 'xiaomi':
                return this.parseXiaomi(content);
            case 'vivo':
                return this.parseVivo(content);
            case 'oppo':
                return this.parseOPPO(content);
            case 'huawei':
                return this.parseHuawei(content);
            default:
                return this.parseGeneric(content);
        }
    }
};

// 导出模块
if (typeof module !== 'undefined' && module.exports) {
    module.exports = BatteryParsers;
}
