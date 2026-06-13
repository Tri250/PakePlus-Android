/**
 * 电池容量数据库
 * 包含常见安卓手机的电池容量信息
 */

const BatteryDatabase = {
    // 数据库
    data: [
        // 小米/红米系列
        { brand: '小米', model: '14 Ultra', capacity: 5300 },
        { brand: '小米', model: '14 Pro', capacity: 4880 },
        { brand: '小米', model: '14', capacity: 4610 },
        { brand: '小米', model: '13 Ultra', capacity: 5000 },
        { brand: '小米', model: '13 Pro', capacity: 4820 },
        { brand: '小米', model: '13', capacity: 4500 },
        { brand: '小米', model: '12S Ultra', capacity: 4860 },
        { brand: '小米', model: '12S Pro', capacity: 4600 },
        { brand: '小米', model: '12S', capacity: 4500 },
        { brand: '小米', model: '12 Pro', capacity: 4600 },
        { brand: '小米', model: '12', capacity: 4500 },
        { brand: '小米', model: '12X', capacity: 4500 },
        { brand: '小米', model: '11 Ultra', capacity: 5000 },
        { brand: '小米', model: '11 Pro', capacity: 5000 },
        { brand: '小米', model: '11', capacity: 4600 },
        { brand: '小米', model: '11 青春版', capacity: 4250 },
        { brand: '小米', model: '10 Ultra', capacity: 4500 },
        { brand: '小米', model: '10 Pro', capacity: 4500 },
        { brand: '小米', model: '10', capacity: 4780 },
        { brand: '小米', model: '10S', capacity: 4780 },
        { brand: '小米', model: '9 Pro', capacity: 4000 },
        { brand: '小米', model: '9', capacity: 3300 },
        { brand: '小米', model: 'MIX 4', capacity: 4500 },
        { brand: '小米', model: 'MIX Fold 3', capacity: 4800 },
        { brand: '小米', model: 'MIX Fold 2', capacity: 4500 },
        { brand: '小米', model: 'Civi 4 Pro', capacity: 4700 },
        { brand: '小米', model: 'Civi 3', capacity: 4500 },
        
        // 红米系列
        { brand: '红米', model: 'K70 Pro', capacity: 5000 },
        { brand: '红米', model: 'K70', capacity: 5000 },
        { brand: '红米', model: 'K70E', capacity: 5500 },
        { brand: '红米', model: 'K60 Ultra', capacity: 5000 },
        { brand: '红米', model: 'K60 Pro', capacity: 5000 },
        { brand: '红米', model: 'K60', capacity: 5500 },
        { brand: '红米', model: 'K60E', capacity: 5500 },
        { brand: '红米', model: 'K50 Ultra', capacity: 5000 },
        { brand: '红米', model: 'K50 Pro', capacity: 5000 },
        { brand: '红米', model: 'K50', capacity: 5500 },
        { brand: '红米', model: 'K50G', capacity: 4700 },
        { brand: '红米', model: 'K40S', capacity: 4520 },
        { brand: '红米', model: 'K40 Pro', capacity: 4520 },
        { brand: '红米', model: 'K40', capacity: 4520 },
        { brand: '红米', model: 'K40 游戏版', capacity: 5065 },
        { brand: '红米', model: 'Note 13 Pro+', capacity: 5000 },
        { brand: '红米', model: 'Note 13 Pro', capacity: 5100 },
        { brand: '红米', model: 'Note 13', capacity: 5000 },
        { brand: '红米', model: 'Note 12 Pro+', capacity: 5000 },
        { brand: '红米', model: 'Note 12 Pro', capacity: 5000 },
        { brand: '红米', model: 'Note 12', capacity: 5000 },
        { brand: '红米', model: 'Note 12 Turbo', capacity: 5000 },
        { brand: '红米', model: 'Note 11 Pro+', capacity: 4500 },
        { brand: '红米', model: 'Note 11 Pro', capacity: 5160 },
        { brand: '红米', model: 'Note 11', capacity: 5000 },
        { brand: '红米', model: 'Note 10 Pro', capacity: 5020 },
        { brand: '红米', model: 'Note 10', capacity: 5000 },
        { brand: '红米', model: 'Note 9 Pro', capacity: 4820 },
        { brand: '红米', model: 'Note 9', capacity: 5020 },
        { brand: '红米', model: '13C', capacity: 5000 },
        { brand: '红米', model: '12C', capacity: 5000 },
        { brand: '红米', model: '10A', capacity: 5000 },
        { brand: '红米', model: '9A', capacity: 5000 },
        
        // vivo系列
        { brand: 'vivo', model: 'X100 Ultra', capacity: 5500 },
        { brand: 'vivo', model: 'X100 Pro', capacity: 5400 },
        { brand: 'vivo', model: 'X100', capacity: 5000 },
        { brand: 'vivo', model: 'X90 Pro+', capacity: 4700 },
        { brand: 'vivo', model: 'X90 Pro', capacity: 4870 },
        { brand: 'vivo', model: 'X90', capacity: 4810 },
        { brand: 'vivo', model: 'X80 Pro', capacity: 4700 },
        { brand: 'vivo', model: 'X80', capacity: 4500 },
        { brand: 'vivo', model: 'X70 Pro+', capacity: 4500 },
        { brand: 'vivo', model: 'X70 Pro', capacity: 4450 },
        { brand: 'vivo', model: 'X70', capacity: 4400 },
        { brand: 'vivo', model: 'X60 Pro+', capacity: 4200 },
        { brand: 'vivo', model: 'X60 Pro', capacity: 4200 },
        { brand: 'vivo', model: 'X60', capacity: 4300 },
        { brand: 'vivo', model: 'S18 Pro', capacity: 5000 },
        { brand: 'vivo', model: 'S18', capacity: 5000 },
        { brand: 'vivo', model: 'S17 Pro', capacity: 4600 },
        { brand: 'vivo', model: 'S17', capacity: 4600 },
        { brand: 'vivo', model: 'S16 Pro', capacity: 4600 },
        { brand: 'vivo', model: 'S16', capacity: 4600 },
        { brand: 'vivo', model: 'Y100', capacity: 5000 },
        { brand: 'vivo', model: 'Y78+', capacity: 5000 },
        { brand: 'vivo', model: 'Y77', capacity: 4500 },
        { brand: 'vivo', model: 'Y76s', capacity: 4100 },
        { brand: 'vivo', model: 'Y55s', capacity: 6000 },
        { brand: 'vivo', model: 'Y53s', capacity: 5000 },
        { brand: 'vivo', model: 'Y52s', capacity: 5000 },
        
        // iQOO系列
        { brand: 'iQOO', model: '12 Pro', capacity: 5100 },
        { brand: 'iQOO', model: '12', capacity: 5000 },
        { brand: 'iQOO', model: '11S', capacity: 4700 },
        { brand: 'iQOO', model: '11 Pro', capacity: 4700 },
        { brand: 'iQOO', model: '11', capacity: 5000 },
        { brand: 'iQOO', model: '10 Pro', capacity: 4700 },
        { brand: 'iQOO', model: '10', capacity: 4700 },
        { brand: 'iQOO', model: '9 Pro', capacity: 4700 },
        { brand: 'iQOO', model: '9', capacity: 4700 },
        { brand: 'iQOO', model: '8 Pro', capacity: 4500 },
        { brand: 'iQOO', model: '8', capacity: 4350 },
        { brand: 'iQOO', model: '7', capacity: 4000 },
        { brand: 'iQOO', model: 'Neo9 Pro', capacity: 5160 },
        { brand: 'iQOO', model: 'Neo9', capacity: 5160 },
        { brand: 'iQOO', model: 'Neo8 Pro', capacity: 5000 },
        { brand: 'iQOO', model: 'Neo8', capacity: 5000 },
        { brand: 'iQOO', model: 'Neo7', capacity: 5000 },
        { brand: 'iQOO', model: 'Neo6', capacity: 4700 },
        { brand: 'iQOO', model: 'Neo5', capacity: 4400 },
        { brand: 'iQOO', model: 'Z8', capacity: 5000 },
        { brand: 'iQOO', model: 'Z7', capacity: 5000 },
        { brand: 'iQOO', model: 'Z6', capacity: 4500 },
        { brand: 'iQOO', model: 'Z5', capacity: 5000 },
        
        // OPPO系列
        { brand: 'OPPO', model: 'Find X7 Ultra', capacity: 5000 },
        { brand: 'OPPO', model: 'Find X7', capacity: 5000 },
        { brand: 'OPPO', model: 'Find X6 Pro', capacity: 5000 },
        { brand: 'OPPO', model: 'Find X6', capacity: 4800 },
        { brand: 'OPPO', model: 'Find X5 Pro', capacity: 5000 },
        { brand: 'OPPO', model: 'Find X5', capacity: 4800 },
        { brand: 'OPPO', model: 'Find X3 Pro', capacity: 4500 },
        { brand: 'OPPO', model: 'Find X3', capacity: 4500 },
        { brand: 'OPPO', model: 'Reno11 Pro', capacity: 4700 },
        { brand: 'OPPO', model: 'Reno11', capacity: 4800 },
        { brand: 'OPPO', model: 'Reno10 Pro+', capacity: 4700 },
        { brand: 'OPPO', model: 'Reno10 Pro', capacity: 4600 },
        { brand: 'OPPO', model: 'Reno10', capacity: 4600 },
        { brand: 'OPPO', model: 'Reno9 Pro+', capacity: 4700 },
        { brand: 'OPPO', model: 'Reno9 Pro', capacity: 4500 },
        { brand: 'OPPO', model: 'Reno9', capacity: 4500 },
        { brand: 'OPPO', model: 'Reno8 Pro+', capacity: 4500 },
        { brand: 'OPPO', model: 'Reno8 Pro', capacity: 4500 },
        { brand: 'OPPO', model: 'Reno8', capacity: 4500 },
        { brand: 'OPPO', model: 'Reno7 Pro', capacity: 4500 },
        { brand: 'OPPO', model: 'Reno7', capacity: 4500 },
        { brand: 'OPPO', model: 'Reno6 Pro+', capacity: 4500 },
        { brand: 'OPPO', model: 'Reno6 Pro', capacity: 4500 },
        { brand: 'OPPO', model: 'Reno6', capacity: 4300 },
        { brand: 'OPPO', model: 'K11', capacity: 5000 },
        { brand: 'OPPO', model: 'K10', capacity: 5000 },
        { brand: 'OPPO', model: 'K9', capacity: 4300 },
        { brand: 'OPPO', model: 'K7', capacity: 4025 },
        { brand: 'OPPO', model: 'A2 Pro', capacity: 5000 },
        { brand: 'OPPO', model: 'A2', capacity: 5000 },
        { brand: 'OPPO', model: 'A1 Pro', capacity: 4800 },
        { brand: 'OPPO', model: 'A1', capacity: 5000 },
        { brand: 'OPPO', model: 'A97', capacity: 5000 },
        { brand: 'OPPO', model: 'A96', capacity: 4500 },
        { brand: 'OPPO', model: 'A95', capacity: 4310 },
        { brand: 'OPPO', model: 'A93', capacity: 5000 },
        { brand: 'OPPO', model: 'A72', capacity: 4040 },
        { brand: 'OPPO', model: 'A58', capacity: 5000 },
        { brand: 'OPPO', model: 'A57', capacity: 5000 },
        { brand: 'OPPO', model: 'A56', capacity: 5000 },
        { brand: 'OPPO', model: 'A55', capacity: 5000 },
        { brand: 'OPPO', model: 'A53', capacity: 4040 },
        
        // 一加系列
        { brand: '一加', model: '12', capacity: 5400 },
        { brand: '一加', model: '11', capacity: 5000 },
        { brand: '一加', model: '10 Pro', capacity: 5000 },
        { brand: '一加', model: '9 Pro', capacity: 4500 },
        { brand: '一加', model: '9', capacity: 4500 },
        { brand: '一加', model: '9RT', capacity: 4500 },
        { brand: '一加', model: '9R', capacity: 4500 },
        { brand: '一加', model: '8T', capacity: 4500 },
        { brand: '一加', model: '8 Pro', capacity: 4510 },
        { brand: '一加', model: '8', capacity: 4300 },
        { brand: '一加', model: 'Ace 3', capacity: 5500 },
        { brand: '一加', model: 'Ace 2 Pro', capacity: 5000 },
        { brand: '一加', model: 'Ace 2', capacity: 5000 },
        { brand: '一加', model: 'Ace Pro', capacity: 4800 },
        { brand: '一加', model: 'Ace', capacity: 4500 },
        { brand: '一加', model: 'Ace 竞速版', capacity: 5000 },
        
        // realme系列
        { brand: 'realme', model: 'GT5 Pro', capacity: 5400 },
        { brand: 'realme', model: 'GT5', capacity: 5240 },
        { brand: 'realme', model: 'GT Neo6', capacity: 5500 },
        { brand: 'realme', model: 'GT Neo5', capacity: 5000 },
        { brand: 'realme', model: 'GT Neo3', capacity: 5000 },
        { brand: 'realme', model: 'GT2 Pro', capacity: 5000 },
        { brand: 'realme', model: 'GT2', capacity: 5000 },
        { brand: 'realme', model: 'GT', capacity: 4500 },
        { brand: 'realme', model: '12 Pro+', capacity: 5000 },
        { brand: 'realme', model: '12 Pro', capacity: 5000 },
        { brand: 'realme', model: '11 Pro+', capacity: 5000 },
        { brand: 'realme', model: '11 Pro', capacity: 5000 },
        { brand: 'realme', model: '10 Pro+', capacity: 5000 },
        { brand: 'realme', model: '10 Pro', capacity: 5000 },
        { brand: 'realme', model: 'Q5 Pro', capacity: 5000 },
        { brand: 'realme', model: 'Q5', capacity: 5000 },
        { brand: 'realme', model: 'Q3', capacity: 5000 },
        
        // 华为系列
        { brand: '华为', model: 'Mate 60 Pro+', capacity: 5000 },
        { brand: '华为', model: 'Mate 60 Pro', capacity: 5000 },
        { brand: '华为', model: 'Mate 60', capacity: 4750 },
        { brand: '华为', model: 'Mate 50 Pro', capacity: 4700 },
        { brand: '华为', model: 'Mate 50', capacity: 4460 },
        { brand: '华为', model: 'Mate 50E', capacity: 4460 },
        { brand: '华为', model: 'Mate 40 Pro+', capacity: 4400 },
        { brand: '华为', model: 'Mate 40 Pro', capacity: 4400 },
        { brand: '华为', model: 'Mate 40', capacity: 4200 },
        { brand: '华为', model: 'Mate 40E', capacity: 4200 },
        { brand: '华为', model: 'Mate 30 Pro', capacity: 4500 },
        { brand: '华为', model: 'Mate 30', capacity: 4200 },
        { brand: '华为', model: 'P60 Pro', capacity: 4815 },
        { brand: '华为', model: 'P60', capacity: 4815 },
        { brand: '华为', model: 'P60 Art', capacity: 5100 },
        { brand: '华为', model: 'P50 Pro', capacity: 4360 },
        { brand: '华为', model: 'P50', capacity: 4100 },
        { brand: '华为', model: 'P50E', capacity: 4100 },
        { brand: '华为', model: 'P40 Pro+', capacity: 4200 },
        { brand: '华为', model: 'P40 Pro', capacity: 4200 },
        { brand: '华为', model: 'P40', capacity: 3800 },
        { brand: '华为', model: 'nova 12 Pro', capacity: 4600 },
        { brand: '华为', model: 'nova 12', capacity: 4600 },
        { brand: '华为', model: 'nova 12 Ultra', capacity: 4600 },
        { brand: '华为', model: 'nova 11 Pro', capacity: 4500 },
        { brand: '华为', model: 'nova 11', capacity: 4500 },
        { brand: '华为', model: 'nova 10 Pro', capacity: 4500 },
        { brand: '华为', model: 'nova 10', capacity: 4000 },
        { brand: '华为', model: 'nova 9 Pro', capacity: 4000 },
        { brand: '华为', model: 'nova 9', capacity: 4300 },
        { brand: '华为', model: 'nova 8 Pro', capacity: 4000 },
        { brand: '华为', model: 'nova 8', capacity: 3800 },
        { brand: '华为', model: 'nova 7 Pro', capacity: 4000 },
        { brand: '华为', model: 'nova 7', capacity: 4000 },
        { brand: '华为', model: '畅享 60 Pro', capacity: 5000 },
        { brand: '华为', model: '畅享 60', capacity: 6000 },
        { brand: '华为', model: '畅享 50 Pro', capacity: 5000 },
        { brand: '华为', model: '畅享 50', capacity: 6000 },
        { brand: '华为', model: '畅享 20 Pro', capacity: 4000 },
        { brand: '华为', model: '畅享 20', capacity: 5000 },
        { brand: '华为', model: '畅享 Z', capacity: 4000 },
        
        // 荣耀系列
        { brand: '荣耀', model: 'Magic6 Pro', capacity: 5600 },
        { brand: '荣耀', model: 'Magic6', capacity: 5450 },
        { brand: '荣耀', model: 'Magic5 Pro', capacity: 5450 },
        { brand: '荣耀', model: 'Magic5', capacity: 5100 },
        { brand: '荣耀', model: 'Magic5 至臻版', capacity: 5450 },
        { brand: '荣耀', model: 'Magic4 Pro', capacity: 4600 },
        { brand: '荣耀', model: 'Magic4', capacity: 4800 },
        { brand: '荣耀', model: 'Magic4 至臻版', capacity: 4600 },
        { brand: '荣耀', model: 'Magic3 Pro', capacity: 4600 },
        { brand: '荣耀', model: 'Magic3', capacity: 4600 },
        { brand: '荣耀', model: 'Magic3 至臻版', capacity: 4600 },
        { brand: '荣耀', model: 'Magic V2', capacity: 5000 },
        { brand: '荣耀', model: 'Magic Vs', capacity: 5000 },
        { brand: '荣耀', model: '100 Pro', capacity: 5000 },
        { brand: '荣耀', model: '100', capacity: 5000 },
        { brand: '荣耀', model: '90 Pro', capacity: 5000 },
        { brand: '荣耀', model: '90', capacity: 5000 },
        { brand: '荣耀', model: '90 GT', capacity: 5000 },
        { brand: '荣耀', model: '80 Pro', capacity: 4800 },
        { brand: '荣耀', model: '80', capacity: 4800 },
        { brand: '荣耀', model: '80 GT', capacity: 4800 },
        { brand: '荣耀', model: '70 Pro', capacity: 4500 },
        { brand: '荣耀', model: '70', capacity: 4800 },
        { brand: '荣耀', model: '60 Pro', capacity: 4800 },
        { brand: '荣耀', model: '60', capacity: 4800 },
        { brand: '荣耀', model: '50 Pro', capacity: 4000 },
        { brand: '荣耀', model: '50', capacity: 4300 },
        { brand: '荣耀', model: 'X50', capacity: 5800 },
        { brand: '荣耀', model: 'X50i', capacity: 4500 },
        { brand: '荣耀', model: 'X40', capacity: 5100 },
        { brand: '荣耀', model: 'X40i', capacity: 4000 },
        { brand: '荣耀', model: 'X30', capacity: 4800 },
        { brand: '荣耀', model: 'X30i', capacity: 4000 },
        { brand: '荣耀', model: 'X20', capacity: 4300 },
        { brand: '荣耀', model: 'Play 8T', capacity: 6000 },
        { brand: '荣耀', model: 'Play 7T', capacity: 6000 },
        { brand: '荣耀', model: 'Play 6T', capacity: 5000 },
        { brand: '荣耀', model: 'Play 5T', capacity: 5000 },
    ],

    /**
     * 搜索手机型号
     * @param {string} keyword - 搜索关键词
     * @returns {Array} - 匹配的结果
     */
    search(keyword) {
        if (!keyword || keyword.trim() === '') {
            return [];
        }
        
        const lowerKeyword = keyword.toLowerCase().trim();
        
        return this.data.filter(item => {
            const searchText = `${item.brand} ${item.model}`.toLowerCase();
            return searchText.includes(lowerKeyword);
        }).slice(0, 5); // 最多返回5个结果
    },

    /**
     * 根据品牌和型号获取电池容量
     * @param {string} brand - 品牌
     * @param {string} model - 型号
     * @returns {number|null} - 电池容量(mAh)或null
     */
    getCapacity(brand, model) {
        const item = this.data.find(item => 
            item.brand === brand && item.model === model
        );
        return item ? item.capacity : null;
    },

    /**
     * 获取所有品牌列表
     * @returns {Array} - 品牌列表
     */
    getBrands() {
        const brands = new Set(this.data.map(item => item.brand));
        return Array.from(brands);
    },

    /**
     * 获取指定品牌的所有型号
     * @param {string} brand - 品牌
     * @returns {Array} - 型号列表
     */
    getModelsByBrand(brand) {
        return this.data
            .filter(item => item.brand === brand)
            .map(item => item.model);
    }
};

// 导出模块
if (typeof module !== 'undefined' && module.exports) {
    module.exports = BatteryDatabase;
}
