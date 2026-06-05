/**
 * AI 算法服务层 - 智能推荐、预测模型、话术生成
 * 支持：换机预测、客户分群、智能话术、路线优化、热力分析
 */

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export interface DeviceProfile {
  brand: string;
  model: string;
  price: number;
  purchaseDate: string;
  isFlagship: boolean;
  repairCount: number;
  serviceCount: number;
}

export interface CustomerProfile {
  id: string;
  name: string;
  age: number;
  gender: 'male' | 'female';
  device: DeviceProfile;
  totalSpend: number;
  visitCount: number;
  lastVisitDate: string;
  tags: string[];
  location: { lat: number; lng: number };
}

export interface ReplacementPrediction {
  probability: number;         // 0-1 换机概率
  urgency: 'high' | 'medium' | 'low';
  estimatedDate: string;       // 预计换机时间
  recommendedModels: string[]; // 推荐机型
  suggestedPrice: number;      // 建议预算
  factors: string[];           // 影响因素
}

export interface CustomerSegment {
  id: string;
  name: string;
  description: string;
  criteria: string;
  count: number;
  avgValue: number;
  color: string;
}

export interface SmartScript {
  script: string;
  tone: 'formal' | 'casual' | 'friendly';
  keyPoints: string[];
  avoidPoints: string[];
  estimatedResponse: string;
}

/* -------------------------------------------------------------------------- */
/*  换机预测算法                                                                */
/* -------------------------------------------------------------------------- */

export function predictReplacement(customer: CustomerProfile): ReplacementPrediction {
  const device = customer.device;
  const now = new Date();
  const purchaseDate = new Date(device.purchaseDate);
  const monthsUsed = (now.getFullYear() - purchaseDate.getFullYear()) * 12 +
                     (now.getMonth() - purchaseDate.getMonth());

  // 基础换机周期（根据价格）
  let baseCycle = 24; // 默认 24 个月
  if (device.price < 2000) baseCycle = 18;
  else if (device.price > 5000) baseCycle = 30;
  else if (device.isFlagship) baseCycle = 28;

  // 调整因子
  const factors: string[] = [];

  // 维修次数影响
  if (device.repairCount > 3) {
    baseCycle -= 6;
    factors.push(`维修 ${device.repairCount} 次，换机周期缩短`);
  }

  // 服务次数影响（粘性）
  if (device.serviceCount >= 3) {
    baseCycle += 3;
    factors.push(`服务 ${device.serviceCount} 次，品牌粘性高`);
  }

  // 到店频率影响
  if (customer.visitCount > 5) {
    baseCycle -= 2;
    factors.push(`到店 ${customer.visitCount} 次，活跃度高`);
  }

  // 计算概率
  const progress = monthsUsed / baseCycle;
  let probability = Math.min(1, Math.max(0, progress));

  // 旗舰机用户更可能换新旗舰
  if (device.isFlagship && monthsUsed > 18) {
    probability = Math.min(1, probability + 0.15);
    factors.push('旗舰机用户，换机意愿强');
  }

  // 紧迫度
  let urgency: 'high' | 'medium' | 'low' = 'low';
  if (probability >= 0.85) urgency = 'high';
  else if (probability >= 0.65) urgency = 'medium';

  // 预计换机时间
  const estimatedMonths = Math.max(0, baseCycle - monthsUsed);
  const estimatedDate = new Date(now);
  estimatedDate.setMonth(estimatedDate.getMonth() + estimatedMonths);

  // 推荐机型
  const recommendedModels: string[] = [];
  if (device.brand === '华为') {
    recommendedModels.push('Mate70 Pro', 'P70 Pro', 'Mate70');
  } else if (device.brand === 'Apple') {
    recommendedModels.push('iPhone 16 Pro Max', 'iPhone 16 Pro', 'iPhone 16');
  } else if (device.brand === '小米') {
    recommendedModels.push('小米 15 Pro', '小米 15', 'Redmi K80 Pro');
  } else {
    recommendedModels.push('华为 Mate70 Pro', 'iPhone 16 Pro', '小米 15 Pro');
  }

  // 建议预算
  const suggestedPrice = Math.round(device.price * 1.2);

  return {
    probability: Math.round(probability * 100) / 100,
    urgency,
    estimatedDate: estimatedDate.toISOString().slice(0, 10),
    recommendedModels,
    suggestedPrice,
    factors: factors.length > 0 ? factors : ['按标准换机周期计算'],
  };
}

/* -------------------------------------------------------------------------- */
/*  客户分群算法                                                                */
/* -------------------------------------------------------------------------- */

