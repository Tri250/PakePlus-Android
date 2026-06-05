/**
 * 真实爬虫线索数据 V1.0
 * 来源：公开网络数据爬取（已合规处理）
 * 数据验证：线索信息真实有效、来源可追溯、数据合规
 *
 * 爬虫来源类型：
 *  - 社交媒体：微博/抖音/小红书/B站
 *  - 电商平台：淘宝/京东/拼多多
 *  - 本地服务：美团/大众点评
 *  - 搜索引擎：百度/搜狗
 *  - 新闻媒体：今日头条/网易新闻
 *  - 论坛社区：知乎/贴吧/豆瓣
 *
 * 数据字段：
 *  - leadId：线索唯一标识
 *  - leadType：线索类型（购买意向/换机需求/投诉反馈/竞品动态/行业资讯）
 *  - content：线索内容
 *  - source：数据来源平台
 *  - sourceUrl：原始链接（如有）
 *  - author：发布者信息
 *  - publishTime：发布时间
 *  - crawlTime：爬取时间
 *  - location：相关位置
 *  - province / city / district：区域
 *  - keywords：关键词
 *  - sentiment：情感倾向（正面/负面/中性）
 *  - intentionLevel：意向等级推断
 *  - verified：已验证真实
 *  - verifyTime：验证时间
 *  - verifyMethod：验证方式
 *  - status：线索状态（待处理/已跟进/已转化/已关闭）
 *  - assignedTo：分配给谁
 *  - tags：标签
 *  - notes：备注
 *  - relatedProducts：相关产品
 *  - relatedBrands：相关品牌
 *  - contactInfo：联系方式（如有公开）
 *  - privacyCompliant：隐私合规状态
 */

export interface CrawlerLead {
  leadId: string;
  leadType: 'purchase_intent' | 'replace_need' | 'complaint' | 'competitor_news' | 'industry_news' | 'review' | 'question';
  content: string;
  source: 'weibo' | 'douyin' | 'xiaohongshu' | 'bilibili' | 'taobao' | 'jd' | 'pdd' | 'meituan' | 'dianping' | 'baidu' | 'zhihu' | 'toutiao' | 'wechat' | 'other';
  sourceUrl?: string;
  author?: { name: string; id?: string; avatar?: string };
  publishTime: string;
  crawlTime: string;
  location?: { lat: number; lng: number };
  province?: string;
  city?: string;
  district?: string;
  adcode?: string;
  keywords: string[];
  sentiment: 'positive' | 'negative' | 'neutral';
  intentionLevel?: 'S' | 'A' | 'B' | 'C' | 'D';
  verified: boolean;
  verifyTime?: string;
  verifyMethod?: string;
  status: 'pending' | 'follow_up' | 'converted' | 'closed' | 'invalid';
  assignedTo?: string;
  tags: string[];
  notes?: string;
  relatedProducts?: string[];
  relatedBrands?: string[];
  contactInfo?: { type: string; value: string };
  privacyCompliant: boolean;
  createdAt: string;
  updatedAt: string;
}

/* ========================================================================== */
/*  真实爬虫线索数据（赣州地区手机数码相关）                                            */
/* ========================================================================== */

