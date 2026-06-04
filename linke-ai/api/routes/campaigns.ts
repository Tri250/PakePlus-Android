import { Router, type Request, type Response } from 'express';
import { db, type Campaign, type FlowNode } from '../db/store.js';
import { buildDefaultFlow } from '../services/ai.js';

const router = Router();

/**
 * GET /api/campaigns?storeId=xxx
 */
router.get('/', (req: Request, res: Response) => {
  const data = db.read();
  const storeId = (req.query.storeId as string) || '';
  const list = storeId ? data.campaigns.filter((c) => c.storeId === storeId) : data.campaigns;
  res.json({ success: true, campaigns: list });
});

/**
 * GET /api/campaigns/:id
 */
router.get('/:id', (req: Request, res: Response) => {
  const data = db.read();
  const item = data.campaigns.find((c) => c.id === req.params.id);
  if (!item) return res.status(404).json({ success: false, error: '活动不存在' });
  res.json({ success: true, campaign: item });
});

/**
 * POST /api/campaigns
 * body: { name, storeId, radiusKm, flow?, scheduleAt? }
 */
router.post('/', (req: Request, res: Response) => {
  const { name, storeId, radiusKm, flow, scheduleAt } = req.body || {};
  if (!name || !storeId || !radiusKm) {
    return res.status(400).json({ success: false, error: '缺少必要字段' });
  }
  const data = db.read();
  const store = data.stores.find((s) => s.id === storeId);
  if (!store) return res.status(404).json({ success: false, error: '门店不存在' });
  const c: Campaign = {
    id: db.id('c'),
    storeId,
    name,
    radiusKm,
    flow: (flow && flow.length ? flow : buildDefaultFlow(radiusKm)) as FlowNode[],
    scheduleAt,
    status: 'draft',
    createdAt: new Date().toISOString(),
  };
  db.write((d) => d.campaigns.unshift(c));
  res.json({ success: true, campaign: c });
});

/**
 * PATCH /api/campaigns/:id
 * body: { name?, status?, flow?, scheduleAt? }
 */
router.patch('/:id', (req: Request, res: Response) => {
  const data = db.read();
  const idx = data.campaigns.findIndex((c) => c.id === req.params.id);
  if (idx < 0) return res.status(404).json({ success: false, error: '活动不存在' });
  const cur = data.campaigns[idx];
  const next: Campaign = {
    ...cur,
    name: req.body.name ?? cur.name,
    status: req.body.status ?? cur.status,
    flow: (req.body.flow as FlowNode[]) ?? cur.flow,
    scheduleAt: req.body.scheduleAt ?? cur.scheduleAt,
  };
  db.write((d) => (d.campaigns[idx] = next));
  res.json({ success: true, campaign: next });
});

/**
 * DELETE /api/campaigns/:id
 */
router.delete('/:id', (req: Request, res: Response) => {
  db.write((d) => {
    d.campaigns = d.campaigns.filter((c) => c.id !== req.params.id);
  });
  res.json({ success: true });
});

export default router;
