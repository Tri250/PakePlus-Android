/**
 * GEO 搜索优化引擎
 * 2026 新增核心模块 - 针对 AI 搜索引擎优化
 * 
 * 痛点：用户使用豆包/ChatGPT/微信AI 搜索"附近华为体验店哪家服务好"
 * 不做 GEO 优化的门店在 AI 搜索结果中不可见
 */

import { safeLocalStorageGet, safeLocalStorageSet } from './env';
import { mapService } from './mapService';

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type AISearchPlatform = 'doubao' | 'chatgpt' | 'wechat_ai' | 'tencent_yuanbao' | 'deepseek' | 'kimi';

export interface BrandKeyword {
  id: string;
  keyword: string;
  category: 'tradein' | 'service' | 'accessory' | 'repair' | 'consult';
  priority: number;
  searchVolume?: number;
  rankPosition?: number;
  lastChecked?: string;
}

export interface StoreDescription {
  storeId: string;
  storeName: string;
  address: string;
  phone?: string;
  businessHours: string;
  services: string[];
  features: string[];
  reviews: { rating: number; count: number; highlights: string[] };
  geoLocation: { lat: number; lng: number };
  structuredContent: string;
}

export interface AISearchRankResult {
  platform: AISearchPlatform;
  keyword: string;
  rankPosition: number | null;
  isVisible: boolean;
  competitors: string[];
  checkedAt: string;
}

export interface AttributionRecord {
  id: string;
  customerId: string;
  channel: 'ai_search' | 'map' | 'xiaohongshu' | 'douyin' | 'wecom' | 'offline' | 'other';
  platform?: AISearchPlatform;
  searchQuery?: string;
  storeId: string;
  timestamp: string;
  notes?: string;
}

export interface GEORankingReport {
  id: string;
  storeId: string;
  date: string;
  totalKeywords: number;
  visibleKeywords: number;
  averageRank: number;
  platformResults: AISearchRankResult[];
  recommendations: string[];
}

/* -------------------------------------------------------------------------- */
/*  品牌关键词矩阵                                                              */
/* -------------------------------------------------------------------------- */

const DEFAULT_KEYWORDS: BrandKeyword[] = [
  // 以旧换新
  { id: 'kw-1', keyword: '华为以旧换新', category: 'tradein', priority: 10 },
  { id: 'kw-2', keyword: '小米以旧换新', category: 'tradein', priority: 10 },
  { id: 'kw-3', keyword: 'OPPO以旧换新', category: 'tradein', priority: 10 },
  { id: 'kw-4', keyword: 'vivo以旧换新', category: 'tradein', priority: 10 },
  { id: 'kw-5', keyword: '手机以旧换新哪家好', category: 'tradein', priority: 9 },
  { id: 'kw-6', keyword: '以旧换新划算的手机店', category: 'tradein', priority: 9 },
  
  // 服务
  { id: 'kw-7', keyword: '华为体验店服务', category: 'service', priority: 8 },
  { id: 'kw-8', keyword: '小米之家服务', category: 'service', priority: 8 },
  { id: 'kw-9', keyword: '手机数据迁移服务', category: 'service', priority: 7 },
  { id: 'kw-10', keyword: '手机贴膜哪家好', category: 'service', priority: 7 },
  
  // 配件
  { id: 'kw-11', keyword: '华为手机壳', category: 'accessory', priority: 6 },
  { id: 'kw-12', keyword: '小米手机配件', category: 'accessory', priority: 6 },
  { id: 'kw-13', keyword: '原装充电器购买', category: 'accessory', priority: 5 },
  
  // 维修
  { id: 'kw-14', keyword: '华为碎屏维修', category: 'repair', priority: 8 },
  { id: 'kw-15', keyword: '手机屏幕维修', category: 'repair', priority: 7 },
  { id: 'kw-16', keyword: '手机电池更换', category: 'repair', priority: 7 },
  
  // 咨询
  { id: 'kw-17', keyword: '附近华为体验店', category: 'consult', priority: 10 },
  { id: 'kw-18', keyword: '附近手机店哪家好', category: 'consult', priority: 9 },
  { id: 'kw-19', keyword: '买手机去哪家店靠谱', category: 'consult', priority: 8 },
];

/* -------------------------------------------------------------------------- */
/*  中国行政区划数据（5级）                                                       */
/* -------------------------------------------------------------------------- */

