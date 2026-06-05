/**
 * Nominatim (OpenStreetMap) 免费地理编码服务
 * 完全免费开源 · 无需 Key · 1 QPS 速率限制
 * 适用场景：高德/腾讯 API 临时不可用时的降级兜底
 */

export interface NominatimResult {
  lat: number;
  lng: number;
  displayName: string;
  type: string;
  importance: number;
}

export interface NominatimSearchParams {
  query: string;
  limit?: number;
  countryCode?: string;
}

// 速率限制：1 QPS
let lastCallTime = 0;
const MIN_INTERVAL = 1100; // 1.1 秒

async function enforceRateLimit() {
  const now = Date.now();
  const elapsed = now - lastCallTime;
  if (elapsed < MIN_INTERVAL) {
    await new Promise((r) => setTimeout(r, MIN_INTERVAL - elapsed));
  }
  lastCallTime = Date.now();
}

/**
 * Nominatim 地理编码（地址 → 坐标）
 */
export async function nominatimGeocode(params: NominatimSearchParams): Promise<NominatimResult | null> {
  await enforceRateLimit();

  const url = new URL('https://nominatim.openstreetmap.org/search');
  url.searchParams.set('q', params.query);
  url.searchParams.set('format', 'json');
  url.searchParams.set('limit', String(params.limit || 1));
  url.searchParams.set('countrycodes', params.countryCode || 'cn');
  url.searchParams.set('addressdetails', '1');

  try {
    const resp = await fetch(url.toString(), {
      headers: {
        'User-Agent': 'ZhangShangShangKe/2.0 (contact@handbiz.com)',
        'Accept': 'application/json',
      },
    });

    if (!resp.ok) {
      console.error('[Nominatim] HTTP error:', resp.status);
      return null;
    }

    const data = await resp.json();
    if (!Array.isArray(data) || data.length === 0) {
      return null;
    }

    const first = data[0];
    return {
      lat: parseFloat(first.lat),
      lng: parseFloat(first.lon),
      displayName: first.display_name,
      type: first.type,
      importance: first.importance || 0,
    };
  } catch (err) {
    console.error('[Nominatim] Network error:', err);
    return null;
  }
}

/**
 * Nominatim 逆地理编码（坐标 → 地址）
 */
export async function nominatimReverseGeocode(lat: number, lng: number): Promise<NominatimResult | null> {
  await enforceRateLimit();

  const url = new URL('https://nominatim.openstreetmap.org/reverse');
  url.searchParams.set('lat', String(lat));
  url.searchParams.set('lon', String(lng));
  url.searchParams.set('format', 'json');
  url.searchParams.set('addressdetails', '1');

  try {
    const resp = await fetch(url.toString(), {
      headers: {
        'User-Agent': 'ZhangShangShangKe/2.0 (contact@handbiz.com)',
        'Accept': 'application/json',
      },
    });

    if (!resp.ok) {
      console.error('[Nominatim Reverse] HTTP error:', resp.status);
      return null;
    }

    const data = await resp.json();
    if (!data || data.error) {
      return null;
    }

    return {
      lat: parseFloat(data.lat),
      lng: parseFloat(data.lon),
      displayName: data.display_name,
      type: data.type,
      importance: 1,
    };
  } catch (err) {
    console.error('[Nominatim Reverse] Network error:', err);
    return null;
  }
}

/**
 * 搜索 POI（使用 Nominatim 的 search 端点）
 */
export async function nominatimSearchPOI(params: {
  query: string;
  nearLat?: number;
  nearLng?: number;
  radiusKm?: number;
}): Promise<NominatimResult[]> {
  await enforceRateLimit();

  const url = new URL('https://nominatim.openstreetmap.org/search');
  url.searchParams.set('q', params.query);
  url.searchParams.set('format', 'json');
  url.searchParams.set('limit', '10');
  url.searchParams.set('countrycodes', 'cn');
  url.searchParams.set('addressdetails', '1');

  // 如果有中心点，添加 viewbox 限制范围
  if (params.nearLat && params.nearLng && params.radiusKm) {
    const delta = params.radiusKm * 0.01; // 粗略转换
    url.searchParams.set('viewbox', [
      params.nearLng - delta,
      params.nearLat + delta,
      params.nearLng + delta,
      params.nearLat - delta,
    ].join(','));
    url.searchParams.set('bounded', '1');
  }

  try {
    const resp = await fetch(url.toString(), {
      headers: {
        'User-Agent': 'ZhangShangShangKe/2.0 (contact@handbiz.com)',
        'Accept': 'application/json',
      },
    });

    if (!resp.ok) return [];

    const data = await resp.json();
    if (!Array.isArray(data)) return [];

    return data.map((item: any) => ({
      lat: parseFloat(item.lat),
      lng: parseFloat(item.lon),
      displayName: item.display_name,
      type: item.type,
      importance: item.importance || 0,
    }));
  } catch (err) {
    console.error('[Nominatim POI] Error:', err);
    return [];
  }
}

/**
 * 带降级的地理编码（高德 → Nominatim）
 */
export async function geocodeWithFallback(address: string): Promise<{ lat: number; lng: number; source: string } | null> {
  // 先尝试高德（如果有 API Key）
  // const amapResult = await amapGeocode(address);
  // if (amapResult) return { ...amapResult, source: 'amap' };

  // 降级到 Nominatim
  const nominatimResult = await nominatimGeocode({ query: address });
  if (nominatimResult) {
    return {
      lat: nominatimResult.lat,
      lng: nominatimResult.lng,
      source: 'nominatim',
    };
  }

  return null;
}
