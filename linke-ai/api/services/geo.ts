/**
 * 模拟地理服务:基于门店经纬度生成同心圆 + 高潜 POI,
 * 不依赖外部地图厂商,方便离线开发与演示。
 */
export interface POI {
  id: string;
  name: string;
  category: 'office' | 'mall' | 'school' | 'residence' | 'subway' | 'park';
  lng: number;
  lat: number;
  hotScore: number; // 0-100
  radiusKm: 3 | 5 | 8 | 10;
}

export interface RadiusStats {
  km: 3 | 5 | 8 | 10;
  population: number;
  hotSpots: number;
  avgScore: number;
  competitorCount: number;
}

const KM_PER_DEG_LAT = 111;
const kmToDegLng = (km: number, lat: number) => km / (KM_PER_DEG_LAT * Math.cos((lat * Math.PI) / 180));
const kmToDegLat = (km: number) => km / KM_PER_DEG_LAT;

const POI_NAMES: Record<POI['category'], string[]> = {
  office: ['华贸中心 B 座', '国贸三期', '金融街中心', '万达广场写字楼', '阿里中心 · 北京', '腾讯北京总部', '京东大厦', '银河 SOHO'],
  mall: ['朝阳大悦城', '三里屯太古里', 'SKP', '合生汇', '侨福芳草地', '西单大悦城', '银泰中心', '龙湖长楹天街'],
  school: ['清华附中朝阳学校', '人大附中朝阳分校', '陈经纶中学', '芳草地国际学校', '北京中学', '八十中'],
  residence: ['润枫·御景湾', '远洋天地', '富力城', '苹果社区', '建外 SOHO', '珠江帝景', '阳光 100', '棕榈泉国际公寓'],
  subway: ['国贸站', '大望路站', '四惠东站', '四惠站', '双井站', '劲松站', '团结湖站'],
  park: ['朝阳公园', '团结湖公园', '红领巾公园', '日坛公园', '通惠河沿岸'],
};

// 简单的伪随机(基于种子)
const seededRandom = (seed: number) => {
  let s = seed;
  return () => {
    s = (s * 9301 + 49297) % 233280;
    return s / 233280;
  };
};

const genPoisForRadius = (centerLng: number, centerLat: number, km: 3 | 5 | 8 | 10, storeId: string): POI[] => {
  const rand = seededRandom(storeId.charCodeAt(storeId.length - 1) * 1000 + km);
  const count = km === 3 ? 8 : km === 5 ? 14 : km === 8 ? 22 : 30;
  const result: POI[] = [];
  const categories: POI['category'][] = ['office', 'mall', 'school', 'residence', 'subway', 'park'];

  for (let i = 0; i < count; i++) {
    // 在半径 km 内均匀分布
    const r = Math.sqrt(rand()) * (km - 0.4);
    const theta = rand() * Math.PI * 2;
    const dLng = kmToDegLng(r, centerLat) * Math.cos(theta);
    const dLat = kmToDegLat(r) * Math.sin(theta);
    const cat = categories[Math.floor(rand() * categories.length)];
    const name = `${POI_NAMES[cat][Math.floor(rand() * POI_NAMES[cat].length)]}·${km}km`;
    // 越靠近中心 / 越近半径,评分越高
    const distFactor = 1 - r / km;
    const hotScore = Math.round(50 + distFactor * 35 + rand() * 15);
    result.push({
      id: `${storeId}_${km}_poi_${i}`,
      name,
      category: cat,
      lng: centerLng + dLng,
      lat: centerLat + dLat,
      hotScore,
      radiusKm: km,
    });
  }
  return result;
};

export const buildRadiusPayload = (centerLng: number, centerLat: number, storeId: string) => {
  const kms: (3 | 5 | 8 | 10)[] = [3, 5, 8, 10];
  const pois: POI[] = [];
  const stats: RadiusStats[] = kms.map((km) => {
    const list = genPoisForRadius(centerLng, centerLat, km, storeId);
    pois.push(...list);
    const avg = list.reduce((s, p) => s + p.hotScore, 0) / list.length;
    return {
      km,
      population: km === 3 ? 124800 : km === 5 ? 312600 : km === 8 ? 689100 : 1245000,
      hotSpots: list.filter((p) => p.hotScore >= 70).length,
      avgScore: Math.round(avg),
      competitorCount: km === 3 ? 4 : km === 5 ? 9 : km === 8 ? 17 : 28,
    };
  });

  // 构造同心圆 Polygon
  const circles = kms.map((km) => ({
    type: 'Feature',
    properties: { km, kind: 'ring' },
    geometry: buildCirclePolygon(centerLng, centerLat, km),
  }));

  // POI 转 Feature
  const poiFeatures = pois.map((p) => ({
    type: 'Feature',
    properties: {
      id: p.id,
      name: p.name,
      category: p.category,
      hotScore: p.hotScore,
      radiusKm: p.radiusKm,
      kind: 'poi',
    },
    geometry: { type: 'Point', coordinates: [p.lng, p.lat] },
  }));

  const center = {
    type: 'Feature',
    properties: { kind: 'center' },
    geometry: { type: 'Point', coordinates: [centerLng, centerLat] },
  };

  return {
    stats,
    pois,
    geojson: {
      type: 'FeatureCollection' as const,
      features: [...circles, center, ...poiFeatures],
    },
  };
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
