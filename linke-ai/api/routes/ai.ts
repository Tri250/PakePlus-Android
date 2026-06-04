import { Router, type Request, type Response } from 'express';
import { generateCopy, generatePersona, todaySuggestion } from '../services/ai.js';
import { db } from '../db/store.js';

const router = Router();

/**
 * POST /api/ai/persona
 * body: { storeId, radiusKm, categories? }
 */
router.post('/persona', (req: Request, res: Response) => {
  const { storeId, radiusKm, categories } = req.body || {};
  const data = db.read();
  const store = data.stores.find((s) => s.id === storeId);
  if (!store) return res.status(404).json({ success: false, error: '门店不存在' });
  const persona = generatePersona({
    radiusKm: radiusKm as 3 | 5 | 8 | 10,
    category: store.category,
    categories,
  });
  res.json({ success: true, persona, store: { id: store.id, name: store.name, category: store.category } });
});

/**
 * POST /api/ai/copywriting
 * body: { channel, radiusKm, storeId }
 */
router.post('/copywriting', (req: Request, res: Response) => {
  const { channel = 'wechat', radiusKm = 3, storeId } = req.body || {};
  const data = db.read();
  const store = data.stores.find((s) => s.id === storeId);
  const persona = generatePersona({ radiusKm, category: store?.category || '通用' });
  const copies = generateCopy({ channel, personaKeywords: persona.keywords });
  res.json({ success: true, copies, persona });
});

/**
 * GET /api/ai/suggestion?radiusKm=3
 */
router.get('/suggestion', (req: Request, res: Response) => {
  const radiusKm = (Number(req.query.radiusKm) || 3) as 3 | 5 | 8 | 10;
  res.json({ success: true, suggestions: todaySuggestion(radiusKm) });
});

export default router;
