import { Router, type Request, type Response } from 'express';
import { db } from '../db/store.js';

const router = Router();

/**
 * 简化版登录:开发期使用手机号 + 验证码,验证码恒为 0000
 * POST /api/auth/login
 */
router.post('/login', (req: Request, res: Response) => {
  const { phone, code } = req.body || {};
  if (!phone || !code) {
    return res.status(400).json({ success: false, error: '请提供手机号与验证码' });
  }
  if (code !== '0000') {
    return res.status(401).json({ success: false, error: '验证码错误(开发期验证码为 0000)' });
  }
  const data = db.read();
  let user = data.users.find((u) => u.phone === phone);
  if (!user) {
    user = { id: db.id('u'), phone, name: `店长 ${phone.slice(-4)}`, createdAt: new Date().toISOString() };
    db.write((d) => d.users.push(user!));
  }
  const token = `tk_${user.id}_${Date.now()}`;
  res.json({ success: true, token, user });
});

/**
 * GET /api/auth/me
 */
router.get('/me', (req: Request, res: Response) => {
  const auth = req.header('authorization') || '';
  const m = auth.match(/^Bearer\s+(.+)$/);
  if (!m) return res.status(401).json({ success: false, error: '未登录' });
  const userId = m[1].split('_')[1];
  const data = db.read();
  const user = data.users.find((u) => u.id === userId);
  if (!user) return res.status(404).json({ success: false, error: '用户不存在' });
  res.json({ success: true, user });
});

/**
 * POST /api/auth/logout (mock)
 */
router.post('/logout', (_req: Request, res: Response) => {
  res.json({ success: true });
});

export default router;
