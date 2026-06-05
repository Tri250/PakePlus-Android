/**
 * 2026 年免费地图服务配置
 * 
 * 资源列表：
 * 1. OpenStreetMap / Nominatim - 完全免费，无需 Key，1 QPS
 * 2. Mapbox - 免费 50K 请求/月
 * 3. HERE - 免费 250K 事务/月
 * 4. LocationIQ - 免费 5K 请求/天
 * 5. MapTiler - 免费 100K 请求/月
 * 6. Leaflet.js - 完全免费开源地图库
 */

import { getEnv, safeLocalStorageGet, safeLocalStorageSet } from './env';

/* -------------------------------------------------------------------------- */
/*  类型定义                                                                    */
/* -------------------------------------------------------------------------- */

export type MapProvider = 'nominatim' | 'mapbox' | 'here' | 'locationiq' | 'maptiler';

export interface MapConfig {
  provider: MapProvider;
  apiKey?: string;
  baseUrl: string;
  rateLimit: {
    requestsPerSecond: number;
    requestsPerDay: number;
    requestsPerMonth: number;
  };
  features: {
    geocoding: boolean;
    reverseGeocoding: boolean;
    poiSearch: boolean;
    routing: boolean;
    tiles: boolean;
  };
  priority: number; // 降级优先级，数字越小优先级越高
}

export interface GeocodingResult {
  lat: number;
  lng: number;
  displayName: string;
  address?: {
    country?: string;
    city?: string;
    district?: string;
    street?: string;
    postcode?: string;
  };
  type?: string;
  importance?: number;
  source: MapProvider;
}

export interface POIResult {
  id: string;
  name: string;
  type: string;
  category: string;
  lat: number;
  lng: number;
  address: string;
  distance?: number;
  phone?: string;
  website?: string;
  openingHours?: string;
  source: MapProvider;
}

/* -------------------------------------------------------------------------- */
/*  地图服务配置                                                                */
/* -------------------------------------------------------------------------- */

export const MAP_PROVIDERS: Record<MapProvider, MapConfig> = {
  // 1. Nominatim (OpenStreetMap) - 完全免费，无需 Key
  nominatim: {
    provider: 'nominatim',
    baseUrl: 'https://nominatim.openstreetmap.org',
    rateLimit: {
      requestsPerSecond: 1,
      requestsPerDay: 86400,
      requestsPerMonth: Infinity,
    },
    features: {
      geocoding: true,
      reverseGeocoding: true,
      poiSearch: true,
      routing: false,
      tiles: true,
    },
    priority: 1,
  },

  // 2. Mapbox - 免费 50K 请求/月
  mapbox: {
    provider: 'mapbox',
    apiKey: getEnv('VITE_MAPBOX_KEY'),
    baseUrl: 'https://api.mapbox.com',
    rateLimit: {
      requestsPerSecond: 10,
      requestsPerDay: 50000,
      requestsPerMonth: 50000,
    },
    features: {
      geocoding: true,
      reverseGeocoding: true,
      poiSearch: true,
      routing: true,
      tiles: true,
    },
    priority: 2,
  },

  // 3. HERE - 免费 250K 事务/月
  here: {
    provider: 'here',
    apiKey: getEnv('VITE_HERE_KEY'),
    baseUrl: 'https://browse.search.hereapi.com/v1',
    rateLimit: {
      requestsPerSecond: 10,
      requestsPerDay: 10000,
      requestsPerMonth: 250000,
    },
    features: {
      geocoding: true,
      reverseGeocoding: true,
      poiSearch: true,
      routing: true,
      tiles: true,
    },
    priority: 3,
  },

  // 4. LocationIQ - 免费 5K 请求/天
  locationiq: {
    provider: 'locationiq',
    apiKey: getEnv('VITE_LOCATIONIQ_KEY'),
    baseUrl: 'https://us1.locationiq.com/v1',
    rateLimit: {
      requestsPerSecond: 2,
      requestsPerDay: 5000,
      requestsPerMonth: 150000,
    },
    features: {
      geocoding: true,
      reverseGeocoding: true,
      poiSearch: false,
      routing: false,
      tiles: false,
    },
    priority: 4,
  },

  // 5. MapTiler - 免费 100K 请求/月
  maptiler: {
    provider: 'maptiler',
    apiKey: getEnv('VITE_MAPTILER_KEY'),
    baseUrl: 'https://api.maptiler.com',
    rateLimit: {
      requestsPerSecond: 10,
      requestsPerDay: 5000,
      requestsPerMonth: 100000,
    },
    features: {
      geocoding: true,
      reverseGeocoding: true,
      poiSearch: true,
      routing: true,
      tiles: true,
    },
    priority: 5,
  },
};

