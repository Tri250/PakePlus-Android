/**
 * 竞品热力监控服务
 * 实时监控门店周边竞品变化，生成截流建议
 */

import { mapService } from './mapService';
import { safeLocalStorageGet, safeLocalStorageSet } from './env';

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type CompetitorBrand = 'huawei' | 'xiaomi' | 'oppo' | 'vivo' | 'apple' | 'honor' | 'samsung';

export interface CompetitorStore {
  id: string;
  brand: CompetitorBrand;
  brandName: string;
  name: string;
  lat: number;
  lng: number;
  address: string;
  distance: number; // 米
  status: 'open' | 'closed' | 'renovating' | 'new';
  openDate?: string;
  closeDate?: string;
  rating?: number;
  reviewCount?: number;
  activities?: CompetitorActivity[];
  lastChecked: string;
}

export interface CompetitorActivity {
  id: string;
  storeId: string;
  type: 'promotion' | 'new_product' | 'event' | 'discount';
  title: string;
  description: string;
  platform: 'dianping' | 'meituan' | 'douyin' | 'xiaohongshu';
  startDate: string;
  endDate?: string;
  detectedAt: string;
}

export interface HeatmapData {
  lat: number;
  lng: number;
  intensity: number; // 0-100
  type: 'our_store' | 'competitor' | 'potential_customer';
  label: string;
  details?: any;
}

export interface InterceptionPlan {
  id: string;
  triggerStoreId: string;
  triggerEvent: 'new_store' | 'promotion' | 'high_traffic';
  targetCustomers: string[];
  actions: InterceptionAction[];
  estimatedImpact: number;
  createdAt: string;
}

export interface InterceptionAction {
  type: 'push_notification' | 'sms' | 'coupon' | 'event' | 'call';
  target: string;
  content: string;
  priority: 'high' | 'medium' | 'low';
}

export interface CompetitorMonitorReport {
  id: string;
  storeId: string;
  scanTime: string;
  radius: number;
  totalCompetitors: number;
  newStores: CompetitorStore[];
  closedStores: CompetitorStore[];
  activePromotions: CompetitorActivity[];
  heatmapData: HeatmapData[];
  interceptionPlans: InterceptionPlan[];
  summary: string;
}

/* -------------------------------------------------------------------------- */
/*  竞品品牌配置                                                                */
/* -------------------------------------------------------------------------- */

const COMPETITOR_CONFIGS: Record<CompetitorBrand, { name: string; keywords: string[]; color: string }> = {
  huawei: { name: '华为', keywords: ['华为', 'HUAWEI', '华为体验店', '华为授权'], color: '#cf0a2c' },
  xiaomi: { name: '小米', keywords: ['小米', 'Xiaomi', '小米之家', '小米授权'], color: '#ff6700' },
  oppo: { name: 'OPPO', keywords: ['OPPO', 'oppo', 'OPPO体验店', 'OPPO专卖'], color: '#1ba784' },
  vivo: { name: 'vivo', keywords: ['vivo', 'VIVO', 'vivo体验店', 'vivo专卖'], color: '#415fff' },
  apple: { name: 'Apple', keywords: ['Apple', '苹果', 'Apple Store', '苹果店'], color: '#555555' },
  honor: { name: '荣耀', keywords: ['荣耀', 'Honor', '荣耀体验店'], color: '#ff0033' },
  samsung: { name: '三星', keywords: ['三星', 'Samsung', '三星体验店'], color: '#1428a0' },
};

/* -------------------------------------------------------------------------- */
/*  竞品热力监控服务                                                             */
/* -------------------------------------------------------------------------- */

class CompetitorMonitorService {
  private monitoredStores: Map<string, CompetitorStore[]> = new Map();
  private activities: CompetitorActivity[] = [];

