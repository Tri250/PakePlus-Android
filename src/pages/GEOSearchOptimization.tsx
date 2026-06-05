import { useState } from 'react';
import { 
  Search, TrendingUp, Brain, MapPin, Sparkles, Target, 
  Eye, MessageCircle, Plus, RefreshCw, BarChart3, CheckCircle2,
  AlertCircle, Zap, Award, ChevronRight, Filter, Calendar
} from 'lucide-react';

interface KeywordMetric {
  keyword: string;
  category: string;
  searchVolume: number;
  ranking: number;
  prevRanking: number;
  platforms: { name: string; rank: number }[];
  trend: 'up' | 'down' | 'stable';
}

interface PlatformStatus {
  name: string;
  logo: string;
  color: string;
  avgRank: number;
  visibility: number;
  weeklyChange: number;
}

const platforms: PlatformStatus[] = [
  { name: '豆包', logo: '🫘', color: 'blue', avgRank: 3.2, visibility: 78, weeklyChange: 12.5 },
  { name: '腾讯元宝', logo: '💎', color: 'cyan', avgRank: 4.5, visibility: 65, weeklyChange: 8.3 },
  { name: 'DeepSeek', logo: '🌊', color: 'indigo', avgRank: 5.1, visibility: 58, weeklyChange: 18.2 },
  { name: 'ChatGPT', logo: '🤖', color: 'green', avgRank: 6.8, visibility: 42, weeklyChange: 5.6 },
  { name: 'Kimi', logo: '🌙', color: 'purple', avgRank: 4.9, visibility: 55, weeklyChange: 9.8 },
  { name: '文心一言', logo: '🔥', color: 'red', avgRank: 7.2, visibility: 38, weeklyChange: -2.3 },
];

const keywords: KeywordMetric[] = [
  { 
    keyword: '附近华为体验店哪家服务好', 
    category: '服务评价',
    searchVolume: 3200, 
    ranking: 1, 
    prevRanking: 2,
    platforms: [
      { name: '豆包', rank: 1 },
      { name: '腾讯元宝', rank: 2 },
      { name: 'DeepSeek', rank: 1 },
      { name: 'ChatGPT', rank: 3 },
    ],
    trend: 'up'
  },
  { 
    keyword: '华为以旧换新补贴政策', 
    category: '促销活动',
    searchVolume: 5600, 
    ranking: 2, 
    prevRanking: 5,
    platforms: [
      { name: '豆包', rank: 2 },
      { name: '腾讯元宝', rank: 1 },
      { name: 'DeepSeek', rank: 3 },
      { name: 'Kimi', rank: 2 },
    ],
    trend: 'up'
  },
  { 
    keyword: '小米手机贴膜去哪里', 
    category: '售后服务',
    searchVolume: 4100, 
    ranking: 3, 
    prevRanking: 3,
    platforms: [
      { name: '豆包', rank: 3 },
      { name: '腾讯元宝', rank: 4 },
      { name: 'DeepSeek', rank: 2 },
    ],
    trend: 'stable'
  },
  { 
    keyword: 'OPPO数据迁移服务', 
    category: '数据服务',
    searchVolume: 2800, 
    ranking: 5, 
    prevRanking: 8,
    platforms: [
      { name: '豆包', rank: 4 },
      { name: '腾讯元宝', rank: 6 },
      { name: 'DeepSeek', rank: 5 },
    ],
    trend: 'up'
  },
  { 
    keyword: 'vivo碎屏维修多少钱', 
    category: '维修服务',
    searchVolume: 6800, 
    ranking: 8, 
    prevRanking: 6,
    platforms: [
      { name: '豆包', rank: 7 },
      { name: '腾讯元宝', rank: 9 },
      { name: 'DeepSeek', rank: 6 },
    ],
    trend: 'down'
  },
];

interface AttributionData {
  source: string;
  visits: number;
  conversion: number;
  color: string;
  percentage: number;
}

