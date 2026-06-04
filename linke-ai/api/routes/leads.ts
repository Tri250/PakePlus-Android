import { Router, type Request, type Response } from 'express';
import { db, type Lead, type LeadStatus, type LeadEvent } from '../db/store.js';

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
 * 根据当前选中的半径快速造一批演示线索
 */
router.post('/seed', (req: Request, res: Response) => {
  const { storeId, radiusKm, count = 6 } = req.body || {};
  if (!storeId || !radiusKm) return res.status(400).json({ success: false, error: '缺少门店或半径' });
  const data = db.read();
  const store = data.stores.find((s) => s.id === storeId);
  if (!store) return res.status(404).json({ success: false, error: '门店不存在' });
  const names = ['王', '李', '张', '陈', '刘', '赵', '孙', '周', '吴', '郑', '黄', '马'];
  const statuses: LeadStatus[] = ['pending', 'added', 'visited', 'won', 'lost'];
  const created: Lead[] = [];
  for (let i = 0; i < Number(count); i++) {
    const surname = names[Math.floor(Math.random() * names.length)];
    const lead: Lead = {
      id: db.id('l'),
      storeId,
      fromRadius: radiusKm,
      name: `${surname}${i % 2 === 0 ? '女士' : '先生'}`,
      phone: `139${String(Math.floor(Math.random() * 1e8)).padStart(8, '0')}`,
      status: statuses[Math.floor(Math.random() * statuses.length)],
      createdAt: new Date().toISOString(),
    };
    created.push(lead);
  }
  db.write((d) => created.forEach((l) => d.leads.unshift(l)));
  res.json({ success: true, created: created.length });
});

export default router;
