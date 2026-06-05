/**
 * LBS 雷达扫描服务（重构版 V2.0）
 * 
 * 核心差异：V1.0 只是调地图 API 搜 POI
 * V2.0 融合了地图数据 + 品牌自有数据 + 换机周期模型，输出「可执行的销售线索」
 * 
 * 数据源四层融合：
 * - 第一层：地图 POI 数据
 * - 第二层：品牌 CRM 数据回流
 * - 第三层：换机周期预测模型
 * - 第四层：以旧换新国补计算器
 */

import { mapService } from './mapService';
import { geolocationService } from './geolocation';
import { safeLocalStorageGet, safeLocalStorageSet } from './env';

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type CustomerSegment = 'white_collar' | 'student' | 'family' | 'senior' | 'business';
export type AlertLevel = 'high' | 'medium' | 'low' | 'none';

export interface POIData {
  id: string;
  name: string;
  type: 'office' | 'residential' | 'school' | 'mall' | 'hospital' | 'hotel';
  lat: number;
  lng: number;
  address: string;
  distance: number;
  estimatedPopulation?: number;
}

export interface CRMData {
  customerId: string;
  name: string;
  phone: string;
  lat: number;
  lng: number;
  lastPurchaseDate?: string;
  purchasedModel?: string;
  purchasePrice?: number;
  serviceRecords?: ServiceRecord[];
  memberTier?: 'S' | 'A' | 'B' | 'C' | 'D';
}

export interface ServiceRecord {
  type: 'repair' | 'accessory' | 'consult' | 'tradein';
  date: string;
  model?: string;
  amount?: number;
}

export interface ReplacementPrediction {
  customerId: string;
  purchasedModel: string;
  purchaseDate: string;
  monthsSincePurchase: number;
  alertLevel: AlertLevel;
  alertLabel: string;
  predictedValue: number; // 预估换机价值
  recommendedModels: string[];
}

export interface TradeInQuote {
  oldModel: string;
  oldModelPrice: number;
  governmentSubsidy: number;
  brandSubsidy: number;
  tradeInValue: number;
  totalDeduction: number;
  newModel?: string;
  newModelPrice?: number;
  estimatedPayment?: number;
}

export interface SalesLead {
  id: string;
  type: 'poi' | 'crm_customer' | 'predicted_tradein';
  source: 'layer1_poi' | 'layer2_crm' | 'layer3_prediction' | 'layer4_tradein';
  
  // 基本信息
  name: string;
  lat: number;
  lng: number;
  address?: string;
  distance: number;
  
  // POI 信息
  poiType?: POIData['type'];
  estimatedPopulation?: number;
  
  // 客户信息
  customerId?: string;
  customerName?: string;
  phone?: string;
  memberTier?: string;
  
  // 换机预测
  alertLevel?: AlertLevel;
  alertLabel?: string;
  predictedValue?: number;
  recommendedModels?: string[];
  
  // 以旧换新报价
  tradeInQuote?: TradeInQuote;
  
  // 热力分数
  heatScore: number;
  
  // 建议话术
  suggestedScript?: string;
  
  // 元数据
  createdAt: string;
}

export interface LBSRadarScanResult {
  id: string;
  storeId: string;
  scanTime: string;
  centerLat: number;
  centerLng: number;
  radius: number;
  
  // 四层数据
  layer1POIs: POIData[];
  layer2CRMCustomers: CRMData[];
  layer3Predictions: ReplacementPrediction[];
  layer4TradeInQuotes: TradeInQuote[];
  
  // 融合后的销售线索
  salesLeads: SalesLead[];
  
  // 热力数据
  heatmapData: HeatmapPoint[];
  
  // 统计
  stats: {
    totalLeads: number;
    highValueLeads: number;
    predictedTradeIns: number;
    bySegment: Record<CustomerSegment, number>;
  };
  
  // 建议话术
  suggestedScripts: string[];
}

export interface HeatmapPoint {
  lat: number;
  lng: number;
  intensity: number;
  type: 'high_value_customer' | 'potential_customer' | 'poi';
  label: string;
}

/* -------------------------------------------------------------------------- */
/*  换机周期预测模型                                                             */
/* -------------------------------------------------------------------------- */

