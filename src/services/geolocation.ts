/**
 * 地理定位服务
 * 支持浏览器 Geolocation API 和 IP 定位
 */

import { getEnv } from './env';

export interface LocationResult {
  lat: number;
  lng: number;
  accuracy: number;
  address?: string;
  source: 'gps' | 'ip' | 'cache' | 'manual';
  timestamp: number;
}

export interface WatchPositionOptions {
  enableHighAccuracy?: boolean;
  timeout?: number;
  maximumAge?: number;
}

/* -------------------------------------------------------------------------- */
/*  模拟数据 - 中国主要城市坐标                                                    */
/* -------------------------------------------------------------------------- */

const MAJOR_CITIES: Record<string, { lat: number; lng: number; address: string }> = {
  '北京': { lat: 39.9042, lng: 116.4074, address: '北京市' },
  '上海': { lat: 31.2304, lng: 121.4737, address: '上海市' },
  '广州': { lat: 23.1291, lng: 113.2644, address: '广东省广州市' },
  '深圳': { lat: 22.5431, lng: 114.0579, address: '广东省深圳市' },
  '杭州': { lat: 30.2741, lng: 120.1551, address: '浙江省杭州市' },
  '成都': { lat: 30.5728, lng: 104.0668, address: '四川省成都市' },
  '武汉': { lat: 30.5928, lng: 114.3055, address: '湖北省武汉市' },
  '西安': { lat: 34.3416, lng: 108.9398, address: '陕西省西安市' },
  '南京': { lat: 32.0603, lng: 118.7969, address: '江苏省南京市' },
  '重庆': { lat: 29.4316, lng: 106.9183, address: '重庆市' },
  '天津': { lat: 39.0839, lng: 117.2000, address: '天津市' },
  '苏州': { lat: 31.2990, lng: 120.5858, address: '江苏省苏州市' },
  '郑州': { lat: 34.7466, lng: 113.6254, address: '河南省郑州市' },
  '长沙': { lat: 28.2282, lng: 112.9388, address: '湖南省长沙市' },
  '青岛': { lat: 36.0671, lng: 120.3826, address: '山东省青岛市' },
  '厦门': { lat: 24.4798, lng: 118.0894, address: '福建省厦门市' },
  '福州': { lat: 26.0745, lng: 119.2965, address: '福建省福州市' },
  '合肥': { lat: 31.8206, lng: 117.2272, address: '安徽省合肥市' },
  '昆明': { lat: 25.0389, lng: 102.7183, address: '云南省昆明市' },
  '哈尔滨': { lat: 45.8038, lng: 126.5350, address: '黑龙江省哈尔滨市' },
};

// 默认位置：北京国贸
const DEFAULT_LOCATION: LocationResult = {
  lat: 39.9087,
  lng: 116.4667,
  accuracy: 100,
  address: '北京市朝阳区国贸',
  source: 'manual',
  timestamp: Date.now(),
};

/* -------------------------------------------------------------------------- */
/*  缓存管理                                                                    */
/* -------------------------------------------------------------------------- */

let cachedLocation: LocationResult | null = null;
const CACHE_KEY = 'geolocation_cache';
const CACHE_TTL = 30 * 60 * 1000; // 30分钟

function loadCache(): LocationResult | null {
  if (cachedLocation) return cachedLocation;
  
  try {
    const saved = typeof localStorage !== 'undefined' 
      ? localStorage.getItem(CACHE_KEY) 
      : null;
    if (saved) {
      const parsed = JSON.parse(saved);
      if (Date.now() - parsed.timestamp < CACHE_TTL) {
        cachedLocation = parsed;
        return parsed;
      }
    }
  } catch {}
  return null;
}

function saveCache(location: LocationResult): void {
  cachedLocation = location;
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(CACHE_KEY, JSON.stringify(location));
    }
  } catch {}
}

/* -------------------------------------------------------------------------- */
/*  定位服务                                                                    */
/* -------------------------------------------------------------------------- */

class GeolocationService {
  private watchId: number | null = null;
  private listeners: Array<(location: LocationResult) => void> = [];

