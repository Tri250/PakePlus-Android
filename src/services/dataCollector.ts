/**
 * 数据采集爬虫服务
 * 支持方圆 3/5/8/10+ 公里范围的 POI 数据采集
 * 
 * 采集目标：
 * - 写字楼/园区（白领客群）
 * - 住宅小区（家庭客群）
 * - 高校/职校（年轻客群）
 * - 商场/购物中心
 * - 竞品门店（自动识别）
 * - 运营商营业厅
 */

import { mapService } from './mapService';
import { repository } from './storage';
import { toastSuccess, toastError, toastLoading } from '../components/Toast';

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type ScanRadius = 3 | 5 | 8 | 10 | 15 | 20;

export type POICategory =
  | 'office'        // 写字楼/园区
  | 'residential'   // 住宅小区
  | 'school'        // 高校/职校
  | 'mall'          // 商场/购物中心
  | 'competitor'    // 竞品门店
  | 'operator'      // 运营商营业厅
  | 'hospital'      // 医院
  | 'hotel'         // 酒店
  | 'restaurant';   // 餐饮

export interface ScanConfig {
  centerLat: number;
  centerLng: number;
  radius: ScanRadius;
  categories: POICategory[];
  includeCompetitors: boolean;
  competitorBrands: string[];
  maxResults: number;
  saveToDB: boolean;
}

export interface ScanResult {
  id: string;
  category: POICategory;
  name: string;
  lat: number;
  lng: number;
  address: string;
  distance: number;
  phone?: string;
  website?: string;
  openingHours?: string;
  brand?: string;
  estimatedPopulation?: number;  // 预估人流
  heatScore?: number;            // 热力分数
  source: string;
  collectedAt: string;
}

export interface ScanReport {
  id: string;
  config: ScanConfig;
  startTime: string;
  endTime: string;
  duration: number;
  totalResults: number;
  byCategory: Record<POICategory, number>;
  results: ScanResult[];
  status: 'success' | 'partial' | 'failed';
  errors: string[];
}

/* -------------------------------------------------------------------------- */
/*  POI 搜索关键词映射                                                          */
/* -------------------------------------------------------------------------- */

const POI_KEYWORDS: Record<POICategory, string[]> = {
  office: ['写字楼', '商务中心', '科技园', '产业园', '创业园', '办公', 'CBD', '大厦', '中心'],
  residential: ['小区', '住宅', '家园', '花园', '公寓', '社区', '居', '苑', '城'],
  school: ['大学', '学院', '学校', '职校', '技校', '中学', '高中', '中专'],
  mall: ['商场', '购物中心', '广场', '百货', '商城', '购物广场', '商业中心'],
  competitor: ['华为', '小米', 'OPPO', 'vivo', 'Apple', '苹果', '荣耀', '三星', '手机店', '体验店', '专卖店'],
  operator: ['移动营业厅', '联通营业厅', '电信营业厅', '营业厅', '移动', '联通', '电信'],
  hospital: ['医院', '诊所', '卫生院', '医疗', '健康'],
  hotel: ['酒店', '宾馆', '旅馆', '民宿', '公寓'],
  restaurant: ['餐厅', '饭店', '美食', '小吃', '快餐', '火锅', '烧烤'],
};

const COMPETITOR_BRANDS = [
  { name: '华为', keywords: ['华为', 'HUAWEI', '华为体验店', '华为专卖店'] },
  { name: '小米', keywords: ['小米', 'Xiaomi', '小米之家', '小米专卖店'] },
  { name: 'OPPO', keywords: ['OPPO', 'oppo', 'OPPO体验店', 'OPPO专卖店'] },
  { name: 'vivo', keywords: ['vivo', 'VIVO', 'vivo体验店', 'vivo专卖店'] },
  { name: 'Apple', keywords: ['Apple', '苹果', 'Apple Store', '苹果店'] },
  { name: '荣耀', keywords: ['荣耀', 'Honor', '荣耀体验店'] },
  { name: '三星', keywords: ['三星', 'Samsung', '三星体验店'] },
];

/* -------------------------------------------------------------------------- */
/*  数据采集爬虫类                                                              */
/* -------------------------------------------------------------------------- */

export class DataCollector {
  private abortController: AbortController | null = null;
  private isRunning = false;

