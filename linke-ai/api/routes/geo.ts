/**
 * 实时定位 / 反查 / 找最近门店 API
 * POST /api/geo/reverse  { lng, lat, accuracy?, source? }
 * GET  /api/poi/nearby   ?lng=&lat=&radiusKm=5&category=office
 * GET  /api/geo/nearest-store?lng=&lat=
 */
import { Router, type Request, type Response } from 'express';
import { findNearbyPois, findNearestStore, realtimeLocate } from '../services/geo-locate.js';

const router = Router();

/**
 * GET /api/geo/nearest-store?lng=&lat=
 * 根据经纬度找最近门店
 */
router.get('/nearest-store', (req: Request, res: Response) => {
  const lng = Number(req.query.lng);
  const lat = Number(req.query.lat);
  if (Number.isNaN(lng) || Number.isNaN(lat)) {
    return res.status(400).json({ success: false, error: '缺少经纬度' });
  }
  const store = findNearestStore(lng, lat);
  if (!store) return res.status(404).json({ success: false, error: '暂无门店' });
  res.json({ success: true, store });
});

/**
 * POST /api/geo/reverse
 * body: { lng, lat, accuracy?, source? }
 * 返回:完整地址 + 附近 POI + 最近门店 + 候选门店(前 5)
 */
router.post('/reverse', (req: Request, res: Response) => {
  const { lng, lat, accuracy, source } = req.body || {};
  if (typeof lng !== 'number' || typeof lat !== 'number') {
    return res.status(400).json({ success: false, error: '缺少经纬度' });
  }
  if (lng < -180 || lng > 180 || lat < -90 || lat > 90) {
    return res.status(400).json({ success: false, error: '经纬度不合法' });
  }
  const result = realtimeLocate({ lng, lat, accuracy, source: source || 'browser' });
  res.json({ success: true, ...result });
});

/**
 * GET /api/geo/reverse?lng=&lat=
 * GET 形式调用反查(便于 URL 直接使用)
 */
router.get('/reverse', (req: Request, res: Response) => {
  const lng = Number(req.query.lng);
  const lat = Number(req.query.lat);
  if (Number.isNaN(lng) || Number.isNaN(lat)) {
    return res.status(400).json({ success: false, error: '缺少经纬度' });
  }
  res.json({ success: true, ...realtimeLocate({ lng, lat, source: 'manual' }) });
});

/**
 * GET /api/poi/nearby?lng=&lat=&radiusKm=5&category=office
 */
router.get('/poi/nearby', (req: Request, res: Response) => {
  const lng = Number(req.query.lng);
  const lat = Number(req.query.lat);
  const radiusKm = Number(req.query.radiusKm || 5);
  const category = (req.query.category as string) || undefined;
  if (Number.isNaN(lng) || Number.isNaN(lat)) {
    return res.status(400).json({ success: false, error: '缺少经纬度' });
  }
  const items = findNearbyPois(lng, lat, radiusKm, category);
  res.json({ success: true, total: items.length, items });
});

/**
 * GET /api/poi/nearby-by-city?city=&lng=&lat=&radiusKm=
 * 在指定城市内找附近 POI
 */
router.get('/poi/nearby-by-city', (req: Request, res: Response) => {
  const city = (req.query.city as string) || '';
  const lng = Number(req.query.lng);
  const lat = Number(req.query.lat);
  const radiusKm = Number(req.query.radiusKm || 5);
  if (!city) return res.status(400).json({ success: false, error: '缺少 city' });
  if (Number.isNaN(lng) || Number.isNaN(lat)) return res.status(400).json({ success: false, error: '缺少经纬度' });
  const items = findNearbyPois(lng, lat, radiusKm)
    .filter((p) => p.city === city || p.city.includes(city) || city.includes(p.city));
  res.json({ success: true, total: items.length, items });
});

export default router;