const REPLACEMENT_MODEL_RULES = {
  // 旗舰机用户换机周期较短
  flagship: {
    brands: ['iPhone Pro', 'Mate Pro', 'P Pro', 'Find X', 'X Pro'],
    normalCycle: 24, // 24个月
    alertThreshold: 18, // 18个月开始预警
  },
  // 中端机用户
  midrange: {
    brands: ['iPhone', 'Mate', 'P', 'Reno', 'X'],
    normalCycle: 30,
    alertThreshold: 24,
  },
  // 入门机用户
  budget: {
    brands: ['nova', 'Redmi', 'A', 'Y'],
    normalCycle: 36,
    alertThreshold: 30,
  },
};

/* -------------------------------------------------------------------------- */
/*  国补计算规则（2026年）                                                        */
/* -------------------------------------------------------------------------- */

const GOVERNMENT_SUBSIDY_RULES = {
  // 手机以旧换新国补（2026年政策）
  mobile: {
    minOldValue: 500, // 旧机最低估值
    maxSubsidy: 1000, // 最高国补
    subsidyRate: 0.1, // 补贴比例 10%
  },
  // 品牌额外补贴
  brandSubsidy: {
    huawei: { flagship: 500, midrange: 300, budget: 100 },
    xiaomi: { flagship: 400, midrange: 200, budget: 100 },
    oppo: { flagship: 400, midrange: 250, budget: 100 },
    vivo: { flagship: 400, midrange: 250, budget: 100 },
    apple: { flagship: 600, midrange: 400, budget: 200 },
  },
};

/* -------------------------------------------------------------------------- */
/*  LBS 雷达扫描服务                                                             */
/* -------------------------------------------------------------------------- */

class LBSRadarService {
  /**
   * 执行四层融合扫描
   */
  async scan(
    storeId: string,
    options: {
      lat?: number;
      lng?: number;
      radius?: number; // km
      segments?: CustomerSegment[];
    } = {}
  ): Promise<LBSRadarScanResult> {
    const scanId = `LBS-${Date.now()}`;
    console.log(`[LBSRadar] 开始扫描: ${scanId}`);

    // 获取中心点
    let centerLat = options.lat;
    let centerLng = options.lng;
    
    if (!centerLat || !centerLng) {
      const location = await geolocationService.getCurrentLocation();
      centerLat = location.lat;
      centerLng = location.lng;
    }

    const radius = options.radius || 5;
    const segments = options.segments || ['white_collar', 'student', 'family'];

    // 第一层：地图 POI 数据
    const layer1POIs = await this.fetchLayer1POIs(centerLat, centerLng, radius);
    console.log(`[LBSRadar] Layer1 POIs: ${layer1POIs.length}`);

    // 第二层：品牌 CRM 数据
    const layer2CRMCustomers = await this.fetchLayer2CRMData(storeId, centerLat, centerLng, radius);
    console.log(`[LBSRadar] Layer2 CRM: ${layer2CRMCustomers.length}`);

    // 第三层：换机周期预测
    const layer3Predictions = this.predictReplacementCycles(layer2CRMCustomers);
    console.log(`[LBSRadar] Layer3 Predictions: ${layer3Predictions.length}`);

    // 第四层：以旧换新报价
    const layer4TradeInQuotes = this.calculateTradeInQuotes(layer3Predictions);
    console.log(`[LBSRadar] Layer4 TradeIns: ${layer4TradeInQuotes.length}`);

    // 融合生成销售线索
    const salesLeads = this.mergeToSalesLeads(
      layer1POIs,
      layer2CRMCustomers,
      layer3Predictions,
      layer4TradeInQuotes,
      centerLat,
      centerLng
    );
    console.log(`[LBSRadar] Sales Leads: ${salesLeads.length}`);

    // 生成热力数据
    const heatmapData = this.generateHeatmapData(salesLeads, centerLat, centerLng);

    // 生成统计
    const stats = this.generateStats(salesLeads, segments);

    // 生成建议话术
    const suggestedScripts = this.generateSuggestedScripts(salesLeads);

    return {
      id: scanId,
      storeId,
      scanTime: new Date().toISOString(),
      centerLat,
      centerLng,
      radius,
      layer1POIs,
      layer2CRMCustomers,
      layer3Predictions,
      layer4TradeInQuotes,
      salesLeads,
      heatmapData,
      stats,
      suggestedScripts,
    };
  }

