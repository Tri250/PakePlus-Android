import { useMemo, useState } from 'react';
import {
  Building2,
  TrendingUp,
  Users,
  Target,
  Database,
  Shield,
  Map,
  Activity,
  Eye,
  Download,
  RefreshCw,
  Sparkles,
  ArrowUpRight,
  ArrowDownRight,
  Filter,
  Globe,
  Crown,
  Layers,
  Radio,
  CheckCircle2,
  AlertCircle,
  FileText,
  Zap,
  Clock,
  Trophy,
} from 'lucide-react';

/* -------------------------------------------------------------------------- */
/*  品牌数据中台 V2.0                                                            */
/*  总部驾驶舱 / 客群画像 / 竞品热力 / API 回传 / 营销 ROI                        */
/* -------------------------------------------------------------------------- */

interface StoreData {
  id: string;
  name: string;
  city: string;
  heatIndex: number;        // 热力指数 0-100
  intentCustomers: number;  // 换机意向客户
  conversionRate: number;   // 到店转化率 %
  competitorPenetration: number; // 竞品渗透率 %
  todayLeads: number;
  todayConversion: number;
  trend: 'up' | 'down' | 'flat';
}

const STORES: StoreData[] = [
  { id: 'ST001', name: '北京国贸旗舰店', city: '北京', heatIndex: 92, intentCustomers: 248, conversionRate: 24.6, competitorPenetration: 18, todayLeads: 47, todayConversion: 12, trend: 'up' },
  { id: 'ST002', name: '上海南京西路店', city: '上海', heatIndex: 88, intentCustomers: 215, conversionRate: 22.1, competitorPenetration: 22, todayLeads: 41, todayConversion: 10, trend: 'up' },
  { id: 'ST003', name: '深圳福田 COCO Park', city: '深圳', heatIndex: 85, intentCustomers: 198, conversionRate: 26.3, competitorPenetration: 16, todayLeads: 38, todayConversion: 11, trend: 'up' },
  { id: 'ST004', name: '广州天河城店', city: '广州', heatIndex: 79, intentCustomers: 176, conversionRate: 20.4, competitorPenetration: 25, todayLeads: 33, todayConversion: 8, trend: 'flat' },
  { id: 'ST005', name: '成都太古里店', city: '成都', heatIndex: 82, intentCustomers: 189, conversionRate: 23.8, competitorPenetration: 20, todayLeads: 36, todayConversion: 9, trend: 'up' },
  { id: 'ST006', name: '杭州西湖店', city: '杭州', heatIndex: 76, intentCustomers: 162, conversionRate: 19.2, competitorPenetration: 28, todayLeads: 30, todayConversion: 7, trend: 'down' },
  { id: 'ST007', name: '西安钟楼店', city: '西安', heatIndex: 71, intentCustomers: 142, conversionRate: 18.5, competitorPenetration: 24, todayLeads: 27, todayConversion: 6, trend: 'up' },
  { id: 'ST008', name: '南京新街口店', city: '南京', heatIndex: 68, intentCustomers: 128, conversionRate: 17.6, competitorPenetration: 30, todayLeads: 24, todayConversion: 5, trend: 'flat' },
];

const COMPETITORS = [
  { name: 'Apple 直营', count: 86, color: '#9ca3af', share: 22 },
  { name: '华为体验店', count: 124, color: '#ef4444', share: 32 },
  { name: '小米之家', count: 98, color: '#f59e0b', share: 25 },
  { name: 'OPPO 旗舰店', count: 56, color: '#10b981', share: 14 },
  { name: 'vivo 体验店', count: 28, color: '#3b82f6', share: 7 },
];

