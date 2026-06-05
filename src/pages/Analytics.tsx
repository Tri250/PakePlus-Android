import { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  BarChart3,
  TrendingUp,
  TrendingDown,
  Users,
  Target,
  DollarSign,
  Calendar,
  Download,
  Filter,
  RefreshCw,
  PieChart,
  Activity,
  Zap,
  MapPin,
  Building2,
  Clock,
  ArrowUpRight,
  ArrowDownRight,
  Layers,
  Eye,
  MousePointerClick,
  ShoppingCart,
  CheckCircle2,
} from 'lucide-react';
import { SkeletonCard, SkeletonTable } from '../components/Skeleton';

/* -------------------------------------------------------------------------- */
/*  数据分析 V2.0 - 多维度洞察 + 趋势图 + 导出                                   */
/* -------------------------------------------------------------------------- */

type TimeRange = 'today' | '7d' | '30d' | '90d';
type ViewType = 'overview' | 'funnel' | 'trend' | 'geo';

interface MetricCard {
  label: string;
  value: string | number;
  change: number;
  changeLabel: string;
  icon: any;
  color: string;
}

const METRICS: MetricCard[] = [
  { label: '总线索', value: 1284, change: 18.4, changeLabel: '较上周', icon: Users, color: 'from-blue-500 to-cyan-500' },
  { label: '到店客户', value: 472, change: 12.6, changeLabel: '较上周', icon: ShoppingCart, color: 'from-violet-500 to-purple-500' },
  { label: '成交订单', value: 186, change: 24.8, changeLabel: '较上周', icon: CheckCircle2, color: 'from-emerald-500 to-teal-500' },
  { label: '总营收', value: '¥108.4万', change: 15.2, changeLabel: '较上周', icon: DollarSign, color: 'from-amber-500 to-orange-500' },
];

const FUNNEL_STEPS = [
  { stage: '曝光', value: 48620, color: 'bg-blue-500' },
  { stage: '点击', value: 3842, color: 'bg-indigo-500' },
  { stage: '线索', value: 1284, color: 'bg-violet-500' },
  { stage: '到店', value: 472, color: 'bg-purple-500' },
  { stage: '成交', value: 186, color: 'bg-emerald-500' },
];

const TREND_DATA = [
  { date: '05-30', leads: 42, arrived: 15, closed: 6 },
  { date: '05-31', leads: 38, arrived: 12, closed: 4 },
  { date: '06-01', leads: 56, arrived: 21, closed: 8 },
  { date: '06-02', leads: 48, arrived: 18, closed: 7 },
  { date: '06-03', leads: 62, arrived: 24, closed: 9 },
  { date: '06-04', leads: 71, arrived: 28, closed: 11 },
  { date: '06-05', leads: 84, arrived: 32, closed: 13 },
];

const GEO_DATA = [
  { city: '北京', leads: 312, arrived: 118, closed: 48, share: 24.3 },
  { city: '上海', leads: 286, arrived: 102, closed: 42, share: 22.3 },
  { city: '深圳', leads: 248, arrived: 94, closed: 38, share: 19.3 },
  { city: '广州', leads: 198, arrived: 72, closed: 28, share: 15.4 },
  { city: '成都', leads: 142, arrived: 48, closed: 18, share: 11.1 },
  { city: '其他', leads: 98, arrived: 38, closed: 12, share: 7.6 },
];

