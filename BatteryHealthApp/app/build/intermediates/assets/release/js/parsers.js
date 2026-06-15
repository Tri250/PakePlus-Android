/**
 * 电池信息解析器模块 - 专业版
 * 真实解析安卓诊断文件中的电池健康度、温度、循环次数
 * 支持小米、vivo、OPPO、华为、三星、魅族、努比亚等多品牌
 */

// ============================================
// 配置：各品牌解析规则（支持容量、循环次数、温度）
// ============================================
const BRAND_CONFIG = {
    xiaomi: {
        name: '小米/Redmi',
        // 当前容量解析规则
        capacityPatterns: [
            /Min learned battery capacity:\s*(\d+)\s*mAh/i,
            /fc=(\d+)/i,
            /MF_05[=:\s]+(\d+)/i,
            /charge_capacity[=:\s]+(\d+)/i,
            /last_full_capacity[=:\s]+(\d+)/i,
            /charge[_\s-]?counter[=:\s]+(\d+)/i,
            /CHARGE_COUNTER[=:\s]+(\d+)/i
        ],
        capacityDivisor: [1, 1000, 1, 1, 1, 1, 1],
        // 设计容量解析规则
        designPatterns: [
            /dc=(\d+)/i,
            /MF_06[=:\s]+(\d+)/i,
            /design_capacity[=:\s]+(\d+)/i,
            /nominal_capacity[=:\s]+(\d+)/i
        ],
        // 循环次数解析规则
        cyclePatterns: [
            /CYCLE_COUNT[=:\s]+(\d+)/i,
            /cycle_count[=:\s]+(\d+)/i,
            /cc=(\d+)/i,
            /MF_02[=:\s]+(\d+)/i,
            /charge_cycles[=:\s]+(\d+)/i
        ],
        // 温度解析规则（单位0.1°C，需除以10）
        tempPatterns: [
            /temperature[=:\s]+(\d+)/i,
            /t=(\d+)/i,
            /battery_temperature[=:\s]+(\d+)/i,
            /temp[=:\s]+(\d+)/i
        ],
        tempDivisor: 10
    },
    huawei: {
        name: '华为',
        capacityPatterns: [
            /healthd:[\s\S]*?capacity[=:\s]+(\d+)/i,
            /battery_capacity[=:\s]+(\d+)/i,
            /actual_capacity[=:\s]+(\d+)/i,
            /Charge\s*capacity[=:\s]+(\d+)/i,
            /fc=(\d+)/i,
            /charge[_\s-]?counter[=:\s]+(\d+)/i
        ],
        capacityDivisor: [1, 1, 1, 1, 1000, 1],
        designPatterns: [
            /design_capacity[=:\s]+(\d+)/i,
            /nominal_capacity[=:\s]+(\d+)/i,
            /dc=(\d+)/i
        ],
        cyclePatterns: [
            /cycle_count[=:\s]+(\d+)/i,
            /cc=(\d+)/i,
            /charge_cycles[=:\s]+(\d+)/i,
            /CYCLE_COUNT[=:\s]+(\d+)/i
        ],
        tempPatterns: [
            /temperature[=:\s]+(\d+)/i,
            /t=(\d+)/i,
            /battery_temperature[=:\s]+(\d+)/i
        ],
        tempDivisor: 10
    },
    honor: {
        name: '荣耀',
        capacityPatterns: [
            /healthd:[\s\S]*?capacity[=:\s]+(\d+)/i,
            /battery_capacity[=:\s]+(\d+)/i,
            /actual_capacity[=:\s]+(\d+)/i,
            /fc=(\d+)/i,
            /charge[_\s-]?counter[=:\s]+(\d+)/i
        ],
        capacityDivisor: [1, 1, 1, 1000, 1],
        designPatterns: [
            /design_capacity[=:\s]+(\d+)/i,
            /nominal_capacity[=:\s]+(\d+)/i,
            /dc=(\d+)/i
        ],
        cyclePatterns: [
            /cc=(\d+)/i,
            /cycle_count[=:\s]+(\d+)/i,
            /charge_cycles[=:\s]+(\d+)/i,
            /CYCLE_COUNT[=:\s]+(\d+)/i
        ],
        tempPatterns: [
            /temperature[=:\s]+(\d+)/i,
            /t=(\d+)/i,
            /battery_temperature[=:\s]+(\d+)/i
        ],
        tempDivisor: 10
    },
    oppo: {
        name: 'OPPO',
        capacityPatterns: [
            /battery_capacity[=:\s]+(\d+)/i,
            /current_capacity[=:\s]+(\d+)/i,
            /actual_capacity[=:\s]+(\d+)/i,
            /fc=(\d+)/i,
            /last_full_capacity[=:\s]+(\d+)/i,
            /charge[_\s-]?counter[=:\s]+(\d+)/i
        ],
        capacityDivisor: [1, 1, 1, 1000, 1, 1],
        designPatterns: [
            /design_capacity[=:\s]+(\d+)/i,
            /nominal_capacity[=:\s]+(\d+)/i,
            /dc=(\d+)/i
        ],
        cyclePatterns: [
            /cycle_count[=:\s]+(\d+)/i,
            /cc=(\d+)/i,
            /charge_cycles[=:\s]+(\d+)/i,
            /CYCLE_COUNT[=:\s]+(\d+)/i
        ],
        tempPatterns: [
            /temperature[=:\s]+(\d+)/i,
            /t=(\d+)/i,
            /battery_temperature[=:\s]+(\d+)/i
        ],
        tempDivisor: 10
    },
    vivo: {
        name: 'vivo',
        capacityPatterns: [
            /battery_capacity[=:\s]+(\d+)/i,
            /charge_capacity[=:\s]+(\d+)/i,
            /current_capacity[=:\s]+(\d+)/i,
            /fc=(\d+)/i,
            /charge[_\s-]?counter[=:\s]+(\d+)/i
        ],
        capacityDivisor: [1, 1, 1, 1000, 1],
        designPatterns: [
            /design_capacity[=:\s]+(\d+)/i,
            /nominal_capacity[=:\s]+(\d+)/i,
            /dc=(\d+)/i
        ],
        cyclePatterns: [
            /cycle_count[=:\s]+(\d+)/i,
            /cc=(\d+)/i,
            /charge_cycles[=:\s]+(\d+)/i,
            /CYCLE_COUNT[=:\s]+(\d+)/i
        ],
        tempPatterns: [
            /temperature[=:\s]+(\d+)/i,
            /t=(\d+)/i,
            /battery_temperature[=:\s]+(\d+)/i
        ],
        tempDivisor: 10
    },
    oneplus: {
        name: '一加',
        capacityPatterns: [
            /battery_capacity[=:\s]+(\d+)/i,
            /fc=(\d+)/i,
            /actual_capacity[=:\s]+(\d+)/i,
            /last_full_capacity[=:\s]+(\d+)/i,
            /charge[_\s-]?counter[=:\s]+(\d+)/i
        ],
        capacityDivisor: [1, 1000, 1, 1, 1],
        designPatterns: [
            /design_capacity[=:\s]+(\d+)/i,
            /dc=(\d+)/i,
            /nominal_capacity[=:\s]+(\d+)/i
        ],
        cyclePatterns: [
            /cycle_count[=:\s]+(\d+)/i,
            /cc=(\d+)/i,
            /charge_cycles[=:\s]+(\d+)/i,
            /CYCLE_COUNT[=:\s]+(\d+)/i
        ],
        tempPatterns: [
            /temperature[=:\s]+(\d+)/i,
            /t=(\d+)/i,
            /battery_temperature[=:\s]+(\d+)/i
        ],
        tempDivisor: 10
    },
    samsung: {
        name: '三星',
        capacityPatterns: [
            /battery_capacity[=:\s]+(\d+)/i,
            /fc=(\d+)/i,
            /charge[_\s-]?counter[=:\s]+(\d+)/i
        ],
        capacityDivisor: [1, 1000, 1],
        designPatterns: [
            /design_capacity[=:\s]+(\d+)/i,
            /nominal_capacity[=:\s]+(\d+)/i
        ],
        cyclePatterns: [
            /cycle_count[=:\s]+(\d+)/i,
            /cc=(\d+)/i
        ],
        tempPatterns: [
            /temperature[=:\s]+(\d+)/i,
            /battery_temperature[=:\s]+(\d+)/i
        ],
        tempDivisor: 10
    },
    meizu: {
        name: '魅族',
        capacityPatterns: [
            /battery_capacity[=:\s]+(\d+)/i,
            /fc=(\d+)/i,
            /charge[_\s-]?counter[=:\s]+(\d+)/i
        ],
        capacityDivisor: [1, 1000, 1],
        designPatterns: [
            /design_capacity[=:\s]+(\d+)/i,
            /nominal_capacity[=:\s]+(\d+)/i
        ],
        cyclePatterns: [
            /cycle_count[=:\s]+(\d+)/i,
            /cc=(\d+)/i
        ],
        tempPatterns: [
            /temperature[=:\s]+(\d+)/i,
            /battery_temperature[=:\s]+(\d+)/i
        ],
        tempDivisor: 10
    },
    nubia: {
        name: '努比亚',
        capacityPatterns: [
            /battery_capacity[=:\s]+(\d+)/i,
            /fc=(\d+)/i,
            /charge[_\s-]?counter[=:\s]+(\d+)/i
        ],
        capacityDivisor: [1, 1000, 1],
        designPatterns: [
            /design_capacity[=:\s]+(\d+)/i,
            /nominal_capacity[=:\s]+(\d+)/i
        ],
        cyclePatterns: [
            /cycle_count[=:\s]+(\d+)/i,
            /cc=(\d+)/i
        ],
        tempPatterns: [
            /temperature[=:\s]+(\d+)/i,
            /battery_temperature[=:\s]+(\d+)/i
        ],
        tempDivisor: 10
    }
};

