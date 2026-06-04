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
    },
  });
});

export default router;