export default function Analytics() {
  const [timeRange, setTimeRange] = useState<TimeRange>('7d');
  const [viewType, setViewType] = useState<ViewType>('overview');
  const [loading, setLoading] = useState(false);

  const handleRefresh = () => {
    setLoading(true);
    setTimeout(() => setLoading(false), 1200);
  };

  const maxLeads = Math.max(...TREND_DATA.map(d => d.leads));

  return (
    <div className="space-y-6">
      {/* 顶部 */}
      <div className="bg-gradient-to-r from-indigo-600 via-purple-600 to-pink-600 rounded-2xl p-5 text-white">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <BarChart3 className="w-5 h-5" />
              <h1 className="text-xl font-bold">数据分析 V2.0</h1>
              <span className="px-2 py-0.5 text-xs bg-white/20 rounded border border-white/30">多维度洞察</span>
            </div>
            <p className="text-sm text-white/90">
              线索 → 到店 → 成交全链路分析 · 趋势图 · 地域分布 · 导出报表
            </p>
          </div>
          <div className="flex items-center gap-2">
            <select
              value={timeRange}
              onChange={(e) => setTimeRange(e.target.value as TimeRange)}
              className="px-3 py-1.5 bg-white/10 backdrop-blur border border-white/20 rounded-lg text-sm text-white"
            >
              <option value="today">今日</option>
              <option value="7d">近 7 天</option>
              <option value="30d">近 30 天</option>
              <option value="90d">近 90 天</option>
            </select>
            <button
              onClick={handleRefresh}
              className="p-2 bg-white/10 backdrop-blur border border-white/20 rounded-lg hover:bg-white/20"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            </button>
            <button className="px-3 py-1.5 bg-white text-indigo-700 rounded-lg text-sm font-medium flex items-center gap-1">
              <Download className="w-4 h-4" /> 导出
            </button>
          </div>
        </div>
      </div>

      {/* Tab 导航 */}
      <div className="bg-white rounded-xl border border-gray-200 p-1 flex gap-1">
        {[
          { key: 'overview', label: '总览', icon: PieChart },
          { key: 'funnel', label: '转化漏斗', icon: Layers },
          { key: 'trend', label: '趋势图', icon: TrendingUp },
          { key: 'geo', label: '地域分布', icon: MapPin },
        ].map((t) => {
          const Icon = t.icon;
          const active = viewType === t.key;
          return (
            <button
              key={t.key}
              onClick={() => setViewType(t.key as ViewType)}
              className={`flex-1 flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-medium rounded-lg transition-colors ${
                active ? 'bg-indigo-600 text-white shadow-sm' : 'text-gray-600 hover:bg-gray-50'
              }`}
            >
              <Icon className="w-4 h-4" />
              {t.label}
            </button>
          );
        })}
      </div>

      <AnimatePresence mode="wait">
        {viewType === 'overview' && (
          <motion.div
            key="overview"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className="space-y-4"
          >
            {/* 4 大指标卡 */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              {METRICS.map((m, i) => {
                const Icon = m.icon;
                return (
                  <motion.div
                    key={m.label}
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: i * 0.1 }}
                    className="bg-white rounded-xl border border-gray-200 p-4 relative overflow-hidden"
                  >
                    <div className={`absolute top-0 left-0 right-0 h-1 bg-gradient-to-r ${m.color}`} />
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs text-gray-500">{m.label}</span>
                      <div className={`w-8 h-8 rounded-lg bg-gradient-to-br ${m.color} flex items-center justify-center`}>
                        <Icon className="w-4 h-4 text-white" />
                      </div>
                    </div>
                    <div className="text-2xl font-bold text-gray-900">
                      {typeof m.value === 'number' ? m.value.toLocaleString() : m.value}
                    </div>
                    <div className={`text-xs mt-1 flex items-center gap-0.5 ${m.change >= 0 ? 'text-emerald-600' : 'text-red-600'}`}>
                      {m.change >= 0 ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
                      {Math.abs(m.change)}% {m.changeLabel}
                    </div>
                  </motion.div>
                );
              })}
            </div>

            {/* 快速洞察 */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <InsightCard
                title="转化率洞察"
                items={[
                  { label: '点击→线索', value: '33.4%', trend: 'up' },
                  { label: '线索→到店', value: '36.8%', trend: 'up' },
                  { label: '到店→成交', value: '39.4%', trend: 'up' },
                ]}
              />
              <InsightCard
                title="渠道贡献 TOP3"
                items={[
                  { label: 'LBS 雷达', value: '38%', trend: 'up' },
                  { label: 'GEO 优化', value: '26%', trend: 'up' },
                  { label: '地推 NFC', value: '22%', trend: 'flat' },
                ]}
              />
              <InsightCard
                title="时段分布"
                items={[
                  { label: '上午 9-12', value: '32%', trend: 'up' },
                  { label: '下午 14-18', value: '45%', trend: 'up' },
                  { label: '晚间 19-22', value: '23%', trend: 'down' },
                ]}
              />
            </div>
          </motion.div>
        )}

        {viewType === 'funnel' && (
          <motion.div
            key="funnel"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className="bg-white rounded-xl border border-gray-200 p-6"
          >
            <h3 className="font-semibold text-gray-900 mb-6">转化漏斗</h3>
            <div className="space-y-4">
              {FUNNEL_STEPS.map((step, i) => {
                const prevValue = i > 0 ? FUNNEL_STEPS[i - 1].value : step.value;
                const rate = i > 0 ? ((step.value / prevValue) * 100).toFixed(1) : '100';
                const width = (step.value / FUNNEL_STEPS[0].value) * 100;
                return (
                  <motion.div
                    key={step.stage}
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: i * 0.1 }}
                    className="flex items-center gap-4"
                  >
                    <div className="w-20 text-sm text-gray-600">{step.stage}</div>
                    <div className="flex-1 h-10 bg-gray-100 rounded-lg relative overflow-hidden">
                      <motion.div
                        className={`h-full ${step.color} rounded-lg flex items-center justify-end pr-3`}
                        initial={{ width: 0 }}
                        animate={{ width: `${width}%` }}
                        transition={{ duration: 0.6, delay: i * 0.1 }}
                      >
                        <span className="text-white text-sm font-bold">{step.value.toLocaleString()}</span>
                      </motion.div>
                    </div>
                    <div className="w-20 text-right text-sm text-gray-500">
                      {i > 0 && <span className="text-emerald-600">{rate}%</span>}
                    </div>
                  </motion.div>
                );
              })}
            </div>
            <div className="mt-6 p-4 bg-indigo-50 rounded-lg text-sm text-indigo-800">
              💡 综合转化率 <b>0.38%</b>（曝光→成交）· 行业平均 0.25% · 表现优秀
            </div>
          </motion.div>
        )}

        {viewType === 'trend' && (
          <motion.div
            key="trend"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className="bg-white rounded-xl border border-gray-200 p-6"
          >
            <div className="flex items-center justify-between mb-6">
              <h3 className="font-semibold text-gray-900">近 7 天趋势</h3>
              <div className="flex items-center gap-4 text-xs">
                <span className="flex items-center gap-1"><span className="w-3 h-3 rounded bg-blue-500" />线索</span>
                <span className="flex items-center gap-1"><span className="w-3 h-3 rounded bg-violet-500" />到店</span>
                <span className="flex items-center gap-1"><span className="w-3 h-3 rounded bg-emerald-500" />成交</span>
              </div>
            </div>
            <div className="space-y-2">
              {TREND_DATA.map((d, i) => (
                <motion.div
                  key={d.date}
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: i * 0.05 }}
                  className="flex items-center gap-4"
                >
                  <div className="w-14 text-xs text-gray-500">{d.date}</div>
                  <div className="flex-1 flex gap-1">
                    <Bar value={d.leads} max={maxLeads} color="bg-blue-500" label={d.leads} />
                    <Bar value={d.arrived} max={maxLeads} color="bg-violet-500" label={d.arrived} />
                    <Bar value={d.closed} max={maxLeads} color="bg-emerald-500" label={d.closed} />
                  </div>
                </motion.div>
              ))}
            </div>
          </motion.div>
        )}

        {viewType === 'geo' && (
          <motion.div
            key="geo"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className="bg-white rounded-xl border border-gray-200 overflow-hidden"
          >
            <div className="p-4 border-b border-gray-200">
              <h3 className="font-semibold text-gray-900">地域分布</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 text-xs text-gray-500">
                  <tr>
                    <th className="px-4 py-2 text-left">城市</th>
                    <th className="px-4 py-2 text-right">线索</th>
                    <th className="px-4 py-2 text-right">到店</th>
                    <th className="px-4 py-2 text-right">成交</th>
                    <th className="px-4 py-2 text-right">占比</th>
                  </tr>
                </thead>
                <tbody>
                  {GEO_DATA.map((g, i) => (
                    <motion.tr
                      key={g.city}
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: i * 0.05 }}
                      className="border-t border-gray-100 hover:bg-gray-50"
                    >
                      <td className="px-4 py-2.5 font-medium text-gray-900">{g.city}</td>
                      <td className="px-4 py-2.5 text-right">{g.leads}</td>
                      <td className="px-4 py-2.5 text-right">{g.arrived}</td>
                      <td className="px-4 py-2.5 text-right font-medium">{g.closed}</td>
                      <td className="px-4 py-2.5 text-right">
                        <span className="text-indigo-600 font-medium">{g.share}%</span>
                      </td>
                    </motion.tr>
                  ))}
                </tbody>
              </table>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function InsightCard({ title, items }: { title: string; items: { label: string; value: string; trend: string }[] }) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-4">
      <h4 className="text-sm font-semibold text-gray-900 mb-3">{title}</h4>
      <div className="space-y-2">
        {items.map((item) => (
          <div key={item.label} className="flex items-center justify-between text-sm">
            <span className="text-gray-600">{item.label}</span>
            <div className="flex items-center gap-1">
              <span className="font-medium text-gray-900">{item.value}</span>
              {item.trend === 'up' && <ArrowUpRight className="w-3 h-3 text-emerald-500" />}
              {item.trend === 'down' && <ArrowDownRight className="w-3 h-3 text-red-500" />}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function Bar({ value, max, color, label }: { value: number; max: number; color: string; label: number }) {
  const width = (value / max) * 100;
  return (
    <div className="relative h-6 bg-gray-100 rounded flex-1">
      <motion.div
        className={`h-full ${color} rounded flex items-center justify-end pr-1`}
        initial={{ width: 0 }}
        animate={{ width: `${width}%` }}
        transition={{ duration: 0.4 }}
      >
        {width > 15 && <span className="text-white text-[10px] font-bold">{label}</span>}
      </motion.div>
    </div>
  );
}