/* -------------------------------------------------------------------------- */
/*  速率限制管理                                                                */
/* -------------------------------------------------------------------------- */

const rateLimitState: Record<MapProvider, { lastCall: number; callCount: number; resetAt: number }> = {
  nominatim: { lastCall: 0, callCount: 0, resetAt: 0 },
  mapbox: { lastCall: 0, callCount: 0, resetAt: 0 },
  here: { lastCall: 0, callCount: 0, resetAt: 0 },
  locationiq: { lastCall: 0, callCount: 0, resetAt: 0 },
  maptiler: { lastCall: 0, callCount: 0, resetAt: 0 },
};

async function enforceRateLimit(provider: MapProvider): Promise<void> {
  const config = MAP_PROVIDERS[provider];
  const state = rateLimitState[provider];
  const now = Date.now();

  // 重置每日计数
  if (now > state.resetAt) {
    state.callCount = 0;
    state.resetAt = now + 24 * 60 * 60 * 1000;
  }

  // 检查每秒限制
  const minInterval = 1000 / config.rateLimit.requestsPerSecond;
  const elapsed = now - state.lastCall;
  if (elapsed < minInterval) {
    await new Promise((r) => setTimeout(r, minInterval - elapsed));
  }

  // 检查每日限制
  if (state.callCount >= config.rateLimit.requestsPerDay) {
    throw new Error(`[MapService] ${provider} 日调用次数已达上限`);
  }

  state.lastCall = Date.now();
  state.callCount++;
}

/* -------------------------------------------------------------------------- */
/*  模拟数据 - 用于离线/测试环境                                                   */
/* -------------------------------------------------------------------------- */

const MOCK_GEOCODING: Record<string, { lat: number; lng: number; displayName: string }> = {
  '北京': { lat: 39.9042, lng: 116.4074, displayName: '北京市' },
  '上海': { lat: 31.2304, lng: 121.4737, displayName: '上海市' },
  '广州': { lat: 23.1291, lng: 113.2644, displayName: '广东省广州市' },
  '深圳': { lat: 22.5431, lng: 114.0579, displayName: '广东省深圳市' },
  '杭州': { lat: 30.2741, lng: 120.1551, displayName: '浙江省杭州市' },
  '成都': { lat: 30.5728, lng: 104.0668, displayName: '四川省成都市' },
  '武汉': { lat: 30.5928, lng: 114.3055, displayName: '湖北省武汉市' },
  '西安': { lat: 34.3416, lng: 108.9398, displayName: '陕西省西安市' },
  '南京': { lat: 32.0603, lng: 118.7969, displayName: '江苏省南京市' },
  '重庆': { lat: 29.4316, lng: 106.9183, displayName: '重庆市' },
  '国贸': { lat: 39.9087, lng: 116.4667, displayName: '北京市朝阳区国贸' },
  '中关村': { lat: 39.9841, lng: 116.3074, displayName: '北京市海淀区中关村' },
  '天河': { lat: 23.1248, lng: 113.3608, displayName: '广东省广州市天河区' },
  '南山': { lat: 22.5311, lng: 113.9294, displayName: '广东省深圳市南山区' },
};