  /**
   * 执行扫描采集
   */
  async scan(config: ScanConfig): Promise<ScanReport> {
    if (this.isRunning) {
      throw new Error('已有扫描任务在执行中');
    }

    this.isRunning = true;
    this.abortController = new AbortController();

    const reportId = `SCAN-${Date.now()}`;
    const startTime = new Date().toISOString();
    const errors: string[] = [];
    const results: ScanResult[] = [];
    const byCategory: Record<POICategory, number> = {
      office: 0, residential: 0, school: 0, mall: 0,
      competitor: 0, operator: 0, hospital: 0, hotel: 0, restaurant: 0,
    };

    const toastId = toastLoading(`开始扫描方圆 ${config.radius}km 区域...`);

    try {
      // 按类别分批采集
      for (const category of config.categories) {
        if (this.abortController.signal.aborted) break;

        const keywords = POI_KEYWORDS[category];
        const categoryResults: ScanResult[] = [];

        // 每个类别使用多个关键词搜索
        for (const keyword of keywords.slice(0, 3)) {
          if (this.abortController.signal.aborted) break;
          if (categoryResults.length >= config.maxResults / config.categories.length) break;

          try {
            const poiResults = await mapService.searchPOI({
              query: keyword,
              lat: config.centerLat,
              lng: config.centerLng,
              radius: config.radius,
              limit: 50,
            });

            for (const poi of poiResults) {
              const distance = this.calculateDistance(
                config.centerLat, config.centerLng,
                poi.lat, poi.lng
              );

              // 过滤超出半径的结果
              if (distance > config.radius) continue;

              // 检查是否已存在
              if (categoryResults.some(r => 
                Math.abs(r.lat - poi.lat) < 0.0001 && 
                Math.abs(r.lng - poi.lng) < 0.0001
              )) continue;

              const result: ScanResult = {
                id: `${reportId}-${category}-${categoryResults.length}`,
                category,
                name: poi.name,
                lat: poi.lat,
                lng: poi.lng,
                address: poi.address,
                distance: Math.round(distance * 100) / 100,
                phone: poi.phone,
                website: poi.website,
                openingHours: poi.openingHours,
                heatScore: this.calculateHeatScore(category, distance),
                source: poi.source,
                collectedAt: new Date().toISOString(),
              };

              // 竞品品牌识别
              if (category === 'competitor') {
                for (const brand of COMPETITOR_BRANDS) {
                  if (brand.keywords.some(k => poi.name.includes(k))) {
                    result.brand = brand.name;
                    break;
                  }
                }
              }

              categoryResults.push(result);
            }

            // 速率限制
            await new Promise(r => setTimeout(r, 1100));
          } catch (err: any) {
            errors.push(`[${category}:${keyword}] ${err.message}`);
          }
        }

        results.push(...categoryResults);
        byCategory[category] = categoryResults.length;
      }

      // 竞品门店专项扫描
      if (config.includeCompetitors && config.competitorBrands.length > 0) {
        for (const brandName of config.competitorBrands) {
          const brand = COMPETITOR_BRANDS.find(b => b.name === brandName);
          if (!brand) continue;

          for (const keyword of brand.keywords) {
            try {
              const poiResults = await mapService.searchPOI({
                query: keyword,
                lat: config.centerLat,
                lng: config.centerLng,
                radius: config.radius,
                limit: 20,
              });

              for (const poi of poiResults) {
                const distance = this.calculateDistance(
                  config.centerLat, config.centerLng,
                  poi.lat, poi.lng
                );

                if (distance > config.radius) continue;

                results.push({
                  id: `${reportId}-competitor-${results.length}`,
                  category: 'competitor',
                  name: poi.name,
                  lat: poi.lat,
                  lng: poi.lng,
                  address: poi.address,
                  distance: Math.round(distance * 100) / 100,
                  brand: brandName,
                  heatScore: 80,
                  source: poi.source,
                  collectedAt: new Date().toISOString(),
                });
              }

              byCategory.competitor = results.filter(r => r.category === 'competitor').length;
              await new Promise(r => setTimeout(r, 1100));
            } catch (err: any) {
              errors.push(`[competitor:${brandName}] ${err.message}`);
            }
          }
        }
      }

      // 去重
      const uniqueResults = this.deduplicateResults(results);

      // 保存到数据库
      if (config.saveToDB) {
        for (const result of uniqueResults) {
          try {
            await repository.lead.save({
              ...result,
              _version: 1,
              _updatedAt: new Date().toISOString(),
            });
          } catch (err) {
            // 忽略保存错误
          }
        }
      }

      const endTime = new Date().toISOString();
      const duration = new Date(endTime).getTime() - new Date(startTime).getTime();

      toastSuccess(`扫描完成，共采集 ${uniqueResults.length} 个 POI`);

      return {
        id: reportId,
        config,
        startTime,
        endTime,
        duration,
        totalResults: uniqueResults.length,
        byCategory,
        results: uniqueResults,
        status: errors.length === 0 ? 'success' : 'partial',
        errors,
      };

    } catch (err: any) {
      toastError(`扫描失败: ${err.message}`);
      return {
        id: reportId,
        config,
        startTime,
        endTime: new Date().toISOString(),
        duration: new Date().getTime() - new Date(startTime).getTime(),
        totalResults: 0,
        byCategory,
        results: [],
        status: 'failed',
        errors: [err.message],
      };
    } finally {
      this.isRunning = false;
      this.abortController = null;
    }
  }