const CHINA_REGIONS = {
  // 省级（直辖市、省、自治区）
  provinces: [
    { code: '110000', name: '北京市', level: 1 },
    { code: '120000', name: '天津市', level: 1 },
    { code: '310000', name: '上海市', level: 1 },
    { code: '440000', name: '广东省', level: 1 },
    { code: '330000', name: '浙江省', level: 1 },
    { code: '320000', name: '江苏省', level: 1 },
    { code: '510000', name: '四川省', level: 1 },
    { code: '420000', name: '湖北省', level: 1 },
    { code: '430000', name: '湖南省', level: 1 },
    { code: '410000', name: '河南省', level: 1 },
    { code: '370000', name: '山东省', level: 1 },
    { code: '350000', name: '福建省', level: 1 },
    { code: '360000', name: '江西省', level: 1 },
    { code: '340000', name: '安徽省', level: 1 },
    { code: '610000', name: '陕西省', level: 1 },
    { code: '530000', name: '云南省', level: 1 },
    { code: '520000', name: '贵州省', level: 1 },
    { code: '500000', name: '重庆市', level: 1 },
    { code: '230000', name: '黑龙江省', level: 1 },
    { code: '220000', name: '吉林省', level: 1 },
    { code: '210000', name: '辽宁省', level: 1 },
    { code: '140000', name: '山西省', level: 1 },
    { code: '150000', name: '内蒙古自治区', level: 1 },
    { code: '450000', name: '广西壮族自治区', level: 1 },
    { code: '460000', name: '海南省', level: 1 },
    { code: '620000', name: '甘肃省', level: 1 },
    { code: '630000', name: '青海省', level: 1 },
    { code: '640000', name: '宁夏回族自治区', level: 1 },
    { code: '650000', name: '新疆维吾尔自治区', level: 1 },
    { code: '540000', name: '西藏自治区', level: 1 },
  ],
  
  // 重点城市（地级市）
  cities: [
    { code: '110100', name: '北京市', provinceCode: '110000', level: 2 },
    { code: '120100', name: '天津市', provinceCode: '120000', level: 2 },
    { code: '310100', name: '上海市', provinceCode: '310000', level: 2 },
    { code: '440100', name: '广州市', provinceCode: '440000', level: 2 },
    { code: '440300', name: '深圳市', provinceCode: '440000', level: 2 },
    { code: '441900', name: '东莞市', provinceCode: '440000', level: 2 },
    { code: '330100', name: '杭州市', provinceCode: '330000', level: 2 },
    { code: '330200', name: '宁波市', provinceCode: '330000', level: 2 },
    { code: '320100', name: '南京市', provinceCode: '320000', level: 2 },
    { code: '320500', name: '苏州市', provinceCode: '320000', level: 2 },
    { code: '510100', name: '成都市', provinceCode: '510000', level: 2 },
    { code: '420100', name: '武汉市', provinceCode: '420000', level: 2 },
    { code: '430100', name: '长沙市', provinceCode: '430000', level: 2 },
    { code: '410100', name: '郑州市', provinceCode: '410000', level: 2 },
    { code: '370100', name: '济南市', provinceCode: '370000', level: 2 },
    { code: '370200', name: '青岛市', provinceCode: '370000', level: 2 },
    { code: '350100', name: '福州市', provinceCode: '350000', level: 2 },
    { code: '350200', name: '厦门市', provinceCode: '350000', level: 2 },
    { code: '500100', name: '重庆市', provinceCode: '500000', level: 2 },
    { code: '610100', name: '西安市', provinceCode: '610000', level: 2 },
  ],
  
  // 区县级（重点商圈）
  districts: [
    { code: '110105', name: '朝阳区', cityCode: '110100', level: 3 },
    { code: '110108', name: '海淀区', cityCode: '110100', level: 3 },
    { code: '310115', name: '浦东新区', cityCode: '310100', level: 3 },
    { code: '440103', name: '荔湾区', cityCode: '440100', level: 3 },
    { code: '440304', name: '南山区', cityCode: '440300', level: 3 },
    { code: '330102', name: '上城区', cityCode: '330100', level: 3 },
    { code: '320505', name: '虎丘区', cityCode: '320500', level: 3 },
    { code: '510104', name: '锦江区', cityCode: '510100', level: 3 },
  ],
};

/* -------------------------------------------------------------------------- */
/*  GEO 搜索优化引擎服务                                                         */
/* -------------------------------------------------------------------------- */

class GEOOptimizationEngine {
  private keywords: Map<string, BrandKeyword> = new Map();
  private attributions: AttributionRecord[] = [];
  
  constructor() {
    // 初始化关键词库
    DEFAULT_KEYWORDS.forEach(kw => {
      this.keywords.set(kw.id, kw);
    });
    
    // 加载缓存数据
    this.loadFromCache();
  }

  /**
   * 获取所有品牌关键词
   */
  getKeywords(category?: BrandKeyword['category']): BrandKeyword[] {
    const all = Array.from(this.keywords.values());
    if (category) {
      return all.filter(kw => kw.category === category);
    }
    return all.sort((a, b) => b.priority - a.priority);
  }