  /**
   * 扫描周边竞品门店
   */
  async scanCompetitors(
    storeId: string,
    centerLat: number,
    centerLng: number,
    radius: number = 5000 // 默认5km
  ): Promise<CompetitorMonitorReport> {
    console.log(`[CompetitorMonitor] 扫描竞品: ${storeId}, 半径 ${radius}m`);

    const competitors: CompetitorStore[] = [];
    const radiusKm = radius / 1000;

    // 扫描各品牌门店
    for (const [brand, config] of Object.entries(COMPETITOR_CONFIGS)) {
      for (const keyword of config.keywords.slice(0, 2)) {
        try {
          const pois = await mapService.searchPOI({
            query: keyword,
            lat: centerLat,
            lng: centerLng,
            radius: radiusKm,
            limit: 20,
          });

          for (const poi of pois) {
            // 计算距离
            const distance = this.calculateDistance(centerLat, centerLng, poi.lat, poi.lng);
            
            // 避免重复
            const exists = competitors.some(
              c => Math.abs(c.lat - poi.lat) < 0.0001 && Math.abs(c.lng - poi.lng) < 0.0001
            );
            if (exists) continue;

            competitors.push({
              id: `COMP-${brand}-${competitors.length}`,
              brand: brand as CompetitorBrand,
              brandName: config.name,
              name: poi.name,
              lat: poi.lat,
              lng: poi.lng,
              address: poi.address,
              distance: Math.round(distance),
              status: 'open',
              rating: 4 + Math.random() * 1,
              reviewCount: Math.floor(Math.random() * 500) + 50,
              lastChecked: new Date().toISOString(),
            });
          }
        } catch (err) {
          console.warn(`[CompetitorMonitor] 扫描 ${config.name} 失败:`, err);
        }
      }
    }

    // 按距离排序
    competitors.sort((a, b) => a.distance - b.distance);

    // 缓存结果
    this.monitoredStores.set(storeId, competitors);

    // 检测变化
    const previousStores = this.loadPreviousScan(storeId);
    const newStores = this.detectNewStores(competitors, previousStores || []);
    const closedStores = this.detectClosedStores(competitors, previousStores || []);

    // 生成热力数据
    const heatmapData = this.generateHeatmapData(centerLat, centerLng, competitors);

    // 生成截流计划
    const interceptionPlans = this.generateInterceptionPlans(storeId, newStores);

    // 检测活动
    const activePromotions = await this.detectCompetitorActivities(competitors);

    // 保存扫描结果
    this.saveScanResult(storeId, competitors);

    const summary = this.generateSummary(competitors, newStores, closedStores, activePromotions);

    return {
      id: `CM-REPORT-${Date.now()}`,
      storeId,
      scanTime: new Date().toISOString(),
      radius,
      totalCompetitors: competitors.length,
      newStores,
      closedStores,
      activePromotions,
      heatmapData,
      interceptionPlans,
      summary,
    };
  }

  /**
   * 检测新开门店
   */
  private detectNewStores(
    current: CompetitorStore[],
    previous: CompetitorStore[]
  ): CompetitorStore[] {
    if (!previous || previous.length === 0) return [];

    return current.filter(c => {
      return !previous.some(
        p => p.brand === c.brand && Math.abs(p.lat - c.lat) < 0.0001 && Math.abs(p.lng - c.lng) < 0.0001
      );
    }).map(c => ({ ...c, status: 'new' as const }));
  }

  /**
   * 检测关闭门店
   */
  private detectClosedStores(
    current: CompetitorStore[],
    previous: CompetitorStore[]
  ): CompetitorStore[] {
    if (!previous || previous.length === 0) return [];

    return previous.filter(p => {
      return !current.some(
        c => c.brand === p.brand && Math.abs(c.lat - p.lat) < 0.0001 && Math.abs(c.lng - p.lng) < 0.0001
      );
    }).map(p => ({ ...p, status: 'closed' as const }));
  }

  /**
   * 生成热力数据
   */
  private generateHeatmapData(
    centerLat: number,
    centerLng: number,
    competitors: CompetitorStore[]
  ): HeatmapData[] {
    const data: HeatmapData[] = [];

    // 本店位置（高热度）
    data.push({
      lat: centerLat,
      lng: centerLng,
      intensity: 100,
      type: 'our_store',
      label: '本店',
    });

    // 竞品门店（根据距离和评分计算热度）
    competitors.forEach(c => {
      const distanceFactor = Math.max(0, 1 - c.distance / 5000);
      const ratingFactor = (c.rating || 4) / 5;
      const intensity = Math.round(80 * distanceFactor * ratingFactor);

      data.push({
        lat: c.lat,
        lng: c.lng,
        intensity,
        type: 'competitor',
        label: `${c.brandName} - ${c.name}`,
        details: {
          brand: c.brand,
          distance: c.distance,
          rating: c.rating,
        },
      });
    });

    // 潜在客户聚集区（基于POI类型）
    // 这里可以结合写字楼、小区等数据生成

    return data;
  }

