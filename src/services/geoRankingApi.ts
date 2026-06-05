/**
 * 5.2 GEO 关键词排名查询接口 Mock
 * GET /api/geo/ranking?keyword=附近手机维修&location=116.473168,39.993015&platform=doubao
 */

export type GEOPlatform = 'doubao' | 'kimi' | 'yuanbao' | 'deepseek' | 'chatgpt' | 'wenxin';

export interface Competitor {
  rank: number;
  name: string;
  brand: string;
  exposure: number;
  matchScore: number; // 0-100
}

export interface GEORankingResponse {
  platform: GEOPlatform;
  platformName: string;
  keyword: string;
  location: string;
  rank: number;             // 我方当前排名
  totalResults: number;     // 该关键词 AI 总返回数
  exposure_7d: number;      // 7 天曝光
  click_7d: number;         // 7 天点击
  ctr: number;              // 7 天 CTR %
  competitors: Competitor[]; // 前 5 竞品
  trend7d: number[];        // 7 天排名趋势（值越小越好）
  fetchedAt: number;
}

const PLATFORM_NAMES: Record<GEOPlatform, string> = {
  doubao: '豆包',
  kimi: 'Kimi',
  yuanbao: '腾讯元宝',
  deepseek: 'DeepSeek',
  chatgpt: 'ChatGPT',
  wenxin: '文心一言',
};

export async function queryGEORanking(params: {
  keyword: string;
  location: string;
  platform: GEOPlatform;
}): Promise<GEORankingResponse> {
  // 模拟网络延迟
  await new Promise((r) => setTimeout(r, 600 + Math.random() * 400));

  // 基于关键词生成稳定但有差异的 mock 数据
  const seed = params.keyword.length + params.platform.length;
  const baseRank = (seed % 5) + 1;
  const baseExposure = 1000 + (seed * 137) % 1500;

  const competitorPool: Competitor[] = [
    { rank: 1, name: '京东手机维修', brand: '京东服务+', exposure: 3200, matchScore: 92 },
    { rank: 2, name: '顺电快修', brand: '顺电', exposure: 2800, matchScore: 88 },
    { rank: 3, name: '华为客户服务中心', brand: '华为', exposure: 2400, matchScore: 85 },
    { rank: 4, name: 'Apple 授权维修', brand: 'Apple', exposure: 2100, matchScore: 82 },
    { rank: 5, name: '小米之家维修', brand: '小米', exposure: 1800, matchScore: 78 },
  ];

  // 让本品牌挤进前 5
  const myEntry: Competitor = {
    rank: baseRank,
    name: `${PLATFORM_NAMES[params.platform]} 推荐门店`,
    brand: '本品牌',
    exposure: baseExposure,
    matchScore: 70 + (seed % 25),
  };

  const competitors = [myEntry, ...competitorPool.filter((c) => c.rank !== baseRank)]
    .sort((a, b) => a.rank - b.rank)
    .slice(0, 5)
    .map((c, idx) => ({ ...c, rank: idx + 1 }));

  return {
    platform: params.platform,
    platformName: PLATFORM_NAMES[params.platform],
    keyword: params.keyword,
    location: params.location,
    rank: baseRank,
    totalResults: 18,
    exposure_7d: baseExposure,
    click_7d: Math.round(baseExposure * 0.06 + (seed % 30)),
    ctr: parseFloat((6 + (seed % 4)).toFixed(2)),
    competitors,
    trend7d: Array.from({ length: 7 }, (_, i) => Math.max(1, baseRank + Math.sin(i + seed) * 2)),
    fetchedAt: Date.now(),
  };
}

export function listPlatforms(): { value: GEOPlatform; label: string }[] {
  return Object.entries(PLATFORM_NAMES).map(([value, label]) => ({
    value: value as GEOPlatform,
    label,
  }));
}
