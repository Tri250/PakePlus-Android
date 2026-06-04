/**
 * 触达服务:模拟 5 大渠道(短信 / 企微 / 朋友圈 / 抖音 / AI 外呼 / 卡券)的真实发送流程
 * 真实生产时,以下 send 函数会替换为:
 *  - sms:     阿里云 / 腾讯云 短信 SDK
 *  - wechat:  企业微信 SCRM (wework.qq.com)
 *  - douyin:  巨量引擎 / 抖音来客 Open API
 *  - card:    美团商家 / 有赞卡券 API
 *  - phone:   百应 / 容联七陌 AI 外呼 API
 */
import { db, type Lead } from '../db/store.js';

export type Channel = 'sms' | 'wechat' | 'douyin' | 'card' | 'phone';

export interface SendResult {
  channel: Channel;
  leadId: string;
  phone: string;
  name: string;
  status: 'success' | 'failed' | 'partial';
  messageId?: string;
  error?: string;
  deliveredAt: string;
  // 渠道特征
  estimatedCost: number;     // 元 / 条
  estimatedOpen: number;     // 0-100
}

export interface BatchSendInput {
  channel: Channel;
  leads: Lead[];
  title: string;
  body: string;
  cta: string;
  storeId: string;
  campaignId?: string;
}

const CHANNEL_META: Record<Channel, { cost: number; openRate: number; label: string; unit: string; rateLimit: number }> = {
  sms:     { cost: 0.045, openRate: 0.92,  label: '阿里云短信',   unit: '条', rateLimit: 200 },
  wechat:  { cost: 0.08,  openRate: 0.68,  label: '企微 SCRM',   unit: '条', rateLimit: 100 },
  douyin:  { cost: 0.18,  openRate: 0.74,  label: '抖音同城广告', unit: '条', rateLimit: 50 },
  card:    { cost: 0.02,  openRate: 0.45,  label: '美团/有赞卡券', unit: '张', rateLimit: 500 },
  phone:   { cost: 0.35,  openRate: 0.41,  label: 'AI 智能外呼',  unit: '通', rateLimit: 30 },
};

// 单条发送:模拟真实三方 API 调用(带抖动延迟 + 失败率)
const sendOne = async (channel: Channel, lead: Lead, msg: { title: string; body: string; cta: string }): Promise<SendResult> => {
  // 模拟网络延迟
  await new Promise((r) => setTimeout(r, 6 + Math.random() * 14));

  // 真实三方 API 会有 1-3% 失败率(余额不足/号码黑名单/敏感词)
  const failRate = 0.02;
  const isFail = Math.random() < failRate;

  const meta = CHANNEL_META[channel];
  const result: SendResult = {
    channel,
    leadId: lead.id,
    phone: lead.phone,
    name: lead.name,
    status: isFail ? 'failed' : 'success',
    error: isFail ? pick(['余额不足', '号码黑名单', '敏感词拦截', '频次限制']) : undefined,
    messageId: isFail ? undefined : `${channel}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    deliveredAt: new Date().toISOString(),
    estimatedCost: meta.cost,
    estimatedOpen: Math.round(meta.openRate * 100 + (Math.random() * 8 - 4)),
  };

  // 写入触达日志
  const eventType = isFail ? 'note' : 'touch';
  const messageId = result.messageId;
  const channelLog = channel;
  db.write((d) => {
    d.events.push({
      id: db.id('e'),
      leadId: lead.id,
      type: eventType,
      payload: {
        channel: channelLog,
        title: msg.title,
        body: msg.body,
        cta: msg.cta,
        messageId,
        cost: meta.cost,
        status: result.status,
        error: result.error,
      },
      createdAt: new Date().toISOString(),
    });
    // 同步到 lead 的最新触达时间
    const targetLead = d.leads.find((l) => l.id === result.leadId);
    if (targetLead && result.status === 'success' && targetLead.status === 'pending') {
      // 自动推进:如果线索是 pending 且触达成功,标记为 "added"
      targetLead.status = 'added';
    }
  });

  return result;
};

const pick = <T,>(arr: T[]) => arr[Math.floor(Math.random() * arr.length)];

// 批量发送:并发 + 速率限制
export const batchSend = async (input: BatchSendInput): Promise<SendResult[]> => {
  const meta = CHANNEL_META[input.channel];
  const concurrency = meta.rateLimit;
  const results: SendResult[] = [];

  // 简易并发池
  const queue = [...input.leads];
  const workers = Array.from({ length: Math.min(concurrency, queue.length) }, async () => {
    while (queue.length) {
      const lead = queue.shift();
      if (!lead) break;
      try {
        const r = await sendOne(input.channel, lead, {
          title: input.title,
          body: input.body,
          cta: input.cta,
        });
        results.push(r);
      } catch (e) {
        results.push({
          channel: input.channel,
          leadId: lead.id,
          phone: lead.phone,
          name: lead.name,
          status: 'failed',
          error: e instanceof Error ? e.message : 'unknown',
          deliveredAt: new Date().toISOString(),
          estimatedCost: 0,
          estimatedOpen: 0,
        });
      }
    }
  });
  await Promise.all(workers);

  return results;
};

export const getChannelMeta = () => ({ ...CHANNEL_META });