  /**
   * 添加自定义关键词
   */
  addKeyword(keyword: Omit<BrandKeyword, 'id'>): BrandKeyword {
    const id = `kw-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`;
    const newKeyword: BrandKeyword = { ...keyword, id };
    this.keywords.set(id, newKeyword);
    this.saveToCache();
    return newKeyword;
  }

  /**
   * 生成结构化门店描述（符合 AI 搜索引擎抓取规范）
   */
  generateStoreDescription(store: {
    id: string;
    name: string;
    address: string;
    phone?: string;
    lat: number;
    lng: number;
    services?: string[];
    businessHours?: string;
  }): StoreDescription {
    const services = store.services || [
      '以旧换新',
      '手机维修',
      '数据迁移',
      '贴膜服务',
      '配件销售',
    ];
    
    const features = [
      '官方授权门店',
      '正品保障',
      '专业服务团队',
      '现场维修',
    ];
    
    const reviews = {
      rating: 4.8,
      count: 1280,
      highlights: [
        '服务态度好',
        '价格透明',
        '维修速度快',
        '以旧换新划算',
      ],
    };
    
    // 生成结构化内容（符合 AI 搜索引擎优先抓取规范）
    const structuredContent = `
【${store.name}】
📍 地址：${store.address}
📞 电话：${store.phone || '请到店咨询'}
🕐 营业时间：${store.businessHours || '09:00-21:00'}

✨ 特色服务：
${services.map(s => `• ${s}`).join('\n')}

⭐ 用户评价（${reviews.count}条）：
综合评分：${reviews.rating}分
${reviews.highlights.map(h => `• "${h}"`).join('\n')}

🏷️ 搜索标签：${services.join('、')}、官方授权、正品保障
`.trim();

    return {
      storeId: store.id,
      storeName: store.name,
      address: store.address,
      phone: store.phone,
      businessHours: store.businessHours || '09:00-21:00',
      services,
      features,
      reviews,
      geoLocation: { lat: store.lat, lng: store.lng },
      structuredContent,
    };
  }

  /**
   * 检查 AI 搜索排名（模拟实现）
   */
  async checkAISearchRank(
    storeId: string,
    keyword: string,
    platform: AISearchPlatform
  ): Promise<AISearchRankResult> {
    // 模拟检查 AI 搜索排名
    // 实际实现需要调用各平台 API
    
    const isVisible = Math.random() > 0.3; // 70% 可见率
    const rankPosition = isVisible ? Math.floor(Math.random() * 10) + 1 : null;
    
    const competitors = isVisible
      ? ['华为授权体验店', '小米之家', 'OPPO专卖店'].filter(() => Math.random() > 0.5)
      : [];

    const result: AISearchRankResult = {
      platform,
      keyword,
      rankPosition,
      isVisible,
      competitors,
      checkedAt: new Date().toISOString(),
    };

    // 更新关键词排名
    for (const [id, kw] of this.keywords) {
      if (kw.keyword === keyword) {
        kw.rankPosition = rankPosition || undefined;
        kw.lastChecked = result.checkedAt;
        this.keywords.set(id, kw);
        break;
      }
    }

    this.saveToCache();
    return result;
  }

  /**
   * 批量检查所有平台排名
   */
  async checkAllPlatforms(
    storeId: string,
    keywords?: string[]
  ): Promise<GEORankingReport> {
    const targetKeywords = keywords || this.getKeywords().slice(0, 10).map(k => k.keyword);
    const platforms: AISearchPlatform[] = ['doubao', 'chatgpt', 'wechat_ai', 'tencent_yuanbao', 'deepseek'];
    
    const platformResults: AISearchRankResult[] = [];
    
    for (const keyword of targetKeywords) {
      for (const platform of platforms) {
        const result = await this.checkAISearchRank(storeId, keyword, platform);
        platformResults.push(result);
      }
    }

    const visibleKeywords = new Set(
      platformResults.filter(r => r.isVisible).map(r => r.keyword)
    ).size;

    const ranksWithPosition = platformResults.filter(r => r.rankPosition !== null);
    const averageRank = ranksWithPosition.length > 0
      ? ranksWithPosition.reduce((sum, r) => sum + (r.rankPosition || 0), 0) / ranksWithPosition.length
      : 0;

    // 生成优化建议
    const recommendations = this.generateRecommendations(platformResults);

    const report: GEORankingReport = {
      id: `GEO-${Date.now()}`,
      storeId,
      date: new Date().toISOString().split('T')[0],
      totalKeywords: targetKeywords.length,
      visibleKeywords,
      averageRank: Math.round(averageRank * 10) / 10,
      platformResults,
      recommendations,
    };

    return report;
  }