  /**
   * 生成截流计划
   */
  private generateInterceptionPlans(
    storeId: string,
    newStores: CompetitorStore[]
  ): InterceptionPlan[] {
    const plans: InterceptionPlan[] = [];

    newStores.forEach(store => {
      if (store.distance < 1000) { // 1km内的新店需要重点关注
        plans.push({
          id: `PLAN-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
          triggerStoreId: store.id,
          triggerEvent: 'new_store',
          targetCustomers: ['高价值会员', '近期有换机意向', '附近居民'],
          actions: [
            {
              type: 'push_notification',
              target: '附近会员',
              content: `【专属福利】${store.brandName}新店开业期间，老客户专享额外补贴`,
              priority: 'high',
            },
            {
              type: 'coupon',
              target: '高价值会员',
              content: '老客户专属优惠券：以旧换新额外补贴200元',
              priority: 'high',
            },
            {
              type: 'sms',
              target: '换机意向客户',
              content: '【提醒】竞品新店开业，建议提前邀约客户到店体验',
              priority: 'medium',
            },
          ],
          estimatedImpact: Math.round(100 - store.distance / 20),
          createdAt: new Date().toISOString(),
        });
      }
    });

    return plans;
  }

  /**
   * 检测竞品活动
   */
  private async detectCompetitorActivities(
    competitors: CompetitorStore[]
  ): Promise<CompetitorActivity[]> {
    // 模拟检测活动（实际需要调用大众点评/美团/抖音 API）
    const activities: CompetitorActivity[] = [];

    // 随机生成一些活动
    competitors.slice(0, 5).forEach(store => {
      if (Math.random() > 0.7) {
        activities.push({
          id: `ACT-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
          storeId: store.id,
          type: 'promotion',
          title: `${store.brandName}限时优惠`,
          description: '以旧换新最高补贴500元',
          platform: 'dianping',
          startDate: new Date().toISOString(),
          detectedAt: new Date().toISOString(),
        });
      }
    });

    return activities;
  }

  /**
   * 生成摘要
   */
  private generateSummary(
    competitors: CompetitorStore[],
    newStores: CompetitorStore[],
    closedStores: CompetitorStore[],
    activities: CompetitorActivity[]
  ): string {
    const parts: string[] = [];
    
    parts.push(`周边5km内共${competitors.length}家竞品门店`);
    
    if (newStores.length > 0) {
      parts.push(`新开${newStores.length}家`);
    }
    
    if (closedStores.length > 0) {
      parts.push(`关闭${closedStores.length}家`);
    }
    
    if (activities.length > 0) {
      parts.push(`${activities.length}个促销活动进行中`);
    }

    // 按品牌统计
    const byBrand: Record<string, number> = {};
    competitors.forEach(c => {
      byBrand[c.brandName] = (byBrand[c.brandName] || 0) + 1;
    });
    
    const brandStats = Object.entries(byBrand)
      .map(([name, count]) => `${name}:${count}家`)
      .join('、');
    
    parts.push(`(${brandStats})`);

    return parts.join('，');
  }

  /**
   * 计算距离（米）
   */
  private calculateDistance(lat1: number, lng1: number, lat2: number, lng2: number): number {
    const R = 6371000;
    const φ1 = (lat1 * Math.PI) / 180;
    const φ2 = (lat2 * Math.PI) / 180;
    const Δφ = ((lat2 - lat1) * Math.PI) / 180;
    const Δλ = ((lng2 - lng1) * Math.PI) / 180;

    const a = Math.sin(Δφ / 2) ** 2 + Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) ** 2;
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return R * c;
  }

  /* -------------------------------------------------------------------------- */
  /*  数据持久化                                                                  */
  /* -------------------------------------------------------------------------- */

  private SCAN_CACHE_KEY = 'competitor_scan_cache';

  private loadPreviousScan(storeId: string): CompetitorStore[] | null {
    try {
      const cached = safeLocalStorageGet(this.SCAN_CACHE_KEY);
      if (cached) {
        const data = JSON.parse(cached);
        return data[storeId] || null;
      }
    } catch {}
    return null;
  }

  private saveScanResult(storeId: string, stores: CompetitorStore[]): void {
    try {
      const cached = safeLocalStorageGet(this.SCAN_CACHE_KEY) || '{}';
      const data = JSON.parse(cached);
      data[storeId] = stores;
      data[`${storeId}_time`] = new Date().toISOString();
      safeLocalStorageSet(this.SCAN_CACHE_KEY, JSON.stringify(data));
    } catch {}
  }

  /**
   * 获取竞品品牌配置
   */
  getCompetitorConfigs() {
    return COMPETITOR_CONFIGS;
  }
}

export const competitorMonitorService = new CompetitorMonitorService();
export default competitorMonitorService;