  /**
   * 第一层：获取地图 POI 数据
   */
  private async fetchLayer1POIs(
    lat: number,
    lng: number,
    radius: number
  ): Promise<POIData[]> {
    const pois: POIData[] = [];
    
    const poiTypes = [
      { type: 'office' as const, keywords: ['写字楼', '商务中心', '科技园'] },
      { type: 'residential' as const, keywords: ['小区', '住宅', '公寓'] },
      { type: 'school' as const, keywords: ['大学', '学院', '学校'] },
      { type: 'mall' as const, keywords: ['商场', '购物中心', '广场'] },
    ];

    for (const { type, keywords } of poiTypes) {
      for (const keyword of keywords.slice(0, 1)) {
        try {
          const results = await mapService.searchPOI({
            query: keyword,
            lat,
            lng,
            radius,
            limit: 20,
          });

          results.forEach(poi => {
            const distance = this.calculateDistance(lat, lng, poi.lat, poi.lng);
            pois.push({
              id: poi.id,
              name: poi.name,
              type,
              lat: poi.lat,
              lng: poi.lng,
              address: poi.address,
              distance: Math.round(distance),
              estimatedPopulation: this.estimatePopulation(type, poi.name),
            });
          });
        } catch (err) {
          console.warn(`[LBSRadar] POI搜索失败: ${keyword}`, err);
        }
      }
    }

    // 去重并排序
    const unique = this.deduplicatePOIs(pois);
    return unique.sort((a, b) => a.distance - b.distance);
  }

  /**
   * 第二层：获取品牌 CRM 数据
   */
  private async fetchLayer2CRMData(
    storeId: string,
    lat: number,
    lng: number,
    radius: number
  ): Promise<CRMData[]> {
    // 模拟 CRM 数据（实际应调用品牌 CRM API）
    const mockCustomers: CRMData[] = [];
    
    // 生成模拟客户数据
    const customerCount = Math.floor(Math.random() * 50) + 20;
    
    for (let i = 0; i < customerCount; i++) {
      const angle = Math.random() * 2 * Math.PI;
      const distance = Math.random() * radius * 1000;
      const customerLat = lat + (distance / 111000) * Math.cos(angle);
      const customerLng = lng + (distance / 111000) * Math.sin(angle);
      
      const monthsAgo = Math.floor(Math.random() * 36);
      const purchaseDate = new Date(Date.now() - monthsAgo * 30 * 24 * 60 * 60 * 1000);
      
      mockCustomers.push({
        customerId: `CUST-${i}`,
        name: `客户${i + 1}`,
        phone: `138****${String(i).padStart(4, '0')}`,
        lat: customerLat,
        lng: customerLng,
        lastPurchaseDate: purchaseDate.toISOString(),
        purchasedModel: ['Mate60 Pro', 'P60', 'nova12', 'iPhone 15', '小米14'][i % 5],
        purchasePrice: [6999, 4999, 2999, 7999, 3999][i % 5],
        memberTier: ['S', 'A', 'B', 'C', 'D'][Math.floor(Math.random() * 5)] as any,
      });
    }

    return mockCustomers;
  }

  /**
   * 第三层：换机周期预测
   */
  private predictReplacementCycles(
    customers: CRMData[]
  ): ReplacementPrediction[] {
    const predictions: ReplacementPrediction[] = [];

    customers.forEach(customer => {
      if (!customer.lastPurchaseDate || !customer.purchasedModel) return;

      const purchaseDate = new Date(customer.lastPurchaseDate);
      const monthsSincePurchase = Math.floor(
        (Date.now() - purchaseDate.getTime()) / (30 * 24 * 60 * 60 * 1000)
      );

      // 判断机型类型
      const modelCategory = this.categorizeModel(customer.purchasedModel);
      const rules = REPLACEMENT_MODEL_RULES[modelCategory];

      let alertLevel: AlertLevel = 'none';
      let alertLabel = '';

      if (monthsSincePurchase >= rules.normalCycle + 6) {
        alertLevel = 'high';
        alertLabel = '超期服役（红色预警）';
      } else if (monthsSincePurchase >= rules.normalCycle) {
        alertLevel = 'medium';
        alertLabel = '即将换机（橙色预警）';
      } else if (monthsSincePurchase >= rules.alertThreshold) {
        alertLevel = 'low';
        alertLabel = '关注换机意向';
      }

      if (alertLevel !== 'none') {
        predictions.push({
          customerId: customer.customerId,
          purchasedModel: customer.purchasedModel,
          purchaseDate: customer.lastPurchaseDate,
          monthsSincePurchase,
          alertLevel,
          alertLabel,
          predictedValue: customer.purchasePrice || 3000,
          recommendedModels: this.getRecommendedModels(customer.purchasedModel),
        });
      }
    });

    return predictions.sort((a, b) => {
      const order = { high: 0, medium: 1, low: 2, none: 3 };
      return order[a.alertLevel] - order[b.alertLevel];
    });
  }