const BRAND_CRM_ENDPOINTS = [
  { name: '华为 CEM', status: 'connected', latency: 86, lastSync: '2 分钟前', icon: Crown },
  { name: '小米零售通', status: 'connected', latency: 124, lastSync: '5 分钟前', icon: Zap },
  { name: 'OPPO 零售系统', status: 'connected', latency: 96, lastSync: '3 分钟前', icon: Database },
  { name: 'vivo 零售云', status: 'warning', latency: 2400, lastSync: '12 分钟前', icon: Database },
  { name: 'Apple Reseller', status: 'connected', latency: 110, lastSync: '8 分钟前', icon: Crown },
];

interface ROIRow {
  campaign: string;
  channel: string;
  spend: number;       // 元
  leads: number;
  arrived: number;     // 到店
  closed: number;      // 成交
  cac: number;         // 单线索成本
  roi: number;         // 投资回报率
  cpa: number;         // 单到店成本
}

const ROI_ROWS: ROIRow[] = [
  { campaign: 'Mate70 上市首发', channel: '朋友圈广告', spend: 48000, leads: 612, arrived: 187, closed: 68, cac: 78, roi: 3.2, cpa: 257 },
  { campaign: '暑期学生季', channel: '小红书种草', spend: 24000, leads: 386, arrived: 102, closed: 31, cac: 62, roi: 2.4, cpa: 235 },
  { campaign: '以旧换新专项', channel: '地推 + NFC', spend: 18000, leads: 421, arrived: 156, closed: 84, cac: 43, roi: 5.8, cpa: 115 },
  { campaign: '教师节感恩', channel: '企微私域', spend: 6000, leads: 124, arrived: 38, closed: 18, cac: 48, roi: 4.1, cpa: 158 },
  { campaign: '家庭融合套餐', channel: '运营商联动', spend: 36000, leads: 218, arrived: 78, closed: 22, cac: 165, roi: 1.8, cpa: 462 },
];

