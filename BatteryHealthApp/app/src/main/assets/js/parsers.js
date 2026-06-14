/**
 * 电池信息解析器模块 - 多品牌增强版 (v1.5.0)
 * 真实解析安卓诊断文件中的电池健康度、温度、循环次数
 * 支持小米/红米/澎湃OS、华为/荣耀/HarmonyOS、OPPO/一加/realme/ColorOS/OxygenOS、
 * vivo/iQOO/OriginOS/Funtouch OS、三星/One UI、魅族/Flyme、努比亚/红魔等品牌
 *
 * 各品牌 bugreport 关键字段:
 * - 小米/澎湃OS: MF_05(实际容量mAh) MF_06(设计容量mAh) MF_02(循环) MB_06(健康) fc= cc=
 * - 华为/荣耀:   healthd日志 fc=full charge cc=cycle charge_full charge_full_design
 * - OPPO/一加:   charge_full charge_full_design cycle_count dumpsys battery
 * - vivo/iQOO:   ChargeCounter FCC BatteryInfo
 * - 通用:        dumpsys battery  healthd battery l= v= t= h= st= c= fc= cc=
 */

const BatteryParsers = {
    /**
     * 检测文件品牌类型
     * @param {Array} entries - ZIP文件条目
     * @param {string} content - 文件内容
     * @returns {string} - 品牌名称
     */
    detectBrand(entries, content) {
        const entryNames = entries ? entries.map(e => e.filename.toLowerCase()) : [];
        const contentLower = (content || '').toLowerCase();

        // ========== 根据文件路径特征判断 ==========
        if (entryNames.some(name => name.includes('miui') || name.includes('xiaomi') || name.includes('hyperos'))) {
            return 'xiaomi';
        }
        if (entryNames.some(name => name.includes('vivo') || name.includes('funtouch') || name.includes('originos') || name.includes('iqoo'))) {
            return 'vivo';
        }
        if (entryNames.some(name => name.includes('coloros') || name.includes('oppo') || name.includes('oneplus') || name.includes('oos') || name.includes('realme'))) {
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
        if (entryNames.some(name => name.includes('zte') || name.includes('axon'))) {
            return 'zte';
        }
        if (entryNames.some(name => name.includes('moto') || name.includes('motorola'))) {
            return 'motorola';
        }

        // ========== 根据内容特征判断 ==========
        // 小米/HyperOS
        if (contentLower.includes('miui') || contentLower.includes('hyperos') ||
            contentLower.includes('xiaomi') || contentLower.includes('redmi') ||
            contentLower.includes('mf_05') || contentLower.includes('mf_06') || contentLower.includes('mf_02') || contentLower.includes('mb_06')) {
            return 'xiaomi';
        }
        // 华为/荣耀/HarmonyOS
        if (contentLower.includes('harmonyos') || contentLower.includes('emui') || contentLower.includes('magicui') ||
            contentLower.includes('huawei') || contentLower.includes('honor') || contentLower.includes('hw.battery')) {
            return 'huawei';
        }
        // vivo/iQOO
        if (contentLower.includes('funtouch') || contentLower.includes('originos') ||
            contentLower.includes('vivo ') || contentLower.includes('iqoo') ||
            contentLower.includes('batteryhealthservice')) {
            return 'vivo';
        }
        // OPPO/一加/realme
        if (contentLower.includes('coloros') || contentLower.includes('oxygenos') || contentLower.includes('realmeui') ||
            contentLower.includes('oppo') || contentLower.includes('oneplus') || contentLower.includes('realme')) {
            return 'oppo';
        }
        // 三星
        if (contentLower.includes('samsung') || contentLower.includes('one ui') || contentLower.includes('oneui')) {
            return 'samsung';
        }
        // 魅族
        if (contentLower.includes('flyme') || contentLower.includes('meizu')) {
            return 'meizu';
        }
        // 努比亚/红魔
        if (contentLower.includes('redmagic') || contentLower.includes('nubia')) {
            return 'nubia';
        }

        return 'generic';
    },

    /**
     * 提取 healthd 日志段落
     * 关键格式: healthd: battery l=58 v=3951 t=25.0 h=2 st=3 c=120 fc=2835105 cc=344
     * 适用于小米/华为/荣耀/通用Android
     */
    extractHealthdData(content) {
        const result = {};

        // 关键修复：使用 [\s\S] 匹配跨行
        // 健康度日志匹配：healthd: battery l= v= t= h= st= c= fc= cc=
        const healthdRegex = /healthd[:\s]+battery\s+([^]*?)(?=healthd|$)/gi;
        const matches = content.match(healthdRegex);

        if (matches && matches.length > 0) {
            // 取最后一条（最新数据）
            const lastEntry = matches[matches.length - 1];

            // 解析 l= (level 电量)
            const lMatch = lastEntry.match(/\bl=(-?\d+)/i);
            if (lMatch) result.level = parseInt(lMatch[1]);

            // 解析 v= (voltage 电压 mV)
            const vMatch = lastEntry.match(/\bv=(-?\d+)/i);
            if (vMatch) result.voltage = parseInt(vMatch[1]);

            // 解析 t= (temperature 温度)
            const tMatch = lastEntry.match(/\bt=(-?\d+\.?\d*)/i);
            if (tMatch) result.temp = parseFloat(tMatch[1]);

            // 解析 h= (health)
            const hMatch = lastEntry.match(/\bh=(-?\d+)/i);
            if (hMatch) result.healthCode = parseInt(hMatch[1]);

            // 解析 st= (status)
            const stMatch = lastEntry.match(/\bst=(-?\d+)/i);
            if (stMatch) result.status = parseInt(stMatch[1]);

            // 解析 c= (current 电流)
            const cMatch = lastEntry.match(/\bc=(-?\d+)/i);
            if (cMatch) result.current = parseInt(cMatch[1]);

            // 关键字段：fc= (full charge 满充容量 uAh)
            const fcMatch = lastEntry.match(/\bfc=(-?\d+)/i);
            if (fcMatch && fcMatch[1] !== '0') {
                const fcValue = parseInt(fcMatch[1]);
                if (fcValue > 1000) {
                    result.fullChargeCapacity = fcValue; // 单位 uAh
                    result.fullChargeCapacityMah = Math.round(fcValue / 1000);
                }
            }

            // 关键字段：cc= (cycle count 循环次数 或 charge counter)
            // 注意：在 healthd 日志中 cc 通常是 cycle count
            const ccMatch = lastEntry.match(/\bcc=(-?\d+)/i);
            if (ccMatch && ccMatch[1] !== '0') {
                const ccValue = parseInt(ccMatch[1]);
                // cycle count 通常 < 10000，charge counter 单位 uAh 通常 > 100000
                if (ccValue < 10000) {
                    result.cycleCount = ccValue;
                } else {
                    result.chargeCounter = ccValue;
                }
            }
        }

        return result;
    },

    /**
     * 提取小米/HyperOS 工程代码字段 (MF_xx, MB_xx)
     * MF_05: 当前实际容量 mAh
     * MF_06: 出厂设计容量 mAh
     * MF_02: 电池循环次数
     * MB_06: 电池健康百分比
     */
    extractXiaomiMFData(content) {
        const result = {};

        // MF_05: 当前实际容量 (mAh)
        const mf05Match = content.match(/\bMF_05\s*[:=]\s*(\d+)/);
        if (mf05Match) {
            result.fullChargeCapacity = parseInt(mf05Match[1]);
        }

        // MF_06: 设计容量 (mAh)
        const mf06Match = content.match(/\bMF_06\s*[:=]\s*(\d+)/);
        if (mf06Match) {
            result.designCapacity = parseInt(mf06Match[1]);
        }

        // MF_02: 循环次数
        const mf02Match = content.match(/\bMF_02\s*[:=]\s*(\d+)/);
        if (mf02Match) {
            result.cycleCount = parseInt(mf02Match[1]);
        }

        // MB_06: 电池健康百分比
        const mb06Match = content.match(/\bMB_06\s*[:=]\s*(\d+)/);
        if (mb06Match) {
            result.healthPercent = parseInt(mb06Match[1]);
        }

        // MB_00: 电池状态
        const mb00Match = content.match(/\bMB_00\s*[:=]\s*(\w+)/);
        if (mb00Match) {
            result.healthStatus = mb00Match[1];
        }

        return result;
    },

    /**
     * 提取 dumpsys battery 输出
     * 通用Android dumpsys格式: Current Battery Service state
     */
    extractDumpsysBattery(content) {
        const result = {};

        // 查找 dumpsys battery 段落
        const dumpsysSection = content.match(/DUMP\s+OF\s+SERVICE\s+batterystats[\s\S]*?(?=DUMP\s+OF\s+SERVICE|$)/i) ||
                              content.match(/Current\s+Battery\s+Service\s+state[\s\S]*?(?=\n\n|\nDUMP|$)/i) ||
                              content.match(/Battery\s+Service[\s\S]*?(?=\n\n|\nDUMP|$)/i);

        if (!dumpsysSection) return result;

        const section = dumpsysSection[0];

        // Charge counter (uAh)
        const ccMatch = section.match(/Charge\s*counter[:\s]+(\d+)/i);
        if (ccMatch) {
            const v = parseInt(ccMatch[1]);
            if (v > 1000) {
                result.chargeCounter = v;
            }
        }

        // Cycle count
        const cycleMatch = section.match(/Cycle\s*count[:\s]+(\d+)/i);
        if (cycleMatch) {
            result.cycleCount = parseInt(cycleMatch[1]);
        }

        // Temperature
        const tempMatch = section.match(/Temperature[:\s]+(\d+)/i);
        if (tempMatch) {
            let t = parseInt(tempMatch[1]);
            if (t > 100) t = t / 10;
            result.temp = t;
        }

        // Voltage
        const voltMatch = section.match(/Voltage[:\s]+(\d+)/i);
        if (voltMatch) {
            let v = parseInt(voltMatch[1]);
            if (v > 10000) v = v / 1000;
            result.voltage = v;
        }

        // Current now
        const currentMatch = section.match(/Current\s*now[:\s]+(-?\d+)/i);
        if (currentMatch) {
            result.current = parseInt(currentMatch[1]);
        }

        // Charge type
        const chgTypeMatch = section.match(/Charge\s*type[:\s]+(\w+)/i);
        if (chgTypeMatch) {
            result.chargeType = chgTypeMatch[1];
        }

        // Health
        const healthMatch = section.match(/Health[:\s]+(\w+)/i);
        if (healthMatch) {
            result.health = healthMatch[1];
        }

        return result;
    },

    /**
     * 提取华为/荣耀 HW 字段
     * charge_full, charge_full_design, charge_cycle
     */
    extractHuaweiData(content) {
        const result = {};

        // 华为POWER_SUPPLY格式
        // charge_full: 满充容量 uAh
        const chargeFullMatch = content.match(/charge_full[:\s]+(\d+)/i);
        if (chargeFullMatch) {
            const v = parseInt(chargeFullMatch[1]);
            if (v > 1000) {
                result.chargeCounter = v;
                result.fullChargeCapacity = v;
            }
        }

        // charge_full_design: 设计容量 uAh
        const chargeFullDesignMatch = content.match(/charge_full_design[:\s]+(\d+)/i);
        if (chargeFullDesignMatch) {
            const v = parseInt(chargeFullDesignMatch[1]);
            if (v > 1000) {
                result.designCapacity = v;
            }
        }

        // charge_cycle: 循环次数
        const chargeCycleMatch = content.match(/charge_cycle[:\s]+(\d+)/i);
        if (chargeCycleMatch) {
            result.cycleCount = parseInt(chargeCycleMatch[1]);
        }

        // 荣耀的 betteryfull (实际是 batteryfull 笔误)
        const betteryFullMatch = content.match(/betteryfull[:\s]+(\d+)/i);
        if (betteryFullMatch) {
            const v = parseInt(betteryFullMatch[1]);
            if (v > 100) {
                result.chargeCounter = v;
                result.fullChargeCapacity = v;
            }
        }

        // capacity (%)
        const capacityMatch = content.match(/capacity[:\s]+(\d+)/i);
        if (capacityMatch) {
            const v = parseInt(capacityMatch[1]);
            if (v >= 0 && v <= 100) {
                result.capacity = v;
            }
        }

        // battery_temp
        const batteryTempMatch = content.match(/battery_temp[:\s]+(\d+)/i);
        if (batteryTempMatch) {
            let t = parseInt(batteryTempMatch[1]);
            if (t > 100) t = t / 10;
            result.temp = t;
        }

        return result;
    },

    /**
     * 提取 OPPO/一加/realme 字段
     */
    extractOppoData(content) {
        const result = {};

        // charge_full: 满充容量
        const chargeFullMatch = content.match(/charge_full[:\s]+(\d+)/i);
        if (chargeFullMatch) {
            const v = parseInt(chargeFullMatch[1]);
            if (v > 1000) {
                result.chargeCounter = v;
                result.fullChargeCapacity = v;
            }
        }

        // charge_full_design: 设计容量
        const chargeFullDesignMatch = content.match(/charge_full_design[:\s]+(\d+)/i);
        if (chargeFullDesignMatch) {
            const v = parseInt(chargeFullDesignMatch[1]);
            if (v > 1000) {
                result.designCapacity = v;
            }
        }

        // cycle_count
        const cycleMatch = content.match(/cycle_count[:\s]+(\d+)/i);
        if (cycleMatch) {
            result.cycleCount = parseInt(cycleMatch[1]);
        }

        // FCC: 满充容量缩写
        const fccMatch = content.match(/\bFCC[:\s]+(\d+)/i);
        if (fccMatch && !result.chargeCounter) {
            const v = parseInt(fccMatch[1]);
            if (v > 1000) {
                result.chargeCounter = v;
            }
        }

        // DesignCapacity
        const designMatch = content.match(/\bDesignCapacity[:\s]+(\d+)/i);
        if (designMatch && !result.designCapacity) {
            result.designCapacity = parseInt(designMatch[1]);
        }

        // FullChargeCapacity
        const fccMatch2 = content.match(/\bFullChargeCapacity[:\s]+(\d+)/i);
        if (fccMatch2 && !result.chargeCounter) {
            const v = parseInt(fccMatch2[1]);
            if (v > 100) {
                result.chargeCounter = v;
            }
        }

        return result;
    },

    /**
     * 通用解析器 - 从Android系统标准电池属性文件解析
     * @param {string} content - 文件内容
     * @returns {Object|null} - 解析结果
     */
    parseGeneric(content) {
        if (!content || content.length < 100) return null;

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
            // 扩展字段
            fullChargeCapacity: null,    // 满充容量 (uAh)
            fullChargeCapacityMah: null, // 满充容量 (mAh)
            designCapacity: null,        // 设计容量 (uAh 或 mAh)
            healthPercent: null,         // 健康度百分比
            rawContent: null,
            confidence: 0,
            healthGrade: null,
            debugInfo: null,
            brand: 'generic'
        };

        // ========== 第一步：提取各种来源的数据 ==========

        // 1. healthd 日志（小米/华为/通用）
        const healthdData = this.extractHealthdData(content);

        // 2. 小米 MF_xx MB_xx 字段
        const xiaomiData = this.extractXiaomiMFData(content);

        // 3. dumpsys battery 输出
        const dumpsysData = this.extractDumpsysBattery(content);

        // 4. 华为/荣耀 HW 字段
        const huaweiData = this.extractHuaweiData(content);

        // 5. OPPO/一加字段
        const oppoData = this.extractOppoData(content);

        // ========== 第二步：合并数据（按优先级） ==========

        // 满充容量
        if (xiaomiData.fullChargeCapacity) {
            result.fullChargeCapacityMah = xiaomiData.fullChargeCapacity;
            result.fullChargeCapacity = xiaomiData.fullChargeCapacity * 1000; // 转换为 uAh
        } else if (healthdData.fullChargeCapacity) {
            result.fullChargeCapacity = healthdData.fullChargeCapacity;
            result.fullChargeCapacityMah = healthdData.fullChargeCapacityMah;
        } else if (huaweiData.fullChargeCapacity) {
            result.fullChargeCapacity = huaweiData.fullChargeCapacity;
            result.fullChargeCapacityMah = Math.round(huaweiData.fullChargeCapacity / 1000);
        } else if (oppoData.fullChargeCapacity) {
            result.fullChargeCapacity = oppoData.fullChargeCapacity;
            result.fullChargeCapacityMah = Math.round(oppoData.fullChargeCapacity / 1000);
        } else if (dumpsysData.chargeCounter) {
            result.chargeCounter = dumpsysData.chargeCounter;
            result.fullChargeCapacity = dumpsysData.chargeCounter;
            result.fullChargeCapacityMah = Math.round(dumpsysData.chargeCounter / 1000);
        }

        // chargeCounter
        if (!result.chargeCounter) {
            if (dumpsysData.chargeCounter) {
                result.chargeCounter = dumpsysData.chargeCounter;
            } else if (healthdData.chargeCounter) {
                result.chargeCounter = healthdData.chargeCounter;
            } else if (huaweiData.chargeCounter) {
                result.chargeCounter = huaweiData.chargeCounter;
            } else if (oppoData.chargeCounter) {
                result.chargeCounter = oppoData.chargeCounter;
            }
        }

        // 设计容量
        if (xiaomiData.designCapacity) {
            result.designCapacity = xiaomiData.designCapacity;
        } else if (huaweiData.designCapacity) {
            result.designCapacity = huaweiData.designCapacity;
        } else if (oppoData.designCapacity) {
            result.designCapacity = oppoData.designCapacity;
        }

        // 循环次数
        if (xiaomiData.cycleCount !== undefined) {
            result.cycleCount = xiaomiData.cycleCount;
        } else if (healthdData.cycleCount !== undefined) {
            result.cycleCount = healthdData.cycleCount;
        } else if (dumpsysData.cycleCount !== undefined) {
            result.cycleCount = dumpsysData.cycleCount;
        } else if (huaweiData.cycleCount !== undefined) {
            result.cycleCount = huaweiData.cycleCount;
        } else if (oppoData.cycleCount !== undefined) {
            result.cycleCount = oppoData.cycleCount;
        }

        // 健康度百分比（小米 MB_06）
        if (xiaomiData.healthPercent) {
            result.healthPercent = xiaomiData.healthPercent;
        }

        // 电池电量百分比
        if (healthdData.level !== undefined) {
            result.capacity = healthdData.level;
        } else if (dumpsysData.capacity !== undefined) {
            result.capacity = dumpsysData.capacity;
        } else if (huaweiData.capacity !== undefined) {
            result.capacity = huaweiData.capacity;
        }

        // 温度
        if (healthdData.temp !== undefined) {
            result.batteryTemp = healthdData.temp;
        } else if (dumpsysData.temp !== undefined) {
            result.batteryTemp = dumpsysData.temp;
        } else if (huaweiData.temp !== undefined) {
            result.batteryTemp = huaweiData.temp;
        }

        // 电压（转换为mV）
        if (healthdData.voltage !== undefined) {
            result.voltage = healthdData.voltage;
        } else if (dumpsysData.voltage !== undefined) {
            result.voltage = dumpsysData.voltage;
        }

        // 电流
        if (healthdData.current !== undefined) {
            result.currentNow = healthdData.current;
        } else if (dumpsysData.current !== undefined) {
            result.currentNow = dumpsysData.current;
        }

        // 健康状态
        if (dumpsysData.health) {
            result.health = dumpsysData.health;
        } else if (healthdData.healthCode !== undefined) {
            // h=2 = GOOD, h=3 = OVERHEAT 等
            const healthMap = { 1: 'unknown', 2: 'good', 3: 'overheat', 4: 'dead', 5: 'overvoltage', 6: 'failure' };
            result.health = healthMap[healthdData.healthCode] || 'unknown';
        }

        // ========== 第三步：兜底正则匹配 ==========

        // 1. charge_counter (兼容 dumpsys 格式)
        if (!result.chargeCounter) {
            const ccPatterns = [
                /charge[_\s-]?counter[:\s]+(\d+)\s*uah/i,
                /CHARGE_COUNTER[:\s]+(\d+)/i,
                /cc[:\s]+(\d+)\s*uah/i,
                /last[_\s-]?full[_\s-]?charge[_\s-]?counter[:\s]+(\d+)/i,
                /full[_\s-]?charge[_\s-]?capacity[:\s]+(\d+)\s*uah/i,
                /fcc[:\s]+(\d+)/i,
                /\bcharge_counter\b[\s:=]+(\d+)/i,
                /\/sys\/class\/power_supply\/[^\/]+\/charge_counter[^\d]{0,50}(\d{4,})/i
            ];
            for (const pattern of ccPatterns) {
                const match = content.match(pattern);
                if (match) {
                    const v = parseInt(match[1]);
                    if (v > 1000 && v < 10000000) {
                        result.chargeCounter = v;
                        break;
                    }
                }
            }
        }

        // 2. cycle_count
        if (!result.cycleCount) {
            const cyclePatterns = [
                /cycle[_\s-]?count[:\s]+(\d+)/i,
                /CYCLE_COUNT[:\s]+(\d+)/i,
                /charge[_\s-]?cycle[:\s]+(\d+)/i,
                /battery[_\s-]?cycle[:\s]+(\d+)/i,
                /cycle[_\s-]?counter[:\s]+(\d+)/i,
                /charge[_\s-]?cycles[:\s]+(\d+)/i,
                /充电循环次数[:\s]+(\d+)/i,
                /循环次数[:\s]+(\d+)/i,
                /累计循环[:\s]+(\d+)/i,
                /\bcycle_count\b[\s:=]+(\d+)/i,
                /\/sys\/class\/power_supply\/[^\/]+\/cycle_count[^\d]{0,50}(\d{1,4})/i
            ];
            for (const pattern of cyclePatterns) {
                const match = content.match(pattern);
                if (match) {
                    const v = parseInt(match[1]);
                    if (v >= 0 && v < 10000) {
                        result.cycleCount = v;
                        break;
                    }
                }
            }
        }

        // 3. temperature
        if (!result.batteryTemp) {
            const tempPatterns = [
                /temperature[:\s]+(-?\d+\.?\d*)\s*°c/i,
                /temperature[:\s]+(-?\d+\.?\d*)/i,
                /TEMP[:\s]+(-?\d+)/i,
                /battery[_\s-]?temp[:\s]+(-?\d+\.?\d*)/i,
                /temp[:\s]+(-?\d+\.?\d*)(?!\s*%)/i,
                /电池温度[:\s]+(-?\d+\.?\d*)/i,
                /温度[:\s]+(-?\d+\.?\d*)/i,
                /\btemperature\b[\s:=]+(-?\d+)/i,
                /\btemp\b[\s:=]+(-?\d+)/i
            ];
            for (const pattern of tempPatterns) {
                const match = content.match(pattern);
                if (match) {
                    let t = parseFloat(match[1]);
                    if (t > 100 && t < 1000) t = t / 10;
                    if (t > 1000 && t < 10000) t = t / 100;
                    if (t >= -20 && t <= 80) {
                        result.batteryTemp = t;
                        break;
                    }
                }
            }
        }

        // 4. voltage
        if (!result.voltage) {
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
                    let v = parseFloat(match[1]);
                    if (v > 10000) v = v / 1000; // uV -> mV
                    if (v >= 2500 && v <= 5000) {
                        result.voltage = v;
                        break;
                    }
                }
            }
        }

        // ========== 第四步：计算 currentCapacity ==========

        // 优先级：
        // 1. 小米 MF_05 (mAh) - 最准确
        // 2. healthd fc= (uAh / 1000)
        // 3. dumpsys chargeCounter (uAh / 1000)
        // 4. 华为 charge_full (uAh / 1000)
        // 5. OPPO charge_full (uAh / 1000)

        if (result.fullChargeCapacityMah) {
            result.currentCapacity = result.fullChargeCapacityMah;
            result.confidence = 0.95;
        } else if (result.chargeCounter) {
            result.currentCapacity = Math.round(result.chargeCounter / 1000);
            result.confidence = 0.9;
        } else if (result.fullChargeCapacity) {
            result.currentCapacity = Math.round(result.fullChargeCapacity / 1000);
            result.confidence = 0.85;
        }

        // 如果都没有，尝试最后的兜底
        if (!result.currentCapacity) {
            const fccMatch = content.match(/(?:full[_\s-]?charge[_\s-]?capacity|fcc|design[_\s-]?capacity)[:\s]+(\d+)\s*mah/i);
            if (fccMatch) {
                result.currentCapacity = parseInt(fccMatch[1]);
                result.confidence = 0.7;
            }
        }

        // ========== 第五步：计算健康等级 ==========
        if (result.cycleCount) {
            result.healthGrade = this.calculateHealthGrade(result.cycleCount);
        } else if (result.healthPercent) {
            // 某些品牌（如小米）直接提供 healthPercent
            const hp = result.healthPercent;
            if (hp >= 90) {
                result.healthGrade = { grade: 'A+', estimatedHealth: '95-100%', description: '电池状态极佳' };
            } else if (hp >= 80) {
                result.healthGrade = { grade: 'A', estimatedHealth: '90-95%', description: '电池状态优秀' };
            } else if (hp >= 70) {
                result.healthGrade = { grade: 'B', estimatedHealth: '80-90%', description: '电池状态正常' };
            } else if (hp >= 60) {
                result.healthGrade = { grade: 'C', estimatedHealth: '70-80%', description: '电池轻度老化' };
            } else {
                result.healthGrade = { grade: 'D', estimatedHealth: '<70%', description: '电池严重老化' };
            }
        }

        // ========== 第六步：保存原始内容 ==========
        const batteryMatch = content.match(/Battery[\s\S]{0,2000}?(?=\n\n[A-Z]|\n[A-Z][a-z]+:|$)/i);
        if (batteryMatch) {
            result.rawContent = batteryMatch[0];
        } else {
            // 保存包含关键数据的一小段
            const healthdIdx = content.indexOf('healthd: battery');
            if (healthdIdx >= 0) {
                result.rawContent = content.substring(healthdIdx, healthdIdx + 1500);
            }
        }

        return result.currentCapacity ? result : null;
    },

    /**
     * 根据循环次数计算健康等级
     */
    calculateHealthGrade(cycleCount) {
        let grade, color, description, estimatedHealth;

        if (cycleCount <= 100) {
            grade = 'A+'; color = '#2ecc71';
            description = '电池状态极佳，几乎无损耗'; estimatedHealth = '95-100%';
        } else if (cycleCount <= 200) {
            grade = 'A'; color = '#27ae60';
            description = '电池状态优秀，轻微使用'; estimatedHealth = '90-95%';
        } else if (cycleCount <= 300) {
            grade = 'B+'; color = '#3498db';
            description = '电池状态良好，正常使用'; estimatedHealth = '85-90%';
        } else if (cycleCount <= 500) {
            grade = 'B'; color = '#2980b9';
            description = '电池状态正常，常规老化'; estimatedHealth = '80-85%';
        } else if (cycleCount <= 700) {
            grade = 'C'; color = '#f39c12';
            description = '电池轻度老化，续航下降'; estimatedHealth = '75-80%';
        } else if (cycleCount <= 900) {
            grade = 'D'; color = '#e67e22';
            description = '电池中度老化，建议关注'; estimatedHealth = '70-75%';
        } else {
            grade = 'E'; color = '#e74c3c';
            description = '电池严重老化，建议更换'; estimatedHealth = '<70%';
        }

        return { grade, color, description, estimatedHealth, cycleCount };
    },

    /**
     * 小米专用解析器
     */
    parseXiaomi(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;
        result.brand = 'xiaomi';
        return result;
    },

    /**
     * 华为/荣耀专用解析器
     * 关键：提取 healthd 日志中的 fc= (满充容量) 和 cc= (循环次数)
     */
    parseHuawei(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;
        result.brand = 'huawei';
        return result;
    },

    /**
     * vivo/iQOO专用解析器
     */
    parseVivo(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;
        result.brand = 'vivo';
        return result;
    },

    /**
     * OPPO/一加/realme专用解析器
     */
    parseOPPO(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;
        result.brand = 'oppo';
        return result;
    },

    /**
     * 三星专用解析器
     */
    parseSamsung(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;
        result.brand = 'samsung';
        return result;
    },

    /**
     * 魅族专用解析器
     */
    parseMeizu(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;
        result.brand = 'meizu';
        return result;
    },

    /**
     * 努比亚/红魔专用解析器
     */
    parseNubia(content) {
        const result = this.parseGeneric(content);
        if (!result) return null;
        result.brand = 'nubia';
        return result;
    },

    /**
     * 主解析函数
     */
    parse(content, brand = 'generic') {
        const brandLower = (brand || '').toLowerCase();
        switch (brandLower) {
            case 'xiaomi':
            case 'mi':
            case 'redmi':
            case 'poco':
                return this.parseXiaomi(content);
            case 'vivo':
            case 'iqoo':
                return this.parseVivo(content);
            case 'oppo':
            case 'realme':
            case 'oneplus':
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
    }
};

if (typeof module !== 'undefined' && module.exports) {
    module.exports = BatteryParsers;
}