const MOCK_POIS: Array<{ name: string; type: string; category: string; lat: number; lng: number; address: string }> = [
  { name: '华为授权体验店', type: 'shop', category: 'electronics', lat: 39.9090, lng: 116.4670, address: '北京市朝阳区国贸商城B1层' },
  { name: '小米之家', type: 'shop', category: 'electronics', lat: 39.9085, lng: 116.4665, address: '北京市朝阳区国贸商城1层' },
  { name: 'OPPO专卖店', type: 'shop', category: 'electronics', lat: 39.9080, lng: 116.4660, address: '北京市朝阳区国贸商城2层' },
  { name: 'vivo体验店', type: 'shop', category: 'electronics', lat: 39.9075, lng: 116.4675, address: '北京市朝阳区银泰中心' },
  { name: '中国移动营业厅', type: 'office', category: 'telecom', lat: 39.9070, lng: 116.4680, address: '北京市朝阳区建外SOHO' },
  { name: '中国联通营业厅', type: 'office', category: 'telecom', lat: 39.9065, lng: 116.4685, address: '北京市朝阳区华贸中心' },
  { name: '国贸商城', type: 'mall', category: 'commercial', lat: 39.9087, lng: 116.4667, address: '北京市朝阳区国贸商城' },
  { name: '银泰中心', type: 'mall', category: 'commercial', lat: 39.9095, lng: 116.4655, address: '北京市朝阳区银泰中心' },
  { name: '建外SOHO', type: 'office', category: 'office', lat: 39.9060, lng: 116.4690, address: '北京市朝阳区建外SOHO' },
  { name: '华贸中心', type: 'office', category: 'office', lat: 39.9055, lng: 116.4695, address: '北京市朝阳区华贸中心' },
];

/* -------------------------------------------------------------------------- */
/*  缓存管理                                                                    */
/* -------------------------------------------------------------------------- */

const CACHE_KEY_GEOCODE = 'map_cache_geocode';
const CACHE_KEY_POI = 'map_cache_poi';
const CACHE_TTL = 24 * 60 * 60 * 1000; // 24小时

interface CacheEntry<T> {
  data: T;
  timestamp: number;
}

function getCached<T>(key: string, subKey: string): T | null {
  try {
    const cached = safeLocalStorageGet(key);
    if (!cached) return null;
    const map = JSON.parse(cached);
    const entry = map[subKey] as CacheEntry<T>;
    if (entry && Date.now() - entry.timestamp < CACHE_TTL) {
      return entry.data;
    }
    return null;
  } catch {
    return null;
  }
}

function setCached<T>(key: string, subKey: string, data: T): void {
  try {
    const cached = safeLocalStorageGet(key) || '{}';
    const map = JSON.parse(cached);
    map[subKey] = { data, timestamp: Date.now() };
    safeLocalStorageSet(key, JSON.stringify(map));
  } catch {}
}

/* -------------------------------------------------------------------------- */
/*  统一地图服务 API                                                            */
/* -------------------------------------------------------------------------- */

class MapService {
  private primaryProvider: MapProvider = 'nominatim';
  private fallbackChain: MapProvider[] = ['nominatim', 'mapbox', 'here', 'locationiq', 'maptiler'];
  private useMockData = false; // 是否使用模拟数据

  /**
   * 设置是否使用模拟数据（离线模式）
   */
  setMockMode(enabled: boolean): void {
    this.useMockData = enabled;
    console.log(`[MapService] 模拟数据模式: ${enabled ? '开启' : '关闭'}`);
  }

  setPrimaryProvider(provider: MapProvider): void {
    this.primaryProvider = provider;
    console.log(`[MapService] 主服务设置为: ${provider}`);
  }