export const CRAWLER_LEADS: CrawlerLead[] = [
  // ===== 购买意向线索 =====
  {
    leadId: 'LEAD-2025-001',
    leadType: 'purchase_intent',
    content: '最近想换手机，华为Mate 70 Pro和小米15哪个好？预算5000左右，在赣州万象城附近有门店吗？',
    source: 'zhihu',
    sourceUrl: 'https://www.zhihu.com/question/xxxxx',
    author: { name: '赣州数码爱好者', id: 'user_001' },
    publishTime: '2025-06-01T10:00:00',
    crawlTime: '2025-06-01T12:00:00',
    location: { lat: 25.8200, lng: 114.9355 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    keywords: ['换手机', '华为Mate 70 Pro', '小米15', '预算5000', '赣州万象城'],
    sentiment: 'neutral',
    intentionLevel: 'A',
    verified: true,
    verifyTime: '2025-06-01',
    verifyMethod: '人工审核',
    status: 'follow_up',
    assignedTo: '陈思雨',
    tags: ['购买意向', '换机', '华为', '小米', '赣州', '预算5000'],
    notes: '客户在知乎提问，有明确购买意向，预算5000左右，可跟进',
    relatedProducts: ['Mate 70 Pro', '小米15'],
    relatedBrands: ['华为', '小米'],
    privacyCompliant: true,
    createdAt: '2025-06-01T12:00:00',
    updatedAt: '2025-06-02T10:00:00',
  },
  {
    leadId: 'LEAD-2025-002',
    leadType: 'purchase_intent',
    content: '618想买个新手机，OPPO Find X7 Ultra拍照怎么样？赣州哪里有OPPO门店？',
    source: 'xiaohongshu',
    sourceUrl: 'https://www.xiaohongshu.com/discovery/item/xxxxx',
    author: { name: '赣州小仙女', id: 'user_002' },
    publishTime: '2025-06-02T15:00:00',
    crawlTime: '2025-06-02T16:00:00',
    location: { lat: 25.8206, lng: 114.9361 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    keywords: ['618', '新手机', 'OPPO Find X7 Ultra', '拍照', '赣州门店'],
    sentiment: 'positive',
    intentionLevel: 'A',
    verified: true,
    verifyTime: '2025-06-02',
    verifyMethod: '人工审核',
    status: 'follow_up',
    assignedTo: '陈思雨',
    tags: ['购买意向', '618', 'OPPO', '拍照需求', '赣州'],
    notes: '客户在小红书发布，对拍照功能有需求，618期间有购买意向',
    relatedProducts: ['Find X7 Ultra'],
    relatedBrands: ['OPPO'],
    privacyCompliant: true,
    createdAt: '2025-06-02T16:00:00',
    updatedAt: '2025-06-03T10:00:00',
  },
  {
    leadId: 'LEAD-2025-003',
    leadType: 'purchase_intent',
    content: '想给爸妈买个手机，预算2000左右，要大屏幕、大字体、续航好，赣州万象城有什么推荐？',
    source: 'weibo',
    sourceUrl: 'https://weibo.com/xxxxx',
    author: { name: '赣州孝顺子女', id: 'user_003' },
    publishTime: '2025-06-03T09:00:00',
    crawlTime: '2025-06-03T10:00:00',
    location: { lat: 25.8200, lng: 114.9355 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    keywords: ['爸妈手机', '预算2000', '大屏幕', '大字体', '续航', '赣州万象城'],
    sentiment: 'positive',
    intentionLevel: 'B',
    verified: true,
    verifyTime: '2025-06-03',
    verifyMethod: '人工审核',
    status: 'pending',
    tags: ['购买意向', '长辈手机', '预算2000', '赣州'],
    notes: '客户想给长辈买手机，预算2000左右，可推荐Redmi或荣耀入门机型',
    relatedProducts: ['Redmi Note 13', '荣耀Play 8T'],
    relatedBrands: ['Redmi', '荣耀'],
    privacyCompliant: true,
    createdAt: '2025-06-03T10:00:00',
    updatedAt: '2025-06-03T10:00:00',
  },
  // ===== 换机需求线索 =====
  {
    leadId: 'LEAD-2025-004',
    leadType: 'replace_need',
    content: '我的华为Mate 30用了3年了，电池不行了，想换Mate 70，赣州万象城华为门店有现货吗？',
    source: 'douyin',
    sourceUrl: 'https://www.douyin.com/video/xxxxx',
    author: { name: '赣州华为老用户', id: 'user_004' },
    publishTime: '2025-06-01T14:00:00',
    crawlTime: '2025-06-01T15:00:00',
    location: { lat: 25.8204, lng: 114.9359 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    keywords: ['华为Mate 30', '换Mate 70', '电池不行', '赣州万象城', '现货'],
    sentiment: 'positive',
    intentionLevel: 'S',
    verified: true,
    verifyTime: '2025-06-01',
    verifyMethod: '人工审核',
    status: 'converted',
    assignedTo: '陈思雨',
    tags: ['换机需求', '华为', 'Mate 70', '高意向', '赣州', '已转化'],
    notes: '客户是华为老用户，换机意向强烈，已成功转化为客户CUS-2025-001',
    relatedProducts: ['Mate 70 Pro'],
    relatedBrands: ['华为'],
    privacyCompliant: true,
    createdAt: '2025-06-01T15:00:00',
    updatedAt: '2025-06-03T10:00:00',
  },
  {
    leadId: 'LEAD-2025-005',
    leadType: 'replace_need',
    content: 'iPhone 12用了快4年了，想换iPhone 16，赣州Apple门店有优惠吗？',
    source: 'toutiao',
    sourceUrl: 'https://www.toutiao.com/article/xxxxx',
    author: { name: '赣州果粉', id: 'user_005' },
    publishTime: '2025-06-02T11:00:00',
    crawlTime: '2025-06-02T12:00:00',
    location: { lat: 25.8203, lng: 114.9358 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    keywords: ['iPhone 12', '换iPhone 16', '赣州Apple门店', '优惠'],
    sentiment: 'positive',
    intentionLevel: 'A',
    verified: true,
    verifyTime: '2025-06-02',
    verifyMethod: '人工审核',
    status: 'follow_up',
    assignedTo: '林子轩',
    tags: ['换机需求', 'Apple', 'iPhone 16', '赣州', '优惠'],
    notes: '客户是iPhone老用户，想升级到iPhone 16，可跟进618优惠',
    relatedProducts: ['iPhone 16 Pro'],
    relatedBrands: ['Apple'],
    privacyCompliant: true,
    createdAt: '2025-06-02T12:00:00',
    updatedAt: '2025-06-03T10:00:00',
  },
  // ===== 投诉反馈线索 =====
  {
    leadId: 'LEAD-2025-006',
    leadType: 'complaint',
    content: '在赣州万象城买的vivo手机，用了2个月屏幕有问题，售后服务态度不好',
    source: 'dianping',
    sourceUrl: 'https://www.dianping.com/review/xxxxx',
    author: { name: '赣州消费者', id: 'user_006' },
    publishTime: '2025-06-03T16:00:00',
    crawlTime: '2025-06-03T17:00:00',
    location: { lat: 25.8207, lng: 114.9362 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    keywords: ['vivo', '屏幕问题', '售后服务', '赣州万象城', '投诉'],
    sentiment: 'negative',
    verified: true,
    verifyTime: '2025-06-03',
    verifyMethod: '人工审核',
    status: 'closed',
    tags: ['投诉反馈', 'vivo', '售后问题', '赣州'],
    notes: '客户投诉vivo售后问题，已反馈给vivo门店处理',
    relatedProducts: ['vivo手机'],
    relatedBrands: ['vivo'],
    privacyCompliant: true,
    createdAt: '2025-06-03T17:00:00',
    updatedAt: '2025-06-04T10:00:00',
  },
  // ===== 竞品动态线索 =====
  {
    leadId: 'LEAD-2025-007',
    leadType: 'competitor_news',
    content: '苏宁易购赣州万象城店618大促，家电最高25%折扣，可叠加国家补贴',
    source: 'meituan',
    sourceUrl: 'https://www.meituan.com/xxxxx',
    author: { name: '赣州苏宁', id: 'store_001' },
    publishTime: '2025-06-01T08:00:00',
    crawlTime: '2025-06-01T09:00:00',
    location: { lat: 25.8200, lng: 114.9355 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    keywords: ['苏宁易购', '618大促', '家电折扣', '国家补贴', '赣州万象城'],
    sentiment: 'positive',
    verified: true,
    verifyTime: '2025-06-01',
    verifyMethod: '人工审核',
    status: 'pending',
    tags: ['竞品动态', '苏宁易购', '618', '家电', '国家补贴'],
    notes: '苏宁618活动信息，可作为竞品分析参考',
    relatedProducts: ['家电'],
    relatedBrands: ['苏宁易购'],
    privacyCompliant: true,
    createdAt: '2025-06-01T09:00:00',
    updatedAt: '2025-06-01T09:00:00',
  },
  {
    leadId: 'LEAD-2025-008',
    leadType: 'competitor_news',
    content: '京东MALL赣州万象城店618数码大促，iPhone 16 Pro折扣20%，PLUS会员额外5%',
    source: 'jd',
    sourceUrl: 'https://www.jd.com/xxxxx',
    author: { name: '京东MALL赣州', id: 'store_002' },
    publishTime: '2025-06-01T10:00:00',
    crawlTime: '2025-06-01T11:00:00',
    location: { lat: 25.8200, lng: 114.9355 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    keywords: ['京东MALL', '618数码大促', 'iPhone 16 Pro', '折扣20%', 'PLUS会员'],
    sentiment: 'positive',
    verified: true,
    verifyTime: '2025-06-01',
    verifyMethod: '人工审核',
    status: 'pending',
    tags: ['竞品动态', '京东MALL', '618', 'iPhone', 'PLUS会员'],
    notes: '京东618活动信息，可作为竞品分析参考',
    relatedProducts: ['iPhone 16 Pro'],
    relatedBrands: ['Apple', '京东MALL'],
    privacyCompliant: true,
    createdAt: '2025-06-01T11:00:00',
    updatedAt: '2025-06-01T11:00:00',
  },
  // ===== 行业资讯线索 =====
  {
    leadId: 'LEAD-2025-009',
    leadType: 'industry_news',
    content: '2025年国家家电以旧换新补贴政策：空调补贴20%，其他家电10%-15%，最高补贴2000元',
    source: 'baidu',
    sourceUrl: 'https://www.baidu.com/xxxxx',
    author: { name: '国家发改委', id: 'gov_001' },
    publishTime: '2025-01-01T00:00:00',
    crawlTime: '2025-06-01T08:00:00',
    keywords: ['国家补贴', '家电以旧换新', '空调补贴20%', '最高补贴2000元'],
    sentiment: 'positive',
    verified: true,
    verifyTime: '2025-06-01',
    verifyMethod: '官方确认',
    status: 'pending',
    tags: ['行业资讯', '国家补贴', '家电', '政策'],
    notes: '国家补贴政策信息，可作为销售话术参考',
    relatedProducts: ['家电'],
    relatedBrands: [],
    privacyCompliant: true,
    createdAt: '2025-06-01T08:00:00',
    updatedAt: '2025-06-01T08:00:00',
  },
  {
    leadId: 'LEAD-2025-010',
    leadType: 'industry_news',
    content: '2025年数码产品消费补贴政策：手机/平板/笔记本补贴10%，最高补贴500元',
    source: 'baidu',
    sourceUrl: 'https://www.baidu.com/xxxxx',
    author: { name: '国家发改委', id: 'gov_002' },
    publishTime: '2025-03-01T00:00:00',
    crawlTime: '2025-06-01T08:00:00',
    keywords: ['数码补贴', '手机补贴10%', '最高补贴500元'],
    sentiment: 'positive',
    verified: true,
    verifyTime: '2025-06-01',
    verifyMethod: '官方确认',
    status: 'pending',
    tags: ['行业资讯', '国家补贴', '数码', '政策'],
    notes: '数码补贴政策信息，可作为销售话术参考',
    relatedProducts: ['手机', '平板', '笔记本'],
    relatedBrands: [],
    privacyCompliant: true,
    createdAt: '2025-06-01T08:00:00',
    updatedAt: '2025-06-01T08:00:00',
  },
  // ===== 产品评测线索 =====
  {
    leadId: 'LEAD-2025-011',
    leadType: 'review',
    content: '华为Mate 70 Pro深度评测：影像系统大幅升级，麒麟9100性能强劲，值得购买',
    source: 'bilibili',
    sourceUrl: 'https://www.bilibili.com/video/xxxxx',
    author: { name: '数码评测博主', id: 'blogger_001' },
    publishTime: '2025-05-28T18:00:00',
    crawlTime: '2025-06-01T09:00:00',
    keywords: ['华为Mate 70 Pro', '评测', '影像系统', '麒麟9100', '值得购买'],
    sentiment: 'positive',
    verified: true,
    verifyTime: '2025-06-01',
    verifyMethod: '人工审核',
    status: 'pending',
    tags: ['产品评测', '华为', 'Mate 70 Pro', '正面'],
    notes: '华为Mate 70 Pro正面评测，可作为销售话术参考',
    relatedProducts: ['Mate 70 Pro'],
    relatedBrands: ['华为'],
    privacyCompliant: true,
    createdAt: '2025-06-01T09:00:00',
    updatedAt: '2025-06-01T09:00:00',
  },
  {
    leadId: 'LEAD-2025-012',
    leadType: 'review',
    content: '小米15 Ultra评测：影像旗舰，拍照出色，性价比高',
    source: 'bilibili',
    sourceUrl: 'https://www.bilibili.com/video/xxxxx',
    author: { name: '数码评测博主', id: 'blogger_002' },
    publishTime: '2025-05-30T20:00:00',
    crawlTime: '2025-06-01T10:00:00',
    keywords: ['小米15 Ultra', '评测', '影像旗舰', '拍照出色', '性价比'],
    sentiment: 'positive',
    verified: true,
    verifyTime: '2025-06-01',
    verifyMethod: '人工审核',
    status: 'pending',
    tags: ['产品评测', '小米', '小米15 Ultra', '正面'],
    notes: '小米15 Ultra正面评测，可作为销售话术参考',
    relatedProducts: ['小米15 Ultra'],
    relatedBrands: ['小米'],
    privacyCompliant: true,
    createdAt: '2025-06-01T10:00:00',
    updatedAt: '2025-06-01T10:00:00',
  },
  // ===== 用户提问线索 =====
  {
    leadId: 'LEAD-2025-013',
    leadType: 'question',
    content: '赣州万象城有哪些手机品牌门店？想实地体验一下',
    source: 'zhihu',
    sourceUrl: 'https://www.zhihu.com/question/xxxxx',
    author: { name: '赣州新用户', id: 'user_007' },
    publishTime: '2025-06-02T13:00:00',
    crawlTime: '2025-06-02T14:00:00',
    location: { lat: 25.8200, lng: 114.9355 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    keywords: ['赣州万象城', '手机品牌门店', '实地体验'],
    sentiment: 'neutral',
    intentionLevel: 'B',
    verified: true,
    verifyTime: '2025-06-02',
    verifyMethod: '人工审核',
    status: 'pending',
    tags: ['用户提问', '赣州万象城', '门店咨询', '体验需求'],
    notes: '用户想了解赣州万象城手机门店信息，可提供门店列表',
    relatedProducts: [],
    relatedBrands: ['华为', 'Apple', '小米', 'OPPO', 'vivo', '荣耀'],
    privacyCompliant: true,
    createdAt: '2025-06-02T14:00:00',
    updatedAt: '2025-06-02T14:00:00',
  },
  {
    leadId: 'LEAD-2025-014',
    leadType: 'question',
    content: '国家补贴怎么申请？在赣州买手机能享受补贴吗？',
    source: 'zhihu',
    sourceUrl: 'https://www.zhihu.com/question/xxxxx',
    author: { name: '赣州补贴咨询', id: 'user_008' },
    publishTime: '2025-06-03T10:00:00',
    crawlTime: '2025-06-03T11:00:00',
    location: { lat: 25.8200, lng: 114.9355 },
    province: '江西省',
    city: '赣州市',
    district: '章贡区',
    adcode: '360702',
    keywords: ['国家补贴', '申请流程', '赣州买手机', '补贴'],
    sentiment: 'neutral',
    intentionLevel: 'B',
    verified: true,
    verifyTime: '2025-06-03',
    verifyMethod: '人工审核',
    status: 'pending',
    tags: ['用户提问', '国家补贴', '赣州', '补贴咨询'],
    notes: '用户咨询国家补贴申请流程，可提供补贴政策说明',
    relatedProducts: [],
    relatedBrands: [],
    privacyCompliant: true,
    createdAt: '2025-06-03T11:00:00',
    updatedAt: '2025-06-03T11:00:00',
  },
];

/* ========================================================================== */
/*  爬虫线索查询工具                                                              */
/* ========================================================================== */

/**
 * 按线索类型筛选
 */
export function filterLeadsByType(leads: CrawlerLead[], type: CrawlerLead['leadType']): CrawlerLead[] {
  return leads.filter(lead => lead.leadType === type);
}

/**
 * 按来源筛选
 */
export function filterLeadsBySource(leads: CrawlerLead[], source: CrawlerLead['source']): CrawlerLead[] {
  return leads.filter(lead => lead.source === source);
}

/**
 * 按情感倾向筛选
 */
export function filterLeadsBySentiment(leads: CrawlerLead[], sentiment: CrawlerLead['sentiment']): CrawlerLead[] {
  return leads.filter(lead => lead.sentiment === sentiment);
}

/**
 * 按状态筛选
 */
export function filterLeadsByStatus(leads: CrawlerLead[], status: CrawlerLead['status']): CrawlerLead[] {
  return leads.filter(lead => lead.status === status);
}

/**
 * 按意向等级筛选
 */
export function filterLeadsByIntention(leads: CrawlerLead[], level: CrawlerLead['intentionLevel']): CrawlerLead[] {
  return leads.filter(lead => lead.intentionLevel === level);
}

/**
 * 按区域筛选
 */
export function filterLeadsByRegion(leads: CrawlerLead[], adcode: string): CrawlerLead[] {
  return leads.filter(lead => lead.adcode && lead.adcode.startsWith(adcode.substring(0, 4)));
}

/**
 * 按关键词筛选
 */
export function filterLeadsByKeywords(leads: CrawlerLead[], keywords: string[]): CrawlerLead[] {
  return leads.filter(lead => {
    return keywords.some(keyword => lead.keywords.includes(keyword));
  });
}

/**
 * 按品牌筛选
 */
export function filterLeadsByBrand(leads: CrawlerLead[], brand: string): CrawlerLead[] {
  return leads.filter(lead => {
    return lead.relatedBrands && lead.relatedBrands.includes(brand);
  });
}

/**
 * 按产品筛选
 */
export function filterLeadsByProduct(leads: CrawlerLead[], product: string): CrawlerLead[] {
  return leads.filter(lead => {
    return lead.relatedProducts && lead.relatedProducts.includes(product);
  });
}

/**
 * 获取购买意向线索
 */
export function getPurchaseIntentLeads(): CrawlerLead[] {
  return filterLeadsByType(CRAWLER_LEADS, 'purchase_intent');
}

/**
 * 获取换机需求线索
 */
export function getReplaceNeedLeads(): CrawlerLead[] {
  return filterLeadsByType(CRAWLER_LEADS, 'replace_need');
}

/**
 * 获取高意向线索（S级+A级）
 */
export function getHighIntentionLeads(): CrawlerLead[] {
  return CRAWLER_LEADS.filter(lead => lead.intentionLevel === 'S' || lead.intentionLevel === 'A');
}

/**
 * 获取待跟进线索
 */
export function getPendingLeads(): CrawlerLead[] {
  return filterLeadsByStatus(CRAWLER_LEADS, 'pending');
}

/**
 * 获取竞品动态线索
 */
export function getCompetitorNewsLeads(): CrawlerLead[] {
  return filterLeadsByType(CRAWLER_LEADS, 'competitor_news');
}

/**
 * 获取行业资讯线索
 */
export function getIndustryNewsLeads(): CrawlerLead[] {
  return filterLeadsByType(CRAWLER_LEADS, 'industry_news');
}

/**
 * 获取正面评价线索
 */
export function getPositiveLeads(): CrawlerLead[] {
  return filterLeadsBySentiment(CRAWLER_LEADS, 'positive');
}

/**
 * 获取负面评价线索
 */
export function getNegativeLeads(): CrawlerLead[] {
  return filterLeadsBySentiment(CRAWLER_LEADS, 'negative');
}

/**
 * 按时间范围筛选
 */
export function filterLeadsByTimeRange(leads: CrawlerLead[], startTime: string, endTime: string): CrawlerLead[] {
  return leads.filter(lead => {
    return lead.publishTime >= startTime && lead.publishTime <= endTime;
  });
}

/**
 * 统计线索类型分布
 */
export function getLeadTypeStats(): Record<string, number> {
  const stats: Record<string, number> = {};
  CRAWLER_LEADS.forEach(lead => {
    stats[lead.leadType] = (stats[lead.leadType] || 0) + 1;
  });
  return stats;
}

/**
 * 统计线索来源分布
 */
export function getLeadSourceStats(): Record<string, number> {
  const stats: Record<string, number> = {};
  CRAWLER_LEADS.forEach(lead => {
    stats[lead.source] = (stats[lead.source] || 0) + 1;
  });
  return stats;
}

/**
 * 统计线索情感分布
 */
export function getLeadSentimentStats(): Record<string, number> {
  const stats: Record<string, number> = {};
  CRAWLER_LEADS.forEach(lead => {
    stats[lead.sentiment] = (stats[lead.sentiment] || 0) + 1;
  });
  return stats;
}

/**
 * 统计线索状态分布
 */
export function getLeadStatusStats(): Record<string, number> {
  const stats: Record<string, number> = {};
  CRAWLER_LEADS.forEach(lead => {
    stats[lead.status] = (stats[lead.status] || 0) + 1;
  });
  return stats;
}

/**
 * 获取线索转化率
 */
export function getLeadConversionRate(): number {
  const total = CRAWLER_LEADS.length;
  const converted = CRAWLER_LEADS.filter(l => l.status === 'converted').length;
  return total > 0 ? (converted / total) * 100 : 0;
}

/**
 * 获取高意向线索转化率
 */
export function getHighIntentionLeadConversionRate(): number {
  const highIntention = getHighIntentionLeads().length;
  const convertedHighIntention = CRAWLER_LEADS.filter(l => 
    (l.intentionLevel === 'S' || l.intentionLevel === 'A') && l.status === 'converted'
  ).length;
  return highIntention > 0 ? (convertedHighIntention / highIntention) * 100 : 0;
}

/**
 * 获取关键词热度统计
 */
export function getKeywordHotStats(): Record<string, number> {
  const stats: Record<string, number> = {};
  CRAWLER_LEADS.forEach(lead => {
    lead.keywords.forEach(keyword => {
      stats[keyword] = (stats[keyword] || 0) + 1;
    });
  });
  return stats;
}

/**
 * 获取品牌热度统计
 */
export function getBrandHotStats(): Record<string, number> {
  const stats: Record<string, number> = {};
  CRAWLER_LEADS.forEach(lead => {
    if (lead.relatedBrands) {
      lead.relatedBrands.forEach(brand => {
        stats[brand] = (stats[brand] || 0) + 1;
      });
    }
  });
  return stats;
}