  /**
   * 快速扫描（预设配置）
   */
  async quickScan(
    lat: number,
    lng: number,
    radius: ScanRadius = 5
  ): Promise<ScanReport> {
    return this.scan({
      centerLat: lat,
      centerLng: lng,
      radius,
      categories: ['office', 'residential', 'school', 'mall', 'competitor', 'operator'],
      includeCompetitors: true,
      competitorBrands: ['华为', '小米', 'OPPO', 'vivo', 'Apple'],
      maxResults: 200,
      saveToDB: true,
    });
  }

  /**
   * 停止扫描
   */
  stop(): void {
    if (this.abortController) {
      this.abortController.abort();
      this.isRunning = false;
      toastSuccess('扫描已停止');
    }
  }

  /**
   * 获取扫描状态
   */
  getStatus(): { isRunning: boolean } {
    return { isRunning: this.isRunning };
  }

  /**
   * 计算两点距离（km）
   */
  private calculateDistance(lat1: number, lng1: number, lat2: number, lng2: number): number {
    const R = 6371;
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLng = ((lng2 - lng1) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) ** 2 +
      Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLng / 2) ** 2;
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  /**
   * 计算热力分数
   */
  private calculateHeatScore(category: POICategory, distance: number): number {
    const categoryWeights: Record<POICategory, number> = {
      office: 90,
      residential: 70,
      school: 80,
      mall: 85,
      competitor: 60,
      operator: 50,
      hospital: 65,
      hotel: 55,
      restaurant: 45,
    };

    const baseScore = categoryWeights[category] || 50;
    const distanceFactor = Math.max(0, 1 - distance / 10);
    return Math.round(baseScore * (0.5 + 0.5 * distanceFactor));
  }

  /**
   * 结果去重
   */
  private deduplicateResults(results: ScanResult[]): ScanResult[] {
    const seen = new Map<string, ScanResult>();

    for (const result of results) {
      const key = `${result.category}:${result.name}:${result.lat.toFixed(4)}:${result.lng.toFixed(4)}`;
      if (!seen.has(key)) {
        seen.set(key, result);
      }
    }

    return Array.from(seen.values()).sort((a, b) => a.distance - b.distance);
  }
}

/* -------------------------------------------------------------------------- */
/*  导出单例                                                                    */
/* -------------------------------------------------------------------------- */

export const dataCollector = new DataCollector();
export default dataCollector;

/* -------------------------------------------------------------------------- */
/*  预设扫描配置                                                                */
/* -------------------------------------------------------------------------- */

export const PRESET_SCANS = {
  // 快速扫描：3km，核心客群
  quick: {
    radius: 3 as ScanRadius,
    categories: ['office', 'residential', 'mall'],
    maxResults: 50,
  },
  // 标准扫描：5km，全客群
  standard: {
    radius: 5 as ScanRadius,
    categories: ['office', 'residential', 'school', 'mall', 'competitor', 'operator'] as POICategory[],
    maxResults: 100,
  },
  // 深度扫描：8km，竞品专项
  deep: {
    radius: 8 as ScanRadius,
    categories: ['office', 'residential', 'school', 'mall', 'competitor', 'operator', 'hospital', 'hotel'] as POICategory[],
    maxResults: 200,
  },
  // 全面扫描：10km+，全量采集
  full: {
    radius: 10 as ScanRadius,
    categories: ['office', 'residential', 'school', 'mall', 'competitor', 'operator', 'hospital', 'hotel', 'restaurant'] as POICategory[],
    maxResults: 500,
  },
};
