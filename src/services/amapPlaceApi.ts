/**
 * 5.1 高德地图 Place API Mock（V1.0 保留 + V2.0 扩展）
 * 新增运营商营业厅分类码；24h 缓存策略
 */

export interface Place {
  id: string;
  name: string;
  type: 'office' | 'residential' | 'school' | 'mall' | 'operator' | 'competitor';
  categoryCode: string; // 高德 POI 分类码
  lat: number;
  lng: number;
  address: string;
  distance?: number;
}

// V2.0 新增：运营商营业厅分类码
const OPERATOR_CATEGORY_CODES = {
  china_mobile: '070000', // 中国移动营业厅
  china_unicom: '070100', // 中国联通营业厅
  china_telecom: '070200', // 中国电信营业厅
};

const CACHE_TTL = 24 * 60 * 60 * 1000; // 24h

interface CacheEntry<T> {
  data: T;
  expiresAt: number;
}

const placeCache = new Map<string, CacheEntry<Place[]>>();

function getCacheKey(keyword: string, location: string, radius: number): string {
  return `${keyword}|${location}|${radius}`;
}

export async function searchPlaces(params: {
  keyword: string;
  location: string; // "lng,lat"
  radius: number;
  types?: string[];
}): Promise<Place[]> {
  const cacheKey = getCacheKey(params.keyword, params.location, params.radius);
  const cached = placeCache.get(cacheKey);
  if (cached && cached.expiresAt > Date.now()) {
    console.log('[Place API] 缓存命中:', cacheKey);
    return cached.data;
  }

  // 模拟网络延迟
  await new Promise((r) => setTimeout(r, 400));

  // 模拟数据生成
  const baseLat = parseFloat(params.location.split(',')[1]);
  const baseLng = parseFloat(params.location.split(',')[0]);
  const mockResults: Place[] = [
    {
      id: 'P001',
      name: '国贸 CBD · 银泰中心',
      type: 'office',
      categoryCode: '120200',
      lat: baseLat + 0.005,
      lng: baseLng + 0.003,
      address: '北京市朝阳区建国门外大街 2 号',
      distance: 0.8,
    },
    {
      id: 'P002',
      name: '中国联通望京营业厅',
      type: 'operator',
      categoryCode: OPERATOR_CATEGORY_CODES.china_unicom,
      lat: baseLat - 0.003,
      lng: baseLng + 0.006,
      address: '北京市朝阳区望京街 9 号',
      distance: 1.2,
    },
    {
      id: 'P003',
      name: '北京邮电大学',
      type: 'school',
      categoryCode: '140100',
      lat: baseLat + 0.012,
      lng: baseLng - 0.008,
      address: '北京市海淀区西土城路 10 号',
      distance: 2.4,
    },
    {
      id: 'P004',
      name: '幸福家园小区',
      type: 'residential',
      categoryCode: '120100',
      lat: baseLat - 0.008,
      lng: baseLng - 0.004,
      address: '北京市朝阳区幸福二村',
      distance: 1.5,
    },
  ];

  // V2.0: 如果 types 包含运营商分类码，优先返回运营商
  if (params.types?.some((t) => Object.values(OPERATOR_CATEGORY_CODES).includes(t))) {
    mockResults.sort((a, b) => (a.type === 'operator' ? -1 : 1) - (b.type === 'operator' ? -1 : 1));
  }

  placeCache.set(cacheKey, { data: mockResults, expiresAt: Date.now() + CACHE_TTL });
  return mockResults;
}

export function clearPlaceCache() {
  placeCache.clear();
}