  /**
   * 生成优化建议
   */
  private generateRecommendations(results: AISearchRankResult[]): string[] {
    const recommendations: string[] = [];
    
    const invisibleCount = results.filter(r => !r.isVisible).length;
    if (invisibleCount > results.length * 0.3) {
      recommendations.push('⚠️ 超过30%的关键词在AI搜索中不可见，建议优化门店描述的结构化内容');
    }

    const lowRankResults = results.filter(r => r.rankPosition && r.rankPosition > 5);
    if (lowRankResults.length > 0) {
      recommendations.push(`📊 ${lowRankResults.length}个关键词排名在5名之后，建议增加相关服务评价和内容`);
    }

    const platforms = new Set(results.map(r => r.platform));
    if (platforms.size < 5) {
      recommendations.push('🔍 建议覆盖更多AI搜索平台（豆包、ChatGPT、微信AI、腾讯元宝、DeepSeek）');
    }

    if (recommendations.length === 0) {
      recommendations.push('✅ GEO优化状态良好，继续保持内容更新');
    }

    return recommendations;
  }

  /**
   * 记录到店归因
   */
  recordAttribution(record: Omit<AttributionRecord, 'id' | 'timestamp'>): AttributionRecord {
    const newRecord: AttributionRecord = {
      ...record,
      id: `ATTR-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      timestamp: new Date().toISOString(),
    };
    
    this.attributions.push(newRecord);
    this.saveToCache();
    
    console.log(`[GEOEngine] 记录归因: ${record.channel} -> ${record.storeId}`);
    return newRecord;
  }

  /**
   * 获取归因统计
   */
  getAttributionStats(storeId: string, days: number = 30): Record<string, number> {
    const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;
    const recentAttributions = this.attributions.filter(
      a => a.storeId === storeId && new Date(a.timestamp).getTime() > cutoff
    );

    const stats: Record<string, number> = {};
    recentAttributions.forEach(a => {
      stats[a.channel] = (stats[a.channel] || 0) + 1;
    });

    return stats;
  }

  /**
   * 获取中国行政区划数据
   */
  getChinaRegions(level: 1 | 2 | 3 | 4 | 5 = 3) {
    const result: any = {
      provinces: CHINA_REGIONS.provinces,
    };
    
    if (level >= 2) {
      result.cities = CHINA_REGIONS.cities;
    }
    
    if (level >= 3) {
      result.districts = CHINA_REGIONS.districts;
    }
    
    // 4级和5级需要动态加载（街道、社区）
    if (level >= 4) {
      result.streets = [];
      result.note = '街道数据需要调用地图API动态获取';
    }
    
    if (level >= 5) {
      result.communities = [];
      result.note = '社区数据需要调用地图API动态获取';
    }

    return result;
  }

  /**
   * 搜索行政区划
   */
  searchRegion(query: string): Array<{ code: string; name: string; level: number; fullPath: string }> {
    const results: Array<{ code: string; name: string; level: number; fullPath: string }> = [];
    
    // 搜索省份
    CHINA_REGIONS.provinces.forEach(p => {
      if (p.name.includes(query)) {
        results.push({ ...p, fullPath: p.name });
      }
    });
    
    // 搜索城市
    CHINA_REGIONS.cities.forEach(c => {
      if (c.name.includes(query)) {
        const province = CHINA_REGIONS.provinces.find(p => p.code === c.provinceCode);
        results.push({
          ...c,
          fullPath: province ? `${province.name} > ${c.name}` : c.name,
        });
      }
    });
    
    // 搜索区县
    CHINA_REGIONS.districts.forEach(d => {
      if (d.name.includes(query)) {
        const city = CHINA_REGIONS.cities.find(c => c.code === d.cityCode);
        const province = city ? CHINA_REGIONS.provinces.find(p => p.code === city.provinceCode) : null;
        results.push({
          ...d,
          fullPath: province && city
            ? `${province.name} > ${city.name} > ${d.name}`
            : d.name,
        });
      }
    });

    return results;
  }

  /* -------------------------------------------------------------------------- */
  /*  缓存管理                                                                    */
  /* -------------------------------------------------------------------------- */

  private CACHE_KEY = 'geo_engine_cache';

  private loadFromCache(): void {
    try {
      const cached = safeLocalStorageGet(this.CACHE_KEY);
      if (cached) {
        const data = JSON.parse(cached);
        if (data.keywords) {
          data.keywords.forEach((kw: BrandKeyword) => {
            this.keywords.set(kw.id, kw);
          });
        }
        if (data.attributions) {
          this.attributions = data.attributions;
        }
      }
    } catch {}
  }

  private saveToCache(): void {
    try {
      const data = {
        keywords: Array.from(this.keywords.values()),
        attributions: this.attributions.slice(-1000), // 保留最近1000条
      };
      safeLocalStorageSet(this.CACHE_KEY, JSON.stringify(data));
    } catch {}
  }
}

export const geoOptimizationEngine = new GEOOptimizationEngine();
export default geoOptimizationEngine;
