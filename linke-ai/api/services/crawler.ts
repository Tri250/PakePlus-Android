/**
 * 真实数据爬虫采集模拟器
 * 模拟从大众点评 / 美团 / 高德地图 / 百度地图等公开平台
 * 抓取 3-5-8-10 km 范围内真实商家的联系方式
 *
 * ⚠️ 演示用:实际生产环境需对接官方开放平台 API
 *    - 美团开放平台:https://developer.meituan.com
 *    - 大众点评: 商家信息
 *    - 高德地图 Web API:https://lbs.amap.com/api
 *    - 百度地图 Web API:https://lbsyun.baidu.com
 *    - 腾讯位置服务:https://lbs.qq.com
 */
import { NATIONAL_POI, estimateAudience, type RealPOI } from './poi-data.js';

export interface CrawledLead {
  // 基础信息
  name: string;          // 客户姓名(真实姓名)
  phone: string;         // 手机号(脱敏)
  phoneRaw: string;      // 原始手机号(管理员可见)
  wechat?: string;       // 微信号
  gender: 'M' | 'F';     // 性别
  age: number;           // 年龄
  occupation: string;    // 职业
  // 来源信息
  source: string;        // 来源平台
  sourceUrl?: string;    // 来源链接
  fromRadius: 3 | 5 | 8 | 10;
  fromPoi: string;       // 来自哪个 POI
  fromPoiCategory: string;
  fromDistrict: string;   // 来自哪个区县
  fromCity: string;
  fromProvince: string;
  // 行为信息
  visitedPois: string[]; // 访问过的 POI
  hotScore: number;      // AI 评分 0-100
  intentScore: number;   // 购买意向 0-100
  ltv: number;           // 预估客单价
  // 元信息
  crawledAt: number;     // 爬取时间戳
  notes: string;         // AI 备注
}

// 中文姓氏 + 男女名
const SURNAMES_F = ['王', '李', '张', '刘', '陈', '杨', '赵', '黄', '周', '吴', '徐', '孙', '胡', '朱', '高', '林', '何', '郭', '马', '罗'];
const GIVEN_F = ['婷', '静', '丽', '敏', '艳', '娟', '芳', '霞', '梅', '燕', '玲', '红', '萍', '文', '倩', '婷', '华', '丹', '玲', '莹', '洁', '丽', '颖', '露', '怡'];
const GIVEN_M = ['伟', '强', '磊', '军', '勇', '涛', '明', '超', '刚', '峰', '亮', '辉', '建国', '建斌', '海涛', '小龙', '凯', '浩然', '宇轩', '子轩', '梓豪', '俊杰', '志远'];
const OCCUPATIONS = [
  '产品经理', '软件工程师', 'UI 设计师', '运营专员', '销售经理', '客户经理', '市场总监',
  '财务总监', 'HR 经理', '总裁助理', '投资经理', '咨询顾问', '律师', '医生', '教师',
  '自由职业', '公务员', '企业主', '网红', '自媒体', '全职妈妈', '大学生', '研究生',
];

// 真实脱敏手机号(演示用,以 139 / 138 / 136 / 188 / 199 开头)
// ⚠️ 这些都是模拟数据,不会联系到任何真实用户
function genPhone(seed: number): string {
  const prefixes = ['139', '138', '136', '188', '199', '186', '156', '177', '152', '188'];
  const p = prefixes[seed % prefixes.length];
  const n = String((seed * 7 + 10000000) % 100000000).padStart(8, '0');
  return `${p}${n}`;
}

function genWechat(name: string, seed: number): string {
  const cleanName = name.toLowerCase().replace(/\s/g, '');
  return `${cleanName}_${(seed * 13 % 10000).toString().padStart(4, '0')}`;
}

// 爬取"附近的人"模拟
export function crawlNearbyLeads(
  storeId: string,
  storeLng: number,
  storeLat: number,
  storeCity: string,
  radiusKm: 3 | 5 | 8 | 10,
  count: number = 24,
): CrawledLead[] {
  // 1. 在此范围内按距离过滤真实 POI
  // 城市模糊匹配:支持 "上海" / "上海浦东" / "上海浦东店" 都能命中 city="上海" 的 POI
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
      const dist = Math.sqrt(dx * dx + dy * dy) * 111; // 1 度 ≈ 111 km
      return { poi: p, dist };
    })
    .filter((x) => x.dist <= radiusKm)
    .sort((a, b) => a.dist - b.dist);

  // 2. 模拟"采集周边人"
  // 每个 POI 估算可触达人数 / 100,得到"潜在客户池"
  const leads: CrawledLead[] = [];
  const used = new Set<string>();
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
    const surname = SURNAMES_F[seed % SURNAMES_F.length];
    const given = isFemale ? GIVEN_F[seed % GIVEN_F.length] : GIVEN_M[seed % GIVEN_M.length];
    const name = surname + given;

    const phoneRaw = genPhone(seed + 11);
    const phone = phoneRaw.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');

    // 年龄 / 职业
    const age = 22 + ((seed * 3) % 30);
    const occupation = OCCUPATIONS[seed % OCCUPATIONS.length];

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
      gender: isFemale ? 'F' : 'M',
      age,
      occupation,
      source: ['美团商家', '大众点评', '高德地图', '百度地图', '抖音同城', '小红书种草', '微信朋友圈', '腾讯位置'][cursor % 8],
      fromRadius: radiusKm,
      fromPoi: poi.name,
      fromPoiCategory: poi.category,
      fromDistrict: poi.district || '未知',
      fromCity: poi.city,
      fromProvince: poi.province,
      visitedPois: [poi.name],
      hotScore,
      intentScore,
      ltv,
      crawledAt: Date.now() - (cursor % 72) * 3600_000, // 过去 72 小时内
      notes: generateNotes(poi.name, poi.category, dist, intentScore),
    };
    leads.push(lead);
    used.add(phone);
  }

  // 按热评分降序
  leads.sort((a, b) => b.hotScore - a.hotScore);
  return leads;
}

function generateNotes(poi: string, category: string, dist: number, intent: number): string {
  const intentText = intent >= 70 ? '强烈' : intent >= 50 ? '较有' : '一般';
  const catLabel: Record<string, string> = {
    office: '写字楼',
    mall: '商场',
    school: '学校',
    residence: '住宅',
    subway: '地铁',
    park: '公园',
    community: '社区',
    street: '街道',
    cbd: '商圈',
    industrial: '产业园',
  };
  return `来自${poi}${catLabel[category] || ''}周边 ${dist.toFixed(1)}km,${intentText}购买意向。近期常出没,可推送午间 / 下班优惠券。`;
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
}

const crawlLogs: CrawlLog[] = [];

export function recordCrawlLog(log: CrawlLog) {
  crawlLogs.unshift(log);
  if (crawlLogs.length > 50) crawlLogs.pop();
}

export function getCrawlLogs(storeId?: string): CrawlLog[] {
  return storeId ? crawlLogs.filter((l) => l.storeId === storeId) : crawlLogs;
}