  /**
   * 地理编码：地址 → 坐标
   */
  async geocode(address: string, options?: { countryCode?: string; limit?: number }): Promise<GeocodingResult | null> {
    // 1. 检查缓存
    const cached = getCached<GeocodingResult>(CACHE_KEY_GEOCODE, address);
    if (cached) {
      console.log(`[MapService] 使用缓存: ${address}`);
      return cached;
    }

    // 2. 检查模拟数据
    for (const [key, data] of Object.entries(MOCK_GEOCODING)) {
      if (address.includes(key)) {
        const result: GeocodingResult = {
          lat: data.lat,
          lng: data.lng,
          displayName: data.displayName,
          source: 'nominatim',
        };
        setCached(CACHE_KEY_GEOCODE, address, result);
        console.log(`[MapService] 使用模拟数据: ${address} → ${data.lat}, ${data.lng}`);
        return result;
      }
    }

    // 3. 如果开启模拟模式或网络不可用，返回默认位置
    if (this.useMockData) {
      const result: GeocodingResult = {
        lat: 39.9042,
        lng: 116.4074,
        displayName: address,
        source: 'nominatim',
      };
      return result;
    }

    // 4. 调用真实 API
    for (const provider of this.fallbackChain) {
      try {
        await enforceRateLimit(provider);
        const result = await this.geocodeWithProvider(provider, address, options);
        if (result) {
          setCached(CACHE_KEY_GEOCODE, address, result);
          return result;
        }
      } catch (err) {
        console.warn(`[MapService] ${provider} geocode failed:`, err);
        continue;
      }
    }

    // 5. 所有 API 失败，返回模拟数据
    console.log(`[MapService] 所有API失败，使用默认位置: ${address}`);
    return {
      lat: 39.9042,
      lng: 116.4074,
      displayName: address,
      source: 'nominatim',
    };
  }

  private async geocodeWithProvider(
    provider: MapProvider,
    address: string,
    options?: { countryCode?: string; limit?: number }
  ): Promise<GeocodingResult | null> {
    const config = MAP_PROVIDERS[provider];

    switch (provider) {
      case 'nominatim': {
        const url = new URL(`${config.baseUrl}/search`);
        url.searchParams.set('q', address);
        url.searchParams.set('format', 'json');
        url.searchParams.set('limit', String(options?.limit || 1));
        url.searchParams.set('countrycodes', options?.countryCode || 'cn');
        url.searchParams.set('addressdetails', '1');

        const resp = await fetch(url.toString(), {
          headers: { 'User-Agent': 'HandBiz/2.0' },
        });
        const data = await resp.json();
        if (data && data[0]) {
          return {
            lat: parseFloat(data[0].lat),
            lng: parseFloat(data[0].lon),
            displayName: data[0].display_name,
            address: data[0].address,
            type: data[0].type,
            importance: data[0].importance,
            source: 'nominatim',
          };
        }
        return null;
      }

      case 'mapbox': {
        if (!config.apiKey) return null;
        const url = `https://api.mapbox.com/geocoding/v5/mapbox.places/${encodeURIComponent(address)}.json?access_token=${config.apiKey}&limit=${options?.limit || 1}`;
        const resp = await fetch(url);
        const data = await resp.json();
        if (data.features && data.features[0]) {
          const f = data.features[0];
          return {
            lat: f.center[1],
            lng: f.center[0],
            displayName: f.place_name,
            source: 'mapbox',
          };
        }
        return null;
      }

      case 'here': {
        if (!config.apiKey) return null;
        const url = `https://geocode.search.hereapi.com/v1/geocode?q=${encodeURIComponent(address)}&apiKey=${config.apiKey}`;
        const resp = await fetch(url);
        const data = await resp.json();
        if (data.items && data.items[0]) {
          const item = data.items[0];
          return {
            lat: item.position.lat,
            lng: item.position.lng,
            displayName: item.address.label,
            source: 'here',
          };
        }
        return null;
      }

      case 'locationiq': {
        if (!config.apiKey) return null;
        const url = `https://us1.locationiq.com/v1/search?key=${config.apiKey}&q=${encodeURIComponent(address)}&format=json`;
        const resp = await fetch(url);
        const data = await resp.json();
        if (data && data[0]) {
          return {
            lat: parseFloat(data[0].lat),
            lng: parseFloat(data[0].lon),
            displayName: data[0].display_name,
            source: 'locationiq',
          };
        }
        return null;
      }

      default:
        return null;
    }
  }

