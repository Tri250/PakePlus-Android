/**
 * 真实地理服务:基于公开建筑/小区/商场 POI 数据,
 * 计算同心圆内 POI 圈层与可触达客户数(用于"一键拓客")
 */
import { NATIONAL_POI, estimateAudience, type RealPOI } from './poi-data.js';

export type POICategory = 'office' | 'mall' | 'school' | 'residence' | 'subway' | 'park';

export interface POI {
  id: string;
  name: string;
  category: POICategory;
  lng: number;
  lat: number;
  hotScore: number; // 0-100(基于规模 + 距离)
  radiusKm: 3 | 5 | 8 | 10;
  audience: number; // 估算可触达客户数
  scale: number; // POI 原始规模
}

export interface RadiusStats {
  km: 3 | 5 | 8 | 10;
  population: number;        // 周边常驻人口估算
  hotSpots: number;         // 圈层内 POI 数量
  avgScore: number;         // 平均高潜指数
  competitorCount: number;  // 同类竞品(同 category)估算
  reachableCustomers: number; // 圈层可触达客户数
}

const KM_PER_DEG_LAT = 111;
const kmToDegLng = (km: number, lat: number) => km / (KM_PER_DEG_LAT * Math.cos((lat * Math.PI) / 180));
const kmToDegLat = (km: number) => km / KM_PER_DEG_LAT;

export const haversineKm = (lng1: number, lat1: number, lng2: number, lat2: number) => {
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(a));
};

interface BuildOptions {
  category?: string; // 门店品类,用于计算同类竞品
}

export const buildRadiusPayload = (
  centerLng: number,
  centerLat: number,
  storeId: string,
  options: BuildOptions = {},
) => {
  const kms: (3 | 5 | 8 | 10)[] = [3, 5, 8, 10];
  const allPois: POI[] = [];

  for (const km of kms) {
    NATIONAL_POI.forEach((raw, i) => {
      const dist = haversineKm(centerLng, centerLat, raw.lng, raw.lat);
      if (dist <= km) {
        // 高潜指数:基于规模(0-40) + 距离衰减(0-60)
        const sizeScore = Math.min(40, Math.log10(raw.scale + 1) * 8);
        const distScore = Math.max(0, 60 * (1 - dist / km));
        const hotScore = Math.min(100, Math.round(sizeScore + distScore));
        allPois.push({
          id: `${storeId}_${km}_poi_${i}`,
          name: raw.name,
          category: raw.category,
          lng: raw.lng,
          lat: raw.lat,
          hotScore,
          radiusKm: km,
          audience: estimateAudience(raw),
          scale: raw.scale,
        });
      }
    });
  }

  // 同圈层(同 category) POI 去重,只保留最远圈层(避免重复)
  const uniqByName = new Map<string, POI>();
  for (const p of allPois) {
    const cur = uniqByName.get(p.name);
    if (!cur || p.radiusKm < cur.radiusKm) uniqByName.set(p.name, p);
  }
  const dedupPois = Array.from(uniqByName.values());

  const stats: RadiusStats[] = kms.map((km) => {
    const inRing = dedupPois.filter((p) => p.radiusKm === km);
    const reachable = inRing.reduce((s, p) => s + p.audience, 0);
    const avg = inRing.length ? inRing.reduce((s, p) => s + p.hotScore, 0) / inRing.length : 0;
    // 同类竞品估算:按门店品类同 category 的 POI
    const sameCat = inRing.filter((p) => p.category === (options.category || '')).length;
    return {
      km,
      population: reachable,
      hotSpots: inRing.filter((p) => p.hotScore >= 70).length,
      avgScore: Math.round(avg),
      competitorCount: sameCat,
      reachableCustomers: reachable,
    };
  });

  // 同心圆 Polygon
  const circles = kms.map((km) => ({
    type: 'Feature' as const,
    properties: { km, kind: 'ring' },
    geometry: buildCirclePolygon(centerLng, centerLat, km),
  }));

  // POI Feature
  const poiFeatures = dedupPois.map((p) => ({
    type: 'Feature' as const,
    properties: {
      id: p.id,
      name: p.name,
      category: p.category,
      hotScore: p.hotScore,
      radiusKm: p.radiusKm,
      audience: p.audience,
      kind: 'poi',
    },
    geometry: { type: 'Point' as const, coordinates: [p.lng, p.lat] },
  }));

  const center = {
    type: 'Feature' as const,
    properties: { kind: 'center' },
    geometry: { type: 'Point' as const, coordinates: [centerLng, centerLat] },
  };

  return {
    stats,
    pois: dedupPois,
    geojson: {
      type: 'FeatureCollection' as const,
      features: [...circles, center, ...poiFeatures],
    },
  };
};

// 给定圈层,从 POI 批量生成"可触达线索"(可指定数量上限)
export const generateLeadsFromPois = (
  centerLng: number,
  centerLat: number,
  radiusKm: 3 | 5 | 8 | 10,
  storeId: string,
  max: number = 30,
) => {
  const result = buildRadiusPayload(centerLng, centerLat, storeId);
  const candidates = result.pois
    .filter((p) => p.radiusKm <= radiusKm)
    .sort((a, b) => b.hotScore - a.hotScore)
    .slice(0, max);

  const surnames = ['王', '李', '张', '陈', '刘', '赵', '孙', '周', '吴', '郑', '黄', '马', '林', '郭', '何', '高', '罗'];
  const givenNames = ['女士', '先生'];
  const leads = candidates.flatMap((p, i) => {
    // 每个高潜 POI 生成 1-2 个客户
    const leadCount = p.hotScore >= 80 ? 2 : 1;
    return Array.from({ length: leadCount }, (_, k) => {
      const surname = surnames[(i * 3 + k) % surnames.length];
      const title = givenNames[(i + k) % givenNames.length];
      return {
        name: `${surname}${title}`,
        phone: `139${String((i * 31 + k * 7 + 1000) % 1e8).padStart(8, '0')}`,
        sourcePoi: p.name,
        sourcePoiCategory: p.category,
        hotScore: p.hotScore,
      };
    });
  });

  return leads.slice(0, max);
};

const buildCirclePolygon = (lng: number, lat: number, km: number, steps = 64) => {
  const coords: [number, number][] = [];
  for (let i = 0; i <= steps; i++) {
    const t = (i / steps) * Math.PI * 2;
    const dLng = kmToDegLng(km, lat) * Math.cos(t);
    const dLat = kmToDegLat(km) * Math.sin(t);
    coords.push([lng + dLng, lat + dLat]);
  }
  return { type: 'Polygon' as const, coordinates: [coords] };
};

export { NATIONAL_POI };
export type { RealPOI };