  /**
   * 第四层：以旧换新报价计算
   */
  private calculateTradeInQuotes(
    predictions: ReplacementPrediction[]
  ): TradeInQuote[] {
    return predictions.map(p => {
      const oldModelPrice = p.predictedValue;
      
      // 国补计算
      const govSubsidy = Math.min(
        oldModelPrice * GOVERNMENT_SUBSIDY_RULES.mobile.subsidyRate,
        GOVERNMENT_SUBSIDY_RULES.mobile.maxSubsidy
      );

      // 品牌补贴（假设华为）
      const brandSubsidy = GOVERNMENT_SUBSIDY_RULES.brandSubsidy.huawei.midrange;

      // 旧机折价（简化计算）
      const tradeInValue = oldModelPrice * 0.3;

      const totalDeduction = govSubsidy + brandSubsidy + tradeInValue;

      return {
        oldModel: p.purchasedModel,
        oldModelPrice,
        governmentSubsidy: Math.round(govSubsidy),
        brandSubsidy,
        tradeInValue: Math.round(tradeInValue),
        totalDeduction: Math.round(totalDeduction),
      };
    });
  }

  /**
   * 融合生成销售线索
   */
  private mergeToSalesLeads(
    pois: POIData[],
    customers: CRMData[],
    predictions: ReplacementPrediction[],
    tradeInQuotes: TradeInQuote[],
    centerLat: number,
    centerLng: number
  ): SalesLead[] {
    const leads: SalesLead[] = [];

    // POI 线索
    pois.forEach(poi => {
      leads.push({
        id: `LEAD-POI-${leads.length}`,
        type: 'poi',
        source: 'layer1_poi',
        name: poi.name,
        lat: poi.lat,
        lng: poi.lng,
        address: poi.address,
        distance: poi.distance,
        poiType: poi.type,
        estimatedPopulation: poi.estimatedPopulation,
        heatScore: this.calculatePOIHeatScore(poi),
        createdAt: new Date().toISOString(),
      });
    });

    // 换机预测线索（高价值）
    predictions.forEach((pred, i) => {
      const customer = customers.find(c => c.customerId === pred.customerId);
      if (!customer) return;

      const distance = this.calculateDistance(centerLat, centerLng, customer.lat, customer.lng);

      leads.push({
        id: `LEAD-PRED-${leads.length}`,
        type: 'predicted_tradein',
        source: 'layer3_prediction',
        name: customer.name,
        lat: customer.lat,
        lng: customer.lng,
        distance: Math.round(distance),
        customerId: customer.customerId,
        customerName: customer.name,
        phone: customer.phone,
        memberTier: customer.memberTier,
        alertLevel: pred.alertLevel,
        alertLabel: pred.alertLabel,
        predictedValue: pred.predictedValue,
        recommendedModels: pred.recommendedModels,
        tradeInQuote: tradeInQuotes[i],
        heatScore: this.calculatePredictionHeatScore(pred, customer),
        suggestedScript: this.generateScript(pred, customer),
        createdAt: new Date().toISOString(),
      });
    });

    // 按热力分数排序
    return leads.sort((a, b) => b.heatScore - a.heatScore);
  }

  /**
   * 生成热力数据
   */
  private generateHeatmapData(
    leads: SalesLead[],
    centerLat: number,
    centerLng: number
  ): HeatmapPoint[] {
    const points: HeatmapPoint[] = [];

    // 本店
    points.push({
      lat: centerLat,
      lng: centerLng,
      intensity: 100,
      type: 'high_value_customer',
      label: '本店',
    });

    // 销售线索
    leads.forEach(lead => {
      points.push({
        lat: lead.lat,
        lng: lead.lng,
        intensity: lead.heatScore,
        type: lead.type === 'predicted_tradein' ? 'high_value_customer' : 'potential_customer',
        label: lead.name,
      });
    });

    return points;
  }

  /**
   * 生成统计
   */
  private generateStats(
    leads: SalesLead[],
    segments: CustomerSegment[]
  ): LBSRadarScanResult['stats'] {
    const highValueLeads = leads.filter(l => l.heatScore >= 80).length;
    const predictedTradeIns = leads.filter(l => l.type === 'predicted_tradein').length;

    const bySegment: Record<CustomerSegment, number> = {
      white_collar: 0,
      student: 0,
      family: 0,
      senior: 0,
      business: 0,
    };

    leads.forEach(lead => {
      if (lead.poiType === 'office') bySegment.white_collar++;
      else if (lead.poiType === 'school') bySegment.student++;
      else if (lead.poiType === 'residential') bySegment.family++;
    });

    return {
      totalLeads: leads.length,
      highValueLeads,
      predictedTradeIns,
      bySegment,
    };
  }