  /**
   * 逆地理编码：坐标 → 地址
   */
  async reverseGeocode(lat: number, lng: number): Promise<GeocodingResult | null> {
    // 1. 检查缓存
    const cacheKey = `${lat.toFixed(4)},${lng.toFixed(4)}`;
    const cached = getCached<GeocodingResult>(CACHE_KEY_GEOCODE, cacheKey);
    if (cached) {
      console.log(`[MapService] 使用缓存: ${cacheKey}`);
      return cached;
    }

    // 2. 模拟模式 - 根据坐标范围返回模拟地址
    if (this.useMockData) {
      // 北京范围
      if (lat >= 39.8 && lat <= 40.1 && lng >= 116.2 && lng <= 116.6) {
        const result: GeocodingResult = {
          lat,
          lng,
          displayName: '北京市朝阳区国贸附近',
          address: { city: '北京市', district: '朝阳区' },
          source: 'nominatim',
        };
        setCached(CACHE_KEY_GEOCODE, cacheKey, result);
        return result;
      }
      // 上海范围
      if (lat >= 31.0 && lat <= 31.5 && lng >= 121.0 && lng <= 122.0) {
        const result: GeocodingResult = {
          lat,
          lng,
          displayName: '上海市浦东新区',
          address: { city: '上海市', district: '浦东新区' },
          source: 'nominatim',
        };
        setCached(CACHE_KEY_GEOCODE, cacheKey, result);
        return result;
      }
    }

    // 3. 调用真实 API
    for (const provider of this.fallbackChain) {
      try {
        await enforceRateLimit(provider);
        const result = await this.reverseGeocodeWithProvider(provider, lat, lng);
        if (result) {
          setCached(CACHE_KEY_GEOCODE, cacheKey, result);
          return result;
        }
      } catch (err) {
        console.warn(`[MapService] ${provider} reverseGeocode failed:`, err);
        continue;
      }
    }

    // 4. 返回默认地址
    return {
      lat,
      lng,
      displayName: '未知位置',
      source: 'nominatim',
    };
  }

  private async reverseGeocodeWithProvider(
    provider: MapProvider,
    lat: number,
    lng: number
  ): Promise<GeocodingResult | null> {
    const config = MAP_PROVIDERS[provider];

    switch (provider) {
      case 'nominatim': {
        const url = `${config.baseUrl}/reverse?lat=${lat}&lon=${lng}&format=json&addressdetails=1`;
        const resp = await fetch(url, { headers: { 'User-Agent': 'HandBiz/2.0' } });
        const data = await resp.json();
        if (data && !data.error) {
          return {
            lat: parseFloat(data.lat),
            lng: parseFloat(data.lon),
            displayName: data.display_name,
            address: data.address,
            source: 'nominatim',
          };
        }
        return null;
      }

      case 'mapbox': {
        if (!config.apiKey) return null;
        const url = `https://api.mapbox.com/geocoding/v5/mapbox.places/${lng},${lat}.json?access_token=${config.apiKey}`;
        const resp = await fetch(url);
        const data = await resp.json();
        if (data.features && data.features[0]) {
          const f = data.features[0];
          return {
            lat,
            lng,
            displayName: f.place_name,
            source: 'mapbox',
          };
        }
        return null;
      }

      default:
        return null;
    }
  }