  /**
   * 获取当前位置（优先级：GPS > IP > 缓存 > 默认）
   */
  async getCurrentLocation(): Promise<LocationResult> {
    // 1. 检查缓存
    const cached = loadCache();
    if (cached) {
      console.log('[Geolocation] 使用缓存位置:', cached.address);
      return { ...cached, source: 'cache' };
    }

    // 2. 尝试 GPS 定位
    if (typeof navigator !== 'undefined' && navigator.geolocation) {
      try {
        const gpsLocation = await this.getGPSLocation();
        saveCache(gpsLocation);
        return gpsLocation;
      } catch (err) {
        console.warn('[Geolocation] GPS定位失败:', err);
      }
    }

    // 3. 尝试 IP 定位
    try {
      const ipLocation = await this.getIPLocation();
      saveCache(ipLocation);
      return ipLocation;
    } catch (err) {
      console.warn('[Geolocation] IP定位失败:', err);
    }

    // 4. 返回默认位置
    console.log('[Geolocation] 使用默认位置');
    return DEFAULT_LOCATION;
  }

  /**
   * GPS 定位
   */
  private getGPSLocation(): Promise<LocationResult> {
    return new Promise((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          resolve({
            lat: position.coords.latitude,
            lng: position.coords.longitude,
            accuracy: position.coords.accuracy,
            source: 'gps',
            timestamp: position.timestamp,
          });
        },
        (error) => {
          reject(new Error(`GPS错误: ${error.message}`));
        },
        {
          enableHighAccuracy: true,
          timeout: 10000,
          maximumAge: 60000,
        }
      );
    });
  }

  /**
   * IP 定位（使用免费服务）
   */
  private async getIPLocation(): Promise<LocationResult> {
    // 使用 ip-api.com 免费服务
    const resp = await fetch('http://ip-api.com/json/?lang=zh-CN');
    if (!resp.ok) throw new Error('IP定位服务不可用');
    
    const data = await resp.json();
    if (data.status !== 'success') throw new Error('IP定位失败');
    
    return {
      lat: data.lat,
      lng: data.lon,
      accuracy: 5000, // IP定位精度约5km
      address: `${data.country}${data.regionName}${data.city}`,
      source: 'ip',
      timestamp: Date.now(),
    };
  }

  /**
   * 持续监听位置变化
   */
  watchPosition(
    callback: (location: LocationResult) => void,
    options?: WatchPositionOptions
  ): () => void {
    this.listeners.push(callback);

    if (typeof navigator !== 'undefined' && navigator.geolocation && !this.watchId) {
      this.watchId = navigator.geolocation.watchPosition(
        (position) => {
          const location: LocationResult = {
            lat: position.coords.latitude,
            lng: position.coords.longitude,
            accuracy: position.coords.accuracy,
            source: 'gps',
            timestamp: position.timestamp,
          };
          saveCache(location);
          this.listeners.forEach((cb) => cb(location));
        },
        (error) => {
          console.warn('[Geolocation] watchPosition error:', error);
        },
        {
          enableHighAccuracy: options?.enableHighAccuracy ?? true,
          timeout: options?.timeout ?? 10000,
          maximumAge: options?.maximumAge ?? 60000,
        }
      );
    }

    // 返回取消监听函数
    return () => {
      const index = this.listeners.indexOf(callback);
      if (index > -1) this.listeners.splice(index, 1);
      
      if (this.listeners.length === 0 && this.watchId !== null) {
        navigator.geolocation.clearWatch(this.watchId);
        this.watchId = null;
      }
    };
  }

  /**
   * 手动设置位置
   */
  setLocation(location: Partial<LocationResult>): LocationResult {
    const newLocation: LocationResult = {
      lat: location.lat ?? DEFAULT_LOCATION.lat,
      lng: location.lng ?? DEFAULT_LOCATION.lng,
      accuracy: location.accuracy ?? 10,
      address: location.address ?? '',
      source: 'manual',
      timestamp: Date.now(),
    };
    saveCache(newLocation);
    this.listeners.forEach((cb) => cb(newLocation));
    return newLocation;
  }

  /**
   * 根据城市名获取坐标
   */
  getCityLocation(cityName: string): LocationResult | null {
    const city = MAJOR_CITIES[cityName];
    if (!city) return null;
    
    return {
      lat: city.lat,
      lng: city.lng,
      accuracy: 1000,
      address: city.address,
      source: 'manual',
      timestamp: Date.now(),
    };
  }

  /**
   * 获取所有支持的城市
   */
  getSupportedCities(): string[] {
    return Object.keys(MAJOR_CITIES);
  }

  /**
   * 计算两点距离（米）
   */
  calculateDistance(lat1: number, lng1: number, lat2: number, lng2: number): number {
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

  /**
   * 判断点是否在范围内
   */
  isInRadius(
    centerLat: number,
    centerLng: number,
    pointLat: number,
    pointLng: number,
    radiusMeters: number
  ): boolean {
    return this.calculateDistance(centerLat, centerLng, pointLat, pointLng) <= radiusMeters;
  }
}

export const geolocationService = new GeolocationService();
export default geolocationService;
