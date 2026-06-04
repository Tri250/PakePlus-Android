/**
 * 模拟 AI 能力:基于模板的概率组合文案生成。
 * 投产时可替换为真实 LLM 调用,接口签名保持不变。
 */
import type { FlowNode } from '../db/store.js';

const pick = <T,>(arr: T[]) => arr[Math.floor(Math.random() * arr.length)];

export interface Persona {
  summary: string;
  radar: { dim: string; value: number }[];
  keywords: string[];
  highlights: string[];
}

const radarDims = ['消费力', '活跃度', '复购率', '价格敏感', '社交裂变', '到店动因'];

export const generatePersona = (input: {
  radiusKm: 3 | 5 | 8 | 10;
  category: string;
  categories?: string[];
}): Persona => {
  // 半径越大,人群越多元,价格敏感度也提升
  const r = input.radiusKm;
  const base = 60;
  const bias = (r - 3) * 3;

  const radar = radarDims.map((dim, i) => {
    const seed = (i * 17 + r * 11) % 30;
    const value = Math.max(35, Math.min(95, base + seed - bias / 2 + (i === 0 ? 6 : 0)));
    return { dim, value };
  });

  const keywordPool: Record<number, string[]> = {
    3: ['写字楼白领', '午休时间紧', '咖啡刚需', '会员复购', '楼宇电梯', '25-35 岁'],
    5: ['年轻家庭', '周末出行', '亲子场景', '性价比优先', '美团小红书', '30-40 岁'],
    8: ['通勤族', '团购达人', '内容种草', '价格敏感', '25-40 岁', '抖音同城'],
    10: ['全城客群', '礼品馈赠', '到店率低', '高客单价', '节日营销', '25-50 岁'],
  };

  const summaryTemplates = [
    `半径 ${r} 公里内的核心客群以"${pick(keywordPool[r])}"为主,平均到店距离 ${(r * 0.6).toFixed(1)} 公里,推荐优先投入 ${pick(['企微私域', '朋友圈广告', '抖音同城', '美团卡券'])}。`,
    `${r} km 圈层画像:${pick(keywordPool[r])} 占比最高,${pick(['复购周期短', '价格敏感度中等', '社交裂变意愿强', '到店动因以场景化为主'])},建议使用"${pick(['工作日午间', '周末早午餐', '下班前 1 小时', '节假日午后'])}"作为触达窗口。`,
    `基于近 7 天 ${r} 公里行为数据,该圈层偏好的关键词是"${pick(keywordPool[r])}"、"${pick(keywordPool[r])}",AI 建议话术围绕"${pick(['限时福利', '场景化体验', '会员专享', '0 门槛尝鲜'])}"展开。`,
  ];

  return {
    summary: pick(summaryTemplates),
    radar,
    keywords: keywordPool[r].slice(0, 6),
    highlights: [
      `${r} km 内同类门店 ${8 + r * 3} 家,差异化机会在"${pick(['第三空间', '烘焙自选', '宠物友好', '深夜档'])}"`,
      `高潜 POI ${20 + r * 4} 个,写字楼 / 商场占比 ${40 + r * 2}%`,
      `近 7 天同圈层加微成本 ¥${(2 + r * 0.6).toFixed(1)},低于行业均值 18%`,
    ],
  };
};

const copyBank: Record<string, { title: string; body: string; cta: string }[]> = {
  wechat: [
    {
      title: '工作日午间·轻享一杯',
      body: '在 CBD 找一杯不踩雷的咖啡?本周到店出示朋友圈即可领取 12 盎司冰美式一杯,搭配当日烘焙可颂,让午休 30 分钟直接 "回血"。',
      cta: '点击查看门店 · 限 3 公里内到店',
    },
    {
      title: '你的下一杯,我们请',
      body: '我们给 3 km 内的你准备了一份小确幸:工作日 14:00 前到店,凭微信即可 9.9 元享原价 32 元的手冲 + 司康,每天 30 份。',
      cta: '一键领取 · 仅限今日',
    },
  ],
  sms: [
    {
      title: '【邻客 AI】早午餐福利',
      body: '亲爱的[姓名],门店今日推出 3 公里专属早午餐 A 套(精品手冲+现烤三明治)¥39 限今日,凭短信 1 小时内到店核销。',
      cta: '回复 TD 退订',
    },
  ],
  douyin: [
    {
      title: '同城探店·3 公里也能到',
      body: '清晨 8 点的 CBD,我们用一杯手冲迎接 3 公里内的你。点开视频定位,1 公里内可享 "早安 8.8 元" 福利,30 份 / 天。',
      cta: '点击定位 · 到店核销',
    },
  ],
  card: [
    {
      title: '会员卡券·周边专享',
      body: '5 公里内的你,本周可领 30 元代金券 × 2,叠加满 50 减 12 券。外卖平台与门店同享,周末家庭到店再赠儿童烘焙体验一次。',
      cta: '立即领券 · 7 天有效',
    },
  ],
};

export const generateCopy = (input: { channel: 'sms' | 'wechat' | 'douyin' | 'card'; personaKeywords: string[] }) => {
  const bank = copyBank[input.channel] || copyBank.wechat;
  const picks: { title: string; body: string; cta: string }[] = [];
  for (let i = 0; i < 3; i++) picks.push({ ...pick(bank) });
  return picks;
};

export const buildDefaultFlow = (radiusKm: number): FlowNode[] => [
  { id: 'n1', type: 'channel', channel: 'wechat' },
  { id: 'n2', type: 'wait', min: 1440 },
  { id: 'n3', type: 'channel', channel: radiusKm >= 5 ? 'douyin' : 'sms' },
  { id: 'n4', type: 'card' },
];

export const todaySuggestion = (radiusKm: number) => {
  const suggestions = [
    {
      title: `${radiusKm} km 写字楼午间专场`,
      body: '检测到周边 3 栋写字楼今日有 6 场行业沙龙,建议在 12:00 前推送"午间手冲 9.9 元"朋友圈广告,预计触达 1.2 万人。',
      cta: '一键投放',
    },
    {
      title: '夜间内容种草时段',
      body: `${radiusKm} km 圈层的抖音活跃高峰在 21:30,AI 已为你准备 3 条 15 秒短视频脚本,加上 30 元卡券,今晚就能投。`,
      cta: '查看脚本',
    },
    {
      title: '雨天应急加推',
      body: '今日小雨,3 公里外卖订单上涨 32%。建议追加 50 份 "雨天暖咖" 套餐到美团 / 饿了么,自动派发免运费。',
      cta: '加入卡券包',
    },
  ];
  return suggestions;
};