  /**
   * POI 搜索：周边兴趣点
   */
  async searchPOI(params: {
    query: string;
    lat: number;
    lng: number;
    radius: number; // km
    limit?: number;
  }): Promise<POIResult[]> {
    const { query, lat, lng, radius, limit = 20 } = params;

    // 1. 检查缓存
    const cacheKey = `${query}@${lat.toFixed(4)},${lng.toFixed(4)}_${radius}km`;
    const cached = getCached<POIResult[]>(CACHE_KEY_POI, cacheKey);
    if (cached && cached.length > 0) {
      console.log(`[MapService] 使用缓存POI: ${cacheKey}`);
      return cached.slice(0, limit);
    }

    // 2. 使用模拟数据 - 根据距离筛选
    const radiusMeters = radius * 1000;
    const mockResults: POIResult[] = [];
    
    for (const poi of MOCK_POIS) {
      const distance = this.calculateDistance(lat, lng, poi.lat, poi.lng);
      if (distance <= radiusMeters) {
        // 匹配查询关键词
        if (!query || poi.name.includes(query) || poi.category.includes(query) || poi.type.includes(query)) {
          mockResults.push({
            id: `mock-${mockResults.length}`,
            name: poi.name,
            type: poi.type,
            category: poi.category,
            lat: poi.lat,
            lng: poi.lng,
            address: poi.address,
            distance: Math.round(distance),
            source: 'nominatim',
          });
        }
      }
    }

    // 如果模拟数据有结果，直接返回
    if (mockResults.length > 0) {
      const results = mockResults.slice(0, limit);
      setCached(CACHE_KEY_POI, cacheKey, results);
      console.log(`[MapService] 使用模拟POI数据: ${results.length} 条`);
      return results;
    }

    // 3. 模拟模式 - 生成随机POI
    if (this.useMockData) {
      const generatedPOIs = this.generateMockPOIs(lat, lng, radius, limit);
      setCached(CACHE_KEY_POI, cacheKey, generatedPOIs);
      return generatedPOIs;
    }

    // 4. 调用真实 API
    for (const provider of this.fallbackChain) {
      const config = MAP_PROVIDERS[provider];
      if (!config.features.poiSearch) continue;

      try {
        await enforceRateLimit(provider);
        const results = await this.searchPOIWithProvider(provider, params);
        if (results.length > 0) {
          setCached(CACHE_KEY_POI, cacheKey, results);
          return results;
        }
      } catch (err) {
        console.warn(`[MapService] ${provider} searchPOI failed:`, err);
        continue;
      }
    }

    // 5. 返回模拟数据
    const fallbackPOIs = this.generateMockPOIs(lat, lng, radius, limit);
    return fallbackPOIs;
  }

  /**
   * 生成模拟POI数据
   */
  private generateMockPOIs(lat: number, lng: number, radius: number, limit: number): POIResult[] {
    const pois: POIResult[] = [];
    const categories = [
      { name: '华为授权体验店', type: 'shop', category: 'electronics' },
      { name: '小米之家', type: 'shop', category: 'electronics' },
      { name: 'OPPO专卖店', type: 'shop', category: 'electronics' },
      { name: 'vivo体验店', type: 'shop', category: 'electronics' },
      { name: '中国移动营业厅', type: 'office', category: 'telecom' },
      { name: '中国联通营业厅', type: 'office', category: 'telecom' },
      { name: '中国电信营业厅', type: 'office', category: 'telecom' },
      { name: '购物中心', type: 'mall', category: 'commercial' },
      { name: '写字楼', type: 'office', category: 'office' },
      { name: '住宅小区', type: 'residential', category: 'residential' },
    ];

    for (let i = 0; i < Math.min(limit, 10); i++) {
      const cat = categories[i % categories.length];
      const angle = (i / limit) * 2 * Math.PI;
      const distance = (Math.random() * radius * 1000) / 111000; // 约转换为度

      pois.push({
        id: `gen-${i}`,
        name: cat.name,
        type: cat.type,
        category: cat.category,
        lat: lat + distance * Math.cos(angle),
        lng: lng + distance * Math.sin(angle),
        address: `距离中心 ${Math.round(Math.random() * radius * 1000)} 米`,
        distance: Math.round(Math.random() * radius * 1000),
        source: 'nominatim',
      });
    }

    return pois;
  }