const attributions: AttributionData[] = [
  { source: '豆包AI搜索', visits: 186, conversion: 32, color: 'blue', percentage: 28 },
  { source: '腾讯元宝', visits: 142, conversion: 28, color: 'cyan', percentage: 21 },
  { source: 'DeepSeek', visits: 98, conversion: 18, color: 'indigo', percentage: 15 },
  { source: '高德地图', visits: 86, conversion: 22, color: 'red', percentage: 13 },
  { source: '小红书', visits: 75, conversion: 15, color: 'pink', percentage: 11 },
  { source: '抖音', visits: 52, conversion: 8, color: 'purple', percentage: 8 },
  { source: '其他', visits: 28, conversion: 5, color: 'gray', percentage: 4 },
];

export default function GEOSearchOptimization() {
  const [selectedCategory, setSelectedCategory] = useState('全部');
  const [refreshing, setRefreshing] = useState(false);
  const [scanning, setScanning] = useState(false);

  const handleRefresh = () => {
    setRefreshing(true);
    setTimeout(() => setRefreshing(false), 1500);
  };

  const handleScan = () => {
    setScanning(true);
    setTimeout(() => setScanning(false), 2500);
  };

  const categories = ['全部', '服务评价', '促销活动', '售后服务', '数据服务', '维修服务'];
  const filteredKeywords = selectedCategory === '全部' 
    ? keywords 
    : keywords.filter(k => k.category === selectedCategory);

  return (
    <div className="space-y-6">
      {/* 头部 */}
      <div className="bg-gradient-to-r from-violet-600 via-fuchsia-600 to-pink-600 rounded-2xl p-6 text-white">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 bg-white/20 rounded-2xl flex items-center justify-center backdrop-blur-sm">
              <Brain className="w-8 h-8" />
            </div>
            <div>
              <div className="flex items-center gap-2 mb-1">
                <h2 className="text-2xl font-bold">GEO 搜索优化引擎</h2>
                <span className="px-2 py-0.5 bg-yellow-400 text-yellow-900 text-xs font-bold rounded">2026 新增</span>
              </div>
              <p className="text-violet-100 text-sm">
                AI 搜索时代，让门店在豆包/元宝/DeepSeek 中排名靠前
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleScan}
              disabled={scanning}
              className="px-4 py-2 bg-white text-violet-600 rounded-lg font-medium hover:bg-violet-50 disabled:opacity-50 flex items-center gap-2"
            >
              <Zap className={`w-4 h-4 ${scanning ? 'animate-pulse' : ''}`} />
              {scanning ? '扫描中...' : 'AI 扫描'}
            </button>
            <button
              onClick={handleRefresh}
              disabled={refreshing}
              className="px-4 py-2 bg-white/20 backdrop-blur-sm rounded-lg hover:bg-white/30 disabled:opacity-50 flex items-center gap-2"
            >
              <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />
              刷新数据
            </button>
          </div>
        </div>

        {/* 核心指标 */}
        <div className="grid grid-cols-4 gap-4 mt-6">
          <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4">
            <div className="flex items-center gap-2 text-violet-100 text-sm mb-1">
              <Target className="w-4 h-4" />
              关键词覆盖
            </div>
            <div className="text-3xl font-bold">128</div>
            <div className="text-violet-200 text-xs mt-1">+15 本周新增</div>
          </div>
          <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4">
            <div className="flex items-center gap-2 text-violet-100 text-sm mb-1">
              <Award className="w-4 h-4" />
              首位占比
            </div>
            <div className="text-3xl font-bold">42%</div>
            <div className="text-green-300 text-xs mt-1">↑ 8.5% 较上周</div>
          </div>
          <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4">
            <div className="flex items-center gap-2 text-violet-100 text-sm mb-1">
              <Eye className="w-4 h-4" />
              月曝光量
            </div>
            <div className="text-3xl font-bold">8.6K</div>
            <div className="text-green-300 text-xs mt-1">↑ 23.1% 增长</div>
          </div>
          <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4">
            <div className="flex items-center gap-2 text-violet-100 text-sm mb-1">
              <MessageCircle className="w-4 h-4" />
              AI 归因到店
            </div>
            <div className="text-3xl font-bold">426</div>
            <div className="text-green-300 text-xs mt-1">↑ 18.6% 本月</div>
          </div>
        </div>
      </div>

      {/* AI 平台状态卡片 */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <div className="flex items-center justify-between mb-4">
          <h3 className="font-semibold text-gray-900 flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-violet-500" />
            AI 平台可见性
          </h3>
          <button className="text-sm text-violet-600 hover:text-violet-700 flex items-center gap-1">
            查看详情 <ChevronRight className="w-4 h-4" />
          </button>
        </div>
        <div className="grid grid-cols-6 gap-3">
          {platforms.map((p) => (
            <div key={p.name} className="border border-gray-200 rounded-xl p-3 hover:shadow-md transition-shadow">
              <div className="flex items-center gap-2 mb-2">
                <span className="text-2xl">{p.logo}</span>
                <span className="font-medium text-sm">{p.name}</span>
              </div>
              <div className="text-xs text-gray-500 mb-1">平均排名</div>
              <div className="flex items-baseline gap-1 mb-2">
                <span className="text-2xl font-bold text-gray-900">{p.avgRank}</span>
                <span className="text-xs text-gray-500">/ 10</span>
              </div>
              <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden mb-2">
                <div
                  className={`h-full bg-${p.color}-500 rounded-full`}
                  style={{ width: `${p.visibility}%` }}
                />
              </div>
              <div className="flex items-center justify-between text-xs">
                <span className="text-gray-500">可见性 {p.visibility}%</span>
                <span className={p.weeklyChange > 0 ? 'text-green-600' : 'text-red-600'}>
                  {p.weeklyChange > 0 ? '↑' : '↓'} {Math.abs(p.weeklyChange)}%
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 关键词矩阵管理 */}
      <div className="bg-white rounded-xl border border-gray-200">
        <div className="p-5 border-b border-gray-200">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-semibold text-gray-900 flex items-center gap-2">
              <Search className="w-5 h-5 text-violet-500" />
              品牌关键词矩阵
            </h3>
            <button className="px-3 py-1.5 bg-violet-600 text-white text-sm rounded-lg hover:bg-violet-700 flex items-center gap-1">
              <Plus className="w-4 h-4" />
              新增关键词
            </button>
          </div>
          {/* 分类筛选 */}
          <div className="flex items-center gap-2 flex-wrap">
            <Filter className="w-4 h-4 text-gray-500" />
            {categories.map((cat) => (
              <button
                key={cat}
                onClick={() => setSelectedCategory(cat)}
                className={`px-3 py-1.5 text-sm rounded-lg transition-colors ${
                  selectedCategory === cat
                    ? 'bg-violet-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                {cat}
              </button>
            ))}
          </div>
        </div>
        <div className="divide-y divide-gray-100">
          {filteredKeywords.map((kw) => (
            <div key={kw.keyword} className="p-4 hover:bg-gray-50">
              <div className="flex items-start gap-4">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-2">
                    <span className="font-medium text-gray-900">{kw.keyword}</span>
                    <span className="px-2 py-0.5 bg-gray-100 text-gray-600 text-xs rounded">
                      {kw.category}
                    </span>
                    {kw.trend === 'up' && (
                      <span className="flex items-center gap-1 text-xs text-green-600">
                        <TrendingUp className="w-3 h-3" />
                        提升 {kw.prevRanking - kw.ranking} 位
                      </span>
                    )}
                    {kw.trend === 'down' && (
                      <span className="flex items-center gap-1 text-xs text-red-600">
                        <TrendingUp className="w-3 h-3 rotate-180" />
                        下降 {kw.ranking - kw.prevRanking} 位
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-4 text-xs text-gray-500 mb-2">
                    <span>月搜索量: {kw.searchVolume.toLocaleString()}</span>
                    <span>•</span>
                    <span>综合排名: <span className="font-medium text-violet-600">第 {kw.ranking} 位</span></span>
                  </div>
                  {/* 平台排名 */}
                  <div className="flex items-center gap-2 flex-wrap">
                    {kw.platforms.map((p) => (
                      <div key={p.name} className="flex items-center gap-1 px-2 py-1 bg-gray-50 rounded text-xs">
                        <span className="text-gray-600">{p.name}</span>
                        <span className={`font-bold ${
                          p.rank === 1 ? 'text-yellow-600' : 
                          p.rank <= 3 ? 'text-green-600' : 
                          p.rank <= 5 ? 'text-blue-600' : 'text-gray-600'
                        }`}>
                          #{p.rank}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <button className="p-2 hover:bg-gray-100 rounded-lg" title="查看详情">
                    <Eye className="w-4 h-4 text-gray-400" />
                  </button>
                  <button className="p-2 hover:bg-gray-100 rounded-lg" title="优化建议">
                    <Sparkles className="w-4 h-4 text-violet-500" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* AI 内容生成 + 到店归因 */}
      <div className="grid grid-cols-2 gap-6">
        {/* AI 内容生成 */}
        <div className="bg-white rounded-xl border border-gray-200">
          <div className="p-5 border-b border-gray-200">
            <h3 className="font-semibold text-gray-900 flex items-center gap-2">
              <Sparkles className="w-5 h-5 text-pink-500" />
              AI 门店描述生成
            </h3>
            <p className="text-xs text-gray-500 mt-1">符合豆包/元宝/DeepSeek 抓取规范的结构化内容</p>
          </div>
          <div className="p-5 space-y-3">
            <div className="p-3 bg-gradient-to-r from-violet-50 to-pink-50 rounded-lg border border-violet-100">
              <div className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-green-500 flex-shrink-0 mt-0.5" />
                <div>
                  <p className="text-sm font-medium text-gray-900">门店名称</p>
                  <p className="text-sm text-gray-600 mt-0.5">华为体验店·中关村旗舰店</p>
                </div>
              </div>
            </div>
            <div className="p-3 bg-gradient-to-r from-violet-50 to-pink-50 rounded-lg border border-violet-100">
              <div className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-green-500 flex-shrink-0 mt-0.5" />
                <div>
                  <p className="text-sm font-medium text-gray-900">地址定位</p>
                  <p className="text-sm text-gray-600 mt-0.5">北京市海淀区中关村大街1号（地铁4号线中关村站A口）</p>
                </div>
              </div>
            </div>
            <div className="p-3 bg-gradient-to-r from-violet-50 to-pink-50 rounded-lg border border-violet-100">
              <div className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-green-500 flex-shrink-0 mt-0.5" />
                <div>
                  <p className="text-sm font-medium text-gray-900">特色服务</p>
                  <p className="text-sm text-gray-600 mt-0.5">Mate60 Pro / P70 系列现货 · 以旧换新最高补贴 2000 元 · 免费贴膜 · 1 小时碎屏维修</p>
                </div>
              </div>
            </div>
            <div className="p-3 bg-gradient-to-r from-violet-50 to-pink-50 rounded-lg border border-violet-100">
              <div className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-green-500 flex-shrink-0 mt-0.5" />
                <div>
                  <p className="text-sm font-medium text-gray-900">真实评价摘要</p>
                  <p className="text-sm text-gray-600 mt-0.5">"服务热情专业，以旧换新价格公道""手机壳款式多，配件齐全"</p>
                </div>
              </div>
            </div>
            <button className="w-full py-2 bg-gradient-to-r from-violet-600 to-pink-600 text-white rounded-lg font-medium hover:from-violet-700 hover:to-pink-700 flex items-center justify-center gap-2">
              <Sparkles className="w-4 h-4" />
              重新生成内容
            </button>
          </div>
        </div>

        {/* 到店归因追踪 */}
        <div className="bg-white rounded-xl border border-gray-200">
          <div className="p-5 border-b border-gray-200">
            <h3 className="font-semibold text-gray-900 flex items-center gap-2">
              <MapPin className="w-5 h-5 text-blue-500" />
              到店归因追踪
            </h3>
            <p className="text-xs text-gray-500 mt-1">本周 667 位客户到店来源</p>
          </div>
          <div className="p-5">
            {/* 来源分布图 */}
            <div className="space-y-3">
              {attributions.map((a) => (
                <div key={a.source}>
                  <div className="flex items-center justify-between text-sm mb-1">
                    <span className="text-gray-700">{a.source}</span>
                    <span className="text-gray-900 font-medium">
                      {a.visits} 人 ({a.percentage}%)
                    </span>
                  </div>
                  <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                    <div
                      className={`h-full bg-${a.color}-500 rounded-full`}
                      style={{ width: `${a.percentage * 2}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>

            {/* AI 搜索占比 */}
            <div className="mt-5 p-4 bg-gradient-to-r from-violet-50 to-fuchsia-50 rounded-lg border border-violet-100">
              <div className="flex items-center gap-2 mb-2">
                <Brain className="w-5 h-5 text-violet-600" />
                <span className="font-medium text-gray-900">AI 搜索归因占比</span>
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl font-bold text-violet-600">64%</span>
                <span className="text-sm text-gray-500">较上月 ↑ 12%</span>
              </div>
              <p className="text-xs text-gray-500 mt-2">
                豆包 28% + 腾讯元宝 21% + DeepSeek 15% = 64% 客户通过 AI 搜索到店
              </p>
            </div>

            {/* NFC 碰一碰 */}
            <div className="mt-4 p-4 bg-blue-50 rounded-lg border border-blue-100">
              <div className="flex items-center gap-2 mb-2">
                <Zap className="w-5 h-5 text-blue-600" />
                <span className="font-medium text-gray-900">NFC 碰一碰</span>
              </div>
              <p className="text-xs text-gray-600">
                客户手机碰一碰门店 NFC 标签，自动跳转 AI 搜索确认页，提升归因精度
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* 周报 */}
      <div className="bg-white rounded-xl border border-gray-200">
        <div className="p-5 border-b border-gray-200 flex items-center justify-between">
          <h3 className="font-semibold text-gray-900 flex items-center gap-2">
            <BarChart3 className="w-5 h-5 text-violet-500" />
            GEO 优化周报
          </h3>
          <div className="flex items-center gap-2 text-sm text-gray-500">
            <Calendar className="w-4 h-4" />
            2026-01-13 ~ 2026-01-19
          </div>
        </div>
        <div className="p-5 space-y-3">
          <div className="flex items-start gap-3 p-3 bg-green-50 rounded-lg border border-green-100">
            <CheckCircle2 className="w-5 h-5 text-green-500 flex-shrink-0 mt-0.5" />
            <div>
              <p className="font-medium text-green-900">关键词排名整体提升</p>
              <p className="text-sm text-green-700 mt-1">
                "华为以旧换新" 关键词从第 5 位提升至第 2 位，"OPPO 数据迁移" 从第 8 位提升至第 5 位
              </p>
            </div>
          </div>
          <div className="flex items-start gap-3 p-3 bg-yellow-50 rounded-lg border border-yellow-100">
            <AlertCircle className="w-5 h-5 text-yellow-500 flex-shrink-0 mt-0.5" />
            <div>
              <p className="font-medium text-yellow-900">vivo 碎屏维修 排名下降</p>
              <p className="text-sm text-yellow-700 mt-1">
                建议：增加"vivo 官方授权维修"内容，添加价格透明度信息，更新客户评价摘要
              </p>
            </div>
          </div>
          <div className="flex items-start gap-3 p-3 bg-blue-50 rounded-lg border border-blue-100">
            <Sparkles className="w-5 h-5 text-blue-500 flex-shrink-0 mt-0.5" />
            <div>
              <p className="font-medium text-blue-900">AI 建议</p>
              <p className="text-sm text-blue-700 mt-1">
                建议补充"手机贴膜服务"相关关键词，竞品在该词条表现较弱，是突破口
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
