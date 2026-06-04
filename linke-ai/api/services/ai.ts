/**
 * AI 文案服务:按"行业 × 时段 × 优惠"组合,生成贴近 2026 国内 SCRM / 团购 / 私域 风格的真实话术
 */

const pick = <T,>(arr: T[]) => arr[Math.floor(Math.random() * arr.length)];

// 行业 → 客群 / 卖点 / 关键词
const INDUSTRY: Record<string, {
  audience: string[];
  sellingPoints: string[];
  keywords: string[];
  crowdVoice: string;
}> = {
  '精品咖啡 · 烘焙': {
    audience: ['写字楼白领', '早午餐党', '下午茶续命人', '周末 brunch 家庭'],
    sellingPoints: ['手冲精品', '现烤烘焙', '第三空间', '宠物友好', '深夜档不打烊'],
    keywords: ['精品手冲', '现烤可颂', '会员积分', '工作日午间', '周末 brunch'],
    crowdVoice: '在 CBD 找一杯不踩雷的咖啡',
  },
  '轻食简餐': {
    audience: ['减脂党', '健身人群', '写字楼白领', '学生'],
    sellingPoints: ['低卡高蛋白', '现做现拌', '热量标注透明', '搭配套餐'],
    keywords: ['减脂', '高蛋白', '低卡', '热量透明', '健身'],
    crowdVoice: '今天又该吃草了',
  },
  '美容 · 医美': {
    audience: ['25-40 岁女性', '写字楼白领', '精致妈妈'],
    sellingPoints: ['正品保障', '医生亲诊', '无套路', '不推销', '可分期'],
    keywords: ['小气泡', '光子嫩肤', '热玛吉', '玻尿酸', '正品'],
    crowdVoice: '约一次闺蜜一起变美',
  },
  '健身 · 瑜伽': {
    audience: ['减脂塑形', '产后修复', '通勤族', '学生党'],
    sellingPoints: ['私教 1V1', '团课丰富', '24 小时', '免费体验', '无推销'],
    keywords: ['私教', '团课', '免费体验', '体态评估', '拉伸'],
    crowdVoice: '管住嘴迈开腿',
  },
  '教育培训': {
    audience: ['K12 家长', '大学生', '职场充电族'],
    sellingPoints: ['名师精讲', '小班直播', '课后答疑', '0 元试听'],
    keywords: ['名师', '0 元试听', '小班', '陪练', '提分'],
    crowdVoice: '给孩子报个靠谱的班',
  },
  '家政 · 保洁': {
    audience: ['白领家庭', '双职工', '新装修'],
    sellingPoints: ['持证上岗', '自带工具', '不满意重做', '小时工 / 深度'],
    keywords: ['深度保洁', '小时工', '持证', '新居开荒', '月嫂'],
    crowdVoice: '家里实在太乱了',
  },
  '零售 · 服饰': {
    audience: ['写字楼白领', '潮人', '宝妈', '大学生'],
    sellingPoints: ['新品上市', '买二送一', '满 299 减 80', '会员专享'],
    keywords: ['新品', '折扣', '会员', '上新', '买赠'],
    crowdVoice: '下班逛街顺便买一件',
  },
  default: {
    audience: ['周边居民', '写字楼白领', '学生家长'],
    sellingPoints: ['品质保障', '服务到位', '性价比高', '会员专享'],
    keywords: ['限时', '专享', '体验', '会员', '折扣'],
    crowdVoice: '到店看看',
  },
};

// 时段 → 推送话术结构
const TIME_SLOT: Record<string, { prefix: string; cta: string; tone: string }> = {
  morning:   { prefix: '早安', cta: '7:30 - 10:00 到店', tone: '元气' },
  noon:      { prefix: '午间', cta: '11:30 - 14:00 到店', tone: '高效' },
  afternoon: { prefix: '下午', cta: '14:00 - 17:30 到店', tone: '治愈' },
  evening:   { prefix: '下班', cta: '17:30 - 21:00 到店', tone: '放松' },
  night:     { prefix: '深夜', cta: '21:00 - 24:00 限定', tone: '仪式感' },
};

// 优惠类型
const OFFER: Record<string, string> = {
  discount: '9.9 元起 / 第二件半价',
  gift:     '到店即送价值 38 元小食',
  coupon:   '30 元代金券 × 2(可叠加)',
  trial:    '0 元体验 1 次(限新客)',
  member:   '会员日 5 折 / 双倍积分',
  vip:      '私域专属 8 折 + 优先排队',
};

