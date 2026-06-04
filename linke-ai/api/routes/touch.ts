/**
 * 触达中心 API
 *  - POST /api/touch/send     一键群发(单渠道)
 *  - POST /api/touch/multi    一键多渠道群发
 *  - GET  /api/touch/channels 渠道清单(费用 / 速率)
 *  - GET  /api/touch/logs     触达日志(按 leadId)
 */
import { Router, type Request, type Response } from 'express';
import { db, type Lead } from '../db/store.js';
import { batchSend, getChannelMeta, type Channel } from '../services/touch.js';

const router = Router();

router.get('/channels', (_req: Request, res: Response) => {
  res.json({ success: true, channels: getChannelMeta() });
});

router.post('/send', async (req: Request, res: Response) => {
  const { channel, leadIds, title, body, cta, storeId, campaignId } = req.body || {};
  if (!channel || !Array.isArray(leadIds) || leadIds.length === 0 || !title || !body) {
    return res.status(400).json({ success: false, error: '缺少必要字段' });
  }
  if (!storeId) {
    return res.status(400).json({ success: false, error: '缺少 storeId' });
  }
  const data = db.read();
  const leads: Lead[] = data.leads.filter((l) => leadIds.includes(l.id) && l.storeId === storeId);
  if (leads.length === 0) {
    return res.status(404).json({ success: false, error: '未找到可触达的客户' });
  }
  const results = await batchSend({ channel, leads, title, body, cta, storeId, campaignId });
  const success = results.filter((r) => r.status === 'success').length;
  const failed = results.length - success;
  const totalCost = results.reduce((s, r) => s + r.estimatedCost, 0);
  const avgOpen = results.length
    ? Math.round(results.reduce((s, r) => s + r.estimatedOpen, 0) / results.length)
    : 0;
  res.json({
    success: true,
    summary: { total: results.length, success, failed, totalCost: +totalCost.toFixed(2), avgOpen },
    results,
  });
});

router.post('/multi', async (req: Request, res: Response) => {
  const { channels, leadIds, title, body, cta, storeId, campaignId } = req.body || {};
  if (!Array.isArray(channels) || channels.length === 0) {
    return res.status(400).json({ success: false, error: '请至少选择一个渠道' });
  }
  if (!Array.isArray(leadIds) || leadIds.length === 0 || !title || !body) {
    return res.status(400).json({ success: false, error: '缺少必要字段' });
  }
  const data = db.read();
  const leads: Lead[] = data.leads.filter((l) => leadIds.includes(l.id) && l.storeId === storeId);
  if (leads.length === 0) {
    return res.status(404).json({ success: false, error: '未找到可触达的客户' });
  }
  const all: Record<string, unknown> = {};
  for (const ch of channels as Channel[]) {
    const r = await batchSend({ channel: ch, leads, title, body, cta, storeId, campaignId });
    all[ch] = r;
  }
  res.json({ success: true, results: all });
});

router.get('/logs', (req: Request, res: Response) => {
  const leadId = (req.query.leadId as string) || '';
  const data = db.read();
  if (leadId) {
    const events = data.events
      .filter((e) => e.leadId === leadId)
      .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));
    return res.json({ success: true, events });
  }
  // 全部触达日志(按 lead 聚合)
  const touchEvents = data.events.filter((e) => e.type === 'touch' || e.type === 'note');
  res.json({ success: true, total: touchEvents.length, events: touchEvents.slice(-100).reverse() });
});

export default router;