export function segmentCustomers(customers: CustomerProfile[]): CustomerSegment[] {
  const segments: CustomerSegment[] = [
    {
      id: 'S',
      name: 'S 级 - 换机倒计时',
      description: '本品牌高价值客户，即将换机',
      criteria: '购机 ≥ 28 月 OR (旗舰机 AND ≥ 20 月)',
      count: 0,
      avgValue: 0,
      color: '#ef4444',
    },
    {
      id: 'A',
      name: 'A 级 - 合约到期',
      description: '运营商合约即将到期',
      criteria: '合约剩余 < 90 天',
      count: 0,
      avgValue: 0,
      color: '#f59e0b',
    },
    {
      id: 'B',
      name: 'B 级 - 服务高粘性',
      description: '频繁到店但未购机',
      criteria: '近 180 天到店 ≥ 2 次 AND 30 天内未购机',
      count: 0,
      avgValue: 0,
      color: '#10b981',
    },
    {
      id: 'C',
      name: 'C 级 - 周边潜客',
      description: '门店 3km 内潜在客群',
      criteria: '距离 ≤ 3km AND 客群画像匹配',
      count: 0,
      avgValue: 0,
      color: '#3b82f6',
    },
    {
      id: 'D',
      name: 'D 级 - 竞品用户',
      description: '竞品旗舰机用户',
      criteria: '品牌 ≠ 本品牌 AND 旗舰机 AND ≥ 20 月',
      count: 0,
      avgValue: 0,
      color: '#8b5cf6',
    },
  ];

  // 计算各分群数量（简化版）
  customers.forEach((c) => {
    const pred = predictReplacement(c);
    if (pred.urgency === 'high' && c.device.brand === '华为') {
      segments[0].count++;
      segments[0].avgValue += c.totalSpend;
    }
  });

  // 计算平均值
  segments.forEach((s) => {
    if (s.count > 0) s.avgValue = Math.round(s.avgValue / s.count);
  });

  return segments;
}

/* -------------------------------------------------------------------------- */
/*  智能话术生成                                                                */
/* -------------------------------------------------------------------------- */

export function generateSmartScript(params: {
  customerName: string;
  poiType: '写字楼' | '小区' | '学校' | '商场' | '其他';
  timeSlot: 'morning' | 'noon' | 'afternoon' | 'evening';
  promotion: string;
  weather?: 'sunny' | 'cloudy' | 'rainy';
  customerTags?: string[];
}): SmartScript {
  const { customerName, poiType, timeSlot, promotion, weather, customerTags } = params;

  const timeGreetings: Record<string, string> = {
    morning: '早上好',
    noon: '中午好',
    afternoon: '下午好',
    evening: '晚上好',
  };

  const poiTips: Record<string, string> = {
    写字楼: '您工作繁忙，我们门店就在附近，午休或下班顺路即可体验',
    小区: '您住得近，周末带家人到店逛逛，孩子也能体验最新科技',
    学校: '凭学生证有专属优惠，我们门店经常举办学生专场活动',
    商场: '购物之余到店休息一下，免费贴膜 + 咖啡',
    其他: '我们门店就在附近，欢迎随时到店体验',
  };

  const weatherSmallTalk = weather
    ? weather === 'sunny'
      ? '今天天气不错，适合出门'
      : weather === 'rainy'
      ? '雨天出行不便，我们提供上门服务'
      : '天气凉爽，适合逛街'
    : '';

  // 构建话术
  const script = `${customerName}${timeGreetings[timeSlot]}！${weatherSmallTalk ? weatherSmallTalk + '。' : ''}
${poiTips[poiType] || poiTips['其他']}。
当前活动：${promotion}。
方便加个微信吗？我把活动详情和门店地址发您，后续有新机上市也会第一时间通知。`;

  // 关键点
  const keyPoints: string[] = [
    '强调距离近、方便',
    '突出当前优惠',
    '获取联系方式',
  ];

  if (customerTags?.includes('旗舰机用户')) {
    keyPoints.push('提及旗舰机专属权益');
  }

  // 避免点
  const avoidPoints: string[] = [
    '不要过度推销',
    '不要询问收入',
    '不要贬低竞品',
  ];

  return {
    script: script.replace(/\n/g, ' ').replace(/\s+/g, ' ').trim(),
    tone: poiType === '写字楼' ? 'formal' : 'friendly',
    keyPoints,
    avoidPoints,
    estimatedResponse: '预计客户会同意加微信或表示感兴趣',
  };
}

