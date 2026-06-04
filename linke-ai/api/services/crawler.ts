/**
 * 真实数据爬虫采集模拟器
 * 模拟从 百度地图 / 高德地图 / 腾讯位置 三大 LBS 平台
 * 抓取 3-5-8-10 km 范围内真实用户的精准"名片"信息
 *
 * ⚠️ 演示用:实际生产环境需对接官方开放平台 API
 *    - 高德地图 Web API:https://lbs.amap.com/api  (search_around / regeo)
 *    - 百度地图 Web API:https://lbsyun.baidu.com  (place/v2/search)
 *    - 腾讯位置服务:    https://lbs.qq.com          (ws/place/v1/search)
 *    - 真实行业数据:    餐饮 / 购物 / 生活 / 出行 POI 详情
 */
import { NATIONAL_POI, estimateAudience, type RealPOI } from './poi-data.js';

// 三大地图平台(主要数据源)
const MAP_SOURCES = [
  { key: 'baidu',  name: '百度地图',    baseUrl: 'https://api.map.baidu.com/place/v2',     weight: 0.34 },
  { key: 'amap',   name: '高德地图',    baseUrl: 'https://restapi.amap.com/v3/place',     weight: 0.33 },
  { key: 'tencent', name: '腾讯位置',    baseUrl: 'https://apis.map.qq.com/ws/place/v1',   weight: 0.33 },
];

// 补充数据源
const EXTRA_SOURCES = [
  { key: 'meituan', name: '美团商家',     weight: 0.15 },
  { key: 'dianping', name: '大众点评',    weight: 0.12 },
  { key: 'douyin',  name: '抖音同城',     weight: 0.10 },
  { key: 'xiaohongshu', name: '小红书种草', weight: 0.08 },
  { key: 'wechat',  name: '微信朋友圈',   weight: 0.06 },
];

export interface CrawledLead {
  // ===== 名片信息(精准) =====
  name: string;          // 客户姓名(真实姓名)
  phone: string;         // 手机号(脱敏)
  phoneRaw: string;      // 原始手机号(管理员可见)
  wechat?: string;       // 微信号
  email?: string;        // 邮箱
  // 职业/公司
  title: string;         // 职位
  company: string;       // 公司
  industry: string;      // 行业
  // 个人信息
  gender: 'M' | 'F';
  age: number;
  // 来源信息
  source: string;        // 来源平台(百度地图 / 高德地图 / 腾讯位置 / 美团商家 / 大众点评 / 抖音同城 / 小红书种草 / 微信朋友圈)
  sourceUrl?: string;    // 来源链接
  fromRadius: 3 | 5 | 8 | 10;
  fromPoi: string;       // 来自哪个 POI
  fromPoiCategory: string;
  fromPoiAddress: string;
  fromDistrict: string;
  fromCity: string;
  fromProvince: string;
  // 行为信息
  visitedPois: string[];
  hotScore: number;      // AI 评分 0-100
  intentScore: number;   // 购买意向 0-100
  ltv: number;           // 预估客单价
  // 元信息
  crawledAt: number;
  notes: string;
}

// 中文姓氏 + 男女名 + 常见职位
const SURNAMES = ['王', '李', '张', '刘', '陈', '杨', '赵', '黄', '周', '吴', '徐', '孙', '胡', '朱', '高', '林', '何', '郭', '马', '罗', '梁', '宋', '郑', '谢', '韩', '唐', '冯', '于', '董', '萧'];
const GIVEN_F = ['婷', '静', '丽', '敏', '艳', '娟', '芳', '霞', '梅', '燕', '玲', '红', '萍', '文', '倩', '华', '丹', '莹', '洁', '颖', '露', '怡', '雅', '诗', '涵', '琪', '悦', '欣', '可', '心'];
const GIVEN_M = ['伟', '强', '磊', '军', '勇', '涛', '明', '超', '刚', '峰', '亮', '辉', '建国', '建斌', '海涛', '小龙', '凯', '浩然', '宇轩', '子轩', '梓豪', '俊杰', '志远', '嘉伟', '昊然', '天宇', '瑞', '昊', '哲', '睿'];

const TITLES = [
  '产品经理', '高级产品经理', '产品总监', '软件工程师', '高级工程师', '技术总监', 'CTO',
  'UI 设计师', '视觉设计师', 'UX 设计师', '运营专员', '运营总监', '销售经理', '客户经理',
  '大客户经理', '市场总监', 'CMO', '财务总监', 'CFO', 'HR 经理', 'HRBP', '总裁助理',
  '投资经理', '投资总监', '咨询顾问', '高级顾问', '合伙人', '律师', '法务总监',
  '医生', '主任医师', '主治医师', '教师', '高校教师', '校长', '公务员', '处长',
  '企业主', 'CEO', '创始人', '网红', 'KOL', '自媒体', '博主', '直播主',
  '全职妈妈', '宝妈', '大学生', '研究生', '博士生', '留学生', '海归',
];

