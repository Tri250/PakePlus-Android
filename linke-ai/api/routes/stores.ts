import { Router, type Request, type Response } from 'express';
import { db } from '../db/store.js';
import { buildRadiusPayload } from '../services/geo.js';

const router = Router();

/**
 * GET /api/stores
 */
router.get('/', (_req: Request, res: Response) => {
  const data = db.read();
  res.json({ success: true, stores: data.stores });
});

/**
 * GET /api/stores/:id
 */
router.get('/:id', (req: Request, res: Response) => {
  const data = db.read();
  const store = data.stores.find((s) => s.id === req.params.id);
  if (!store) return res.status(404).json({ success: false, error: '门店不存在' });
  res.json({ success: true, store });
});

/**
 * GET /api/stores/:id/radius?km=3,5,8,10
 * 返回半径统计 + 圈层 GeoJSON(基于真实北京 POI 数据)
 */
router.get('/:id/radius', (req: Request, res: Response) => {
  const data = db.read();
  const store = data.stores.find((s) => s.id === req.params.id);
  if (!store) return res.status(404).json({ success: false, error: '门店不存在' });
  const payload = buildRadiusPayload(store.lng, store.lat, store.id, { category: store.category });
  res.json({ success: true, ...payload });
});

/**
 * GET /api/stores/:id/members
 */
router.get('/:id/members', (req: Request, res: Response) => {
  const data = db.read();
  const members = data.members
    .filter((m) => m.storeId === req.params.id)
    .map((m) => {
      const u = data.users.find((x) => x.id === m.userId);
      return { ...m, user: u };
    });
  res.json({ success: true, members });
});

export default router;
