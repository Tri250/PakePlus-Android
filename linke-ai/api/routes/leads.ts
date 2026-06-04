import { Router, type Request, type Response } from 'express';
import { db, type Lead, type LeadStatus, type LeadEvent } from '../db/store.js';
import { generateLeadsFromPois } from '../services/geo.js';

const router = Router();

/**
 * GET /api/leads?storeId=&status=&page=&pageSize=
 */
router.get('/', (req: Request, res: Response) => {
  const data = db.read();
  let list = [...data.leads];
  const storeId = req.query.storeId as string | undefined;
  const status = req.query.status as string | undefined;
  if (storeId) list = list.filter((l) => l.storeId === storeId);
  if (status) list = list.filter((l) => l.status === status);
  list.sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));
  const total = list.length;
  res.json({ success: true, total, items: list });
});

/**
 * GET /api/leads/:id
 */
router.get('/:id', (req: Request, res: Response) => {
  const data = db.read();
  const lead = data.leads.find((l) => l.id === req.params.id);
  if (!lead) return res.status(404).json({ success: false, error: '线索不存在' });
  const events = data.events.filter((e) => e.leadId === lead.id).sort((a, b) => (a.createdAt < b.createdAt ? -1 : 1));
  res.json({ success: true, lead, events });
});

/**
 * POST /api/leads
 * body: { storeId, campaignId?, fromRadius, name, phone }
 */
router.post('/', (req: Request, res: Response) => {
  const { storeId, campaignId, fromRadius, name, phone } = req.body || {};
  if (!storeId || !fromRadius || !name || !phone) {
    return res.status(400).json({ success: false, error: '缺少必要字段' });
  }
  const lead: Lead = {
    id: db.id('l'),
    storeId,
    campaignId,
    fromRadius,
    name,
    phone,
    status: 'pending',
    createdAt: new Date().toISOString(),
  };
  const ev: LeadEvent = {
    id: db.id('e'),
    leadId: lead.id,
    type: 'touch',
    payload: { channel: 'wechat' },
    createdAt: new Date().toISOString(),
  };
  db.write((d) => {
    d.leads.unshift(lead);
    d.events.push(ev);
  });
  res.json({ success: true, lead });
});

/**
 * PATCH /api/leads/:id
 * body: { status?, ownerId?, note? }
 */
router.patch('/:id', (req: Request, res: Response) => {
  const data = db.read();
  const idx = data.leads.findIndex((l) => l.id === req.params.id);
  if (idx < 0) return res.status(404).json({ success: false, error: '线索不存在' });
  const cur = data.leads[idx];
  const nextStatus: LeadStatus = req.body.status ?? cur.status;
  const next: Lead = {
    ...cur,
    status: nextStatus,
    ownerId: req.body.ownerId ?? cur.ownerId,
    note: req.body.note ?? cur.note,
  };
  // 跟进时间线
  const event: LeadEvent = {
    id: db.id('e'),
    leadId: cur.id,
    type: nextStatus === 'pending' ? 'touch' : (nextStatus as LeadEvent['type']),
    payload: req.body.note ? { note: req.body.note } : undefined,
    createdAt: new Date().toISOString(),
  };
  db.write((d) => {
    d.leads[idx] = next;
    d.events.push(event);
  });
  res.json({ success: true, lead: next });
});

/**
 * POST /api/leads/seed
 * 根据当前选中的半径,从真实 POI 批量造线索(可指定 count)
 */
router.post('/seed', (req: Request, res: Response) => {
  const { storeId, radiusKm, count = 20 } = req.body || {};
  if (!storeId || !radiusKm) return res.status(400).json({ success: false, error: '缺少门店或半径' });
  const data = db.read();
  const store = data.stores.find((s) => s.id === storeId);
  if (!store) return res.status(404).json({ success: false, error: '门店不存在' });

  // 从真实 POI 生成候选
  const candidates = generateLeadsFromPois(store.lng, store.lat, radiusKm, storeId, Number(count));
  const statuses: LeadStatus[] = ['pending', 'pending', 'pending', 'added', 'visited', 'won', 'lost'];
  const created: Lead[] = candidates.map((c, i) => ({
    id: db.id('l'),
    storeId,
    campaignId: undefined,
    fromRadius: radiusKm,
    name: c.name,
    phone: c.phone,
    status: statuses[i % statuses.length],
    note: `来自 ${c.sourcePoi} (${({ office: '写字楼', mall: '商场', school: '学校', residence: '住宅', subway: '地铁', park: '公园' } as Record<string, string>)[c.sourcePoiCategory] || c.sourcePoiCategory}) · 高潜 ${c.hotScore}`,
    createdAt: new Date().toISOString(),
  }));
  db.write((d) => created.forEach((l) => d.leads.unshift(l)));
  res.json({ success: true, created: created.length, samples: created.slice(0, 3) });
});

/**
 * POST /api/leads/batch-touch
 * 一键群发(对一组 lead 触发触达)
 */
router.post('/batch-touch', async (req: Request, res: Response) => {
  const { storeId, leadIds, channel, title, body, cta } = req.body || {};
  if (!storeId || !Array.isArray(leadIds) || !channel || !title || !body) {
    return res.status(400).json({ success: false, error: '缺少必要字段' });
  }
  // 委托给 touch 服务
  const { batchSend } = await import('../services/touch.js');
  const data = db.read();
  const leads: Lead[] = data.leads.filter((l) => leadIds.includes(l.id) && l.storeId === storeId);
  if (leads.length === 0) return res.status(404).json({ success: false, error: '未找到可触达的客户' });
  const results = await batchSend({ channel, leads, title, body, cta, storeId });
  res.json({ success: true, results });
});

export default router;
