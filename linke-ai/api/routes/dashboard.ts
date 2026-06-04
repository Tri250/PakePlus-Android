import { Router, type Request, type Response } from 'express';
import { db } from '../db/store.js';

const router = Router();

const daysFromNow = (n: number) => {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d;
};

const fmt = (d: Date) => `${d.getMonth() + 1}/${d.getDate()}`;

/**
 * GET /api/dashboard/overview?range=7d|30d&storeId=xxx
 */
router.get('/overview', (req: Request, res: Response) => {
  const range = (req.query.range as string) || '7d';
  const days = range === '30d' ? 30 : 7;
  const storeId = (req.query.storeId as string) || '';
  const data = db.read();
  const leads = storeId ? data.leads.filter((l) => l.storeId === storeId) : data.leads;

  const reachBase = leads.length * 120 + 2400;
  const trend: { date: string; reach: number; added: number; visited: number }[] = [];
  for (let i = days - 1; i >= 0; i--) {
    const d = daysFromNow(i);
    const seed = (i * 7 + leads.length) % 11;
    const reach = Math.round(reachBase / days * (0.7 + (seed % 5) * 0.1));
    const added = Math.round(reach * 0.08);
    const visited = Math.round(added * 0.32);
    trend.push({ date: fmt(d), reach, added, visited });
  }

  const addedCount = leads.filter((l) => ['added', 'visited', 'won'].includes(l.status)).length;
  const visitedCount = leads.filter((l) => ['visited', 'won'].includes(l.status)).length;
  const wonCount = leads.filter((l) => l.status === 'won').length;

  const radiusCompare = ([3, 5, 8, 10] as const).map((km) => {
    const subset = leads.filter((l) => l.fromRadius === km);
    return {
      km,
      cost: subset.length * 0.8 + 1.6 + km * 0.2,
      conv: subset.length ? subset.filter((l) => l.status === 'won').length / subset.length : 0,
      count: subset.length,
    };
  });

  // 真实事件流:取门店所有 leads 的 events,按时间倒序
  const leadIds = new Set(leads.map((l) => l.id));
  const recentEvents = data.events
    .filter((e) => leadIds.has(e.leadId) || e.leadId === 'system')
    .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
    .slice(0, 20);

  // 渠道效果统计:从真实触达事件聚合
  const channelMap: Record<string, { success: number; failed: number; totalCost: number; count: number }> = {};
  for (const ev of data.events) {
    if (!leadIds.has(ev.leadId)) continue;
    const ch = (ev.payload?.channel as string | undefined) || 'unknown';
    const status = (ev.payload?.status as string | undefined) || 'success';
    const cost = typeof ev.payload?.cost === 'number' ? (ev.payload.cost as number) : 0;
    if (!channelMap[ch]) channelMap[ch] = { success: 0, failed: 0, totalCost: 0, count: 0 };
    channelMap[ch].count += 1;
    channelMap[ch].totalCost += cost;
    if (status === 'success') channelMap[ch].success += 1;
    else channelMap[ch].failed += 1;
  }
  const channelStats = Object.entries(channelMap)
    .map(([channel, v]) => ({ channel, ...v }))
    .sort((a, b) => b.success - a.success);

  res.json({
    success: true,
    overview: {
      reach: reachBase,
      addedWechat: addedCount,
      visited: visitedCount,
      won: wonCount,
      roi: +(wonCount * 168 / Math.max(1, reachBase) * 100).toFixed(2),
      trend,
      radiusCompare,
      recentEvents,
      channelStats,
    },
  });
});

export default router;
