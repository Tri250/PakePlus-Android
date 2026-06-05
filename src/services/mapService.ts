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
    apiKey: import.meta.env.VITE_MAPBOX_KEY || '',
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
    apiKey: import.meta.env.VITE_HERE_KEY || '',
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
    apiKey: import.meta.env.VITE_LOCATIONIQ_KEY || '',
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
    apiKey: import.meta.env.VITE_MAPTILER_KEY || '',
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
/*  统一地图服务 API                                                            */
/* -------------------------------------------------------------------------- */

class MapService {
  private primaryProvider: MapProvider = 'nominatim';
  private fallbackChain: MapProvider[] = ['nominatim', 'mapbox', 'here', 'locationiq', 'maptiler'];

  setPrimaryProvider(provider: MapProvider): void {
    this.primaryProvider = provider;
    console.log(`[MapService] 主服务设置为: ${provider}`);
  }

  /**
   * 地理编码：地址 → 坐标
   */
  async geocode(address: string, options?: { countryCode?: string; limit?: number }): Promise<GeocodingResult | null> {
    for (const provider of this.fallbackChain) {
      try {
        await enforceRateLimit(provider);
        const result = await this.geocodeWithProvider(provider, address, options);
        if (result) return result;
      } catch (err) {
        console.warn(`[MapService] ${provider} geocode failed:`, err);
        continue;
      }
    }
    return null;
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
    for (const provider of this.fallbackChain) {
      try {
        await enforceRateLimit(provider);
        const result = await this.reverseGeocodeWithProvider(provider, lat, lng);
        if (result) return result;
      } catch (err) {
        console.warn(`[MapService] ${provider} reverseGeocode failed:`, err);
        continue;
      }
    }
    return null;
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
    for (const provider of this.fallbackChain) {
      const config = MAP_PROVIDERS[provider];
      if (!config.features.poiSearch) continue;

      try {
        await enforceRateLimit(provider);
        const results = await this.searchPOIWithProvider(provider, params);
        if (results.length > 0) return results;
      } catch (err) {
        console.warn(`[MapService] ${provider} searchPOI failed:`, err);
        continue;
      }
    }
    return [];
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