// 渠道特征
const CHANNEL_TONE: Record<string, { suffix: string; length: 'short' | 'medium' | 'long' }> = {
  sms:     { suffix: '退订回 T', length: 'short' },
  wechat:  { suffix: '点击查看门店 · 限 3 公里内到店', length: 'medium' },
  douyin:  { suffix: '点击定位 · 到店核销', length: 'medium' },
  card:    { suffix: '立即领券 · 7 天有效', length: 'short' },
  phone:   { suffix: '请问您本周方便到店吗?', length: 'long' },
};

export interface Persona {
  summary: string;
  radar: { dim: string; value: number }[];
  keywords: string[];
  highlights: string[];
}

const radarDims = ['消费力', '活跃度', '复购率', '价格敏感', '社交裂变', '到店动因'];

export interface PersonaInput {
  radiusKm: 3 | 5 | 8 | 10;
  category?: string;
}

export const generatePersona = (input: PersonaInput): Persona => {
  const r = input.radiusKm;
  const ind = INDUSTRY[input.category || 'default'] || INDUSTRY.default;
  const base = 60;
  const bias = (r - 3) * 3;
  const radar = radarDims.map((dim, i) => {
    const seed = (i * 17 + r * 11) % 30;
    const value = Math.max(35, Math.min(95, base + seed - bias / 2 + (i === 0 ? 6 : 0)));
    return { dim, value };
  });

  const keywordPool: Record<number, string[]> = {
    3: ['写字楼白领', '午休时间紧', '工作日刚需', '会员复购', '楼宇电梯', '25-35 岁'],
    5: ['年轻家庭', '周末出行', '亲子场景', '性价比优先', '美团小红书', '30-40 岁'],
    8: ['通勤族', '团购达人', '内容种草', '价格敏感', '25-40 岁', '抖音同城'],
    10: ['全城客群', '礼品馈赠', '到店率低', '高客单价', '节日营销', '25-50 岁'],
  };

  const summaryTemplates = [
    `半径 ${r} 公里内的核心客群以"${pick(keywordPool[r])}"为主,平均到店距离 ${(r * 0.6).toFixed(1)} 公里,推荐优先投入 ${pick(['企微私域', '朋友圈广告', '抖音同城', '美团卡券'])}。`,
    `${r} km 圈层画像:${pick(keywordPool[r])} 占比最高,${pick(['复购周期短', '价格敏感度中等', '社交裂变意愿强', '到店动因以场景化为主'])},建议使用"${pick(['工作日午间', '周末早午餐', '下班前 1 小时', '节假日午后'])}"作为触达窗口。`,
    `基于近 7 天 ${r} 公里行为数据,该圈层偏好的关键词是"${pick(keywordPool[r])}"、"${pick(keywordPool[r])}",AI 建议话术围绕"${pick(Object.keys(OFFER))}"展开。`,
  ];

  return {
    summary: pick(summaryTemplates),
    radar,
    keywords: keywordPool[r].slice(0, 6),
    highlights: [
      `${r} km 内"${input.category || '同品类'}"门店 ${8 + r * 3} 家,差异化机会在"${pick(ind.sellingPoints)}"`,
      `高潜 POI ${20 + r * 4} 个,写字楼 / 商场占比 ${40 + r * 2}%`,
      `近 7 天同圈层加微成本 ¥${(2 + r * 0.6).toFixed(1)},低于行业均值 18%`,
    ],
  };
};

// ================ 文案生成 ================
export interface CopyInput {
  channel: 'sms' | 'wechat' | 'douyin' | 'card' | 'phone';
  radiusKm: 3 | 5 | 8 | 10;
  category?: string;
  offer?: keyof typeof OFFER;
  timeSlot?: keyof typeof TIME_SLOT;
  personaKeywords?: string[];
}

export interface Copy {
  title: string;
  body: string;
  cta: string;
  channel: CopyInput['channel'];
  offer: string;
  estimatedOpen: number; // 0-100 模拟打开率
  estimatedConvert: number; // 0-100 模拟转化率
}

const TITLE_BANK: Record<CopyInput['channel'], string[]> = {
  sms:    ['【邻客 AI】限时福利提醒', '【门店】今天到店有惊喜', '【工作日特惠】见信如面'],
  wechat: ['你今天的外卖省钱攻略', 'CBD 的人都悄悄来这家', '距离你 2 公里的秘密基地', '5 公里内的人都在领'],
  douyin: ['同城探店·3 公里也能到', '今天被这家圈粉了', '雨天更配的暖心套餐'],
  card:   ['30 元代金券·3 公里专享', '限时 9.9 元·3 公里专享', '会员日 5 折·仅限今日'],
  phone:  ['[AI 外呼] 关于您附近的福利', '[AI 外呼] 打扰您一分钟', '[AI 外呼] 3 公里内有家店想推荐'],
};

