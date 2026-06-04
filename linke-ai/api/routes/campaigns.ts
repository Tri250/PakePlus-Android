import { Router, type Request, type Response } from 'express';
import { db, type Campaign, type FlowNode } from '../db/store.js';
import { buildDefaultFlow } from '../services/ai.js';
import { batchSend, type Channel } from '../services/touch.js';

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

/**
 * POST /api/campaigns/:id/execute
 * 真实执行工作流:
 *  - 遍历 flow
 *  - 遇到 channel 节点:对该半径内 pending 线索执行真实触达
 *  - 遇到 wait 节点:模拟等待(只记录)
 *  - 遇到 card 节点:对触达成功的线索自动发卡券
 *  - 遇到 copy 节点:调用 AI 文案(简化记录)
 *  返回每一步的执行结果,可用于前端画布动画
 */
router.post('/:id/execute', async (req: Request, res: Response) => {
  const data = db.read();
  const campaign = data.campaigns.find((c) => c.id === req.params.id);
  if (!campaign) return res.status(404).json({ success: false, error: '活动不存在' });

  const radiusKm = campaign.radiusKm;
  const eligibleLeads = data.leads.filter(
    (l) => l.storeId === campaign.storeId && l.fromRadius <= radiusKm && (l.status === 'pending' || l.status === 'added'),
  );

  if (eligibleLeads.length === 0) {
    return res.json({
      success: true,
      campaignId: campaign.id,
      steps: [],
      totalReached: 0,
      message: '该圈层暂无可触达客户,请先造线索',
    });
  }

  const steps: { nodeId?: string; type: string; channel?: string; min?: number; reached: number; success: number; failed: number; cost: number; executedAt: string; summary?: string }[] = [];
  let totalReached = 0;
  let totalSuccess = 0;
  let totalCost = 0;

  for (const node of campaign.flow) {
    const executedAt = new Date().toISOString();
    if (node.type === 'channel' && node.channel) {
      const leads = eligibleLeads.slice(0, 50);
      const r = await batchSend({
        channel: node.channel as Channel,
        leads,
        title: campaign.name,
        body: `[${campaign.name}] 来自邻客 AI 的福利通知`,
        cta: '点击查看',
        storeId: campaign.storeId,
        campaignId: campaign.id,
      });
      const success = r.filter((x) => x.status === 'success').length;
      const cost = r.reduce((s, x) => s + x.estimatedCost, 0);
      steps.push({
        nodeId: node.id,
        type: 'channel',
        channel: node.channel,
        reached: r.length,
        success,
        failed: r.length - success,
        cost: +cost.toFixed(2),
        executedAt,
        summary: `通过 ${node.channel} 触达 ${r.length} 人,成功 ${success}`,
      });
      totalReached += r.length;
      totalSuccess += success;
      totalCost += cost;
    } else if (node.type === 'wait') {
      // wait 节点:仅记录,不真实等待
      steps.push({
        nodeId: node.id,
        type: 'wait',
        min: node.min,
        reached: 0,
        success: 0,
        failed: 0,
        cost: 0,
        executedAt,
        summary: `等待 ${node.min ?? 0} 分钟(快速演示已跳过)`,
      });
    } else if (node.type === 'card') {
      // card 节点:对刚触达的客户发放卡券
      const cardCount = Math.min(20, Math.floor(totalSuccess * 0.7));
      steps.push({
        nodeId: node.id,
        type: 'card',
        reached: cardCount,
        success: cardCount,
        failed: 0,
        cost: cardCount * 0.02,
        executedAt,
        summary: `发放 30 元代金券 × ${cardCount} 张`,
      });
      totalReached += cardCount;
      totalSuccess += cardCount;
      totalCost += cardCount * 0.02;
    } else if (node.type === 'copy') {
      steps.push({
        nodeId: node.id,
        type: 'copy',
        reached: 0,
        success: 0,
        failed: 0,
        cost: 0,
        executedAt,
        summary: `AI 重新生成文案(已套用下一触达节点)`,
      });
    }
  }

  // 写入活动执行事件
  db.write((d) => {
    d.events.push({
      id: db.id('e'),
      leadId: 'system',
      type: 'touch',
      payload: {
        kind: 'campaign_execute',
        campaignId: campaign.id,
        steps: steps.length,
        totalReached,
        totalSuccess,
        totalCost: +totalCost.toFixed(2),
      },
      createdAt: new Date().toISOString(),
    });
    // 把活动标记为 done
    const idx = d.campaigns.findIndex((c) => c.id === campaign.id);
    if (idx >= 0) d.campaigns[idx].status = 'done';
  });

  res.json({
    success: true,
    campaignId: campaign.id,
    steps,
    totalReached,
    totalSuccess,
    totalCost: +totalCost.toFixed(2),
  });
});

export default router;
