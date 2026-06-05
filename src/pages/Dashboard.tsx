import { Link } from 'react-router-dom';
import {
  TrendingUp,
  Users,
  Target,
  DollarSign,
  Clock,
  AlertCircle,
  ChevronRight,
  Building2,
  Sparkles,
  Eye,
  MousePointerClick,
  ShoppingCart,
  Crown,
  Calendar,
  PhoneCall,
  Zap,
  BarChart3,
  ArrowUpRight,
  ArrowDownRight,
  CheckCircle2,
  X,
  Bell,
  Brain,
} from 'lucide-react';
import { useMemo, useState } from 'react';

/* -------------------------------------------------------------------------- */
/*  首页工作台 V2.0                                                              */
/*  今日待办 / 核心指标卡（线索→到店→成交→客单价）/ GEO 曝光数据                */
/* -------------------------------------------------------------------------- */

type TodoType = '换机预警' | '合约到期' | '应回访';
type TodoPriority = 'high' | 'medium' | 'low';

interface TodoItem {
  id: string;
  type: TodoType;
  customer: string;
  device: string;
  detail: string;
  dueTime: string;
  priority: TodoPriority;
  action: '触达' | '回访' | '续约' | '邀约';
}

const TODOS: TodoItem[] = [
  {
    id: 'T001',
    type: '换机预警',
    customer: '王先生',
    device: '华为 Mate40 Pro',
    detail: '已用 26 个月 · 旗舰机 · 意向分 92',
    dueTime: '10:30',
    priority: 'high',
    action: '触达',
  },
  {
    id: 'T002',
    type: '合约到期',
    customer: '李女士',
    device: 'vivo X90 Pro',
    detail: '合约剩余 67 天 · 续约黄金期',
    dueTime: '14:00',
    priority: 'high',
    action: '续约',
  },
  {
    id: 'T003',
    type: '应回访',
    customer: '张先生',
    device: 'iPhone 14 Pro',
    detail: '28 天未联系 · 上次咨询 Mate70',
    dueTime: '15:30',
    priority: 'medium',
    action: '回访',
  },
  {
    id: 'T004',
    type: '换机预警',
    customer: '陈女士',
    device: 'iPhone 14 Pro',
    detail: '已用 29 个月 · 红色预警',
    dueTime: '16:00',
    priority: 'high',
    action: '触达',
  },
  {
    id: 'T005',
    type: '合约到期',
    customer: '周女士',
    device: '荣耀 Magic5',
    detail: '合约剩余 81 天 · 家庭融合推荐',
    dueTime: '17:30',
    priority: 'medium',
    action: '续约',
  },
  {
    id: 'T006',
    type: '应回访',
    customer: '刘先生',
    device: '小米 13 Ultra',
    detail: '上次贴膜咨询 · 14 天未跟进',
    dueTime: '今日',
    priority: 'low',
    action: '回访',
  },
];

interface GEOPlatformMetric {
  platform: string;
  exposure: number;
  clicks: number;
  conversions: number;
  ctr: number;
  trend: 'up' | 'down' | 'flat';
  trendValue: number;
}

const GEO_METRICS: GEOPlatformMetric[] = [
  { platform: '豆包', exposure: 4820, clicks: 312, conversions: 28, ctr: 6.5, trend: 'up', trendValue: 12.4 },
  { platform: '腾讯元宝', exposure: 3940, clicks: 268, conversions: 22, ctr: 6.8, trend: 'up', trendValue: 8.6 },
  { platform: 'Kimi', exposure: 2710, clicks: 174, conversions: 14, ctr: 6.4, trend: 'up', trendValue: 15.2 },
  { platform: 'DeepSeek', exposure: 2150, clicks: 138, conversions: 12, ctr: 6.4, trend: 'flat', trendValue: 0.3 },
  { platform: '文心一言', exposure: 1640, clicks: 92, conversions: 6, ctr: 5.6, trend: 'down', trendValue: -3.1 },
  { platform: 'ChatGPT', exposure: 1180, clicks: 78, conversions: 8, ctr: 6.6, trend: 'up', trendValue: 5.4 },
];

