import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import {
  LineChart as LineIcon,
  TrendingUp,
  Users2,
  MapPin,
  Award,
  ArrowUpRight,
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
} from 'recharts';
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

  return (
    <div className="p-8 max-w-[1400px] mx-auto">
      <header className="flex items-end justify-between flex-wrap gap-4 mb-6">
        <div>
          <div className="flex items-center gap-2 text-[11px] font-mono uppercase tracking-widest text-cyber-200">
            <LineIcon className="w-3.5 h-3.5" />
            Dashboard · 数据看板
          </div>
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
                range === r ? 'bg-ember-500 text-ink-950' : 'text-ink-300 hover:text-white',
              )}
            >
              {r === '7d' ? '近 7 天' : '近 30 天'}
            </button>
          ))}
        </div>
      </header>

      {/* 顶部数据卡 */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-6">
        {[
          { label: '总曝光',    value: overview?.reach ?? 0,        icon: TrendingUp, delta: '+12.4%' },
          { label: '加微数',    value: overview?.addedWechat ?? 0,   icon: Users2,     delta: '+8.7%'  },
          { label: '到店数',    value: overview?.visited ?? 0,      icon: MapPin,     delta: '+5.2%'  },
          { label: 'ROI 倍数',  value: `${overview?.roi ?? 0}x`,    icon: Award,      delta: '+18.3%' },
        ].map((c, i) => (
          <motion.div
            key={c.label}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.04 }}
            className="panel p-5"
          >
            <div className="flex items-center justify-between mb-3">
              <div className="text-[10px] font-mono uppercase tracking-widest text-ink-400">{c.label}</div>
              <c.icon className="w-4 h-4 text-ember-500" />
            </div>
            <div className="metric-num">{typeof c.value === 'number' ? c.value.toLocaleString() : c.value}</div>
            <div className="mt-1.5 text-[11px] font-mono text-cyber-200 inline-flex items-center gap-1">
              <ArrowUpRight className="w-3 h-3" /> 较上周期 {c.delta}
            </div>
          </motion.div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 趋势 */}
        <div className="lg:col-span-2 panel p-5">
          <div className="flex items-center justify-between mb-4">
            <div>
              <div className="text-sm font-semibold text-white">曝光 / 加微 / 到店 趋势</div>
              <div className="text-[10px] font-mono text-ink-400">单位:人数</div>
            </div>
            <div className="flex items-center gap-3 text-[11px] font-mono text-ink-300">
              {[
                { c: '#FF6A2C', l: '曝光' },
                { c: '#3CE0C6', l: '加微' },
                { c: '#A78BFA', l: '到店' },
              ].map((s) => (
                <span key={s.l} className="inline-flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full" style={{ background: s.c }} />
                  {s.l}
                </span>
              ))}
            </div>
          </div>
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
                <XAxis dataKey="date" tickLine={false} axisLine={false} />
                <YAxis tickLine={false} axisLine={false} width={32} />
                <Tooltip
                  contentStyle={{
                    background: 'rgba(11,11,15,0.95)',
                    border: '1px solid rgba(255,106,44,0.3)',
                    borderRadius: 12,
                    fontSize: 12,
                    color: '#fff',
                  }}
                />
                <Area type="monotone" dataKey="reach" stroke="#FF6A2C" fill="url(#g-reach)" strokeWidth={2} />
                <Area type="monotone" dataKey="added" stroke="#3CE0C6" fill="url(#g-added)" strokeWidth={2} />
                <Area type="monotone" dataKey="visited" stroke="#A78BFA" fill="url(#g-visited)" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* 圈层对比 */}
        <div className="panel p-5">
          <div className="flex items-center justify-between mb-4">
            <div>
              <div className="text-sm font-semibold text-white">圈层加微成本</div>
              <div className="text-[10px] font-mono text-ink-400">当前半径 {radius} km</div>
            </div>
          </div>
          <div className="h-[300px]">
            <ResponsiveContainer>
              <BarChart data={overview?.radiusCompare || []}>
                <CartesianGrid strokeDasharray="3 6" stroke="rgba(255,255,255,0.05)" />
                <XAxis
                  dataKey="km"
                  tickFormatter={(v) => `${v}km`}
                  tickLine={false}
                  axisLine={false}
                />
                <YAxis tickLine={false} axisLine={false} width={28} />
                <Tooltip
                  contentStyle={{
                    background: 'rgba(11,11,15,0.95)',
                    border: '1px solid rgba(60,224,198,0.3)',
                    borderRadius: 12,
                    fontSize: 12,
                    color: '#fff',
                  }}
                  formatter={(v: number) => [`¥${v.toFixed(2)}`, '加微成本']}
                />
                <Bar dataKey="cost" fill="#FF6A2C" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* 圈层转化率 */}
      <div className="mt-4 panel p-5">
        <div className="text-sm font-semibold text-white mb-4">3 / 5 / 8 / 10 km · 圈层转化漏斗</div>
        <div className="grid grid-cols-4 gap-3">
          {overview?.radiusCompare.map((r) => (
            <div key={r.km} className="rounded-xl border border-white/5 bg-ink-800/30 p-4">
              <div className="flex items-center justify-between">
                <div className="font-mono font-bold text-lg">{r.km} <span className="text-xs text-ink-400">km</span></div>
                <span className="text-[10px] font-mono text-cyber-200">线索 {r.count}</span>
              </div>
              <div className="mt-3 space-y-2">
                <div>
                  <div className="flex items-center justify-between text-[10px] font-mono">
                    <span className="text-ink-400">加微成本</span>
                    <span className="text-ember-200">¥{r.cost.toFixed(2)}</span>
                  </div>
                  <div className="mt-1 h-1.5 rounded-full bg-white/5 overflow-hidden">
                    <div
                      className="h-full bg-gradient-to-r from-ember-500 to-ember-300"
                      style={{ width: `${Math.min(100, r.cost * 20)}%` }}
                    />
                  </div>
                </div>
                <div>
                  <div className="flex items-center justify-between text-[10px] font-mono">
                    <span className="text-ink-400">转化率</span>
                    <span className="text-cyber-200">{(r.conv * 100).toFixed(1)}%</span>
                  </div>
                  <div className="mt-1 h-1.5 rounded-full bg-white/5 overflow-hidden">
                    <div
                      className="h-full bg-gradient-to-r from-cyber-500 to-cyber-200"
                      style={{ width: `${r.conv * 100}%` }}
                    />
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