const COMPANIES = [
  '阿里巴巴', '腾讯', '字节跳动', '美团', '京东', '百度', '小米', '华为', '网易', '滴滴',
  '快手', '拼多多', '携程', 'B 站', '知乎', '小红书', '得物', 'Keep', 'BOSS 直聘', '链家',
  '中信建投', '中金', '中银国际', '招商银行', '平安集团', '中国人寿', '中国平安', '泰康人寿',
  '罗兰贝格', '麦肯锡', 'BCG', '贝恩', '德勤', '普华永道', '安永', '毕马威',
  '中国电信', '中国移动', '中国联通', '国家电网', '中石油', '中石化', '中国邮政',
  '万达集团', '万科地产', '恒大集团', '保利地产', '龙湖地产', '碧桂园', '融创',
  '瑞幸咖啡', '海底捞', '喜茶', '奈雪', '星巴克', '麦当劳', '肯德基', '西贝',
  '中国移动咪咕', '央视', '新华社', '人民日报', '财新', '36 氪', '虎嗅',
  '美团到店', '口碑', '饿了么', '盒马', '便利蜂', '物美', '永辉', '家乐福',
];

const INDUSTRIES = ['互联网', '金融', '咨询', '法律', '医疗', '教育', '媒体', '广告', '零售', '餐饮', '房地产', '汽车', '制造业', '政务', '文化娱乐'];

const EMAIL_DOMAINS = ['163.com', 'qq.com', 'gmail.com', 'outlook.com', '126.com', 'foxmail.com', 'sina.com', 'hotmail.com', 'sina.cn'];

// 真实脱敏手机号(模拟)
function genPhone(seed: number): string {
  const prefixes = ['139', '138', '136', '188', '199', '186', '156', '177', '152', '187', '158', '133'];
  const p = prefixes[seed % prefixes.length];
  const n = String((seed * 7 + 10000000) % 100000000).padStart(8, '0');
  return `${p}${n}`;
}

function genWechat(name: string, seed: number): string {
  const cleanName = name.toLowerCase().replace(/\s/g, '');
  return `${cleanName}_${(seed * 13 % 10000).toString().padStart(4, '0')}`;
}

function genEmail(name: string, company: string, seed: number): string {
  const en = name.length > 1
    ? name
        .replace(/[王李张刘陈杨赵黄周吴徐孙胡朱高林何郭马罗]/g, (s) => ({
          王: 'wang', 李: 'li', 张: 'zhang', 刘: 'liu', 陈: 'chen', 杨: 'yang',
          赵: 'zhao', 黄: 'huang', 周: 'zhou', 吴: 'wu', 徐: 'xu', 孙: 'sun',
          胡: 'hu', 朱: 'zhu', 高: 'gao', 林: 'lin', 何: 'he', 郭: 'guo', 马: 'ma', 罗: 'luo',
        }[s] || s))
        .toLowerCase()
    : `user${seed % 1000}`;
  const enCompany = company
    .replace(/[^a-zA-Z\u4e00-\u9fa5]/g, '')
    .slice(0, 8);
  return `${en}${seed % 99}@${EMAIL_DOMAINS[seed % EMAIL_DOMAINS.length]}`;
}

// 选一个来源平台(按权重)
function pickSource(seed: number): string {
  const all = [...MAP_SOURCES, ...EXTRA_SOURCES];
  const totalWeight = all.reduce((s, x) => s + x.weight, 0);
  let r = (seed * 1009 + 7919) % 1000 / 1000 * totalWeight;
  for (const src of all) {
    r -= src.weight;
    if (r <= 0) return src.name;
  }
  return all[0].name;
}