const BatteryParsers = {
    /**
     * 智能单位转换：将 charge_counter 转换为 mAh
     * 关键修复：处理不同厂商的单位差异
     * @param {number} value - 原始值
     * @returns {number} - 转换后的 mAh 值
     */
    convertToMah(value) {
        if (!value || value <= 0) return 0;
        
        // 如果值已经很小（< 1000），可能是 mAh，直接返回
        if (value < 1000) {
            return Math.round(value);
        }
        
        // 如果值很大（>= 1000000），肯定是 uAh，除以 1000
        if (value >= 1000000) {
            return Math.round(value / 1000);
        }
        
        // 值在 1000-100000 之间，需要智能判断
        // 如果除以 1000 后结果在合理范围（1000-20000 mAh），则是 uAh
        // 否则可能是 mAh
        const divided = Math.round(value / 1000);
        if (divided >= 1000 && divided <= 20000) {
            return divided;
        }
        
        // 如果原始值在合理范围，直接返回
        if (value >= 1000 && value <= 20000) {
            return Math.round(value);
        }
        
        // 默认除以 1000
        return divided;
    },

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
        if (entryNames.some(name => name.includes('coloros') || name.includes('oppo') || name.includes('oneplus') || name.includes('oos'))) {
            return 'oppo';
        }
        if (entryNames.some(name => name.includes('harmony') || name.includes('emui') || name.includes('hmos') || name.includes('huawei') || name.includes('honor'))) {
            return 'huawei';
        }
        if (entryNames.some(name => name.includes('flyme') || name.includes('meizu'))) {
            return 'meizu';
        }
        if (entryNames.some(name => name.includes('nubia') || name.includes('redmagic') || name.includes('redmag'))) {
            return 'nubia';
        }
        if (entryNames.some(name => name.includes('samsung') || name.includes('oneui'))) {
            return 'samsung';
        }
        if (entryNames.some(name => name.includes('realme') || name.includes('realm'))) {
            return 'realme';
        }
        if (entryNames.some(name => name.includes('iqoo'))) {
            return 'iqoo';
        }
        if (entryNames.some(name => name.includes('zte') || name.includes('axon'))) {
            return 'zte';
        }
        if (entryNames.some(name => name.includes('moto') || name.includes('motorola'))) {
            return 'motorola';
        }
        
        // 根据内容特征判断
        if (contentLower.includes('miui') || contentLower.includes('xiaomi')) {
            return 'xiaomi';
        }
        if (contentLower.includes('funtouch') || contentLower.includes('originos') || contentLower.includes('vivo')) {
            return 'vivo';
        }
        if (contentLower.includes('coloros') || contentLower.includes('oxygenos') || contentLower.includes('realmeui')) {
            return 'oppo';
        }
        if (contentLower.includes('harmonyos') || contentLower.includes('emui') || contentLower.includes('magicui')) {
            return 'huawei';
        }
        if (contentLower.includes('flyme')) {
            return 'meizu';
        }
        if (contentLower.includes('redmagic') || contentLower.includes('nubia')) {
            return 'nubia';
        }
        if (contentLower.includes('samsung') || contentLower.includes('one ui')) {
            return 'samsung';
        }
        
        return 'generic';
    },

    /**
     * 通用解析器 - 从Android系统标准电池属性文件解析
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
            rawContent: null,
            confidence: 0,
            healthGrade: null
        };

        // ========== 1. 解析 charge_counter (充电计数器，单位微安时 uAh) ==========
        // 这是计算当前电池容量的关键指标
        const chargeCounterPatterns = [
            /charge[_\s-]?counter[:\s]+(\d+)\s*uah/i,
            /charge[_\s-]?counter[:\s]+(\d+)/i,
            /CHARGE_COUNTER[:\s]+(\d+)/i,
            /cc[:\s]+(\d+)\s*uah/i,
            /last[_\s-]?full[_\s-]?charge[_\s-]?counter[:\s]+(\d+)/i,
            /full[_\s-]?charge[_\s-]?capacity[:\s]+(\d+)\s*uah/i,
            /fcc[:\s]+(\d+)/i,
            /\bcharge_counter\b[\s:=]+(\d+)/i
        ];
        
        for (const pattern of chargeCounterPatterns) {
            const match = content.match(pattern);
            if (match) {
                const value = parseInt(match[1]);
                if (value > 0) {
                    result.chargeCounter = value;
                    // 关键修复：使用统一的单位转换函数
                    result.currentCapacity = this.convertToMah(value);
                    result.confidence = 0.95;
                    break;
                }
            }
        }

        // ========== 2. 解析 current_now (当前电流，单位微安 uA) ==========
        const currentNowPatterns = [
            /current[_\s-]?now[:\s]+(-?\d+)\s*ua/i,
            /current[_\s-]?now[:\s]+(-?\d+)/i,
            /CURRENT_NOW[:\s]+(-?\d+)/i,
            /\bcurrent_now\b[\s:=]+(-?\d+)/i
        ];
        
        for (const pattern of currentNowPatterns) {
            const match = content.match(pattern);
            if (match) {
                result.currentNow = parseInt(match[1]);
                break;
            }
        }

        // ========== 3. 解析 capacity (电池电量百分比) ==========
        const capacityPatterns = [
            /capacity[:\s]+(\d+)\s*%/i,
            /CAPACITY[:\s]+(\d+)/i,
            /level[:\s]+(\d+)\s*%/i,
            /\bcapacity\b[\s:=]+(\d+)(?!\s*mah)/i
        ];
        
        for (const pattern of capacityPatterns) {
            const match = content.match(pattern);
            if (match) {
                const value = parseInt(match[1]);
                if (value >= 0 && value <= 100) {
                    result.capacity = value;
                    break;
                }
            }
        }

        // ========== 4. 解析 health (电池健康状态值) ==========
        const healthPatterns = [
            /health[:\s]+(\w+)/i,
            /HEALTH[:\s]+(\w+)/i,
            /battery[_\s-]?health[:\s]+(\w+)/i,
            /\bhealth\b[\s:=]+(\w+)/i
        ];
        
        for (const pattern of healthPatterns) {
            const match = content.match(pattern);
            if (match) {
                result.health = match[1].toLowerCase();
                break;
            }
        }

        // ========== 5. 解析 cycle_count (充电循环次数) ==========
        // 这是评估电池寿命的重要指标
        const cycleCountPatterns = [
            /cycle[_\s-]?count[:\s]+(\d+)/i,
            /CYCLE_COUNT[:\s]+(\d+)/i,
            /charge[_\s-]?cycle[:\s]+(\d+)/i,
            /battery[_\s-]?cycle[:\s]+(\d+)/i,
            /cycle[_\s-]?counter[:\s]+(\d+)/i,
            /cc[:\s]+(\d+)(?!\s*uah)/i,
            /charge[_\s-]?cycles[:\s]+(\d+)/i,
            /充电循环次数[:\s]+(\d+)/i,
            /循环次数[:\s]+(\d+)/i,
            /累计循环[:\s]+(\d+)/i,
            /\bcycle_count\b[\s:=]+(\d+)/i,
            /\bcycle\b[\s:=]+(\d+)/i
        ];
        
        for (const pattern of cycleCountPatterns) {
            const match = content.match(pattern);
            if (match) {
                const value = parseInt(match[1]);
                if (value >= 0 && value < 10000) { // 合理范围检查
                    result.cycleCount = value;
                    break;
                }
            }
        }

        // ========== 6. 解析电池温度 (单位通常是十分之一摄氏度，需转换) ==========
        const tempPatterns = [
            /temperature[:\s]+(-?\d+\.?\d*)\s*°c/i,
            /temperature[:\s]+(-?\d+\.?\d*)/i,
            /TEMP[:\s]+(-?\d+)/i,
            /battery[_\s-]?temp[:\s]+(-?\d+\.?\d*)/i,
            /temp[:\s]+(-?\d+\.?\d*)(?!\s*%)/i,
            /电池温度[:\s]+(-?\d+\.?\d*)/i,
            /温度[:\s]+(-?\d+\.?\d*)/i,
            /\btemperature\b[\s:=]+(-?\d+)/i
        ];
        
        for (const pattern of tempPatterns) {
            const match = content.match(pattern);
            if (match) {
                let temp = parseFloat(match[1]);
                // Android系统温度单位通常是十分之一摄氏度(如250表示25°C)
                if (temp > 100 && temp < 1000) {
                    temp = temp / 10;
                } else if (temp > 1000 && temp < 10000) {
                    temp = temp / 100;
                }
                // 合理范围检查：-20°C 到 80°C
                if (temp >= -20 && temp <= 80) {
                    result.batteryTemp = temp;
                    break;
                }
            }
        }

        // ========== 7. 解析电压 (单位通常是微伏 uV 或毫伏 mV) ==========
        const voltagePatterns = [
            /voltage[:\s]+(\d+\.?\d*)\s*v/i,
            /voltage[:\s]+(\d+)/i,
            /VOLTAGE[:\s]+(\d+)/i,
            /batt[_\s-]?voltage[:\s]+(\d+)/i,
            /\bvoltage\b[\s:=]+(\d+)/i
        ];
        
        for (const pattern of voltagePatterns) {
            const match = content.match(pattern);
            if (match) {
                let voltage = parseFloat(match[1]);
                // 转换为毫伏(mV)
                if (voltage > 10000) {
                    voltage = voltage / 1000; // uV -> mV
                } else if (voltage > 1000 && voltage < 5000) {
                    voltage = voltage; // 已经是mV
                } else if (voltage >= 3 && voltage <= 5) {
                    voltage = voltage * 1000; // V -> mV
                }
                // 合理范围：2500mV - 5000mV
                if (voltage >= 2500 && voltage <= 5000) {
                    result.voltage = voltage;
                    break;
                }
            }
        }

        // ========== 8. 解析电池技术类型 ==========
        const techPatterns = [
            /technology[:\s]+(\w+)/i,
            /TECHNOLOGY[:\s]+(\w+)/i,
            /battery[_\s-]?type[:\s]+(\w+)/i,
            /\btechnology\b[\s:=]+(\w+)/i
        ];
        
        for (const pattern of techPatterns) {
            const match = content.match(pattern);
            if (match) {
                result.technology = match[1];
                break;
            }
        }

        // ========== 9. 从dumpstate/bugreport中提取电池信息 ==========
        // 尝试从标准Android dumpstate格式中提取
        const dumpstateBatterySection = content.match(/DUMP OF SERVICE batterystats[\s\S]*?(?=DUMP OF SERVICE|$)/i);
        if (dumpstateBatterySection) {
            const section = dumpstateBatterySection[0];
            
            // 从batterystats服务输出中提取
            if (!result.cycleCount) {
                const cycleMatch = section.match(/Daily stats[\s\S]*?charge cycles:\s*(\d+)/i);
                if (cycleMatch) {
                    result.cycleCount = parseInt(cycleMatch[1]);
                }
            }
            
            // 保存原始内容片段
            result.rawContent = section.substring(0, 2000);
        }

        // ========== 10. 从BatteryProperties/系统属性中提取 ==========
        const batteryPropsSection = content.match(/Battery Properties[\s\S]*?(?=\n\n[A-Z]|\n[A-Z][a-z]+:|$)/i);
        if (batteryPropsSection) {
            const section = batteryPropsSection[0];
            
            if (!result.chargeCounter) {
                const ccMatch = section.match(/Charge counter:\s*(\d+)/i);
                if (ccMatch) {
                    const value = parseInt(ccMatch[1]);
                    result.chargeCounter = value;
                    // 关键修复：使用统一的单位转换函数
                    result.currentCapacity = this.convertToMah(value);
                    result.confidence = 0.9;
                }
            }
            
            if (!result.cycleCount) {
                const cycleMatch = section.match(/Cycle count:\s*(\d+)/i);
                if (cycleMatch) {
                    result.cycleCount = parseInt(cycleMatch[1]);
                }
            }
            
            if (!result.batteryTemp) {
                const tempMatch = section.match(/Temperature:\s*(\d+)/i);
                if (tempMatch) {
                    let temp = parseInt(tempMatch[1]);
                    if (temp > 100) temp = temp / 10;
                    result.batteryTemp = temp;
                }
            }
        }

        // ========== 11. 尝试从/sys/class/power_supply/battery格式提取 ==========
        // 这是Android系统读取电池信息的标准路径
        const sysfsPatterns = {
            chargeCounter: /\/sys\/class\/power_supply\/battery\/charge_counter[\s\S]*?(\d+)/i,
            cycleCount: /\/sys\/class\/power_supply\/battery\/cycle_count[\s\S]*?(\d+)/i,
            temp: /\/sys\/class\/power_supply\/battery\/temp[\s\S]*?(\d+)/i,
            voltage: /\/sys\/class\/power_supply\/battery\/voltage_now[\s\S]*?(\d+)/i
        };
        
        for (const [key, pattern] of Object.entries(sysfsPatterns)) {
            if (!result[key === 'temp' ? 'batteryTemp' : key]) {
                const match = content.match(pattern);
                if (match) {
                    const value = parseInt(match[1]);
                    if (key === 'chargeCounter' && value > 0) {
                        result.chargeCounter = value;
                        // 关键修复：使用统一的单位转换函数
                        result.currentCapacity = this.convertToMah(value);
                        result.confidence = 0.85;
                    } else if (key === 'cycleCount') {
                        result.cycleCount = value;
                    } else if (key === 'temp') {
                        result.batteryTemp = value > 100 ? value / 10 : value;
                    } else if (key === 'voltage') {
                        result.voltage = value > 10000 ? value / 1000 : value;
                    }
                }
            }
        }

        // ========== 12. 计算健康等级 ==========
        if (result.cycleCount) {
            result.healthGrade = this.calculateHealthGrade(result.cycleCount);
        }

        // ========== 13. 如果仍未获取容量，尝试其他方式 ==========
        if (!result.currentCapacity) {
            // 尝试从Full Charge Capacity字段提取
            const fccMatch = content.match(/(?:full[_\s-]?charge[_\s-]?capacity|fcc|design[_\s-]?capacity)[:\s]+(\d+)\s*mah/i);
            if (fccMatch) {
                result.currentCapacity = parseInt(fccMatch[1]);
                result.confidence = 0.7;
            }
            
            // 尝试从电池信息段落提取
            const batteryInfoMatch = content.match(/Battery Information[\s\S]*?Capacity[:\s]+(\d+)\s*mah/i);
            if (batteryInfoMatch) {
                result.currentCapacity = parseInt(batteryInfoMatch[1]);
                result.confidence = 0.6;
            }
        }

        // 保存原始内容片段用于调试
        if (!result.rawContent) {
            const batteryMatch = content.match(/Battery[\s\S]*?(?=\n\n[A-Z]|\n[A-Z][a-z]+:|$)/i);
            if (batteryMatch) {
                result.rawContent = batteryMatch[0].substring(0, 1500);
            }
        }

        return result.currentCapacity ? result : null;
    },

    /**
     * 根据循环次数计算健康等级
     * @param {number} cycleCount - 循环次数
     * @returns {Object} - 健康等级信息
     */
    calculateHealthGrade(cycleCount) {
        let grade, color, description, estimatedHealth;
        
        if (cycleCount <= 100) {
            grade = 'A+';
            color = '#2ecc71';
            description = '电池状态极佳，几乎无损耗';
            estimatedHealth = '95-100%';
        } else if (cycleCount <= 200) {
            grade = 'A';
            color = '#27ae60';
            description = '电池状态优秀，轻微使用';
            estimatedHealth = '90-95%';
        } else if (cycleCount <= 300) {
            grade = 'B+';
            color = '#3498db';
            description = '电池状态良好，正常使用';
            estimatedHealth = '85-90%';
        } else if (cycleCount <= 500) {
            grade = 'B';
            color = '#2980b9';
            description = '电池状态正常，常规老化';
            estimatedHealth = '80-85%';
        } else if (cycleCount <= 700) {
            grade = 'C';
            color = '#f39c12';
            description = '电池轻度老化，续航下降';
            estimatedHealth = '75-80%';
        } else if (cycleCount <= 900) {
            grade = 'D';
            color = '#e67e22';
            description = '电池中度老化，建议关注';
            estimatedHealth = '70-75%';
        } else {
            grade = 'E';
            color = '#e74c3c';
            description = '电池严重老化，建议更换';
            estimatedHealth = '<70%';
        }
        
        return {
            grade,
            color,
            description,
            estimatedHealth,
            cycleCount
        };
    },

    /**
     * 小米专用解析器
     * @param {string} content - 文件内容
     * @returns {Object|null} - 解析结果
     */
    parseXiaomi(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;

        // 小米MIUI特有的电池信息格式
        // MIUI的bugreport通常包含更详细的电池信息
        
        // 尝试从MIUI电池服务输出中提取
        const miuiBatterySection = content.match(/Battery Service[\s\S]*?(?=\n\n[A-Z]|\n[A-Z][a-z]+:|$)/i);
        if (miuiBatterySection) {
            const section = miuiBatterySection[0];
            
            // 小米特有的charge_counter格式
            if (!result.chargeCounter) {
                const miCCMatch = section.match(/Charge counter:\s*(\d+)\s*uAh/i) ||
                                  section.match(/Charge counter:\s*(\d+)/i);
                if (miCCMatch) {
                    const value = parseInt(miCCMatch[1]);
                    result.chargeCounter = value;
                    // 关键修复：使用统一的单位转换函数
                    result.currentCapacity = this.convertToMah(value);
                    result.confidence = 0.95;
                }
            }
            
            // 小米的循环次数
            if (!result.cycleCount) {
                const miCycleMatch = section.match(/Cycle count:\s*(\d+)/i) ||
                                     section.match(/Battery cycle count:\s*(\d+)/i) ||
                                     content.match(/cycle_count:\s*(\d+)/i);
                if (miCycleMatch) {
                    result.cycleCount = parseInt(miCycleMatch[1]);
                }
            }
            
            // 小米电池温度
            if (!result.batteryTemp) {
                const miTempMatch = section.match(/Temperature:\s*(\d+)/i);
                if (miTempMatch) {
                    let temp = parseInt(miTempMatch[1]);
                    result.batteryTemp = temp > 100 ? temp / 10 : temp;
                }
            }
            
            // 保存原始内容
            if (!result.rawContent) {
                result.rawContent = section.substring(0, 2000);
            }
        }

        // 尝试从MIUI系统属性中提取
        const miuiSysPropMatch = content.match(/ro\.miui\.battery[\s\S]*?(?=\nro\.|$)/i);
        if (miuiSysPropMatch) {
            const props = miuiSysPropMatch[0];
            
            if (!result.cycleCount) {
                const cycleMatch = props.match(/cycle_count[:\s]+(\d+)/i);
                if (cycleMatch) {
                    result.cycleCount = parseInt(cycleMatch[1]);
                }
            }
        }

        // 从dumpstate_battery中提取
        const dumpstateBattery = content.match(/dumpstate_battery[\s\S]*?(?=\n\n|$)/i);
        if (dumpstateBattery) {
            const section = dumpstateBattery[0];
            
            if (!result.currentCapacity) {
                const capMatch = section.match(/capacity[:\s]+(\d+)\s*mAh/i) ||
                                 section.match(/charge_counter[:\s]+(\d+)/i);
                if (capMatch) {
                    const value = parseInt(capMatch[1]);
                    if (capMatch[0].includes('mAh')) {
                        result.currentCapacity = value;
                    } else {
                        result.chargeCounter = value;
                        // 关键修复：使用统一的单位转换函数
                        result.currentCapacity = this.convertToMah(value);
                    }
                    result.confidence = 0.9;
                }
            }
        }

        // 计算健康等级
        if (result.cycleCount) {
            result.healthGrade = this.calculateHealthGrade(result.cycleCount);
        }

        result.brand = 'xiaomi';
        return result;
    },

    /**
     * vivo/iQOO专用解析器
     * @param {string} content - 文件内容
     * @returns {Object|null} - 解析结果
     */
    parseVivo(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;

        // vivo/iQOO特有的电池信息格式
        const vivoPatterns = {
            cycleCount: [
                /充电循环次数[:\s]+(\d+)/i,
                /循环次数[:\s]+(\d+)/i,
                /charge[_\s-]?cycles[:\s]+(\d+)/i,
                /cycle[_\s-]?count[:\s]+(\d+)/i
            ],
            batteryTemp: [
                /电池温度[:\s]+(-?\d+\.?\d*)/i,
                /温度[:\s]+(-?\d+\.?\d*)°C/i,
                /battery[_\s-]?temp[:\s]+(-?\d+)/i
            ],
            currentCapacity: [
                /当前容量[:\s]+(\d+)\s*mAh/i,
                /实际容量[:\s]+(\d+)\s*mAh/i,
                /charge[_\s-]?counter[:\s]+(\d+)/i
            ]
        };

        for (const [key, patterns] of Object.entries(vivoPatterns)) {
            for (const pattern of patterns) {
                const match = content.match(pattern);
                if (match && !result[key]) {
                    let value = parseFloat(match[1]);
                    if (key === 'currentCapacity') {
                        if (pattern.toString().includes('counter')) {
                            result.chargeCounter = value;
                            // 关键修复：使用统一的单位转换函数
                            result.currentCapacity = this.convertToMah(value);
                            result.confidence = 0.9;
                        } else {
                            result.currentCapacity = Math.round(value);
                            result.confidence = 0.85;
                        }
                    } else if (key === 'batteryTemp') {
                        if (value > 100) value = value / 10;
                        result.batteryTemp = value;
                    } else {
                        result[key] = value;
                    }
                    break;
                }
            }
        }

        // vivo特有的BatteryInfo段落
        const vivoBatterySection = content.match(/BatteryInfo[\s\S]*?(?=\n\n[A-Z]|\n[A-Z][a-z]+:|$)/i);
        if (vivoBatterySection) {
            const section = vivoBatterySection[0];
            
            if (!result.currentCapacity) {
                const capMatch = section.match(/ChargeCounter[:\s]+(\d+)/i);
                if (capMatch) {
                    const value = parseInt(capMatch[1]);
                    result.chargeCounter = value;
                    // 关键修复：使用统一的单位转换函数
                    result.currentCapacity = this.convertToMah(value);
                    result.confidence = 0.9;
                }
            }
            
            if (!result.rawContent) {
                result.rawContent = section.substring(0, 1500);
            }
        }

        // 计算健康等级
        if (result.cycleCount) {
            result.healthGrade = this.calculateHealthGrade(result.cycleCount);
        }

        result.brand = 'vivo';
        return result;
    },

    /**
     * OPPO/一加/realme专用解析器
     * @param {string} content - 文件内容
     * @returns {Object|null} - 解析结果
     */
    parseOPPO(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;

        // ColorOS/OxygenOS特有的电池信息格式
        const oppoSection = content.match(/Battery Information[\s\S]*?(?=\n\n|\n[A-Z][a-z]+:|$)/i);
        if (oppoSection) {
            const section = oppoSection[0];
            
            // OPPO特有的字段格式
            const oppoPatterns = {
                cycleCount: [
                    /Cycle Count[:\s]+(\d+)/i,
                    /cycle_count[:\s]+(\d+)/i,
                    /充电循环[:\s]+(\d+)/i
                ],
                batteryTemp: [
                    /Temperature[:\s]+(\d+\.?\d*)/i,
                    /temp[:\s]+(\d+)/i
                ],
                chargeCounter: [
                    /Charge Counter[:\s]+(\d+)/i,
                    /charge_counter[:\s]+(\d+)/i,
                    /FCC[:\s]+(\d+)/i
                ]
            };

            for (const [key, patterns] of Object.entries(oppoPatterns)) {
                for (const pattern of patterns) {
                    const match = section.match(pattern);
                    if (match && !result[key]) {
                        let value = parseFloat(match[1]);
                        if (key === 'chargeCounter') {
                            result.chargeCounter = value;
                            // 关键修复：使用统一的单位转换函数
                            result.currentCapacity = this.convertToMah(value);
                            result.confidence = 0.9;
                        } else if (key === 'batteryTemp') {
                            result.batteryTemp = value > 100 ? value / 10 : value;
                        } else {
                            result[key] = value;
                        }
                        break;
                    }
                }
            }
            
            if (!result.rawContent) {
                result.rawContent = section.substring(0, 1500);
            }
        }

        // 一加OxygenOS特有格式
        const oxygenSection = content.match(/OxygenOS Battery[\s\S]*?(?=\n\n|$)/i);
        if (oxygenSection) {
            const section = oxygenSection[0];
            
            if (!result.cycleCount) {
                const cycleMatch = section.match(/cycle_count[:\s]+(\d+)/i);
                if (cycleMatch) {
                    result.cycleCount = parseInt(cycleMatch[1]);
                }
            }
            
            if (!result.currentCapacity) {
                const capMatch = section.match(/capacity[:\s]+(\d+)/i);
                if (capMatch) {
                    const value = parseInt(capMatch[1]);
                    result.chargeCounter = value;
                    // 关键修复：使用统一的单位转换函数
                    result.currentCapacity = this.convertToMah(value);
                    result.confidence = 0.85;
                }
            }
        }

        // 计算健康等级
        if (result.cycleCount) {
            result.healthGrade = this.calculateHealthGrade(result.cycleCount);
        }

        result.brand = 'oppo';
        return result;
    },

    /**
     * 华为/荣耀专用解析器
     * @param {string} content - 文件内容
     * @returns {Object|null} - 解析结果
     */
    parseHuawei(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;

        // HarmonyOS/EMUI/MagicUI特有的电池信息格式
        const huaweiSection = content.match(/Battery Stats[\s\S]*?(?=\n\n|\n[A-Z][a-z]+:|$)/i) ||
                              content.match(/BatteryInfo[\s\S]*?(?=\n\n|\n[A-Z][a-z]+:|$)/i);
        
        if (huaweiSection) {
            const section = huaweiSection[0];
            
            // 华为特有的字段格式
            const hwPatterns = {
                cycleCount: [
                    /Charge cycles?[:\s]+(\d+)/i,
                    /cycle_count[:\s]+(\d+)/i,
                    /充电循环次数[:\s]+(\d+)/i,
                    /累计充电次数[:\s]+(\d+)/i
                ],
                batteryTemp: [
                    /Battery temp[:\s]+(-?\d+\.?\d*)/i,
                    /temp[:\s]+(-?\d+)/i,
                    /电池温度[:\s]+(-?\d+\.?\d*)/i
                ],
                chargeCounter: [
                    /Charge counter[:\s]+(\d+)/i,
                    /charge_counter[:\s]+(\d+)/i,
                    /FCC[:\s]+(\d+)/i,
                    /Full charge capacity[:\s]+(\d+)/i
                ]
            };

            for (const [key, patterns] of Object.entries(hwPatterns)) {
                for (const pattern of patterns) {
                    const match = section.match(pattern);
                    if (match && !result[key]) {
                        let value = parseFloat(match[1]);
                        if (key === 'chargeCounter') {
                            result.chargeCounter = value;
                            // 关键修复：使用统一的单位转换函数
                            result.currentCapacity = this.convertToMah(value);
                            result.confidence = 0.9;
                        } else if (key === 'batteryTemp') {
                            result.batteryTemp = value > 100 ? value / 10 : value;
                        } else {
                            result[key] = value;
                        }
                        break;
                    }
                }
            }
            
            if (!result.rawContent) {
                result.rawContent = section.substring(0, 1500);
            }
        }

        // 华为系统属性中的电池信息
        const hwSysProp = content.match(/hw\.battery[\s\S]*?(?=\nhw\.|$)/i);
        if (hwSysProp) {
            const props = hwSysProp[0];
            
            if (!result.cycleCount) {
                const cycleMatch = props.match(/cycle_count[:\s]+(\d+)/i);
                if (cycleMatch) {
                    result.cycleCount = parseInt(cycleMatch[1]);
                }
            }
        }

        // 计算健康等级
        if (result.cycleCount) {
            result.healthGrade = this.calculateHealthGrade(result.cycleCount);
        }

        result.brand = 'huawei';
        return result;
    },

    /**
     * 三星专用解析器
     * @param {string} content - 文件内容
     * @returns {Object|null} - 解析结果
     */
    parseSamsung(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;

        // One UI特有的电池信息格式
        const samsungSection = content.match(/Battery[\s\S]*?(?=\n\n[A-Z]|\n[A-Z][a-z]+:|$)/i);
        if (samsungSection) {
            const section = samsungSection[0];
            
            // 三星特有的字段
            if (!result.cycleCount) {
                const cycleMatch = section.match(/cycle_count[:\s]+(\d+)/i) ||
                                   section.match(/Cycle[:\s]+(\d+)/i);
                if (cycleMatch) {
                    result.cycleCount = parseInt(cycleMatch[1]);
                }
            }
            
            if (!result.rawContent) {
                result.rawContent = section.substring(0, 1500);
            }
        }

        // 计算健康等级
        if (result.cycleCount) {
            result.healthGrade = this.calculateHealthGrade(result.cycleCount);
        }

        result.brand = 'samsung';
        return result;
    },

    /**
     * 魅族专用解析器
     * @param {string} content - 文件内容
     * @returns {Object|null} - 解析结果
     */
    parseMeizu(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;

        // Flyme特有的电池信息格式
        const meizuSection = content.match(/BatteryInfo[\s\S]*?(?=\n\n|\n[A-Z][a-z]+:|$)/i);
        if (meizuSection) {
            const section = meizuSection[0];
            
            if (!result.cycleCount) {
                const cycleMatch = section.match(/cycle_count[:\s]+(\d+)/i) ||
                                   section.match(/充电循环[:\s]+(\d+)/i);
                if (cycleMatch) {
                    result.cycleCount = parseInt(cycleMatch[1]);
                }
            }
            
            if (!result.rawContent) {
                result.rawContent = section.substring(0, 1500);
            }
        }

        // 计算健康等级
        if (result.cycleCount) {
            result.healthGrade = this.calculateHealthGrade(result.cycleCount);
        }

        result.brand = 'meizu';
        return result;
    },

    /**
     * 努比亚/红魔专用解析器
     * @param {string} content - 文件内容
     * @returns {Object|null} - 解析结果
     */
    parseNubia(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;

        // 红魔特有的电池信息格式
        const nubiaSection = content.match(/Battery[\s\S]*?(?=\n\n[A-Z]|\n[A-Z][a-z]+:|$)/i);
        if (nubiaSection) {
            const section = nubiaSection[0];
            
            if (!result.cycleCount) {
                const cycleMatch = section.match(/cycle_count[:\s]+(\d+)/i) ||
                                   section.match(/Cycle count[:\s]+(\d+)/i);
                if (cycleMatch) {
                    result.cycleCount = parseInt(cycleMatch[1]);
                }
            }
            
            if (!result.rawContent) {
                result.rawContent = section.substring(0, 1500);
            }
        }

        // 计算健康等级
        if (result.cycleCount) {
            result.healthGrade = this.calculateHealthGrade(result.cycleCount);
        }

        result.brand = 'nubia';
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
            case 'iqoo':
                return this.parseVivo(content);
            case 'oppo':
            case 'realme':
                return this.parseOPPO(content);
            case 'huawei':
            case 'honor':
                return this.parseHuawei(content);
            case 'samsung':
                return this.parseSamsung(content);
            case 'meizu':
                return this.parseMeizu(content);
            case 'nubia':
                return this.parseNubia(content);
            default:
                return this.parseGeneric(content);
        }
    },

    /**
     * 使用 BRAND_CONFIG 提取电池数据（新方法）
     * @param {string} text - 文件内容
     * @param {string} brandKey - 品牌key
     * @returns {Object} - 提取的电池数据
     */
    extractBatteryDataWithConfig(text, brandKey) {
        const config = BRAND_CONFIG[brandKey] || BRAND_CONFIG.xiaomi;
        const result = {
            capacity: null,
            designCapacity: null,
            cycleCount: null,
            temperature: null
        };

        // 提取当前容量
        for (let i = 0; i < config.capacityPatterns.length; i++) {
            const match = text.match(config.capacityPatterns[i]);
            if (match) {
                let value = parseInt(match[1]);
                const divisor = config.capacityDivisor[i] || 1;
                value = value / divisor;
                result.capacity = Math.round(value);
                break;
            }
        }

        // 提取设计容量
        for (const pattern of config.designPatterns) {
            const match = text.match(pattern);
            if (match) {
                result.designCapacity = parseInt(match[1]);
                break;
            }
        }

        // 提取循环次数
        for (const pattern of config.cyclePatterns) {
            const match = text.match(pattern);
            if (match) {
                result.cycleCount = parseInt(match[1]);
                break;
            }
        }

        // 提取温度
        for (const pattern of config.tempPatterns) {
            const match = text.match(pattern);
            if (match) {
                let tempValue = parseInt(match[1]);
                result.temperature = tempValue / (config.tempDivisor || 10);
                break;
            }
        }

        return result;
    },

    /**
     * 综合评级算法（新方法）
     * @param {number} healthPercent - 健康度百分比
     * @param {number} cycleCount - 循环次数
     * @param {number} temperature - 温度
     * @returns {Object} - 评级结果
     */
    calculateBatteryGrade(healthPercent, cycleCount, temperature) {
        const weights = {
            health: 0.5,
            cycles: 0.3,
            temperature: 0.2
        };

        // 健康度评分
        let healthScore = healthPercent;

        // 循环次数评分
        let cycleScore = 100;
        if (cycleCount !== null && cycleCount > 0) {
            if (cycleCount <= 300) {
                cycleScore = 100 - (cycleCount / 300) * 10;
            } else if (cycleCount <= 500) {
                cycleScore = 90 - ((cycleCount - 300) / 200) * 20;
            } else if (cycleCount <= 800) {
                cycleScore = 70 - ((cycleCount - 500) / 300) * 30;
            } else {
                cycleScore = Math.max(0, 40 - ((cycleCount - 800) / 200) * 20);
            }
        }

        // 温度评分
        let tempScore = 100;
        if (temperature !== null) {
            if (temperature >= 20 && temperature <= 35) {
                tempScore = 100;
            } else if (temperature < 20) {
                tempScore = Math.max(60, 100 - (20 - temperature) * 2);
            } else if (temperature > 35 && temperature <= 45) {
                tempScore = Math.max(60, 100 - (temperature - 35) * 4);
            } else if (temperature > 45) {
                tempScore = Math.max(20, 60 - (temperature - 45) * 4);
            }
        }

        // 综合评分
        const totalScore = (healthScore * weights.health) +
                           (cycleScore * weights.cycles) +
                           (tempScore * weights.temperature);

        // 评级判定
        let grade, gradeClass, gradeDesc;
        if (totalScore >= 90) {
            grade = 'A';
            gradeClass = 'grade-excellent';
            gradeDesc = '优秀 - 电池状态极佳，可正常长期使用';
        } else if (totalScore >= 80) {
            grade = 'B';
            gradeClass = 'grade-good';
            gradeDesc = '良好 - 电池状态良好，日常使用无影响';
        } else if (totalScore >= 70) {
            grade = 'C';
            gradeClass = 'grade-fair';
            gradeDesc = '一般 - 电池轻度老化，续航略有下降';
        } else if (totalScore >= 60) {
            grade = 'D';
            gradeClass = 'grade-poor';
            gradeDesc = '较差 - 电池明显老化，建议关注续航变化';
        } else {
            grade = 'E';
            gradeClass = 'grade-critical';
            gradeDesc = '严重 - 电池严重老化，建议尽快更换';
        }

        return {
            score: Math.round(totalScore),
            grade: grade,
            gradeClass: gradeClass,
            gradeDesc: gradeDesc,
            healthScore: Math.round(healthScore),
            cycleScore: Math.round(cycleScore),
            tempScore: Math.round(tempScore)
        };
    },

    /**
     * 温度状态判断
     * @param {number} temperature - 温度
     * @returns {Object} - 温度状态
     */
    getTemperatureStatus(temperature) {
        if (temperature === null || temperature === undefined) {
            return { label: '未知', className: '', icon: 'fa-question-circle' };
        }
        if (temperature < 20) {
            return { label: '低温', className: 'temp-status-cold', icon: 'fa-snowflake' };
        }
        if (temperature >= 20 && temperature <= 35) {
            return { label: '正常', className: 'temp-status-normal', icon: 'fa-check-circle' };
        }
        if (temperature > 35 && temperature <= 45) {
            return { label: '偏高', className: 'temp-status-high', icon: 'fa-exclamation-circle' };
        }
        return { label: '危险', className: 'temp-status-danger', icon: 'fa-fire' };
    }
};

