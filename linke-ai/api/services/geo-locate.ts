/**
 * 实时定位服务
 * 模拟百度/高德/腾讯地图的反查接口:
 *  - /api/geo/reverse:经纬度 → 省/市/区县/POI
 *  - /api/stores/nearest:经纬度 → 最近的门店
 *  - /api/poi/nearby:经纬度 → 附近的真实 POI
 *
 * 实际生产应调用:
 *  - 高德:https://restapi.amap.com/v3/geocode/regeo
 *  - 百度:https://api.map.baidu.com/geocoder/v2/
 *  - 腾讯:https://apis.map.qq.com/ws/geocoder/v1/
 */
import { NATIONAL_POI, estimateAudience, type RealPOI } from './poi-data.js';
import { db } from '../db/store.js';

const KM_PER_DEG = 111; // 1 度 ≈ 111 km

export const haversineKm = (lng1: number, lat1: number, lng2: number, lat2: number): number => {
  const dx = (lng1 - lng2) * Math.cos((lat1 + lat2) / 2 * Math.PI / 180);
  const dy = lat1 - lat2;
  return Math.sqrt(dx * dx + dy * dy) * KM_PER_DEG;
};

/**
 * 反查经纬度:从 NATIONAL_POI 中找到最近的真实地址
 * 返回结构: { province, city, district, address, poi, lng, lat, distance }
 */
export const reverseGeocode = (lng: number, lat: number) => {
  if (!NATIONAL_POI.length) {
    return {
      province: '未知', city: '未知', district: '未知', address: `${lng.toFixed(4)}, ${lat.toFixed(4)}`,
      poi: null, lng, lat, distance: 0,
    };
  }
  // 找最近的 POI
  let nearest = NATIONAL_POI[0];
  let minDist = haversineKm(lng, lat, nearest.lng, nearest.lat);
  for (let i = 1; i < NATIONAL_POI.length; i++) {
    const p = NATIONAL_POI[i];
    const d = haversineKm(lng, lat, p.lng, p.lat);
    if (d < minDist) {
      minDist = d;
      nearest = p;
    }
  }
  return {
    province: nearest.province,
    city: nearest.city,
    district: nearest.district || '未知',
    address: nearest.address || nearest.name,
    poi: { id: nearest.id, name: nearest.name, category: nearest.category, scale: nearest.scale },
    lng: nearest.lng,
    lat: nearest.lat,
    lngRaw: lng,
    latRaw: lat,
    distance: +minDist.toFixed(2), // 距离最近 POI 的真实距离(km)
  };
};

/**
 * 找最近的门店
 */
export const findNearestStore = (lng: number, lat: number) => {
  const data = db.read();
  if (!data.stores.length) return null;
  let nearest = data.stores[0];
  let minDist = haversineKm(lng, lat, nearest.lng, nearest.lat);
  for (let i = 1; i < data.stores.length; i++) {
    const s = data.stores[i];
    const d = haversineKm(lng, lat, s.lng, s.lat);
    if (d < minDist) {
      minDist = d;
      nearest = s;
    }
  }
  return { ...nearest, distance: +minDist.toFixed(2) };
};

/**
 * 找附近 POI(按距离 + 类别)
 */
export const findNearbyPois = (lng: number, lat: number, radiusKm: number, category?: string) => {
  return NATIONAL_POI
    .map((p) => ({ poi: p, dist: +haversineKm(lng, lat, p.lng, p.lat).toFixed(2) }))
    .filter((x) => x.dist <= radiusKm)
    .filter((x) => (category ? x.poi.category === category : true))
    .sort((a, b) => a.dist - b.dist)
    .slice(0, 30)
    .map((x) => ({
      ...x.poi,
      distance: x.dist,
      audience: estimateAudience(x.poi),
    }));
};

export interface RealtimeLocateInput {
  lng: number;
  lat: number;
  accuracy?: number; // 浏览器 Geolocation 精度(米)
  source?: 'browser' | 'manual' | 'ip';
}

export interface RealtimeLocateResult {
  position: { lng: number; lat: number; accuracy?: number; source?: string };
  address: { province: string; city: string; district: string; detail: string; nearestPoi: { name: string; category: string } | null };
  nearestStore: (ReturnType<typeof findNearestStore>) | null;
  nearbyPoiCount: number;
  candidates: { id: string; name: string; city: string; district: string; lng: number; lat: number; distance: number }[];
}

/**
 * 一站式实时定位:经纬度 → 完整地址 + 门店候选
 */
export const realtimeLocate = (input: RealtimeLocateInput): RealtimeLocateResult => {
  const { lng, lat, accuracy, source } = input;
  const rev = reverseGeocode(lng, lat);
  const store = findNearestStore(lng, lat);

  // 5 km 内 POI 数量
  const nearbyPois = NATIONAL_POI
    .map((p) => ({ p, d: haversineKm(lng, lat, p.lng, p.lat) }))
    .filter((x) => x.d <= 5);

  // 候选门店:距离最近的 5 家
  const data = db.read();
  const candidates = data.stores
    .map((s) => ({ ...s, distance: +haversineKm(lng, lat, s.lng, s.lat).toFixed(2) }))
    .sort((a, b) => a.distance - b.distance)
    .slice(0, 5)
    .map((s) => ({
      id: s.id, name: s.name, city: s.address, district: s.address, lng: s.lng, lat: s.lat, distance: s.distance,
    }));

  return {
    position: { lng, lat, accuracy, source },
    address: {
      province: rev.province,
      city: rev.city,
      district: rev.district,
      detail: rev.address,
      nearestPoi: rev.poi ? { name: rev.poi.name, category: rev.poi.category } : null,
    },
    nearestStore: store,
    nearbyPoiCount: nearbyPois.length,
    candidates,
  };
};