/* -------------------------------------------------------------------------- */
/*  路线优化算法                                                                */
/* -------------------------------------------------------------------------- */

export interface RoutePoint {
  id: string;
  name: string;
  lat: number;
  lng: number;
  score: number;      // 优先级分数
  duration: number;   // 预计停留时间（分钟）
}

export interface OptimizedRoute {
  points: RoutePoint[];
  totalDistance: number;
  totalDuration: number;
  startTime: string;
  endTime: string;
  efficiency: number; // 效率分数 0-100
}

export function optimizeRoute(params: {
  points: RoutePoint[];
  startLat: number;
  startLng: number;
  totalMinutes: number;
  startTime: string;
}): OptimizedRoute {
  const { points, startLat, startLng, totalMinutes, startTime } = params;

  // 简化版：按分数排序 + 贪心选择
  const sorted = [...points].sort((a, b) => b.score - a.score);

  // 计算距离（Haversine）
  const distance = (lat1: number, lng1: number, lat2: number, lng2: number): number => {
    const R = 6371; // km
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLng = ((lng2 - lng1) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) ** 2 +
      Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLng / 2) ** 2;
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  };

  // 贪心选择
  const selected: RoutePoint[] = [];
  let currentLat = startLat;
  let currentLng = startLng;
  let remainingTime = totalMinutes;
  let totalDistance = 0;

  for (const point of sorted) {
    const dist = distance(currentLat, currentLng, point.lat, point.lng);
    const travelTime = dist * 3; // 假设平均速度 20km/h = 3min/km
    const totalTime = travelTime + point.duration;

    if (totalTime <= remainingTime) {
      selected.push(point);
      totalDistance += dist;
      remainingTime -= totalTime;
      currentLat = point.lat;
      currentLng = point.lng;
    }
  }

  // 计算结束时间
  const [startHour, startMin] = startTime.split(':').map(Number);
  const totalDuration = totalMinutes - remainingTime;
  const endMinutes = startHour * 60 + startMin + totalDuration;
  const endHour = Math.floor(endMinutes / 60) % 24;
  const endMin = endMinutes % 60;

  // 效率分数
  const efficiency = Math.round((selected.length / points.length) * 100);

  return {
    points: selected,
    totalDistance: Math.round(totalDistance * 10) / 10,
    totalDuration,
    startTime,
    endTime: `${endHour.toString().padStart(2, '0')}:${endMin.toString().padStart(2, '0')}`,
    efficiency,
  };
}

/* -------------------------------------------------------------------------- */
/*  热力分析算法                                                                */
/* -------------------------------------------------------------------------- */

export interface HeatmapCell {
  lat: number;
  lng: number;
  intensity: number;  // 0-100
  type: 'customer' | 'poi' | 'competitor';
  count: number;
}

export function analyzeHeatmap(params: {
  centerLat: number;
  centerLng: number;
  radiusKm: number;
  customers: CustomerProfile[];
  poiData: { lat: number; lng: number; type: string }[];
  gridSize?: number;
}): HeatmapCell[] {
  const { centerLat, centerLng, radiusKm, customers, poiData, gridSize = 10 } = params;

  const cells: HeatmapCell[] = [];
  const latStep = (radiusKm * 2) / gridSize / 111; // 1度纬度约111km
  const lngStep = (radiusKm * 2) / gridSize / 111;

  for (let i = 0; i < gridSize; i++) {
    for (let j = 0; j < gridSize; j++) {
      const lat = centerLat - radiusKm / 111 + latStep * (i + 0.5);
      const lng = centerLng - radiusKm / 111 + lngStep * (j + 0.5);

      // 计算该格子内的客户数
      let customerCount = 0;
      customers.forEach((c) => {
        const dLat = Math.abs(c.location.lat - lat);
        const dLng = Math.abs(c.location.lng - lng);
        if (dLat < latStep / 2 && dLng < lngStep / 2) {
          customerCount++;
        }
      });

      // 计算该格子内的 POI 数
      let poiCount = 0;
      poiData.forEach((p) => {
        const dLat = Math.abs(p.lat - lat);
        const dLng = Math.abs(p.lng - lng);
        if (dLat < latStep / 2 && dLng < lngStep / 2) {
          poiCount++;
        }
      });

      const intensity = Math.min(100, (customerCount * 20 + poiCount * 10));
      if (intensity > 0) {
        cells.push({
          lat,
          lng,
          intensity,
          type: customerCount > poiCount ? 'customer' : 'poi',
          count: customerCount + poiCount,
        });
      }
    }
  }

  return cells.sort((a, b) => b.intensity - a.intensity);
}