// ============================================
// 电池保养建议系统
// ============================================
const MAINTENANCE_ADVICE = {
    A: {
        banner: '您的电池状态极佳，继续保持良好的使用习惯即可',
        charging: [
            { text: '最佳充电区间为20%-80%，可进一步延长电池寿命', icon: 'check-circle' },
            { text: '避免长时间保持100%满电状态', icon: 'check-circle' },
            { text: '使用原装或认证充电器，确保充电稳定', icon: 'check-circle' },
            { text: '夜间充电建议开启智能充电保护功能', icon: 'check-circle' }
        ],
        usage: [
            { text: '避免边充边玩大型游戏或进行高负载操作', icon: 'check-circle' },
            { text: '避免在高温环境（如车内、阳光直射）下使用', icon: 'check-circle' },
            { text: '定期清理后台应用，减少不必要的电量消耗', icon: 'check-circle' },
            { text: '保持系统更新，获取最新的电池管理优化', icon: 'check-circle' }
        ],
        replace: [
            { text: '当前电池状态优秀，无需更换', icon: 'check-circle' },
            { text: '预计仍可正常使用2年以上', icon: 'check-circle' },
            { text: '建议每半年进行一次电池健康检查', icon: 'check-circle' }
        ]
    },
    B: {
        banner: '您的电池状态良好，注意保养可延长使用寿命',
        charging: [
            { text: '最佳充电区间为20%-80%，避免过充过放', icon: 'check-circle' },
            { text: '电量达到80%后建议拔掉充电器', icon: 'info-circle' },
            { text: '避免电量低于20%再充电，及时补电', icon: 'info-circle' },
            { text: '尽量使用慢充，减少快充对电池的损耗', icon: 'info-circle' }
        ],
        usage: [
            { text: '减少边充边玩的频率，尤其是游戏和视频', icon: 'info-circle' },
            { text: '避免手机长时间处于高温环境', icon: 'info-circle' },
            { text: '关闭不必要的定位、蓝牙等功能', icon: 'check-circle' },
            { text: '使用省电模式延长单次续航时间', icon: 'check-circle' }
        ],
        replace: [
            { text: '当前电池状态良好，无需立即更换', icon: 'check-circle' },
            { text: '预计可继续使用1-2年', icon: 'check-circle' },
            { text: '如感觉续航明显下降，可考虑更换', icon: 'info-circle' }
        ]
    },
    C: {
        banner: '您的电池出现轻度老化，需要加强保养并关注续航变化',
        charging: [
            { text: '严格控制充电区间在20%-80%之间', icon: 'exclamation-circle', class: 'warning' },
            { text: '避免过夜充电，充满后及时拔掉', icon: 'exclamation-circle', class: 'warning' },
            { text: '减少快充使用频率，优先使用普通充电', icon: 'info-circle' },
            { text: '充电时取下手机壳，帮助散热', icon: 'info-circle' }
        ],
        usage: [
            { text: '严禁边充边玩，尤其是游戏和高负载应用', icon: 'exclamation-circle', class: 'warning' },
            { text: '避免在高温环境下长时间使用', icon: 'exclamation-circle', class: 'warning' },
            { text: '开启省电模式，限制后台活动', icon: 'info-circle' },
            { text: '降低屏幕亮度和刷新率以节省电量', icon: 'info-circle' }
        ],
        replace: [
            { text: '电池已轻度老化，建议关注续航表现', icon: 'info-circle' },
            { text: '如每日需要多次充电，建议准备备用电池或充电宝', icon: 'info-circle' },
            { text: '预计半年到一年内可能需要更换', icon: 'exclamation-circle', class: 'warning' }
        ]
    },
    D: {
        banner: '您的电池老化明显，建议尽快更换以恢复良好体验',
        charging: [
            { text: '充电区间严格控制在30%-80%', icon: 'exclamation-circle', class: 'warning' },
            { text: '避免完全放电后再充电，随用随充', icon: 'exclamation-circle', class: 'warning' },
            { text: '切勿边充边用，充电时尽量不使用手机', icon: 'exclamation-circle', class: 'danger' },
            { text: '使用原装充电器，避免使用劣质充电设备', icon: 'exclamation-circle', class: 'warning' }
        ],
        usage: [
            { text: '绝对避免边充边玩，防止电池过热', icon: 'exclamation-circle', class: 'danger' },
            { text: '避免在高温环境使用，注意散热', icon: 'exclamation-circle', class: 'danger' },
            { text: '始终开启省电模式，延长使用时间', icon: 'warning' },
            { text: '减少高负载应用使用，降低功耗', icon: 'warning' }
        ],
        replace: [
            { text: '电池已明显老化，建议尽快更换', icon: 'exclamation-circle', class: 'warning' },
            { text: '续航已严重下降，影响日常使用', icon: 'exclamation-circle', class: 'warning' },
            { text: '建议前往官方售后更换原装电池', icon: 'info-circle' },
            { text: '更换后电池健康度可恢复至95%以上', icon: 'check-circle' }
        ]
    },
    E: {
        banner: '您的电池严重老化，请立即更换，避免安全隐患',
        charging: [
            { text: '充电时务必有人看管，避免无人值守', icon: 'exclamation-circle', class: 'danger' },
            { text: '避免过夜充电，充满立即拔掉', icon: 'exclamation-circle', class: 'danger' },
            { text: '切勿边充边用，防止过热引发危险', icon: 'exclamation-circle', class: 'danger' },
            { text: '仅使用原装充电器，确保充电安全', icon: 'exclamation-circle', class: 'danger' }
        ],
        usage: [
            { text: '严禁边充边用，存在安全风险', icon: 'exclamation-circle', class: 'danger' },
            { text: '避免任何高温环境下使用', icon: 'exclamation-circle', class: 'danger' },
            { text: '如电池鼓包或发热异常，立即停止使用', icon: 'exclamation-circle', class: 'danger' },
            { text: '尽量减少使用，准备更换', icon: 'warning' }
        ],
        replace: [
            { text: '电池严重老化，请立即更换', icon: 'exclamation-circle', class: 'danger' },
            { text: '继续使用可能存在安全隐患', icon: 'exclamation-circle', class: 'danger' },
            { text: '建议立即前往官方售后更换', icon: 'exclamation-circle', class: 'danger' },
            { text: '更换后设备续航将大幅改善', icon: 'check-circle' }
        ]
    }
};

// 导出模块
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { BatteryParsers, BRAND_CONFIG, MAINTENANCE_ADVICE };
}