export default function BrandDataPlatform() {
  const [tab, setTab] = useState<'cockpit' | 'portrait' | 'competitor' | 'api' | 'roi'>('cockpit');

  return (
    <div className="space-y-6">
      {/* 顶部 */}
      <div className="bg-gradient-to-r from-indigo-900 via-blue-800 to-cyan-700 rounded-2xl p-5 text-white">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <Database className="w-5 h-5 text-cyan-300" />
              <h1 className="text-xl font-bold">品牌数据中台</h1>
              <span className="px-2 py-0.5 text-xs bg-cyan-400/20 text-cyan-300 rounded border border-cyan-400/30">
                V2.0 新增模块
              </span>
            </div>
            <p className="text-sm text-blue-100">
              门店是品牌的数据采集终端，不是数据孤岛 · 总部驾驶舱 / 客群画像 / 竞品热力 / API 回传 / 营销 ROI
            </p>
          </div>
          <div className="flex items-center gap-3">
            <button className="flex items-center gap-1.5 px-3 py-1.5 bg-white/10 backdrop-blur border border-white/20 rounded-lg text-sm hover:bg-white/20">
              <RefreshCw className="w-4 h-4" /> 刷新
            </button>
            <button className="flex items-center gap-1.5 px-3 py-1.5 bg-white/10 backdrop-blur border border-white/20 rounded-lg text-sm hover:bg-white/20">
              <Download className="w-4 h-4" /> 导出报表
            </button>
            <button className="flex items-center gap-1.5 px-3 py-1.5 bg-cyan-400 text-cyan-950 rounded-lg text-sm font-medium hover:bg-cyan-300">
              <FileText className="w-4 h-4" /> 申请审计报告
            </button>
          </div>
        </div>
      </div>

      {/* Tab 导航 */}
      <div className="bg-white rounded-xl border border-gray-200 p-1 flex gap-1 overflow-x-auto">
        {[
          { key: 'cockpit', label: '品牌总部驾驶舱', icon: Building2 },
          { key: 'portrait', label: '客群画像看板', icon: Users },
          { key: 'competitor', label: '竞品热力地图', icon: Map },
          { key: 'api', label: '数据 API 回传', icon: Radio },
          { key: 'roi', label: '营销 ROI 看板', icon: TrendingUp },
        ].map((t) => {
          const Icon = t.icon;
          const active = tab === t.key;
          return (
            <button
              key={t.key}
              onClick={() => setTab(t.key as any)}
              className={`flex-1 min-w-[140px] flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-medium rounded-lg transition-colors ${
                active ? 'bg-indigo-600 text-white shadow-sm' : 'text-gray-600 hover:bg-gray-50'
              }`}
            >
              <Icon className="w-4 h-4" />
              {t.label}
            </button>
          );
        })}
      </div>

      {tab === 'cockpit' && <CockpitPanel />}
      {tab === 'portrait' && <PortraitPanel />}
      {tab === 'competitor' && <CompetitorPanel />}
      {tab === 'api' && <APIPanel />}
      {tab === 'roi' && <ROIPanel />}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  1. 品牌总部驾驶舱                                                            */
/* -------------------------------------------------------------------------- */

function CockpitPanel() {
  const totals = useMemo(() => {
    return STORES.reduce(
      (acc, s) => ({
        leads: acc.leads + s.todayLeads,
        conversion: acc.conversion + s.todayConversion,
        intent: acc.intent + s.intentCustomers,
        avgHeat: acc.avgHeat + s.heatIndex,
        avgRate: acc.avgRate + s.conversionRate,
        avgComp: acc.avgComp + s.competitorPenetration,
      }),
      { leads: 0, conversion: 0, intent: 0, avgHeat: 0, avgRate: 0, avgComp: 0 }
    );
  }, []);

  return (
    <div className="space-y-4">
      {/* 4 大核心指标 */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <MetricCard
          label="全国体验店"
          value={STORES.length.toString()}
          unit="家"
          icon={Building2}
          color="indigo"
          trend="+2"
          trendUp
        />
        <MetricCard
          label="今日获客"
          value={totals.leads.toString()}
          unit="人"
          icon={Users}
          color="blue"
          trend="+18.4%"
          trendUp
        />
        <MetricCard
          label="换机意向客户"
          value={totals.intent.toString()}
          unit="人"
          icon={Target}
          color="violet"
          trend="+12.7%"
          trendUp
        />
        <MetricCard
          label="平均热力指数"
          value={(totals.avgHeat / STORES.length).toFixed(1)}
          unit="℃"
          icon={Activity}
          color="emerald"
          trend="+3.2"
          trendUp
        />
      </div>

      {/* 门店排行榜 */}
      <div className="bg-white rounded-xl border border-gray-200">
        <div className="p-4 border-b border-gray-200 flex items-center gap-2">
          <Trophy className="w-4 h-4 text-amber-500" />
          <h3 className="font-semibold text-gray-900">全国门店排行榜</h3>
          <span className="text-xs text-gray-500 ml-auto">实时更新 · 30 秒前</span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-xs text-gray-500">
              <tr>
                <th className="px-4 py-2 text-left">排名</th>
                <th className="px-4 py-2 text-left">门店</th>
                <th className="px-4 py-2 text-right">热力</th>
                <th className="px-4 py-2 text-right">意向客户</th>
                <th className="px-4 py-2 text-right">到店转化</th>
                <th className="px-4 py-2 text-right">竞品渗透</th>
                <th className="px-4 py-2 text-right">今日获客</th>
                <th className="px-4 py-2 text-right">成交</th>
                <th className="px-4 py-2 text-center">趋势</th>
              </tr>
            </thead>
            <tbody>
              {STORES.sort((a, b) => b.heatIndex - a.heatIndex).map((s, i) => (
                <tr key={s.id} className="border-t border-gray-100 hover:bg-gray-50">
                  <td className="px-4 py-2.5">
                    {i < 3 ? (
                      <span className={`w-6 h-6 rounded-full inline-flex items-center justify-center text-xs font-bold ${
                        i === 0 ? 'bg-amber-100 text-amber-700' : i === 1 ? 'bg-gray-200 text-gray-700' : 'bg-orange-100 text-orange-700'
                      }`}>
                        {i + 1}
                      </span>
                    ) : (
                      <span className="text-gray-400 ml-1.5">{i + 1}</span>
                    )}
                  </td>
                  <td className="px-4 py-2.5">
                    <div className="font-medium text-gray-900">{s.name}</div>
                    <div className="text-xs text-gray-500">{s.city}</div>
                  </td>
                  <td className="px-4 py-2.5 text-right">
                    <span className={`font-semibold ${
                      s.heatIndex >= 85 ? 'text-red-600' : s.heatIndex >= 75 ? 'text-amber-600' : 'text-gray-700'
                    }`}>{s.heatIndex}</span>
                  </td>
                  <td className="px-4 py-2.5 text-right font-medium text-violet-700">{s.intentCustomers}</td>
                  <td className="px-4 py-2.5 text-right text-emerald-700">{s.conversionRate.toFixed(1)}%</td>
                  <td className="px-4 py-2.5 text-right">
                    <span className={s.competitorPenetration >= 25 ? 'text-red-600' : 'text-gray-700'}>
                      {s.competitorPenetration}%
                    </span>
                  </td>
                  <td className="px-4 py-2.5 text-right">{s.todayLeads}</td>
                  <td className="px-4 py-2.5 text-right font-medium">{s.todayConversion}</td>
                  <td className="px-4 py-2.5 text-center">
                    {s.trend === 'up' ? <ArrowUpRight className="w-4 h-4 text-emerald-600 inline" /> :
                     s.trend === 'down' ? <ArrowDownRight className="w-4 h-4 text-red-600 inline" /> :
                     <span className="text-gray-400">—</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* 全国热力图（简化版柱状示意） */}
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex items-center gap-2 mb-3">
          <Globe className="w-4 h-4 text-indigo-600" />
          <h3 className="font-semibold text-gray-900">全国热力分布</h3>
        </div>
        <div className="grid grid-cols-8 gap-2 h-32 items-end">
          {STORES.map((s) => (
            <div key={s.id} className="flex flex-col items-center gap-1">
              <div
                className="w-full rounded-t"
                style={{
                  height: `${s.heatIndex}%`,
                  background: s.heatIndex >= 85
                    ? 'linear-gradient(180deg, #ef4444, #f59e0b)'
                    : s.heatIndex >= 75
                    ? 'linear-gradient(180deg, #f59e0b, #fde68a)'
                    : 'linear-gradient(180deg, #3b82f6, #93c5fd)',
                }}
              />
              <div className="text-[10px] text-gray-500 truncate w-full text-center" title={s.name}>
                {s.city}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function MetricCard({ label, value, unit, icon: Icon, color, trend, trendUp }: any) {
  const colorMap: Record<string, string> = {
    indigo: 'from-indigo-500 to-blue-500',
    blue: 'from-blue-500 to-cyan-500',
    violet: 'from-violet-500 to-fuchsia-500',
    emerald: 'from-emerald-500 to-teal-500',
  };
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-4">
      <div className="flex items-center justify-between mb-2">
        <div className={`w-9 h-9 rounded-lg bg-gradient-to-br ${colorMap[color]} flex items-center justify-center`}>
          <Icon className="w-4 h-4 text-white" />
        </div>
        <span className={`text-xs flex items-center gap-0.5 ${trendUp ? 'text-emerald-600' : 'text-red-600'}`}>
          {trendUp ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
          {trend}
        </span>
      </div>
      <div className="flex items-baseline gap-1">
        <span className="text-2xl font-bold text-gray-900">{value}</span>
        {unit && <span className="text-xs text-gray-500">{unit}</span>}
      </div>
      <div className="text-xs text-gray-500 mt-0.5">{label}</div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  2. 客群画像看板                                                              */
/* -------------------------------------------------------------------------- */

function PortraitPanel() {
  const [dimension, setDimension] = useState<'城市' | '商圈' | '门店'>('城市');

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-gray-200 p-4 flex flex-wrap items-center gap-3">
        <Layers className="w-4 h-4 text-indigo-600" />
        <span className="text-sm text-gray-700">查看维度：</span>
        {(['城市', '商圈', '门店'] as const).map((d) => (
          <button
            key={d}
            onClick={() => setDimension(d)}
            className={`px-3 py-1.5 text-sm rounded-lg border ${
              dimension === d
                ? 'bg-indigo-600 text-white border-indigo-600'
                : 'bg-white text-gray-700 border-gray-200'
            }`}
          >
            {d}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* 年龄分布 */}
        <ChartCard title="年龄分布" subtitle="本品牌换机意向客户">
          <BarGroup
            data={[
              { label: '18-24', value: 18, color: 'bg-pink-400' },
              { label: '25-34', value: 36, color: 'bg-violet-500' },
              { label: '35-44', value: 24, color: 'bg-indigo-500' },
              { label: '45-54', value: 14, color: 'bg-blue-500' },
              { label: '55+', value: 8, color: 'bg-cyan-500' },
            ]}
          />
        </ChartCard>

        {/* 消费力分布 */}
        <ChartCard title="消费力分布" subtitle="客单价 TGI 指数">
          <BarGroup
            data={[
              { label: '< 3000', value: 22, color: 'bg-gray-400' },
              { label: '3000-5000', value: 38, color: 'bg-amber-500' },
              { label: '5000-8000', value: 28, color: 'bg-orange-500' },
              { label: '> 8000', value: 12, color: 'bg-red-500' },
            ]}
          />
        </ChartCard>

        {/* 品牌偏好 */}
        <ChartCard title="品牌偏好 TGI" subtitle="vs 全国基准 100">
          <div className="space-y-2">
            {[
              { label: '本品牌', value: 168, highlight: true },
              { label: 'Apple', value: 132 },
              { label: '华为', value: 105 },
              { label: '小米', value: 98 },
              { label: 'OPPO', value: 87 },
              { label: 'vivo', value: 79 },
            ].map((row) => (
              <div key={row.label} className="flex items-center gap-3 text-sm">
                <span className={`w-16 ${row.highlight ? 'font-bold text-indigo-700' : 'text-gray-700'}`}>
                  {row.label}
                </span>
                <div className="flex-1 h-5 bg-gray-100 rounded">
                  <div
                    className={`h-full rounded ${row.highlight ? 'bg-gradient-to-r from-indigo-500 to-fuchsia-500' : 'bg-gray-400'}`}
                    style={{ width: `${Math.min(100, (row.value / 200) * 100)}%` }}
                  />
                </div>
                <span className={`w-12 text-right ${row.highlight ? 'font-bold text-indigo-700' : 'text-gray-700'}`}>
                  {row.value}
                </span>
              </div>
            ))}
          </div>
        </ChartCard>

        {/* 换机周期分布 */}
        <ChartCard title="换机周期分布" subtitle="客户当前已用机月数">
          <BarGroup
            data={[
              { label: '0-6 月', value: 8, color: 'bg-emerald-400' },
              { label: '7-12 月', value: 16, color: 'bg-emerald-500' },
              { label: '13-18 月', value: 22, color: 'bg-amber-400' },
              { label: '19-24 月', value: 28, color: 'bg-amber-500' },
              { label: '25-30 月', value: 18, color: 'bg-orange-500' },
              { label: '> 30 月', value: 8, color: 'bg-red-500' },
            ]}
          />
        </ChartCard>
      </div>
    </div>
  );
}

function ChartCard({ title, subtitle, children }: { title: string; subtitle?: string; children: any }) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-4">
      <div className="mb-3">
        <h3 className="font-semibold text-gray-900 text-sm">{title}</h3>
        {subtitle && <p className="text-xs text-gray-500 mt-0.5">{subtitle}</p>}
      </div>
      {children}
    </div>
  );
}

function BarGroup({ data }: { data: { label: string; value: number; color: string }[] }) {
  return (
    <div className="space-y-2">
      {data.map((d) => (
        <div key={d.label} className="flex items-center gap-3 text-sm">
          <span className="w-20 text-gray-700">{d.label}</span>
          <div className="flex-1 h-5 bg-gray-100 rounded">
            <div className={`h-full rounded ${d.color}`} style={{ width: `${d.value}%` }} />
          </div>
          <span className="w-10 text-right font-medium text-gray-900">{d.value}%</span>
        </div>
      ))}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  3. 竞品热力地图                                                              */
/* -------------------------------------------------------------------------- */

function CompetitorPanel() {
  const [selectedCity, setSelectedCity] = useState<string | null>(null);
  const total = COMPETITORS.reduce((a, c) => a + c.count, 0);

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex items-center gap-2 mb-3">
          <Map className="w-4 h-4 text-indigo-600" />
          <h3 className="font-semibold text-gray-900">全国竞品门店分布</h3>
          <span className="text-xs text-gray-500 ml-auto">共 {total} 家竞品门店</span>
        </div>

        {/* 城市选择器 */}
        <div className="flex flex-wrap gap-2 mb-4">
          {['全部', '北京', '上海', '深圳', '广州', '成都', '杭州'].map((c) => (
            <button
              key={c}
              onClick={() => setSelectedCity(c === '全部' ? null : c)}
              className={`px-3 py-1 text-sm rounded-full border ${
                (c === '全部' && !selectedCity) || selectedCity === c
                  ? 'bg-indigo-600 text-white border-indigo-600'
                  : 'bg-white text-gray-700 border-gray-200 hover:border-indigo-300'
              }`}
            >
              {c}
            </button>
          ))}
        </div>

        {/* 简化热力图（热力点阵） */}
        <div className="grid grid-cols-12 gap-1 h-48 p-3 bg-gradient-to-br from-indigo-50 to-blue-50 rounded-lg">
          {Array.from({ length: 48 }).map((_, i) => {
            const intensity = Math.sin(i * 0.4) * 0.3 + Math.cos(i * 0.2) * 0.3 + 0.5;
            const cellHeat = Math.max(0, Math.min(1, intensity));
            return (
              <div
                key={i}
                className="rounded"
                style={{
                  background: `rgba(99, 102, 241, ${cellHeat})`,
                }}
              />
            );
          })}
        </div>
        <div className="flex items-center gap-2 mt-2 text-xs text-gray-500">
          <span>低</span>
          <div className="flex-1 h-2 rounded" style={{ background: 'linear-gradient(90deg, rgba(99,102,241,0.1), rgba(99,102,241,1))' }} />
          <span>高</span>
        </div>
      </div>

      {/* 竞品 TOP 5 */}
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h3 className="font-semibold text-gray-900 mb-3">竞品 TOP 5（全国）</h3>
        <div className="space-y-3">
          {COMPETITORS.map((c, i) => (
            <div key={c.name} className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg flex items-center justify-center text-white font-bold text-sm" style={{ background: c.color }}>
                {i + 1}
              </div>
              <div className="flex-1">
                <div className="flex items-center justify-between mb-1">
                  <span className="font-medium text-gray-900">{c.name}</span>
                  <span className="text-sm text-gray-600">{c.count} 家 · {c.share}%</span>
                </div>
                <div className="h-2 bg-gray-100 rounded">
                  <div className="h-full rounded" style={{ width: `${c.share * 2.5}%`, background: c.color }} />
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 flex items-start gap-3">
        <Sparkles className="w-5 h-5 text-amber-600 mt-0.5" />
        <div className="text-sm text-amber-900">
          <div className="font-medium mb-1">AI 攻防建议</div>
          <div className="text-xs text-amber-800">
            北京/上海 Apple 直营 + 华为体验店高度密集，建议在国贸 CBD、徐家汇商圈加密换新补贴投放。
            杭州 OPPO/vivo 渗透高，需加强 D 级（竞品用户）专项触达。
          </div>
        </div>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  4. 数据 API 回传                                                              */
/* -------------------------------------------------------------------------- */

function APIPanel() {
  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex items-center gap-2 mb-3">
          <Radio className="w-4 h-4 text-indigo-600" />
          <h3 className="font-semibold text-gray-900">品牌 CRM 数据回传通道</h3>
          <span className="ml-auto text-xs text-gray-500">实时同步 · 端到端加密</span>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {BRAND_CRM_ENDPOINTS.map((ep) => {
            const Icon = ep.icon;
            const ok = ep.status === 'connected';
            return (
              <div key={ep.name} className={`p-3 rounded-lg border ${
                ok ? 'border-emerald-200 bg-emerald-50/50' : 'border-amber-200 bg-amber-50/50'
              }`}>
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${
                      ok ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'
                    }`}>
                      <Icon className="w-4 h-4" />
                    </div>
                    <div>
                      <div className="font-semibold text-sm text-gray-900">{ep.name}</div>
                      <div className="text-xs text-gray-500">最后同步：{ep.lastSync}</div>
                    </div>
                  </div>
                  {ok ? (
                    <span className="flex items-center gap-1 text-xs text-emerald-700">
                      <CheckCircle2 className="w-3 h-3" /> 已连接
                    </span>
                  ) : (
                    <span className="flex items-center gap-1 text-xs text-amber-700">
                      <AlertCircle className="w-3 h-3" /> 延迟告警
                    </span>
                  )}
                </div>
                <div className="text-xs text-gray-600">
                  API 延迟 <span className="font-mono font-bold">{ep.latency}ms</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* API 文档 */}
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h3 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
          <FileText className="w-4 h-4 text-indigo-600" />
          REST API 端点示例
        </h3>
        <div className="space-y-3">
          {[
            {
              method: 'POST',
              path: '/api/brand/sync/customers',
              desc: '推送客户标签、换机意向、服务记录',
            },
            {
              method: 'POST',
              path: '/api/brand/sync/replacement',
              desc: '推送 AI 换机预测结果（红/黄/绿预警）',
            },
            {
              method: 'GET',
              path: '/api/brand/sync/leads?store_id=ST001',
              desc: '查询门店当日获客数据',
            },
            {
              method: 'POST',
              path: '/api/brand/sync/nfc_events',
              desc: '回传 NFC 碰一碰归因事件',
            },
          ].map((api) => (
            <div key={api.path} className="border border-gray-200 rounded-lg p-3">
              <div className="flex items-center gap-2 mb-1">
                <span className={`px-2 py-0.5 text-xs font-bold rounded ${
                  api.method === 'GET' ? 'bg-emerald-100 text-emerald-700' : 'bg-blue-100 text-blue-700'
                }`}>
                  {api.method}
                </span>
                <code className="text-sm font-mono text-gray-900">{api.path}</code>
              </div>
              <div className="text-xs text-gray-500">{api.desc}</div>
            </div>
          ))}
        </div>
      </div>

      {/* 合规说明 */}
      <div className="bg-indigo-50 border border-indigo-200 rounded-xl p-4 flex items-start gap-3">
        <Shield className="w-5 h-5 text-indigo-600 mt-0.5" />
        <div className="text-sm text-indigo-900">
          <div className="font-medium mb-1">数据隔离 + 审计保障</div>
          <ul className="space-y-0.5 text-xs text-indigo-800">
            <li>• 不同品牌数据物理/逻辑隔离 · 品牌方只能查看自己的数据</li>
            <li>• 手机号 AES-256 加密 · 传输端到端 TLS 1.3</li>
            <li>• 员工离职权限秒级回收 · 异常行为实时告警</li>
            <li>• 品牌方可申请 180 天操作日志审计报告</li>
          </ul>
        </div>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  5. 营销 ROI 看板                                                             */
/* -------------------------------------------------------------------------- */

function ROIPanel() {
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <MetricCard label="总营销投入" value="13.2" unit="万元" icon={TrendingUp} color="blue" trend="+8.4%" trendUp />
        <MetricCard label="总获客线索" value="1761" unit="条" icon={Users} color="violet" trend="+22.1%" trendUp />
        <MetricCard label="总成交" value="223" unit="单" icon={Target} color="emerald" trend="+15.7%" trendUp />
        <MetricCard label="综合 ROI" value="3.26" unit="x" icon={Activity} color="indigo" trend="+0.4x" trendUp />
      </div>

      <div className="bg-white rounded-xl border border-gray-200">
        <div className="p-4 border-b border-gray-200 flex items-center gap-2">
          <Filter className="w-4 h-4 text-indigo-600" />
          <h3 className="font-semibold text-gray-900">活动 ROI 明细</h3>
          <span className="text-xs text-gray-500 ml-auto">每条线索平均成本 ¥75</span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-xs text-gray-500">
              <tr>
                <th className="px-4 py-2 text-left">活动</th>
                <th className="px-4 py-2 text-left">渠道</th>
                <th className="px-4 py-2 text-right">投入</th>
                <th className="px-4 py-2 text-right">线索</th>
                <th className="px-4 py-2 text-right">到店</th>
                <th className="px-4 py-2 text-right">成交</th>
                <th className="px-4 py-2 text-right">CAC</th>
                <th className="px-4 py-2 text-right">CPA</th>
                <th className="px-4 py-2 text-right">ROI</th>
              </tr>
            </thead>
            <tbody>
              {ROI_ROWS.map((r) => (
                <tr key={r.campaign} className="border-t border-gray-100 hover:bg-gray-50">
                  <td className="px-4 py-2.5 font-medium text-gray-900">{r.campaign}</td>
                  <td className="px-4 py-2.5 text-gray-600">{r.channel}</td>
                  <td className="px-4 py-2.5 text-right">¥{(r.spend / 1000).toFixed(1)}k</td>
                  <td className="px-4 py-2.5 text-right">{r.leads}</td>
                  <td className="px-4 py-2.5 text-right">{r.arrived}</td>
                  <td className="px-4 py-2.5 text-right font-medium">{r.closed}</td>
                  <td className="px-4 py-2.5 text-right text-gray-600">¥{r.cac}</td>
                  <td className="px-4 py-2.5 text-right text-gray-600">¥{r.cpa}</td>
                  <td className="px-4 py-2.5 text-right">
                    <span className={`px-2 py-0.5 rounded text-xs font-bold ${
                      r.roi >= 4 ? 'bg-emerald-100 text-emerald-700' :
                      r.roi >= 2.5 ? 'bg-amber-100 text-amber-700' :
                      'bg-red-100 text-red-700'
                    }`}>
                      {r.roi}x
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="bg-gradient-to-r from-indigo-50 to-fuchsia-50 border border-indigo-200 rounded-xl p-4 flex items-start gap-3">
        <Sparkles className="w-5 h-5 text-indigo-600 mt-0.5" />
        <div className="text-sm text-indigo-900">
          <div className="font-medium mb-1">AI 优化建议</div>
          <ul className="space-y-0.5 text-xs text-indigo-800">
            <li>• <b>以旧换新专项（地推 + NFC）</b> ROI 5.8x 最高 · 建议提升 30% 预算</li>
            <li>• <b>家庭融合套餐（运营商联动）</b> ROI 1.8x 偏低 · 建议优化人群定向</li>
            <li>• <b>教师节感恩（企微私域）</b> 成本最优 · 可复制到 9 月开学季</li>
          </ul>
        </div>
      </div>
    </div>
  );
}