const FUNNEL_DATA = [
  { stage: '线索', value: 128, color: 'from-blue-500 to-cyan-500', icon: Users },
  { stage: '到店', value: 47, ratio: 36.7, color: 'from-violet-500 to-purple-500', icon: ShoppingCart },
  { stage: '成交', value: 18, ratio: 14.1, color: 'from-emerald-500 to-teal-500', icon: CheckCircle2 },
  { stage: '客单价', value: '¥5,820', color: 'from-amber-500 to-orange-500', icon: DollarSign },
];

export default function Dashboard() {
  const [todoFilter, setTodoFilter] = useState<TodoType | '全部'>('全部');
  const [dismissedTodos, setDismissedTodos] = useState<Set<string>>(new Set());

  const filteredTodos = useMemo(() => {
    return TODOS.filter((t) => {
      if (dismissedTodos.has(t.id)) return false;
      if (todoFilter !== '全部' && t.type !== todoFilter) return false;
      return true;
    });
  }, [todoFilter, dismissedTodos]);

  const todoCount = useMemo(() => ({
    total: TODOS.length - dismissedTodos.size,
    换机预警: TODOS.filter((t) => t.type === '换机预警' && !dismissedTodos.has(t.id)).length,
    合约到期: TODOS.filter((t) => t.type === '合约到期' && !dismissedTodos.has(t.id)).length,
    应回访: TODOS.filter((t) => t.type === '应回访' && !dismissedTodos.has(t.id)).length,
  }), [dismissedTodos]);

  const geoTotal = useMemo(() => {
    return GEO_METRICS.reduce(
      (acc, p) => ({
        exposure: acc.exposure + p.exposure,
        clicks: acc.clicks + p.clicks,
        conversions: acc.conversions + p.conversions,
      }),
      { exposure: 0, clicks: 0, conversions: 0 }
    );
  }, []);

  const dismissTodo = (id: string) => {
    setDismissedTodos((prev) => new Set(prev).add(id));
  };

  return (
    <div className="space-y-6">
      {/* 顶部欢迎条 */}
      <div className="bg-gradient-to-r from-slate-900 via-indigo-900 to-slate-900 rounded-2xl p-5 text-white">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <Sparkles className="w-5 h-5 text-amber-400" />
              <h1 className="text-xl font-bold">早安，李店长</h1>
              <span className="px-2 py-0.5 text-xs bg-emerald-400/20 text-emerald-300 rounded border border-emerald-400/30">
                国贸旗舰店 · 营业中
              </span>
            </div>
            <p className="text-sm text-slate-300">
              今日有 <b className="text-amber-300">{todoCount.total}</b> 项待办 · GEO 曝光 <b className="text-cyan-300">{geoTotal.exposure.toLocaleString()}</b> · 当前 LBS 扫描半径 3km
            </p>
          </div>
          <div className="flex items-center gap-2">
            <div className="text-right">
              <div className="text-xs text-slate-400">2026-06-05</div>
              <div className="text-xs text-slate-400">周四 · 晴 24°C</div>
            </div>
            <button className="relative p-2 bg-white/10 backdrop-blur rounded-lg hover:bg-white/20">
              <Bell className="w-4 h-4" />
              <span className="absolute -top-1 -right-1 w-4 h-4 bg-red-500 text-white text-[10px] rounded-full flex items-center justify-center">
                {todoCount.total}
              </span>
            </button>
          </div>
        </div>
      </div>

      {/* 核心指标卡：线索→到店→成交→客单价 */}
      <section>
        <div className="flex items-center gap-2 mb-3">
          <BarChart3 className="w-4 h-4 text-indigo-600" />
          <h2 className="font-semibold text-gray-900">核心指标卡 · 今日</h2>
          <span className="text-xs text-gray-500 ml-2">实时数据 · 30 秒前</span>
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {FUNNEL_DATA.map((f, i) => {
            const Icon = f.icon;
            return (
              <div key={f.stage} className="bg-white rounded-xl border border-gray-200 p-4 relative overflow-hidden">
                <div className={`absolute top-0 left-0 right-0 h-1 bg-gradient-to-r ${f.color}`} />
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs text-gray-500">{f.stage}</span>
                  <div className={`w-7 h-7 rounded-lg bg-gradient-to-br ${f.color} flex items-center justify-center`}>
                    <Icon className="w-3.5 h-3.5 text-white" />
                  </div>
                </div>
                <div className="flex items-baseline gap-1">
                  <span className="text-2xl font-bold text-gray-900">
                    {typeof f.value === 'number' ? f.value.toLocaleString() : f.value}
                  </span>
                </div>
                <div className="text-xs text-emerald-600 flex items-center gap-0.5 mt-1">
                  <ArrowUpRight className="w-3 h-3" />
                  {i === 0 ? '+18.4%' : i === 1 ? '+12.6%' : i === 2 ? '+24.8%' : '+8.2%'} 较昨日
                </div>
                {f.ratio && (
                  <div className="text-[10px] text-gray-400 mt-1">转化率 {f.ratio}%</div>
                )}
              </div>
            );
          })}
        </div>

        {/* 漏斗图 */}
        <div className="mt-3 bg-white rounded-xl border border-gray-200 p-4">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-semibold text-gray-900">获客漏斗</h3>
            <span className="text-xs text-gray-500">从线索 → 成交 · 端到端转化 14.1%</span>
          </div>
          <div className="flex items-center gap-1">
            {[
              { label: '线索', value: 128, width: 100, color: 'bg-blue-500' },
              { label: '到店', value: 47, width: 60, color: 'bg-violet-500' },
              { label: '成交', value: 18, width: 28, color: 'bg-emerald-500' },
            ].map((step, idx) => (
              <div key={step.label} className="flex-1 flex flex-col items-center">
                <div className="w-full flex justify-center">
                  <div
                    className={`${step.color} h-8 rounded-md flex items-center justify-center text-white text-xs font-bold`}
                    style={{ width: `${step.width}%` }}
                  >
                    {step.value}
                  </div>
                </div>
                <span className="text-xs text-gray-500 mt-1">{step.label}</span>
                {idx < 2 && (
                  <ArrowUpRight className="w-3 h-3 text-emerald-500 mt-1" />
                )}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* 今日待办 + GEO 曝光数据（两栏） */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 今日待办 */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200">
          <div className="p-4 border-b border-gray-200">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <Clock className="w-4 h-4 text-orange-600" />
                <h2 className="font-semibold text-gray-900">今日待办</h2>
                <span className="text-xs text-gray-500">({todoCount.total} 项)</span>
              </div>
              <Link to="/customers" className="text-xs text-blue-600 hover:text-blue-700 flex items-center gap-0.5">
                查看全部 <ChevronRight className="w-3 h-3" />
              </Link>
            </div>
            {/* 类型 Tab */}
            <div className="flex gap-1">
              {(['全部', '换机预警', '合约到期', '应回访'] as const).map((t) => {
                const count = t === '全部' ? todoCount.total : todoCount[t as TodoType];
                const active = todoFilter === t;
                return (
                  <button
                    key={t}
                    onClick={() => setTodoFilter(t as any)}
                    className={`px-2.5 py-1 text-xs rounded-lg flex items-center gap-1 ${
                      active ? 'bg-orange-100 text-orange-700' : 'text-gray-600 hover:bg-gray-50'
                    }`}
                  >
                    {t}
                    <span className={`px-1 text-[10px] rounded ${active ? 'bg-white/60' : 'bg-gray-100'}`}>
                      {count}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
          <div className="divide-y divide-gray-100 max-h-[420px] overflow-y-auto">
            {filteredTodos.length === 0 && (
              <div className="p-8 text-center text-gray-400">
                <CheckCircle2 className="w-10 h-10 mx-auto mb-2 opacity-50" />
                <p className="text-sm">当前类型待办已全部处理完毕</p>
              </div>
            )}
            {filteredTodos.map((t) => {
              const typeBadge =
                t.type === '换机预警'
                  ? 'bg-red-100 text-red-700 border-red-200'
                  : t.type === '合约到期'
                  ? 'bg-amber-100 text-amber-700 border-amber-200'
                  : 'bg-blue-100 text-blue-700 border-blue-200';
              const typeIcon =
                t.type === '换机预警' ? <Crown className="w-3 h-3" /> :
                t.type === '合约到期' ? <Calendar className="w-3 h-3" /> :
                <PhoneCall className="w-3 h-3" />;
              return (
                <div key={t.id} className="p-3 hover:bg-gray-50 flex items-center gap-3 group">
                  <div className={`w-1.5 h-10 rounded-full ${
                    t.priority === 'high' ? 'bg-red-500' : t.priority === 'medium' ? 'bg-amber-400' : 'bg-gray-300'
                  }`} />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-0.5 flex-wrap">
                      <span className="font-medium text-gray-900 text-sm">{t.customer}</span>
                      <span className={`px-1.5 py-0.5 text-[10px] rounded border flex items-center gap-0.5 ${typeBadge}`}>
                        {typeIcon}{t.type}
                      </span>
                      <span className="text-xs text-gray-500">· {t.device}</span>
                    </div>
                    <div className="text-xs text-gray-500 truncate">{t.detail}</div>
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    <span className="text-xs text-gray-500">{t.dueTime}</span>
                    <div className="flex gap-1">
                      <button className={`px-2 py-0.5 text-[10px] rounded ${
                        t.action === '续约'
                          ? 'bg-amber-100 text-amber-700'
                          : t.action === '回访'
                          ? 'bg-blue-100 text-blue-700'
                          : 'bg-violet-100 text-violet-700'
                      }`}>
                        {t.action}
                      </button>
                      <button
                        onClick={() => dismissTodo(t.id)}
                        className="p-0.5 text-gray-400 hover:text-gray-600 opacity-0 group-hover:opacity-100"
                        title="忽略"
                      >
                        <X className="w-3 h-3" />
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* GEO 曝光数据 */}
        <div className="bg-white rounded-xl border border-gray-200">
          <div className="p-4 border-b border-gray-200">
            <div className="flex items-center gap-2 mb-1">
              <Brain className="w-4 h-4 text-indigo-600" />
              <h2 className="font-semibold text-gray-900">GEO 曝光数据</h2>
              <span className="ml-auto text-[10px] px-1.5 py-0.5 bg-indigo-100 text-indigo-700 rounded">2026 新增</span>
            </div>
            <p className="text-xs text-gray-500">AI 搜索平台曝光 · 点击 · 转化</p>
          </div>

          {/* 汇总 3 指标 */}
          <div className="p-4 grid grid-cols-3 gap-2">
            <div className="text-center">
              <div className="text-lg font-bold text-gray-900">{geoTotal.exposure.toLocaleString()}</div>
              <div className="text-[10px] text-gray-500 flex items-center justify-center gap-0.5">
                <Eye className="w-3 h-3" />曝光
              </div>
            </div>
            <div className="text-center border-x border-gray-100">
              <div className="text-lg font-bold text-gray-900">{geoTotal.clicks}</div>
              <div className="text-[10px] text-gray-500 flex items-center justify-center gap-0.5">
                <MousePointerClick className="w-3 h-3" />点击
              </div>
            </div>
            <div className="text-center">
              <div className="text-lg font-bold text-emerald-600">{geoTotal.conversions}</div>
              <div className="text-[10px] text-gray-500 flex items-center justify-center gap-0.5">
                <Target className="w-3 h-3" />转化
              </div>
            </div>
          </div>

          {/* 平台列表 */}
          <div className="px-4 pb-4 space-y-2">
            {GEO_METRICS.map((p) => {
              const maxExp = Math.max(...GEO_METRICS.map((g) => g.exposure));
              const width = (p.exposure / maxExp) * 100;
              return (
                <div key={p.platform}>
                  <div className="flex items-center justify-between text-xs mb-0.5">
                    <span className="text-gray-700 font-medium">{p.platform}</span>
                    <div className="flex items-center gap-1.5">
                      <span className="text-gray-900">{p.exposure.toLocaleString()}</span>
                      {p.trend === 'up' ? (
                        <ArrowUpRight className="w-3 h-3 text-emerald-500" />
                      ) : p.trend === 'down' ? (
                        <ArrowDownRight className="w-3 h-3 text-red-500" />
                      ) : null}
                    </div>
                  </div>
                  <div className="h-1.5 bg-gray-100 rounded">
                    <div
                      className="h-full rounded bg-gradient-to-r from-indigo-400 to-fuchsia-400"
                      style={{ width: `${width}%` }}
                    />
                  </div>
                  <div className="flex items-center justify-between text-[10px] text-gray-500 mt-0.5">
                    <span>点击 {p.clicks} · 转化 {p.conversions}</span>
                    <span>CTR {p.ctr}%</span>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="px-4 py-2 bg-indigo-50 border-t border-indigo-100 text-[10px] text-indigo-700 flex items-center gap-1">
            <Zap className="w-3 h-3" />
            7 日汇总 · 6 大 AI 平台
            <Link to="/geo-optimization" className="ml-auto text-indigo-600 hover:underline">
              详情 →
            </Link>
          </div>
        </div>
      </div>

      {/* AI 智能建议 + 快捷入口 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <h3 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-violet-600" />
            AI 智能建议
          </h3>
          <div className="space-y-2">
            <Suggestion
              color="blue"
              icon={TrendingUp}
              title="高转化机会"
              desc="3 位 S 级客户近期换机概率高，建议优先触达"
            />
            <Suggestion
              color="orange"
              icon={AlertCircle}
              title="客户关怀"
              desc="5 位客户超过 30 天未联系，建议回访"
            />
            <Suggestion
              color="emerald"
              icon={Building2}
              title="门店动态"
              desc="国贸店周环比上升 12%，表现优秀"
            />
          </div>
        </div>

        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <h3 className="font-semibold text-gray-900 mb-3">快捷入口</h3>
          <div className="grid grid-cols-2 gap-2">
            {[
              { name: 'LBS 雷达', href: '/lbs', icon: Target, color: 'bg-blue-50 text-blue-700' },
              { name: 'GEO 优化', href: '/geo-optimization', icon: Brain, color: 'bg-violet-50 text-violet-700' },
              { name: '客户分层', href: '/customers', icon: Users, color: 'bg-emerald-50 text-emerald-700' },
              { name: 'AI 作战', href: '/ground-combat', icon: Zap, color: 'bg-pink-50 text-pink-700' },
              { name: '数据中台', href: '/brand-data', icon: BarChart3, color: 'bg-cyan-50 text-cyan-700' },
              { name: '营销作战', href: '/marketing', icon: Sparkles, color: 'bg-amber-50 text-amber-700' },
            ].map((q) => {
              const Icon = q.icon;
              return (
                <Link
                  key={q.href}
                  to={q.href}
                  className={`p-2.5 rounded-lg ${q.color} hover:opacity-80 flex items-center gap-2 text-sm font-medium`}
                >
                  <Icon className="w-4 h-4" />
                  {q.name}
                </Link>
              );
            })}
          </div>
        </div>

        <div className="bg-gradient-to-br from-amber-50 to-orange-50 border border-amber-200 rounded-xl p-4">
          <div className="flex items-center gap-2 mb-2">
            <Crown className="w-4 h-4 text-amber-600" />
            <h3 className="font-semibold text-amber-900">本周门店排行</h3>
          </div>
          <div className="space-y-2">
            {[
              { rank: 1, name: '国贸旗舰店', score: 92, change: '+12%' },
              { rank: 2, name: '南京西路店', score: 88, change: '+8%' },
              { rank: 3, name: '福田 COCO', score: 85, change: '+15%' },
            ].map((s) => (
              <div key={s.rank} className="flex items-center gap-2 text-sm">
                <span className={`w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold ${
                  s.rank === 1 ? 'bg-amber-200 text-amber-800' :
                  s.rank === 2 ? 'bg-gray-200 text-gray-700' :
                  'bg-orange-200 text-orange-800'
                }`}>
                  {s.rank}
                </span>
                <span className="text-amber-900 font-medium flex-1">{s.name}</span>
                <span className="text-amber-700 font-mono">{s.score}</span>
                <span className="text-emerald-700 text-xs">{s.change}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function Suggestion({ color, icon: Icon, title, desc }: { color: string; icon: any; title: string; desc: string }) {
  const colorMap: Record<string, string> = {
    blue: 'bg-blue-50 text-blue-700',
    orange: 'bg-orange-50 text-orange-700',
    emerald: 'bg-emerald-50 text-emerald-700',
  };
  return (
    <div className={`p-2.5 rounded-lg ${colorMap[color]}`}>
      <div className="flex items-center gap-1.5 mb-0.5">
        <Icon className="w-3.5 h-3.5" />
        <span className="text-sm font-medium">{title}</span>
      </div>
      <p className="text-xs opacity-80">{desc}</p>
    </div>
  );
}