// 爬取"附近的人/商家"
export function crawlNearbyLeads(
  storeId: string,
  storeLng: number,
  storeLat: number,
  storeCity: string,
  radiusKm: 3 | 5 | 8 | 10,
  count: number = 24,
  realLng?: number,  // 真实定位(浏览器 GPS)lng
  realLat?: number,  // 真实定位 lat
): CrawledLead[] {
  // 1. 城市模糊匹配
  const inRange = NATIONAL_POI
    .filter((p) => {
      if (p.city === storeCity) return true;
      if (storeCity.startsWith(p.city)) return true;
      if (storeCity.includes(p.city)) return true;
      return false;
    })
    .map((p) => {
      const dx = p.lng - storeLng;
      const dy = p.lat - storeLat;
      const dist = Math.sqrt(dx * dx + dy * dy) * 111;
      return { poi: p, dist };
    })
    .filter((x) => x.dist <= radiusKm)
    .sort((a, b) => a.dist - b.dist);

  // 2. 模拟"采集周边人" — 真实位置(浏览器 GPS)优先
  const leads: CrawledLead[] = [];
  let cursor = Math.floor(Date.now() / 1000) % 10000;

  for (let i = 0; i < count; i++) {
    cursor++;
    const seed = storeId.length * 7 + i * 3 + cursor;

    // 选一个最近 POI
    const target = inRange[i % inRange.length] || { poi: NATIONAL_POI[i % NATIONAL_POI.length], dist: 0.5 };
    const poi = target.poi;

    // 估算此 POI 可触达人数
    const totalAudience = estimateAudience(poi);
    const sampleSize = Math.max(3, Math.min(8, Math.floor(totalAudience / 8000)));
    const sampleIdx = cursor % sampleSize;
    const dist = +(target.dist + (sampleIdx - sampleSize / 2) * 0.3).toFixed(2);

    // 生成姓名
    const isFemale = cursor % 2 === 0;
    const surname = SURNAMES[seed % SURNAMES.length];
    const given = isFemale ? GIVEN_F[seed % GIVEN_F.length] : GIVEN_M[seed % GIVEN_M.length];
    const name = surname + given;

    const phoneRaw = genPhone(seed + 11);
    const phone = phoneRaw.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');

    // 年龄 / 职业 / 公司
    const age = 22 + ((seed * 3) % 30);
    const title = TITLES[seed % TITLES.length];
    const company = COMPANIES[seed % COMPANIES.length];
    const industry = INDUSTRIES[seed % INDUSTRIES.length];

    // 来源平台(主要来自三大地图)
    const source = pickSource(seed);
    const sourceMap = MAP_SOURCES.find((s) => s.name === source);
    const sourceUrl = sourceMap ? `${sourceMap.baseUrl}?location=${storeLat.toFixed(6)},${storeLng.toFixed(6)}&radius=${radiusKm * 1000}` : undefined;

    // 评分
    const hotScore = Math.min(100, Math.max(20,
      Math.round(poi.scale / 800 + dist * -2 + (cursor % 30))
    ));
    const intentScore = Math.min(100, Math.max(10,
      Math.round(70 - dist * 3 + (cursor % 20))
    ));
    const ltv = Math.round(50 + (hotScore * 4) + (cursor % 200));

    const lead: CrawledLead = {
      name,
      phone,
      phoneRaw,
      wechat: genWechat(name, seed),
      email: genEmail(name, company, seed),
      title,
      company,
      industry,
      gender: isFemale ? 'F' : 'M',
      age,
      source,
      sourceUrl,
      fromRadius: radiusKm,
      fromPoi: poi.name,
      fromPoiCategory: poi.category,
      fromPoiAddress: poi.address || poi.name,
      fromDistrict: poi.district || '未知',
      fromCity: poi.city,
      fromProvince: poi.province,
      visitedPois: [poi.name],
      hotScore,
      intentScore,
      ltv,
      crawledAt: Date.now() - (cursor % 72) * 3600_000,
      notes: generateNotes(poi.name, poi.category, dist, intentScore, realLng, realLat),
    };
    leads.push(lead);
  }

  // 按热评分降序
  leads.sort((a, b) => b.hotScore - a.hotScore);
  return leads;
}

function generateNotes(poi: string, category: string, dist: number, intent: number, realLng?: number, realLat?: number): string {
  const intentText = intent >= 70 ? '强烈' : intent >= 50 ? '较有' : '一般';
  const catLabel: Record<string, string> = {
    office: '写字楼', mall: '商场', school: '学校', residence: '住宅', subway: '地铁',
    park: '公园', community: '社区', street: '街道', cbd: '商圈', industrial: '产业园',
  };
  let note = `来自${poi}${catLabel[category] || ''}周边 ${dist.toFixed(1)}km,${intentText}购买意向。近期常出没,可推送午间 / 下班优惠券。`;
  if (typeof realLng === 'number' && typeof realLat === 'number') {
    note += ` 🛰️ 真实定位 (${realLng.toFixed(4)}, ${realLat.toFixed(4)})`;
  }
  return note;
}

// 采集历史记录(模拟)
export interface CrawlLog {
  id: string;
  storeId: string;
  city: string;
  radiusKm: number;
  count: number;
  status: 'running' | 'done' | 'failed';
  startedAt: number;
  finishedAt?: number;
  error?: string;
  sources?: string[];
}

const crawlLogs: CrawlLog[] = [];

export function recordCrawlLog(log: CrawlLog) {
  crawlLogs.unshift(log);
  if (crawlLogs.length > 50) crawlLogs.pop();
}

export function getCrawlLogs(storeId?: string): CrawlLog[] {
  return storeId ? crawlLogs.filter((l) => l.storeId === storeId) : crawlLogs;
}
