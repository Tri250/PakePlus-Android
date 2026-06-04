import { Router, type Request, type Response } from 'express';
import { db, type Lead, type LeadStatus, type LeadEvent } from '../db/store.js';
import { generateLeadsFromPois } from '../services/geo.js';
import { crawlNearbyLeads, recordCrawlLog, getCrawlLogs } from '../services/crawler.js';

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
 * 真实爬虫模拟:基于门店位置 + 半径,从大众点评 / 美团 / 高德 / 百度 / 抖音同城
 * 采集 3-5-8-10 km 范围内的真实用户联系方式(脱敏)
 */
router.post('/seed', (req: Request, res: Response) => {
  const { storeId, radiusKm, count = 24 } = req.body || {};
  if (!storeId || !radiusKm) return res.status(400).json({ success: false, error: '缺少门店或半径' });
  const data = db.read();
  const store = data.stores.find((s) => s.id === storeId);
  if (!store) return res.status(404).json({ success: false, error: '门店不存在' });

  // 记录爬虫日志
  const crawlId = db.id('cr');
  const startedAt = Date.now();
  recordCrawlLog({
    id: crawlId,
    storeId,
    city: store.name,
    radiusKm,
    count: Number(count),
    status: 'running',
    startedAt,
  });

  // 1. 真实爬虫采集:基于门店经纬度 + 半径
  const crawled = crawlNearbyLeads(
    storeId,
    store.lng,
    store.lat,
    store.name.includes('·') ? store.name.split('·')[1]?.trim() || '北京' : store.name,
    radiusKm,
    Number(count),
  );

  // 2. 落库为线索
  const created: Lead[] = crawled.map((c) => ({
    id: db.id('l'),
    storeId,
    campaignId: undefined,
    fromRadius: radiusKm,
    name: c.name,
    phone: c.phone,
    status: 'pending',
    note: `🕷️ ${c.source} · ${c.fromPoi} (${c.fromDistrict}) · ${c.notes} · 高潜 ${c.hotScore} · 意向 ${c.intentScore}`,
    createdAt: new Date().toISOString(),
  }));

  // 3. 记录爬虫元数据事件
  const metaEvent: LeadEvent = {
    id: db.id('e'),
    leadId: 'system',
    type: 'touch',
    payload: {
      kind: 'crawl',
      crawlId,
      storeId,
      city: store.name,
      radiusKm,
      count: created.length,
      source: crawled.map((c) => c.source).join('、'),
    },
    createdAt: new Date().toISOString(),
  };

  db.write((d) => {
    created.forEach((l) => d.leads.unshift(l));
    d.events.push(metaEvent);
  });

  // 更新爬虫日志
  recordCrawlLog({
    id: crawlId,
    storeId,
    city: store.name,
    radiusKm,
    count: created.length,
    status: 'done',
    startedAt,
    finishedAt: Date.now(),
  });

  res.json({
    success: true,
    created: created.length,
    crawlId,
    samples: created.slice(0, 3).map((l, i) => ({
      ...l,
      meta: {
        source: crawled[i].source,
        fromPoi: crawled[i].fromPoi,
        fromDistrict: crawled[i].fromDistrict,
        fromProvince: crawled[i].fromProvince,
        occupation: crawled[i].occupation,
        hotScore: crawled[i].hotScore,
        intentScore: crawled[i].intentScore,
        ltv: crawled[i].ltv,
        crawledAt: crawled[i].crawledAt,
      },
    })),
  });
});

/**
 * GET /api/leads/crawl-logs?storeId=
 * 查看爬虫执行历史
 */
router.get('/crawl-logs', (req: Request, res: Response) => {
  const storeId = req.query.storeId as string | undefined;
  const logs = getCrawlLogs(storeId);
  res.json({ success: true, logs });
});

/**
 * POST /api/leads/batch-touch
 */
router.post('/batch-touch', async (req: Request, res: Response) => {
  const { storeId, leadIds, channel, title, body, cta } = req.body || {};
  if (!storeId || !Array.isArray(leadIds) || !channel || !title || !body) {
    return res.status(400).json({ success: false, error: '缺少必要字段' });
  }
  const { batchSend } = await import('../services/touch.js');
  const data = db.read();
  const leads: Lead[] = data.leads.filter((l) => leadIds.includes(l.id) && l.storeId === storeId);
  if (leads.length === 0) return res.status(404).json({ success: false, error: '未找到可触达的客户' });
  const results = await batchSend({ channel, leads, title, body, cta, storeId });
  res.json({ success: true, results });
});

export default router;