const BODY_BANK: Record<CopyInput['channel'], ((ctx: { offer: string; ind: (typeof INDUSTRY)[string]; time: (typeof TIME_SLOT)[string]; r: number; poi: string }) => string)[]> = {
  sms: [
    ({ offer, ind, time, r }) => `亲爱的[姓名],${time.prefix}好!门店推出 ${r} 公里内"${offer}"福利:${pick(ind.sellingPoints)}。凭短信 ${time.cta} 核销,每天限 30 份。`,
    ({ offer, ind, r }) => `[门店] ${r} km 内的您,${offer}仅 3 天:${pick(ind.sellingPoints)}。到店报手机号即可,过时不再。`,
  ],
  wechat: [
    ({ offer, ind, time, r, poi }) => `在 ${r} km 内的「${poi}」上班 / 居住的你,今天 ${time.prefix}好。我们想请你 ${time.cta} 体验一份"${offer}":${pick(ind.sellingPoints)}。名额有限,先到先得。`,
    ({ offer, ind, r, poi }) => `身边的朋友都悄悄来我们这家了——${r} km 内的"${poi}"邻居专属:"${offer}"。${pick(ind.sellingPoints)},工作日随时欢迎。`,
  ],
  douyin: [
    ({ offer, ind, time, r }) => `${time.prefix}好,3 公里内也能刷到的探店:${pick(ind.audience)} 都在 ${time.cta} 来这家。今天"${offer}",${pick(ind.sellingPoints)}。`,
    ({ offer, ind, r }) => `下雨 / 加班 / 不知道吃啥,点开这条就对了。${r} km 专享"${offer}",${pick(ind.sellingPoints)}。`,
  ],
  card: [
    ({ offer, ind, r }) => `${r} 公里专享卡券:"${offer}"。${pick(ind.sellingPoints)},可与门店其他活动叠加。`,
  ],
  phone: [
    ({ offer, ind, r, poi }) => `您好,我是 ${r} 公里内「[门店]」的 AI 顾问小邻。我们最近给周边"${poi}"的朋友准备了一份"${offer}":${pick(ind.sellingPoints)}。请问您本周方便到店体验吗?不合适也没关系,我可以为您取消。`,
  ],
};

export const generateCopy = (input: CopyInput): Copy[] => {
  const ind = INDUSTRY[input.category || 'default'] || INDUSTRY.default;
  const offer = OFFER[input.offer || pick(['discount', 'gift', 'coupon', 'trial', 'member'])];
  const time = TIME_SLOT[input.timeSlot || 'noon'];
  const r = input.radiusKm;
  const poi = input.personaKeywords?.[0] || '写字楼';

  const out: Copy[] = [];
  for (let i = 0; i < 3; i++) {
    const title = pick(TITLE_BANK[input.channel]);
    const body = pick(BODY_BANK[input.channel])({ offer, ind, time, r, poi });
    const cta = CHANNEL_TONE[input.channel].suffix;
    // 模拟打开 / 转化率:渠道特征 + 时段
    const base = input.channel === 'sms' ? 78 : input.channel === 'wechat' ? 64 : input.channel === 'phone' ? 52 : 71;
    const open = Math.min(98, base + Math.floor(Math.random() * 18));
    const conv = Math.max(2, Math.min(28, open * 0.18 + Math.random() * 4));
    out.push({ title, body, cta, channel: input.channel, offer, estimatedOpen: open, estimatedConvert: Math.round(conv) });
  }
  return out;
};

export const buildDefaultFlow = (radiusKm: number) => {
  type FlowStep = { id: string; type: 'channel'; channel: 'sms' | 'wechat' | 'douyin' | 'phone' } | { id: string; type: 'wait'; min: number } | { id: string; type: 'card' };
  const base: FlowStep[] = [
    { id: 'n1', type: 'channel', channel: 'wechat' },
    { id: 'n2', type: 'wait', min: 1440 },
    { id: 'n3', type: 'channel', channel: radiusKm >= 5 ? 'douyin' : 'sms' },
    { id: 'n4', type: 'card' },
  ];
  if (radiusKm >= 8) {
    base.push({ id: 'n5', type: 'channel', channel: 'phone' });
  }
  return base;
};

export const todaySuggestion = (radiusKm: number) => {
  const list = [
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
    {
      title: 'AI 智能外呼',
      body: '今日 18-20 点为最佳外呼窗口,AI 已为 8km 圈层 120 位高潜客户准备好个性化话术,接通率预估 41%。',
      cta: '启动外呼',
    },
  ];
  return list;
};

export const INDUSTRY_LIST = Object.keys(INDUSTRY);
export const OFFER_LIST = Object.keys(OFFER);
export const TIME_SLOT_LIST = Object.keys(TIME_SLOT);
export const CHANNEL_LIST = Object.keys(CHANNEL_TONE);
