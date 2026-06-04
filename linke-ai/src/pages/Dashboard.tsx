import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import {
  LineChart as LineIcon,
  TrendingUp,
  Users2,
  MapPin,
  Award,
  ArrowUpRight,
  Activity,
  Layers,
  Target,
  Banknote,
} from 'lucide-react';
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  Radar,
} from 'recharts';
import StatCard from '@/components/StatCard';
import SectionHeader from '@/components/SectionHeader';
import ProgressRing from '@/components/ProgressRing';
import { useGlobal } from '@/store/useGlobal';
import { api } from '@/lib/api';
import type { Overview } from '@/lib/types';
import { cn } from '@/lib/utils';

export default function DashboardPage() {
  const { currentStoreId, radius } = useGlobal();
  const [overview, setOverview] = useState<Overview | null>(null);
  const [range, setRange] = useState<'7d' | '30d'>('7d');

  useEffect(() => {
    if (!currentStoreId) return;
    api
      .get<{ overview: Overview }>(`/dashboard/overview?storeId=${currentStoreId}&range=${range}`)
      .then((r) => setOverview(r.overview));
  }, [currentStoreId, range]);

  const radarData = [
    { dim: '曝光', value: 88 },
    { dim: '加微', value: 72 },
    { dim: '到店', value: 56 },
    { dim: '成交', value: 38 },
    { dim: '复购', value: 64 },
    { dim: '推荐', value: 48 },
  ];

  return (
    <div className="p-8 max-w-[1400px] mx-auto">
      <header className="flex items-end justify-between flex-wrap gap-4 mb-6">
        <div>
          <motion.div
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            className="flex items-center gap-2 text-[11px] font-mono uppercase tracking-widest text-cyber-200"
          >
            <LineIcon className="w-3.5 h-3.5" />
            Dashboard · 数据看板
            <span className="text-ink-500">·</span>
            <span className="text-ink-400">REAL-TIME</span>
          </motion.div>
          <h1 className="mt-2 text-3xl font-display font-extrabold">3 / 5 / 8 / 10 km · ROI 复盘</h1>
          <p className="mt-1 text-sm text-ink-400">从曝光 → 加微 → 到店 → 成交的完整漏斗</p>
        </div>
        <div className="flex items-center gap-1.5 panel p-1">
          {(['7d', '30d'] as const).map((r) => (
            <button
              key={r}
              onClick={() => setRange(r)}
              className={cn(
                'px-4 py-1.5 text-xs font-mono rounded-lg transition',
                range === r
                  ? 'bg-gradient-to-r from-ember-500 to-ember-600 text-ink-950 shadow-glow'
                  : 'text-ink-300 hover:text-white',
              )}
            >
              {r === '7d' ? '近 7 天' : '近 30 天'}
            </button>
          ))}
        </div>
      </header>

      {/* 顶部数据卡 — StatCard 组件 */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-6">
        <StatCard
          label="总曝光"
          value={overview?.reach ?? 0}
          format="wan"
          icon={TrendingUp}
          tone="ember"
          delta={12.4}
          spark={overview?.trend?.map((t) => t.reach) || [120, 180, 240, 320, 280, 360, 410]}
          delay={0}
        />
        <StatCard
          label="加微数"
          value={overview?.addedWechat ?? 0}
          format="comma"
          icon={Users2}
          tone="cyber"
          delta={8.7}
          spark={overview?.trend?.map((t) => t.added) || [8, 12, 16, 22, 18, 26, 32]}
          delay={0.06}
        />
        <StatCard
          label="到店数"
          value={overview?.visited ?? 0}
          format="comma"
          icon={MapPin}
          tone="violet"
          delta={5.2}
          spark={overview?.trend?.map((t) => t.visited) || [3, 5, 7, 9, 6, 11, 14]}
          delay={0.12}
        />
        <StatCard
          label="ROI 倍数"
          value={overview?.roi ?? 0}
          format="comma"
          suffix="x"
          icon={Award}
          tone="gold"
          delta={18.3}
          delay={0.18}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 趋势 */}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="lg:col-span-2 panel p-5 relative overflow-hidden"
        >
          <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full bg-ember-500/15 blur-3xl" />
          <SectionHeader
            index="01"
            icon={Activity}
            title="曝光 / 加微 / 到店 趋势"
            caption="单位:人数"
            actions={
              <div className="flex items-center gap-3 text-[11px] font-mono text-ink-300">
                {[
                  { c: '#FF6A2C', l: '曝光' },
                  { c: '#3CE0C6', l: '加微' },
                  { c: '#A78BFA', l: '到店' },
                ].map((s) => (
                  <span key={s.l} className="inline-flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full shadow-[0_0_6px_currentColor]" style={{ background: s.c, color: s.c }} />
                    {s.l}
                  </span>
                ))}
              </div>
            }
          />
          <div className="h-[300px]">
            <ResponsiveContainer>
              <AreaChart data={overview?.trend || []}>
                <defs>
                  <linearGradient id="g-reach" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#FF6A2C" stopOpacity={0.45} />
                    <stop offset="100%" stopColor="#FF6A2C" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="g-added" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#3CE0C6" stopOpacity={0.45} />
                    <stop offset="100%" stopColor="#3CE0C6" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="g-visited" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#A78BFA" stopOpacity={0.45} />
                    <stop offset="100%" stopColor="#A78BFA" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 6" stroke="rgba(255,255,255,0.05)" />
                <XAxis dataKey="date" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: '#7B7B85' }} />
                <YAxis tickLine={false} axisLine={false} width={32} tick={{ fontSize: 11, fill: '#7B7B85' }} />
                <Tooltip
                  contentStyle={{
                    background: 'rgba(11,11,15,0.95)',
                    border: '1px solid rgba(255,106,44,0.3)',
                    borderRadius: 12,
                    fontSize: 12,
                    color: '#fff',
                    boxShadow: '0 8px 30px rgba(0,0,0,0.4)',
                  }}
                />
                <Area type="monotone" dataKey="reach" stroke="#FF6A2C" fill="url(#g-reach)" strokeWidth={2.5} />
                <Area type="monotone" dataKey="added" stroke="#3CE0C6" fill="url(#g-added)" strokeWidth={2.5} />
                <Area type="monotone" dataKey="visited" stroke="#A78BFA" fill="url(#g-visited)" strokeWidth={2.5} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </motion.div>

        {/* 圈层对比 */}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.25 }}
          className="panel p-5 relative overflow-hidden"
        >
          <div className="absolute -top-12 -right-12 w-32 h-32 rounded-full bg-cyber-300/20 blur-3xl" />
          <SectionHeader
            index="02"
            icon={Banknote}
            title="圈层加微成本"
            caption={`当前半径 ${radius} km`}
          />
          <div className="h-[300px]">
            <ResponsiveContainer>
              <BarChart data={overview?.radiusCompare || []}>
                <defs>
                  <linearGradient id="g-bar" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#FF6A2C" stopOpacity={1} />
                    <stop offset="100%" stopColor="#FF6A2C" stopOpacity={0.4} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 6" stroke="rgba(255,255,255,0.05)" />
                <XAxis
                  dataKey="km"
                  tickFormatter={(v) => `${v}km`}
                  tickLine={false}
                  axisLine={false}
                  tick={{ fontSize: 11, fill: '#7B7B85' }}
                />
                <YAxis tickLine={false} axisLine={false} width={28} tick={{ fontSize: 11, fill: '#7B7B85' }} />
                <Tooltip
                  contentStyle={{
                    background: 'rgba(11,11,15,0.95)',
                    border: '1px solid rgba(60,224,198,0.3)',
                    borderRadius: 12,
                    fontSize: 12,
                    color: '#fff',
                    boxShadow: '0 8px 30px rgba(0,0,0,0.4)',
                  }}
                  formatter={(v: number) => [`¥${v.toFixed(2)}`, '加微成本']}
                  cursor={{ fill: 'rgba(255,106,44,0.08)' }}
                />
                <Bar dataKey="cost" fill="url(#g-bar)" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </motion.div>
      </div>

      {/* 圈层转化率 + 雷达 */}
      <div className="mt-4 grid grid-cols-1 lg:grid-cols-3 gap-4">
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
          className="lg:col-span-2 panel p-5"
        >
          <SectionHeader
            index="03"
            icon={Layers}
            title="3 / 5 / 8 / 10 km · 圈层转化漏斗"
            caption="从曝光到加微到到店到成交"
          />
          <div className="grid grid-cols-4 gap-3">
            {overview?.radiusCompare.map((r, i) => (
              <motion.div
                key={r.km}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.35 + i * 0.05 }}
                whileHover={{ y: -3 }}
                className="group relative rounded-xl border border-white/5 bg-ink-800/30 p-4 hover:border-ember-500/30 transition overflow-hidden"
              >
                <div className="absolute -top-8 -right-8 w-24 h-24 rounded-full bg-ember-500/0 group-hover:bg-ember-500/20 blur-2xl transition" />
                <div className="relative flex items-center justify-between">
                  <div className="font-mono font-bold text-lg">
                    {r.km} <span className="text-xs text-ink-400">km</span>
                  </div>
                  <span className="text-[10px] font-mono text-cyber-200">线索 {r.count}</span>
                </div>
                <div className="relative mt-3 space-y-2">
                  <div>
                    <div className="flex items-center justify-between text-[10px] font-mono">
                      <span className="text-ink-400">加微成本</span>
                      <span className="text-ember-200">¥{r.cost.toFixed(2)}</span>
                    </div>
                    <div className="mt-1 h-1.5 rounded-full bg-white/5 overflow-hidden">
                      <motion.div
                        initial={{ width: 0 }}
                        animate={{ width: `${Math.min(100, r.cost * 20)}%` }}
                        transition={{ duration: 1, ease: [0.16, 1, 0.3, 1] }}
                        className="h-full bg-gradient-to-r from-ember-500 to-ember-300"
                      />
                    </div>
                  </div>
                  <div>
                    <div className="flex items-center justify-between text-[10px] font-mono">
                      <span className="text-ink-400">转化率</span>
                      <span className="text-cyber-200">{(r.conv * 100).toFixed(1)}%</span>
                    </div>
                    <div className="mt-1 h-1.5 rounded-full bg-white/5 overflow-hidden">
                      <motion.div
                        initial={{ width: 0 }}
                        animate={{ width: `${r.conv * 100}%` }}
                        transition={{ duration: 1, ease: [0.16, 1, 0.3, 1] }}
                        className="h-full bg-gradient-to-r from-cyber-500 to-cyber-200"
                      />
                    </div>
                  </div>
                </div>
              </motion.div>
            ))}
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.35 }}
          className="panel p-5 relative overflow-hidden"
        >
          <div className="absolute -top-12 -right-12 w-32 h-32 rounded-full bg-signal-violet/20 blur-3xl" />
          <SectionHeader
            index="04"
            icon={Target}
            title="综合转化雷达"
            caption="6 维度评估"
          />
          <div className="h-[260px] flex items-center justify-center">
            <ResponsiveContainer>
              <RadarChart data={radarData}>
                <defs>
                  <linearGradient id="g-radar" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#A78BFA" stopOpacity={0.6} />
                    <stop offset="100%" stopColor="#A78BFA" stopOpacity={0.1} />
                  </linearGradient>
                </defs>
                <PolarGrid stroke="rgba(255,255,255,0.08)" />
                <PolarAngleAxis dataKey="dim" tick={{ fontSize: 11, fill: '#9B9BA3' }} />
                <PolarRadiusAxis angle={30} domain={[0, 100]} tick={false} axisLine={false} />
                <Radar
                  dataKey="value"
                  stroke="#A78BFA"
                  fill="url(#g-radar)"
                  fillOpacity={0.7}
                  strokeWidth={2}
                />
              </RadarChart>
            </ResponsiveContainer>
          </div>
        </motion.div>
      </div>
    </div>
  );
}