  /**
   * 生成建议话术
   */
  private generateSuggestedScripts(leads: SalesLead[]): string[] {
    const scripts: string[] = [];
    
    const highValueCount = leads.filter(l => l.heatScore >= 80).length;
    if (highValueCount > 0) {
      scripts.push(`发现${highValueCount}个高价值换机意向客户，建议优先联系`);
    }

    const tradeInLeads = leads.filter(l => l.alertLevel === 'high');
    if (tradeInLeads.length > 0) {
      scripts.push(`${tradeInLeads.length}个客户手机超期服役，换机需求迫切`);
    }

    return scripts;
  }

  /* -------------------------------------------------------------------------- */
  /*  辅助方法                                                                    */
  /* -------------------------------------------------------------------------- */

  private calculateDistance(lat1: number, lng1: number, lat2: number, lng2: number): number {
    const R = 6371000;
    const φ1 = (lat1 * Math.PI) / 180;
    const φ2 = (lat2 * Math.PI) / 180;
    const Δφ = ((lat2 - lat1) * Math.PI) / 180;
    const Δλ = ((lng2 - lng1) * Math.PI) / 180;
    const a = Math.sin(Δφ / 2) ** 2 + Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) ** 2;
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  private estimatePopulation(type: POIData['type'], name: string): number {
    const estimates: Record<POIData['type'], number> = {
      office: 500,
      residential: 1000,
      school: 2000,
      mall: 3000,
      hospital: 500,
      hotel: 200,
    };
    return estimates[type] || 100;
  }

  private deduplicatePOIs(pois: POIData[]): POIData[] {
    const seen = new Set<string>();
    return pois.filter(poi => {
      const key = `${poi.lat.toFixed(4)},${poi.lng.toFixed(4)}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }

  private categorizeModel(model: string): 'flagship' | 'midrange' | 'budget' {
    const lower = model.toLowerCase();
    if (lower.includes('pro') || lower.includes('ultra') || lower.includes('max')) {
      return 'flagship';
    }
    if (lower.includes('nova') || lower.includes('redmi') || lower.includes('a') || lower.includes('y')) {
      return 'budget';
    }
    return 'midrange';
  }

  private getRecommendedModels(currentModel: string): string[] {
    // 根据当前机型推荐升级机型
    if (currentModel.includes('Mate')) return ['Mate70 Pro', 'P70 Pro'];
    if (currentModel.includes('P')) return ['P70 Pro', 'Mate70'];
    if (currentModel.includes('nova')) return ['nova13', 'P70'];
    if (currentModel.includes('iPhone')) return ['iPhone 16 Pro', 'iPhone 16'];
    if (currentModel.includes('小米')) return ['小米15 Pro', '小米15'];
    return ['Mate70 Pro', 'P70 Pro', 'nova13'];
  }

  private calculatePOIHeatScore(poi: POIData): number {
    const typeScores: Record<POIData['type'], number> = {
      office: 90,
      residential: 70,
      school: 80,
      mall: 85,
      hospital: 60,
      hotel: 50,
    };
    const baseScore = typeScores[poi.type] || 50;
    const distanceFactor = Math.max(0, 1 - poi.distance / 5000);
    return Math.round(baseScore * (0.5 + 0.5 * distanceFactor));
  }

  private calculatePredictionHeatScore(pred: ReplacementPrediction, customer: CRMData): number {
    const alertScores = { high: 100, medium: 80, low: 60, none: 40 };
    const tierScores = { S: 20, A: 15, B: 10, C: 5, D: 0 };
    
    return alertScores[pred.alertLevel] + (tierScores[customer.memberTier || 'D'] || 0);
  }

  private generateScript(pred: ReplacementPrediction, customer: CRMData): string {
    const templates = {
      high: `${customer.name}您好，您的${pred.purchasedModel}已使用${pred.monthsSincePurchase}个月，现在换新可享国补+品牌补贴最高${pred.predictedValue * 0.4}元，推荐${pred.recommendedModels?.[0] || '新款机型'}`,
      medium: `${customer.name}您好，${pred.purchasedModel}换新优惠进行中，以旧换新最高抵扣${Math.round(pred.predictedValue * 0.5)}元`,
      low: `${customer.name}您好，关注一下我们的新款机型，老客户专享优惠`,
      none: '',
    };
    return templates[pred.alertLevel];
  }
}

export const lbsRadarService = new LBSRadarService();
export default lbsRadarService;