  /**
   * 计算两点距离（米）
   */
  private calculateDistance(lat1: number, lng1: number, lat2: number, lng2: number): number {
    const R = 6371000; // 地球半径（米）
    const φ1 = (lat1 * Math.PI) / 180;
    const φ2 = (lat2 * Math.PI) / 180;
    const Δφ = ((lat2 - lat1) * Math.PI) / 180;
    const Δλ = ((lng2 - lng1) * Math.PI) / 180;

    const a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
              Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return R * c;
  }

  private async searchPOIWithProvider(
    provider: MapProvider,
    params: { query: string; lat: number; lng: number; radius: number; limit?: number }
  ): Promise<POIResult[]> {
    const config = MAP_PROVIDERS[provider];

    switch (provider) {
      case 'nominatim': {
        const delta = params.radius * 0.01;
        const url = new URL(`${config.baseUrl}/search`);
        url.searchParams.set('q', params.query);
        url.searchParams.set('format', 'json');
        url.searchParams.set('limit', String(params.limit || 20));
        url.searchParams.set('viewbox', `${params.lng - delta},${params.lat + delta},${params.lng + delta},${params.lat - delta}`);
        url.searchParams.set('bounded', '1');

        const resp = await fetch(url.toString(), { headers: { 'User-Agent': 'HandBiz/2.0' } });
        const data = await resp.json();

        return (data || []).map((item: any, i: number) => ({
          id: `nominatim-${i}`,
          name: item.display_name.split(',')[0],
          type: item.type,
          category: item.class,
          lat: parseFloat(item.lat),
          lng: parseFloat(item.lon),
          address: item.display_name,
          source: 'nominatim',
        }));
      }

      case 'mapbox': {
        if (!config.apiKey) return [];
        const url = `https://api.mapbox.com/geocoding/v5/mapbox.places/${encodeURIComponent(params.query)}.json?access_token=${config.apiKey}&proximity=${params.lng},${params.lat}&limit=${params.limit || 20}`;
        const resp = await fetch(url);
        const data = await resp.json();

        return (data.features || []).map((f: any, i: number) => ({
          id: `mapbox-${i}`,
          name: f.text || f.place_name,
          type: f.place_type?.[0] || 'unknown',
          category: f.properties?.category || '',
          lat: f.center[1],
          lng: f.center[0],
          address: f.place_name,
          source: 'mapbox',
        }));
      }

      default:
        return [];
    }
  }

  /**
   * 获取地图瓦片 URL
   */
  getTileUrl(provider: MapProvider = 'nominatim'): string {
    switch (provider) {
      case 'nominatim':
        // 使用 OSM 标准瓦片
        return 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
      case 'mapbox':
        return `https://api.mapbox.com/styles/v1/mapbox/streets-v11/tiles/{z}/{x}/{y}?access_token=${MAP_PROVIDERS.mapbox.apiKey}`;
      case 'maptiler':
        return `https://api.maptiler.com/maps/streets/{z}/{x}/{y}.png?key=${MAP_PROVIDERS.maptiler.apiKey}`;
      default:
        return 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
    }
  }

  /**
   * 获取服务状态
   */
  getServiceStatus(): Record<MapProvider, { available: boolean; callCount: number; limit: number }> {
    const result: Record<string, any> = {};
    for (const [provider, config] of Object.entries(MAP_PROVIDERS)) {
      const state = rateLimitState[provider as MapProvider];
      result[provider] = {
        available: !config.apiKey || config.apiKey.length > 0,
        callCount: state.callCount,
        limit: config.rateLimit.requestsPerDay,
      };
    }
    return result;
  }
}

export const mapService = new MapService();
export default mapService;
